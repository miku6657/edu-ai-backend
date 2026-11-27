import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import { resetApiClient } from '@/api/client'
import {
  addKnowledgeDependency,
  addKnowledgePoint,
  applyLearningPathAdjustment,
  deleteKnowledgePoint,
  generateLearningPath,
  getCourseKnowledgeMap,
  getLearningPath,
  getLearningPathByGoal,
  getLearningPathProgress,
  getMyKnowledgeMastery,
  getStudentWeakPointDiagnosis,
  knowledgeDifficultyLabel,
  listKnowledgePoints,
  listWeakKnowledgePoints,
  previewLearningPathAdjustment,
  previewWeakReview,
  previewWeakReviewPlan,
  applyWeakReviewPlan,
  removeKnowledgeDependency,
  updateKnowledgeMastery,
  updateKnowledgePoint,
  updateKnowledgePointKeywords,
} from '@/api/learning-path'

const LARGE_ID = '9223372036854775807'

function jsonResponse(data: unknown): Response {
  return new Response(JSON.stringify({ code: 200, message: '操作成功', data }), {
    status: 200,
    headers: { 'Content-Type': 'application/json' },
  })
}

describe('learning path API', () => {
  beforeEach(() => {
    resetApiClient()
  })

  afterEach(() => {
    vi.unstubAllGlobals()
    resetApiClient()
  })

  it('uses the learning-path endpoints and preserves BIGINT IDs', async () => {
    const point = {
      id: LARGE_ID,
      courseId: LARGE_ID,
      title: 'Java 集合',
      chapterId: null,
      parentId: null,
      difficulty: 2,
      matchKeywords: 'HashMap,ArrayList',
      estimatedMinutes: 120,
      sortOrder: 1,
    }
    const weakPoint = {
      pointId: LARGE_ID,
      title: point.title,
      effectiveCount: 8,
      sessionCount: 3,
      inferredLevel: 1,
    }
    const path = {
      pathId: LARGE_ID,
      goalId: LARGE_ID,
      courseId: LARGE_ID,
      totalMinutes: 120,
      totalWeeks: 1,
      status: 'GENERATED',
      items: [{
        id: LARGE_ID,
        pointId: LARGE_ID,
        title: point.title,
        segmentNo: 1,
        sequenceNo: 1,
        weekNo: 1,
        plannedMinutes: 120,
        plannedDueDate: '2026-12-01',
        status: 'PENDING',
      }],
    }
    const progress = {
      total: 1,
      completed: 0,
      percentage: 0,
    }
    const adjustment = {
      feasible: true,
      fingerprint: 'preview-123',
      message: '可以调整学习计划',
      changedCount: 1,
      items: [{
        itemId: LARGE_ID,
        oldWeek: 1,
        newWeek: 2,
        oldDueDate: '2026-12-01',
        newDueDate: '2026-12-08',
      }],
    }
    const weakReview = {
      pathId: LARGE_ID,
      additionalTotalMinutes: 45,
      message: '建议增加复习时间',
      items: [{
        pointId: LARGE_ID,
        title: point.title,
        effectiveCount: 8,
        sessionCount: 3,
        manualLevel: 2,
        inferredLevel: 1,
        currentMinutes: 60,
        recommendedMinutes: 105,
        additionalMinutes: 45,
      }],
    }
    const weakReviewPlan = {
      feasible: true,
      fingerprint: 'weak-review-456',
      message: '复习计划可以执行',
      additionalMinutes: 45,
      moves: [{
        itemId: LARGE_ID,
        oldWeek: 1,
        newWeek: 2,
        oldDueDate: '2026-12-01',
        newDueDate: '2026-12-08',
      }],
      additions: [{
        pointId: LARGE_ID,
        title: point.title,
        segmentNo: 2,
        weekNo: 2,
        plannedMinutes: 45,
        dueDate: '2026-12-08',
      }],
    }
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(jsonResponse([point]))
      .mockResolvedValueOnce(jsonResponse({ [LARGE_ID]: 3 }))
      .mockResolvedValueOnce(jsonResponse({ pointId: LARGE_ID }))
      .mockResolvedValueOnce(jsonResponse('依赖已添加'))
      .mockResolvedValueOnce(jsonResponse('掌握度已更新'))
      .mockResolvedValueOnce(jsonResponse('关键词已更新'))
      .mockResolvedValueOnce(jsonResponse([weakPoint]))
      .mockResolvedValueOnce(jsonResponse(path))
      .mockResolvedValueOnce(jsonResponse(null))
      .mockResolvedValueOnce(jsonResponse(path))
      .mockResolvedValueOnce(jsonResponse(progress))
      .mockResolvedValueOnce(jsonResponse(adjustment))
      .mockResolvedValueOnce(jsonResponse(adjustment))
      .mockResolvedValueOnce(jsonResponse(weakReview))
      .mockResolvedValueOnce(jsonResponse(weakReviewPlan))
      .mockResolvedValueOnce(jsonResponse(weakReviewPlan))
    vi.stubGlobal('fetch', fetchMock)

    await expect(listKnowledgePoints(LARGE_ID)).resolves.toEqual([point])
    await expect(getMyKnowledgeMastery(LARGE_ID)).resolves.toEqual({
      [LARGE_ID]: 3,
    })
    await expect(
      addKnowledgePoint(LARGE_ID, {
        title: point.title,
        estimatedMinutes: 120,
        sortOrder: 1,
        matchKeywords: 'HashMap,ArrayList',
        chapterId: null,
        parentId: null,
        difficulty: 2,
      }),
    ).resolves.toEqual({ pointId: LARGE_ID })
    await expect(
      addKnowledgeDependency(LARGE_ID, LARGE_ID, LARGE_ID),
    ).resolves.toBe('依赖已添加')
    await expect(
      updateKnowledgeMastery(LARGE_ID, LARGE_ID, 3),
    ).resolves.toBe('掌握度已更新')
    await expect(
      updateKnowledgePointKeywords(
        LARGE_ID,
        LARGE_ID,
        'HashMap,ArrayList',
      ),
    ).resolves.toBe('关键词已更新')
    await expect(listWeakKnowledgePoints(LARGE_ID)).resolves.toEqual([weakPoint])
    await expect(generateLearningPath(LARGE_ID)).resolves.toEqual(path)
    await expect(getLearningPathByGoal(LARGE_ID)).resolves.toBeNull()
    await expect(getLearningPath(LARGE_ID)).resolves.toEqual(path)
    await expect(getLearningPathProgress(LARGE_ID)).resolves.toEqual(progress)
    await expect(
      previewLearningPathAdjustment(LARGE_ID),
    ).resolves.toEqual(adjustment)
    await expect(
      applyLearningPathAdjustment(LARGE_ID, adjustment.fingerprint),
    ).resolves.toEqual(adjustment)
    await expect(previewWeakReview(LARGE_ID)).resolves.toEqual(weakReview)
    await expect(
      previewWeakReviewPlan(LARGE_ID),
    ).resolves.toEqual(weakReviewPlan)
    await expect(
      applyWeakReviewPlan(LARGE_ID, weakReviewPlan.fingerprint),
    ).resolves.toEqual(weakReviewPlan)

    expect(fetchMock.mock.calls.map(([url, init]) => ({
      url: String(url),
      method: (init as RequestInit | undefined)?.method,
      body: (init as RequestInit | undefined)?.body,
    }))).toEqual([
      {
        url: `/api/v1/learning-paths/courses/${LARGE_ID}/points`,
        method: 'GET',
        body: undefined,
      },
      {
        url: `/api/v1/learning-paths/courses/${LARGE_ID}/mastery`,
        method: 'GET',
        body: undefined,
      },
      {
        url: `/api/v1/learning-paths/courses/${LARGE_ID}/points`,
        method: 'POST',
        body: JSON.stringify({
          title: point.title,
          estimatedMinutes: 120,
          sortOrder: 1,
          matchKeywords: 'HashMap,ArrayList',
          chapterId: null,
          parentId: null,
          difficulty: 2,
        }),
      },
      {
        url: `/api/v1/learning-paths/courses/${LARGE_ID}/dependencies`,
        method: 'POST',
        body: JSON.stringify({
          pointId: LARGE_ID,
          prerequisiteId: LARGE_ID,
        }),
      },
      {
        url: `/api/v1/learning-paths/courses/${LARGE_ID}/points/${LARGE_ID}/mastery`,
        method: 'PUT',
        body: JSON.stringify({ level: 3 }),
      },
      {
        url: `/api/v1/learning-paths/courses/${LARGE_ID}/points/${LARGE_ID}/keywords`,
        method: 'PUT',
        body: JSON.stringify({ matchKeywords: 'HashMap,ArrayList' }),
      },
      {
        url: `/api/v1/learning-paths/courses/${LARGE_ID}/weak-points`,
        method: 'GET',
        body: undefined,
      },
      {
        url: `/api/v1/learning-paths/goals/${LARGE_ID}/generate`,
        method: 'POST',
        body: undefined,
      },
      {
        url: `/api/v1/learning-paths/goals/${LARGE_ID}`,
        method: 'GET',
        body: undefined,
      },
      {
        url: `/api/v1/learning-paths/${LARGE_ID}`,
        method: 'GET',
        body: undefined,
      },
      {
        url: `/api/v1/learning-paths/${LARGE_ID}/progress`,
        method: 'GET',
        body: undefined,
      },
      {
        url: `/api/v1/learning-paths/${LARGE_ID}/adjustment-preview`,
        method: 'GET',
        body: undefined,
      },
      {
        url: `/api/v1/learning-paths/${LARGE_ID}/adjust`,
        method: 'POST',
        body: JSON.stringify({ fingerprint: adjustment.fingerprint }),
      },
      {
        url: `/api/v1/learning-paths/${LARGE_ID}/weak-review-preview`,
        method: 'GET',
        body: undefined,
      },
      {
        url: `/api/v1/learning-paths/${LARGE_ID}/weak-review-plan`,
        method: 'GET',
        body: undefined,
      },
      {
        url: `/api/v1/learning-paths/${LARGE_ID}/apply-weak-review`,
        method: 'POST',
        body: JSON.stringify({ fingerprint: weakReviewPlan.fingerprint }),
      },
    ])
  })

  it('updates and deletes knowledge points, dependencies, and loads the knowledge map', async () => {
    const map = {
      courseId: LARGE_ID,
      chapters: [{
        id: LARGE_ID,
        name: '第一章',
        sortOrder: 1,
      }],
      points: [{
        id: LARGE_ID,
        title: 'Java 集合',
        chapterId: LARGE_ID,
        parentId: null,
        difficulty: 2,
        estimatedMinutes: 120,
        sortOrder: 1,
        matchKeywords: 'HashMap,ArrayList',
      }],
      dependencies: [{
        pointId: LARGE_ID,
        prerequisiteId: '9223372036854775806',
      }],
      statistics: {
        chapterCount: 1,
        pointCount: 1,
        dependencyCount: 1,
        unclassifiedCount: 0,
        basicCount: 0,
        intermediateCount: 1,
        advancedCount: 0,
        totalEstimatedMinutes: 120,
      },
    }
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(jsonResponse('知识点修改成功'))
      .mockResolvedValueOnce(jsonResponse('知识点删除成功'))
      .mockResolvedValueOnce(jsonResponse('依赖已解除'))
      .mockResolvedValueOnce(jsonResponse(map))
    vi.stubGlobal('fetch', fetchMock)

    await expect(updateKnowledgePoint(LARGE_ID, LARGE_ID, {
      title: 'Java 集合',
      estimatedMinutes: 120,
      sortOrder: 1,
      matchKeywords: 'HashMap,ArrayList',
      chapterId: LARGE_ID,
      parentId: null,
      difficulty: 2,
    })).resolves.toBe('知识点修改成功')
    await expect(deleteKnowledgePoint(LARGE_ID, LARGE_ID))
      .resolves.toBe('知识点删除成功')
    await expect(removeKnowledgeDependency(
      LARGE_ID,
      LARGE_ID,
      '9223372036854775806',
    )).resolves.toBe('依赖已解除')
    await expect(getCourseKnowledgeMap(LARGE_ID)).resolves.toEqual(map)

    expect(fetchMock.mock.calls.map(([url, init]) => ({
      url: String(url),
      method: (init as RequestInit | undefined)?.method,
      body: (init as RequestInit | undefined)?.body,
    }))).toEqual([
      {
        url: `/api/v1/learning-paths/courses/${LARGE_ID}/points/${LARGE_ID}`,
        method: 'PUT',
        body: JSON.stringify({
          title: 'Java 集合',
          estimatedMinutes: 120,
          sortOrder: 1,
          matchKeywords: 'HashMap,ArrayList',
          chapterId: LARGE_ID,
          parentId: null,
          difficulty: 2,
        }),
      },
      {
        url: `/api/v1/learning-paths/courses/${LARGE_ID}/points/${LARGE_ID}`,
        method: 'DELETE',
        body: undefined,
      },
      {
        url: `/api/v1/learning-paths/courses/${LARGE_ID}/dependencies/${LARGE_ID}/9223372036854775806`,
        method: 'DELETE',
        body: undefined,
      },
      {
        url: `/api/v1/learning-paths/courses/${LARGE_ID}/knowledge-map`,
        method: 'GET',
        body: undefined,
      },
    ])
    expect(knowledgeDifficultyLabel(1)).toBe('基础')
    expect(knowledgeDifficultyLabel(2)).toBe('中等')
    expect(knowledgeDifficultyLabel(3)).toBe('进阶')
    expect(knowledgeDifficultyLabel(0)).toBe('未知')
  })

  it('loads integrated student weak point diagnosis', async () => {

    const diagnosis = [{
      pointId: LARGE_ID,
      title: 'Java 集合',

      effectiveQuestionCount: 6,
      questionSessionCount: 3,
      questionInferredLevel: 0,

      practiceQuestionCount: 10,
      masteredQuestionCount: 4,
      practiceMasteryRate: 40,

      selfAssessmentLevel: 2,
      practiceLevel: 0,

      diagnosisLevel: 0,
      diagnosisLabel: '重点巩固',

      diagnosisReason:
        '练习掌握率为40%，共统计10道题；最近30天有效提问6次，涉及3个会话',

      suggestion:
        '优先复习对应课程资料，完成基础练习，并针对错题进行重练。',

      lastPracticedAt: '2026-11-27T15:30:00',
    }]

    const fetchMock = vi.fn()
      .mockResolvedValueOnce(jsonResponse(diagnosis))

    vi.stubGlobal('fetch', fetchMock)

    await expect(
      getStudentWeakPointDiagnosis(LARGE_ID),
    ).resolves.toEqual(diagnosis)

    expect(
      fetchMock.mock.calls[0][0],
    ).toBe(
      `/api/v1/learning-paths/courses/${LARGE_ID}/weak-point-diagnosis`,
    )
  })
})
