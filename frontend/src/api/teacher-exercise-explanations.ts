import { apiRequest } from '@/api/client'
import type { BigIntId } from '@/api/types'

export interface TeacherExerciseExplainKnowledgePoint {
  pointId: BigIntId | null
  title: string
  sessionCount: number
  studentCount: number
  requestCount: number
  latestActivityAt: string | null
}

export interface TeacherExerciseExplainHotQuestion {
  questionId: BigIntId
  stem: string
  knowledgePointTitle: string
  sessionCount: number
  studentCount: number
  requestCount: number
  latestActivityAt: string | null
}

export interface TeacherExerciseExplainAnalytics {
  sessionCount: number
  studentCount: number
  questionCount: number
  requestCount: number
  followUpCount: number
  latestActivityAt: string | null
  knowledgePoints: TeacherExerciseExplainKnowledgePoint[]
  hotQuestions: TeacherExerciseExplainHotQuestion[]
}

export interface TeacherExerciseExplainFilter {
  startDate?: string
  endDate?: string
}

export function getTeacherExerciseExplainAnalytics(
  courseId: BigIntId,
  filter: TeacherExerciseExplainFilter = {},
): Promise<TeacherExerciseExplainAnalytics> {

  const query = new URLSearchParams()

  if (filter.startDate) {
    query.set('startDate', filter.startDate)
  }

  if (filter.endDate) {
    query.set('endDate', filter.endDate)
  }

  const suffix = query.toString()
  const url =
    `/v1/teacher-exercise-explanations/courses/${encodeURIComponent(courseId)}`
    + (suffix ? `?${suffix}` : '')

  return apiRequest<TeacherExerciseExplainAnalytics>(url)
}
