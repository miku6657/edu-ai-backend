import { apiRequest } from '@/api/client'
import type { BigIntId } from '@/api/types'

export interface ContentOperationLogItem {
  id: BigIntId
  courseId: BigIntId
  courseName: string
  documentId: BigIntId | null
  documentName: string | null
  operatorId: BigIntId
  operatorName: string
  operationType: string
  operationName: string
  description: string | null
  beforeValue: string | null
  afterValue: string | null
  createTime: string
}

export interface ContentOperationLogPage {
  current: number
  size: number
  total: number
  pages: number
  records: ContentOperationLogItem[]
}

export interface ContentOperationLogQuery {
  current?: number
  size?: number
  courseId?: BigIntId
  operatorId?: BigIntId
  operationType?: string
  startTime?: string
  endTime?: string
}

/**
 * 管理员分页查询课程内容运营日志。
 */
export function listContentOperationLogs(
  query: ContentOperationLogQuery,
): Promise<ContentOperationLogPage> {

  const params = new URLSearchParams()

  if (query.current !== undefined) {
    params.set('current', String(query.current))
  }

  if (query.size !== undefined) {
    params.set('size', String(query.size))
  }

  if (query.courseId) {
    params.set('courseId', query.courseId)
  }

  if (query.operatorId) {
    params.set('operatorId', query.operatorId)
  }

  if (query.operationType) {
    params.set('operationType', query.operationType)
  }

  if (query.startTime) {
    params.set('startTime', query.startTime)
  }

  if (query.endTime) {
    params.set('endTime', query.endTime)
  }

  return apiRequest<ContentOperationLogPage>(
    `/v1/admin/content-operation-logs?${params.toString()}`,
  )
}
