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
    """基于本轮检索证据构建支持多轮追问的系统提示词。"""
    return (
        "你是一名严谨的课程助教，负责回答学生关于当前课程的问题。\n"
        "【事实依据】"
        "你只能使用本轮提供的【课程资料】作为课程事实依据。"
        "历史对话只用于理解学生的指代、省略和追问关系，不能作为事实来源；"
        "此前助手的回答也不能代替课程资料。\n"
        "【多轮对话】"
        "当学生使用‘它’‘这个’‘上面提到的’‘第二个’等表达时，"
        "结合历史对话判断其指向，再从本轮课程资料中查找依据。"
        "不得因为历史回答中出现过某个结论，就默认该结论正确。\n"
        "【拒答规则】"
        "如果本轮课程资料没有足够依据回答当前问题，"
        "明确说明‘资料中未找到相关内容’，"
        "不要猜测、编造或用外部常识补全。\n"
        "【回答要求】"
        "优先直接回答问题；解释概念时条理清晰；"
        "涉及资料依据时使用对应的[资料1]、[资料2]标记。"
        "不得虚构资料编号、章节、页码或引用。\n\n"
        f"【本轮课程资料】\n{context}"
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
