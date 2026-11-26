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

  // F2-03 知识点关联
  knowledgeStatus: ChunkKnowledgeStatus
  confirmedPointCount: number
  pendingPointCount: number
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
    case 3:
      return '章节聚合切片'
    default:
      return '未知切片算法'
  }
}

/**
 * F2-03 知识切片关联处理状态。
 */
export type ChunkKnowledgeStatus =
  | 'UNPROCESSED'
  | 'RECOMMENDED'
  | 'CONFIRMED'
  | 'NOT_APPLICABLE'

/**
 * 知识点关联确认状态。
 */
export type ChunkKnowledgeConfirmStatus =
  | 'PENDING'
  | 'CONFIRMED'
  | 'REJECTED'

/**
 * 知识点推荐处理决定。
 */
export type ChunkKnowledgeDecision =
  | 'CONFIRM'
  | 'REJECT'

/**
 * 单条知识切片与知识点的关联信息。
 */
export interface ChunkKnowledgeRelation {
  id: BigIntId
  chunkId: BigIntId
  pointId: BigIntId
  pointTitle: string
  chapterId: BigIntId | null
  chapterName: string | null
  difficulty: number
  matchScore: number
  relationSource: string
  confirmStatus: ChunkKnowledgeConfirmStatus
  matchReason: string | null
  confirmedBy: BigIntId | null
  confirmedTime: string | null
}

/**
 * 单个切片的完整知识点关联详情。
 */
export interface ChunkKnowledgeDetail {
  chunkId: BigIntId
  knowledgeStatus: ChunkKnowledgeStatus
  knowledgeCheckedBy: BigIntId | null
  knowledgeCheckedTime: string | null
  relations: ChunkKnowledgeRelation[]
}

/**
 * 文档知识点关联进度。
 */
export interface ChunkKnowledgeProgress {
  documentId: BigIntId
  totalChunks: number
  unprocessedChunks: number
  recommendedChunks: number
  confirmedChunks: number
  notApplicableChunks: number
  pendingRelations: number
  confirmedRelations: number
  completionPercent: number
  completed: boolean
}

/**
 * 自动推荐知识点请求。
 *
 * 不传chunkIds时，
 * 后端自动处理下一批未处理切片。
 */
export interface ChunkKnowledgeRecommendRequest {
  chunkIds?: BigIntId[]
}

/**
 * 自动推荐知识点统计结果。
 */
export interface ChunkKnowledgeRecommendResult {
  processedChunks: number
  recommendedChunks: number
  unmatchedChunks: number
  skippedChunks: number
  generatedRelations: number
}

/**
 * 批量确认或拒绝知识点推荐。
 */
export interface ChunkKnowledgeDecisionRequest {
  relationIds: BigIntId[]
  decision: ChunkKnowledgeDecision
}

/**
 * 人工设置切片的最终知识点集合。
 */
export interface ChunkKnowledgeReplaceRequest {
  pointIds: BigIntId[]
}

/**
 * 批量处理知识切片。
 */
export interface ChunkKnowledgeBatchRequest {
  chunkIds: BigIntId[]
}

/**
 * 批量处理结果。
 */
export interface ChunkKnowledgeBatchResult {
  processedCount: number
}

/**
 * 知识点关联接口统一路径。
 */
function chunkKnowledgeUrl(
  courseId: BigIntId,
  documentId: BigIntId,
): string {
  return `${documentChunkUrl(
    courseId,
    documentId,
  )}/knowledge`
}

/**
 * 1. 自动生成知识点推荐。
 *
 * 指定chunkIds：
 * 处理指定的知识切片。
 *
 * 不指定chunkIds：
 * 自动处理下一批未处理切片。
 */
export function recommendChunkKnowledge(
  courseId: BigIntId,
  documentId: BigIntId,
  data: ChunkKnowledgeRecommendRequest = {},
): Promise<ChunkKnowledgeRecommendResult> {
  return apiRequest<ChunkKnowledgeRecommendResult>(
    `${chunkKnowledgeUrl(
      courseId,
      documentId,
    )}/recommend`,
    {
      method: 'POST',
      body: data,
    },
  )
}

/**
 * 2. 查询单个切片的知识点关联详情。
 */
