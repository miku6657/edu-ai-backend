<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'

import { listAccessibleCourses } from '@/api/course-access'
import type { Course } from '@/api/course'
import type { BigIntId } from '@/api/types'
import {
  getExerciseReview,
  gradeExerciseAttempt,
  listPendingExerciseReviews,
} from '@/api/exercises'
import type {
  ExerciseReviewAnswer,
  ExerciseReviewDetail,
  ExerciseReviewItem,
} from '@/api/exercises'
import BaseCard from '@/components/ui/BaseCard.vue'
import EmptyState from '@/components/ui/EmptyState.vue'
import { useAuthStore } from '@/stores/auth'
import {
  notifyError,
  notifySuccess,
  notifyWarning,
} from '@/utils/notify'

defineOptions({ name: 'ExerciseReviewsView' })

interface GradeFormItem {
  selected: boolean
  score: number
  feedback: string
}

const auth = useAuthStore()
const canReview = computed(() => auth.isTeacher || auth.isAdmin)

const courses = ref<Course[]>([])
const selectedCourseId = ref<BigIntId>('')
const reviews = ref<ExerciseReviewItem[]>([])
const loadingCourses = ref(false)
const loading = ref(false)
const detailLoading = ref(false)
const submitting = ref(false)
const dialogVisible = ref(false)
const currentDetail = ref<ExerciseReviewDetail | null>(null)
const gradeForm = reactive<Record<string, GradeFormItem>>({})

const pendingCount = computed(() =>
  reviews.value.reduce(
    (total, item) => total + item.pendingReviewCount,
    0,
  ),
)

const selectedGradeCount = computed(() =>
  currentDetail.value?.answers.filter((answer) => {
    if (!answer.pendingReview) return false
    return gradeForm[String(answer.answerId)]?.selected === true
  }).length ?? 0,
)

const completedAnswerCount = computed(() =>
  currentDetail.value?.answers.filter(answer => !answer.pendingReview).length ?? 0,
)

onMounted(async () => {
  if (!canReview.value) return
  await loadCourses()
})

watch(selectedCourseId, () => {
  void loadReviews()
})

async function loadCourses(): Promise<void> {
  loadingCourses.value = true

  try {
    courses.value = await listAccessibleCourses()
    await loadReviews()
  } catch (error) {
    notifyError(error)
  } finally {
    loadingCourses.value = false
  }
}

async function loadReviews(): Promise<void> {
  loading.value = true

  try {
    reviews.value = await listPendingExerciseReviews(
      selectedCourseId.value || undefined,
    )
  } catch (error) {
    notifyError(error)
  } finally {
    loading.value = false
  }
}

function questionTypeLabel(type: string): string {
  const labels: Record<string, string> = {
    SINGLE_CHOICE: '单选题',
    MULTIPLE_CHOICE: '多选题',
    TRUE_FALSE: '判断题',
    SHORT_ANSWER: '简答题',
  }

  return labels[type] ?? type
}

function resetGradeForm(answers: ExerciseReviewAnswer[]): void {
  Object.keys(gradeForm).forEach((key) => {
    delete gradeForm[key]
  })

  for (const answer of answers) {
    if (!answer.pendingReview) continue

    gradeForm[String(answer.answerId)] = {
      selected: true,
      score: 0,
      feedback: '',
    }
  }
}

async function openReview(item: ExerciseReviewItem): Promise<void> {
  detailLoading.value = true

  try {
    currentDetail.value = await getExerciseReview(item.attemptId)
    resetGradeForm(currentDetail.value.answers)
    dialogVisible.value = true
  } catch (error) {
    notifyError(error)
  } finally {
    detailLoading.value = false
  }
}

function toggleAnswer(answerId: BigIntId, selected: boolean): void {
  const form = gradeForm[String(answerId)]
  if (form) form.selected = selected
}

