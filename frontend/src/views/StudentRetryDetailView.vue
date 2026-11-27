<script setup lang="ts">
import {
  computed,
  onBeforeUnmount,
  onMounted,
  reactive,
  ref,
  watch,
} from 'vue'
import { ElMessageBox } from 'element-plus'
import { onBeforeRouteLeave, useRoute, useRouter } from 'vue-router'
import { ChevronLeft, Loader } from '@lucide/vue'

import {
  getStudentRetryDetail,
  getStudentRetryDraft,
  saveStudentRetryDraft,
  startStudentRetry,
  submitStudentRetry,
} from '@/api/exercises'
import type {
  ExerciseQuestionType,
  StudentRetryAnswerRequest,
  StudentRetryAttempt,
  StudentRetryDetail,
} from '@/api/exercises'
import type { BigIntId } from '@/api/types'
import BaseCard from '@/components/ui/BaseCard.vue'
import EmptyState from '@/components/ui/EmptyState.vue'
import {
  notifyError,
  notifySuccess,
  notifyWarning,
} from '@/utils/notify'

defineOptions({ name: 'StudentRetryDetailView' })

const route = useRoute()
const router = useRouter()

const loading = ref(false)
const starting = ref(false)
const submitting = ref(false)
const detail = ref<StudentRetryDetail | null>(null)
const attempt = ref<StudentRetryAttempt | null>(null)
const submitted = ref(false)
const answers = reactive<Record<string, string | string[]>>({})

// 草稿自动保存状态
const savingDraft = ref(false)
const draftDirty = ref(false)
const draftSavedAt = ref('')

const restoringDraft = ref(false)

let draftTimer: ReturnType<typeof setTimeout> | null = null

let editVersion = 0
let activeSaveCount = 0

// 串行保存，避免较早的请求覆盖较新的答案
let saveQueue: Promise<void> = Promise.resolve()

const listId = computed(
  () => String(route.params.listId ?? ''),
)

const answering = computed(
  () => attempt.value?.status === 'IN_PROGRESS',
)

const answeredCount = computed(() => {
  if (!detail.value) return 0

  return detail.value.items.filter((item) => {
    const answer = answers[String(item.id)]

    if (Array.isArray(answer)) return answer.length > 0

    return typeof answer === 'string' && answer.trim() !== ''
  }).length
})

const progress = computed(() => {
  const total = detail.value?.items.length ?? 0

  return total === 0
    ? 0
    : Math.round(answeredCount.value / total * 100)
})

watch(
  answers,
  () => {
    if (
      !answering.value
      || restoringDraft.value
      || submitted.value
    ) {
      return
    }

    editVersion++
    draftDirty.value = true

    if (draftTimer) {
      clearTimeout(draftTimer)
    }

    draftTimer = setTimeout(() => {
      void flushDraft()
    }, 400)
  },
  {
    deep: true,
    flush: 'sync',
  },
)

async function flushDraft(): Promise<boolean> {
  if (draftTimer) {
    clearTimeout(draftTimer)
    draftTimer = null
  }

  if (
    !attempt.value
    || !detail.value
    || !answering.value
    || submitted.value
    || !draftDirty.value
  ) {
    return true
  }

  const currentVersion = editVersion
  const payload = buildDraftAnswers()
  const attemptId = attempt.value.id

  activeSaveCount++
  savingDraft.value = true

  const currentRequest = saveQueue.then(async () => {
    await saveStudentRetryDraft(attemptId, payload)
  })

  saveQueue = currentRequest.then(
    () => undefined,
    () => undefined,
  )

  try {
    await currentRequest

    if (currentVersion === editVersion) {
      draftDirty.value = false
      draftSavedAt.value = new Date().toLocaleTimeString(
        'zh-CN',
        {
          hour: '2-digit',
          minute: '2-digit',
          second: '2-digit',
        },
      )
    }

    return true
  } catch (error) {
    notifyError(error)
    return false
  } finally {
    activeSaveCount--
    savingDraft.value = activeSaveCount > 0
  }
}

async function restoreDraft(attemptId: BigIntId): Promise<void> {
  const draft = await getStudentRetryDraft(attemptId)

  const savedAnswers = new Map(
    draft.answers.map(item => [
      String(item.retryItemId),
      item.answer,
    ]),
  )

  restoringDraft.value = true

  try {
    for (const item of detail.value?.items ?? []) {
      const key = String(item.id)
      const value = savedAnswers.get(key) ?? ''

      if (item.questionType === 'MULTIPLE_CHOICE') {
        answers[key] = value
          ? value.split(',').filter(Boolean)
          : []
      } else {
        answers[key] = value
      }
    }

    draftDirty.value = false
  } finally {
    restoringDraft.value = false
  }
}

