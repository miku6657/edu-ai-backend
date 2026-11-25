<script setup lang="ts">
import { computed, ref, watch } from 'vue'

import { ElMessageBox } from 'element-plus'

import type { BigIntId } from '@/api/types'
import type { KnowledgeDoc } from '@/api/knowledge'
import type { CourseChapter } from '@/api/document-classification'
import {
  assignChunkChapter,
  backfillDocumentChunks,
  chunkingVersionLabel,
  listDocumentChunks,
} from '@/api/document-chunk'
import type {
  DocumentChunk,
  DocumentChunkPage,
} from '@/api/document-chunk'
import {
  notifyError,
  notifySuccess,
  notifyWarning,
} from '@/utils/notify'

const props = defineProps<{
  modelValue: boolean
  courseId: BigIntId | null
  document: KnowledgeDoc | null
  chapters: CourseChapter[]
}>()

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  preview: [document: KnowledgeDoc]
}>()

const loading = ref(false)
const saving = ref(false)
const backfilling = ref(false)
const pageData = ref<DocumentChunkPage | null>(null)
const keyword = ref('')
const filterPageNo = ref<number | undefined>()
const filterChapter = ref('')
const page = ref(1)
const pageSize = ref(20)
const selectedChunks = ref<DocumentChunk[]>([])
const detailVisible = ref(false)
const detailChunk = ref<DocumentChunk | null>(null)
const chapterDialogVisible = ref(false)
const targetChapterId = ref('')
const assigningIds = ref<BigIntId[]>([])
const tableKey = ref(0)
const errorMessage = ref('')

let requestVersion = 0

const records = computed(
  () => pageData.value?.records ?? [],
)
const totalChunks = computed(
  () => pageData.value?.totalChunks ?? 0,
)
const classifiedChunks = computed(
  () => pageData.value?.classifiedChunks ?? 0,
)
const unclassifiedChunks = computed(
  () =>
    Math.max(
      0,
      totalChunks.value - classifiedChunks.value,
    ),
)
const classifiedPercent = computed(() => {
  if (!totalChunks.value) return 0

  return Math.round(
    classifiedChunks.value
      / totalChunks.value
      * 100,
  )
})
const busy = computed(
  () => saving.value || backfilling.value,
)
const canEdit = computed(
  () =>
    props.document?.status === 'READY'
    && props.document?.versionStatus !== 'HISTORY'
    && pageData.value?.versionStatus !== 'HISTORY',
)
const canBackfill = computed(
  () =>
    props.document?.status === 'READY'
    && props.document?.versionStatus === 'CURRENT'
    && pageData.value?.initialized === false,
)

function resetSelection(): void {
  selectedChunks.value = []
  tableKey.value++
}

function resetState(): void {
  pageData.value = null
  keyword.value = ''
  filterPageNo.value = undefined
  filterChapter.value = ''
  page.value = 1
  pageSize.value = 20
  detailVisible.value = false
  detailChunk.value = null
  chapterDialogVisible.value = false
  targetChapterId.value = ''
  assigningIds.value = []
  errorMessage.value = ''
  resetSelection()
}

function isCurrentContext(
  courseId: BigIntId,
  documentId: BigIntId,
): boolean {
  return (
    props.modelValue
    && props.courseId === courseId
    && props.document?.id === documentId
  )
}

/**
 * 加载当前文档切片列表。
 */
async function loadChunks(): Promise<void> {
  const courseId = props.courseId
  const doc = props.document

  if (!props.modelValue || !courseId || !doc) {
    return
  }

  const version = ++requestVersion
  loading.value = true
  errorMessage.value = ''
  resetSelection()

  try {
    const result = await listDocumentChunks(
      courseId,
      doc.id,
      {
        keyword: keyword.value,
        pageNo: filterPageNo.value,
        chapterId:
          filterChapter.value
          && filterChapter.value !== 'UNCLASSIFIED'
            ? filterChapter.value
            : undefined,
        unclassified:
          filterChapter.value === 'UNCLASSIFIED',
        page: page.value,
        pageSize: pageSize.value,
      },
    )

    if (
      version !== requestVersion
      || !isCurrentContext(courseId, doc.id)
    ) {
      return
    }

    const lastPage = Math.max(
      1,
      Math.ceil(result.total / pageSize.value),
    )

    if (page.value > lastPage) {
      page.value = lastPage
      await loadChunks()
      return
    }

    pageData.value = result
  } catch (error) {
    if (
      version !== requestVersion
      || !isCurrentContext(courseId, doc.id)
    ) {
      return
    }

    pageData.value = null
    errorMessage.value =
      error instanceof Error
        ? error.message
        : '知识切片加载失败'
    notifyError(error)
  } finally {
    if (version === requestVersion) {
      loading.value = false
    }
  }
}

