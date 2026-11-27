import { apiRequest } from '@/api/client'

export interface StudentLearningDailyRecord {
  date: string
  exerciseCount: number
  questionCount: number
  completedTaskCount: number
}

export interface StudentLearningAnalysis {
  exerciseCount: number
  exerciseAccuracyRate: number | null
  objectiveAnswerCount: number
  questionCount: number
  completedTaskCount: number
  totalTaskCount: number
  weekStart: string
  weekEnd: string
  weeklyRecords: StudentLearningDailyRecord[]
}

/**
 * 获取当前学生的学习概况和本周学习记录。
 */
export function getStudentLearningAnalysis():
  Promise<StudentLearningAnalysis> {

  return apiRequest<StudentLearningAnalysis>(
    '/v1/student/learning-analysis',
  )
}
