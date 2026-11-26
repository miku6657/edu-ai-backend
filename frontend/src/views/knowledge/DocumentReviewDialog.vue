<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { ElMessageBox } from 'element-plus'

import type { BigIntId } from '@/api/types'
import {
  documentReviewStatusLabel,
  documentReviewStatusTagType,
  versionStatusLabel,
} from '@/api/knowledge'
import type { KnowledgeDoc } from '@/api/knowledge'
import {
  decideDocumentReview,
  getDocumentReviewDetail,
  reviewActionLabel,
  reviewActionTagType,
  submitDocumentReview,
  withdrawDocumentReview,
} from '@/api/document-review'
import type {
  DocumentReviewAction,
  DocumentReviewDetail,
} from '@/api/document-review'
import {
  notifyError,
  notifySuccess,
  notifyWarning,
} from '@/utils/notify'

const props = defineProps<{
  modelValue: boolean
  courseId: BigIntId | null
  document: KnowledgeDoc | null
}>()

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  changed: [detail: DocumentReviewDetail]
  preview: [document: KnowledgeDoc]
  chunks: [document: KnowledgeDoc]
}>()

interface ReviewContext {
  courseId: BigIntId
  documentId: BigIntId
}

const loading = ref(false)
const saving = ref(false)
const detail = ref<DocumentReviewDetail | null>(null)
const comment = ref('')
const errorMessage = ref('')
let requestVersion = 0

const busy = computed(() => loading.value || saving.value)
const progress = computed(() => detail.value?.knowledgeProgress ?? null)
const completedChunks = computed(
  () =>
    (progress.value?.confirmedChunks ?? 0)
    + (progress.value?.notApplicableChunks ?? 0),
)
const canSubmit = computed(
  () => !busy.value && detail.value?.canSubmit === true,
)
const canReview = computed(
  () => !busy.value && detail.value?.canReview === true,
)
const canApprove = computed(
  () => !busy.value && detail.value?.canApprove === true,
)
const canWithdraw = computed(
  () => !busy.value && detail.value?.canWithdraw === true,
)

const statusDescription = computed(() => {
  const document = detail.value
  if (!document) return ''

  if (document.versionStatus === 'HISTORY') {
    return '当前资料为历史版本，仅供查看。'
  }
  if (document.published) {
    return '当前资料已正式发布，可以参与知识库问答。'
  }
  if (document.status !== 'READY') {
    return '资料尚未完成解析，暂时不能提交发布审核。'
  }

  switch (document.reviewStatus) {
    case 'DRAFT':
      return progress.value?.completed
        ? '知识点关联已全部完成，可以提交发布审核。'
        : '请先完成全部知识切片的知识点关联。'
    case 'PENDING':
      return '资料正在等待审核，审核期间不能修改切片。'
    case 'REJECTED':
      return '资料审核未通过，请根据审核意见调整后重新提交。'
    case 'APPROVED':
      return '当前版本已经通过审核，但不一定是当前生效版本。'
    default:
      return '当前资料审核状态未知。'
  }
})

const statusAlertType = computed<
  'success' | 'warning' | 'error' | 'info'
>(() => {
  if (detail.value?.published) return 'success'
  if (detail.value?.reviewStatus === 'REJECTED') return 'error'
  if (detail.value?.reviewStatus === 'PENDING') return 'warning'
  return 'info'
})

function getContext(): ReviewContext | null {
  if (
    !props.modelValue
    || !props.courseId
    || !props.document
    || props.document.courseId !== props.courseId
  ) {
    return null
  }
  return {
    courseId: props.courseId,
    documentId: props.document.id,
  }
}

function isCurrentContext(context: ReviewContext): boolean {
  return (
    props.modelValue
    && props.courseId === context.courseId
    && props.document?.id === context.documentId
  )
}

function resetState(): void {
  detail.value = null
  comment.value = ''
  errorMessage.value = ''
}

