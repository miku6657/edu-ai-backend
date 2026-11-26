import { apiRequest } from '@/api/client'
import type { BigIntId } from '@/api/types'

export type QaTicketStatus =
  | 'PENDING'
  | 'ANSWERED'

export interface QaTicket {
  id: BigIntId
  courseId: BigIntId
  courseName: string
  studentId: BigIntId
  studentName: string
  teacherId: BigIntId
  teacherName: string
  sessionId: BigIntId | null
  questionMessageId: BigIntId | null
  questionText: string
  reason: string
  status: QaTicketStatus
  teacherReply: string | null
  createdAt: string
  updatedAt: string
  answeredAt: string | null
}

export interface QaTicketPage {
  page: number
  size: number
  total: number
  totalPages: number
  items: QaTicket[]
}

export interface QaTicketQuery {
  status?: QaTicketStatus
  courseId?: BigIntId
  page?: number
  size?: number
}

/**
 * 学生查看自己的工单。
 */
export function listMyQaTickets(
  query: QaTicketQuery = {},
): Promise<QaTicketPage> {
  const params = new URLSearchParams()
  if (query.status) {
    params.set('status', query.status)
  }
  params.set('page', String(query.page ?? 1))
  params.set('size', String(query.size ?? 10))

  return apiRequest<QaTicketPage>(
    `/v1/qa-tickets/mine?${params.toString()}`,
  )
}

/**
 * 教师或管理员查看工单。
 */
export function listAssignedQaTickets(
  query: QaTicketQuery = {},
): Promise<QaTicketPage> {
  const params = new URLSearchParams()
  if (query.status) {
    params.set('status', query.status)
  }
  if (query.courseId) {
    params.set('courseId', query.courseId)
  }
  params.set('page', String(query.page ?? 1))
  params.set('size', String(query.size ?? 10))

  return apiRequest<QaTicketPage>(
    `/v1/qa-tickets/assigned?${params.toString()}`,
  )
}

/**
 * 查询工单详情。
 */
export function getQaTicket(
  ticketId: BigIntId,
): Promise<QaTicket> {
  return apiRequest<QaTicket>(
    `/v1/qa-tickets/${encodeURIComponent(ticketId)}`,
  )
}

/**
 * 教师提交人工答复。
 */
export function replyQaTicket(
  ticketId: BigIntId,
  reply: string,
): Promise<string> {
  return apiRequest<string>(
    `/v1/qa-tickets/${encodeURIComponent(ticketId)}/reply`,
    {
      method: 'POST',
      body: { reply },
    },
  )
}

/**
 * 学生确认创建人工答疑工单的返回结果。
 */
export interface CreateQaTicketResult {
  ticketId: BigIntId
  status: QaTicketStatus
}

/**
 * 学生确认后创建工单。
 *
 * 后端根据 questionMessageId 查询真实问题和拒答状态，
 * 不接受前端指定学生、课程和教师。
 */
export function createQaTicketFromQuestion(
  questionMessageId: BigIntId,
): Promise<CreateQaTicketResult> {
  return apiRequest<CreateQaTicketResult>(
    '/v1/qa-tickets/from-chat',
    {
      method: 'POST',
      body: {
        questionMessageId,
      },
    },
  )
}
