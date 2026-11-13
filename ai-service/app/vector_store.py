import chromadb

from .config import CHROMA_DIR
from .embedding_service import embed_query, embed_texts

CHROMA_DIR.mkdir(parents=True, exist_ok=True)

client = chromadb.PersistentClient(path=str(CHROMA_DIR))


def collection_name(course_id: int) -> str:
    return f"course_{course_id}"


def get_collection(course_id: int):
    return client.get_or_create_collection(
        name=collection_name(course_id),
        embedding_function=None,
        configuration={
            "hnsw": {"space": "cosine"}
        },
    )


def index_document(
    course_id: int,
    document_id,
    chunks: list[dict],
) -> int:
    """将一篇 PDF 的文本切片写入向量库。

    先写入未激活状态，全部成功后再激活，降低部分失败风险。
    """
    collection = get_collection(course_id)

    texts = [c["text"] for c in chunks]
    embeddings = embed_texts(texts)

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


def search_course(
    course_id: int,
    query: str,
    top_k: int = 5,
) -> list[dict]:
    """按课程检索知识库，返回最相近的文本切片。"""
    collection = get_collection(course_id)

    if collection.count() == 0:
        return []

    query_embedding = embed_query(query)

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