onMounted(() => {
  void loadDetail()
})

async function loadDetail(): Promise<void> {
  if (!/^[1-9]\d*$/.test(listId.value)) {
    notifyWarning('重练清单ID不正确')
    await backToList()
    return
  }

  loading.value = true

  try {
    detail.value = await getStudentRetryDetail(listId.value)
  } catch (error) {
    notifyError(error)
  } finally {
    loading.value = false
  }
}

function questionTypeLabel(type: ExerciseQuestionType): string {
  const labels: Record<ExerciseQuestionType, string> = {
    SINGLE_CHOICE: '单选题',
    MULTIPLE_CHOICE: '多选题',
    TRUE_FALSE: '判断题',
    SHORT_ANSWER: '简答题',
  }

  return labels[type]
}

function getAnswer(id: BigIntId): string {
  const value = answers[String(id)]

  return typeof value === 'string' ? value : ''
}

function getMultipleAnswer(id: BigIntId): string[] {
  const value = answers[String(id)]

  return Array.isArray(value) ? value : []
}

function setAnswer(
  id: BigIntId,
  value: string | number,
): void {
  answers[String(id)] = String(value)
}

function setMultipleAnswer(
  id: BigIntId,
  value: Array<string | number | boolean>,
): void {
  answers[String(id)] = value.map(String)
}

async function startAnswering(): Promise<void> {
  if (!detail.value || starting.value) return

  starting.value = true

  try {
    const started = await startStudentRetry(detail.value.id)

    if (started.status !== 'IN_PROGRESS') {
      notifyWarning('该清单已经提交，请查看重练结果')

      await router.replace({
        name: 'student-retry-result',
        params: {
          attemptId: started.id,
        },
      })

      return
    }

    // 先恢复服务器答案，再进入答题状态
    await restoreDraft(started.id)

    attempt.value = started

    notifySuccess('已进入重练，答案将自动保存')
  } catch (error) {
    notifyError(error)
  } finally {
    starting.value = false
  }
}

function buildDraftAnswers() {
  if (!detail.value) return []

  return detail.value.items.map((item) => {
    const value = answers[String(item.id)]

    return {
      retryItemId: item.id,
      answer: Array.isArray(value)
        ? [...value].sort().join(',')
        : value ?? '',
    }
  })
}

function buildSubmitAnswers(): StudentRetryAnswerRequest[] {
  if (!detail.value) return []

  return detail.value.items.map((item) => {
    const value = answers[String(item.id)]

    return {
      retryItemId: item.id,
      answer: Array.isArray(value)
        ? [...value].sort().join(',')
        : value ?? '',
    }
  })
}

async function submitAnswers(): Promise<void> {
  if (!attempt.value || !detail.value || submitting.value) {
    return
  }

  const total = detail.value.items.length
  const unanswered = total - answeredCount.value

  try {
    await ElMessageBox.confirm(
      unanswered > 0
        ? `还有 ${unanswered} 道题没有作答，确定提交吗？`
        : '确认提交本次重练吗？提交后不能修改答案。',
      '提交重练',
      {
        confirmButtonText: '确认提交',
        cancelButtonText: '继续作答',
        type: 'warning',
      },
    )
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') {
      notifyError(error)
    }
    return
  }

  // 提交前先保存最后一次修改
  const saved = await flushDraft()

  if (!saved) {
    notifyWarning('答案自动保存失败，请检查网络后重试')
    return
  }

  // 等待之前排队的保存请求全部结束
  await saveQueue

  submitting.value = true

  try {
    const result = await submitStudentRetry(
      attempt.value.id,
      buildSubmitAnswers(),
    )

    submitted.value = true
    notifySuccess('重练提交成功')

    await router.replace({
      name: 'student-retry-result',
      params: {
        attemptId: result.attemptId,
      },
    })
  } catch (error) {
    notifyError(error)
  } finally {
    submitting.value = false
  }
}

async function backToList(): Promise<void> {
  await router.push({ name: 'my-retry-lists' })
}

onBeforeRouteLeave(async () => {
  if (!answering.value || submitted.value) {
    return true
  }

  const saved = await flushDraft()

  if (saved) {
    await saveQueue
    return true
  }

  try {
    await ElMessageBox.confirm(
      '答案自动保存失败，离开页面可能丢失最近一次修改。确定离开吗？',
      '保存失败',
      {
        confirmButtonText: '仍然离开',
        cancelButtonText: '继续答题',
        type: 'warning',
      },
    )

    return true
  } catch {
    return false
  }
})

