import { apiRequest } from '@/api/client'
import type { BigIntId } from '@/api/types'

export type DocumentExpiryStatus =
  | 'UNLIMITED'
  | 'VALID'
  | 'EXPIRING'
  | 'EXPIRED'

export interface DocumentExpiryVO {
  documentId: BigIntId
  courseId: BigIntId
  documentName: string
  validUntil: string | null
  updateResponsibleId: BigIntId | null
  expiryRemindDays: number
  expiryStatus: DocumentExpiryStatus
  remainingDays: number | null
}

export interface DocumentExpiryRequest {
  validUntil: string | null
  updateResponsibleId: BigIntId | null
  expiryRemindDays: number
}

function baseUrl(courseId: BigIntId): string {
  return `/v1/document-expiry/courses/${encodeURIComponent(courseId)}`
}

/**
 * 查询资料有效期配置。
 */
export function getDocumentExpiry(
  courseId: BigIntId,
  documentId: BigIntId,
): Promise<DocumentExpiryVO> {
  return apiRequest<DocumentExpiryVO>(
    `${baseUrl(courseId)}/documents/${encodeURIComponent(documentId)}`,
  )
}

/**
 * 修改资料有效期配置。
 */
export function updateDocumentExpiry(
  courseId: BigIntId,
  documentId: BigIntId,
  data: DocumentExpiryRequest,
): Promise<DocumentExpiryVO> {
  return apiRequest<DocumentExpiryVO>(
    `${baseUrl(courseId)}/documents/${encodeURIComponent(documentId)}`,
    {
      method: 'PUT',
      body: data,
    },
  )
}
