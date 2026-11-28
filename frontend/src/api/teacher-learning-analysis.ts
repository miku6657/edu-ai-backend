import { apiRequest } from '@/api/client'
import type { BigIntId } from '@/api/types'

export interface StudentProgress {
  studentId: BigIntId
  studentName: string
  username: string

  submittedAttemptCount: number

  objectiveAnswerCount: number
  correctObjectiveCount: number

  exerciseAccuracyRate: number | null

  questionCount: number

  completedTaskCount: number
  totalTaskCount: number

  taskCompletionRate: number | null

  materialAccessCount: number

  lastActiveAt: string | null
}

export interface KnowledgePointLearningStat {
  pointId: BigIntId
  title: string

  questionCount: number
  questionStudentCount: number

  practiceQuestionCount: number
  masteredQuestionCount: number
  practiceMasteryRate: number | null
  practiceStudentCount: number

  selfAssessmentCount: number
  averageSelfAssessmentLevel: number | null

  latestAskedAt: string | null
  lastPracticedAt: string | null
}

export interface TeacherCourseLearningAnalysis {
  courseId: BigIntId
  courseName: string
  studentCount: number
  activeTaskCount: number
  taskAssignmentCount: number
  completedTaskAssignmentCount: number
  taskCompletionRate: number | null
  publishedExerciseCount: number
  submittedAttemptCount: number
  participatingStudentCount: number
  exerciseParticipationRate: number | null
  materialSummary: MaterialSummary

  knowledgePoints: KnowledgePointLearningStat[]

  students: StudentProgress[]

  materials: MaterialUsage[]
  chapters: ChapterProgress[]
  lowCompletionChapters: ChapterProgress[]
  generatedAt: string
}

export interface MaterialSummary {
  documentCount: number
  accessedDocumentCount: number
  accessCount: number
  previewCount: number
  downloadCount: number
  activeStudentCount: number
}

export interface MaterialUsage {
  documentId: BigIntId
  documentName: string
  resourceType: string | null
  chapterNames: string | null
  previewCount: number
  downloadCount: number
  totalAccessCount: number
  studentCount: number
  lastAccessAt: string | null
}

export interface ChapterProgress {
  chapterId: BigIntId
  chapterName: string
  questionCount: number
  studentCount: number
  totalExpectedAnswers: number
  completedStudentQuestionCount: number
  completionRate: number | null
  lowCompletion: boolean
}

export function getTeacherCourseLearningAnalysis(
  courseId: BigIntId,
): Promise<TeacherCourseLearningAnalysis> {
  return apiRequest<TeacherCourseLearningAnalysis>(
    `/v1/teacher/courses/${encodeURIComponent(courseId)}/learning-analysis`,
  )
}