export function getChunkKnowledgeDetail(
  courseId: BigIntId,
  documentId: BigIntId,
  chunkId: BigIntId,
): Promise<ChunkKnowledgeDetail> {
  return apiRequest<ChunkKnowledgeDetail>(
    `${chunkKnowledgeUrl(
      courseId,
      documentId,
    )}/chunks/${encodeURIComponent(chunkId)}`,
  )
}

/**
 * 3. 批量确认或拒绝知识点推荐。
 */
export function decideChunkKnowledgeRelations(
  courseId: BigIntId,
  documentId: BigIntId,
  data: ChunkKnowledgeDecisionRequest,
): Promise<ChunkKnowledgeBatchResult> {
  return apiRequest<ChunkKnowledgeBatchResult>(
    `${chunkKnowledgeUrl(
      courseId,
      documentId,
    )}/decisions`,
    {
      method: 'POST',
      body: data,
    },
  )
}

/**
 * 4. 人工设置知识切片的最终知识点集合。
 *
 * 注意：此接口是替换，而不是追加。
 */
export function replaceChunkKnowledgePoints(
  courseId: BigIntId,
  documentId: BigIntId,
  chunkId: BigIntId,
  data: ChunkKnowledgeReplaceRequest,
): Promise<ChunkKnowledgeBatchResult> {
  return apiRequest<ChunkKnowledgeBatchResult>(
    `${chunkKnowledgeUrl(
      courseId,
      documentId,
    )}/chunks/${encodeURIComponent(chunkId)}/points`,
    {
      method: 'PUT',
      body: data,
    },
  )
}

/**
 * 5. 批量标记切片无需关联知识点。
 */
export function markChunkKnowledgeNotApplicable(
  courseId: BigIntId,
  documentId: BigIntId,
  data: ChunkKnowledgeBatchRequest,
): Promise<ChunkKnowledgeBatchResult> {
  return apiRequest<ChunkKnowledgeBatchResult>(
    `${chunkKnowledgeUrl(
      courseId,
      documentId,
    )}/not-applicable`,
    {
      method: 'POST',
      body: data,
    },
  )
}

/**
 * 6. 批量重置切片知识点处理状态。
 */
export function resetChunkKnowledge(
  courseId: BigIntId,
  documentId: BigIntId,
  data: ChunkKnowledgeBatchRequest,
): Promise<ChunkKnowledgeBatchResult> {
  return apiRequest<ChunkKnowledgeBatchResult>(
    `${chunkKnowledgeUrl(
      courseId,
      documentId,
    )}/reset`,
    {
      method: 'POST',
      body: data,
    },
  )
}

/**
 * 7. 查询文档知识点关联进度。
 */
export function getChunkKnowledgeProgress(
  courseId: BigIntId,
  documentId: BigIntId,
): Promise<ChunkKnowledgeProgress> {
  return apiRequest<ChunkKnowledgeProgress>(
    `${chunkKnowledgeUrl(
      courseId,
      documentId,
    )}/progress`,
  )
}

/**
 * 知识切片关联状态显示名称。
 */
export function chunkKnowledgeStatusLabel(
  status: string,
): string {
  switch (status) {
    case 'UNPROCESSED':
      return '未处理'

    case 'RECOMMENDED':
      return '待处理'

    case 'CONFIRMED':
      return '已确认'

    case 'NOT_APPLICABLE':
      return '无需关联'

    default:
      return '未知状态'
  }
}

/**
 * 知识切片关联状态标签类型。
 */
export function chunkKnowledgeStatusTagType(
  status: string,
): 'info' | 'warning' | 'success' {
  switch (status) {
    case 'RECOMMENDED':
      return 'warning'

    case 'CONFIRMED':
      return 'success'

    case 'NOT_APPLICABLE':
    case 'UNPROCESSED':
    default:
      return 'info'
  }
}

/**
 * 单条知识点关联确认状态显示名称。
 */
export function knowledgeConfirmStatusLabel(
  status: string,
): string {
  switch (status) {
    case 'PENDING':
      return '待确认'

    case 'CONFIRMED':
      return '已确认'

    case 'REJECTED':
      return '已拒绝'

    default:
      return '未知状态'
  }
}

/**
 * 单条知识点关联确认状态标签类型。
 */
export function knowledgeConfirmStatusTagType(
  status: string,
): 'info' | 'warning' | 'success' | 'danger' {
  switch (status) {
    case 'PENDING':
      return 'warning'

    case 'CONFIRMED':
      return 'success'

    case 'REJECTED':
      return 'danger'

    default:
      return 'info'
  }
}
