import { apiRequest, apiStream } from '@/api/client'
import { ApiError, CLIENT_ERROR_CODES } from '@/api/error'
import type { BigIntId } from '@/api/types'

/** Java ChatSession DTO。 */
export interface ChatSession {
  id: BigIntId
  courseId: BigIntId
  userId: BigIntId
  title: string
  createTime: string
  updateTime: string
}

/** Java ChatMessage DTO。 */
export interface ChatMessage {
  id: BigIntId
  sessionId: BigIntId
  role: 'user' | 'assistant'
  content: string
  sourcesJson: string | null
  createTime: string
}

/** 历史消息的页面展示模型。 */
export interface ChatHistoryMessage {
  message_id: BigIntId
  course_id: BigIntId | null
  platform: string
  role: 'USER' | 'ASSISTANT' | 'SYSTEM'
  intent_type: string | null
  content: string
  citations: RagCitation[] | null
  is_rejected: boolean
  trace_id: string | null
  retrieval_cost_ms: number | null
  llm_first_token_ms: number | null
  created_at: string
}

/**
 * RAG 问答与会话历史 API（`04` §4.5 `API-RAG-001` / `API-CHAT-001/002`）。
 *
 * SSE 使用 `fetch + ReadableStream`，因为浏览器 `EventSource` 不支持 `Authorization` 头。
 */

export interface RagCitation {
  parent_chunk_id: string
  doc_id: BigIntId
  doc_name: string
  page_start: number
  page_end: number
  section_title: string | null
  content: string
  similarity_score?: number | null
}

export interface CitationPayload {
  user_message_id: string
  course_index_status: string
  rebuild_progress_note: string | null
  citations: RagCitation[]
}

export interface RejectionNoticePayload {
  user_message_id: string
  notice_text: string
  has_general_supplement: boolean
}

export interface TokenPayload {
  delta: string
  is_general_supplement: boolean
}

export interface DonePayload {
  sessionId: BigIntId
}

export interface SessionPayload {
  sessionId: BigIntId
}

export interface RagErrorPayload {
  code: string
  message: string
  trace_id: string
}

export interface RagStreamHandlers {
  onSession?: (payload: SessionPayload) => void
  onCitation?: (payload: CitationPayload) => void
  onRejectionNotice?: (payload: RejectionNoticePayload) => void
  onToken?: (payload: TokenPayload) => void
  onDone?: (payload: DonePayload) => void
  onErrorEvent?: (payload: RagErrorPayload) => void
  /** 流自然结束（不论是否收到 done）；断流由调用方根据是否收到 done 判断。 */
  onClosed?: () => void
}

export interface RagStreamRequest {
  courseId: BigIntId
  query: string
  clientMsgId?: string
}

export interface ChatMessagePage {
  items: ChatHistoryMessage[]
  next_cursor: string | null
  has_more: boolean
}

export interface ClearMessagesResult {
  course_id: BigIntId
  deleted_count: number
}

export interface ParsedSseFrame {
  event: string
  data: string
}

interface JavaSource {
  documentId: number
  page: number
}

const sessionCache = new Map<string, BigIntId>()

function normalizeId(id: BigIntId): string {
  if (!/^[1-9]\d*$/.test(id)) {
    throw new Error('课程 ID 不合法')
  }
  return id
}

function requireSessionId(value: unknown): BigIntId {
  if (typeof value !== 'string' || !/^[1-9]\d*$/.test(value)) {
    throw new ApiError({
      code: CLIENT_ERROR_CODES.MALFORMED_RESPONSE,
      message: '服务端 SSE 事件缺少有效会话 ID。',
    })
  }
  return value
}

function sourceToCitation(source: JavaSource): RagCitation {
  return {
    parent_chunk_id: `${source.documentId}-${source.page}`,
    doc_id: String(source.documentId),
    doc_name: `文档 ${source.documentId}`,
    page_start: source.page,
    page_end: source.page,
    section_title: null,
    content: '',
    similarity_score: null,
  }
}

