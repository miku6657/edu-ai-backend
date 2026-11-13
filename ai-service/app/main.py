import hmac
import logging
from contextlib import asynccontextmanager
from threading import Lock

from fastapi import Depends, FastAPI, Header, HTTPException
from fastapi.responses import StreamingResponse
from pydantic import BaseModel, Field

from .config import AI_INTERNAL_KEY, PDF_STORAGE_DIR
from .pdf_parser import parse_pdf
from .rag_service import rag_stream
from .vector_store import delete_document_vectors, index_document, search_course

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
    course_id: int
    document_id: int
    storage_name: str = Field(..., description="PDF 存储文件名")


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


@app.get("/health")
def health():
    return {"status": "ok"}


@app.post("/internal/index")
def internal_index(req: IndexRequest, _: None = Depends(require_internal)):
    pdf_path = (PDF_STORAGE_DIR / req.storage_name).resolve()

    # 防止路径穿越
    if not pdf_path.is_relative_to(PDF_STORAGE_DIR):
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
        indexed = index_document(req.course_id, req.document_id, chunks)

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
        ),
        media_type="text/event-stream",
        headers={
            "Cache-Control": "no-cache",
            "X-Accel-Buffering": "no",
        },
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
            deleted_count = delete_document_vectors(
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
