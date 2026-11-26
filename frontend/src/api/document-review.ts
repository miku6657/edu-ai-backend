import { apiRequest } from '@/api/client'
import type { BigIntId } from '@/api/types'
import type {
  DocumentReviewStatus,
  DocumentStatus,
  DocumentVersionStatus,
} from '@/api/knowledge'
import type { ChunkKnowledgeProgress } from '@/api/document-chunk'

/**
 * 审核操作类型。
 */
export type DocumentReviewAction =
  | 'SUBMIT'
  | 'APPROVE'
  | 'REJECT'
  | 'WITHDRAW'

/**
 * 审核决定。
 */
export type DocumentReviewDecision = 'APPROVE' | 'REJECT'

/**
 * 提交或撤回审核请求。
 */
export interface DocumentReviewSubmitRequest {
  comment?: string
}

/**
 * 审核决定请求。
 */
export interface DocumentReviewDecisionRequest {
  decision: DocumentReviewDecision
  comment?: string
}

/**
 * 单条审核历史。
 */
export interface DocumentReviewRecord {
  id: BigIntId
  operatorId: BigIntId
  action: DocumentReviewAction
  previousStatus: DocumentReviewStatus
  nextStatus: DocumentReviewStatus
  comment: string | null
  createTime: string
}

/**
 * 文档发布审核详情。
 */
export interface DocumentReviewDetail {
  documentId: BigIntId
  courseId: BigIntId
  resourceId: BigIntId
  originalName: string
  versionNo: number
  status: DocumentStatus
  versionStatus: DocumentVersionStatus
  reviewStatus: DocumentReviewStatus
  reviewSubmitterId: BigIntId | null
  reviewSubmitTime: string | null
  reviewerId: BigIntId | null
  reviewTime: string | null
  reviewComment: string | null
  knowledgeProgress: ChunkKnowledgeProgress | null
  canSubmit: boolean
  canReview: boolean
  canApprove: boolean
  qualityBlockReason: string | null
  canWithdraw: boolean
  published: boolean
  records: DocumentReviewRecord[]
}

function documentReviewUrl(
  courseId: BigIntId,
  documentId: BigIntId,
): string {
  return (
    '/v1/document-reviews/courses/'
    + encodeURIComponent(courseId)
    + '/documents/'
    + encodeURIComponent(documentId)
  )
}

/**
 * 查询文档审核详情。
 */
export function getDocumentReviewDetail(
  courseId: BigIntId,
  documentId: BigIntId,
): Promise<DocumentReviewDetail> {
  return apiRequest<DocumentReviewDetail>(
    documentReviewUrl(courseId, documentId),
  )
}

/**
 * 提交知识库发布审核。
 */
export function submitDocumentReview(
  courseId: BigIntId,
  documentId: BigIntId,
  data: DocumentReviewSubmitRequest = {},
): Promise<DocumentReviewDetail> {
  return apiRequest<DocumentReviewDetail>(
    `${documentReviewUrl(courseId, documentId)}/submit`,
    {
      method: 'POST',
      body: data,
    },
  )
}

/**
 * 审核通过或驳回。
 */
export function decideDocumentReview(
  courseId: BigIntId,
  documentId: BigIntId,
  data: DocumentReviewDecisionRequest,
): Promise<DocumentReviewDetail> {
  return apiRequest<DocumentReviewDetail>(
    `${documentReviewUrl(courseId, documentId)}/decision`,
    {
      method: 'POST',
      body: data,
    },
  )
}

/**
 * 撤回发布审核。
 */
export function withdrawDocumentReview(
  courseId: BigIntId,
  documentId: BigIntId,
  data: DocumentReviewSubmitRequest = {},
): Promise<DocumentReviewDetail> {
  return apiRequest<DocumentReviewDetail>(
    `${documentReviewUrl(courseId, documentId)}/withdraw`,
    {
      method: 'POST',
      body: data,
    },
  )
}

/**
 * 审核操作显示名称。
 */
export function reviewActionLabel(action: DocumentReviewAction): string {
  switch (action) {
    case 'SUBMIT':
      return '提交审核'
    case 'APPROVE':
      return '审核通过'
    case 'REJECT':
      return '审核驳回'
    case 'WITHDRAW':
      return '撤回审核'
    default:
      return action
  }
}

/**
 * 审核历史时间线样式。
 */
export function reviewActionTagType(
  action: DocumentReviewAction,
): 'primary' | 'success' | 'danger' | 'info' {
  switch (action) {
    case 'SUBMIT':
      return 'primary'
    case 'APPROVE':
      return 'success'
    case 'REJECT':
      return 'danger'
    default:
      return 'info'
  }
}