function isJavaSource(value: unknown): value is JavaSource {
  if (typeof value !== 'object' || value === null) {
    return false
  }
  const source = value as Record<string, unknown>
  return typeof source.documentId === 'number' && typeof source.page === 'number'
}

function parseSources(json: string | null): RagCitation[] {
  if (!json) {
    return []
  }
  try {
    const parsed: unknown = JSON.parse(json)
    return Array.isArray(parsed)
      ? parsed.filter(isJavaSource).map(sourceToCitation)
      : []
  } catch {
    return []
  }
}

function toChatHistoryMessage(item: ChatMessage): ChatHistoryMessage {
  return {
    message_id: item.id,
    course_id: null,
    platform: 'EDU-05',
    role: item.role === 'assistant' ? 'ASSISTANT' : 'USER',
    intent_type: null,
    content: item.content,
    citations: parseSources(item.sourcesJson),
    is_rejected: false,
    trace_id: null,
    retrieval_cost_ms: null,
    llm_first_token_ms: null,
    created_at: item.createTime,
  }
}

async function sessionsForCourse(courseId: string): Promise<ChatSession[]> {
  const sessions = await apiRequest<ChatSession[]>('/chat/sessions')
  return sessions
    .filter((item) => item.courseId === courseId)
    .sort((a, b) => b.updateTime.localeCompare(a.updateTime))
}

async function resolveSessionId(courseId: string): Promise<BigIntId | null> {
  const sessions = await sessionsForCourse(courseId)
  const cached = sessionCache.get(courseId)
  const latestId = sessions[0]?.id ?? null
  if (cached !== undefined && cached === latestId) {
    return cached
  }

  if (latestId === null) {
    sessionCache.delete(courseId)
  } else {
    sessionCache.set(courseId, latestId)
  }
  return latestId
}

/** 解析一个 SSE 帧块；无 data 返回 null。 */
export function parseSseFrameBlock(raw: string): ParsedSseFrame | null {
  let event = 'message'
  const dataLines: string[] = []
  for (const line of raw.split('\n')) {
    if (line === '' || line.startsWith(':')) {
      continue
    }
    const colon = line.indexOf(':')
    const field = colon === -1 ? line : line.slice(0, colon)
    let value = colon === -1 ? '' : line.slice(colon + 1)
    if (value.startsWith(' ')) {
      value = value.slice(1)
    }
    if (field === 'event') {
      event = value
    } else if (field === 'data') {
      dataLines.push(value)
    }
  }
  if (dataLines.length === 0) {
    return null
  }
  return { event, data: dataLines.join('\n') }
}

/** 增量 SSE 解析器：正确处理跨 chunk 的帧与 CRLF。 */
export function createSseFrameParser(onFrame: (frame: ParsedSseFrame) => void): {
  push: (chunk: string) => void
  flush: () => void
} {
  let buffer = ''
  const emit = (raw: string): void => {
    const frame = parseSseFrameBlock(raw)
    if (frame !== null) {
      onFrame(frame)
    }
  }
  return {
    push(chunk: string): void {
      buffer += chunk.replace(/\r\n/g, '\n')
      let index = buffer.indexOf('\n\n')
      while (index !== -1) {
        emit(buffer.slice(0, index))
        buffer = buffer.slice(index + 2)
        index = buffer.indexOf('\n\n')
      }
    },
    flush(): void {
      if (buffer.trim() !== '') {
        emit(buffer)
      }
      buffer = ''
    },
  }
}

/**
 * 发起 Java 聊天 SSE 问答并逐帧回调。
 */
