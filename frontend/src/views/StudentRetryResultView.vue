<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import {
  getStudentRetryResult,
  selfReviewStudentRetry,
} from '@/api/exercises'
import type {
  StudentRetryResult,
  StudentRetrySelfReviewItem,
} from '@/api/exercises'
import BaseCard from '@/components/ui/BaseCard.vue'
import EmptyState from '@/components/ui/EmptyState.vue'
import { notifyError, notifySuccess } from '@/utils/notify'

defineOptions({ name: 'StudentRetryResultView' })

const route = useRoute()
const router = useRouter()

const loading = ref(false)
const submitting = ref(false)
const result = ref<StudentRetryResult | null>(null)
const review = reactive<Record<string, boolean | undefined>>({})

const attemptId = computed(
  () => String(route.params.attemptId ?? ''),
)

const needsSelfReview = computed(
  () => result.value?.status === 'PENDING_SELF_REVIEW',
)

const pendingItems = computed(
  () => result.value?.answers.filter(
    answer => answer.pendingSelfReview,
  ) ?? [],
)

const objectiveAnswers = computed(() =>
  result.value?.answers.filter(
    answer => answer.questionType !== 'SHORT_ANSWER',
  ) ?? [],
)

const correctCount = computed(() =>
  objectiveAnswers.value.filter(
    answer => answer.correct === true,
  ).length,
)

const wrongCount = computed(() =>
  objectiveAnswers.value.filter(
    answer => answer.correct === false,
  ).length,
)

const pendingCount = computed(
  () => result.value?.pendingSelfReviewCount ?? 0,
)

const accuracy = computed(() => {
  const total = objectiveAnswers.value.length

  if (total === 0) return 0

  return Math.round(correctCount.value / total * 100)
})

onMounted(() => {
  void loadResult()
})

async function loadResult(): Promise<void> {
  if (!/^[1-9]\d*$/.test(attemptId.value)) {
    return
  }

  loading.value = true

  try {
    result.value = await getStudentRetryResult(attemptId.value)
  } catch (error) {
    notifyError(error)
  } finally {
    loading.value = false
  }
}

async function submitSelfReview(): Promise<void> {
  if (!result.value || submitting.value) return

  const unanswered = pendingItems.value.filter(
    answer => review[String(answer.retryItemId)] === undefined,
  )

  if (unanswered.length > 0) {
    notifyError(`还有 ${unanswered.length} 道题没有完成核对`)
    return
  }

  const attempt = result.value.attemptId

  const items: StudentRetrySelfReviewItem[] =
    pendingItems.value.map(answer => ({
      retryItemId: answer.retryItemId,
      understood: review[String(answer.retryItemId)] as boolean,
    }))

  submitting.value = true

  try {
    result.value = await selfReviewStudentRetry(attempt, items)
    notifySuccess('自我核对完成')
  } catch (error) {
    notifyError(error)
  } finally {
    submitting.value = false
  }
}

function resultStatusLabel(status: string): string {
  switch (status) {
    case 'IN_PROGRESS':
      return '进行中'
    case 'PENDING_SELF_REVIEW':
      return '待自我核对'
    case 'GRADED':
      return '已完成判分'
    default:
      return '未知状态'
  }
}

function resultStatusType(
  status: string,
): 'success' | 'warning' | 'info' {
  switch (status) {
    case 'GRADED':
      return 'success'
    case 'PENDING_SELF_REVIEW':
      return 'warning'
    default:
      return 'info'
  }
}

async function backToList(): Promise<void> {
  await router.push({ name: 'my-retry-lists' })
}
</script>

