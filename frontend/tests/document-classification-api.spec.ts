import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import {
  batchAssignDocumentTags,
  createCourseChapter,
  getDocumentTags,
  listDocumentClassifications,
  listCourseChapters,
} from '@/api/document-classification'
import { resetApiClient } from '@/api/client'

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

describe('document classification api', () => {
  beforeEach(() => {
    resetApiClient()
  })

  afterEach(() => {
    vi.unstubAllGlobals()
    resetApiClient()
  })

  it('uses the course chapter endpoints and keeps BIGINT identifiers as strings', async () => {
    const courseId = '9007199254740993'
    const documentId = '9007199254740994'
    const chapterId = '9007199254740995'
    const pointId = '9007199254740996'
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(jsonResponse([{
        id: chapterId,
        name: '第一章',
        sortOrder: 1,
      }]))
      .mockResolvedValueOnce(jsonResponse('章节创建成功'))
      .mockResolvedValueOnce(jsonResponse({
        documentId,
        chapterIds: [chapterId],
        pointIds: [pointId],
      }))
      .mockResolvedValueOnce(jsonResponse({ updatedDocuments: 1 }))
      .mockResolvedValueOnce(jsonResponse([{
        documentId,
        chapters: [{ id: chapterId, name: '第一章' }],
        points: [{ id: pointId, name: 'Java集合' }],
      }]))
    vi.stubGlobal('fetch', fetchMock)

    await expect(listCourseChapters(courseId)).resolves.toEqual([{
      id: chapterId,
      name: '第一章',
      sortOrder: 1,
    }])
    await expect(createCourseChapter(courseId, {
      name: '第一章',
      sortOrder: 1,
    })).resolves.toBe('章节创建成功')
    await expect(getDocumentTags(courseId, documentId)).resolves.toEqual({
      documentId,
      chapterIds: [chapterId],
      pointIds: [pointId],
    })
    await expect(batchAssignDocumentTags(courseId, {
      documentIds: [documentId],
      chapterIds: [chapterId],
      pointIds: [pointId],
    })).resolves.toEqual({ updatedDocuments: 1 })
    await expect(listDocumentClassifications(courseId)).resolves.toEqual([{
      documentId,
      chapters: [{ id: chapterId, name: '第一章' }],
      points: [{ id: pointId, name: 'Java集合' }],
    }])

    expect(fetchMock.mock.calls.map(([url]) => String(url))).toEqual([
      `/api/v1/document-classification/courses/${courseId}/chapters`,
      `/api/v1/document-classification/courses/${courseId}/chapters`,
      `/api/v1/document-classification/courses/${courseId}/documents/${documentId}/tags`,
      `/api/v1/document-classification/courses/${courseId}/documents/tags`,
      `/api/v1/document-classification/courses/${courseId}/documents/tags`,
    ])
    const batchRequest = fetchMock.mock.calls[3]?.[1] as RequestInit | undefined
    expect(batchRequest?.method).toBe('PUT')
    expect(batchRequest?.body).toBe(JSON.stringify({
      documentIds: [documentId],
      chapterIds: [chapterId],
      pointIds: [pointId],
    }))
  })
})
