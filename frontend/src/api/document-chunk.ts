import { apiRequest } from '@/api/client'
import type { BigIntId } from '@/api/types'

/**
 * 单条文档知识切片。
 */
export interface DocumentChunk {
  id: BigIntId
  courseId: BigIntId
  documentId: BigIntId
  chunkKey: string
  pageNo: number
  chunkIndex: number
  chapterId: BigIntId | null
  chapterName: string | null
  sectionTitle: string | null
  content: string
  contentHash: string
  charCount: number
}

/**
 * 切片分页结果。
 */
export interface DocumentChunkPage {
  documentId: BigIntId
  documentName: string
  versionStatus: string
  chunkingVersion: number
  initialized: boolean
  totalChunks: number
  classifiedChunks: number
  total: number
  page: number
  pageSize: number
  records: DocumentChunk[]
}

/**
 * 切片查询条件。
 */
export interface DocumentChunkQuery {
  keyword?: string
  pageNo?: number
  chapterId?: BigIntId
  unclassified?: boolean
  page?: number
  pageSize?: number
}

/**
 * 批量设置切片章节。
 */
export interface AssignChunkChapterRequest {
  chunkIds: BigIntId[]
  chapterId: BigIntId | null
}

export interface AssignChunkChapterResult {
  updatedChunks: number
}

/**
 * 历史文档切片补录结果。
 */
export interface ChunkBackfillResult {
  documentId: BigIntId
  chunkCount: number
  alreadyInitialized: boolean
}

function documentChunkUrl(
  courseId: BigIntId,
  documentId: BigIntId,
): string {
  return (
    `/v1/document-chunks/courses/`
    + `${encodeURIComponent(courseId)}`
    + `/documents/`
    + `${encodeURIComponent(documentId)}`
  )
}

/**
 * 分页查询文档切片。
 */
export function listDocumentChunks(
  courseId: BigIntId,
  documentId: BigIntId,
  query: DocumentChunkQuery = {},
): Promise<DocumentChunkPage> {
  const params = new URLSearchParams()

  if (query.keyword?.trim()) {
    params.set('keyword', query.keyword.trim())
  }

  if (query.pageNo !== undefined) {
    params.set('pageNo', String(query.pageNo))
  }

  if (query.chapterId) {
    params.set('chapterId', query.chapterId)
  }

  if (query.unclassified) {
    params.set('unclassified', 'true')
  }

  params.set('page', String(query.page ?? 1))
  params.set('pageSize', String(query.pageSize ?? 20))

  return apiRequest<DocumentChunkPage>(
    `${documentChunkUrl(courseId, documentId)}?${params.toString()}`,
  )
}

/**
 * 查询单条知识切片。
 */
export function getDocumentChunk(
  courseId: BigIntId,
  documentId: BigIntId,
  chunkId: BigIntId,
): Promise<DocumentChunk> {
  return apiRequest<DocumentChunk>(
    `${documentChunkUrl(courseId, documentId)}/${encodeURIComponent(chunkId)}`,
  )
}

/**
 * 批量调整切片章节。
 */
export function assignChunkChapter(
  courseId: BigIntId,
  documentId: BigIntId,
  data: AssignChunkChapterRequest,
): Promise<AssignChunkChapterResult> {
  return apiRequest<AssignChunkChapterResult>(
    `${documentChunkUrl(courseId, documentId)}/chapter`,
    {
      method: 'PUT',
      body: data,
    },
  )
}

/**
 * 从ChromaDB补录已有文档切片。
 */
export function backfillDocumentChunks(
  courseId: BigIntId,
  documentId: BigIntId,
): Promise<ChunkBackfillResult> {
  return apiRequest<ChunkBackfillResult>(
    `${documentChunkUrl(courseId, documentId)}/backfill`,
    {
      method: 'POST',
    },
  )
}

/**
 * 切片算法显示名称。
 */
export function chunkingVersionLabel(
  version: number,
): string {
  switch (version) {
    case 1:
      return '固定窗口切片'
    case 2:
      return '段落感知切片'
    default:
      return '未知切片算法'
  }
}
