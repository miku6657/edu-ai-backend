import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import { resetApiClient } from '@/api/client'
import {
  createQaTicketFromQuestion,
  getQaTicket,
  listAssignedQaTickets,
  listMyQaTickets,
  replyQaTicket,
} from '@/api/qa-tickets'

const COURSE_ID = '9007199254740993'
const TICKET_ID = '9007199254740995'

function jsonResponse(data: unknown): Response {
  return new Response(JSON.stringify({
    code: 200,
    message: '操作成功',
    data,
  }), {
    status: 200,
    headers: { 'Content-Type': 'application/json' },
  })
}

describe('qa tickets api', () => {
  beforeEach(() => {
    resetApiClient()
  })

  afterEach(() => {
    vi.unstubAllGlobals()
    resetApiClient()
  })

  it('uses the ticket endpoints and preserves BIGINT IDs', async () => {
    const fetchMock = vi.fn()
      .mockImplementation(() => Promise.resolve(jsonResponse({})))
    vi.stubGlobal('fetch', fetchMock)

    await listMyQaTickets({
      status: 'PENDING',
      page: 2,
      size: 20,
    })
    await listAssignedQaTickets({
      status: 'ANSWERED',
      courseId: COURSE_ID,
      page: 3,
      size: 15,
    })
    await getQaTicket(TICKET_ID)
    await replyQaTicket(TICKET_ID, '请查看课程资料中的第二章。')

    expect(fetchMock.mock.calls.map(([url, init]) => ({
      url: String(url),
      method: (init as RequestInit | undefined)?.method,
      body: (init as RequestInit | undefined)?.body,
    }))).toEqual([
      {
        url: '/api/v1/qa-tickets/mine?status=PENDING&page=2&size=20',
        method: 'GET',
        body: undefined,
      },
      {
        url: `/api/v1/qa-tickets/assigned?status=ANSWERED&courseId=${COURSE_ID}&page=3&size=15`,
        method: 'GET',
        body: undefined,
      },
      {
        url: `/api/v1/qa-tickets/${TICKET_ID}`,
        method: 'GET',
        body: undefined,
      },
      {
        url: `/api/v1/qa-tickets/${TICKET_ID}/reply`,
        method: 'POST',
        body: JSON.stringify({ reply: '请查看课程资料中的第二章。' }),
      },
    ])
  })

  it('creates a ticket from a confirmed question ID', async () => {
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(jsonResponse({
        ticketId: TICKET_ID,
        status: 'PENDING',
      }))
    vi.stubGlobal('fetch', fetchMock)

    await expect(createQaTicketFromQuestion(COURSE_ID)).resolves.toEqual({
      ticketId: TICKET_ID,
      status: 'PENDING',
    })

    expect(fetchMock.mock.calls[0]?.[0]).toBe('/api/v1/qa-tickets/from-chat')
    expect(fetchMock.mock.calls[0]?.[1]).toMatchObject({
      method: 'POST',
      body: JSON.stringify({ questionMessageId: COURSE_ID }),
    })
  })
})