function searchChunks(): void {
  if (busy.value) return

  page.value = 1
  void loadChunks()
}

function resetFilters(): void {
  if (busy.value) return

  keyword.value = ''
  filterPageNo.value = undefined
  filterChapter.value = ''
  page.value = 1
  void loadChunks()
}

function onPageChange(value: number): void {
  page.value = value
  void loadChunks()
}

function onPageSizeChange(value: number): void {
  pageSize.value = value
  page.value = 1
  void loadChunks()
}

function onSelectionChange(
  rows: DocumentChunk[],
): void {
  selectedChunks.value = rows
}

function openDetail(chunk: DocumentChunk): void {
  detailChunk.value = chunk
  detailVisible.value = true
}

function openPdf(): void {
  if (props.document) {
    emit('preview', props.document)
  }
}

/**
 * 打开章节设置窗口。
 */
function openChapterAssignment(
  chunks: DocumentChunk[],
): void {
  if (
    !canEdit.value
    || busy.value
    || loading.value
  ) {
    return
  }

  if (!chunks.length) {
    notifyWarning('请先选择需要调整的切片')
    return
  }

  assigningIds.value = [
    ...new Set(chunks.map(chunk => chunk.id)),
  ]
  targetChapterId.value =
    chunks.length === 1
      ? chunks[0]?.chapterId ?? ''
      : ''
  chapterDialogVisible.value = true
}

/**
 * 保存切片章节。
 */
async function saveChapterAssignment(): Promise<void> {
  const courseId = props.courseId
  const doc = props.document

  if (
    !courseId
    || !doc
    || !canEdit.value
    || busy.value
  ) {
    return
  }

  const ids = [...assigningIds.value]

  if (!ids.length || ids.length > 200) {
    notifyWarning('每次请选择1至200个切片')
    return
  }

  saving.value = true

  try {
    const result = await assignChunkChapter(
      courseId,
      doc.id,
      {
        chunkIds: ids,
        chapterId: targetChapterId.value || null,
      },
    )

    notifySuccess(
      `已更新${result.updatedChunks}个切片的章节`,
    )

    if (isCurrentContext(courseId, doc.id)) {
      chapterDialogVisible.value = false
      detailVisible.value = false
      detailChunk.value = null
      assigningIds.value = []
      await loadChunks()
    }
  } catch (error) {
    notifyError(error)
  } finally {
    saving.value = false
  }
}

/**
 * 补录历史资料切片。
 */
async function backfillChunks(): Promise<void> {
  const courseId = props.courseId
  const doc = props.document

  if (
    !courseId
    || !doc
    || !canBackfill.value
    || busy.value
  ) {
    return
  }

  try {
    await ElMessageBox.confirm(
      '系统将从当前向量知识库读取该资料的已有切片，'
      + '并保存到MySQL。此操作不会重新解析PDF，'
      + '也不会调用Embedding模型。',
      '补录历史知识切片',
      {
        type: 'info',
        confirmButtonText: '开始补录',
        cancelButtonText: '取消',
      },
    )
  } catch {
    return
  }

  if (!isCurrentContext(courseId, doc.id) || busy.value) {
    return
  }

  backfilling.value = true

  try {
    const result = await backfillDocumentChunks(
      courseId,
      doc.id,
    )

    notifySuccess(
      result.alreadyInitialized
        ? '该资料已完成切片补录'
        : `成功补录${result.chunkCount}个知识切片`,
    )

    if (isCurrentContext(courseId, doc.id)) {
      page.value = 1
      await loadChunks()
    }
  } catch (error) {
    notifyError(error)
  } finally {
    backfilling.value = false
  }
}

function closeDialog(): void {
  if (busy.value) return

  emit('update:modelValue', false)
}

function onDialogUpdate(value: boolean): void {
  if (!value) {
    closeDialog()
  }
}

watch(
  () => [
    props.modelValue,
    props.courseId,
    props.document?.id,
  ] as const,
  ([visible]) => {
    requestVersion++
    loading.value = false
    resetState()

    if (visible) {
      void loadChunks()
    }
  },
  { immediate: true },
)
</script>

