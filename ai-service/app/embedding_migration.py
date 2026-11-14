import logging
from pathlib import Path

from .pdf_parser import parse_pdf
from .embedding_service import create_embedding_client, embed_texts
from .vector_store import (
    index_document_for_migration,
    get_collection,
    collection_name,
    activate_collection,
)

logger = logging.getLogger(__name__)


def migrate_course(
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
    """为课程创建新版本 Embedding 索引。

    documents 结构：
        [{"document_id": 1, "storage_name": "xxx.pdf"}, ...]

    迁移阶段向量只写入未激活状态，全部文档完成并验证通过后
    统一激活，最后为目标 Collection 打元数据。
    失败时绝不修改旧 Collection。
    """
    if target_version <= source_version:
        raise ValueError("目标版本必须大于源版本")
    if target_version < 2:
        raise ValueError("目标版本必须使用带版本号的Collection")

    client = create_embedding_client(api_key, base_url)

    target_collection = get_collection(course_id, target_version)

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

        indexed = index_document_for_migration(
            course_id=course_id,
            document_id=document_id,
            chunks=chunks,
            version=target_version,
            model=model,
            embedding_client=client,
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

    # 全部文档写入并验证通过后，统一激活目标 Collection。
    activate_collection(course_id, target_version)

    # 最后为目标 Collection 打元数据。
    target_collection.modify(metadata={
        "course_id": str(course_id),
        "version": str(target_version),
        "provider": provider,
        "model": model,
        "dimension": str(dimension),
        "status": "ready",
    })

    return {
        "course_id": course_id,
        "source_version": source_version,
        "target_version": target_version,
        "collection": collection_name(course_id, target_version),
        "total_documents": total_documents,
        "indexed_chunks": total_chunks,
        "dimension": dimension,
        "status": "SUCCEEDED",
    }
