<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import { getStudentExerciseResult } from '@/api/exercises'
import type { ExerciseSubmitResult } from '@/api/exercises'
import BaseCard from '@/components/ui/BaseCard.vue'
import EmptyState from '@/components/ui/EmptyState.vue'
import { notifyError } from '@/utils/notify'

defineOptions({ name: 'StudentExerciseResultView' })

const route = useRoute()
const router = useRouter()

const loading = ref(false)
const result = ref<ExerciseSubmitResult | null>(null)

const attemptId = computed(
  () => String(route.params.attemptId ?? ''),
)

const isPending = computed(
  () => result.value?.status === 'PENDING_REVIEW',
)

const correctCount = computed(
  () => result.value?.answers.filter(item => item.correct === true).length ?? 0,
)

const wrongCount = computed(
  () => result.value?.answers.filter(item => item.correct === false).length ?? 0,
)

const pendingCount = computed(
  () => result.value?.pendingReviewCount ?? 0,
)

const accuracy = computed(() => {
  const count = correctCount.value + wrongCount.value
  return count === 0 ? 0 : Math.round(correctCount.value / count * 100)
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
    result.value = await getStudentExerciseResult(attemptId.value)
  } catch (error) {
    notifyError(error)
  } finally {
    loading.value = false
  }
}

function answerStatus(correct: boolean | null, pending: boolean): string {
  if (pending) return '待教师批改'
  if (correct === true) return '回答正确'
  if (correct === false) return '回答错误'
  return '未判定'
}

function answerStatusType(
  correct: boolean | null,
  pending: boolean,
): 'success' | 'danger' | 'warning' | 'info' {
  if (pending) return 'warning'
  if (correct === true) return 'success'
  if (correct === false) return 'danger'
  return 'info'
}

async function backToList(): Promise<void> {
  await router.push({ name: 'my-exercises' })
}

async function retryExercise(): Promise<void> {
  if (!result.value) return

  await router.push({
    name: 'student-exercise-detail',
    params: {
      exerciseId: result.value.exerciseId,
    },
  })
}
</script>

<template>
  <div class="student-exercise-result">
    <div v-if="loading">
      <el-skeleton :rows="6" animated />
    </div>

    <EmptyState
      v-else-if="!result"
      title="暂时无法获取答题结果"
      description="答题记录不存在，或你没有查看权限。"
    >
      <template #action>
        <el-button @click="backToList">
          返回练习列表
        </el-button>
      </template>
    </EmptyState>

    <template v-else>
      <BaseCard>
        <div class="result-header">
          <div>
            <div class="result-eyebrow">PRACTICE RESULT</div>
            <h2>练习结果</h2>
            <p>第 {{ result.attemptNo }} 次作答</p>
          </div>
          <el-tag :type="isPending ? 'warning' : 'success'" size="large">
            {{ isPending ? '等待教师批改' : '已完成判分' }}
          </el-tag>
        </div>

        <div class="score-overview">
          <div class="score-main">
            <span>{{ isPending ? '当前已得分' : '最终得分' }}</span>
            <strong>
              {{ result.earnedScore }}
              <small>/ {{ result.totalScore }}</small>
            </strong>
          </div>

          <div class="score-stat">
            <span>答对题目</span>
            <strong>{{ correctCount }}</strong>
          </div>
          <div class="score-stat">
            <span>答错题目</span>
            <strong>{{ wrongCount }}</strong>
          </div>
          <div class="score-stat">
            <span>待批改</span>
            <strong>{{ pendingCount }}</strong>
          </div>
        </div>

        <div
          v-if="correctCount + wrongCount > 0"
          class="accuracy-section"
        >
          <div>
            <span>客观题正确率</span>
            <strong>{{ accuracy }}%</strong>
          </div>
          <el-progress :percentage="accuracy" :stroke-width="9" />
        </div>

        <el-alert
          v-if="isPending"
          title="部分简答题需要教师批改，当前得分不是最终成绩。"
          type="warning"
          :closable="false"
          show-icon
          class="pending-alert"
        />
      </BaseCard>

      <BaseCard title="答题详情">
        <div
          v-for="(answer, index) in result.answers"
          :key="answer.questionId"
          class="result-question"
        >
          <div class="result-question__header">
            <strong>第 {{ index + 1 }} 题</strong>
            <el-tag
              :type="answerStatusType(answer.correct, answer.pendingReview)"
              size="small"
            >
              {{ answerStatus(answer.correct, answer.pendingReview) }}
            </el-tag>
          </div>

          <div class="result-question__stem">{{ answer.stem }}</div>

          <div class="result-detail-row">
            <span>我的答案</span>
            <strong>{{ answer.studentAnswer || '未作答' }}</strong>
          </div>

          <template v-if="!answer.pendingReview">
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

          <div v-else class="waiting-review">
            本题已提交，等待任课教师批改。
          </div>

          <div
            v-if="answer.teacherFeedback && !answer.pendingReview"
            class="teacher-feedback"
          >
            <strong>教师评语</strong>
            <p>{{ answer.teacherFeedback }}</p>
          </div>

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
            返回练习列表
          </el-button>
          <el-button type="primary" @click="retryExercise">
            再练一次
          </el-button>
        </div>
      </BaseCard>
    </template>
  </div>
</template>

<style scoped>
.student-exercise-result {
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

.pending-alert {
  margin-top: 18px;
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

.waiting-review {
  padding: 12px;
  border-radius: 8px;
  background: var(--sc-bg);
  color: var(--sc-text-secondary);
  font-size: 12px;
}

.teacher-feedback {
  padding: 13px;
  margin-top: 12px;
  border-left: 3px solid var(--sc-primary);
  border-radius: 6px;
  background: var(--sc-bg);
}

.teacher-feedback strong {
  color: var(--sc-text);
  font-size: 12px;
}

.teacher-feedback p {
  margin: 8px 0 0;
  color: var(--sc-text-secondary);
  font-size: 12px;
  line-height: 1.8;
  white-space: pre-wrap;
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

  .result-actions {
    flex-direction: column-reverse;
  }

  .result-actions .el-button {
    width: 100%;
  }
}
</style>
