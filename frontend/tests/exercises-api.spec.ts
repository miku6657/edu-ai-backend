import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import { configureApiClient, resetApiClient } from '@/api/client'
import {
  closeExercise,
  createExercise,
  createStudentMistakeRetryList,
  deleteExerciseDraft,
  getExerciseReview,
  getExercise,
  getStudentExercise,
  getStudentExerciseRecommendations,
  getStudentExerciseResult,
  gradeExerciseAttempt,
  getStudentMistake,
  getStudentMistakes,
  getStudentRetryDetail,
  getStudentRetryResult,
  listExerciseKnowledgePoints,
  listManagedExercises,
  listPendingExerciseReviews,
  listStudentExercises,
  listStudentRetryLists,
  publishExercise,
  selfReviewStudentRetry,
  startStudentExercise,
  startStudentRetry,
  submitStudentExercise,
  submitStudentRetry,
  updateExercise,
} from '@/api/exercises'
import type {
  ExerciseAnswerRequest,
  ExerciseSaveRequest,
} from '@/api/exercises'

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

function requestUrl(input: RequestInfo | URL): string {
  if (typeof input === 'string') return input
  if (input instanceof URL) return input.href
  return input.url
}

describe('exercises api', () => {
  beforeEach(() => {
    resetApiClient()
    configureApiClient({ tokenProvider: () => 'jwt-token' })
  })

  afterEach(() => {
    vi.unstubAllGlobals()
    resetApiClient()
  })

  it('练习管理请求使用预期路径、方法和请求体', async () => {
    const fetchMock = vi.fn<
      (input: RequestInfo | URL, init?: RequestInit) => Promise<Response>
    >().mockImplementation(() => Promise.resolve(jsonResponse([])))
    vi.stubGlobal('fetch', fetchMock)
    const request: ExerciseSaveRequest = {
      title: 'Java 基础练习',
      description: '',
      difficulty: 2,
      estimatedMinutes: 15,
      questions: [{
        knowledgePointId: '301',
        questionType: 'SINGLE_CHOICE',
        stem: '哪项正确？',
        options: [{ label: 'A', content: '选项 A' }, { label: 'B', content: '选项 B' }],
        correctAnswer: 'A',
        explanation: '',
        score: 5,
      }],
    }

    await listManagedExercises('2001')
    await listExerciseKnowledgePoints('2001')
    await listStudentExercises()
    await listStudentExercises('2001')
    await getExercise('501')
    await createExercise('2001', request)
    await updateExercise('501', request)
    await publishExercise('501')
    await closeExercise('501')
    await deleteExerciseDraft('501')
    await getStudentExercise('501')
    await startStudentExercise('501')
    const answers: ExerciseAnswerRequest[] = [
      { questionId: '601', answer: 'A' },
    ]
    await submitStudentExercise('901', answers)
    await getStudentExerciseResult('901')
    await listPendingExerciseReviews()
    await listPendingExerciseReviews('2001')
    await getExerciseReview('901')
    const grades = [{ answerId: '701', score: 4, feedback: '思路清晰' }]
    await gradeExerciseAttempt('901', grades)
    await getStudentMistakes()
    await getStudentMistakes('2001')
    await getStudentMistake('701')
    await createStudentMistakeRetryList(
      ['701', '702'],
      'Java 基础错题重练',
    )
    await listStudentRetryLists()
    await getStudentRetryDetail('1001')
    await startStudentRetry('1001')

    const retryAnswers = [
      { retryItemId: '2001', answer: 'A' },
      { retryItemId: '2002', answer: 'B,C' },
    ]

    await submitStudentRetry('3001', retryAnswers)
    await getStudentRetryResult('3001')

    const reviewAnswers = [
      { retryItemId: '2003', understood: true },
    ]

    await selfReviewStudentRetry('3001', reviewAnswers)

    expect(fetchMock.mock.calls.map(([url, init]) => [requestUrl(url), init?.method]))
      .toEqual([
        ['/api/v1/exercises/manage?courseId=2001', 'GET'],
        ['/api/v1/exercises/courses/2001/knowledge-points', 'GET'],
        ['/api/v1/student/exercises', 'GET'],
        ['/api/v1/student/exercises?courseId=2001', 'GET'],
        ['/api/v1/exercises/501', 'GET'],
        ['/api/v1/exercises/courses/2001', 'POST'],
        ['/api/v1/exercises/501', 'PUT'],
        ['/api/v1/exercises/501/publish', 'POST'],
        ['/api/v1/exercises/501/close', 'POST'],
        ['/api/v1/exercises/501', 'DELETE'],
        ['/api/v1/student/exercises/501', 'GET'],
        ['/api/v1/student/exercises/501/attempts', 'POST'],
        ['/api/v1/student/exercises/attempts/901/submit', 'POST'],
        ['/api/v1/student/exercises/attempts/901', 'GET'],
        ['/api/v1/exercises/reviews/pending', 'GET'],
        ['/api/v1/exercises/reviews/pending?courseId=2001', 'GET'],
        ['/api/v1/exercises/reviews/901', 'GET'],
        ['/api/v1/exercises/reviews/901/grade', 'POST'],
        ['/api/v1/student/exercises/mistakes', 'GET'],
        ['/api/v1/student/exercises/mistakes?courseId=2001', 'GET'],
        ['/api/v1/student/exercises/mistakes/701', 'GET'],
        ['/api/v1/student/exercises/mistakes/retry-lists', 'POST'],
        ['/api/v1/student/exercises/retry-lists', 'GET'],
        ['/api/v1/student/exercises/retry-lists/1001', 'GET'],
        ['/api/v1/student/exercises/retry-lists/1001/attempts', 'POST'],
        ['/api/v1/student/exercises/retry-lists/attempts/3001/submit', 'POST'],
        ['/api/v1/student/exercises/retry-lists/attempts/3001', 'GET'],
        ['/api/v1/student/exercises/retry-lists/attempts/3001/self-review', 'POST'],
      ])
    const body = fetchMock.mock.calls[5]?.[1]?.body
    if (typeof body !== 'string') {
      throw new Error('请求体不是 JSON 字符串')
    }
    expect(JSON.parse(body)).toEqual(request)
    const submitBody = fetchMock.mock.calls[12]?.[1]?.body
    if (typeof submitBody !== 'string') {
      throw new Error('提交请求体不是 JSON 字符串')
    }
    expect(JSON.parse(submitBody)).toEqual({ answers })
    const gradeBody = fetchMock.mock.calls[17]?.[1]?.body
    if (typeof gradeBody !== 'string') {
      throw new Error('批改请求体不是 JSON 字符串')
    }
    expect(JSON.parse(gradeBody)).toEqual({ answers: grades })
    const retryBody = fetchMock.mock.calls[21]?.[1]?.body
    if (typeof retryBody !== 'string') {
      throw new Error('重练清单请求体不是 JSON 字符串')
    }
    expect(JSON.parse(retryBody)).toEqual({
      answerIds: ['701', '702'],
      title: 'Java 基础错题重练',
    })
    const retrySubmitBody = fetchMock.mock.calls[25]?.[1]?.body
    if (typeof retrySubmitBody !== 'string') {
      throw new Error('重练提交请求体不是 JSON 字符串')
    }
    expect(JSON.parse(retrySubmitBody)).toEqual({ answers: retryAnswers })
    const selfReviewBody = fetchMock.mock.calls[27]?.[1]?.body
    if (typeof selfReviewBody !== 'string') {
      throw new Error('自我核对请求体不是 JSON 字符串')
    }
    expect(JSON.parse(selfReviewBody)).toEqual({ answers: reviewAnswers })
  })

  it('个性化练习推荐支持全部课程和指定课程', async () => {
    const fetchMock = vi.fn<
      (input: RequestInfo | URL, init?: RequestInit) => Promise<Response>
    >().mockImplementation(() =>
      Promise.resolve(jsonResponse({
        weakKnowledgePointCount: 0,
        recommendedExerciseCount: 0,
        recommendations: [],
      })),
    )

    vi.stubGlobal('fetch', fetchMock)

    await getStudentExerciseRecommendations()
    await getStudentExerciseRecommendations('2001')

    expect(
      fetchMock.mock.calls.map(([url, init]) => [
        requestUrl(url),
        init?.method,
      ]),
    ).toEqual([
      [
        '/api/v1/student/exercises/recommendations',
        'GET',
      ],
      [
        '/api/v1/student/exercises/recommendations?courseId=2001',
        'GET',
      ],
    ])
  })
})
