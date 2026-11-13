from openai import OpenAI

from .config import (
    DEEPSEEK_API_KEY,
    DEEPSEEK_BASE_URL,
    DEEPSEEK_MODEL,
)

chat_client = OpenAI(
    api_key=DEEPSEEK_API_KEY,
    base_url=DEEPSEEK_BASE_URL,
    timeout=90.0,
    max_retries=2,
)


def chat(messages: list[dict]) -> str:
    response = chat_client.chat.completions.create(
        model=DEEPSEEK_MODEL,
        messages=messages,
        stream=False,
    )
    return response.choices[0].message.content or ""


def chat_stream(messages: list[dict]):
    response = chat_client.chat.completions.create(
        model=DEEPSEEK_MODEL,
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
