<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { ElMessageBox } from 'element-plus'

import type { BigIntId } from '@/api/types'
import {
  chunkKnowledgeStatusLabel,
  chunkKnowledgeStatusTagType,
  decideChunkKnowledgeRelations,
  getChunkKnowledgeDetail,
  knowledgeConfirmStatusLabel,
  knowledgeConfirmStatusTagType,
  markChunkKnowledgeNotApplicable,
  recommendChunkKnowledge,
  replaceChunkKnowledgePoints,
  resetChunkKnowledge,
} from '@/api/document-chunk'
import type {
  ChunkKnowledgeDecision,
  ChunkKnowledgeDetail,
  ChunkKnowledgeRelation,
  DocumentChunk,
} from '@/api/document-chunk'
import { listKnowledgePoints } from '@/api/learning-path'
import type { KnowledgePoint } from '@/api/learning-path'
import {
  notifyError,
  notifySuccess,
  notifyWarning,
} from '@/utils/notify'

const props = defineProps<{
  modelValue: boolean
  courseId: BigIntId | null
  documentId: BigIntId | null
  chunk: DocumentChunk | null
  editable: boolean
}>()

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  changed: []
  'busy-change': [value: boolean]
}>()

interface KnowledgeContext {
  courseId: BigIntId
  documentId: BigIntId
  chunkId: BigIntId
}

const loading = ref(false)
const saving = ref(false)
const detail = ref<ChunkKnowledgeDetail | null>(null)
const knowledgePoints = ref<KnowledgePoint[]>([])
const selectedRelationIds = ref<BigIntId[]>([])
const selectedPointIds = ref<BigIntId[]>([])
const manualMode = ref(false)
const errorMessage = ref('')
let requestVersion = 0

const relations = computed(
  () => detail.value?.relations ?? [],
)
const pendingRelations = computed(
  () => relations.value.filter(item => item.confirmStatus === 'PENDING'),
)
const confirmedRelations = computed(
  () => relations.value.filter(item => item.confirmStatus === 'CONFIRMED'),
)
const rejectedRelations = computed(
  () => relations.value.filter(item => item.confirmStatus === 'REJECTED'),
)
const currentStatus = computed(
  () =>
    detail.value?.knowledgeStatus
    ?? props.chunk?.knowledgeStatus
    ?? 'UNPROCESSED',
)
const busy = computed(() => loading.value || saving.value)
const canOperate = computed(
  () => props.editable && !busy.value && detail.value !== null,
)
const availablePoints = computed(() => {
  const chapterId = props.chunk?.chapterId
  if (!chapterId) return knowledgePoints.value

  return knowledgePoints.value.filter(
    point => !point.chapterId || point.chapterId === chapterId,
  )
})
const allPendingSelected = computed(
  () =>
    pendingRelations.value.length > 0
    && pendingRelations.value.every(
      item => selectedRelationIds.value.includes(item.id),
    ),
)

function getContext(): KnowledgeContext | null {
  if (
    !props.modelValue
    || !props.courseId
    || !props.documentId
    || !props.chunk
  ) {
    return null
  }

  return {
    courseId: props.courseId,
    documentId: props.documentId,
    chunkId: props.chunk.id,
  }
}

function isCurrentContext(context: KnowledgeContext): boolean {
  return (
    props.modelValue
    && props.courseId === context.courseId
    && props.documentId === context.documentId
    && props.chunk?.id === context.chunkId
  )
}

function resetState(): void {
  detail.value = null
  knowledgePoints.value = []
  selectedRelationIds.value = []
  selectedPointIds.value = []
  manualMode.value = false
  errorMessage.value = ''
}