async function loadDetail(): Promise<void> {
  const context = getContext()
  if (!context) return

  const version = ++requestVersion
  loading.value = true
  errorMessage.value = ''
  try {
    const result = await getDocumentReviewDetail(
      context.courseId,
      context.documentId,
    )
    if (version !== requestVersion || !isCurrentContext(context)) return
    detail.value = result
  } catch (error) {
    if (version !== requestVersion || !isCurrentContext(context)) return
    detail.value = null
    errorMessage.value = '文档审核详情加载失败'
    notifyError(error, errorMessage.value)
  } finally {
    if (version === requestVersion) loading.value = false
  }
}

async function confirmAction(
  message: string,
  title: string,
): Promise<boolean> {
  try {
    await ElMessageBox.confirm(message, title, {
      confirmButtonText: '确认',
      cancelButtonText: '取消',
      type: 'warning',
    })
    return true
  } catch {
    return false
  }
}

async function executeAction(action: DocumentReviewAction): Promise<void> {
  const context = getContext()
  if (!context || busy.value) return
  if (action === 'SUBMIT' && !canSubmit.value) return
  if (
    (action === 'APPROVE' || action === 'REJECT')
    && !canReview.value
  ) {
    return
  }
  if (action === 'APPROVE' && !canApprove.value) return
  if (action === 'WITHDRAW' && !canWithdraw.value) return

  const normalizedComment = comment.value.trim()
  if (action === 'REJECT' && !normalizedComment) {
    notifyWarning('审核驳回时必须填写原因')
    return
  }
  if (normalizedComment.length > 1000) {
    notifyWarning('审核意见不能超过1000个字符')
    return
  }

  const messages: Record<DocumentReviewAction, string> = {
    SUBMIT: '确定提交发布审核吗？提交后将暂时禁止修改知识切片。',
    APPROVE:
      '确定审核通过并正式发布该资料吗？'
      + '如果该资源已有生效版本，原版本将转为历史版本。',
    REJECT: '确定驳回该资料的发布审核吗？',
    WITHDRAW: '确定撤回审核申请吗？撤回后可以继续修改知识切片。',
  }

  saving.value = true
  try {
    const confirmed = await confirmAction(
      messages[action],
      reviewActionLabel(action),
    )
    if (!confirmed || !isCurrentContext(context)) return

    let result: DocumentReviewDetail
    switch (action) {
      case 'SUBMIT':
        result = await submitDocumentReview(
          context.courseId,
          context.documentId,
          { comment: normalizedComment },
        )
        break
      case 'APPROVE':
      case 'REJECT':
        result = await decideDocumentReview(
          context.courseId,
          context.documentId,
          { decision: action, comment: normalizedComment },
        )
        break
      case 'WITHDRAW':
        result = await withdrawDocumentReview(
          context.courseId,
          context.documentId,
          { comment: normalizedComment },
        )
        break
    }

    emit('changed', result)
    if (!isCurrentContext(context)) return
    detail.value = result
    comment.value = ''
    notifySuccess(`${reviewActionLabel(action)}成功`)
  } catch (error) {
    if (isCurrentContext(context)) {
      notifyError(error, `${reviewActionLabel(action)}失败`)
      await loadDetail()
    }
  } finally {
    saving.value = false
  }
}

function currentDocument(): KnowledgeDoc | null {
  if (!props.document || !detail.value) return null
  return {
    ...props.document,
    status: detail.value.status,
    versionStatus: detail.value.versionStatus,
    reviewStatus: detail.value.reviewStatus,
    reviewSubmitterId: detail.value.reviewSubmitterId,
    reviewSubmitTime: detail.value.reviewSubmitTime,
    reviewerId: detail.value.reviewerId,
    reviewTime: detail.value.reviewTime,
    reviewComment: detail.value.reviewComment,
  }
}

function openPreview(): void {
  const document = currentDocument()
  if (!document || busy.value) return
  emit('preview', document)
}

function openChunks(): void {
  const document = currentDocument()
  if (!document || busy.value) return
  emit('update:modelValue', false)
  emit('chunks', document)
}

function formatTime(value: string | null | undefined): string {
  if (!value) return '—'
  return value.replace('T', ' ').slice(0, 19)
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
    props.document?.id,
  ] as const,
  ([visible]) => {
    requestVersion++
    loading.value = false
    resetState()
    if (visible) void loadDetail()
  },
  { immediate: true },
)
</script>

