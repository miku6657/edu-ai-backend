from openai import OpenAI

from .config import (
    EMBEDDING_API_KEY,
    EMBEDDING_BASE_URL,
    EMBEDDING_MODEL,
)


def create_embedding_client(
    api_key: str | None = None,
    base_url: str | None = None,
) -> OpenAI:
    """创建 Embedding 客户端。

    - api_key=None  → 使用全局默认环境变量
    - api_key=""    → 明确使用空 Key（例如 Ollama 本地模型）
    - api_key=其他   → 使用用户自己的 Key
    """
    return OpenAI(
        api_key=(
            EMBEDDING_API_KEY
            if api_key is None
            else api_key
        ),
        base_url=(
            EMBEDDING_BASE_URL
            if base_url is None
            else base_url
        ),
        timeout=60.0,
        max_retries=2,
    )


# 兼容原来的全局客户端
embedding_client = create_embedding_client()


def embed_texts(
    texts: list[str],
    model: str | None = None,
    client: OpenAI | None = None,
) -> list[list[float]]:
    if not texts:
        return []

    model = model or EMBEDDING_MODEL
    client = client or embedding_client

    vectors = []

    # 分批提交，避免单次请求过大
    batch_size = 16
    for start in range(0, len(texts), batch_size):
        batch = texts[start:start + batch_size]

        response = client.embeddings.create(
            model=model,
            input=batch,
        )

        for item in response.data:
            vectors.append(item.embedding)

    return vectors


def embed_query(
    text: str,
    model: str | None = None,
    client: OpenAI | None = None,
) -> list[float]:
    model = model or EMBEDDING_MODEL
    client = client or embedding_client

    response = client.embeddings.create(
        model=model,
        input=[text],
    )
    return response.data[0].embedding
