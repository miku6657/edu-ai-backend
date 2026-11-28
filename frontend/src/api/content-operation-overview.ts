import { apiRequest } from '@/api/client'
import type { BigIntId } from '@/api/types'

export interface ContentDocumentStats {
  publishedCount: number
  expiringCount: number
  expiredCount: number
  suspendedCount: number
  confirmedSummaryCount: number
}

export interface ContentQualityStats {
  openIssueCount: number
  resolvedIssueCount: number
}

export interface ContentHotQuestionStats {
  draftCount: number
  pendingReviewCount: number
  publishedCount: number
  rejectedCount: number
  offlineCount: number
}

export interface ContentUsageStats {
  previewCount: number
  downloadCount: number
  materialStudentCount: number

  questionCount: number
  questionStudentCount: number

  recommendationExposureCount: number
  recommendationClickCount: number
  recommendationClickRate: number | null

  operationCount: number
}

export interface ContentOperationTypeStat {
  operationType: string
  operationName: string
  operationCount: number
}

export interface ContentDailyUsage {
  date: string
  previewCount: number
  downloadCount: number
  questionCount: number
  recommendationExposureCount: number
  recommendationClickCount: number
  operationCount: number
}

export interface ContentOperationOverview {
  courseId: BigIntId | null

  startDate: string
  endDate: string

  documents: ContentDocumentStats

  quality: ContentQualityStats

  hotQuestions: ContentHotQuestionStats

  usage: ContentUsageStats

  operationTypes: ContentOperationTypeStat[]

  dailyUsage: ContentDailyUsage[]

  generatedAt: string
}

export interface ContentOperationOverviewQuery {
  courseId?: BigIntId
  startDate?: string
  endDate?: string
}

export function getContentOperationOverview(
  query: ContentOperationOverviewQuery = {},
): Promise<ContentOperationOverview> {

  const params = new URLSearchParams()

  if (query.courseId) {
    params.set('courseId', query.courseId)
  }

  if (query.startDate) {
    params.set('startDate', query.startDate)
  }

  if (query.endDate) {
    params.set('endDate', query.endDate)
  }

  const search = params.toString()

  return apiRequest<ContentOperationOverview>(
    `/v1/admin/content-operation-overview${search ? `?${search}` : ''}`,
  )
}
