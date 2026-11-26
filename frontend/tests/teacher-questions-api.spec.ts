import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import { resetApiClient } from '@/api/client'
import {
  getQuestionAnalyticsSummary,
  listHotQuestions,
  listKnowledgePointQuestionStats,
  listTeacherQuestionRecords,
} from '@/api/teacher-questions'

const COURSE_ID = '9007199254740993'
const POINT_ID = '9007199254740995'

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

describe('teacher questions api', () => {
  beforeEach(() => {
    resetApiClient()
  })

  afterEach(() => {
    vi.unstubAllGlobals()
    resetApiClient()
  })

  it('sends record filters and keeps BIGINT IDs as strings', async () => {
    const fetchMock = vi.fn()
      .mockImplementation(() => Promise.resolve(jsonResponse({ items: [] })))
    vi.stubGlobal('fetch', fetchMock)

    await listTeacherQuestionRecords(COURSE_ID, {
      pointId: POINT_ID,
      studentKeyword: '张三',
      keyword: '极限',
      startDate: '2025-03-01',
      endDate: '2025-03-31',
      page: 2,
      size: 15,
    })

    expect(fetchMock.mock.calls[0][0]).toBe(
      `/api/v1/teacher-questions/courses/${COURSE_ID}/records?pointId=${POINT_ID}&studentKeyword=%E5%BC%A0%E4%B8%89&keyword=%E6%9E%81%E9%99%90&startDate=2025-03-01&endDate=2025-03-31&page=2&size=15`,
    )
  })

  it('requests analytics data with the appropriate filters', async () => {
    const fetchMock = vi.fn()
      .mockImplementation(() => Promise.resolve(jsonResponse([])))
    vi.stubGlobal('fetch', fetchMock)

    await listHotQuestions(COURSE_ID, {
      pointId: POINT_ID,
      startDate: '2025-03-01',
      endDate: '2025-03-31',
    })
    await listKnowledgePointQuestionStats(COURSE_ID, {
      startDate: '2025-03-01',
      endDate: '2025-03-31',
    })
    await getQuestionAnalyticsSummary(COURSE_ID, {
      startDate: '2025-03-01',
      endDate: '2025-03-31',
    })

    expect(fetchMock.mock.calls.map(([url]) => String(url))).toEqual([
      `/api/v1/teacher-questions/courses/${COURSE_ID}/hot-questions?pointId=${POINT_ID}&startDate=2025-03-01&endDate=2025-03-31&limit=10`,
      `/api/v1/teacher-questions/courses/${COURSE_ID}/knowledge-points?startDate=2025-03-01&endDate=2025-03-31`,
      `/api/v1/teacher-questions/courses/${COURSE_ID}/summary?startDate=2025-03-01&endDate=2025-03-31`,
    ])
  })
})
