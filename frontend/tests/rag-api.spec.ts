import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import { configureApiClient, resetApiClient } from '@/api/client'
import { ApiError } from '@/api/error'
import {
  clearMessages,
  createSseFrameParser,
  listMessages,
  parseSseFrameBlock,
  streamRagQa,
} from '@/api/rag'
import type { RagCitation, TokenPayload } from '@/api/rag'

function sseResponse(chunks: string[]): Response {
  const encoder = new TextEncoder()
  const stream = new ReadableStream<Uint8Array>({
    start(controller) {
      for (const chunk of chunks) {
        controller.enqueue(encoder.encode(chunk))
      }
      controller.close()
    },
  })
  return new Response(stream, {
    status: 200,
    headers: { 'Content-Type': 'text/event-stream; charset=utf-8' },
  })
}

function jsonResponse(data: unknown, status = 200): Response {
  return new Response(JSON.stringify({
    code: status === 200 ? 200 : 4001,
    message: status === 200 ? '操作成功' : '请求失败',
    data,
  }), {
    status,
    headers: { 'Content-Type': 'application/json' },
  })
}

describe('sse frame parsing', () => {
  it('解析 event/data 并忽略注释与心跳', () => {
    expect(parseSseFrameBlock(': ping\nevent: token\ndata: {"content":"x"}')).toEqual({
      event: 'token',
      data: '{"content":"x"}',
    })
    expect(parseSseFrameBlock(': ping')).toBeNull()
  })

  it('正确处理跨 chunk 与 CRLF', () => {
    const frames: Array<{ event: string; data: string }> = []
    const parser = createSseFrameParser((frame) => frames.push(frame))

    parser.push('event: token\r\ndata: {"cont')
    parser.push('ent":"x"}\r\n\r\n')
    parser.push('event: done\ndata: {"ok":true}\n\n')
    parser.flush()

    expect(frames).toEqual([
      { event: 'token', data: '{"content":"x"}' },
      { event: 'done', data: '{"ok":true}' },
    ])
  })
})

