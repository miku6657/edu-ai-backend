import hmac
import logging
from contextlib import asynccontextmanager
from threading import Lock

from fastapi import Depends, FastAPI, Header, HTTPException
from fastapi.responses import StreamingResponse
from pydantic import BaseModel, Field

from .config import AI_INTERNAL_KEY, PDF_STORAGE_DIR
from .embedding_migration import migrate_course
from .pdf_parser import parse_pdf
from .rag_service import rag_stream
from .vector_store import delete_document_vectors_all_versions, index_document, search_course

logger = logging.getLogger(__name__)

# 单进程索引锁
index_lock = Lock()


def require_internal(
    x_internal_key: str = Header(alias="X-Internal-Key"),
) -> None:
    """校验内部认证密钥，只允许 Java 后端调用 /internal 接口。"""
    if not hmac.compare_digest(x_internal_key, AI_INTERNAL_KEY):
        raise HTTPException(status_code=401, detail="无效的内部认证密钥")


@asynccontextmanager
async def lifespan(app: FastAPI):
    logger.info("edu05-ai-service 启动")
    yield
    logger.info("edu05-ai-service 关闭")


app = FastAPI(title="edu05-ai-service", lifespan=lifespan)


class IndexRequest(BaseModel):
    course_id: int = Field(gt=0)
    document_id: int = Field(gt=0)
    storage_name: str = Field(
        min_length=1,
        max_length=100,
    )
    embedding_version: int = Field(
        default=1,
        ge=1,
    )


class SearchRequest(BaseModel):
    course_id: int
    query: str
    top_k: int = Field(5, ge=1, le=50)


class HistoryMessage(BaseModel):
    role: str
    content: str = Field(
        min_length=1,
        max_length=10000,
    )


class ChatRequest(BaseModel):
    course_id: int = Field(gt=0)
    question: str = Field(
        min_length=1,
        max_length=2000,
    )
    history: list[HistoryMessage] = Field(
        default_factory=list,
        max_length=10,
    )
    embedding_version: int = Field(ge=1)
    chat_model: str = Field(
        min_length=1,
        max_length=100,
    )
    chat_api_key: str = Field(
        default="",
        max_length=1000,
    )
    chat_base_url: str = Field(
        min_length=1,
        max_length=500,
    )
    embedding_model: str = Field(
        min_length=1,
        max_length=100,
    )
    embedding_api_key: str = Field(
        default="",
        max_length=1000,
    )
    embedding_base_url: str = Field(
        min_length=1,
        max_length=500,
    )


@app.get("/health")
def health():
    return {"status": "ok"}


@app.post("/internal/index")
def internal_index(req: IndexRequest, _: None = Depends(require_internal)):
    pdf_path = (PDF_STORAGE_DIR / req.storage_name).resolve()

    # 防止路径穿越
    if not pdf_path.is_relative_to(PDF_STORAGE_DIR.resolve()):
        raise HTTPException(status_code=400, detail="非法文件名")

    if not pdf_path.is_file():
        raise HTTPException(status_code=404, detail="PDF 文件不存在")

    try:
        chunks = parse_pdf(pdf_path)
    except ValueError as exc:
        raise HTTPException(status_code=400, detail=str(exc))

    if not chunks:
        raise HTTPException(status_code=400, detail="未从 PDF 提取到文本")

    with index_lock:
        indexed = index_document(
            req.course_id,
            req.document_id,
            chunks,
            version=req.embedding_version,
        )

    return {
        "document_id": req.document_id,
        "indexed_chunks": indexed,
    }


@app.post("/internal/search")
def internal_search(req: SearchRequest, _: None = Depends(require_internal)):
    results = search_course(req.course_id, req.query, req.top_k)
    return {"results": results}


@app.post(
    "/internal/chat/stream",
    dependencies=[Depends(require_internal)],
)
def chat_stream_api(request: ChatRequest):
    history = [
        {
            "role": item.role,
            "content": item.content,
        }
        for item in request.history
        if item.role in ("user", "assistant")
    ]

    return StreamingResponse(
        rag_stream(
            course_id=request.course_id,
            question=request.question.strip(),
            history=history,
            embedding_version=request.embedding_version,
            chat_model=request.chat_model,
            chat_api_key=request.chat_api_key,
            chat_base_url=request.chat_base_url,
            embedding_model=request.embedding_model,
            embedding_api_key=request.embedding_api_key,
            embedding_base_url=request.embedding_base_url,
        ),
        media_type="text/event-stream",
        headers={
            "Cache-Control": "no-cache",
            "X-Accel-Buffering": "no",
        },
    )


class MigrationDocument(BaseModel):
    document_id: int = Field(gt=0)
    storage_name: str = Field(
        min_length=1,
        max_length=100,
    )


class CourseMigrationRequest(BaseModel):
    course_id: int = Field(gt=0)
    source_version: int = Field(ge=1)
    target_version: int = Field(ge=2)
    documents: list[MigrationDocument] = Field(
        min_length=1,
        max_length=500,
    )
    provider: str = Field(min_length=1, max_length=40)
    base_url: str = Field(min_length=1, max_length=500)
    api_key: str = Field(default="", max_length=1000)
    model: str = Field(min_length=1, max_length=100)
    expected_dimension: int = Field(gt=0, le=8192)


@app.post(
    "/internal/embedding/migrate",
    dependencies=[Depends(require_internal)],
)
def internal_embedding_migrate(
    request: CourseMigrationRequest,
):
    documents = [
        item.model_dump()
        for item in request.documents
    ]
    try:
        # 与普通索引共用进程内锁。
        with index_lock:
            result = migrate_course(
                course_id=request.course_id,
                source_version=request.source_version,
                target_version=request.target_version,
                documents=documents,
                pdf_storage_dir=PDF_STORAGE_DIR,
                provider=request.provider,
                base_url=request.base_url,
                api_key=request.api_key,
                model=request.model,
                expected_dimension=request.expected_dimension,
            )
        return result
    except Exception:
        logger.exception(
            "Embedding migration failed: course_id=%s",
            request.course_id,
        )
        raise HTTPException(
            status_code=500,
            detail="Embedding迁移失败",
        )


class DeleteDocumentRequest(BaseModel):
    course_id: int = Field(gt=0)
    document_id: int = Field(gt=0)


@app.post(
    "/internal/documents/delete",
    dependencies=[Depends(require_internal)],
)
def delete_document_api(request: DeleteDocumentRequest):
    try:
        # 与 PDF 索引使用同一把锁，避免进程内读写冲突
        with index_lock:
            deleted_count = delete_document_vectors_all_versions(
                course_id=request.course_id,
                document_id=request.document_id,
            )
        return {
            "success": True,
            "document_id": request.document_id,
            "deleted_vectors": deleted_count,
        }
    except Exception as error:
        logger.exception(
            "Delete vectors failed: document_id=%s",
            request.document_id,
        )
        raise HTTPException(
            status_code=500,
            detail="删除向量失败",
        )
