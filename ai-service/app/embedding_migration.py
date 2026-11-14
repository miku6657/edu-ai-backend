import logging
from pathlib import Path

from .pdf_parser import parse_pdf
from .embedding_service import (
    create_embedding_client,
    embed_texts,
)
from .vector_store import (
    create_migration_collection,
    index_document_into_collection,
)

logger = logging.getLogger(__name__)


def migrate_course(
    migration_id: int,
    execution_token: str,
    course_id: int,
    source_version: int,
    target_version: int,
    documents: list[dict],
    pdf_storage_dir: Path,
    provider: str,
    base_url: str,
    api_key: str,
    model: str,
    expected_dimension: int,
) -> dict:
    """为一次迁移执行构建独立的临时 Collection。

    documents 结构：
        [{"document_id": 1, "storage_name": "xxx.pdf"}, ...]

    返回 STAGED 状态；只有 Java 验证通过后才允许提交新版本。
    Python 负责构建，Java 负责决定是否提交。
    """
    if migration_id <= 0:
        raise ValueError("migration_id 不合法")
    if not execution_token:
        raise ValueError("执行令牌不能为空")
    if target_version <= source_version:
        raise ValueError("目标版本必须大于源版本")
    if target_version < 2:
        raise ValueError("目标版本必须使用带版本号的Collection")

    collection_name, collection = create_migration_collection(
        migration_id,
        execution_token,
        course_id,
    )

    client = create_embedding_client(api_key, base_url)

    total_documents = 0
    total_chunks = 0
    dimension = None

    for item in documents:
        document_id = item["document_id"]
        storage_name = item["storage_name"]

        pdf_path = (pdf_storage_dir / storage_name).resolve()

        # 防止路径穿越
        if not pdf_path.is_relative_to(pdf_storage_dir):
            raise ValueError(f"非法文件名: {storage_name}")

        if not pdf_path.is_file():
            raise FileNotFoundError(f"PDF 文件不存在: {storage_name}")

        chunks = parse_pdf(pdf_path)
        if not chunks:
            raise ValueError(
                f"文档 {document_id} 没有可迁移的文本"
            )

        # 维度校验：取第一个切片作为样本
        if dimension is None:
            sample = embed_texts(
                [chunks[0]["text"]],
                model=model,
                client=client,
            )
            dimension = len(sample[0])
            if dimension != expected_dimension:
                raise ValueError(
                    f"Embedding维度不匹配: 期望{expected_dimension}, "
                    f"实际{dimension}"
                )

        indexed = index_document_into_collection(
            collection,
            document_id,
            chunks,
            model=model,
            embedding_client=client,
            active="1",
        )

        total_documents += 1
        total_chunks += indexed

    if total_documents == 0:
        raise ValueError("没有可迁移的文档")

    if dimension != expected_dimension:
        raise ValueError(
            f"Embedding维度不匹配: 期望{expected_dimension}, "
            f"实际{dimension}"
        )

    return {
        "migration_id": migration_id,
        "execution_token": execution_token,
        "course_id": course_id,
        "source_version": source_version,
        "target_version": target_version,
        "collection_name": collection_name,
        "total_documents": total_documents,
        "indexed_chunks": total_chunks,
        "dimension": dimension,
        "status": "STAGED",
    }
