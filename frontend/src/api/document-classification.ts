import { apiRequest } from '@/api/client'
import type { BigIntId } from '@/api/types'

export interface CourseChapter {
  id: BigIntId
  name: string
  sortOrder: number
}

export interface DocumentTags {
  documentId: BigIntId
  chapterIds: BigIntId[]
  pointIds: BigIntId[]
}

export interface BatchAssignRequest {
  documentIds: BigIntId[]
  chapterIds: BigIntId[]
  pointIds: BigIntId[]
}

export interface BatchAssignResult {
  updatedDocuments: number
}

export interface ClassificationTag {
  id: BigIntId
  name: string
}

export interface DocumentClassification {
  documentId: BigIntId
  chapters: ClassificationTag[]
  points: ClassificationTag[]
}

function courseUrl(courseId: BigIntId): string {
  return `/v1/document-classification/courses/${encodeURIComponent(courseId)}`
}

export function listCourseChapters(
  courseId: BigIntId,
): Promise<CourseChapter[]> {
  return apiRequest<CourseChapter[]>(
    `${courseUrl(courseId)}/chapters`,
  )
}

export function createCourseChapter(
  courseId: BigIntId,
  data: {
    name: string
    sortOrder: number
  },
): Promise<string> {
  return apiRequest<string>(
    `${courseUrl(courseId)}/chapters`,
    {
      method: 'POST',
      body: data,
    },
  )
}

export function getDocumentTags(
  courseId: BigIntId,
  documentId: BigIntId,
): Promise<DocumentTags> {
  return apiRequest<DocumentTags>(
    `${courseUrl(courseId)}/documents/${encodeURIComponent(documentId)}/tags`,
  )
}

export function batchAssignDocumentTags(
  courseId: BigIntId,
  data: BatchAssignRequest,
): Promise<BatchAssignResult> {
  return apiRequest<BatchAssignResult>(
    `${courseUrl(courseId)}/documents/tags`,
    {
      method: 'PUT',
      body: data,
    },
  )
}

/**
 * 一次性获取当前课程全部可见文档的分类。
 */
export function listDocumentClassifications(
  courseId: BigIntId,
): Promise<DocumentClassification[]> {
  return apiRequest<DocumentClassification[]>(
    `${courseUrl(courseId)}/documents/tags`,
  )
}
