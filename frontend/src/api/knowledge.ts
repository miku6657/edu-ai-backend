import { apiPdf, apiRequest } from '@/api/client'
import type { BigIntId } from '@/api/types'
import type {
  DocumentExpiryStatus,
} from '@/api/document-expiry'

export type DocumentStatus =
  | 'UPLOADED'
  | 'PARSING'
  | 'PROCESSING'
  | 'READY'
  | 'FAILED'
  | 'DELETING'
  | 'DELETE_FAILED'
  | 'DELETED'

export type DocumentVersionStatus =
  | 'PENDING'
  | 'CURRENT'
  | 'HISTORY'

/**
 * 知识库发布审核状态。
 *
 * 与文档解析状态、版本状态相互独立。
 */
export type DocumentReviewStatus =
  | 'DRAFT'
  | 'PENDING'
  | 'APPROVED'
  | 'REJECTED'

export type DocumentQualityStatus =
  | 'NORMAL'
  | 'SUSPENDED'

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
  resourceId: BigIntId
  uploaderId: BigIntId
  originalName: string
  fileSize: number
  versionNo: number
  versionStatus: DocumentVersionStatus

  resourceType: ResourceType
  sourceInfo: string | null

  // F2-04 知识库发布审核
  reviewStatus: DocumentReviewStatus
  reviewSubmitterId: BigIntId | null
  reviewSubmitTime: string | null
  reviewerId: BigIntId | null
  reviewTime: string | null
  reviewComment: string | null

  qualityStatus: DocumentQualityStatus
  qualityBlockReason: string | null
  qualityBlockedBy: BigIntId | null
  qualityBlockedAt: string | null

  status: DocumentStatus
  errorMessage: string | null
  createTime: string

  // F7-04 资料有效期管理
  validUntil: string | null

  updateResponsibleId: BigIntId | null

  expiryRemindDays: number

  expiryStatus: DocumentExpiryStatus

  remainingDays: number | null
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

/**
 * 文档版本信息。
 */
export interface DocumentVersion {
  documentId: BigIntId
  resourceId: BigIntId
  versionNo: number
  originalName: string
  versionStatus: DocumentVersionStatus
  status: DocumentStatus
  uploaderId: BigIntId
  createTime: string
}

/**
 * 查询资源的全部版本。
 */
export function listDocumentVersions(
  documentId: BigIntId,
): Promise<DocumentVersion[]> {
  return apiRequest<DocumentVersion[]>(
    `/documents/${encodeURIComponent(documentId)}/versions`,
  )
}

/**
 * 上传已有资源的新版本。
 */
export function uploadDocumentVersion(
  documentId: BigIntId,
  file: File,
): Promise<KnowledgeDoc> {
  const form = new FormData()
  form.append('file', file)

  return apiRequest<KnowledgeDoc>(
    `/documents/${encodeURIComponent(documentId)}/versions`,
    {
      method: 'POST',
      formData: form,
    },
  )
}

/**
 * 版本状态显示名称。
 */
export function versionStatusLabel(
  status: DocumentVersionStatus,
): string {
  switch (status) {
    case 'CURRENT':
      return '当前版本'
    case 'HISTORY':
      return '历史版本'
    case 'PENDING':
      return '待生效版本'
    default:
      return '未知状态'
  }
}

export interface MergeDocumentsRequest {
  documentIds: BigIntId[]
  filename: string
}

/**
 * 合并同类型课程PDF。
 */
export function mergeDocuments(
  courseId: BigIntId,
  data: MergeDocumentsRequest,
): Promise<KnowledgeDoc> {
  return apiRequest<KnowledgeDoc>(
    `/documents/course/${encodeURIComponent(courseId)}/merge`,
    {
      method: 'POST',
      body: data,
    },
  )
}

/**
 * 审核状态显示名称。
 */
export function documentReviewStatusLabel(
  status: DocumentReviewStatus | null | undefined,
): string {
  switch (status) {
    case 'DRAFT':
      return '待提交'
    case 'PENDING':
      return '待审核'
    case 'APPROVED':
      return '审核通过'
    case 'REJECTED':
      return '审核驳回'
    default:
      return '未知状态'
  }
}

/**
 * 审核状态标签样式。
 */
export function documentReviewStatusTagType(
  status: DocumentReviewStatus | null | undefined,
): 'info' | 'warning' | 'success' | 'danger' {
  switch (status) {
    case 'PENDING':
      return 'warning'
    case 'APPROVED':
      return 'success'
    case 'REJECTED':
      return 'danger'
    default:
      return 'info'
  }
}

/**
 * 判断文档是否正式发布。
 */
export function isDocumentPublished(
  document: KnowledgeDoc,
): boolean {
  return (
    document.status === 'READY'
    && document.versionStatus === 'CURRENT'
    && document.reviewStatus === 'APPROVED'
  )
}

/**
 * 判断文档是否允许修改知识切片。
 */
export function isDocumentKnowledgeEditable(
  document: KnowledgeDoc | null | undefined,
): boolean {
  return (
    document?.status === 'READY'
    && document.versionStatus === 'PENDING'
    && (
      document.reviewStatus === 'DRAFT'
      || document.reviewStatus === 'REJECTED'
    )
  )
}