<template>
  <el-dialog
    :model-value="modelValue"
    title="知识切片管理"
    width="min(1180px, 96vw)"
    top="4vh"
    append-to-body
    destroy-on-close
    :close-on-click-modal="!busy"
    :close-on-press-escape="!busy"
    :show-close="!busy"
    @update:model-value="onDialogUpdate"
  >
    <div
      v-loading="loading || backfilling"
      class="chunk-panel"
    >
      <div class="chunk-document-header">
        <div class="chunk-document-info">
          <strong>{{ document?.originalName }}</strong>
          <div class="chunk-document-meta">
            <el-tag
              v-if="document?.versionNo"
              size="small"
            >
              V{{ document.versionNo }}
            </el-tag>
            <el-tag
              v-if="pageData"
              size="small"
              type="info"
            >
              {{ chunkingVersionLabel(pageData.chunkingVersion) }}
            </el-tag>
            <span v-if="pageData">
              {{
                pageData.versionStatus === 'HISTORY'
                  ? '历史版本'
                  : '课程知识资源'
              }}
            </span>
          </div>
        </div>
        <el-button
          :disabled="busy"
          @click="openPdf"
        >
          预览原始PDF
        </el-button>
      </div>

      <el-alert
        v-if="errorMessage"
        :title="errorMessage"
        type="error"
        :closable="false"
        show-icon
      />
      <el-button
        v-if="errorMessage"
        :disabled="busy"
        @click="loadChunks"
      >
        重新加载
      </el-button>

      <div
        v-if="pageData"
        class="chunk-statistics"
      >
        <div class="chunk-statistic">
          <span>切片总数</span>
          <strong>{{ totalChunks }}</strong>
        </div>
        <div class="chunk-statistic">
          <span>已关联章节</span>
          <strong class="text-success">{{ classifiedChunks }}</strong>
        </div>
        <div class="chunk-statistic">
          <span>未关联章节</span>
          <strong class="text-warning">{{ unclassifiedChunks }}</strong>
        </div>
        <div class="chunk-statistic">
          <span>章节关联进度</span>
          <strong>{{ classifiedPercent }}%</strong>
          <el-progress
            :percentage="classifiedPercent"
            :show-text="false"
            :stroke-width="6"
          />
        </div>
      </div>

      <el-alert
        v-if="pageData && !pageData.initialized"
        title="当前资料尚未建立业务切片记录"
        type="warning"
        :closable="false"
        show-icon
      >
        <template #default>
          如果这是之前上传的资料，可以从现有向量库补录切片。
          补录不会改变PDF、向量数据及当前版本状态。
        </template>
      </el-alert>

      <div
        v-if="canBackfill"
        class="backfill-actions"
      >
        <el-button
          type="primary"
          :loading="backfilling"
          :disabled="loading || saving"
          @click="backfillChunks"
        >
          补录历史切片
        </el-button>
      </div>

      <template v-if="pageData?.initialized">
        <div class="chunk-filters">
          <el-input
            v-model="keyword"
            placeholder="搜索切片正文或原文章节"
            maxlength="200"
            clearable
            :disabled="busy"
            @keyup.enter="searchChunks"
            @clear="searchChunks"
          />
          <el-input-number
            v-model="filterPageNo"
            :min="1"
            :precision="0"
            :controls="false"
            placeholder="PDF页码"
            :disabled="busy"
            class="page-number-input"
          />
          <el-select
            v-model="filterChapter"
            placeholder="全部章节"
            :disabled="busy"
            @change="searchChunks"
          >
            <el-option
              label="全部章节"
              value=""
            />
            <el-option
              label="未关联章节"
              value="UNCLASSIFIED"
            />
            <el-option
              v-for="chapter in chapters"
              :key="chapter.id"
              :label="chapter.name"
              :value="chapter.id"
            />
          </el-select>
          <el-button
            type="primary"
            :disabled="busy"
            @click="searchChunks"
          >
            查询
          </el-button>
          <el-button
            :disabled="busy"
            @click="resetFilters"
          >
            重置
          </el-button>
        </div>

        <div class="chunk-actions">
          <span class="chunk-muted">
            共 {{ pageData.total }} 条结果，
            当前页已选择 {{ selectedChunks.length }} 条
          </span>
          <el-button
            v-if="canEdit"
            type="primary"
            plain
            :disabled="!selectedChunks.length || busy || loading"
            @click="openChapterAssignment(selectedChunks)"
          >
            批量设置章节
          </el-button>
        </div>

        <el-table
          :key="tableKey"
          :data="records"
          row-key="id"
          max-height="520"
          empty-text="未查询到符合条件的知识切片"
          @selection-change="onSelectionChange"
        >
          <el-table-column
            v-if="canEdit"
            type="selection"
            width="44"
          />
          <el-table-column
            label="定位"
            width="100"
          >
            <template #default="{ row }">
              <div class="chunk-position">
                <strong>第 {{ row.pageNo }} 页</strong>
                <span>切片 {{ row.chunkIndex + 1 }}</span>
              </div>
            </template>
          </el-table-column>
          <el-table-column
            label="原文章节"
            min-width="130"
            show-overflow-tooltip
          >
            <template #default="{ row }">
              {{ row.sectionTitle || '未标注' }}
            </template>
          </el-table-column>
          <el-table-column
            label="切片正文"
            min-width="300"
          >
            <template #default="{ row }">
              <div
                class="chunk-content-preview"
                @click="openDetail(row)"
              >
                {{ row.content }}
              </div>
              <span class="chunk-muted">
                {{ row.charCount }} 字符
              </span>
            </template>
          </el-table-column>
          <el-table-column
            label="所属课程章节"
            min-width="140"
          >
            <template #default="{ row }">
              <el-tag
                v-if="row.chapterName"
                size="small"
                type="success"
              >
                {{ row.chapterName }}
              </el-tag>
              <el-tag
                v-else
                size="small"
                type="info"
              >
                未分类
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column
            label="操作"
            width="150"
            fixed="right"
          >
            <template #default="{ row }">
              <el-button
                type="primary"
                link
                @click="openDetail(row)"
              >
                详情
              </el-button>
              <el-button
                v-if="canEdit"
                type="primary"
                link
                :disabled="busy || loading"
                @click="openChapterAssignment([row])"
              >
                设置章节
              </el-button>
            </template>
          </el-table-column>
        </el-table>

        <div class="chunk-pagination">
          <el-pagination
            :current-page="page"
            :page-size="pageSize"
            :page-sizes="[10, 20, 50, 100]"
            :total="pageData.total"
            :disabled="loading || busy"
            layout="total, sizes, prev, pager, next"
            @current-change="onPageChange"
            @size-change="onPageSizeChange"
          />
        </div>
      </template>

      <el-empty
        v-if="pageData && !pageData.initialized && !canBackfill"
        description="当前资料暂无可管理的知识切片"
      />
    </div>

    <template #footer>
      <el-button
        :disabled="busy"
        @click="closeDialog"
      >
        关闭
      </el-button>
    </template>

    <el-dialog
      v-model="detailVisible"
      title="知识切片详情"
      width="min(760px, 94vw)"
      append-to-body
      destroy-on-close
    >
      <div
        v-if="detailChunk"
        class="chunk-detail"
      >
        <el-descriptions
          :column="2"
          border
        >
          <el-descriptions-item label="原始页码">
            第 {{ detailChunk.pageNo }} 页
          </el-descriptions-item>
          <el-descriptions-item label="切片序号">
            {{ detailChunk.chunkIndex + 1 }}
          </el-descriptions-item>
          <el-descriptions-item label="原文章节">
            {{ detailChunk.sectionTitle || '未标注' }}
          </el-descriptions-item>
          <el-descriptions-item label="课程章节">
            {{ detailChunk.chapterName || '未分类' }}
          </el-descriptions-item>
          <el-descriptions-item label="字符数量">
            {{ detailChunk.charCount }}
          </el-descriptions-item>
          <el-descriptions-item label="切片标识">
            {{ detailChunk.chunkKey }}
          </el-descriptions-item>
        </el-descriptions>
        <div class="chunk-detail-heading">
          切片完整正文
        </div>
        <div class="chunk-detail-content">
          {{ detailChunk.content }}
        </div>
        <div class="chunk-hash">
          <span>正文SHA-256：</span>
          {{ detailChunk.contentHash }}
        </div>
      </div>

      <template #footer>
        <el-button @click="openPdf">
          查看原始PDF
        </el-button>
        <el-button
          v-if="canEdit && detailChunk"
          type="primary"
          :disabled="busy"
          @click="openChapterAssignment([detailChunk])"
        >
          调整所属章节
        </el-button>
        <el-button @click="detailVisible = false">
          关闭
        </el-button>
      </template>
    </el-dialog>

    <el-dialog
      v-model="chapterDialogVisible"
      title="设置切片所属章节"
      width="min(480px, 92vw)"
      append-to-body
      :close-on-click-modal="!saving"
      :close-on-press-escape="!saving"
      :show-close="!saving"
    >
      <el-form
        label-position="top"
        :disabled="saving"
      >
        <el-alert
          :title="`本次将调整${assigningIds.length}个知识切片`"
          type="info"
          :closable="false"
          class="assignment-alert"
        />
        <el-form-item label="目标课程章节">
          <el-select
            v-model="targetChapterId"
            placeholder="选择目标章节"
            style="width: 100%"
          >
            <el-option
              label="解除章节关联"
              value=""
            />
            <el-option
              v-for="chapter in chapters"
              :key="chapter.id"
              :label="chapter.name"
              :value="chapter.id"
            />
          </el-select>
        </el-form-item>
        <div class="chunk-muted">
          本次操作只调整切片所属课程章节，
          不修改正文、原始页码、PDF文件和向量数据。
        </div>
      </el-form>

      <template #footer>
        <el-button
          :disabled="saving"
          @click="chapterDialogVisible = false"
        >
          取消
        </el-button>
        <el-button
          type="primary"
          :loading="saving"
          @click="saveChapterAssignment"
        >
          确认保存
        </el-button>
      </template>
    </el-dialog>
  </el-dialog>
