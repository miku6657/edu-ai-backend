import { apiRequest } from '@/api/client'
import type { BigIntId } from '@/api/types'

export type QualityTaskStatus = 'IN_PROGRESS' | 'COMPLETED'

export type QualitySampleStatus = 'PENDING' | 'PASS' | 'FAIL'

export type QualityIssueStatus = 'OPEN' | 'RESOLVED' | 'CLOSED'

export type QualityIssueType =
  | 'MISSING_PAGE'
  | 'WRONG_VERSION'
  | 'DUPLICATE_CONTENT'
  | 'PARSE_ERROR'
  | 'WRONG_KNOWLEDGE_POINT'
  | 'OTHER'

export interface QualityTask {
  id: BigIntId
  courseId: BigIntId
  documentId: BigIntId
  resourceId: BigIntId
  versionNo: number
  documentName: string
  title: string
  sampleRate: number
  totalChunks: number
  sampleCount: number
  status: QualityTaskStatus
  creatorId: BigIntId
  completedBy: BigIntId | null
  completedTime: string | null
  createTime: string
  updateTime: string
}

export interface QualitySample {
  id: BigIntId
  taskId: BigIntId
  chunkId: BigIntId
  pageNo: number
  chunkIndex: number
  sectionTitle: string | null
  content: string
  contentHash: string | null
  duplicateCount: number
  status: QualitySampleStatus
  checkComment: string | null
  checkerId: BigIntId | null
  checkedTime: string | null
}

export interface QualityIssue {
  id: BigIntId
  taskId: BigIntId
  sampleId: BigIntId
  issueType: QualityIssueType
  description: string
  reporterId: BigIntId
  assigneeId: BigIntId
  status: QualityIssueStatus
  resolution: string | null
  replacementDocumentId: BigIntId | null
  resolvedTime: string | null
  reviewerId: BigIntId | null
  reviewComment: string | null
  reviewedTime: string | null
  createTime: string
}

export interface QualityProgress {
  totalSamples: number
  pendingSamples: number
  passedSamples: number
  failedSamples: number
  checkedSamples: number
  checkPercent: number
  passRate: number
  totalIssues: number
  openIssues: number
  resolvedIssues: number
  closedIssues: number
  canComplete: boolean
}

export interface QualityDetail {
  task: QualityTask
  progress: QualityProgress
  samples: QualitySample[]
  issues: QualityIssue[]
}

export interface QualityAssignee {
  id: BigIntId
  username: string
  role: string
}

export interface CreateQualityTaskRequest {
  title: string
  sampleRate: number
}

export interface QualityIssueInput {
  issueType: QualityIssueType
  description: string
  assigneeId: BigIntId
}

export interface CheckQualitySampleRequest {
  result: 'PASS' | 'FAIL'
  comment?: string
  issues: QualityIssueInput[]
}

export interface AssignQualityIssueRequest {
  assigneeId: BigIntId
}

export interface ResolveQualityIssueRequest {
  resolution: string
  replacementDocumentId?: BigIntId
}

export interface ReviewQualityIssueRequest {
  decision: 'APPROVE' | 'REJECT'
  comment?: string
}

function baseUrl(courseId: BigIntId): string {
  return `/v1/document-quality/courses/${encodeURIComponent(courseId)}`
}

function taskUrl(courseId: BigIntId, taskId: BigIntId): string {
  return `${baseUrl(courseId)}/tasks/${encodeURIComponent(taskId)}`
}

function issueUrl(
  courseId: BigIntId,
  taskId: BigIntId,
  issueId: BigIntId,
): string {
  return `${taskUrl(courseId, taskId)}/issues/${encodeURIComponent(issueId)}`
}

export function listQualityAssignees(
  courseId: BigIntId,
): Promise<QualityAssignee[]> {
  return apiRequest<QualityAssignee[]>(
    `${baseUrl(courseId)}/assignees`,
  )
}

export function createQualityTask(
  courseId: BigIntId,
  documentId: BigIntId,
  data: CreateQualityTaskRequest,
): Promise<QualityDetail> {
  return apiRequest<QualityDetail>(
    `${baseUrl(courseId)}/documents/${encodeURIComponent(documentId)}/tasks`,
    {
      method: 'POST',
      body: data,
    },
  )
}

export function listQualityTasks(
  courseId: BigIntId,
  documentId?: BigIntId,
): Promise<QualityTask[]> {
  const query = documentId
    ? `?documentId=${encodeURIComponent(documentId)}`
    : ''
  return apiRequest<QualityTask[]>(`${baseUrl(courseId)}/tasks${query}`)
}

export function getQualityDetail(
  courseId: BigIntId,
  taskId: BigIntId,
): Promise<QualityDetail> {
  return apiRequest<QualityDetail>(taskUrl(courseId, taskId))
}

export function checkQualitySample(
  courseId: BigIntId,
  taskId: BigIntId,
  sampleId: BigIntId,
  data: CheckQualitySampleRequest,
): Promise<QualityDetail> {
  return apiRequest<QualityDetail>(
    `${taskUrl(courseId, taskId)}/samples/${encodeURIComponent(sampleId)}/check`,
    {
      method: 'POST',
      body: data,
    },
  )
}

