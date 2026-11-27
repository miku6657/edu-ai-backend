import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import { configureApiClient, resetApiClient } from '@/api/client'
import {
  closeExercise,
  createExercise,
  deleteExerciseDraft,
  getExercise,
  getStudentExercise,
  getStudentExerciseResult,
  listExerciseKnowledgePoints,
  listManagedExercises,
  listStudentExercises,
  publishExercise,
  startStudentExercise,
  submitStudentExercise,
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
  })
})