function validateGrades(answers: ExerciseReviewAnswer[]): boolean {
  for (const answer of answers) {
    const form = gradeForm[String(answer.answerId)]
    if (!form?.selected) continue

    if (
      !Number.isFinite(form.score)
      || form.score < 0
      || form.score > Number(answer.maxScore)
    ) {
      notifyWarning(
        `第 ${answer.questionId} 题的得分必须在 0 到 ${answer.maxScore} 之间`,
      )
      return false
    }
  }

  return true
}

async function submitGrades(): Promise<void> {
  if (!currentDetail.value || submitting.value) return

  const pendingAnswers = currentDetail.value.answers.filter(
    answer => answer.pendingReview,
  )
  const selectedAnswers = pendingAnswers.filter(
    answer => gradeForm[String(answer.answerId)]?.selected,
  )

  if (selectedAnswers.length === 0) {
    notifyWarning('请至少选择一道需要批改的题目')
    return
  }

  if (!validateGrades(pendingAnswers)) return

  submitting.value = true

  try {
    const result = await gradeExerciseAttempt(
      currentDetail.value.attemptId,
      selectedAnswers.map((answer) => {
        const form = gradeForm[String(answer.answerId)]

        return {
          answerId: answer.answerId,
          score: form.score,
          feedback: form.feedback.trim(),
        }
      }),
    )

    currentDetail.value = result
    resetGradeForm(result.answers)
    await loadReviews()

    if (result.status === 'GRADED') {
      notifySuccess('全部简答题批改完成')
    } else {
      notifySuccess('本批次批改完成，仍有题目待批改')
    }
  } catch (error) {
    notifyError(error)
  } finally {
    submitting.value = false
  }
}

function closeDialog(): void {
  if (submitting.value) return

  dialogVisible.value = false
  currentDetail.value = null
  Object.keys(gradeForm).forEach((key) => {
    delete gradeForm[key]
  })
}

function statusLabel(status: string): string {
  return status === 'GRADED' ? '已完成' : '待批改'
}
</script>

