<script setup lang="ts">
import { computed, ref } from 'vue'
import { ElMessageBox } from 'element-plus'

import type { BigIntId } from '@/api/types'
import {
  markChunkKnowledgeNotApplicable,
  recommendChunkKnowledge,
  resetChunkKnowledge,
} from '@/api/document-chunk'
import type {
  ChunkKnowledgeRecommendResult,
  DocumentChunk,
} from '@/api/document-chunk'
import {
  notifyError,
  notifySuccess,
  notifyWarning,
} from '@/utils/notify'

const props = defineProps<{
  courseId: BigIntId | null
  documentId: BigIntId | null
  selectedChunks: DocumentChunk[]
  editable: boolean
  disabled: boolean
}>()

const emit = defineEmits<{
  changed: []
  'busy-change': [value: boolean]
}>()

type BatchAction =
  | 'RECOMMEND_NEXT'
  | 'RECOMMEND_SELECTED'
  | 'NOT_APPLICABLE'
  | 'RESET'

interface BatchContext {
  courseId: BigIntId
  documentId: BigIntId
}

const MAX_BATCH_SIZE = 200
const runningAction = ref<BatchAction | null>(null)
const recommendResult = ref<ChunkKnowledgeRecommendResult | null>(null)

const selectedIds = computed(
  () => [...new Set(props.selectedChunks.map(chunk => chunk.id))],
)
const recommendableIds = computed(
  () => [
    ...new Set(
      props.selectedChunks
        .filter(
          chunk =>
            chunk.knowledgeStatus === 'UNPROCESSED'
            || (
              chunk.knowledgeStatus === 'RECOMMENDED'
              && chunk.confirmedPointCount === 0
            ),
        )
        .map(chunk => chunk.id),
    ),
  ],
)
const operationDisabled = computed(
  () =>
    !props.editable
    || props.disabled
    || runningAction.value !== null
    || !props.courseId
    || !props.documentId,
)

function getContext(): BatchContext | null {
  if (!props.courseId || !props.documentId) return null
  return {
    courseId: props.courseId,
    documentId: props.documentId,
  }
}

function isCurrentContext(context: BatchContext): boolean {
  return (
    props.courseId === context.courseId
    && props.documentId === context.documentId
  )
}

function validateIds(ids: BigIntId[]): boolean {
  if (!ids.length) {
    notifyWarning('请先选择符合操作条件的切片')
    return false
  }
  if (ids.length > MAX_BATCH_SIZE) {
    notifyWarning(`每次最多处理${MAX_BATCH_SIZE}个切片`)
    return false
  }
  return true
}

async function confirmAction(
  message: string,
  title: string,
): Promise<boolean> {
  try {
    await ElMessageBox.confirm(message, title, {
      type: 'warning',
      confirmButtonText: '确认',
      cancelButtonText: '取消',
    })
    return true
  } catch {
    return false
  }
}

async function executeAction(
  type: BatchAction,
  context: BatchContext,
  action: () => Promise<void>,
): Promise<void> {
  if (operationDisabled.value || !isCurrentContext(context)) return

  runningAction.value = type
  recommendResult.value = null
  emit('busy-change', true)

  try {
    await action()
    if (isCurrentContext(context)) emit('changed')
  } catch (error) {
    if (isCurrentContext(context)) {
      notifyError(error, '批量处理知识切片失败')
    }
  } finally {
    runningAction.value = null
    emit('busy-change', false)
  }
}

function showRecommendResult(result: ChunkKnowledgeRecommendResult): void {
  recommendResult.value = result
  if (result.processedChunks === 0) {
    notifyWarning('当前没有符合条件的待推荐切片')
    return
  }
  if (result.unmatchedChunks > 0) {
    notifyWarning(
      `已处理${result.processedChunks}个切片，`
      + `其中${result.unmatchedChunks}个未匹配到知识点，需要人工处理`,
    )
    return
  }
  notifySuccess(`已生成${result.generatedRelations}条知识点推荐`)
}

async function recommendNextBatch(): Promise<void> {
  const context = getContext()
  if (!context || operationDisabled.value) return

  await executeAction('RECOMMEND_NEXT', context, async () => {
    const result = await recommendChunkKnowledge(
      context.courseId,
      context.documentId,
      {},
    )
    if (isCurrentContext(context)) showRecommendResult(result)
  })
}

async function recommendSelected(): Promise<void> {
  const context = getContext()
  const ids = [...recommendableIds.value]
  if (!context || operationDisabled.value || !validateIds(ids)) return

  const confirmed = await confirmAction(
    `确定为选中的${ids.length}个符合条件的切片生成知识点推荐吗？`
    + '已有人工确认的关联不会被自动覆盖。',
    '批量推荐知识点',
  )
  if (!confirmed || !isCurrentContext(context)) return

  await executeAction('RECOMMEND_SELECTED', context, async () => {
    const result = await recommendChunkKnowledge(
      context.courseId,
      context.documentId,
      { chunkIds: ids },
    )
    if (isCurrentContext(context)) showRecommendResult(result)
  })
}