async function loadData(): Promise<void> {
  const context = getContext()
  if (!context) return

  const version = ++requestVersion
  loading.value = true
  errorMessage.value = ''

  try {
    const [result, points] = await Promise.all([
      getChunkKnowledgeDetail(
        context.courseId,
        context.documentId,
        context.chunkId,
      ),
      props.editable
        ? listKnowledgePoints(context.courseId)
        : Promise.resolve([] as KnowledgePoint[]),
    ])

    if (version !== requestVersion || !isCurrentContext(context)) return

    detail.value = result
    knowledgePoints.value = points
    selectedRelationIds.value = []
    selectedPointIds.value = result.relations
      .filter(item => item.confirmStatus === 'CONFIRMED')
      .map(item => item.pointId)
  } catch (error) {
    if (version !== requestVersion || !isCurrentContext(context)) return

    detail.value = null
    errorMessage.value = '知识点关联详情加载失败'
    notifyError(error, errorMessage.value)
  } finally {
    if (version === requestVersion) loading.value = false
  }
}

async function executeMutation(
  action: (context: KnowledgeContext) => Promise<string>,
): Promise<void> {
  const context = getContext()
  if (!context || !canOperate.value) return

  saving.value = true
  emit('busy-change', true)
  try {
    const message = await action(context)
    if (!isCurrentContext(context)) return

    emit('changed')
    notifySuccess(message)
    manualMode.value = false
    await loadData()
  } catch (error) {
    if (isCurrentContext(context)) {
      notifyError(error, '知识点关联操作失败')
    }
  } finally {
    saving.value = false
    emit('busy-change', false)
  }
}

async function confirmAction(message: string): Promise<boolean> {
  try {
    await ElMessageBox.confirm(message, '操作确认', {
      confirmButtonText: '确认',
      cancelButtonText: '取消',
      type: 'warning',
    })
    return true
  } catch {
    return false
  }
}

async function recommendCurrentChunk(): Promise<void> {
  if (!canOperate.value) return

  await executeMutation(async context => {
    const result = await recommendChunkKnowledge(
      context.courseId,
      context.documentId,
      { chunkIds: [context.chunkId] },
    )

    if (result.generatedRelations > 0) {
      return `已生成 ${result.generatedRelations} 条知识点推荐`
    }
    if (result.skippedChunks > 0) return '当前切片无需重复推荐'
    return '未匹配到新的知识点，请尝试人工关联'
  })
}

function toggleRelation(
  relation: ChunkKnowledgeRelation,
  checked: boolean,
): void {
  if (!canOperate.value || relation.confirmStatus !== 'PENDING') return

  selectedRelationIds.value = checked
    ? [...new Set([...selectedRelationIds.value, relation.id])]
    : selectedRelationIds.value.filter(id => id !== relation.id)
}

function toggleAllPending(checked: boolean): void {
  if (!canOperate.value) return
  selectedRelationIds.value = checked
    ? pendingRelations.value.map(item => item.id)
    : []
}

async function decideSelectedRelations(
  decision: ChunkKnowledgeDecision,
): Promise<void> {
  if (!canOperate.value) return

  const ids = [...selectedRelationIds.value]
  if (!ids.length) {
    notifyWarning('请先选择需要处理的推荐记录')
    return
  }
  if (ids.length > 200) {
    notifyWarning('每次最多处理200条关联记录')
    return
  }

  const context = getContext()
  if (!context) return

  const label = decision === 'CONFIRM' ? '确认' : '拒绝'
  if (
    !await confirmAction(
      `确定${label}选中的 ${ids.length} 条知识点推荐吗？`,
    )
    || !isCurrentContext(context)
  ) {
    return
  }

  await executeMutation(async current => {
    const result = await decideChunkKnowledgeRelations(
      current.courseId,
      current.documentId,
      { relationIds: ids, decision },
    )
    return `已${label} ${result.processedCount} 条关联记录`
  })
}

function openManualMode(): void {
  if (!canOperate.value) return
  selectedPointIds.value = confirmedRelations.value.map(item => item.pointId)
  manualMode.value = true
}

