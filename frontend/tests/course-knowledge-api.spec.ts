import { afterEach, beforeEach, describe, expect, it, vi, type Mock } from 'vitest'

import {
  adminGetCourseInviteCode,
  adminListCourseStudents,
  adminRegenerateCourseInviteCode,
  adminRemoveCourseStudent,
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
import {
  deleteDoc,
  downloadDocumentFile,
  listDocs,
  listDocumentVersions,
  previewDocument,
  retryDoc,
  uploadDoc,
  uploadDocumentVersion,
} from '@/api/knowledge'
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

function capturedFormData(fetchMock: Mock, index = 0): FormData {
  const init = fetchMock.mock.calls[index]?.[1] as RequestInit | undefined
  if (!(init?.body instanceof FormData)) {
    throw new Error('Expected request body to be FormData')
  }
  return init.body
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
    await updateCourse(LARGE_ID, {
      courseCode: 'DB101',
      name: course.name,
      description: '',
      credits: 3,
      applicableMajor: '软件工程',
      semester: '2026-2027-1',
    })
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

  it('uses admin-specific endpoints for course invite and membership management', async () => {
    const inviteCode = { inviteCode: 'K7M9X2PA' }
    const member = {
      studentId: '9007199254740994',
      username: 'student01',
      nickname: '学生',
      joinTime: '2026-11-14T10:00:00',
    }
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(jsonResponse(inviteCode))
      .mockResolvedValueOnce(jsonResponse(inviteCode))
      .mockResolvedValueOnce(jsonResponse([member]))
      .mockResolvedValueOnce(jsonResponse(null))
    vi.stubGlobal('fetch', fetchMock)

    await expect(adminGetCourseInviteCode(LARGE_ID)).resolves.toEqual(inviteCode)
    await expect(adminRegenerateCourseInviteCode(LARGE_ID)).resolves.toEqual(inviteCode)
    await expect(adminListCourseStudents(LARGE_ID)).resolves.toEqual([member])
    await adminRemoveCourseStudent(LARGE_ID, member.studentId)

    expect(capturedUrls(fetchMock)).toEqual([
      `/api/admin/courses/${LARGE_ID}/invite-code`,
      `/api/admin/courses/${LARGE_ID}/invite-code`,
      `/api/admin/courses/${LARGE_ID}/students`,
      `/api/admin/courses/${LARGE_ID}/students/${member.studentId}`,
    ])
    expect(capturedRequests(fetchMock)).toEqual([
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
      resourceId: LARGE_ID,
      uploaderId: LARGE_ID,
      originalName: 'lecture.pdf',
      fileSize: 1024,
      versionNo: 2,
      versionStatus: 'PENDING',
      resourceType: 'COURSEWARE',
      sourceInfo: '教师自编',
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

  it('uploads documents with optional resource metadata and defaults to AUTO', async () => {
    const document = {
      id: LARGE_ID,
      courseId: LARGE_ID,
      resourceId: LARGE_ID,
      uploaderId: LARGE_ID,
      originalName: 'lecture.pdf',
      fileSize: 1024,
      versionNo: 2,
      versionStatus: 'PENDING',
      resourceType: 'COURSEWARE',
      sourceInfo: '教师自编',
      status: 'UPLOADED',
      errorMessage: null,
      createTime: '2026-11-14T10:00:00',
    }
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(jsonResponse(document))
      .mockResolvedValueOnce(jsonResponse(document))
    vi.stubGlobal('fetch', fetchMock)
    const file = new File(['pdf'], 'lecture.pdf', { type: 'application/pdf' })

    await expect(uploadDoc(LARGE_ID, file)).resolves.toEqual(document)
    await expect(uploadDoc(LARGE_ID, file, {
      resourceType: 'COURSEWARE',
      sourceInfo: '  教师自编  ',
    })).resolves.toEqual(document)

    const defaultForm = capturedFormData(fetchMock)
    const configuredForm = capturedFormData(fetchMock, 1)
    expect(defaultForm.get('resourceType')).toBe('AUTO')
    expect(defaultForm.get('sourceInfo')).toBeNull()
    expect(configuredForm.get('resourceType')).toBe('COURSEWARE')
    expect(configuredForm.get('sourceInfo')).toBe('教师自编')
  })

  it('uses authenticated PDF preview and download routes', async () => {
    const fetchMock = vi.fn().mockImplementation(() => Promise.resolve(
      new Response(new Blob(['%PDF-test']), {
        status: 200,
        headers: { 'Content-Type': 'application/pdf' },
      }),
    ))
    vi.stubGlobal('fetch', fetchMock)

    await expect(previewDocument(LARGE_ID)).resolves.toBeInstanceOf(Blob)
    await expect(downloadDocumentFile(LARGE_ID)).resolves.toBeInstanceOf(Blob)

    expect(capturedUrls(fetchMock)).toEqual([
      `/api/documents/${LARGE_ID}/preview`,
      `/api/documents/${LARGE_ID}/download`,
    ])
  })

  it('lists and uploads resource versions without converting BIGINT identifiers', async () => {
    const version = {
      documentId: LARGE_ID,
      resourceId: '9007199254740994',
      versionNo: 1,
      originalName: 'lecture.pdf',
      versionStatus: 'CURRENT',
      status: 'READY',
      uploaderId: '9007199254740995',
      createTime: '2026-11-25T10:00:00',
    }
    const uploaded = {
      id: '9007199254740996',
      courseId: '2001',
      resourceId: version.resourceId,
      uploaderId: version.uploaderId,
      originalName: 'lecture-v2.pdf',
      fileSize: 1024,
      versionNo: 2,
      versionStatus: 'PENDING',
      resourceType: 'COURSEWARE',
      sourceInfo: null,
      status: 'PROCESSING',
      errorMessage: null,
      createTime: version.createTime,
    }
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(jsonResponse([version]))
      .mockResolvedValueOnce(jsonResponse(uploaded))
    vi.stubGlobal('fetch', fetchMock)
    const file = new File(['pdf'], 'lecture-v2.pdf', { type: 'application/pdf' })

    await expect(listDocumentVersions(LARGE_ID)).resolves.toEqual([version])
    await expect(uploadDocumentVersion(LARGE_ID, file)).resolves.toEqual(uploaded)

    expect(capturedUrls(fetchMock)).toEqual([
      `/api/documents/${LARGE_ID}/versions`,
      `/api/documents/${LARGE_ID}/versions`,
    ])
    expect(capturedRequests(fetchMock)[1]).toEqual({
      method: 'POST',
      body: undefined,
    })
    expect(capturedFormData(fetchMock, 1).get('file')).toBeInstanceOf(File)
  })
})
