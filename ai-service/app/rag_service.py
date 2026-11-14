import json
import logging

from .chat_service import chat_stream
from .vector_store import search_course, search_course_by_collection

logger = logging.getLogger(__name__)

# 检索时取回的课程资料切片数量
RAG_TOP_K = 5


def sse_event(event: str, data: dict) -> str:
    return (
        f"event: {event}\n"
        f"data: {json.dumps(data, ensure_ascii=False)}\n\n"
    )


def build_context(sources: list[dict]) -> str:
    parts = []
    for index, item in enumerate(sources, start=1):
        parts.append(
            f"[资料{index}]\n"
            f"文档ID：{item['document_id']}\n"
            f"页码：{item['page']}\n"
            f"内容：{item['text']}"
        )
    return "\n\n".join(parts)


def build_system_prompt(context: str) -> str:
    """根据检索到的课程资料构建系统提示词。"""
    return (
        "你是一名课程助教，请严格依据下面的课程资料回答学生的问题。\n"
        "如果资料中没有相关内容，请直接说明“资料中未找到相关内容”，不要编造。\n\n"
        f"【课程资料】\n{context}"
    )


def rag_stream(
    course_id: int,
    question: str,
    history: list[dict],
    embedding_version: int = 1,
    collection_name_value: str | None = None,
    embedding_model: str | None = None,
    embedding_api_key: str | None = None,
    embedding_base_url: str | None = None,
    chat_model: str | None = None,
    chat_api_key: str | None = None,
    chat_base_url: str | None = None,
):
    """查询课程知识库、构建 Prompt，并以 SSE 流式返回答案。

    事件序列：
        event: sources  -> 命中的资料（文档 ID、页码）
        event: token    -> 逐段返回模型输出
        event: done     -> 结束

    课程当前生效的 Embedding 版本与模型，以及用户自己的 Chat 模型，
    都由 Java 通过可信的内部请求传入，不能直接信任前端参数。
    """
    # 1. 检索课程知识库，只取当前课程的资料
    if collection_name_value:
        sources = search_course_by_collection(
            collection_name_value=collection_name_value,
            query=question,
            top_k=RAG_TOP_K,
            model=embedding_model,
            api_key=embedding_api_key,
            base_url=embedding_base_url,
        )
    else:
        # 仅用于尚未完成历史 Profile 初始化的旧版本兼容。
        sources = search_course(
            course_id,
            question,
            top_k=RAG_TOP_K,
            version=embedding_version,
            model=embedding_model,
            api_key=embedding_api_key,
            base_url=embedding_base_url,
        )

    # 2. 返回命中的资料来源
    yield sse_event("sources", {
        "sources": [
            {"documentId": int(item["document_id"]), "page": int(item["page"])}
            for item in sources
        ]
    })

    # 3. 构建 Prompt
    context = build_context(sources)
    messages = [
        {"role": "system", "content": build_system_prompt(context)},
        *history,
        {"role": "user", "content": question},
    ]

    # 4. 调用用户自己的 Chat 模型并逐段输出
    for token in chat_stream(
        messages,
        model=chat_model,
        api_key=chat_api_key,
        base_url=chat_base_url,
    ):
        yield sse_event("token", {"content": token})

    # 5. 结束事件
    yield sse_event("done", {})
