import { apiRequest } from '@/api/client'
import type { BigIntId } from '@/api/types'

export type CourseHotQuestionStatus =
  | 'DRAFT'
  | 'PENDING_REVIEW'
  | 'PUBLISHED'
  | 'REJECTED'
  | 'OFFLINE'

export interface CourseHotQuestion {
  id: BigIntId
  courseId: BigIntId
  knowledgePointId: BigIntId | null
  knowledgePointTitle: string | null

  sourceQuestionText: string

  questionText: string
  answerText: string

  category: string

  questionCount: number
  studentCount: number

  status: CourseHotQuestionStatus

  createdBy: BigIntId
  updatedBy: BigIntId
  reviewedBy: BigIntId | null

  reviewComment: string | null

  createdAt: string
  submittedAt: string | null
  reviewedAt: string | null
  publishedAt: string | null
  offlinedAt: string | null
}

export interface PublishedCourseHotQuestion {
  id: BigIntId
  knowledgePointTitle: string | null
  questionText: string
  answerText: string
  category: string
  publishedAt: string
}

export interface CreateCourseHotQuestionRequest {
  sourceQuestionText: string
  knowledgePointId: BigIntId | null
  questionText: string
  answerText: string
  category: string
}

export interface UpdateCourseHotQuestionRequest {
  knowledgePointId: BigIntId | null
  questionText: string
  answerText: string
  category: string
}

export interface CourseHotQuestionReviewRequest {
  approved: boolean
  comment: string
}

function courseUrl(courseId: BigIntId): string {
  return (
    `/v1/course-hot-questions/courses/`
    + encodeURIComponent(courseId)
  )
}

/**
 * 查询运营管理列表。
 */
export function listManagedHotQuestions(
  courseId: BigIntId,
  status?: CourseHotQuestionStatus | '',
  keyword?: string,
): Promise<CourseHotQuestion[]> {

  const params = new URLSearchParams()

  if (status) {
    params.set('status', status)
  }

  if (keyword?.trim()) {
    params.set('keyword', keyword.trim())
  }

  const query = params.toString()

  return apiRequest<CourseHotQuestion[]>(
    `${courseUrl(courseId)}/manage${query ? `?${query}` : ''}`,
  )
}

/**
 * 学生查询已发布的热门问题。
 */
export function listPublishedHotQuestions(
  courseId: BigIntId,
  keyword?: string,
  category?: string,
): Promise<PublishedCourseHotQuestion[]> {

  const params = new URLSearchParams()

  if (keyword?.trim()) {
    params.set('keyword', keyword.trim())
  }

  if (category) {
    params.set('category', category)
  }

  const query = params.toString()

  return apiRequest<PublishedCourseHotQuestion[]>(
    `${courseUrl(courseId)}/published${query ? `?${query}` : ''}`,
  )
}

/**
 * 根据高频问题创建标准问答。
 */
export function createCourseHotQuestion(
  courseId: BigIntId,
  data: CreateCourseHotQuestionRequest,
): Promise<CourseHotQuestion> {

  return apiRequest<CourseHotQuestion>(
    courseUrl(courseId),
    {
      method: 'POST',
      body: data,
    },
  )
}

/**
 * 修改草稿。
 */
export function updateCourseHotQuestion(
  id: BigIntId,
  data: UpdateCourseHotQuestionRequest,
): Promise<CourseHotQuestion> {

  return apiRequest<CourseHotQuestion>(
    `/v1/course-hot-questions/${encodeURIComponent(id)}`,
    {
      method: 'PUT',
      body: data,
    },
  )
}

/**
 * 提交审核。
 */
export function submitCourseHotQuestion(
  id: BigIntId,
): Promise<CourseHotQuestion> {

  return apiRequest<CourseHotQuestion>(
    `/v1/course-hot-questions/${encodeURIComponent(id)}/submit`,
    {
      method: 'POST',
    },
  )
}

/**
 * 课程负责人审核。
 */
export function reviewCourseHotQuestion(
  id: BigIntId,
  data: CourseHotQuestionReviewRequest,
): Promise<CourseHotQuestion> {

  return apiRequest<CourseHotQuestion>(
    `/v1/course-hot-questions/${encodeURIComponent(id)}/review`,
    {
      method: 'POST',
      body: data,
    },
  )
}

/**
 * 管理员下架。
 */
export function offlineCourseHotQuestion(
  id: BigIntId,
): Promise<CourseHotQuestion> {

  return apiRequest<CourseHotQuestion>(
    `/v1/course-hot-questions/${encodeURIComponent(id)}/offline`,
    {
      method: 'POST',
    },
  )
}