<template>
  <div class="student-retry-result">
    <div v-if="loading">
      <el-skeleton :rows="6" animated />
    </div>

    <EmptyState
      v-else-if="!result"
      title="暂时无法获取重练结果"
      description="重练记录不存在，或你没有查看权限。"
    >
      <template #action>
        <el-button @click="backToList">
          返回重练清单
        </el-button>
      </template>
    </EmptyState>

    <template v-else>
      <BaseCard>
        <div class="result-header">
          <div>
            <div class="result-eyebrow">RETRY RESULT</div>
            <h2>重练结果</h2>
            <p>第 {{ result.attemptNo }} 次重练</p>
          </div>
          <el-tag
            :type="resultStatusType(result.status)"
            size="large"
          >
            {{ resultStatusLabel(result.status) }}
          </el-tag>
        </div>

        <div class="score-overview">
          <div class="score-main">
            <span>{{ needsSelfReview ? '当前已得分' : '最终得分' }}</span>
            <strong>
              {{ result.earnedScore }}
              <small>/ {{ result.totalScore }}</small>
            </strong>
          </div>

          <div class="score-stat">
            <span>客观题正确</span>
            <strong>{{ correctCount }}</strong>
          </div>
          <div class="score-stat">
            <span>客观题错误</span>
            <strong>{{ wrongCount }}</strong>
          </div>
          <div class="score-stat">
            <span>待自我核对</span>
            <strong>{{ pendingCount }}</strong>
          </div>
        </div>

        <div
          v-if="objectiveAnswers.length > 0"
          class="accuracy-section"
        >
          <div>
            <span>客观题正确率</span>
            <strong>{{ accuracy }}%</strong>
          </div>
          <el-progress :percentage="accuracy" :stroke-width="9" />
        </div>

        <p class="result-score-note">
          得分仅统计系统自动判分的客观题，简答题掌握情况单独记录。
        </p>

        <el-alert
          v-if="needsSelfReview"
          title="客观题已自动判分，简答题需要你对照参考答案自我核对。"
          type="warning"
          :closable="false"
          show-icon
          class="pending-alert"
        />
      </BaseCard>

      <BaseCard v-if="needsSelfReview" title="简答题自我核对">
        <div class="review-list">
          <article
            v-for="answer in pendingItems"
            :key="answer.retryItemId"
            class="review-item"
          >
            <div class="review-item__stem">{{ answer.stem }}</div>

            <div class="review-item__row">
              <span>我的答案</span>
              <p>{{ answer.studentAnswer || '未作答' }}</p>
            </div>
            <div class="review-item__row">
              <span>参考答案</span>
              <p>{{ answer.correctAnswer || '未设置参考答案' }}</p>
            </div>
            <div
              v-if="answer.explanation"
              class="review-item__row"
            >
              <span>答案解析</span>
              <p>{{ answer.explanation }}</p>
            </div>

            <el-radio-group
              v-model="review[String(answer.retryItemId)]"
              class="review-choice"
            >
              <el-radio :value="true">
                已经掌握
              </el-radio>
              <el-radio :value="false">
                仍需巩固
              </el-radio>
            </el-radio-group>
          </article>
        </div>

        <div class="review-actions">
          <el-button
            type="primary"
            :loading="submitting"
            @click="submitSelfReview"
          >
            完成自我核对
          </el-button>
        </div>
      </BaseCard>

      <BaseCard title="逐题分析">
        <div
          v-for="(answer, index) in result.answers"
          :key="answer.retryItemId"
          class="result-question"
        >
          <div class="result-question__header">
            <strong>第 {{ index + 1 }} 题</strong>
            <el-tag
              v-if="answer.pendingSelfReview"
              size="small"
              type="warning"
            >
              待自我核对
            </el-tag>
            <el-tag
              v-else-if="answer.questionType === 'SHORT_ANSWER'"
              size="small"
              :type="answer.selfReviewUnderstood ? 'success' : 'warning'"
            >
              {{ answer.selfReviewUnderstood ? '已经掌握' : '仍需巩固' }}
            </el-tag>
            <el-tag
              v-else-if="answer.correct"
              size="small"
              type="success"
            >
              正确
            </el-tag>
            <el-tag
              v-else
              size="small"
              type="danger"
            >
              错误
            </el-tag>
          </div>

          <div class="result-question__stem">{{ answer.stem }}</div>

          <div class="result-detail-row">
            <span>我的答案</span>
            <strong>{{ answer.studentAnswer || '未作答' }}</strong>
          </div>

          <template v-if="answer.pendingSelfReview">
            <div class="result-detail-row">
              <span>参考答案</span>
              <strong class="correct-answer">
                {{ answer.correctAnswer || '未设置' }}
              </strong>
            </div>
            <div
              v-if="answer.explanation"
              class="answer-explanation"
            >
              <strong>答案解析</strong>
              <p>{{ answer.explanation }}</p>
            </div>
          </template>

          <template v-else-if="answer.questionType === 'SHORT_ANSWER'">
            <div class="result-detail-row">
              <span>参考答案</span>
              <strong class="correct-answer">
                {{ answer.correctAnswer || '未设置' }}
              </strong>
            </div>
            <div
              v-if="answer.explanation"
              class="answer-explanation"
            >
              <strong>答案解析</strong>
              <p>{{ answer.explanation }}</p>
            </div>
            <div class="self-review-result">
              自我核对结果：
              <el-tag
                :type="answer.selfReviewUnderstood ? 'success' : 'warning'"
                size="small"
              >
                {{ answer.selfReviewUnderstood ? '已经掌握' : '仍需巩固' }}
              </el-tag>
            </div>
          </template>

          <template v-else>
            <div class="result-detail-row">
              <span>正确答案</span>
              <strong class="correct-answer">
                {{ answer.correctAnswer || '未设置' }}
              </strong>
            </div>
            <div
              v-if="answer.explanation"
              class="answer-explanation"
            >
              <strong>答案解析</strong>
              <p>{{ answer.explanation }}</p>
            </div>
          </template>

          <div class="result-question__score">
            本题得分：
            <strong>{{ answer.earnedScore }}</strong>
            / {{ answer.score }}
          </div>
        </div>
      </BaseCard>

      <BaseCard>
        <div class="result-actions">
          <el-button @click="backToList">
            返回重练清单
          </el-button>
        </div>
      </BaseCard>
    </template>
  </div>
