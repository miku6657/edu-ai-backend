import { apiRequest } from '@/api/client'
import type { BigIntId } from '@/api/types'

export interface LearningReportRequest {
  startDate: string
  endDate: string
}

export interface LearningReport {
  id: BigIntId
  studentId: BigIntId
  studentName: string
  courseId: BigIntId
  courseName: string
  title: string
  periodStart: string
  periodEnd: string
  exerciseCount: number
  objectiveAnswerCount: number
  correctObjectiveCount: number
  exerciseAccuracyRate: number | null
  questionCount: number
  completedTaskCount: number
  totalTaskCount: number
  weakPointCount: number
  createdAt: string
}

export interface LearningReportWeakPoint {
  pointId: BigIntId
  title: string
  effectiveQuestionCount: number
  questionSessionCount: number
  questionInferredLevel: number
  practiceQuestionCount: number
  masteredQuestionCount: number
  practiceMasteryRate: number | null
  diagnosisLevel: number
  diagnosisLabel: string
  diagnosisReason: string
  suggestion: string
}

export interface LearningReportComment {
  id: BigIntId
  teacherId: BigIntId
  teacherName: string
  content: string
  createdAt: string
}

export interface LearningReportDetail extends LearningReport {
  reportSummary: string
  improvementSuggestions: string
  weakPoints: LearningReportWeakPoint[]
  comments: LearningReportComment[]
}

export interface LearningReportCommentRequest {
  content: string
}

/**
 * 学生生成阶段学习报告。
 */
export function generateStudentLearningReport(
  courseId: BigIntId,
  request: LearningReportRequest,
): Promise<LearningReportDetail> {
  return apiRequest<LearningReportDetail>(
    `/v1/student/courses/${encodeURIComponent(courseId)}/learning-reports`,
    {
      method: 'POST',
      body: request,
    },
  )
}

/**
 * 学生查询自己的报告历史。
 */
export function listStudentLearningReports(
  courseId: BigIntId,
): Promise<LearningReport[]> {
  return apiRequest<LearningReport[]>(
    `/v1/student/courses/${encodeURIComponent(courseId)}/learning-reports`,
  )
}

/**
 * 学生查询报告详情。
 */
export function getStudentLearningReport(
  courseId: BigIntId,
  reportId: BigIntId,
): Promise<LearningReportDetail> {
  return apiRequest<LearningReportDetail>(
    `/v1/student/courses/${encodeURIComponent(courseId)}/learning-reports/${encodeURIComponent(reportId)}`,
  )
}

/**
 * 教师查询课程学生报告。
 */
export function listTeacherLearningReports(
  courseId: BigIntId,
): Promise<LearningReport[]> {
  return apiRequest<LearningReport[]>(
    `/v1/teacher/courses/${encodeURIComponent(courseId)}/learning-reports`,
  )
}

/**
 * 教师查看报告详情。
 */
export function getTeacherLearningReport(
  courseId: BigIntId,
  reportId: BigIntId,
): Promise<LearningReportDetail> {
  return apiRequest<LearningReportDetail>(
    `/v1/teacher/courses/${encodeURIComponent(courseId)}/learning-reports/${encodeURIComponent(reportId)}`,
  )
}

/**
 * 教师追加报告批注。
 */
export function addTeacherLearningReportComment(
  courseId: BigIntId,
  reportId: BigIntId,
  request: LearningReportCommentRequest,
): Promise<LearningReportComment> {
  return apiRequest<LearningReportComment>(
    `/v1/teacher/courses/${encodeURIComponent(courseId)}/learning-reports/${encodeURIComponent(reportId)}/comments`,
    {
      method: 'POST',
      body: request,
    },
  )
}
