import { apiRequest } from '@/api/client'
import type { BigIntId } from '@/api/types'

export type DocumentSummaryStatus =
  | 'DRAFT'
  | 'CONFIRMED'
  | 'ARCHIVED'
  | 'SUPERSEDED'

export interface DocumentSummaryOutlineItem {
  title: string
  page: number | null
  description: string
  keyPoints: string[]
}

export interface DocumentSummary {
  id: BigIntId
  courseId: BigIntId
  documentId: BigIntId
  revision: number
  summary: string
  outline: DocumentSummaryOutlineItem[]
  status: DocumentSummaryStatus
  sourceChunkCount: number
  generatedBy: BigIntId
  generatedAt: string
  confirmedBy: BigIntId | null
  confirmedAt: string | null
}

function summaryUrl(
  courseId: BigIntId,
  documentId: BigIntId,
): string {

  return (
    `/v1/document-summary/courses/`
    + `${encodeURIComponent(courseId)}`
    + `/documents/${encodeURIComponent(documentId)}`
  )
}

/**
 * 查询资料摘要。
 */
export function getDocumentSummary(
  courseId: BigIntId,
  documentId: BigIntId,
): Promise<DocumentSummary | null> {

  return apiRequest<DocumentSummary | null>(
    summaryUrl(courseId, documentId),
  )
}

/**
 * AI生成资料摘要。
 */
export function generateDocumentSummary(
  courseId: BigIntId,
  documentId: BigIntId,
): Promise<DocumentSummary> {

  return apiRequest<DocumentSummary>(
    `${summaryUrl(courseId, documentId)}/generate`,
    {
      method: 'POST',
    },
  )
}

/**
 * 确认并发布资料摘要。
 */
export function confirmDocumentSummary(
  courseId: BigIntId,
  documentId: BigIntId,
  summaryId: BigIntId,
): Promise<DocumentSummary> {

  return apiRequest<DocumentSummary>(
    `${summaryUrl(courseId, documentId)}/${encodeURIComponent(summaryId)}/confirm`,
    {
      method: 'POST',
    },
  )
}
