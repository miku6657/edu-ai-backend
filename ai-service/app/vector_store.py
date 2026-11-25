import chromadb
import hashlib
import time

from openai import OpenAI

from .config import CHROMA_DIR, EMBEDDING_MODEL
from .embedding_service import (
    embed_query,
    embed_texts,
    embedding_client,
    create_embedding_client,
)

CHROMA_DIR.mkdir(parents=True, exist_ok=True)

client = chromadb.PersistentClient(path=str(CHROMA_DIR))


def collection_name(course_id: int, version: int = 1) -> str:
    """版本1沿用旧Collection名称，保证历史数据兼容。

    版本2开始使用带版本号的Collection，实现跨模型隔离。
    """
    if version < 1:
        raise ValueError("Embedding版本不合法")
    if version == 1:
        return f"course_{course_id}"
    return f"course_{course_id}_v{version}"


def get_collection(course_id: int, version: int = 1):
    return client.get_or_create_collection(
        name=collection_name(course_id, version),
        embedding_function=None,
        configuration={
            "hnsw": {"space": "cosine"}
        },
    )


def migration_collection_name(
    migration_id: int,
    execution_token: str,
) -> str:
    """生成当前迁移执行者独占的临时 Collection 名称。"""
    if migration_id <= 0:
        raise ValueError("migration_id 不合法")
    if not execution_token:
        raise ValueError("execution_token 不能为空")

    digest = hashlib.sha256(
        execution_token.encode("utf-8")
    ).hexdigest()[:24]

    return f"mig_{migration_id}_{digest}"


def get_collection_by_name(name: str):
    """按明确的 Collection 名称获取集合（只读，不存在则报错）。"""
    if not name or len(name) > 63:
        raise ValueError("Collection 名称不合法")
    return client.get_collection(
        name=name,
        embedding_function=None,
    )


def create_migration_collection(
    migration_id: int,
    execution_token: str,
    course_id: int,
):
    """创建迁移执行者独占的临时 Collection，不允许复用。"""
    name = migration_collection_name(
        migration_id,
        execution_token,
    )

    token_hash = hashlib.sha256(
        execution_token.encode("utf-8")
    ).hexdigest()

    collection = client.create_collection(
        name=name,
        embedding_function=None,
        metadata={
            "migration_id": str(migration_id),
            "course_id": str(course_id),
            "execution_token_hash": token_hash,
            "status": "STAGED",
            "created_at": str(time.time()),
        },
        configuration={
            "hnsw": {"space": "cosine"}
        },
    )

    return name, collection


def get_or_create_index_collection(name: str):
    """按 Profile 中的 Collection 名称获取或创建正式索引集合。"""
    if not name or len(name) > 63:
        raise ValueError("Collection 名称不合法")
    return client.get_or_create_collection(
        name=name,
        embedding_function=None,
        configuration={
            "hnsw": {"space": "cosine"}
        },
    )


def cleanup_orphan_migration_collections(
    keep_collection_names: list[str],
    min_age_hours: int,
) -> dict:
    """清理未被引用且超过保护期的孤立迁移 Collection。"""
    keep = set(keep_collection_names or [])
    now = time.time()
    min_age_seconds = min_age_hours * 3600

    deleted = []
    skipped = []

    for item in client.list_collections():
        name = (
            item.name
            if hasattr(item, "name")
            else str(item)
        )

        if not name.startswith("mig_"):
            continue
        if name in keep:
            continue

        try:
            collection = client.get_collection(
                name=name,
                embedding_function=None,
            )
        except chromadb.errors.NotFoundError:
            continue

        metadata = collection.metadata or {}
        created_at = metadata.get("created_at")

        # 没有 created_at 的旧临时集合不自动删除。
        if created_at is None:
            skipped.append(name)
            continue

        try:
            created_ts = float(created_at)
        except (TypeError, ValueError):
            skipped.append(name)
            continue

        if now - created_ts < min_age_seconds:
            skipped.append(name)
            continue

        client.delete_collection(name=name)
        deleted.append(name)

    return {
        "deleted_collections": deleted,
        "skipped_collections": skipped,
    }


def index_document_into_collection(
    collection,
    document_id: int,
    chunks: list[dict],
    model: str,
    embedding_client,
    active: str = "0",
) -> int:
    """向指定 Collection 写入文档向量。"""
    if not chunks:
        raise ValueError("文档没有有效切片")

    texts = [item["text"] for item in chunks]
    embeddings = embed_texts(
        texts,
        model=model,
        client=embedding_client,
    )

    if len(embeddings) != len(chunks):
        raise RuntimeError("向量数量不匹配")

    ids = []
    documents = []
    metadatas = []

    for chunk in chunks:
        ids.append(
            f"{document_id}:{chunk['page']}:{chunk['chunk_index']}"
        )
        documents.append(chunk["text"])
        metadatas.append({
            "document_id": str(document_id),
            "page": chunk["page"],
            "chunk_index": chunk["chunk_index"],
            "active": active,
        })

    collection.upsert(
        ids=ids,
        embeddings=embeddings,
        documents=documents,
        metadatas=metadatas,
    )

    return len(ids)


