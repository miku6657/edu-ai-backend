import { apiRequest } from '@/api/client'
import type { BigIntId } from '@/api/types'

export interface KnowledgePoint {
  id: BigIntId
  courseId: BigIntId
  title: string
  chapterId: BigIntId | null
  parentId: BigIntId | null
  difficulty: number
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

/**
 * F6-02：知识点综合诊断。
 */
export interface StudentWeakPointDiagnostic {
  pointId: BigIntId
  title: string

  effectiveQuestionCount: number
  questionSessionCount: number
  questionInferredLevel: number

  practiceQuestionCount: number
  masteredQuestionCount: number
  practiceMasteryRate: number | null

  selfAssessmentLevel: number
  practiceLevel: number | null

  diagnosisLevel: number
  diagnosisLabel: string
  diagnosisReason: string
  suggestion: string

  lastPracticedAt: string | null
}

/**
 * 获取课程知识点综合诊断。
 */
export function getStudentWeakPointDiagnosis(
  courseId: BigIntId,
): Promise<StudentWeakPointDiagnostic[]> {

  return apiRequest<StudentWeakPointDiagnostic[]>(
    `/v1/learning-paths/courses/${encodeURIComponent(courseId)}/weak-point-diagnosis`,
  )
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
    chapterId?: BigIntId | null
    parentId?: BigIntId | null
    difficulty?: number
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

/**
 * 需要顺延的现有学习阶段任务
 */
export interface WeakReviewMove {
  itemId: BigIntId
  oldWeek: number
  newWeek: number
  oldDueDate: string
  newDueDate: string
}

/**
 * 需要新增的复习阶段任务
 */
export interface WeakReviewAddition {
  pointId: BigIntId
  title: string
  segmentNo: number
  weekNo: number
  plannedMinutes: number
  dueDate: string
}

/**
 * 薄弱点实际排期预览
 */
export interface WeakReviewPlan {
  feasible: boolean
  fingerprint: string
  message: string
  additionalMinutes: number
  moves: WeakReviewMove[]
  additions: WeakReviewAddition[]
}

/**
 * 预览追加复习任务的排期
 *
 * 不修改学习路径和DDL。
 */
export function previewWeakReviewPlan(
  pathId: BigIntId,
): Promise<WeakReviewPlan> {
  return apiRequest<WeakReviewPlan>(
    `/v1/learning-paths/${encodeURIComponent(pathId)}/weak-review-plan`,
  )
}

/**
 * 确认追加复习任务
 *
 * 后端负责校验指纹，并在事务内同步个人DDL。
 */
export function applyWeakReviewPlan(
  pathId: BigIntId,
  fingerprint: string,
): Promise<WeakReviewPlan> {
  return apiRequest<WeakReviewPlan>(
    `/v1/learning-paths/${encodeURIComponent(pathId)}/apply-weak-review`,
    {
      method: 'POST',
      body: { fingerprint },
    },
  )
}

/**
 * 知识地图章节节点。
 */
export interface KnowledgeMapChapter {
  id: BigIntId
  name: string
  sortOrder: number
}

/**
 * 知识地图知识点节点。
 */
export interface KnowledgeMapPoint {
  id: BigIntId
  title: string
  chapterId: BigIntId | null
  parentId: BigIntId | null
  difficulty: number
  estimatedMinutes: number
  sortOrder: number
  matchKeywords: string
}

/**
 * 知识点先修依赖关系。
 */
export interface KnowledgeMapDependency {
  pointId: BigIntId
  prerequisiteId: BigIntId
}

/**
 * 知识地图统计信息。
 */
export interface KnowledgeMapStatistics {
  chapterCount: number
  pointCount: number
  dependencyCount: number
  unclassifiedCount: number
  basicCount: number
  intermediateCount: number
  advancedCount: number
  totalEstimatedMinutes: number
}

/**
 * 完整课程知识地图。
 */
export interface CourseKnowledgeMap {
  courseId: BigIntId
  chapters: KnowledgeMapChapter[]
  points: KnowledgeMapPoint[]
  dependencies: KnowledgeMapDependency[]
  statistics: KnowledgeMapStatistics
}

/**
 * 知识点完整维护请求。
 */
export interface UpdateKnowledgePointRequest {
  title: string
  estimatedMinutes: number
  sortOrder: number
  matchKeywords: string
  chapterId: BigIntId | null
  parentId: BigIntId | null
  difficulty: number
}

/**
 * 修改知识点。
 */
export function updateKnowledgePoint(
  courseId: BigIntId,
  pointId: BigIntId,
  data: UpdateKnowledgePointRequest,
): Promise<string> {
  return apiRequest<string>(
    `/v1/learning-paths/courses/${encodeURIComponent(courseId)}/points/${encodeURIComponent(pointId)}`,
    {
      method: 'PUT',
      body: data,
    },
  )
}

/**
 * 删除知识点。
 */
export function deleteKnowledgePoint(
  courseId: BigIntId,
  pointId: BigIntId,
): Promise<string> {
  return apiRequest<string>(
    `/v1/learning-paths/courses/${encodeURIComponent(courseId)}/points/${encodeURIComponent(pointId)}`,
    {
      method: 'DELETE',
    },
  )
}

/**
 * 解除知识点先修关系。
 */
export function removeKnowledgeDependency(
  courseId: BigIntId,
  pointId: BigIntId,
  prerequisiteId: BigIntId,
): Promise<string> {
  return apiRequest<string>(
    `/v1/learning-paths/courses/${encodeURIComponent(courseId)}/dependencies/${encodeURIComponent(pointId)}/${encodeURIComponent(prerequisiteId)}`,
    {
      method: 'DELETE',
    },
  )
}

/**
 * 查询课程知识地图。
 */
export function getCourseKnowledgeMap(
  courseId: BigIntId,
): Promise<CourseKnowledgeMap> {
  return apiRequest<CourseKnowledgeMap>(
    `/v1/learning-paths/courses/${encodeURIComponent(courseId)}/knowledge-map`,
  )
}

/**
 * 知识点难度显示。
 */
export function knowledgeDifficultyLabel(
  difficulty: number,
): string {
  switch (difficulty) {
    case 1:
      return '基础'
    case 2:
      return '中等'
    case 3:
      return '进阶'
    default:
      return '未知'
  }
}
