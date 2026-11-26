import { describe, expect, it } from 'vitest'

import type { KnowledgeDoc } from '@/api/knowledge'
import { versionStatusLabel } from '@/api/knowledge'
import {
  canDelete,
  canRetry,
  documentStatusLabel,
  hasPendingDocs,
  LatestRequest,
  documentStatusTagType,
  formatFileSize,
  uploadFileError,
} from '@/views/knowledge/knowledge-form'

function doc(status: KnowledgeDoc['status']): KnowledgeDoc {
  return {
    id: '50001',
    courseId: '2001',
    resourceId: '50001',
    uploaderId: '10001',
    originalName: 'a.pdf',
    fileSize: 1024,
    versionNo: 1,
    versionStatus: status === 'READY' ? 'CURRENT' : 'PENDING',
    resourceType: 'COURSEWARE',
    sourceInfo: null,
    reviewStatus: status === 'READY' ? 'APPROVED' : 'DRAFT',
    reviewSubmitterId: null,
    reviewSubmitTime: null,
    reviewerId: null,
    reviewTime: null,
    reviewComment: null,
    status,
    errorMessage: null,
    createTime: '2026-10-21T10:00:00.000+08:00',
  }
}

describe('knowledge document presentation', () => {
  it('状态映射为可读文案与标签类型', () => {
    expect(documentStatusLabel('UPLOADED')).toBe('待解析')
    expect(documentStatusLabel('PARSING')).toBe('解析中')
    expect(documentStatusLabel('PROCESSING')).toBe('解析中')
    expect(documentStatusLabel('READY')).toBe('就绪')
    expect(documentStatusLabel('FAILED')).toBe('失败')
    expect(documentStatusLabel('DELETING')).toBe('删除中')
    expect(documentStatusLabel('DELETE_FAILED')).toBe('删除失败')
    expect(documentStatusLabel('DELETED')).toBe('已删除')
    expect(documentStatusTagType('READY')).toBe('success')
    expect(documentStatusTagType('PROCESSING')).toBe('warning')
    expect(documentStatusTagType('FAILED')).toBe('danger')
    expect(documentStatusTagType('DELETE_FAILED')).toBe('danger')
  })

  it('非删除态可删除', () => {
    expect(canDelete(doc('READY'))).toBe(true)
    expect(canDelete(doc('DELETING'))).toBe(false)
    expect(canDelete(doc('DELETED'))).toBe(false)
  })

  it('仅 FAILED 可重试', () => {
    expect(canRetry(doc('FAILED'))).toBe(true)
    expect(canRetry(doc('READY'))).toBe(false)
    expect(canRetry(doc('PARSING'))).toBe(false)
    expect(canRetry(doc('PROCESSING'))).toBe(false)
  })

  it('存在处理中或删除中的文档时启用轮询', () => {
    expect(hasPendingDocs([doc('READY'), doc('FAILED')])).toBe(false)
    expect(hasPendingDocs([doc('READY'), doc('PARSING')])).toBe(true)
    expect(hasPendingDocs([doc('PROCESSING')])).toBe(true)
    expect(hasPendingDocs([doc('UPLOADED')])).toBe(true)
    expect(hasPendingDocs([doc('DELETING')])).toBe(true)
    expect(hasPendingDocs([doc('DELETE_FAILED')])).toBe(false)
  })

  it('版本状态映射为可读名称', () => {
    expect(versionStatusLabel('CURRENT')).toBe('当前版本')
    expect(versionStatusLabel('HISTORY')).toBe('历史版本')
    expect(versionStatusLabel('PENDING')).toBe('待生效版本')
  })

  it('快速切换课程时旧请求序号失效', () => {
    const gate = new LatestRequest()
    const first = gate.start()
    const second = gate.start()
    expect(gate.isCurrent(first)).toBe(false)
    expect(gate.isCurrent(second)).toBe(true)
  })

  it('文件大小格式化', () => {
    expect(formatFileSize(512)).toBe('512 B')
    expect(formatFileSize(2048)).toBe('2.0 KB')
    expect(formatFileSize(2 * 1024 * 1024)).toBe('2.0 MB')
  })

  it('客户端上传预检拒绝空文件、超限与非白名单扩展名', () => {
    expect(uploadFileError(new File(['x'], 'a.pdf'))).toBeNull()
    expect(uploadFileError(new File([''], 'a.pdf'))).toContain('空文件')
    expect(uploadFileError(new File(['MZ'], 'a.exe'))).toContain('PDF')
    const oversized = new File(['x'], 'big.pdf')
    Object.defineProperty(oversized, 'size', { value: 30 * 1024 * 1024 + 1 })
    expect(uploadFileError(oversized)).toContain('30MB')
    expect(uploadFileError(new File(['x'], 'a.md'))).toContain('PDF')
  })
})
