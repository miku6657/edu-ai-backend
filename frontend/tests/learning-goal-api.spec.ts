import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import { resetApiClient } from '@/api/client'
import {
  confirmLearningGoal,
  getCourseLearningGoal,
  listLearningGoals,
  saveLearningGoalDraft,
} from '@/api/learning-goal'

const LARGE_ID = '9223372036854775807'

function jsonResponse(data: unknown): Response {
  return new Response(JSON.stringify({ code: 200, message: '操作成功', data }), {
    status: 200,
    headers: { 'Content-Type': 'application/json' },
  })
}

describe('learning goal API', () => {
  beforeEach(() => {
    resetApiClient()
  })

  afterEach(() => {
    vi.unstubAllGlobals()
    resetApiClient()
  })

  it('uses the learning-goal endpoints and preserves BIGINT IDs', async () => {
    const goal = {
      id: LARGE_ID,
      courseId: LARGE_ID,
      studentId: LARGE_ID,
      goalText: '掌握 Java 集合',
      weeklyHours: 6,
      targetDate: '2026-12-31',
      status: 'DRAFT' as const,
      confirmedAt: null,
      createTime: '2026-11-24T10:00:00',
      updateTime: '2026-11-24T10:00:00',
    }
    const confirmedGoal = {
      ...goal,
      status: 'CONFIRMED' as const,
      confirmedAt: '2026-11-24T10:01:00',
    }
    const request = {
      goalText: goal.goalText,
      weeklyHours: goal.weeklyHours,
      targetDate: goal.targetDate,
    }
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(jsonResponse([goal]))
      .mockResolvedValueOnce(jsonResponse(goal))
      .mockResolvedValueOnce(jsonResponse(goal))
      .mockResolvedValueOnce(jsonResponse(confirmedGoal))
    vi.stubGlobal('fetch', fetchMock)

    await expect(listLearningGoals()).resolves.toEqual([goal])
    await expect(getCourseLearningGoal(LARGE_ID)).resolves.toEqual(goal)
    await expect(
      saveLearningGoalDraft(LARGE_ID, request),
    ).resolves.toEqual(goal)
    await expect(confirmLearningGoal(LARGE_ID)).resolves.toEqual(confirmedGoal)

    expect(fetchMock.mock.calls.map(([url, init]) => ({
      url: String(url),
      method: (init as RequestInit | undefined)?.method,
      body: (init as RequestInit | undefined)?.body,
    }))).toEqual([
      { url: '/api/v1/learning-goals', method: 'GET', body: undefined },
      {
        url: `/api/v1/learning-goals/course/${LARGE_ID}`,
        method: 'GET',
        body: undefined,
      },
      {
        url: `/api/v1/learning-goals/course/${LARGE_ID}`,
        method: 'PUT',
        body: JSON.stringify(request),
      },
      {
        url: `/api/v1/learning-goals/${LARGE_ID}/confirm`,
        method: 'POST',
        body: undefined,
      },
    ])
  })
})
