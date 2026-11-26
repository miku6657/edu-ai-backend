import json
import logging

from .chat_service import chat_stream
from .vector_store import search_course_by_collection

logger = logging.getLogger(__name__)

# 检索时取回的课程资料切片数量
RAG_TOP_K = 5

# 多轮追问识别：以这些词开头的问句视为依赖上文
FOLLOW_UP_PREFIXES = (
    "它", "这个", "那个", "这种", "那种", "那",
    "上述", "上面", "刚才", "前面",
    "第一个", "第二个", "第三个", "前者", "后者",
)

# 整句匹配即为追问的短问句
FOLLOW_UP_ONLY_QUESTIONS = {
    "有什么特点",
    "有什么区别",
    "为什么",
    "怎么理解",
    "怎么实现",
    "具体说说",
    "详细说说",
    "继续",
    "继续解释",
    "还有呢",
    "然后呢",
    "分别呢",
    "为什么呢",
    "怎么回事",
    "再解释一下",
}

# 用于构建检索文本的历史消息裁剪参数
MAX_HISTORY_MESSAGES = 6
MAX_MESSAGE_CHARS = 300


def sse_event(event: str, data: dict) -> str:
    return (
        f"event: {event}\n"
        f"data: {json.dumps(data, ensure_ascii=False)}\n\n"
    )


def build_context(
    sources: list[dict],
) -> str:
    parts = []
    for index, item in enumerate(
        sources,
        start=1,
    ):
        section = (
            item.get("section_title")
            or "未标注章节"
        )
        parts.append(
            f"[资料{index}]\n"
            f"文档ID：{item['document_id']}\n"
            f"页码：{item['page']}\n"
            f"章节：{section}\n"
            f"切片序号：{item.get('chunk_index', 0)}\n"
            f"内容：{item['text']}"
        )
    return "\n\n".join(parts)


def build_retrieval_query(question: str, history: list[dict]) -> str:
    """为多轮追问构建检索文本，普通新问题原样返回。

    只有明显依赖上文的追问（代词开头、固定短问句、以“呢”结尾的短句）
    才会拼接最近历史，帮助向量检索理解省略和指代。
    """
    current_question = question.strip()
    normalized_question = current_question

    is_follow_up = (
        normalized_question.startswith(FOLLOW_UP_PREFIXES)
        or normalized_question in FOLLOW_UP_ONLY_QUESTIONS
        or (
            len(normalized_question) <= 8
            and normalized_question.endswith("呢")
        )
    )

    # 普通新问题不混入之前的话题
    if not is_follow_up:
        return current_question

    valid_history = []
    for item in history:
        if not isinstance(item, dict):
            continue
        role = item.get("role")
        content = item.get("content")
        if role not in ("user", "assistant"):
            continue
        if not isinstance(content, str) or not content.strip():
            continue
        valid_history.append((role, content.strip()))

    context_lines = []
    for role, content in valid_history[-MAX_HISTORY_MESSAGES:]:
        content = content[:MAX_MESSAGE_CHARS]
        label = (
            "此前学生提问"
            if role == "user"
            else "此前助手回复（仅作指代线索）"
        )
        context_lines.append(f"{label}：{content}")

    if not context_lines:
        return current_question

    return (
        "以下对话内容仅用于理解当前问题中的省略、指代和上下文，"
        "不能作为课程知识事实依据。\n"
        + "\n".join(context_lines)
        + f"\n当前学生问题：{current_question}"
    )


def build_system_prompt(context: str) -> str:
    """基于检索资料构建可溯源的课程问答提示词。"""
    return (
        "你是一名严谨的课程助教。\n"
        "【事实依据】\n"
        "只能使用本轮提供的课程资料作为课程事实依据。"
        "历史对话只用于理解上下文，不是事实来源。\n"
        "【引用规则】\n"
        "1. 回答中的课程知识结论必须尽可能标注来源。\n"
        "2. 引用格式必须使用 [资料1]、[资料2] 等编号。\n"
        "3. 编号必须与下方课程资料中的编号一致。\n"
        "4. 多份资料共同支持一个结论时，可以同时引用多个编号。\n"
        "5. 不得虚构引用编号，不得引用未提供的资料。\n"
        "【多轮追问】\n"
        "结合历史对话理解指代和省略，"
        "但当前回答仍然必须有本轮课程资料作为依据。\n"
        "【拒答规则】\n"
        "如果当前课程资料没有足够依据回答，"
        "明确说明：资料中未找到相关内容。"
        "不要猜测或编造。\n"
        "【回答要求】\n"
        "直接回答学生的问题，表达清晰，分点组织内容。"
        "不要输出虚假的页码、章节或文档名称。\n\n"
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
    # 构建当前问题的检索文本
    retrieval_query = build_retrieval_query(
        question=question,
        history=history,
    )

    # 使用上下文增强后的问题进行向量检索
    sources = search_course_by_collection(
        collection_name_value=collection_name_value,
        query=retrieval_query,
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

    # 4. 返回完整引用信息，供 Java 保存和前端展示
    citation_sources = []
    for index, item in enumerate(sources, start=1):
        document_id = str(item["document_id"])
        page = int(item["page"])
        chunk_index = int(item.get("chunk_index") or 0)
        citation_sources.append({
            "documentId": document_id,
            "page": page,
            "pageStart": page,
            "pageEnd": page,
            "chunkIndex": chunk_index,
            "parentChunkId": f"{document_id}:{page}:{chunk_index}",
            "sectionTitle": item.get("section_title"),
            "content": item.get("text", ""),
            "similarityScore": item.get("score"),
            "citationIndex": index,
        })

    yield sse_event("sources", {
        "sources": citation_sources
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
