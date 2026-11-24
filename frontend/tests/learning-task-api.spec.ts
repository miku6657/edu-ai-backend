import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import { resetApiClient } from '@/api/client'
import {
  cancelCourseLearningTask,
  createCourseLearningTask,
  getCourseLearningTaskProgress,
  listCourseLearningTasks,
  setCourseLearningTaskCompletion,
  updateCourseLearningTask,
} from '@/api/learning-task'

const LARGE_ID = '9223372036854775807'

function jsonResponse(data: unknown): Response {
  return new Response(JSON.stringify({ code: 200, message: '操作成功', data }), {
    status: 200,
    headers: { 'Content-Type': 'application/json' },
  })
}

function calls(fetchMock: ReturnType<typeof vi.fn>) {
  return fetchMock.mock.calls.map(([url, init]) => ({
    url: String(url),
    method: (init as RequestInit | undefined)?.method,
    body: (init as RequestInit | undefined)?.body,
  }))
}

describe('learning task API', () => {
  beforeEach(() => {
    resetApiClient()
  })

  afterEach(() => {
    vi.unstubAllGlobals()
    resetApiClient()
  })

  it('uses the public course-task endpoints and preserves BIGINT IDs', async () => {
    const request = {
      title: '完成课程作业',
      description: '提交实验报告',
      deadline: '2026-12-01T23:59:00',
    }
    const studentTask = {
      id: LARGE_ID,
      courseId: LARGE_ID,
      title: request.title,
      description: request.description,
      deadline: request.deadline,
      completed: false,
      completedTime: null,
      overdue: false,
    }
    const learningTask = studentTask
    const progress = {
      taskId: LARGE_ID,
      courseId: LARGE_ID,
      totalStudents: 30,
      completedStudents: 12,
      pendingStudents: 18,
      completionRate: 40,
    }
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(jsonResponse([studentTask]))
      .mockResolvedValueOnce(jsonResponse(learningTask))
      .mockResolvedValueOnce(jsonResponse('任务已更新'))
      .mockResolvedValueOnce(jsonResponse('任务已取消'))
      .mockResolvedValueOnce(jsonResponse('已标记完成'))
      .mockResolvedValueOnce(jsonResponse('已取消完成'))
      .mockResolvedValueOnce(jsonResponse(progress))
    vi.stubGlobal('fetch', fetchMock)

    await expect(listCourseLearningTasks(LARGE_ID)).resolves.toEqual([studentTask])
    await expect(
      createCourseLearningTask(LARGE_ID, request),
    ).resolves.toEqual(learningTask)
    await updateCourseLearningTask(LARGE_ID, request)
    await cancelCourseLearningTask(LARGE_ID)
    await setCourseLearningTaskCompletion(LARGE_ID, true)
    await setCourseLearningTaskCompletion(LARGE_ID, false)
    await expect(getCourseLearningTaskProgress(LARGE_ID)).resolves.toEqual(progress)

    expect(calls(fetchMock)).toEqual([
      {
        url: `/api/tasks/course/${LARGE_ID}`,
        method: 'GET',
        body: undefined,
      },
      {
        url: `/api/tasks/course/${LARGE_ID}`,
        method: 'POST',
        body: JSON.stringify(request),
      },
      {
        url: `/api/tasks/${LARGE_ID}`,
        method: 'PUT',
        body: JSON.stringify(request),
      },
      {
        url: `/api/tasks/${LARGE_ID}`,
        method: 'DELETE',
        body: undefined,
      },
      {
        url: `/api/tasks/${LARGE_ID}/complete`,
        method: 'PUT',
        body: undefined,
      },
      {
        url: `/api/tasks/${LARGE_ID}/uncomplete`,
        method: 'PUT',
        body: undefined,
      },
      {
        url: `/api/tasks/${LARGE_ID}/progress`,
        method: 'GET',
        body: undefined,
      },
    ])
  })
})
