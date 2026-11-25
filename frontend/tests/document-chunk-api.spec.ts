import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import { resetApiClient } from '@/api/client'
import {
  assignChunkChapter,
  backfillDocumentChunks,
  chunkingVersionLabel,
  getDocumentChunk,
  listDocumentChunks,
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
    expect(chunkingVersionLabel(3)).toBe('未知切片算法')
  })
})