onBeforeUnmount(() => {
  if (draftTimer) {
    clearTimeout(draftTimer)
    draftTimer = null
  }
})
</script>

<template>
  <div class="student-retry-detail">
    <el-button text class="back-button" @click="backToList">
      <ChevronLeft :size="17" />
      返回重练清单
    </el-button>

    <div v-if="loading">
      <el-skeleton :rows="6" animated />
    </div>

    <EmptyState
      v-else-if="!detail"
      title="重练清单不存在"
      description="清单可能已经删除，或你没有查看权限。"
    >
      <template #action>
        <el-button @click="backToList">
          返回重练清单
        </el-button>
      </template>
    </EmptyState>

    <template v-else>
      <BaseCard>
        <div class="retry-header">
          <div>
            <div class="retry-eyebrow">MISTAKE RETRY</div>
            <h2>{{ detail.title }}</h2>
            <p class="retry-description">
              重新完成清单中的错题，巩固薄弱知识点。
            </p>
          </div>
          <el-tag :type="answering ? 'success' : 'info'">
            {{ answering ? '正在重练' : '重练预览' }}
          </el-tag>
        </div>

        <div class="retry-meta">
          <span>{{ detail.items.length }} 道题目</span>
          <span>
            总分：
            {{ detail.items.reduce((sum, item) => sum + Number(item.score), 0) }}
          </span>
        </div>
      </BaseCard>

      <BaseCard v-if="answering">
        <div class="answer-progress">
          <div>
            <strong>答题进度</strong>
            <span>{{ answeredCount }}/{{ detail.items.length }}</span>
          </div>
          <el-progress :percentage="progress" :stroke-width="8" />
          <div class="draft-save-status">
            <template v-if="savingDraft">
              <el-icon class="is-loading">
                <Loader />
              </el-icon>
              正在保存答案...
            </template>

            <template v-else-if="draftDirty">
              <span class="draft-save-warning">
                有尚未保存的修改
              </span>
            </template>

            <template v-else-if="draftSavedAt">
              <span class="draft-save-success">
                已保存 {{ draftSavedAt }}
              </span>
            </template>

            <template v-else>
              答案自动保存已开启
            </template>
          </div>
        </div>
      </BaseCard>

      <BaseCard title="重练题目">
        <div
          v-for="(item, index) in detail.items"
          :key="item.id"
          class="question-card"
        >
          <div class="question-heading">
            <div class="question-number">第 {{ index + 1 }} 题</div>
            <div class="question-tags">
              <el-tag size="small">
                {{ questionTypeLabel(item.questionType) }}
              </el-tag>
              <el-tag size="small" type="info">
                {{ item.score }} 分
              </el-tag>
            </div>
          </div>

          <div class="question-source">
            {{ item.courseName }} · {{ item.exerciseTitle }}
          </div>
          <div
            v-if="item.knowledgePointTitle"
            class="question-knowledge"
          >
            知识点：{{ item.knowledgePointTitle }}
          </div>
          <div class="question-stem">{{ item.stem }}</div>

          <template v-if="item.questionType === 'SINGLE_CHOICE'">
            <el-radio-group
              v-if="answering"
              :model-value="getAnswer(item.id)"
              class="option-group"
              @update:model-value="setAnswer(item.id, $event)"
            >
              <el-radio
                v-for="option in item.options"
                :key="option.label"
                :value="option.label"
                class="question-option"
              >
                <strong>{{ option.label }}.</strong>
                {{ option.content }}
              </el-radio>
            </el-radio-group>

            <div v-else class="option-preview">
              <div
                v-for="option in item.options"
                :key="option.label"
                class="preview-option"
              >
                <strong>{{ option.label }}.</strong>
                {{ option.content }}
              </div>
            </div>
          </template>

          <template v-else-if="item.questionType === 'MULTIPLE_CHOICE'">
            <el-checkbox-group
              v-if="answering"
              :model-value="getMultipleAnswer(item.id)"
              class="option-group"
              @update:model-value="setMultipleAnswer(item.id, $event)"
            >
              <el-checkbox
                v-for="option in item.options"
                :key="option.label"
                :value="option.label"
                class="question-option"
              >
                <strong>{{ option.label }}.</strong>
                {{ option.content }}
              </el-checkbox>
            </el-checkbox-group>

            <div v-else class="option-preview">
              <div
                v-for="option in item.options"
                :key="option.label"
                class="preview-option"
              >
                <strong>{{ option.label }}.</strong>
                {{ option.content }}
              </div>
            </div>
          </template>

          <template v-else-if="item.questionType === 'TRUE_FALSE'">
            <el-radio-group
              v-if="answering"
              :model-value="getAnswer(item.id)"
              class="option-group"
              @update:model-value="setAnswer(item.id, $event)"
            >
              <el-radio value="TRUE" class="question-option">正确</el-radio>
              <el-radio value="FALSE" class="question-option">错误</el-radio>
            </el-radio-group>

            <div v-else class="option-preview">
              <div class="preview-option">正确</div>
              <div class="preview-option">错误</div>
            </div>
          </template>

          <el-input
            v-else-if="item.questionType === 'SHORT_ANSWER' && answering"
            :model-value="getAnswer(item.id)"
            type="textarea"
            :rows="5"
            maxlength="10000"
            show-word-limit
            placeholder="请输入你的答案"
            @update:model-value="setAnswer(item.id, $event)"
          />

          <div
            v-else-if="item.questionType === 'SHORT_ANSWER'"
            class="short-answer-preview"
          >
            开始重练后可填写简答题答案
          </div>
        </div>
      </BaseCard>

      <BaseCard>
        <div class="retry-actions">
          <div class="action-hint">
            <template v-if="!answering">
              请确认题目内容后开始重练。
            </template>
            <template v-else>
              已完成 {{ answeredCount }} 道，
              共 {{ detail.items.length }} 道题。
            </template>
          </div>

          <el-button
            v-if="!answering"
            type="primary"
            :loading="starting"
            @click="startAnswering"
          >
            开始重练
          </el-button>
          <el-button
            v-else
            type="primary"
            :loading="submitting"
            @click="submitAnswers"
          >
            提交重练
          </el-button>
        </div>
      </BaseCard>
    </template>
  </div>
