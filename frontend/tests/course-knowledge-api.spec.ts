import { afterEach, beforeEach, describe, expect, it, vi, type Mock } from 'vitest'

import {
  deleteCourse,
  getCourseDetail,
  getCourseInviteCode,
  joinCourse,
  listCourseStudents,
  listCourses,
  listJoinedCourses,
  regenerateCourseInviteCode,
  removeCourseStudent,
  updateCourse,
} from '@/api/course'
import { deleteDoc, listDocs, retryDoc } from '@/api/knowledge'
import { resetApiClient } from '@/api/client'

const LARGE_ID = '9007199254740993'

function jsonResponse(data: unknown): Response {
  return new Response(JSON.stringify({ code: 200, message: '操作成功', data }), {
    status: 200,
    headers: { 'Content-Type': 'application/json' },
  })
}

function capturedUrls(fetchMock: Mock): string[] {
  return fetchMock.mock.calls.map((call) => String(call[0]))
}

function capturedRequests(fetchMock: Mock): Array<{
  method?: string
  body?: string
}> {
  return fetchMock.mock.calls.map((call) => {
    const init = call[1] as RequestInit | undefined
    return {
      method: init?.method,
      body: typeof init?.body === 'string' ? init.body : undefined,
    }
  })
}

describe('course and document BIGINT APIs', () => {
  beforeEach(() => {
    resetApiClient()
  })

  afterEach(() => {
    vi.unstubAllGlobals()
    resetApiClient()
  })

  it('preserves string BIGINTs in course response and request paths', async () => {
    const course = {
      id: LARGE_ID,
      name: '数据库',
      description: null,
      ownerId: LARGE_ID,
      status: 1,
      createTime: '2026-11-14T10:00:00',
      updateTime: '2026-11-14T10:00:00',
    }
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(jsonResponse([course]))
      .mockResolvedValueOnce(jsonResponse(course))
      .mockResolvedValueOnce(jsonResponse(null))
      .mockResolvedValueOnce(jsonResponse(null))
    vi.stubGlobal('fetch', fetchMock)

    await expect(listCourses()).resolves.toEqual([course])
    await expect(getCourseDetail(LARGE_ID)).resolves.toEqual(course)
    await updateCourse(LARGE_ID, { name: course.name })
    await deleteCourse(LARGE_ID)

    expect(capturedUrls(fetchMock)).toEqual([
      '/api/courses/mine',
      `/api/courses/${LARGE_ID}`,
      `/api/courses/${LARGE_ID}`,
      `/api/courses/${LARGE_ID}`,
    ])
  })

  it('supports student course membership and teacher invite/member endpoints', async () => {
    const course = {
      id: LARGE_ID,
      name: '数据库',
      description: null,
      ownerId: LARGE_ID,
      status: 1,
      createTime: '2026-11-14T10:00:00',
      updateTime: '2026-11-14T10:00:00',
    }
    const inviteCode = { inviteCode: 'K7M9X2PA' }
    const member = {
      studentId: '9007199254740994',
      username: 'student01',
      nickname: '学生',
      joinTime: '2026-11-14T10:00:00',
    }
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(jsonResponse([course]))
      .mockResolvedValueOnce(jsonResponse(course))
      .mockResolvedValueOnce(jsonResponse(inviteCode))
      .mockResolvedValueOnce(jsonResponse(inviteCode))
      .mockResolvedValueOnce(jsonResponse([member]))
      .mockResolvedValueOnce(jsonResponse(null))
    vi.stubGlobal('fetch', fetchMock)

    await expect(listJoinedCourses()).resolves.toEqual([course])
    await expect(joinCourse({ inviteCode: 'K7M9X2PA' })).resolves.toEqual(course)
    await expect(getCourseInviteCode(LARGE_ID)).resolves.toEqual(inviteCode)
    await expect(regenerateCourseInviteCode(LARGE_ID)).resolves.toEqual(inviteCode)
    await expect(listCourseStudents(LARGE_ID)).resolves.toEqual([member])
    await removeCourseStudent(LARGE_ID, member.studentId)

    expect(capturedUrls(fetchMock)).toEqual([
      '/api/courses/joined',
      '/api/courses/join',
      `/api/courses/${LARGE_ID}/invite-code`,
      `/api/courses/${LARGE_ID}/invite-code`,
      `/api/courses/${LARGE_ID}/students`,
      `/api/courses/${LARGE_ID}/students/${member.studentId}`,
    ])
    expect(capturedRequests(fetchMock)).toEqual([
      { method: 'GET', body: undefined },
      { method: 'POST', body: JSON.stringify({ inviteCode: 'K7M9X2PA' }) },
      { method: 'GET', body: undefined },
      { method: 'POST', body: undefined },
      { method: 'GET', body: undefined },
      { method: 'DELETE', body: undefined },
    ])
  })

  it('preserves string BIGINTs and Java document fields', async () => {
    const document = {
      id: LARGE_ID,
      courseId: LARGE_ID,
      uploaderId: LARGE_ID,
      originalName: 'lecture.pdf',
      fileSize: 1024,
      status: 'PARSING',
      errorMessage: null,
      createTime: '2026-11-14T10:00:00',
    }
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(jsonResponse([document]))
      .mockResolvedValueOnce(jsonResponse('删除成功'))
      .mockResolvedValueOnce(jsonResponse('已提交重新处理'))
    vi.stubGlobal('fetch', fetchMock)

    await expect(listDocs(LARGE_ID)).resolves.toEqual([document])
    await expect(deleteDoc(LARGE_ID)).resolves.toBe('删除成功')
    await expect(retryDoc(LARGE_ID)).resolves.toBe('已提交重新处理')

    expect(capturedUrls(fetchMock)).toEqual([
      `/api/documents/course/${LARGE_ID}`,
      `/api/documents/${LARGE_ID}`,
      `/api/documents/${LARGE_ID}/retry`,
    ])
  })
})