<template>
  <div v-if="!canReview">
    <EmptyState
      title="无权访问"
      description="练习批改仅对教师和管理员开放。"
    />
  </div>

  <div v-else class="exercise-review-page">
    <div class="review-stats">
      <BaseCard>
        <div class="review-stat">
          <span>待批改答题记录</span>
          <strong>{{ reviews.length }}</strong>
        </div>
      </BaseCard>
      <BaseCard>
        <div class="review-stat">
          <span>待批改题目</span>
          <strong>{{ pendingCount }}</strong>
        </div>
      </BaseCard>
    </div>

    <BaseCard
      title="简答题批改"
      subtitle="查看学生提交的简答题，完成评分与反馈"
    >
      <div class="review-toolbar">
        <el-select
          v-model="selectedCourseId"
          placeholder="全部课程"
          clearable
          :loading="loadingCourses"
          style="width: 240px"
        >
          <el-option
            v-for="course in courses"
            :key="course.id"
            :label="course.name"
            :value="course.id"
          />
        </el-select>
        <el-button :loading="loading" @click="loadReviews">
          刷新
        </el-button>
      </div>

      <el-skeleton
        v-if="loading && reviews.length === 0"
        :rows="4"
        animated
      />
      <EmptyState
        v-else-if="reviews.length === 0"
        title="暂无待批改内容"
        description="学生提交包含简答题的练习后，会显示在这里。"
      />
      <el-table
        v-else
        v-loading="loading"
        :data="reviews"
        row-key="attemptId"
        stripe
      >
        <el-table-column
          prop="exerciseTitle"
          label="练习名称"
          min-width="180"
          show-overflow-tooltip
        />
        <el-table-column
          prop="courseName"
          label="所属课程"
          min-width="140"
        />
        <el-table-column
          prop="studentName"
          label="学生"
          min-width="100"
        />
        <el-table-column
          prop="attemptNo"
          label="作答次数"
          width="90"
        >
          <template #default="{ row }">
            第 {{ row.attemptNo }} 次
          </template>
        </el-table-column>
        <el-table-column
          prop="pendingReviewCount"
          label="待批改题数"
          width="110"
        >
          <template #default="{ row }">
            <el-tag type="warning">{{ row.pendingReviewCount }} 道</el-tag>
          </template>
        </el-table-column>
        <el-table-column
          prop="earnedScore"
          label="当前得分"
          width="120"
        >
          <template #default="{ row }">
            {{ row.earnedScore }} / {{ row.totalScore }}
          </template>
        </el-table-column>
        <el-table-column
          prop="submittedAt"
          label="提交时间"
          min-width="165"
        />
        <el-table-column
          label="操作"
          width="110"
          fixed="right"
        >
          <template #default="{ row }">
            <el-button
              type="primary"
              link
              :loading="detailLoading"
              @click="openReview(row)"
            >
              查看并批改
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </BaseCard>

    <el-dialog
      v-model="dialogVisible"
      title="学生答题批改"
      width="min(900px, 95vw)"
      top="4vh"
      :close-on-click-modal="false"
      @closed="closeDialog"
    >
      <template v-if="currentDetail">
        <div class="review-detail-header">
          <div>
            <h3>{{ currentDetail.exerciseTitle }}</h3>
            <p>
              学生：{{ currentDetail.studentName }}
              · 第 {{ currentDetail.attemptNo }} 次作答
            </p>
          </div>
          <el-tag :type="currentDetail.status === 'GRADED' ? 'success' : 'warning'">
            {{ statusLabel(currentDetail.status) }}
          </el-tag>
        </div>

        <div class="review-score-summary">
          <div>
            <span>当前得分</span>
            <strong>{{ currentDetail.earnedScore }} / {{ currentDetail.totalScore }}</strong>
          </div>
          <div>
            <span>已批改题目</span>
            <strong>{{ completedAnswerCount }}</strong>
          </div>
          <div>
            <span>剩余待批改</span>
            <strong>
              {{ currentDetail.answers.filter(item => item.pendingReview).length }}
            </strong>
          </div>
        </div>

        <div
          v-for="(answer, index) in currentDetail.answers"
          :key="answer.answerId"
          class="review-answer"
        >
          <div class="review-answer__header">
            <strong>第 {{ index + 1 }} 题</strong>
            <div class="review-answer__tags">
              <el-tag size="small">{{ questionTypeLabel(answer.questionType) }}</el-tag>
              <el-tag
                :type="answer.pendingReview ? 'warning' : 'success'"
                size="small"
              >
                {{ answer.pendingReview ? '待批改' : '已批改' }}
              </el-tag>
            </div>
          </div>

          <div class="review-knowledge">
            知识点：{{ answer.knowledgePointTitle || '未关联' }}
          </div>
          <div class="review-question-stem">{{ answer.stem }}</div>

          <div class="review-answer-block">
            <span>学生答案</span>
            <p>{{ answer.studentAnswer || '学生未填写答案' }}</p>
          </div>
          <div class="review-answer-block">
            <span>参考答案</span>
            <p>{{ answer.correctAnswer || '教师未设置参考答案' }}</p>
          </div>
          <div
            v-if="answer.explanation"
            class="review-answer-block"
          >
            <span>参考解析</span>
            <p>{{ answer.explanation }}</p>
          </div>

          <template v-if="answer.pendingReview">
            <el-divider />
            <div class="grade-form">
              <el-checkbox
                :model-value="gradeForm[String(answer.answerId)]?.selected ?? false"
                @update:model-value="toggleAnswer(answer.answerId, Boolean($event))"
              >
                本次提交批改
              </el-checkbox>

              <div class="grade-field">
                <span>得分（满分 {{ answer.maxScore }}）</span>
                <el-input-number
                  v-if="gradeForm[String(answer.answerId)]"
                  v-model="gradeForm[String(answer.answerId)].score"
                  :min="0"
                  :max="Number(answer.maxScore)"
                  :precision="2"
                  :step="1"
                  :disabled="!gradeForm[String(answer.answerId)].selected"
                  controls-position="right"
                />
              </div>

              <div class="grade-field grade-field--feedback">
                <span>教师评语</span>
                <el-input
                  v-if="gradeForm[String(answer.answerId)]"
                  v-model="gradeForm[String(answer.answerId)].feedback"
                  type="textarea"
                  :rows="3"
                  maxlength="2000"
                  show-word-limit
                  :disabled="!gradeForm[String(answer.answerId)].selected"
                  placeholder="请填写答题评价、知识点问题及改进建议"
                />
              </div>
            </div>
          </template>

          <div v-else class="graded-result">
            <div>
              批改得分：
              <strong>{{ answer.earnedScore }} / {{ answer.maxScore }}</strong>
            </div>
            <div>教师评语：{{ answer.teacherFeedback || '未填写评语' }}</div>
          </div>
        </div>
      </template>

      <template #footer>
        <el-button @click="closeDialog">关闭</el-button>
        <el-button
          v-if="currentDetail?.status === 'PENDING_REVIEW'"
          type="primary"
          :loading="submitting"
          :disabled="selectedGradeCount === 0"
          @click="submitGrades"
        >
          提交本次批改（{{ selectedGradeCount }} 道）
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.exercise-review-page {
  display: flex;
  flex-direction: column;
  gap: 16px;
  min-width: 0;
}