</template>

<style scoped>
.student-retry-detail {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.back-button {
  align-self: flex-start;
  color: var(--sc-text-secondary);
}

.retry-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 16px;
}

.retry-eyebrow {
  color: var(--sc-primary);
  font-size: 10px;
  font-weight: 700;
  letter-spacing: 2px;
}

.retry-header h2 {
  margin: 8px 0 0;
  color: var(--sc-text);
  font-size: 21px;
}

.retry-description {
  margin: 10px 0 0;
  color: var(--sc-text-secondary);
  font-size: 13px;
  line-height: 1.7;
}

.retry-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 18px;
  margin-top: 20px;
  padding-top: 15px;
  border-top: 1px solid var(--sc-border);
  color: var(--sc-text-secondary);
  font-size: 12px;
}

.answer-progress {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.answer-progress > div:first-child {
  display: flex;
  justify-content: space-between;
  font-size: 13px;
}

.question-card {
  padding: 20px 0;
  border-bottom: 1px solid var(--sc-border);
}

.question-card:last-child {
  border-bottom: 0;
}

.question-heading {
  display: flex;
  align-items: center;
  gap: 12px;
}

.question-number {
  color: var(--sc-text);
  font-size: 14px;
  font-weight: 650;
}

.question-tags {
  display: flex;
  gap: 7px;
}

.question-source {
  margin-top: 12px;
  color: var(--sc-text-placeholder);
  font-size: 11px;
}

.question-knowledge {
  margin-top: 8px;
  color: var(--sc-primary);
  font-size: 12px;
}

.question-stem {
  margin: 15px 0;
  color: var(--sc-text);
  font-size: 14px;
  line-height: 1.9;
  white-space: pre-wrap;
}

.option-group {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 13px;
}

.question-option {
  height: auto;
  margin-right: 0;
  line-height: 1.7;
}

.question-option :deep(.el-radio__label),
.question-option :deep(.el-checkbox__label) {
  white-space: normal;
  line-height: 1.7;
}

.preview-option {
  padding: 9px 12px;
  margin-bottom: 7px;
  border: 1px solid var(--sc-border);
  border-radius: 8px;
  color: var(--sc-text-secondary);
  font-size: 13px;
}

.short-answer-preview {
  padding: 15px;
  border: 1px dashed var(--sc-border);
  border-radius: 8px;
  color: var(--sc-text-placeholder);
  font-size: 12px;
}

.retry-actions {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.action-hint {
  color: var(--sc-text-secondary);
  font-size: 12px;
}

@media (max-width: 640px) {
  .retry-header {
    flex-direction: column;
  }

  .retry-actions {
    align-items: stretch;
    flex-direction: column;
  }

  .retry-actions .el-button {
    width: 100%;
  }
}

.draft-save-status {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-top: 10px;
  color: var(--sc-text-placeholder);
  font-size: 11px;
}

.draft-save-success {
  color: var(--sc-success, #16a34a);
}

.draft-save-warning {
  color: var(--sc-warning, #d97706);
}
</style>