async function saveManualPoints(): Promise<void> {
  if (!canOperate.value) return

  const ids = [...new Set(selectedPointIds.value)]
  if (!ids.length) {
    notifyWarning('请至少选择一个知识点；无需关联时请使用对应按钮')
    return
  }
  if (ids.length > 20) {
    notifyWarning('单个切片最多关联20个知识点')
    return
  }

  const allowedIds = new Set(availablePoints.value.map(point => point.id))
  if (ids.some(id => !allowedIds.has(id))) {
    notifyWarning('存在不属于当前章节或课程的知识点，请重新选择')
    return
  }

  const context = getContext()
  if (!context) return
  if (
    !await confirmAction(
      '保存后将以当前选择的知识点作为最终关联结果，'
      + '替换原有关联记录，确定继续吗？',
    )
    || !isCurrentContext(context)
  ) {
    return
  }

  await executeMutation(async current => {
    await replaceChunkKnowledgePoints(
      current.courseId,
      current.documentId,
      current.chunkId,
      { pointIds: ids },
    )
    return `已保存 ${ids.length} 个知识点关联`
  })
}

async function markNotApplicable(): Promise<void> {
  if (!canOperate.value) return

  const context = getContext()
  if (!context) return
  if (
    !await confirmAction(
      '标记后将清除当前切片已有的知识点关联，'
      + '并将切片设置为无需关联状态。确定继续吗？',
    )
    || !isCurrentContext(context)
  ) {
    return
  }

  await executeMutation(async current => {
    await markChunkKnowledgeNotApplicable(
      current.courseId,
      current.documentId,
      { chunkIds: [current.chunkId] },
    )
    return '已标记为无需关联'
  })
}

async function resetCurrentChunk(): Promise<void> {
  if (!canOperate.value) return

  const context = getContext()
  if (!context) return
  if (
    !await confirmAction(
      '重置后将清除当前切片的知识点关联记录，'
      + '恢复为未处理状态。确定继续吗？',
    )
    || !isCurrentContext(context)
  ) {
    return
  }

  await executeMutation(async current => {
    await resetChunkKnowledge(
      current.courseId,
      current.documentId,
      { chunkIds: [current.chunkId] },
    )
    return '知识点关联状态已重置'
  })
}

function relationSourceLabel(source: string): string {
  switch (source) {
    case 'AUTO':
      return '系统推荐'
    case 'MANUAL':
      return '人工关联'
    default:
      return source || '未知来源'
  }
}

function difficultyLabel(difficulty: number): string {
  switch (difficulty) {
    case 1:
      return '基础'
    case 2:
      return '中等'
    case 3:
      return '困难'
    default:
      return '未设置'
  }
}

function pointOptionLabel(point: KnowledgePoint): string {
  return `${point.title}（${difficultyLabel(point.difficulty)}）`
}

function closeDialog(): void {
  if (!saving.value) emit('update:modelValue', false)
}

function onDialogUpdate(value: boolean): void {
  if (!value) closeDialog()
}

watch(
  () => [
    props.modelValue,
    props.courseId,
    props.documentId,
    props.chunk?.id,
    props.editable,
  ] as const,
  ([visible]) => {
    requestVersion++
    loading.value = false
    resetState()
    if (visible) void loadData()
  },
  { immediate: true },
)
</script>

