import { apiPdf, apiRequest } from '@/api/client'
import type { BigIntId } from '@/api/types'

export type DocumentStatus =
  | 'UPLOADED'
  | 'PARSING'
  | 'READY'
  | 'FAILED'
  | 'DELETING'
  | 'DELETE_FAILED'
  | 'DELETED'

export type ResourceType =
  | 'LESSON_PLAN'
  | 'COURSEWARE'
  | 'EXERCISE'
  | 'PRACTICAL_GUIDE'
  | 'OTHER'

export type ResourceTypeSelection =
  | 'AUTO'
  | ResourceType

export const RESOURCE_TYPE_OPTIONS = [
  { value: 'AUTO', label: '自动识别' },
  { value: 'LESSON_PLAN', label: '教案' },
  { value: 'COURSEWARE', label: '课件' },
  { value: 'EXERCISE', label: '习题' },
  { value: 'PRACTICAL_GUIDE', label: '实训指导' },
  { value: 'OTHER', label: '其他资料' },
] as const

export function resourceTypeLabel(
  type: ResourceType | null | undefined,
): string {
  return RESOURCE_TYPE_OPTIONS.find(
    (item) => item.value === type,
  )?.label ?? '其他资料'
}

export interface KnowledgeDoc {
  id: BigIntId
  courseId: BigIntId
  uploaderId: BigIntId
  originalName: string
  fileSize: number

  resourceType: ResourceType
  sourceInfo: string | null

  status: DocumentStatus
  errorMessage: string | null
  createTime: string
}

export interface UploadDocumentOptions {
  resourceType?: ResourceTypeSelection
  sourceInfo?: string
}

export function listDocs(
  courseId: BigIntId,
): Promise<KnowledgeDoc[]> {
  return apiRequest<KnowledgeDoc[]>(
    `/documents/course/${encodeURIComponent(courseId)}`,
  )
}

export function uploadDoc(
  courseId: BigIntId,
  file: File,
  options: UploadDocumentOptions = {},
): Promise<KnowledgeDoc> {
  const form = new FormData()
  form.append('file', file)
  form.append(
    'resourceType',
    options.resourceType ?? 'AUTO',
  )

  if (options.sourceInfo?.trim()) {
    form.append(
      'sourceInfo',
      options.sourceInfo.trim(),
    )
  }

  return apiRequest<KnowledgeDoc>(
    `/documents/course/${encodeURIComponent(courseId)}`,
    {
      method: 'POST',
      formData: form,
    },
  )
}

export function deleteDoc(
  docId: BigIntId,
): Promise<string> {
  return apiRequest<string>(
    `/documents/${encodeURIComponent(docId)}`,
    {
      method: 'DELETE',
    },
  )
}

export function retryDoc(
  docId: BigIntId,
): Promise<string> {
  return apiRequest<string>(
    `/documents/${encodeURIComponent(docId)}/retry`,
    {
      method: 'POST',
    },
  )
}

export function downloadDoc(
  docId: BigIntId,
): string {
  return `/api/documents/${encodeURIComponent(docId)}/download`
}

/**
 * 获取在线预览所需的PDF文件。
 */
export function previewDocument(
  documentId: BigIntId,
  signal?: AbortSignal,
): Promise<Blob> {
  return apiPdf(
    `/documents/${encodeURIComponent(documentId)}/preview`,
    signal,
  )
}

/**
 * 携带JWT下载PDF文件。
 */
export function downloadDocumentFile(
  documentId: BigIntId,
  signal?: AbortSignal,
): Promise<Blob> {
  return apiPdf(
    `/documents/${encodeURIComponent(documentId)}/download`,
    signal,
  )
}
