import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import { resetApiClient } from '@/api/client'
import {
  decideDocumentReview,
  getDocumentReviewDetail,
  reviewActionLabel,
  reviewActionTagType,
  submitDocumentReview,
  withdrawDocumentReview,
} from '@/api/document-review'
import {
  documentReviewStatusLabel,
  documentReviewStatusTagType,
  isDocumentKnowledgeEditable,
  isDocumentPublished,
} from '@/api/knowledge'
import type { KnowledgeDoc } from '@/api/knowledge'

const COURSE_ID = '9007199254740993'
const DOCUMENT_ID = '9007199254740994'

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

describe('document review api', () => {
  beforeEach(() => {
    resetApiClient()
  })

  afterEach(() => {
    vi.unstubAllGlobals()
    resetApiClient()
  })

  it('uses the review endpoints and preserves BIGINT IDs', async () => {
    const detail = {
      documentId: DOCUMENT_ID,
      courseId: COURSE_ID,
      resourceId: '9007199254740995',
      originalName: '数据库.pdf',
      versionNo: 2,
      status: 'READY',
      versionStatus: 'PENDING',
      reviewStatus: 'DRAFT',
      reviewSubmitterId: null,
      reviewSubmitTime: null,
      reviewerId: null,
      reviewTime: null,
      reviewComment: null,
      knowledgeProgress: null,
      canSubmit: true,
      canReview: false,
      canApprove: false,
      qualityBlockReason: null,
      canWithdraw: false,
      published: false,
      records: [],
    }
    const fetchMock = vi.fn()
      .mockImplementation(() => Promise.resolve(jsonResponse(detail)))
    vi.stubGlobal('fetch', fetchMock)

    await expect(getDocumentReviewDetail(
      COURSE_ID,
      DOCUMENT_ID,
    )).resolves.toEqual(detail)
    await submitDocumentReview(COURSE_ID, DOCUMENT_ID, {
      comment: '准备发布',
    })
    await decideDocumentReview(COURSE_ID, DOCUMENT_ID, {
      decision: 'REJECT',
      comment: '需要补充关联',
    })
    await withdrawDocumentReview(COURSE_ID, DOCUMENT_ID)

    expect(fetchMock.mock.calls.map(([url, init]) => ({
      url: String(url),
      method: (init as RequestInit | undefined)?.method,
      body: (init as RequestInit | undefined)?.body,
    }))).toEqual([
      {
        url: `/api/v1/document-reviews/courses/${COURSE_ID}/documents/${DOCUMENT_ID}`,
        method: 'GET',
        body: undefined,
      },
      {
        url: `/api/v1/document-reviews/courses/${COURSE_ID}/documents/${DOCUMENT_ID}/submit`,
        method: 'POST',
        body: JSON.stringify({ comment: '准备发布' }),
      },
      {
        url: `/api/v1/document-reviews/courses/${COURSE_ID}/documents/${DOCUMENT_ID}/decision`,
        method: 'POST',
        body: JSON.stringify({
          decision: 'REJECT',
          comment: '需要补充关联',
        }),
      },
      {
        url: `/api/v1/document-reviews/courses/${COURSE_ID}/documents/${DOCUMENT_ID}/withdraw`,
        method: 'POST',
        body: JSON.stringify({}),
      },
    ])
  })

  it('labels review states and enforces draft-edit and published rules', () => {
    expect(documentReviewStatusLabel('DRAFT')).toBe('待提交')
    expect(documentReviewStatusLabel('PENDING')).toBe('待审核')
    expect(documentReviewStatusLabel('APPROVED')).toBe('审核通过')
    expect(documentReviewStatusLabel('REJECTED')).toBe('审核驳回')
    expect(documentReviewStatusLabel(undefined)).toBe('未知状态')

    expect(documentReviewStatusTagType('PENDING')).toBe('warning')
    expect(documentReviewStatusTagType('APPROVED')).toBe('success')
    expect(documentReviewStatusTagType('REJECTED')).toBe('danger')
    expect(documentReviewStatusTagType('DRAFT')).toBe('info')

    const base: KnowledgeDoc = {
      id: DOCUMENT_ID,
      courseId: COURSE_ID,
      resourceId: '9007199254740995',
      uploaderId: '9007199254740996',
      originalName: '数据库.pdf',
      fileSize: 1024,
      versionNo: 2,
      versionStatus: 'PENDING',
      resourceType: 'COURSEWARE',
      sourceInfo: null,
      reviewStatus: 'DRAFT',
      reviewSubmitterId: null,
      reviewSubmitTime: null,
      reviewerId: null,
      reviewTime: null,
      reviewComment: null,
      status: 'READY',
      errorMessage: null,
      createTime: '2026-11-26T14:00:00',
    }

    expect(isDocumentKnowledgeEditable(base)).toBe(true)
    expect(isDocumentKnowledgeEditable({
      ...base,
      reviewStatus: 'REJECTED',
    })).toBe(true)
    expect(isDocumentKnowledgeEditable({
      ...base,
      reviewStatus: 'PENDING',
    })).toBe(false)
    expect(isDocumentKnowledgeEditable({
      ...base,
      versionStatus: 'CURRENT',
      reviewStatus: 'APPROVED',
    })).toBe(false)
    expect(isDocumentPublished({
      ...base,
      versionStatus: 'CURRENT',
      reviewStatus: 'APPROVED',
    })).toBe(true)
    expect(isDocumentPublished(base)).toBe(false)

    expect(reviewActionLabel('SUBMIT')).toBe('提交审核')
    expect(reviewActionLabel('WITHDRAW')).toBe('撤回审核')
    expect(reviewActionTagType('APPROVE')).toBe('success')
    expect(reviewActionTagType('REJECT')).toBe('danger')
  })
})
