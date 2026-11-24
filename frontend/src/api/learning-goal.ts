import { apiRequest } from '@/api/client'
import type { BigIntId } from '@/api/types'

export interface LearningGoal {
  id: BigIntId
  courseId: BigIntId
  studentId: BigIntId
  goalText: string
  weeklyHours: number
  targetDate: string
  status: 'DRAFT' | 'CONFIRMED'
  confirmedAt: string | null
  createTime: string
  updateTime: string
}

export interface LearningGoalRequest {
  goalText: string
  weeklyHours: number
  targetDate: string
}

export function listLearningGoals(): Promise<LearningGoal[]> {
  return apiRequest<LearningGoal[]>('/v1/learning-goals')
}

export function getCourseLearningGoal(
  courseId: BigIntId,
): Promise<LearningGoal | null> {
  return apiRequest<LearningGoal | null>(
    `/v1/learning-goals/course/${encodeURIComponent(courseId)}`,
  )
}

export function saveLearningGoalDraft(
  courseId: BigIntId,
  data: LearningGoalRequest,
): Promise<LearningGoal> {
  return apiRequest<LearningGoal>(
    `/v1/learning-goals/course/${encodeURIComponent(courseId)}`,
    {
      method: 'PUT',
      body: data,
    },
  )
}

export function confirmLearningGoal(
  goalId: BigIntId,
): Promise<LearningGoal> {
  return apiRequest<LearningGoal>(
    `/v1/learning-goals/${encodeURIComponent(goalId)}/confirm`,
    { method: 'POST' },
  )
}