export async function streamRagQa(
  request: RagStreamRequest,
  handlers: RagStreamHandlers,
  signal?: AbortSignal,
): Promise<void> {
  const courseId = normalizeId(request.courseId)
  const sessionId = await resolveSessionId(courseId)

  if (signal?.aborted) {
    throw new DOMException('请求已取消', 'AbortError')
  }

  const response = await apiStream('/chat/stream', {
    method: 'POST',
    body: {
      courseId,
      sessionId,
      question: request.query,
    },
    signal,
  })
  if (response.body === null) {
    throw new ApiError({
      code: CLIENT_ERROR_CODES.MALFORMED_RESPONSE,
      message: '服务端未返回 SSE 流。',
      httpStatus: response.status,
    })
  }

  const reader = response.body.getReader()
  const decoder = new TextDecoder('utf-8')
  let completed = false
  let upstreamError = false
  const parser = createSseFrameParser((frame) => {
    let payload: Record<string, unknown>
    try {
      const parsed: unknown = JSON.parse(frame.data)
      if (typeof parsed !== 'object' || parsed === null || Array.isArray(parsed)) {
        throw new Error('SSE payload must be an object')
      }
      payload = parsed as Record<string, unknown>
    } catch (error) {
      throw new ApiError({
        code: CLIENT_ERROR_CODES.MALFORMED_RESPONSE,
        message: '服务端 SSE 数据格式错误。',
        cause: error,
      })
    }

    if (frame.event === 'session') {
      const sessionId = requireSessionId(payload.sessionId)
      sessionCache.set(courseId, sessionId)
      handlers.onSession?.({ sessionId })
    } else if (frame.event === 'sources') {
      const sources = Array.isArray(payload.sources)
        ? payload.sources.filter(isJavaSource).map(sourceToCitation)
        : []
      handlers.onCitation?.({
        user_message_id: '',
        course_index_status: 'UNKNOWN',
        rebuild_progress_note: null,
        citations: sources,
      })
    } else if (frame.event === 'token') {
      handlers.onToken?.({
        delta: typeof payload.content === 'string' ? payload.content : '',
        is_general_supplement: false,
      })
    } else if (frame.event === 'error') {
      upstreamError = true
      handlers.onErrorEvent?.({
        code: 'AI_STREAM_ERROR',
        message: typeof payload.message === 'string' && payload.message !== ''
          ? payload.message
          : 'AI 回答失败',
        trace_id: '',
      })
    } else if (frame.event === 'done') {
      const sessionId = requireSessionId(payload.sessionId)
      sessionCache.set(courseId, sessionId)
      completed = true
      handlers.onDone?.({ sessionId })
    }
  })
  try {
    for (;;) {
      const { done, value } = await reader.read()
      if (done) {
        break
      }
      parser.push(decoder.decode(value, { stream: true }))
    }
    parser.push(decoder.decode())
    parser.flush()
    if (!completed && !upstreamError && !signal?.aborted) {
      handlers.onErrorEvent?.({
        code: 'STREAM_INTERRUPTED',
        message: '连接结束，但未收到完成事件',
        trace_id: '',
      })
    }
    handlers.onClosed?.()
  } finally {
    reader.releaseLock()
  }
}

export async function listMessages(
  courseId: BigIntId,
  _beforeMessageId?: BigIntId | null,
  _limit = 20,
): Promise<ChatMessagePage> {
  void _beforeMessageId
  void _limit
  const id = await resolveSessionId(normalizeId(courseId))
  if (id === null) {
    return { items: [], next_cursor: null, has_more: false }
  }

  const messages = await apiRequest<ChatMessage[]>(
    `/chat/sessions/${encodeURIComponent(id)}/messages`,
  )
  return {
    items: messages.map(toChatHistoryMessage),
    next_cursor: null,
    has_more: false,
  }
}

export async function clearMessages(
  courseId: BigIntId,
): Promise<ClearMessagesResult> {
  const id = normalizeId(courseId)
  const sessions = await sessionsForCourse(id)
  let deleted = 0

  for (const session of sessions) {
    const messages = await apiRequest<ChatMessage[]>(
      `/chat/sessions/${encodeURIComponent(session.id)}/messages`,
    )
    await apiRequest<string>(
      `/chat/sessions/${encodeURIComponent(session.id)}`,
      { method: 'DELETE' },
    )
    deleted += messages.length
  }

  sessionCache.delete(id)
  return { course_id: id, deleted_count: deleted }
}
