import logging
from pathlib import Path

from .embedding_service import (
    create_embedding_client,
    embed_texts,
)

from .vector_store import (
    create_migration_collection,
    index_document_into_collection,
    read_document_chunks,
)

logger = logging.getLogger(__name__)


def migrate_course(
    migration_id: int,
    execution_token: str,
    course_id: int,
    source_version: int,
    target_version: int,
    source_collection_name: str,
    documents: list[dict],
    pdf_storage_dir: Path,
    provider: str,
    base_url: str,
    api_key: str,
    model: str,
    expected_dimension: int,
) -> dict:
    """
    为一次Embedding迁移建立独立的临时Collection。

    从源Collection读取既有知识切片，
    只重新计算向量，不重新解析PDF。

    保证：
    1. 切片正文不变。
    2. 切片ID不变。
    3. 原始页码不变。
    4. 原文章节标题不变。
    5. MySQL知识点关联不受影响。

    返回STAGED状态，由Java负责最终提交。
    """

    if migration_id <= 0:
        raise ValueError("migration_id不合法")

    if course_id <= 0:
        raise ValueError("course_id不合法")

    if not execution_token:
        raise ValueError("执行令牌不能为空")

    if source_version < 1:
        raise ValueError("源版本不合法")

    if target_version <= source_version:
        raise ValueError("目标版本必须大于源版本")

    if target_version < 2:
        raise ValueError("目标版本不合法")

    if not source_collection_name:
        raise ValueError("源Collection不能为空")

    if expected_dimension <= 0:
        raise ValueError("目标向量维度不合法")

    if not documents:
        raise ValueError("没有可迁移的文档")

    # 防止同一个文档在迁移请求中重复出现。
    visited_documents = set()

    parsed_documents = []

    # 1. 先读取并校验全部源切片。
    # 不再调用parse_pdf()。
    for item in documents:

        document_id = item["document_id"]

        if (
            type(document_id) is not int
            or document_id <= 0
        ):
            raise ValueError("迁移文档ID不合法")

        if document_id in visited_documents:
            raise ValueError("迁移文档ID重复")

        visited_documents.add(document_id)

        chunks = read_document_chunks(
            collection_name_value=(
                source_collection_name
            ),
            course_id=course_id,
            document_id=document_id,
        )

        if not chunks:
            raise ValueError(
                f"文档{document_id}没有可迁移的有效切片"
            )

        parsed_documents.append(
            (document_id, chunks)
        )

    expected_total_chunks = sum(
        len(chunks)
        for _, chunks in parsed_documents
    )

    logger.info(
        "开始Embedding迁移: "
        "course_id=%s, source_collection=%s, "
        "documents=%s, chunks=%s",
        course_id,
        source_collection_name,
        len(parsed_documents),
        expected_total_chunks,
    )

    # 2. 初始化目标Embedding模型。
    client = create_embedding_client(
        api_key,
        base_url,
    )

    first_chunk = parsed_documents[0][1][0]

    sample = embed_texts(
        [first_chunk["text"]],
        model=model,
        client=client,
    )

    if len(sample) != 1:
        raise ValueError(
            "目标Embedding模型返回结果不合法"
        )

    dimension = len(sample[0])

    if dimension != expected_dimension:
        raise ValueError(
            f"Embedding维度不匹配: "
            f"期望{expected_dimension}, "
            f"实际{dimension}"
        )

    # 3. 创建独立的目标迁移Collection。
    collection_name, collection = (
        create_migration_collection(
            migration_id,
            execution_token,
            course_id,
        )
    )

    total_documents = 0
    total_chunks = 0

    # 4. 使用原始切片进行重新向量化。
    for document_id, chunks in parsed_documents:

        indexed = index_document_into_collection(
            collection,
            document_id,
            chunks,
            model=model,
            embedding_client=client,
            active="1",
        )

        if indexed != len(chunks):
            raise ValueError(
                f"文档{document_id}迁移数量不一致"
            )

        total_documents += 1
        total_chunks += indexed

        logger.info(
            "迁移文档完成: document_id=%s, chunks=%s",
            document_id,
            indexed,
        )

    # 5. 校验迁移结果。
    if total_documents != len(documents):
        raise ValueError(
            "迁移文档总数不一致"
        )

    if total_chunks != expected_total_chunks:
        raise ValueError(
            "迁移切片总数不一致"
        )

    actual_count = collection.count()

    if actual_count != total_chunks:
        raise ValueError(
            f"Collection向量数量不匹配: "
            f"期望{total_chunks}, "
            f"实际{actual_count}"
        )

    # 6. 校验目标Collection中的切片标识和正文。
    for document_id, source_chunks in parsed_documents:

        target_chunks = read_document_chunks(
            collection_name_value=collection_name,
            course_id=course_id,
            document_id=document_id,
        )

        source_map = {
            (
                item["page"],
                item["chunk_index"],
            ): (
                item["text"],
                item.get("section_title"),
            )
            for item in source_chunks
        }

        target_map = {
            (
                item["page"],
                item["chunk_index"],
            ): (
                item["text"],
                item.get("section_title"),
            )
            for item in target_chunks
        }

        if source_map != target_map:
            raise ValueError(
                f"文档{document_id}迁移前后切片不一致"
            )

    logger.info(
        "Embedding迁移暂存完成: "
        "course_id=%s, collection=%s, "
        "documents=%s, chunks=%s",
        course_id,
        collection_name,
        total_documents,
        total_chunks,
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
