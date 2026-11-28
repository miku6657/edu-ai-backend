import { apiRequest } from '@/api/client'
import type { BigIntId } from '@/api/types'

export interface TeacherQuestionQuery {
  pointId?: BigIntId
  studentKeyword?: string
  keyword?: string
  startDate?: string
  endDate?: string
  page?: number
  size?: number
}

export interface TeacherQuestionRecord {
  messageId: BigIntId
  sessionId: BigIntId
  studentId: BigIntId
  studentName: string
  questionText: string
  askedAt: string
  knowledgePointId: BigIntId | null
  knowledgePointTitle: string | null
  handlingStatus: 'AI_HANDLED' | 'HUMAN_PENDING' | 'HUMAN_ANSWERED'
  ticketId: BigIntId | null
  ticketStatus: 'PENDING' | 'ANSWERED' | null
  noEvidenceReason: string | null
  teacherReply: string | null
  aiAnswerPreview: string | null
}

export interface TeacherQuestionPage {
  page: number
  size: number
  total: number
  totalPages: number
  items: TeacherQuestionRecord[]
}

export interface HotQuestion {
  questionText: string
  knowledgePointId: BigIntId | null
  knowledgePointTitle: string | null
  questionCount: number
  studentCount: number
  lastAskedAt: string
}

export interface KnowledgePointQuestionStat {
  pointId: BigIntId
  title: string
  questionCount: number
  studentCount: number
  latestAskedAt: string | null
}

export interface QuestionAnalyticsSummary {
  totalQuestions: number
  participatingStudents: number
  mappedQuestions: number
  unclassifiedQuestions: number
  pendingTickets: number
  answeredTickets: number
}

export interface QuestionAnalyticsFilter {
  startDate?: string
  endDate?: string
  pointId?: BigIntId
  limit?: number
}

function buildQuery(
  params: Record<string, string | number | undefined>,
): string {
  const query = new URLSearchParams()
  for (const [key, value] of Object.entries(params)) {
    if (value !== undefined && value !== '') {
      query.set(key, String(value))
    }
  }
  return query.toString()
}

export function listTeacherQuestionRecords(
  courseId: BigIntId,
  filter: TeacherQuestionQuery = {},
): Promise<TeacherQuestionPage> {
  const query = buildQuery({
    pointId: filter.pointId,
    studentKeyword: filter.studentKeyword,
    keyword: filter.keyword,
    startDate: filter.startDate,
    endDate: filter.endDate,
    page: filter.page ?? 1,
    size: filter.size ?? 20,
  })

  return apiRequest<TeacherQuestionPage>(
    `/v1/teacher-questions/courses/${encodeURIComponent(courseId)}/records?${query}`,
  )
}

export function listHotQuestions(
  courseId: BigIntId,
  filter: QuestionAnalyticsFilter = {},
): Promise<HotQuestion[]> {
  const query = buildQuery({
    pointId: filter.pointId,
    startDate: filter.startDate,
    endDate: filter.endDate,
    limit: filter.limit ?? 10,
  })

  return apiRequest<HotQuestion[]>(
    `/v1/teacher-questions/courses/${encodeURIComponent(courseId)}/hot-questions?${query}`,
  )
}

export function listKnowledgePointQuestionStats(
  courseId: BigIntId,
  filter: QuestionAnalyticsFilter = {},
): Promise<KnowledgePointQuestionStat[]> {
  const query = buildQuery({
    startDate: filter.startDate,
    endDate: filter.endDate,
  })

  return apiRequest<KnowledgePointQuestionStat[]>(
    `/v1/teacher-questions/courses/${encodeURIComponent(courseId)}/knowledge-points?${query}`,
  )
}

export function getQuestionAnalyticsSummary(
  courseId: BigIntId,
  filter: QuestionAnalyticsFilter = {},
): Promise<QuestionAnalyticsSummary> {
  const query = buildQuery({
    startDate: filter.startDate,
    endDate: filter.endDate,
  })

  return apiRequest<QuestionAnalyticsSummary>(
    `/v1/teacher-questions/courses/${encodeURIComponent(courseId)}/summary?${query}`,
  )
}