def index_document(
    course_id: int,
    document_id,
    chunks: list[dict],
    version: int = 1,
    model: str | None = None,
    client: OpenAI | None = None,
) -> int:
    """将一篇 PDF 的文本切片写入向量库。

    先写入未激活状态，全部成功后再激活，降低部分失败风险。
    """
    collection = get_collection(course_id, version)

    texts = [c["text"] for c in chunks]
    model = model or EMBEDDING_MODEL
    client = client or embedding_client
    embeddings = embed_texts(texts, model=model, client=client)

    ids = []
    documents = []
    metadatas = []

    for c in chunks:
        chunk_id = f"{document_id}:{c['page']}:{c['chunk_index']}"
        ids.append(chunk_id)
        documents.append(c["text"])
        metadatas.append({
            "document_id": str(document_id),
            "page": c["page"],
            "chunk_index": c["chunk_index"],
            "active": "0",
        })

    # 先写未激活向量
    collection.upsert(
        ids=ids,
        embeddings=embeddings,
        documents=documents,
        metadatas=metadatas,
    )

    # 全部写入成功后激活
    active_metadatas = [dict(m, active="1") for m in metadatas]
    collection.update(ids=ids, metadatas=active_metadatas)

    return len(ids)


def index_document_for_migration(
    course_id: int,
    document_id: int,
    chunks: list[dict],
    version: int,
    model: str,
    embedding_client,
) -> int:
    """迁移专用写入：只写未激活状态，不立即激活。

    与 index_document 的区别：写入后不会马上 active=1，
    而是等待整个课程迁移完成并验证通过后统一激活。
    """
    if not chunks:
        raise ValueError(
            f"文档 {document_id} 没有有效切片"
        )

    collection = get_collection(course_id, version)

    texts = [item["text"] for item in chunks]
    embeddings = embed_texts(
        texts,
        model=model,
        client=embedding_client,
    )

    if len(embeddings) != len(chunks):
        raise RuntimeError("向量数量不匹配")

    ids = []
    documents = []
    metadatas = []

    for chunk in chunks:
        chunk_id = (
            f"{document_id}:{chunk['page']}:{chunk['chunk_index']}"
        )
        ids.append(chunk_id)
        documents.append(chunk["text"])
        metadatas.append({
            "document_id": str(document_id),
            "page": chunk["page"],
            "chunk_index": chunk["chunk_index"],
            "active": "0",
        })

    collection.upsert(
        ids=ids,
        embeddings=embeddings,
        documents=documents,
        metadatas=metadatas,
    )

    return len(ids)


def search_course(
    course_id: int,
    query: str,
    top_k: int = 5,
    version: int = 1,
    model: str | None = None,
    api_key: str | None = None,
    base_url: str | None = None,
) -> list[dict]:
    """按课程检索知识库，返回最相近的文本切片。"""
    collection = get_collection(course_id, version)

    if collection.count() == 0:
        return []

    model = model or EMBEDDING_MODEL

    if api_key is None and base_url is None:
        query_client = embedding_client
    else:
        query_client = create_embedding_client(api_key, base_url)

    query_embedding = embed_query(query, model=model, client=query_client)

    result = collection.query(
        query_embeddings=[query_embedding],
        n_results=top_k,
        where={"active": "1"},
        include=["documents", "metadatas", "distances"],
    )

    documents = result.get("documents") or [[]]
    metadatas = result.get("metadatas") or [[]]
    distances = result.get("distances") or [[]]

    hits = []
    for doc, meta, dist in zip(documents[0], metadatas[0], distances[0]):
        hits.append({
            "document_id": meta.get("document_id"),
            "page": meta.get("page"),
            "chunk_index": meta.get("chunk_index"),
            "text": doc,
            "score": round(1.0 - float(dist), 4),
        })

    return hits


