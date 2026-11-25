
import { apiRequest } from '@/api/client'
import type { BigIntId } from '@/api/types'

/**
 * 个人课程空间
 * 对应 Java 后端 Course.java
 */
export interface Course {
  id: BigIntId
  courseCode: string | null
  name: string
  description: string | null
  credits: number | null
  applicableMajor: string | null
  semester: string | null
  ownerId: BigIntId
  status: number
  createTime: string
  updateTime: string
}

/**
 * 创建课程请求
 */
export interface CreateCourseRequest {
  courseCode: string
  name: string
  description?: string
  credits: number
  applicableMajor: string
  semester: string
}

/**
 * 修改课程请求
 */
export interface UpdateCourseRequest {
  courseCode: string
  name: string
  description?: string
  credits: number
  applicableMajor: string
  semester: string
}

export interface CourseMember {
  studentId: BigIntId
  username: string
  nickname: string | null
  joinTime: string
}

export interface JoinCourseRequest {
  inviteCode: string
}

export interface CourseInviteCode {
  inviteCode: string
}

/**
 * 查询我的课程
 */
export function listCourses(): Promise<Course[]> {
  return apiRequest<Course[]>('/courses/mine')
}

/**
 * 学生加入的课程
 */
export function listJoinedCourses(): Promise<Course[]> {
  return apiRequest<Course[]>('/courses/joined')
}

/**
 * 创建课程
 */
export function createCourse(
  request: CreateCourseRequest,
): Promise<Course> {
  return apiRequest<Course>('/courses', {
    method: 'POST',
    body: request,
  })
}

/**
 * 学生通过邀请码加入课程
 */
export function joinCourse(
  request: JoinCourseRequest,
): Promise<Course> {
  return apiRequest<Course>('/courses/join', {
    method: 'POST',
    body: request,
  })
}

/**
 * 查看课程详情
 */
export function getCourseDetail(
  courseId: BigIntId,
): Promise<Course> {
  return apiRequest<Course>(
    `/courses/${encodeURIComponent(courseId)}`,
  )
}

/**
 * 修改课程
 */
export function updateCourse(
  courseId: BigIntId,
  request: UpdateCourseRequest,
): Promise<void> {
  return apiRequest<void>(
    `/courses/${encodeURIComponent(courseId)}`,
    {
      method: 'PUT',
      body: request,
    },
  )
}

/**
 * 停用课程
 */
export function deleteCourse(
  courseId: BigIntId,
): Promise<void> {
  return apiRequest<void>(
    `/courses/${encodeURIComponent(courseId)}`,
    {
      method: 'DELETE',
    },
  )
}

/**
 * 查看邀请码
 */
export function getCourseInviteCode(
  courseId: BigIntId,
): Promise<CourseInviteCode> {
  return apiRequest<CourseInviteCode>(
    `/courses/${encodeURIComponent(courseId)}/invite-code`,
  )
}

/**
 * 重新生成邀请码
 */
export function regenerateCourseInviteCode(
  courseId: BigIntId,
): Promise<CourseInviteCode> {
  return apiRequest<CourseInviteCode>(
    `/courses/${encodeURIComponent(courseId)}/invite-code`,
    {
      method: 'POST',
    },
  )
}

/**
 * 查看课程成员
 */
export function listCourseStudents(
  courseId: BigIntId,
): Promise<CourseMember[]> {
  return apiRequest<CourseMember[]>(
    `/courses/${encodeURIComponent(courseId)}/students`,
  )
}

/**
 * 移除课程学生
 */
export function removeCourseStudent(
  courseId: BigIntId,
  studentId: BigIntId,
): Promise<void> {
  return apiRequest<void>(
    `/courses/${encodeURIComponent(courseId)}/students/${encodeURIComponent(studentId)}`,
    {
      method: 'DELETE',
    },
  )
}

/**
 * 管理员查看课程邀请码
 */
export function adminGetCourseInviteCode(
  courseId: BigIntId,
): Promise<CourseInviteCode> {
  return apiRequest<CourseInviteCode>(
    `/admin/courses/${encodeURIComponent(courseId)}/invite-code`,
  )
}

/**
 * 管理员重新生成课程邀请码
 */
export function adminRegenerateCourseInviteCode(
  courseId: BigIntId,
): Promise<CourseInviteCode> {
  return apiRequest<CourseInviteCode>(
    `/admin/courses/${encodeURIComponent(courseId)}/invite-code`,
    {
      method: 'POST',
    },
  )
}

/**
 * 管理员查看课程成员
 */
export function adminListCourseStudents(
  courseId: BigIntId,
): Promise<CourseMember[]> {
  return apiRequest<CourseMember[]>(
    `/admin/courses/${encodeURIComponent(courseId)}/students`,
  )
}

/**
 * 管理员移除课程学生
 */
export function adminRemoveCourseStudent(
  courseId: BigIntId,
  studentId: BigIntId,
): Promise<void> {
  return apiRequest<void>(
    `/admin/courses/${encodeURIComponent(courseId)}/students/${encodeURIComponent(studentId)}`,
    {
      method: 'DELETE',
    },
  )
}
