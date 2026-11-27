import { apiRequest } from '@/api/client'
import type { BigIntId } from '@/api/types'

export type ExerciseStatus = 'DRAFT' | 'PUBLISHED' | 'CLOSED'
export type ExerciseQuestionType =
  | 'SINGLE_CHOICE'
  | 'MULTIPLE_CHOICE'
  | 'TRUE_FALSE'
  | 'SHORT_ANSWER'

export interface ExerciseOption {
  label: string
  content: string
}

export interface ExerciseQuestionRequest {
  knowledgePointId: BigIntId
  questionType: ExerciseQuestionType
  stem: string
  options: ExerciseOption[]
  correctAnswer: string
  explanation: string
  score: number
}

export interface ExerciseSaveRequest {
  title: string
  description: string
  difficulty: number
  estimatedMinutes: number
  questions: ExerciseQuestionRequest[]
}

export interface ExerciseKnowledgePoint {
  id: BigIntId
  title: string
  difficulty: number
}

export interface ExerciseQuestion {
  id: BigIntId
  knowledgePointId: BigIntId
  knowledgePointTitle: string
  questionType: ExerciseQuestionType
  stem: string
  options: ExerciseOption[] | null
  correctAnswer: string | null
  explanation: string | null
  score: number
  sortOrder: number
}

export interface Exercise {
  id: BigIntId
  courseId: BigIntId
  courseName: string
  title: string
  description: string | null
  difficulty: number
  estimatedMinutes: number
  status: ExerciseStatus
  createdBy: BigIntId
  publishedAt: string | null
  createdAt: string
  updatedAt: string
  questionCount: number
  questions: ExerciseQuestion[]
}

export function listManagedExercises(courseId?: BigIntId): Promise<Exercise[]> {
  const query = courseId
    ? `?courseId=${encodeURIComponent(courseId)}`
    : ''
  return apiRequest<Exercise[]>(`/v1/exercises/manage${query}`)
}

export function listExerciseKnowledgePoints(
  courseId: BigIntId,
): Promise<ExerciseKnowledgePoint[]> {
  return apiRequest<ExerciseKnowledgePoint[]>(
    `/v1/exercises/courses/${encodeURIComponent(courseId)}/knowledge-points`,
  )
}

export function getExercise(exerciseId: BigIntId): Promise<Exercise> {
  return apiRequest<Exercise>(`/v1/exercises/${encodeURIComponent(exerciseId)}`)
}

export function createExercise(
  courseId: BigIntId,
  request: ExerciseSaveRequest,
): Promise<Exercise> {
  return apiRequest<Exercise>(
    `/v1/exercises/courses/${encodeURIComponent(courseId)}`,
    { method: 'POST', body: request },
  )
}

export function updateExercise(
  exerciseId: BigIntId,
  request: ExerciseSaveRequest,
): Promise<Exercise> {
  return apiRequest<Exercise>(
    `/v1/exercises/${encodeURIComponent(exerciseId)}`,
    { method: 'PUT', body: request },
  )
}

export function publishExercise(exerciseId: BigIntId): Promise<Exercise> {
  return apiRequest<Exercise>(
    `/v1/exercises/${encodeURIComponent(exerciseId)}/publish`,
    { method: 'POST' },
  )
}

export function closeExercise(exerciseId: BigIntId): Promise<Exercise> {
  return apiRequest<Exercise>(
    `/v1/exercises/${encodeURIComponent(exerciseId)}/close`,
    { method: 'POST' },
  )
}

export function deleteExerciseDraft(exerciseId: BigIntId): Promise<void> {
  return apiRequest<void>(`/v1/exercises/${encodeURIComponent(exerciseId)}`, {
    method: 'DELETE',
  })
}