.review-stats {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 14px;
}

.review-stat {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.review-stat span,
.review-score-summary span {
  color: var(--sc-text-secondary);
  font-size: 12px;
}

.review-stat strong {
  color: var(--sc-text);
  font-size: 26px;
}

.review-toolbar {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin-bottom: 16px;
}

.review-detail-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 12px;
}

.review-detail-header h3 {
  margin: 0;
  color: var(--sc-text);
  font-size: 16px;
}

.review-detail-header p {
  margin: 8px 0 0;
  color: var(--sc-text-secondary);
  font-size: 12px;
}

.review-score-summary {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 12px;
  padding: 16px;
  margin: 18px 0;
  border-radius: 10px;
  background: var(--sc-bg);
}

.review-score-summary > div {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.review-score-summary strong {
  color: var(--sc-text);
  font-size: 19px;
}

.review-answer {
  padding: 18px 0;
  border-top: 1px solid var(--sc-border);
}

.review-answer__header,
.review-answer__tags {
  display: flex;
  align-items: center;
  gap: 9px;
}

.review-answer__header {
  justify-content: space-between;
}

.review-answer__header > strong {
  color: var(--sc-text);
  font-size: 14px;
}

.review-knowledge {
  margin-top: 12px;
  color: var(--sc-primary);
  font-size: 12px;
}

.review-question-stem {
  margin: 14px 0;
  color: var(--sc-text);
  font-size: 14px;
  line-height: 1.8;
  white-space: pre-wrap;
}

.review-answer-block {
  padding: 11px 13px;
  margin: 9px 0;
  border-radius: 8px;
  background: var(--sc-bg);
}

.review-answer-block span {
  color: var(--sc-text-secondary);
  font-size: 11px;
}

.review-answer-block p {
  margin: 7px 0 0;
  color: var(--sc-text);
  font-size: 13px;
  line-height: 1.7;
  white-space: pre-wrap;
}

.grade-form {
  display: flex;
  flex-direction: column;
  gap: 15px;
}

.grade-field {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 15px;
}

.grade-field > span {
  flex: none;
  color: var(--sc-text-secondary);
  font-size: 12px;
}

.grade-field--feedback {
  align-items: flex-start;
}

.grade-field--feedback :deep(.el-input) {
  max-width: 620px;
}

.graded-result {
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding: 13px;
  border-radius: 8px;
  background: var(--sc-bg);
  color: var(--sc-text-secondary);
  font-size: 12px;
  line-height: 1.7;
}

.graded-result strong {
  color: var(--sc-text);
}

@media (max-width: 640px) {
  .review-stats {
    grid-template-columns: 1fr;
  }

  .review-toolbar :deep(.el-select) {
    width: 100% !important;
  }

  .review-score-summary {
    grid-template-columns: 1fr;
  }

  .grade-field {
    align-items: stretch;
    flex-direction: column;
  }

  .grade-field--feedback :deep(.el-input) {
    max-width: none;
  }
}
</style>
