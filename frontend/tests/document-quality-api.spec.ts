import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import { resetApiClient } from '@/api/client'
import {
  assignQualityIssue,
  checkQualitySample,
  completeQualityTask,
  createQualityTask,
  getQualityDetail,
  listQualityAssignees,
  listQualityTasks,
  qualityIssueStatusLabel,
  qualitySampleStatusLabel,
  reviewQualityIssue,
  resolveQualityIssue,
} from '@/api/document-quality'

const COURSE_ID = '9007199254740993'
const DOCUMENT_ID = '9007199254740994'
const TASK_ID = '9007199254740995'
const SAMPLE_ID = '9007199254740996'
const ISSUE_ID = '9007199254740997'

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

describe('document quality api', () => {
  beforeEach(() => {
    resetApiClient()
  })

  afterEach(() => {
    vi.unstubAllGlobals()
    resetApiClient()
  })

  it('uses the quality endpoints and preserves BIGINT IDs', async () => {
    const fetchMock = vi.fn()
      .mockImplementation(() => Promise.resolve(jsonResponse({})))
    vi.stubGlobal('fetch', fetchMock)

    await listQualityAssignees(COURSE_ID)
    await createQualityTask(COURSE_ID, DOCUMENT_ID, {
      title: '第一章抽检',
      sampleRate: 10,
    })
    await listQualityTasks(COURSE_ID, DOCUMENT_ID)
    await getQualityDetail(COURSE_ID, TASK_ID)
    await checkQualitySample(COURSE_ID, TASK_ID, SAMPLE_ID, {
      result: 'FAIL',
      issues: [{
        issueType: 'PARSE_ERROR',
        description: '页面内容解析错误',
        assigneeId: ISSUE_ID,
      }],
    })
    await assignQualityIssue(COURSE_ID, TASK_ID, ISSUE_ID, {
      assigneeId: ISSUE_ID,
    })
    await resolveQualityIssue(COURSE_ID, TASK_ID, ISSUE_ID, {
      resolution: '已更正资料',
      replacementDocumentId: DOCUMENT_ID,
    })
    await reviewQualityIssue(COURSE_ID, TASK_ID, ISSUE_ID, {
      decision: 'APPROVE',
    })
    await completeQualityTask(COURSE_ID, TASK_ID)

    expect(fetchMock.mock.calls.map(([url, init]) => ({
      url: String(url),
      method: (init as RequestInit | undefined)?.method,
      body: (init as RequestInit | undefined)?.body,
    }))).toEqual([
      {
        url: `/api/v1/document-quality/courses/${COURSE_ID}/assignees`,
        method: 'GET',
        body: undefined,
      },
      {
        url: `/api/v1/document-quality/courses/${COURSE_ID}/documents/${DOCUMENT_ID}/tasks`,
        method: 'POST',
        body: JSON.stringify({ title: '第一章抽检', sampleRate: 10 }),
      },
      {
        url: `/api/v1/document-quality/courses/${COURSE_ID}/tasks?documentId=${DOCUMENT_ID}`,
        method: 'GET',
        body: undefined,
      },
      {
        url: `/api/v1/document-quality/courses/${COURSE_ID}/tasks/${TASK_ID}`,
        method: 'GET',
        body: undefined,
      },
      {
        url: `/api/v1/document-quality/courses/${COURSE_ID}/tasks/${TASK_ID}/samples/${SAMPLE_ID}/check`,
        method: 'POST',
        body: JSON.stringify({
          result: 'FAIL',
          issues: [{
            issueType: 'PARSE_ERROR',
            description: '页面内容解析错误',
            assigneeId: ISSUE_ID,
          }],
        }),
      },
      {
        url: `/api/v1/document-quality/courses/${COURSE_ID}/tasks/${TASK_ID}/issues/${ISSUE_ID}/assign`,
        method: 'POST',
        body: JSON.stringify({ assigneeId: ISSUE_ID }),
      },
      {
        url: `/api/v1/document-quality/courses/${COURSE_ID}/tasks/${TASK_ID}/issues/${ISSUE_ID}/resolve`,
        method: 'POST',
        body: JSON.stringify({
          resolution: '已更正资料',
          replacementDocumentId: DOCUMENT_ID,
        }),
      },
      {
        url: `/api/v1/document-quality/courses/${COURSE_ID}/tasks/${TASK_ID}/issues/${ISSUE_ID}/review`,
        method: 'POST',
        body: JSON.stringify({ decision: 'APPROVE' }),
      },
      {
        url: `/api/v1/document-quality/courses/${COURSE_ID}/tasks/${TASK_ID}/complete`,
        method: 'POST',
        body: undefined,
      },
    ])
  })

  it('labels sample and issue statuses', () => {
    expect(qualitySampleStatusLabel('PENDING')).toBe('待检查')
    expect(qualitySampleStatusLabel('PASS')).toBe('合格')
    expect(qualitySampleStatusLabel('FAIL')).toBe('不合格')
    expect(qualityIssueStatusLabel('OPEN')).toBe('待整改')
    expect(qualityIssueStatusLabel('RESOLVED')).toBe('待复核')
    expect(qualityIssueStatusLabel('CLOSED')).toBe('已关闭')
  })
})
