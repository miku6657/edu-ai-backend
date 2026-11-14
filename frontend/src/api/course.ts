
import { apiRequest } from '@/api/client'

/**
 * EDU-05 课程实体
 * 对应 Java 后端 Course.java
 */
export interface Course {
  id: number
  name: string
  description: string | null
  ownerId: number
  status: number
  createTime: string
  updateTime: string
}

/**
 * 创建课程请求
 */
export interface CreateCourseRequest {
  name: string
  description?: string
}

/**
 * 修改课程请求
 */
export interface UpdateCourseRequest {
  name: string
  description?: string
}

/**
 * 查询我的课程
 */
export function listCourses(): Promise<Course[]> {
  return apiRequest<Course[]>('/courses/mine')
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
 * 查看课程详情
 */
export function getCourseDetail(
  courseId: number,
): Promise<Course> {
  return apiRequest<Course>(
    `/courses/${encodeURIComponent(courseId)}`,
  )
}

/**
 * 修改课程
 */
export function updateCourse(
  courseId: number,
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
  courseId: number,
): Promise<void> {
  return apiRequest<void>(
    `/courses/${encodeURIComponent(courseId)}`,
    {
      method: 'DELETE',
    },
  )
}