def activate_collection(
    course_id: int,
    version: int,
) -> int:
    """将目标 Collection 中所有未激活向量统一激活。

    只在新版本构建完成并验证通过后调用。
    """
    collection = get_collection(course_id, version)

    result = collection.get(
        where={"active": "0"},
        include=["metadatas"],
    )

    ids = result.get("ids") or []
    metadatas = result.get("metadatas") or []

    if not ids:
        return 0

    active_metadatas = [
        dict(m, active="1") for m in metadatas
    ]

    all_ids = list(ids)
    for start in range(0, len(all_ids), 100):
        collection.update(
            ids=all_ids[start:start + 100],
            metadatas=active_metadatas[start:start + 100],
        )

    return len(all_ids)


def delete_document_vectors(
    course_id: int,
    document_id: int,
) -> int:
    """删除指定课程、指定文档的全部向量。

    兼容 document_id 存储为 int 或 str 的记录。
    """
    try:
        collection = client.get_collection(
            name=collection_name(course_id),
            embedding_function=None,
        )
    except chromadb.errors.NotFoundError:
        # 课程知识库尚不存在，视为已清理
        return 0

    ids = set()
    for value in (document_id, str(document_id)):
        result = collection.get(
            where={"document_id": value},
            include=[],
        )
        ids.update(result["ids"])

    if not ids:
        return 0

    all_ids = list(ids)
    for start in range(0, len(all_ids), 100):
        collection.delete(ids=all_ids[start:start + 100])

    return len(all_ids)


def delete_document_vectors_all_versions(
    course_id: int,
    document_id: int,
) -> int:
    """删除指定课程、指定文档在所有正式和临时Collection中的向量。

    覆盖：
    1. course_{id}
    2. course_{id}_v*
    3. mig_* 中 metadata.course_id 对应的迁移 Collection

    不删除 Collection 本身。
    """
    prefix = f"course_{course_id}"
    course_id_str = str(course_id)
    total_deleted = 0

    for item in client.list_collections():
        name = (
            item.name
            if hasattr(item, "name")
            else str(item)
        )

        try:
            collection = client.get_collection(
                name=name,
                embedding_function=None,
            )
        except chromadb.errors.NotFoundError:
            continue

        metadata = collection.metadata or {}

        # 正式 Collection：course_{id} 或 course_{id}_v*
        is_legacy = (
            name == prefix or name.startswith(prefix + "_v")
        )

        # 临时迁移 Collection：mig_* 且 metadata.course_id 匹配
        is_migration = (
            name.startswith("mig_")
            and metadata.get("course_id") == course_id_str
        )

        if not is_legacy and not is_migration:
            continue

        ids = set()
        for value in (document_id, str(document_id)):
            result = collection.get(
                where={"document_id": value},
                include=[],
            )
            ids.update(result["ids"])

        if not ids:
            continue

        all_ids = list(ids)
        for start in range(0, len(all_ids), 100):
            collection.delete(ids=all_ids[start:start + 100])

        total_deleted += len(all_ids)

    return total_deleted


def search_course_by_collection(
    collection_name_value: str,
    query: str,
    allowed_document_ids: list[str],
    top_k: int = 5,
    model: str | None = None,
    api_key: str | None = None,
    base_url: str | None = None,
) -> list[dict]:
    """按指定Collection检索当前生效的课程资源。

    必须同时满足：
    1. 向量处于active状态。
    2. 文档ID属于Java提供的当前生效版本白名单。
    """
    if not collection_name_value:
        raise ValueError("Collection名称不能为空")

    # 去重，同时统一为字符串类型。
    allowed_ids = sorted({
        str(document_id)
        for document_id in allowed_document_ids
    })

    # 空白名单直接返回，不能退化为全量检索。
    if not allowed_ids:
        return []

    if top_k <= 0:
        return []

    collection = client.get_collection(
        name=collection_name_value,
        embedding_function=None,
    )

    if collection.count() == 0:
        return []

    model = model or EMBEDDING_MODEL

    query_client = create_embedding_client(
        api_key=api_key,
        base_url=base_url,
    )

    query_embedding = embed_query(
        query,
        model=model,
        client=query_client,
    )

    result = collection.query(
        query_embeddings=[query_embedding],
        n_results=min(top_k, collection.count()),
        where={
            "$and": [
                {"active": "1"},
                {"document_id": {"$in": allowed_ids}},
            ]
        },
        include=[
            "documents",
            "metadatas",
            "distances",
        ],
    )

    documents = result.get("documents") or [[]]
    metadatas = result.get("metadatas") or [[]]
    distances = result.get("distances") or [[]]

    hits = []
    for doc, meta, dist in zip(documents[0], metadatas[0], distances[0]):
        hits.append({
            "document_id": meta.get("document_id"),
            "page": meta.get("page"),
            "chunk_index": meta.get("chunk_index"),
            "text": doc,
            "score": round(1.0 - float(dist), 4),
        })

    return hits