<template>
  <el-dialog
    :model-value="modelValue"
    title="知识点关联管理"
    width="min(920px, 96vw)"
    top="5vh"
    append-to-body
    destroy-on-close
    :close-on-click-modal="!saving"
    :close-on-press-escape="!saving"
    :show-close="!saving"
    @update:model-value="onDialogUpdate"
  >
    <div
      v-loading="busy"
      class="knowledge-panel"
    >
      <div
        v-if="chunk"
        class="chunk-summary"
      >
        <div class="summary-header">
          <div>
            <strong>
              第 {{ chunk.pageNo }} 页 · 切片 {{ chunk.chunkIndex + 1 }}
            </strong>
            <div class="muted-text">
              {{ chunk.chapterName || chunk.sectionTitle || '未设置章节' }}
            </div>
          </div>
          <el-tag :type="chunkKnowledgeStatusTagType(currentStatus)">
            {{ chunkKnowledgeStatusLabel(currentStatus) }}
          </el-tag>
        </div>
        <div class="chunk-content">
          {{ chunk.content }}
        </div>
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
        @click="loadData"
      >
        重新加载
      </el-button>

      <template v-if="detail">
        <div class="knowledge-statistics">
          <div class="stat-item">
            <span>待确认</span>
            <strong class="warning-text">{{ pendingRelations.length }}</strong>
          </div>
          <div class="stat-item">
            <span>已确认</span>
            <strong class="success-text">{{ confirmedRelations.length }}</strong>
          </div>
          <div class="stat-item">
            <span>已拒绝</span>
            <strong>{{ rejectedRelations.length }}</strong>
          </div>
        </div>

        <div
          v-if="editable"
          class="knowledge-toolbar"
        >
          <el-button
            type="primary"
            :disabled="
              !canOperate
                || currentStatus === 'CONFIRMED'
                || currentStatus === 'NOT_APPLICABLE'
            "
            @click="recommendCurrentChunk"
          >
            自动推荐知识点
          </el-button>
          <el-button
            :disabled="!canOperate"
            @click="openManualMode"
          >
            人工调整
          </el-button>
          <el-button
            :disabled="!canOperate || currentStatus === 'NOT_APPLICABLE'"
            @click="markNotApplicable"
          >
            标记无需关联
          </el-button>
          <el-button
            type="danger"
            plain
            :disabled="
              !canOperate
                || (
                  currentStatus === 'UNPROCESSED'
                  && relations.length === 0
                )
            "
            @click="resetCurrentChunk"
          >
            重置关联
          </el-button>
        </div>

        <div
          v-if="manualMode"
          class="manual-panel"
        >
          <div class="section-title">
            人工设置知识点
          </div>
          <el-alert
            title="保存后将替换当前切片的全部知识点关联"
            type="warning"
            :closable="false"
            show-icon
          />
          <el-select
            v-model="selectedPointIds"
            multiple
            filterable
            clearable
            collapse-tags
            collapse-tags-tooltip
            :multiple-limit="20"
            :disabled="busy"
            placeholder="请选择知识点"
            class="point-select"
          >
            <el-option
              v-for="point in availablePoints"
              :key="point.id"
              :label="pointOptionLabel(point)"
              :value="point.id"
            />
          </el-select>
          <div class="muted-text">
            当前已选择 {{ selectedPointIds.length }}/20 个知识点
          </div>
          <el-empty
            v-if="!availablePoints.length"
            description="当前章节暂无可关联知识点"
            :image-size="60"
          />
          <div class="manual-actions">
            <el-button
              :disabled="saving"
              @click="manualMode = false"
            >
              取消
            </el-button>
            <el-button
              type="primary"
              :loading="saving"
              :disabled="!canOperate || !selectedPointIds.length"
              @click="saveManualPoints"
            >
              保存关联
            </el-button>
          </div>
        </div>

        <div class="relation-section">
          <div class="relation-header">
            <div class="section-title">
              知识点关联记录
            </div>
            <span class="muted-text">共 {{ relations.length }} 条</span>
          </div>

          <div
            v-if="editable && pendingRelations.length"
            class="batch-toolbar"
          >
            <el-checkbox
              :model-value="allPendingSelected"
              :indeterminate="
                selectedRelationIds.length > 0 && !allPendingSelected
              "
              :disabled="!canOperate"
              @change="toggleAllPending($event === true)"
            >
              全选待确认
            </el-checkbox>
            <span class="muted-text">
              已选择 {{ selectedRelationIds.length }} 条
            </span>
            <el-button
              type="success"
              size="small"
              :disabled="!canOperate || !selectedRelationIds.length"
              @click="decideSelectedRelations('CONFIRM')"
            >
              确认选中
            </el-button>
            <el-button
              type="danger"
              size="small"
              plain
              :disabled="!canOperate || !selectedRelationIds.length"
              @click="decideSelectedRelations('REJECT')"
            >
              拒绝选中
            </el-button>
          </div>

          <el-alert
            v-if="
              currentStatus === 'RECOMMENDED'
                && !pendingRelations.length
                && !confirmedRelations.length
            "
            title="当前没有可确认的知识点推荐"
            type="warning"
            :closable="false"
            show-icon
          >
            <template #default>
              系统未匹配到合适的知识点，
              或已有推荐均被拒绝。
              请人工调整知识点，
              或将当前切片标记为无需关联。
            </template>
          </el-alert>
          <el-empty
            v-if="!relations.length"
            :description="
              currentStatus === 'NOT_APPLICABLE'
                ? '当前切片已标记为无需关联'
                : currentStatus === 'RECOMMENDED'
                  ? '未匹配到合适的知识点'
                  : '当前切片暂无知识点关联记录'
            "
            :image-size="80"
          />
          <div
            v-for="relation in relations"
            :key="relation.id"
            class="relation-card"
          >
            <div class="relation-card-header">
              <div class="relation-name">
                <el-checkbox
                  v-if="editable && relation.confirmStatus === 'PENDING'"
                  :model-value="selectedRelationIds.includes(relation.id)"
                  :disabled="!canOperate"
                  @change="toggleRelation(relation, $event === true)"
                />
                <strong>{{ relation.pointTitle }}</strong>
              </div>
              <el-tag
                size="small"
                :type="knowledgeConfirmStatusTagType(relation.confirmStatus)"
              >
                {{ knowledgeConfirmStatusLabel(relation.confirmStatus) }}
              </el-tag>
            </div>
            <div class="relation-meta">
              <el-tag size="small" type="info">
                {{ relationSourceLabel(relation.relationSource) }}
              </el-tag>
              <span>{{ relation.chapterName || '未设置章节' }}</span>
              <span>难度：{{ difficultyLabel(relation.difficulty) }}</span>
              <span>匹配分数：{{ relation.matchScore }}</span>
            </div>
            <div
              v-if="relation.matchReason"
              class="match-reason"
            >
              推荐理由：{{ relation.matchReason }}
            </div>
            <div
              v-if="relation.confirmedTime"
              class="muted-text"
            >
              处理时间：{{ relation.confirmedTime }}
            </div>
          </div>
        </div>
      </template>
    </div>

    <template #footer>
      <el-button
        :disabled="saving"
        @click="closeDialog"
      >
        关闭
      </el-button>
    </template>
  </el-dialog>
