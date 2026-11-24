import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'

import { resetApiClient } from '@/api/client'
import { listAccessibleCourses } from '@/api/course-access'
import { useAuthStore } from '@/stores/auth'

function jsonResponse(data: unknown): Response {
  return new Response(JSON.stringify({ code: 200, message: '操作成功', data }), {
    status: 200,
    headers: { 'Content-Type': 'application/json' },
  })
}

function setRole(role: 'TEACHER' | 'STUDENT' | 'ADMIN'): void {
  useAuthStore().setSession({
    token: 'test-token',
    user: {
      user_id: '1',
      username: 'test',
      nickname: '测试用户',
      role,
    },
  })
}

describe('listAccessibleCourses', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    resetApiClient()
    useAuthStore().clearSession()
  })

  afterEach(() => {
    vi.unstubAllGlobals()
    resetApiClient()
  })

  it('uses the teacher and student course endpoints for their roles', async () => {
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(jsonResponse([]))
      .mockResolvedValueOnce(jsonResponse([]))
    vi.stubGlobal('fetch', fetchMock)

    setRole('TEACHER')
    await expect(listAccessibleCourses()).resolves.toEqual([])
    setRole('STUDENT')
    await expect(listAccessibleCourses()).resolves.toEqual([])

    expect(fetchMock.mock.calls.map(([url]) => String(url))).toEqual([
      '/api/courses/mine',
      '/api/courses/joined',
    ])
  })

  it('reads every page for administrators', async () => {
    const firstCourse = { id: '1', name: '课程一' }
    const secondCourse = { id: '2', name: '课程二' }
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(jsonResponse({
        current: 1,
        size: 100,
        total: 2,
        pages: 2,
        records: [firstCourse],
      }))
      .mockResolvedValueOnce(jsonResponse({
        current: 2,
        size: 100,
        total: 2,
        pages: 2,
        records: [secondCourse],
      }))
    vi.stubGlobal('fetch', fetchMock)

    setRole('ADMIN')

    await expect(listAccessibleCourses()).resolves.toEqual([
      firstCourse,
      secondCourse,
    ])
    expect(fetchMock.mock.calls.map(([url]) => String(url))).toEqual([
      '/api/admin/courses?current=1&size=100',
      '/api/admin/courses?current=2&size=100',
    ])
  })

  it('returns no courses for an unauthenticated user', async () => {
    const fetchMock = vi.fn()
    vi.stubGlobal('fetch', fetchMock)

    await expect(listAccessibleCourses()).resolves.toEqual([])
    expect(fetchMock).not.toHaveBeenCalled()
  })
})
