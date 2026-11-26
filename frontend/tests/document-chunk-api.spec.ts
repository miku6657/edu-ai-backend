import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import { resetApiClient } from '@/api/client'
import {
  assignChunkChapter,
  backfillDocumentChunks,
  chunkingVersionLabel,
  chunkKnowledgeStatusLabel,
  chunkKnowledgeStatusTagType,
  decideChunkKnowledgeRelations,
  getChunkKnowledgeDetail,
  getChunkKnowledgeProgress,
  getDocumentChunk,
  knowledgeConfirmStatusLabel,
  knowledgeConfirmStatusTagType,
  listDocumentChunks,
  markChunkKnowledgeNotApplicable,
  recommendChunkKnowledge,
  replaceChunkKnowledgePoints,
  resetChunkKnowledge,
} from '@/api/document-chunk'

const COURSE_ID = '9007199254740993'
const DOCUMENT_ID = '9007199254740994'
const CHUNK_ID = '9007199254740996'

function jsonResponse(data: unknown): Response {
  return new Response(JSON.stringify({
    code: 200,
    message: '操作成功',
    data,
  }), {
    status: 200,
    headers: { 'Content-Type': 'application/json' },
  })
}

describe('document chunk api', () => {
  beforeEach(() => {
    resetApiClient()
  })

  afterEach(() => {
    vi.unstubAllGlobals()
    resetApiClient()
  })

  it('lists, reads, classifies, and backfills chunks using string BIGINT IDs', async () => {
    const chunk = {
      id: CHUNK_ID,
      courseId: COURSE_ID,
      documentId: DOCUMENT_ID,
      chunkKey: 'chunk-0',
      pageNo: 3,
      chunkIndex: 0,
      chapterId: null,
      chapterName: null,
      sectionTitle: '集合基础',
      content: 'HashMap stores key-value pairs.',
      contentHash: 'sha256-hash',
      charCount: 33,
    }
    const page = {
      documentId: DOCUMENT_ID,
      documentName: 'Java集合.pdf',
      versionStatus: 'CURRENT',
      chunkingVersion: 2,
      initialized: true,
      totalChunks: 1,
      classifiedChunks: 0,
      total: 1,
      page: 2,
      pageSize: 10,
      records: [chunk],
    }
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(jsonResponse(page))
      .mockResolvedValueOnce(jsonResponse(chunk))
      .mockResolvedValueOnce(jsonResponse({ updatedChunks: 1 }))
      .mockResolvedValueOnce(jsonResponse({
        documentId: DOCUMENT_ID,
        chunkCount: 4,
        alreadyInitialized: false,
      }))
    vi.stubGlobal('fetch', fetchMock)

    await expect(listDocumentChunks(COURSE_ID, DOCUMENT_ID, {
      keyword: '  HashMap tree  ',
      pageNo: 3,
      chapterId: '9007199254740995',
      unclassified: true,
      page: 2,
      pageSize: 10,
    })).resolves.toEqual(page)
    await expect(getDocumentChunk(
      COURSE_ID,
      DOCUMENT_ID,
      CHUNK_ID,
    )).resolves.toEqual(chunk)
    await expect(assignChunkChapter(
      COURSE_ID,
      DOCUMENT_ID,
      {
        chunkIds: [CHUNK_ID],
        chapterId: '9007199254740995',
      },
    )).resolves.toEqual({ updatedChunks: 1 })
    await expect(backfillDocumentChunks(
      COURSE_ID,
      DOCUMENT_ID,
    )).resolves.toEqual({
      documentId: DOCUMENT_ID,
      chunkCount: 4,
      alreadyInitialized: false,
    })

    expect(fetchMock.mock.calls.map(([url, init]) => ({
      url: String(url),
      method: (init as RequestInit | undefined)?.method,
      body: (init as RequestInit | undefined)?.body,
    }))).toEqual([
      {
        url: `/api/v1/document-chunks/courses/${COURSE_ID}/documents/${DOCUMENT_ID}?keyword=HashMap+tree&pageNo=3&chapterId=9007199254740995&unclassified=true&page=2&pageSize=10`,
        method: 'GET',
        body: undefined,
      },
      {
        url: `/api/v1/document-chunks/courses/${COURSE_ID}/documents/${DOCUMENT_ID}/${CHUNK_ID}`,
        method: 'GET',
        body: undefined,
      },
      {
        url: `/api/v1/document-chunks/courses/${COURSE_ID}/documents/${DOCUMENT_ID}/chapter`,
        method: 'PUT',
        body: JSON.stringify({
          chunkIds: [CHUNK_ID],
          chapterId: '9007199254740995',
        }),
      },
      {
        url: `/api/v1/document-chunks/courses/${COURSE_ID}/documents/${DOCUMENT_ID}/backfill`,
        method: 'POST',
        body: undefined,
      },
    ])
    expect(chunkingVersionLabel(1)).toBe('固定窗口切片')
    expect(chunkingVersionLabel(2)).toBe('段落感知切片')
    expect(chunkingVersionLabel(3)).toBe('章节聚合切片')
  })

  it('calls knowledge association endpoints with string BIGINT IDs', async () => {
    const fetchMock = vi.fn()
      .mockImplementation(() => Promise.resolve(
        jsonResponse({ processedCount: 1 }),
      ))
    vi.stubGlobal('fetch', fetchMock)

    await recommendChunkKnowledge(COURSE_ID, DOCUMENT_ID, {
      chunkIds: [CHUNK_ID],
    })
    await getChunkKnowledgeDetail(COURSE_ID, DOCUMENT_ID, CHUNK_ID)
    await decideChunkKnowledgeRelations(COURSE_ID, DOCUMENT_ID, {
      relationIds: [CHUNK_ID],
      decision: 'CONFIRM',
    })
    await replaceChunkKnowledgePoints(COURSE_ID, DOCUMENT_ID, CHUNK_ID, {
      pointIds: ['9007199254740997'],
    })
    await markChunkKnowledgeNotApplicable(COURSE_ID, DOCUMENT_ID, {
      chunkIds: [CHUNK_ID],
    })
    await resetChunkKnowledge(COURSE_ID, DOCUMENT_ID, {
      chunkIds: [CHUNK_ID],
    })
    await getChunkKnowledgeProgress(COURSE_ID, DOCUMENT_ID)

    expect(fetchMock.mock.calls.map(([url, init]) => ({
      url: String(url),
      method: (init as RequestInit | undefined)?.method,
      body: (init as RequestInit | undefined)?.body,
    }))).toEqual([
      {
        url: `/api/v1/document-chunks/courses/${COURSE_ID}/documents/${DOCUMENT_ID}/knowledge/recommend`,
        method: 'POST',
        body: JSON.stringify({ chunkIds: [CHUNK_ID] }),
      },
      {
        url: `/api/v1/document-chunks/courses/${COURSE_ID}/documents/${DOCUMENT_ID}/knowledge/chunks/${CHUNK_ID}`,
        method: 'GET',
        body: undefined,
      },
      {
        url: `/api/v1/document-chunks/courses/${COURSE_ID}/documents/${DOCUMENT_ID}/knowledge/decisions`,
        method: 'POST',
        body: JSON.stringify({
          relationIds: [CHUNK_ID],
          decision: 'CONFIRM',
        }),
      },
      {
        url: `/api/v1/document-chunks/courses/${COURSE_ID}/documents/${DOCUMENT_ID}/knowledge/chunks/${CHUNK_ID}/points`,
        method: 'PUT',
        body: JSON.stringify({ pointIds: ['9007199254740997'] }),
      },
      {
        url: `/api/v1/document-chunks/courses/${COURSE_ID}/documents/${DOCUMENT_ID}/knowledge/not-applicable`,
        method: 'POST',
        body: JSON.stringify({ chunkIds: [CHUNK_ID] }),
      },
      {
        url: `/api/v1/document-chunks/courses/${COURSE_ID}/documents/${DOCUMENT_ID}/knowledge/reset`,
        method: 'POST',
        body: JSON.stringify({ chunkIds: [CHUNK_ID] }),
      },
      {
        url: `/api/v1/document-chunks/courses/${COURSE_ID}/documents/${DOCUMENT_ID}/knowledge/progress`,
        method: 'GET',
        body: undefined,
      },
    ])
  })

  it('maps knowledge association statuses to labels and tag types', () => {
    expect(chunkKnowledgeStatusLabel('UNPROCESSED')).toBe('未处理')
    expect(chunkKnowledgeStatusLabel('RECOMMENDED')).toBe('待处理')
    expect(chunkKnowledgeStatusLabel('CONFIRMED')).toBe('已确认')
    expect(chunkKnowledgeStatusLabel('NOT_APPLICABLE')).toBe('无需关联')
    expect(chunkKnowledgeStatusLabel('UNKNOWN')).toBe('未知状态')

    expect(chunkKnowledgeStatusTagType('RECOMMENDED')).toBe('warning')
    expect(chunkKnowledgeStatusTagType('CONFIRMED')).toBe('success')
    expect(chunkKnowledgeStatusTagType('NOT_APPLICABLE')).toBe('info')
    expect(chunkKnowledgeStatusTagType('UNPROCESSED')).toBe('info')

    expect(knowledgeConfirmStatusLabel('PENDING')).toBe('待确认')
    expect(knowledgeConfirmStatusLabel('CONFIRMED')).toBe('已确认')
    expect(knowledgeConfirmStatusLabel('REJECTED')).toBe('已拒绝')
    expect(knowledgeConfirmStatusLabel('UNKNOWN')).toBe('未知状态')

    expect(knowledgeConfirmStatusTagType('PENDING')).toBe('warning')
    expect(knowledgeConfirmStatusTagType('CONFIRMED')).toBe('success')
    expect(knowledgeConfirmStatusTagType('REJECTED')).toBe('danger')
    expect(knowledgeConfirmStatusTagType('UNKNOWN')).toBe('info')
  })
})