</template>

<style scoped>
.student-retry-result {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.result-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 14px;
}

.result-eyebrow {
  color: var(--sc-primary);
  font-size: 10px;
  font-weight: 700;
  letter-spacing: 2px;
}

.result-header h2 {
  margin: 8px 0;
  color: var(--sc-text);
  font-size: 21px;
}

.result-header p {
  margin: 0;
  color: var(--sc-text-secondary);
  font-size: 12px;
}

.score-overview {
  display: grid;
  grid-template-columns: 1.5fr repeat(3, 1fr);
  gap: 12px;
  margin-top: 25px;
  padding: 18px;
  border-radius: 12px;
  background: var(--sc-bg);
}

.score-main,
.score-stat {
  display: flex;
  flex-direction: column;
  gap: 9px;
}

.score-main span,
.score-stat span {
  color: var(--sc-text-secondary);
  font-size: 12px;
}

.score-main strong {
  color: var(--sc-primary);
  font-size: 29px;
}

.score-main small {
  color: var(--sc-text-secondary);
  font-size: 14px;
}

.score-stat strong {
  color: var(--sc-text);
  font-size: 22px;
}

.accuracy-section {
  margin-top: 18px;
}

.accuracy-section > div {
  display: flex;
  justify-content: space-between;
  margin-bottom: 9px;
  font-size: 12px;
}

.result-score-note {
  margin: 12px 0 0;
  color: var(--sc-text-secondary);
  font-size: 11px;
  line-height: 1.7;
}

.pending-alert {
  margin-top: 18px;
}

.review-list {
  display: flex;
  flex-direction: column;
}

.review-item {
  padding: 18px 0;
  border-bottom: 1px solid var(--sc-border);
}

.review-item:last-child {
  border-bottom: 0;
}

.review-item__stem {
  margin-bottom: 14px;
  color: var(--sc-text);
  font-size: 14px;
  font-weight: 500;
  line-height: 1.8;
  white-space: pre-wrap;
}

.review-item__row {
  margin: 10px 0;
  font-size: 12px;
}

.review-item__row span {
  color: var(--sc-text-secondary);
}

.review-item__row p {
  margin: 7px 0 0;
  padding: 11px 13px;
  border-radius: 8px;
  background: var(--sc-bg);
  color: var(--sc-text);
  font-size: 13px;
  line-height: 1.7;
  white-space: pre-wrap;
}

.review-item__check {
  margin-top: 12px;
}

.review-actions {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
  padding-top: 16px;
}

.result-question {
  padding: 19px 0;
  border-bottom: 1px solid var(--sc-border);
}

.result-question:last-child {
  border-bottom: 0;
}

.result-question__header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
}

.result-question__stem {
  margin: 16px 0;
  color: var(--sc-text);
  font-size: 14px;
  line-height: 1.8;
  white-space: pre-wrap;
}

.result-detail-row {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  margin: 12px 0;
  font-size: 12px;
}

.result-detail-row span {
  color: var(--sc-text-secondary);
}

.correct-answer {
  color: var(--sc-success, #16a34a);
}

.answer-explanation {
  padding: 13px;
  margin-top: 13px;
  border-radius: 9px;
  background: var(--sc-bg);
}

.answer-explanation strong {
  color: var(--sc-text);
  font-size: 12px;
}

.answer-explanation p {
  margin: 8px 0 0;
  color: var(--sc-text-secondary);
  font-size: 12px;
  line-height: 1.8;
  white-space: pre-wrap;
}

.self-review-result {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 13px;
  font-size: 12px;
  color: var(--sc-text-secondary);
}

.result-question__score {
  margin-top: 14px;
  color: var(--sc-text-secondary);
  font-size: 12px;
  text-align: right;
}

.result-question__score strong {
  color: var(--sc-text);
}

.result-actions {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
}

@media (max-width: 640px) {
  .score-overview {
    grid-template-columns: repeat(2, 1fr);
  }

  .score-main {
    grid-column: span 2;
  }

  .review-actions,
  .result-actions {
    flex-direction: column-reverse;
  }

  .review-actions .el-button,
  .result-actions .el-button {
    width: 100%;
  }
}
</style>