</template>

<style scoped>
.chunk-panel {
  display: grid;
  gap: 18px;
  min-height: 240px;
}

.chunk-document-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}

.chunk-document-info {
  display: grid;
  gap: 10px;
  min-width: 0;
}

.chunk-document-info strong {
  font-size: 17px;
  overflow-wrap: anywhere;
}

.chunk-document-meta {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.chunk-statistics {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
}

.chunk-statistic {
  display: grid;
  gap: 10px;
  padding: 16px;
  border: 1px solid var(--el-border-color);
  border-radius: 9px;
}

.chunk-statistic span {
  font-size: 13px;
  color: var(--el-text-color-secondary);
}

.chunk-statistic strong {
  font-size: 25px;
  font-weight: 600;
}

.text-success {
  color: var(--el-color-success);
}

.text-warning {
  color: var(--el-color-warning);
}

.backfill-actions {
  display: flex;
  justify-content: flex-start;
}

.chunk-filters {
  display: grid;
  grid-template-columns:
    minmax(200px, 2fr)
    110px
    minmax(150px, 1fr)
    auto
    auto;
  gap: 10px;
  align-items: center;
}

.chunk-filters :deep(.el-button) {
  margin-left: 0;
}

.page-number-input {
  width: 100%;
}

.chunk-actions {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}

.chunk-muted {
  font-size: 12px;
  line-height: 1.7;
  color: var(--el-text-color-secondary);
}

.chunk-position {
  display: grid;
  gap: 4px;
}

.chunk-position span {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.chunk-content-preview {
  display: -webkit-box;
  overflow: hidden;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 3;
  line-height: 1.7;
  white-space: pre-wrap;
  overflow-wrap: anywhere;
  cursor: pointer;
}

.chunk-content-preview:hover {
  color: var(--el-color-primary);
}

.chunk-pagination {
  display: flex;
  justify-content: flex-end;
  overflow-x: auto;
}

.chunk-detail {
  display: grid;
  gap: 16px;
}

.chunk-detail-heading {
  font-size: 15px;
  font-weight: 600;
}

.chunk-detail-content {
  max-height: 400px;
  overflow-y: auto;
  padding: 18px;
  background: var(--el-fill-color-light);
  border: 1px solid var(--el-border-color-light);
  border-radius: 8px;
  line-height: 1.9;
  white-space: pre-wrap;
  overflow-wrap: anywhere;
}

.chunk-hash {
  font-size: 12px;
  overflow-wrap: anywhere;
  color: var(--el-text-color-secondary);
}

.assignment-alert {
  margin-bottom: 18px;
}

@media (max-width: 768px) {
  .chunk-statistics {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .chunk-filters {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .chunk-document-header {
    flex-wrap: wrap;
  }

  .chunk-actions {
    flex-wrap: wrap;
  }

  .chunk-pagination {
    justify-content: flex-start;
  }
}
</style>