<template>
  <el-dialog
    :model-value="modelValue"
    title="知识库发布审核"
    width="min(880px, 96vw)"
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
      class="review-panel"
    >
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
        @click="loadDetail"
      >
        重新加载
      </el-button>

      <template v-if="detail">
        <div class="review-document">
          <div class="document-header">
            <div class="document-title">
              {{ detail.originalName }}
            </div>
            <el-tag :type="documentReviewStatusTagType(detail.reviewStatus)">
              {{ documentReviewStatusLabel(detail.reviewStatus) }}
            </el-tag>
          </div>

          <el-descriptions :column="2" border size="small">
            <el-descriptions-item label="资源版本">
              V{{ detail.versionNo }}
            </el-descriptions-item>
            <el-descriptions-item label="版本状态">
              {{ versionStatusLabel(detail.versionStatus) }}
            </el-descriptions-item>
            <el-descriptions-item label="提交时间">
              {{ formatTime(detail.reviewSubmitTime) }}
            </el-descriptions-item>
            <el-descriptions-item label="提交人 ID">
              {{ detail.reviewSubmitterId || '—' }}
            </el-descriptions-item>
            <el-descriptions-item label="审核时间">
              {{ formatTime(detail.reviewTime) }}
            </el-descriptions-item>
            <el-descriptions-item label="审核人 ID">
              {{ detail.reviewerId || '—' }}
            </el-descriptions-item>
          </el-descriptions>

          <div class="document-actions">
            <el-button
              v-if="detail.status === 'READY'"
              :disabled="busy"
              @click="openPreview"
            >
              预览原始资料
            </el-button>
            <el-button
              v-if="detail.status === 'READY'"
              :disabled="busy"
              @click="openChunks"
            >
              查看知识切片
            </el-button>
            <el-button
              :disabled="busy"
              @click="loadDetail"
            >
              刷新审核状态
            </el-button>
          </div>
        </div>

        <el-alert
          :title="statusDescription"
          :type="statusAlertType"
          :closable="false"
          show-icon
        />

        <section
          v-if="progress"
          class="review-section"
        >
          <div class="section-header">
            <strong>知识点关联检查</strong>
            <el-tag
              :type="progress.completed ? 'success' : 'warning'"
              size="small"
            >
              {{ progress.completed ? '已完成' : '未完成' }}
            </el-tag>
          </div>
          <div class="statistics-grid">
            <div class="stat-item">
              <span>切片总数</span>
              <strong>{{ progress.totalChunks }}</strong>
            </div>
            <div class="stat-item">
              <span>未处理</span>
              <strong>{{ progress.unprocessedChunks }}</strong>
            </div>
            <div class="stat-item">
              <span>待处理</span>
              <strong class="warning-text">
                {{ progress.recommendedChunks }}
              </strong>
            </div>
            <div class="stat-item">
              <span>已确认</span>
              <strong class="success-text">
                {{ progress.confirmedChunks }}
              </strong>
            </div>
            <div class="stat-item">
              <span>无需关联</span>
              <strong>{{ progress.notApplicableChunks }}</strong>
            </div>
          </div>
          <el-progress
            :percentage="progress.completionPercent"
            :status="progress.completed ? 'success' : undefined"
            :stroke-width="10"
          />
          <div class="muted-text">
            已完成 {{ completedChunks }} / {{ progress.totalChunks }} 个切片，
            待确认关联 {{ progress.pendingRelations }} 条。
          </div>
        </section>

        <section class="review-section">
          <strong>知识库质量检查</strong>
          <el-alert
            v-if="detail.qualityBlockReason"
            :title="detail.qualityBlockReason"
            type="warning"
            :closable="false"
            show-icon
          />
          <el-alert
            v-else
            title="当前不存在阻止发布的质量抽检问题"
            type="success"
            :closable="false"
            show-icon
          />
        </section>

        <section
          v-if="detail.reviewComment"
          class="review-section"
        >
          <strong>最近审核意见</strong>
          <div class="review-comment">
            {{ detail.reviewComment }}
          </div>
        </section>

        <section
          v-if="detail.canSubmit || detail.canReview || detail.canWithdraw"
          class="review-section"
        >
          <strong>{{ detail.canReview ? '审核意见' : '提交说明' }}</strong>
          <el-input
            v-model="comment"
            type="textarea"
            :rows="4"
            maxlength="1000"
            show-word-limit
            :disabled="busy"
            :placeholder="
              detail.canReview
                ? '请输入审核意见，驳回时必须填写原因'
                : '请输入本次提交审核的说明（选填）'
            "
          />
          <div class="review-actions">
            <el-button
              v-if="detail.canSubmit"
              type="primary"
              :disabled="!canSubmit"
              :loading="saving"
              @click="executeAction('SUBMIT')"
            >
              提交审核
            </el-button>
            <el-button
              v-if="detail.canReview"
              type="success"
              :disabled="!canApprove"
              :loading="saving"
              @click="executeAction('APPROVE')"
            >
              审核通过并发布
            </el-button>
            <el-button
              v-if="detail.canReview"
              type="danger"
              plain
              :disabled="!canReview"
              @click="executeAction('REJECT')"
            >
              审核驳回
            </el-button>
            <el-button
              v-if="detail.canWithdraw"
              :disabled="!canWithdraw"
              @click="executeAction('WITHDRAW')"
            >
              撤回申请
            </el-button>
          </div>
        </section>

        <section class="review-section">
          <div class="section-header">
            <strong>审核历史</strong>
            <span class="muted-text">
              最近 {{ detail.records.length }} 条记录
            </span>
          </div>
          <el-empty
            v-if="!detail.records.length"
            description="暂无审核历史"
            :image-size="70"
          />
          <el-timeline v-else>
            <el-timeline-item
              v-for="record in detail.records"
              :key="record.id"
              :timestamp="formatTime(record.createTime)"
              :type="reviewActionTagType(record.action)"
              placement="top"
            >
              <div class="history-card">
                <div class="history-header">
                  <strong>{{ reviewActionLabel(record.action) }}</strong>
                  <span class="muted-text">
                    操作人 ID：{{ record.operatorId }}
                  </span>
                </div>
                <div class="history-transition">
                  <el-tag
                    size="small"
                    :type="documentReviewStatusTagType(record.previousStatus)"
                  >
                    {{ documentReviewStatusLabel(record.previousStatus) }}
                  </el-tag>
                  <span>→</span>
                  <el-tag
                    size="small"
                    :type="documentReviewStatusTagType(record.nextStatus)"
                  >
                    {{ documentReviewStatusLabel(record.nextStatus) }}
                  </el-tag>
                </div>
                <div
                  v-if="record.comment"
                  class="history-comment"
                >
                  {{ record.comment }}
                </div>
              </div>
            </el-timeline-item>
          </el-timeline>
        </section>
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
.review-panel {
  display: flex;
  flex-direction: column;
  gap: 16px;
  min-height: 160px;
}