</template>

<style scoped>
.knowledge-panel {
  display: flex;
  flex-direction: column;
  gap: 16px;
  min-height: 180px;
}

.chunk-summary,
.manual-panel,
.relation-card {
  padding: 16px;
  border: 1px solid var(--el-border-color);
  border-radius: 8px;
}

.chunk-summary {
  background: var(--el-fill-color-light);
}

.summary-header,
.relation-card-header,
.relation-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.summary-header {
  margin-bottom: 12px;
}

.chunk-content {
  max-height: 180px;
  overflow-y: auto;
  line-height: 1.8;
  white-space: pre-wrap;
  overflow-wrap: anywhere;
  color: var(--el-text-color-regular);
}

.muted-text {
  font-size: 12px;
  color: var(--el-text-color-secondary);
  line-height: 1.6;
}

.knowledge-statistics {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
}

.stat-item {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 14px;
  border: 1px solid var(--el-border-color);
  border-radius: 8px;
}

.stat-item span {
  font-size: 13px;
  color: var(--el-text-color-secondary);
}

.stat-item strong {
  font-size: 24px;
}

.warning-text {
  color: var(--el-color-warning);
}

.success-text {
  color: var(--el-color-success);
}

.knowledge-toolbar,
.batch-toolbar,
.manual-actions {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
}

.knowledge-toolbar :deep(.el-button),
.batch-toolbar :deep(.el-button),
.manual-actions :deep(.el-button) {
  margin-left: 0;
}

.manual-panel {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.point-select {
  width: 100%;
}

.manual-actions {
  justify-content: flex-end;
}

.section-title {
  font-size: 15px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.relation-section {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.batch-toolbar {
  padding: 8px 12px;
  border-radius: 6px;
  background: var(--el-fill-color-light);
}

.relation-card {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.relation-name {
  display: flex;
  align-items: center;
  gap: 8px;
  overflow-wrap: anywhere;
}

.relation-meta {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 10px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.match-reason {
  padding: 10px;
  border-radius: 6px;
  background: var(--el-fill-color-light);
  color: var(--el-text-color-regular);
  font-size: 13px;
  line-height: 1.6;
}
</style>
