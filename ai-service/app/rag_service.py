import json
import logging

from .chat_service import chat_stream
from .vector_store import search_course_by_collection

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
        "你是一名课程助教，请严格依据下面的当前课程资料回答学生的问题。\n"
        "请以当前提供的资料为准，不要使用资料之外或历史对话中的信息。\n"
        "如果资料中没有相关内容，请直接说明“资料中未找到相关内容”，不要编造。\n\n"
        f"【课程资料】\n{context}"
    )


def rag_stream(
    course_id: int,
    question: str,
    history: list[dict],
    allowed_document_ids: list[str],
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
    # 1. 空白名单直接拒答，不调用 Embedding 与 LLM。
    if not allowed_document_ids:
        yield sse_event("sources", {"sources": []})
        yield sse_event("token", {"content": "当前课程暂无可检索的生效资料。"})
        yield sse_event("done", {})
        return

    # 2. 检索课程知识库：只按 Profile 指定的 Collection 检索，
    #    并且只允许白名单中的当前生效文档。
    sources = search_course_by_collection(
        collection_name_value=collection_name_value,
        query=question,
        allowed_document_ids=allowed_document_ids,
        top_k=RAG_TOP_K,
        model=embedding_model,
        api_key=embedding_api_key,
        base_url=embedding_base_url,
    )

    # 3. 检索不到切片时直接拒答，避免模型编造答案。
    if not sources:
        yield sse_event("sources", {"sources": []})
        yield sse_event("token", {"content": "资料中未找到相关内容。"})
        yield sse_event("done", {})
        return

    # 4. 返回命中的资料来源（documentId 使用字符串，避免前端精度丢失）
    yield sse_event("sources", {
        "sources": [
            {"documentId": str(item["document_id"]), "page": int(item["page"])}
            for item in sources
        ]
    })

    # 5. 构建 Prompt
    context = build_context(sources)
    messages = [
        {"role": "system", "content": build_system_prompt(context)},
        *history,
        {"role": "user", "content": question},
    ]

    # 6. 调用用户自己的 Chat 模型并逐段输出
    for token in chat_stream(
        messages,
        model=chat_model,
        api_key=chat_api_key,
        base_url=chat_base_url,
    ):
        yield sse_event("token", {"content": token})

    # 7. 结束事件
    yield sse_event("done", {})
