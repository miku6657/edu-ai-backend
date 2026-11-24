import { apiRequest } from '@/api/client'
import type { BigIntId } from '@/api/types'

export interface KnowledgePoint {
  id: BigIntId
  courseId: BigIntId
  title: string
  matchKeywords: string | null
  estimatedMinutes: number
  sortOrder: number
}

export interface WeakKnowledgePoint {
  pointId: BigIntId
  title: string
  effectiveCount: number
  sessionCount: number
  inferredLevel: number
}

export interface WeakReviewItem {
  pointId: BigIntId
  title: string
  effectiveCount: number
  sessionCount: number
  manualLevel: number
  inferredLevel: number
  currentMinutes: number
  recommendedMinutes: number
  additionalMinutes: number
}

export interface WeakReviewPreview {
  pathId: BigIntId
  additionalTotalMinutes: number
  message: string
  items: WeakReviewItem[]
}

export interface LearningPathItem {
  id: BigIntId
  pointId: BigIntId
  title: string
  segmentNo: number
  sequenceNo: number
  weekNo: number
  plannedMinutes: number
  plannedDueDate: string
  status: 'PENDING' | 'COMPLETED'
}

export interface LearningPath {
  pathId: BigIntId
  goalId: BigIntId
  courseId: BigIntId
  totalMinutes: number
  totalWeeks: number
  status: string
  items: LearningPathItem[]
}

export interface LearningPathProgress {
  total: number
  completed: number
  percentage: number
}

export interface AdjustmentItem {
  itemId: BigIntId
  oldWeek: number
  newWeek: number
  oldDueDate: string
  newDueDate: string
}

export interface AdjustmentPreview {
  feasible: boolean
  fingerprint: string
  message: string
  changedCount: number
  items: AdjustmentItem[]
}

export function listKnowledgePoints(
  courseId: BigIntId,
): Promise<KnowledgePoint[]> {
  return apiRequest<KnowledgePoint[]>(
    `/v1/learning-paths/courses/${encodeURIComponent(courseId)}/points`,
  )
}

export function addKnowledgePoint(
  courseId: BigIntId,
  data: {
    title: string
    estimatedMinutes: number
    sortOrder: number
    matchKeywords?: string
  },
): Promise<{ pointId: BigIntId }> {
  return apiRequest<{ pointId: BigIntId }>(
    `/v1/learning-paths/courses/${encodeURIComponent(courseId)}/points`,
    { method: 'POST', body: data },
  )
}

export function addKnowledgeDependency(
  courseId: BigIntId,
  pointId: BigIntId,
  prerequisiteId: BigIntId,
): Promise<string> {
  return apiRequest<string>(
    `/v1/learning-paths/courses/${encodeURIComponent(courseId)}/dependencies`,
    {
      method: 'POST',
      body: { pointId, prerequisiteId },
    },
  )
}

export function updateKnowledgeMastery(
  courseId: BigIntId,
  pointId: BigIntId,
  level: number,
): Promise<string> {
  return apiRequest<string>(
    `/v1/learning-paths/courses/${encodeURIComponent(courseId)}/points/${encodeURIComponent(pointId)}/mastery`,
    { method: 'PUT', body: { level } },
  )
}

export function updateKnowledgePointKeywords(
  courseId: BigIntId,
  pointId: BigIntId,
  matchKeywords: string,
): Promise<string> {
  return apiRequest<string>(
    `/v1/learning-paths/courses/${encodeURIComponent(courseId)}/points/${encodeURIComponent(pointId)}/keywords`,
    {
      method: 'PUT',
      body: { matchKeywords },
    },
  )
}

export function generateLearningPath(
  goalId: BigIntId,
): Promise<LearningPath> {
  return apiRequest<LearningPath>(
    `/v1/learning-paths/goals/${encodeURIComponent(goalId)}/generate`,
    { method: 'POST' },
  )
}

/**
 * 根据学习目标查找已有学习路径。
 * 不存在时返回 null，不会生成新的路径。
 */
export function getLearningPathByGoal(
  goalId: BigIntId,
): Promise<LearningPath | null> {
  return apiRequest<LearningPath | null>(
    `/v1/learning-paths/goals/${encodeURIComponent(goalId)}`,
  )
}

export function getLearningPath(
  pathId: BigIntId,
): Promise<LearningPath> {
  return apiRequest<LearningPath>(
    `/v1/learning-paths/${encodeURIComponent(pathId)}`,
  )
}

export function getLearningPathProgress(
  pathId: BigIntId,
): Promise<LearningPathProgress> {
  return apiRequest<LearningPathProgress>(
    `/v1/learning-paths/${encodeURIComponent(pathId)}/progress`,
  )
}

/** 查看调整建议：不会修改任何任务 */
export function previewLearningPathAdjustment(
  pathId: BigIntId,
): Promise<AdjustmentPreview> {
  return apiRequest<AdjustmentPreview>(
    `/v1/learning-paths/${encodeURIComponent(pathId)}/adjustment-preview`,
  )
}

/** 学生确认后应用调整 */
export function applyLearningPathAdjustment(
  pathId: BigIntId,
  fingerprint: string,
): Promise<AdjustmentPreview> {
  return apiRequest<AdjustmentPreview>(
    `/v1/learning-paths/${encodeURIComponent(pathId)}/adjust`,
    {
      method: 'POST',
      body: { fingerprint },
    },
  )
}

export function getMyKnowledgeMastery(
  courseId: BigIntId,
): Promise<Record<string, number>> {
  return apiRequest<Record<string, number>>(
    `/v1/learning-paths/courses/${encodeURIComponent(courseId)}/mastery`,
  )
}

export function listWeakKnowledgePoints(
  courseId: BigIntId,
): Promise<WeakKnowledgePoint[]> {
  return apiRequest<WeakKnowledgePoint[]>(
    `/v1/learning-paths/courses/${encodeURIComponent(courseId)}/weak-points`,
  )
}

/**
 * 查看高频提问带来的额外复习时间建议。
 * 此接口不修改学习路径。
 */
export function previewWeakReview(
  pathId: BigIntId,
): Promise<WeakReviewPreview> {
  return apiRequest<WeakReviewPreview>(
    `/v1/learning-paths/${encodeURIComponent(pathId)}/weak-review-preview`,
  )
}
