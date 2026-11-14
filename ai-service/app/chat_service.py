from openai import OpenAI

from .config import (
    DEEPSEEK_API_KEY,
    DEEPSEEK_BASE_URL,
    DEEPSEEK_MODEL,
)


def create_chat_client(
    api_key: str | None = None,
    base_url: str | None = None,
) -> OpenAI:
    """创建 Chat 客户端。

    - api_key=None  → 使用全局默认环境变量
    - api_key=""    → 明确使用空 Key（例如 Ollama 本地模型）
    - api_key=其他   → 使用用户自己的 Key
    """
    return OpenAI(
        api_key=(
            DEEPSEEK_API_KEY
            if api_key is None
            else api_key
        ),
        base_url=(
            DEEPSEEK_BASE_URL
            if base_url is None
            else base_url
        ),
        timeout=90.0,
        max_retries=2,
    )


# 兼容旧版全局客户端
chat_client = create_chat_client()


def _resolve_client(
    api_key: str | None,
    base_url: str | None,
) -> OpenAI:
    if api_key is None and base_url is None:
        return chat_client
    return create_chat_client(api_key, base_url)


def chat(
    messages: list[dict],
    model: str | None = None,
    api_key: str | None = None,
    base_url: str | None = None,
) -> str:
    model = model or DEEPSEEK_MODEL
    client = _resolve_client(api_key, base_url)

    response = client.chat.completions.create(
        model=model,
        messages=messages,
        stream=False,
    )
    return response.choices[0].message.content or ""


def chat_stream(
    messages: list[dict],
    model: str | None = None,
    api_key: str | None = None,
    base_url: str | None = None,
):
    model = model or DEEPSEEK_MODEL
    client = _resolve_client(api_key, base_url)

    response = client.chat.completions.create(
        model=model,
        messages=messages,
        stream=True,
    )

    for chunk in response:
        if not chunk.choices:
            continue

        delta = chunk.choices[0].delta
        content = getattr(delta, "content", None)

        if content:
            yield content