export function assignQualityIssue(
  courseId: BigIntId,
  taskId: BigIntId,
  issueId: BigIntId,
  data: AssignQualityIssueRequest,
): Promise<QualityDetail> {
  return apiRequest<QualityDetail>(
    `${issueUrl(courseId, taskId, issueId)}/assign`,
    {
      method: 'POST',
      body: data,
    },
  )
}

export function resolveQualityIssue(
  courseId: BigIntId,
  taskId: BigIntId,
  issueId: BigIntId,
  data: ResolveQualityIssueRequest,
): Promise<QualityDetail> {
  return apiRequest<QualityDetail>(
    `${issueUrl(courseId, taskId, issueId)}/resolve`,
    {
      method: 'POST',
      body: data,
    },
  )
}

export function reviewQualityIssue(
  courseId: BigIntId,
  taskId: BigIntId,
  issueId: BigIntId,
  data: ReviewQualityIssueRequest,
): Promise<QualityDetail> {
  return apiRequest<QualityDetail>(
    `${issueUrl(courseId, taskId, issueId)}/review`,
    {
      method: 'POST',
      body: data,
    },
  )
}

export function completeQualityTask(
  courseId: BigIntId,
  taskId: BigIntId,
): Promise<QualityDetail> {
  return apiRequest<QualityDetail>(
    `${taskUrl(courseId, taskId)}/complete`,
    { method: 'POST' },
  )
}

export const QUALITY_ISSUE_OPTIONS: Array<{
  value: QualityIssueType
  label: string
}> = [
  { value: 'MISSING_PAGE', label: '缺页或内容缺失' },
  { value: 'WRONG_VERSION', label: '资料版本错误' },
  { value: 'DUPLICATE_CONTENT', label: '重复内容' },
  { value: 'PARSE_ERROR', label: '解析错误' },
  { value: 'WRONG_KNOWLEDGE_POINT', label: '知识点关联错误' },
  { value: 'OTHER', label: '其他问题' },
]

export function qualityIssueTypeLabel(type: QualityIssueType): string {
  return QUALITY_ISSUE_OPTIONS.find(item => item.value === type)?.label ?? type
}

export function qualityTaskStatusLabel(status: QualityTaskStatus): string {
  return status === 'COMPLETED' ? '已完成' : '进行中'
}

export function qualitySampleStatusLabel(status: QualitySampleStatus): string {
  switch (status) {
    case 'PASS':
      return '合格'
    case 'FAIL':
      return '不合格'
    default:
      return '待检查'
  }
}

export function qualitySampleTagType(
  status: QualitySampleStatus,
): 'success' | 'danger' | 'info' {
  switch (status) {
    case 'PASS':
      return 'success'
    case 'FAIL':
      return 'danger'
    default:
      return 'info'
  }
}

export function qualityIssueStatusLabel(status: QualityIssueStatus): string {
  switch (status) {
    case 'RESOLVED':
      return '待复核'
    case 'CLOSED':
      return '已关闭'
    default:
      return '待整改'
  }
}

export function qualityIssueTagType(
  status: QualityIssueStatus,
): 'success' | 'warning' | 'danger' {
  switch (status) {
    case 'CLOSED':
      return 'success'
    case 'RESOLVED':
      return 'warning'
    default:
      return 'danger'
  }
}

/**
 * 文档质量控制状态。
 */
export interface QualityDocumentControl {
  documentId: BigIntId
  qualityStatus: 'NORMAL' | 'SUSPENDED'
  qualityBlockReason: string | null
  qualityBlockedBy: BigIntId | null
  qualityBlockedAt: string | null
  retrievable: boolean
}

export interface QualityControlActionRequest {
  reason: string
}

/**
 * 查询文档检索控制状态。
 */
export function getQualityDocumentControl(
  courseId: BigIntId,
  documentId: BigIntId,
): Promise<QualityDocumentControl> {
  return apiRequest<QualityDocumentControl>(
    `${baseUrl(courseId)}/documents/`
      + `${encodeURIComponent(documentId)}/control`,
  )
}

/**
 * 暂停资料检索。
 */
export function suspendQualityDocument(
  courseId: BigIntId,
  documentId: BigIntId,
  reason: string,
): Promise<QualityDocumentControl> {
  return apiRequest<QualityDocumentControl>(
    `${baseUrl(courseId)}/documents/`
      + `${encodeURIComponent(documentId)}/suspend`,
    {
      method: 'POST',
      body: { reason },
    },
  )
}

/**
 * 恢复资料检索。
 */
export function resumeQualityDocument(
  courseId: BigIntId,
  documentId: BigIntId,
  reason: string,
): Promise<QualityDocumentControl> {
  return apiRequest<QualityDocumentControl>(
    `${baseUrl(courseId)}/documents/`
      + `${encodeURIComponent(documentId)}/resume`,
    {
      method: 'POST',
      body: { reason },
    },
  )
}
