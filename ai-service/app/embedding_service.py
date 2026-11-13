from openai import OpenAI

from .config import (
    EMBEDDING_API_KEY,
    EMBEDDING_BASE_URL,
    EMBEDDING_MODEL,
)

embedding_client = OpenAI(
    api_key=EMBEDDING_API_KEY,
    base_url=EMBEDDING_BASE_URL,
    timeout=60.0,
    max_retries=2,
)


def embed_texts(texts: list[str]) -> list[list[float]]:
    if not texts:
        return []

    vectors = []

    # 分批提交，避免单次请求过大
    batch_size = 16
    for start in range(0, len(texts), batch_size):
        batch = texts[start:start + batch_size]

        response = embedding_client.embeddings.create(
            model=EMBEDDING_MODEL,
            input=batch,
        )

        for item in response.data:
            vectors.append(item.embedding)

    return vectors


def embed_query(text: str) -> list[float]:
    response = embedding_client.embeddings.create(
        model=EMBEDDING_MODEL,
        input=[text],
    )
    return response.data[0].embedding