.review-document,
.review-section {
  display: flex;
  flex-direction: column;
  gap: 14px;
  padding: 16px;
  border: 1px solid var(--el-border-color);
  border-radius: 8px;
}

.document-header,
.section-header,
.history-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.document-title {
  font-size: 17px;
  font-weight: 600;
  overflow-wrap: anywhere;
}

.document-actions,
.review-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.document-actions :deep(.el-button),
.review-actions :deep(.el-button) {
  margin-left: 0;
}

.statistics-grid {
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  gap: 10px;
}

.stat-item {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 12px;
  border-radius: 6px;
  background: var(--el-fill-color-light);
}

.stat-item span {
  color: var(--el-text-color-secondary);
  font-size: 12px;
}

.stat-item strong {
  font-size: 24px;
}

.success-text {
  color: var(--el-color-success);
}

.warning-text {
  color: var(--el-color-warning);
}

.muted-text {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.review-comment,
.history-comment {
  padding: 12px;
  border-radius: 6px;
  background: var(--el-fill-color-light);
  white-space: pre-wrap;
  overflow-wrap: anywhere;
  line-height: 1.7;
}

.history-card {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.history-transition {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
}

.review-section :deep(.el-timeline) {
  margin: 0;
  padding-left: 6px;
}

@media (max-width: 768px) {
  .statistics-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
</style>