describe('Java chat API', () => {
  beforeEach(() => {
    resetApiClient()
    configureApiClient({ tokenProvider: () => 'jwt-token' })
  })

  afterEach(() => {
    vi.unstubAllGlobals()
    resetApiClient()
  })

  it('通过 Java 会话接口创建流并转换 session、sources、token、done 事件', async () => {
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(jsonResponse([]))
      .mockResolvedValueOnce(sseResponse([
        'event: session\ndata: {"sessionId":"301"}\n\n',
        'event: sources\ndata: {"sources":[{"documentId":501,"page":4}]}\n\n',
        'event: token\ndata: {"content":"答案"}\n\n',
        'event: done\ndata: {"sessionId":"301"}\n\n',
      ]))
    vi.stubGlobal('fetch', fetchMock)
    const tokens: TokenPayload[] = []
    const citations: RagCitation[][] = []
    let receivedSessionId: string | null = null
    let receivedDoneSessionId: string | null = null
    let done = false
    let closed = false

    await streamRagQa(
      { courseId: '2001', query: '问题', clientMsgId: 'ignored-client-id' },
      {
        onSession: (payload) => {
          receivedSessionId = payload.sessionId
        },
        onCitation: (payload) => citations.push(payload.citations),
        onToken: (payload) => tokens.push(payload),
        onDone: (payload) => {
          receivedDoneSessionId = payload.sessionId
          done = true
        },
        onClosed: () => {
          closed = true
        },
      },
    )

    expect(fetchMock).toHaveBeenCalledTimes(2)
    expect(fetchMock.mock.calls[0]?.[0]).toBe('/api/chat/sessions')
    expect(fetchMock.mock.calls[1]?.[0]).toBe('/api/chat/stream')
    const streamRequest = fetchMock.mock.calls[1] as [string, RequestInit]
    expect(streamRequest[1].method).toBe('POST')
    expect((streamRequest[1].headers as Record<string, string>).Authorization)
      .toBe('Bearer jwt-token')
    expect(streamRequest[1].body).toBe(JSON.stringify({
      courseId: '2001',
      sessionId: null,
      question: '问题',
    }))
    expect(citations[0]?.[0]).toMatchObject({
      doc_id: '501',
      doc_name: '文档 501',
      page_start: 4,
      page_end: 4,
      content: '',
    })
    expect(tokens).toEqual([{ delta: '答案', is_general_supplement: false }])
    expect(receivedSessionId).toBe('301')
    expect(receivedDoneSessionId).toBe('301')
    expect(done).toBe(true)
    expect(closed).toBe(true)
  })

  it('收到 done 事件后立即结束，不等待 HTTP 流自然关闭', async () => {
    const encoder = new TextEncoder()
    let cancelled = false

    const stream = new ReadableStream<Uint8Array>({
      start(controller) {
        controller.enqueue(
          encoder.encode(
            'event: done\n'
            + 'data: {"sessionId":"301"}\n\n',
          ),
        )
      },
      cancel() {
        cancelled = true
      },
    })

    const fetchMock = vi.fn()
      .mockResolvedValueOnce(jsonResponse([]))
      .mockResolvedValueOnce(
        new Response(stream, {
          status: 200,
          headers: {
            'Content-Type': 'text/event-stream',
          },
        }),
      )

    vi.stubGlobal('fetch', fetchMock)

    let doneReceived = false
    let closed = false

    await streamRagQa(
      {
        courseId: '2001',
        query: '什么是Java',
      },
      {
        onDone: () => {
          doneReceived = true
        },
        onClosed: () => {
          closed = true
        },
      },
    )

    expect(doneReceived).toBe(true)
    expect(closed).toBe(true)
    expect(cancelled).toBe(true)
  })

  it('校验并解析拒答 SSE 事件', async () => {
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(jsonResponse([]))
      .mockResolvedValueOnce(sseResponse([
        'event: rejection\ndata: {"user_message_id":"9007199254740993","notice_text":"未找到资料","reason":"NO_RETRIEVAL_MATCH"}\n\n',
        'event: rejection\ndata: {"user_message_id":12}\n\n',
        'event: done\ndata: {"sessionId":"702"}\n\n',
      ]))
    vi.stubGlobal('fetch', fetchMock)
    const rejections: Array<{
      user_message_id: string
      notice_text: string
      has_general_supplement: boolean
      reason?: string
    }> = []

    await streamRagQa(
      { courseId: '7002', query: '问题' },
      {
        onRejectionNotice: payload => rejections.push(payload),
      },
    )

    expect(rejections).toEqual([
      {
        user_message_id: '9007199254740993',
        notice_text: '未找到资料',
        has_general_supplement: false,
        reason: 'NO_RETRIEVAL_MATCH',
      },
      {
        user_message_id: '',
        notice_text: '资料中未找到相关内容。',
        has_general_supplement: false,
        reason: 'NO_RETRIEVAL_MATCH',
      },
    ])
  })

  it('解析 SSE 人工转交事件并校验工单信息', async () => {
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(jsonResponse([]))
      .mockResolvedValueOnce(sseResponse([
        'event: handoff\ndata: {"created":true,"ticketId":"9007199254740993","status":"PENDING","message":"问题已转交任课教师"}\n\n',
        'event: handoff\ndata: {"created":false,"ticketId":12,"status":"UNKNOWN","message":null}\n\n',
        'event: done\ndata: {"sessionId":"702"}\n\n',
      ]))
    vi.stubGlobal('fetch', fetchMock)
    const handoffs: Array<{
      created: boolean
      ticketId?: string
      status?: 'PENDING' | 'ANSWERED'
      message: string
    }> = []

    await streamRagQa(
      { courseId: '7002', query: '我需要教师帮助' },
      {
        onHandoff: payload => handoffs.push(payload),
      },
    )

    expect(handoffs).toEqual([
      {
        created: true,
        ticketId: '9007199254740993',
        status: 'PENDING',
        message: '问题已转交任课教师',
      },
      {
        created: false,
        ticketId: undefined,
        status: undefined,
        message: '答疑工单处理状态未知',
      },
    ])
  })

  it('完整解析 SSE 引用信息', async () => {
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(jsonResponse([]))
      .mockResolvedValueOnce(sseResponse([
        'event: session\ndata: {"sessionId":"701"}\n\n',
        'event: sources\ndata: {"sources":[{' +
          '"documentId":"9007199254740993",' +
          '"docName":"Java基础教程.pdf",' +
          '"page":12,' +
          '"pageStart":12,' +
          '"pageEnd":12,' +
          '"chunkIndex":2,' +
          '"parentChunkId":"9007199254740993:12:2",' +
          '"sectionTitle":"1.1.2 Java概述",' +
          '"content":"Java是一种面向对象的程序设计语言。",' +
          '"similarityScore":0.8721,' +
          '"citationIndex":1' +
          '}]}\n\n',
        'event: done\ndata: {"sessionId":"701"}\n\n',
      ]))
    vi.stubGlobal('fetch', fetchMock)

    const citations: RagCitation[][] = []

    await streamRagQa(
      {
        courseId: '7001',
        query: '什么是Java',
      },
      {
        onCitation: payload => citations.push(payload.citations),
      },
    )

    expect(citations[0]?.[0]).toMatchObject({
      parent_chunk_id: '9007199254740993:12:2',
      doc_id: '9007199254740993',
      doc_name: 'Java基础教程.pdf',
      page_start: 12,
      page_end: 12,
      section_title: '1.1.2 Java概述',
      content: 'Java是一种面向对象的程序设计语言。',
      similarity_score: 0.8721,
    })
  })

  it('继续当前课程最近更新的会话', async () => {
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(jsonResponse([]))
      .mockResolvedValueOnce(sseResponse([
        'event: session\ndata: {"sessionId":"301"}\n\n',
        'event: done\ndata: {"sessionId":"301"}\n\n',
      ]))
      .mockResolvedValueOnce(jsonResponse([
        {
          id: '301',
          courseId: '2001',
          userId: '1',
          title: '旧会话',
          createTime: '2026-01-01T10:00:00',
          updateTime: '2026-01-01T10:00:00',
        },
        {
          id: '9007199254740993',
          courseId: '2001',
          userId: '1',
          title: '最近会话',
          createTime: '2026-01-02T10:00:00',
          updateTime: '2026-01-03T10:00:00',
        },
      ]))
      .mockResolvedValueOnce(sseResponse(['event: done\ndata: {"sessionId":"302"}\n\n']))
    vi.stubGlobal('fetch', fetchMock)

    await streamRagQa({ courseId: '2001', query: '初次提问' }, {})
    await streamRagQa({ courseId: '2001', query: '继续' }, {})

    const streamRequest = fetchMock.mock.calls[3] as [string, RequestInit]
    const body = streamRequest[1].body
    if (typeof body !== 'string') {
      throw new Error('Expected the stream request to send a JSON body')
    }
    expect(JSON.parse(body)).toEqual({
      courseId: '2001',
      sessionId: '9007199254740993',
      question: '继续',
    })
  })

  it('历史记录读取更新时间最近的课程会话并转换 Java 字段', async () => {
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(jsonResponse([
        {
          id: '11',
          courseId: '2001',
          userId: '1',
          title: '旧会话',
          createTime: '2026-01-01T10:00:00',
          updateTime: '2026-01-01T10:00:00',
        },
        {
          id: '12',
          courseId: '2001',
          userId: '1',
          title: '最近会话',
          createTime: '2026-01-02T10:00:00',
          updateTime: '2026-01-03T10:00:00',
        },
        {
          id: '13',
          courseId: '2002',
          userId: '1',
          title: '其他课程',
          createTime: '2026-01-04T10:00:00',
          updateTime: '2026-01-04T10:00:00',
        },
      ]))
      .mockResolvedValueOnce(jsonResponse([
        {
          id: '901',
          sessionId: '12',
          role: 'user',
          content: '问题',
          sourcesJson: null,
          createTime: '2026-01-03T10:00:00',
        },
        {
          id: '902',
          sessionId: '12',
          role: 'assistant',
          content: '回答',
          sourcesJson: '[{"documentId":501,"page":4}]',
          createTime: '2026-01-03T10:00:01',
          noEvidenceReason: 'NO_RETRIEVAL_MATCH',
          questionMessageId: '901',
          ticketId: '9007199254740995',
          ticketStatus: 'PENDING',
        },
      ]))
    vi.stubGlobal('fetch', fetchMock)

    const page = await listMessages('2001')

    expect(fetchMock.mock.calls[0]?.[0]).toBe('/api/chat/sessions')
    expect(fetchMock.mock.calls[1]?.[0]).toBe('/api/chat/sessions/12/messages')
    expect(page).toMatchObject({
      next_cursor: null,
      has_more: false,
      items: [
        { message_id: '901', role: 'USER', course_id: null },
        {
          message_id: '902',
          role: 'ASSISTANT',
          is_rejected: true,
          citations: [{ doc_id: '501', page_start: 4 }],
          question_message_id: '901',
          no_evidence_reason: 'NO_RETRIEVAL_MATCH',
          ticket_id: '9007199254740995',
          ticket_status: 'PENDING',
        },
      ],
    })
  })

  it('清空课程时删除该课程的所有会话和消息', async () => {
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(jsonResponse([
        { id: '21', courseId: '2001', userId: '1', title: '1', createTime: '', updateTime: '2' },
        { id: '22', courseId: '2001', userId: '1', title: '2', createTime: '', updateTime: '1' },
        { id: '23', courseId: '2002', userId: '1', title: '3', createTime: '', updateTime: '3' },
      ]))
      .mockResolvedValueOnce(jsonResponse([{ id: 1 }]))
      .mockResolvedValueOnce(jsonResponse('deleted'))
      .mockResolvedValueOnce(jsonResponse([{ id: 2 }, { id: 3 }]))
      .mockResolvedValueOnce(jsonResponse('deleted'))
    vi.stubGlobal('fetch', fetchMock)

    await expect(clearMessages('2001')).resolves.toEqual({
      course_id: '2001',
      deleted_count: 3,
    })
    expect(fetchMock.mock.calls[0]?.[0]).toBe('/api/chat/sessions')
    expect(fetchMock.mock.calls[1]?.[0]).toBe('/api/chat/sessions/21/messages')
    expect(fetchMock.mock.calls[2]?.[0]).toBe('/api/chat/sessions/21')
    expect(fetchMock.mock.calls[3]?.[0]).toBe('/api/chat/sessions/22/messages')
    expect(fetchMock.mock.calls[4]?.[0]).toBe('/api/chat/sessions/22')
    expect((fetchMock.mock.calls[2]?.[1] as RequestInit).method).toBe('DELETE')
    expect((fetchMock.mock.calls[4]?.[1] as RequestInit).method).toBe('DELETE')
  })

  it('流式请求 4xx 信封保留后端错误码', async () => {
    vi.stubGlobal('fetch', vi.fn()
      .mockResolvedValueOnce(jsonResponse([]))
      .mockResolvedValueOnce(jsonResponse(null, 400)))

    await expect(streamRagQa({ courseId: '2001', query: '问题' }, {}))
      .rejects.toMatchObject({ code: '4001' })
  })

  it('流内非法 JSON 停止处理', async () => {
    vi.stubGlobal('fetch', vi.fn()
      .mockResolvedValueOnce(jsonResponse([]))
      .mockResolvedValueOnce(sseResponse(['event: token\ndata: not-json\n\n'])))

    await expect(streamRagQa({ courseId: '2001', query: '问题' }, {}))
      .rejects.toBeInstanceOf(ApiError)
  })

  it('拒绝以 number 发送的 SSE 会话 ID，避免 BIGINT 精度丢失', async () => {
    vi.stubGlobal('fetch', vi.fn()
      .mockResolvedValueOnce(jsonResponse([]))
      .mockResolvedValueOnce(sseResponse([
        'event: session\ndata: {"sessionId":9007199254740993}\n\n',
      ])))

    await expect(streamRagQa({ courseId: '2001', query: '问题' }, {}))
      .rejects.toMatchObject({ code: 'CLIENT_MALFORMED_RESPONSE' })
  })

  it('兼容字符串类型的 RAG 引用 ID', async () => {
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(jsonResponse([]))
      .mockResolvedValueOnce(sseResponse([
        'event: session\ndata: {"sessionId":"701"}\n\n',
        'event: sources\ndata: {"sources":[{"documentId":"9007199254740993","page":4}]}\n\n',
        'event: token\ndata: {"content":"测试回答"}\n\n',
        'event: done\ndata: {"sessionId":"701"}\n\n',
      ]))

    vi.stubGlobal('fetch', fetchMock)

    const citations: RagCitation[][] = []

    await streamRagQa(
      {
        courseId: '7001',
        query: '测试文档引用',
      },
      {
        onCitation: payload => citations.push(payload.citations),
      },
    )

    expect(citations[0]?.[0]).toMatchObject({
      doc_id: '9007199254740993',
      page_start: 4,
      page_end: 4,
    })
  })

  it('历史消息能够恢复引用版本信息', async () => {
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(jsonResponse([
        {
          id: '801',
          courseId: '8001',
          userId: '4',
          title: '历史课程问答',
          createTime: '2026-11-25T10:00:00',
          updateTime: '2026-11-25T10:00:00',
        },
      ]))
      .mockResolvedValueOnce(jsonResponse([
        {
          id: '901',
          sessionId: '801',
          role: 'assistant',
          content: '期末考试占比为60%。',
          sourcesJson: JSON.stringify([
            {
              documentId: '101',
              page: 3,
              pageStart: 2,
              pageEnd: 4,
              docName: '课程考核说明.pdf',
              chunkIndex: 1,
              parentChunkId: '101:2:1',
              sectionTitle: '考核方式',
              content: '期末考试占比为60%。',
              similarityScore: 0.91,
            },
          ]),
          citationVersions: [
            {
              documentId: '101',
              documentName: '课程考核说明.pdf',
              resourceId: '101',
              versionNo: 1,
              versionStatus: 'HISTORY',
              currentDocumentId: '102',
              currentDocumentName: '课程考核说明V2.pdf',
              currentVersionNo: 2,
              outdated: true,
            },
          ],
          createTime: '2026-11-25T10:00:00',
        },
      ]))

    vi.stubGlobal('fetch', fetchMock)

    const result = await listMessages('8001')

    expect(result.items[0]?.citations?.[0]).toMatchObject({
      doc_id: '101',
      doc_name: '课程考核说明.pdf',
      parent_chunk_id: '101:2:1',
      page_start: 2,
      page_end: 4,
      section_title: '考核方式',
      content: '期末考试占比为60%。',
      similarity_score: 0.91,
      version: {
        outdated: true,
        versionNo: 1,
        currentVersionNo: 2,
        currentDocumentId: '102',
      },
    })
  })
})