async function markSelectedNotApplicable(): Promise<void> {
  const context = getContext()
  const ids = [...selectedIds.value]
  if (!context || operationDisabled.value || !validateIds(ids)) return

  const confirmed = await confirmAction(
    `确定将选中的${ids.length}个切片标记为无需关联吗？`
    + '原有的知识点关联记录将被清除。',
    '批量标记无需关联',
  )
  if (!confirmed || !isCurrentContext(context)) return

  await executeAction('NOT_APPLICABLE', context, async () => {
    const result = await markChunkKnowledgeNotApplicable(
      context.courseId,
      context.documentId,
      { chunkIds: ids },
    )
    if (isCurrentContext(context)) {
      notifySuccess(`已处理${result.processedCount}个切片`)
    }
  })
}

async function resetSelected(): Promise<void> {
  const context = getContext()
  const ids = [...selectedIds.value]
  if (!context || operationDisabled.value || !validateIds(ids)) return

  const confirmed = await confirmAction(
    `确定重置选中的${ids.length}个切片吗？`
    + '已有推荐和人工关联记录将被清除，切片恢复为未处理状态。',
    '批量重置关联',
  )
  if (!confirmed || !isCurrentContext(context)) return

  await executeAction('RESET', context, async () => {
    const result = await resetChunkKnowledge(
      context.courseId,
      context.documentId,
      { chunkIds: ids },
    )
    if (isCurrentContext(context)) {
      notifySuccess(`已重置${result.processedCount}个切片`)
    }
  })
}
</script>

<template>
  <div
    v-if="editable"
    class="batch-panel"
  >
    <div class="batch-header">
      <div>
        <div class="batch-title">
          知识点批量处理
        </div>
        <div class="batch-description">
          每批最多处理200个切片，自动推荐后仍需人工确认
        </div>
      </div>
      <el-tag type="info">
        已选择 {{ selectedIds.length }} 个
      </el-tag>
    </div>

    <div class="batch-buttons">
      <el-button
        type="primary"
        :disabled="operationDisabled"
        :loading="runningAction === 'RECOMMEND_NEXT'"
        @click="recommendNextBatch"
      >
        自动推荐下一批
      </el-button>
      <el-button
        type="primary"
        plain
        :disabled="operationDisabled || recommendableIds.length === 0"
        :loading="runningAction === 'RECOMMEND_SELECTED'"
        @click="recommendSelected"
      >
        推荐选中切片（{{ recommendableIds.length }}）
      </el-button>
      <el-button
        :disabled="operationDisabled || selectedIds.length === 0"
        :loading="runningAction === 'NOT_APPLICABLE'"
        @click="markSelectedNotApplicable"
      >
        批量无需关联
      </el-button>
      <el-button
        type="danger"
        plain
        :disabled="operationDisabled || selectedIds.length === 0"
        :loading="runningAction === 'RESET'"
        @click="resetSelected"
      >
        批量重置关联
      </el-button>
    </div>

    <div
      v-if="recommendResult"
      class="recommend-result"
    >
      <div class="result-header">
        <strong>本次推荐结果</strong>
        <el-button
          link
          size="small"
          @click="recommendResult = null"
        >
          收起
        </el-button>
      </div>
      <div class="result-grid">
        <div class="result-item">
          <span>已处理切片</span>
          <strong>{{ recommendResult.processedChunks }}</strong>
        </div>
        <div class="result-item">
          <span>匹配成功</span>
          <strong class="text-success">
            {{ recommendResult.recommendedChunks }}
          </strong>
        </div>
        <div class="result-item">
          <span>未匹配</span>
          <strong class="text-warning">
            {{ recommendResult.unmatchedChunks }}
          </strong>
        </div>
        <div class="result-item">
          <span>已跳过</span>
          <strong>{{ recommendResult.skippedChunks }}</strong>
        </div>
        <div class="result-item">
          <span>生成关联</span>
          <strong>{{ recommendResult.generatedRelations }}</strong>
        </div>
      </div>
      <el-alert
        v-if="recommendResult.unmatchedChunks > 0"
        title="部分切片未匹配到知识点，请进入关联管理进行人工调整或标记无需关联"
        type="warning"
        :closable="false"
        show-icon
      />
    </div>
  </div>
</template>

<style scoped>
.batch-panel {
  display: flex;
  flex-direction: column;
  gap: 14px;
  padding: 16px;
  border: 1px solid var(--el-border-color);
  border-radius: 10px;
}

.batch-header,
.result-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.batch-title {
  font-size: 15px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.batch-description {
  margin-top: 4px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.batch-buttons {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
}

.batch-buttons :deep(.el-button) {
  margin-left: 0;
}

.recommend-result {
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding: 14px;
  border-radius: 8px;
  background: var(--el-fill-color-light);
}

.result-grid {
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  gap: 10px;
}

.result-item {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.result-item span {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.result-item strong {
  font-size: 20px;
}

.text-success {
  color: var(--el-color-success);
}

.text-warning {
  color: var(--el-color-warning);
}

@media (max-width: 768px) {
  .result-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
</style>
