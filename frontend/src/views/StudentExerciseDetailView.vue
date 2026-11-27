<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessageBox } from 'element-plus'
import { onBeforeRouteLeave, useRoute, useRouter } from 'vue-router'
import { ChevronLeft } from '@lucide/vue'

import {
  getStudentExercise,
  startStudentExercise,
  submitStudentExercise,
} from '@/api/exercises'
import type {
  ExerciseAnswerRequest,
  ExerciseAttempt,
  ExerciseQuestionType,
  StudentExercise,
} from '@/api/exercises'
import type { BigIntId } from '@/api/types'
import BaseCard from '@/components/ui/BaseCard.vue'
import EmptyState from '@/components/ui/EmptyState.vue'
import {
  notifyError,
  notifySuccess,
  notifyWarning,
} from '@/utils/notify'

defineOptions({ name: 'StudentExerciseDetailView' })

const route = useRoute()
const router = useRouter()

const loading = ref(false)
const starting = ref(false)
const submitting = ref(false)
const exercise = ref<StudentExercise | null>(null)
const attempt = ref<ExerciseAttempt | null>(null)
const submitted = ref(false)
const answers = reactive<Record<string, string | string[]>>({})

const exerciseId = computed(
  () => String(route.params.exerciseId ?? ''),
)

const answering = computed(
  () => attempt.value?.status === 'IN_PROGRESS',
)

const answeredCount = computed(() => {
  if (!exercise.value) return 0

  return exercise.value.questions.filter((question) => {
    const answer = answers[String(question.id)]

    if (Array.isArray(answer)) return answer.length > 0

    return typeof answer === 'string' && answer.trim() !== ''
  }).length
})

const progress = computed(() => {
  const total = exercise.value?.questions.length ?? 0

  return total === 0
    ? 0
    : Math.round(answeredCount.value / total * 100)
})

onMounted(() => {
  void loadDetail()
})

async function loadDetail(): Promise<void> {
  if (!/^[1-9]\d*$/.test(exerciseId.value)) {
    notifyWarning('练习ID不正确')
    await backToList()
    return
  }

  loading.value = true

  try {
    exercise.value = await getStudentExercise(exerciseId.value)
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
  if (!exercise.value || starting.value) return

  starting.value = true

  try {
    attempt.value = await startStudentExercise(exercise.value.id)

    if (attempt.value.status !== 'IN_PROGRESS') {
      notifyWarning('当前答题记录已经提交，请查看答题结果')
      return
    }

    notifySuccess('已进入答题状态')
  } catch (error) {
    notifyError(error)
  } finally {
    starting.value = false
  }
}

function buildSubmitAnswers(): ExerciseAnswerRequest[] {
  if (!exercise.value) return []

  return exercise.value.questions.map((question) => {
    const value = answers[String(question.id)]

    return {
      questionId: question.id,
      answer: Array.isArray(value)
        ? [...value].sort().join(',')
        : value ?? '',
    }
  })
}

async function submitAnswers(): Promise<void> {
  if (!attempt.value || !exercise.value || submitting.value) {
    return
  }

  const total = exercise.value.questions.length
  const unanswered = total - answeredCount.value

  try {
    await ElMessageBox.confirm(
      unanswered > 0
        ? `还有 ${unanswered} 道题没有作答，确定提交吗？`
        : '确认提交本次练习吗？提交后不能修改答案。',
      '提交练习',
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

  submitting.value = true

  try {
    const result = await submitStudentExercise(
      attempt.value.id,
      buildSubmitAnswers(),
    )

    submitted.value = true
    notifySuccess('练习提交成功')

    await router.replace({
      name: 'student-exercise-result',
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
  await router.push({ name: 'my-exercises' })
}

onBeforeRouteLeave(async () => {
  if (!answering.value || submitted.value || answeredCount.value === 0) {
    return true
  }

  try {
    await ElMessageBox.confirm(
      '当前答案尚未提交，离开页面会丢失尚未提交的作答内容。',
      '离开答题页面',
      {
        confirmButtonText: '确认离开',
        cancelButtonText: '继续作答',
        type: 'warning',
      },
    )

    return true
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') {
      notifyError(error)
    }
    return false
  }
})
</script>

<template>
  <div class="student-exercise-detail">
    <el-button text class="back-button" @click="backToList">
      <ChevronLeft :size="17" />
      返回练习列表
    </el-button>

    <div v-if="loading">
      <el-skeleton :rows="6" animated />
    </div>

    <EmptyState
      v-else-if="!exercise"
      title="练习不存在"
      description="练习可能已经关闭或被删除。"
    >
      <template #action>
        <el-button @click="backToList">
          返回练习列表
        </el-button>
      </template>
    </EmptyState>

    <template v-else>
      <BaseCard>
        <div class="exercise-header">
          <div>
            <div class="exercise-course">{{ exercise.courseName }}</div>
            <h2>{{ exercise.title }}</h2>
            <p class="exercise-description">
              {{ exercise.description || '教师暂未填写练习说明' }}
            </p>
          </div>
          <el-tag :type="answering ? 'success' : 'info'">
            {{ answering ? '正在作答' : '练习预览' }}
          </el-tag>
        </div>

        <div class="exercise-meta">
          <span>{{ exercise.questions.length }} 道题目</span>
          <span>难度：{{ exercise.difficulty }}</span>
          <span>预计 {{ exercise.estimatedMinutes }} 分钟</span>
          <span>
            总分：
            {{ exercise.questions.reduce((sum, question) => sum + Number(question.score), 0) }}
          </span>
        </div>
      </BaseCard>

      <BaseCard v-if="answering">
        <div class="answer-progress">
          <div>
            <strong>答题进度</strong>
            <span>{{ answeredCount }}/{{ exercise.questions.length }}</span>
          </div>
          <el-progress :percentage="progress" :stroke-width="8" />
        </div>
      </BaseCard>

      <BaseCard title="练习题目">
        <div
          v-for="(question, index) in exercise.questions"
          :key="question.id"
          class="question-card"
        >
          <div class="question-heading">
            <div class="question-number">第 {{ index + 1 }} 题</div>
            <div class="question-tags">
              <el-tag size="small">
                {{ questionTypeLabel(question.questionType) }}
              </el-tag>
              <el-tag size="small" type="info">
                {{ question.score }} 分
              </el-tag>
            </div>
          </div>

          <div class="question-knowledge">
            知识点：{{ question.knowledgePointTitle }}
          </div>
          <div class="question-stem">{{ question.stem }}</div>

          <template v-if="question.questionType === 'SINGLE_CHOICE'">
            <el-radio-group
              v-if="answering"
              :model-value="getAnswer(question.id)"
              class="option-group"
              @update:model-value="setAnswer(question.id, $event)"
            >
              <el-radio
                v-for="option in question.options"
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
                v-for="option in question.options"
                :key="option.label"
                class="preview-option"
              >
                <strong>{{ option.label }}.</strong>
                {{ option.content }}
              </div>
            </div>
          </template>

          <template v-else-if="question.questionType === 'MULTIPLE_CHOICE'">
            <el-checkbox-group
              v-if="answering"
              :model-value="getMultipleAnswer(question.id)"
              class="option-group"
              @update:model-value="setMultipleAnswer(question.id, $event)"
            >
              <el-checkbox
                v-for="option in question.options"
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
                v-for="option in question.options"
                :key="option.label"
                class="preview-option"
              >
                <strong>{{ option.label }}.</strong>
                {{ option.content }}
              </div>
            </div>
          </template>

          <template v-else-if="question.questionType === 'TRUE_FALSE'">
            <el-radio-group
              v-if="answering"
              :model-value="getAnswer(question.id)"
              class="option-group"
              @update:model-value="setAnswer(question.id, $event)"
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
            v-else-if="question.questionType === 'SHORT_ANSWER' && answering"
            :model-value="getAnswer(question.id)"
            type="textarea"
            :rows="5"
            maxlength="10000"
            show-word-limit
            placeholder="请输入你的答案"
            @update:model-value="setAnswer(question.id, $event)"
          />

          <div
            v-else-if="question.questionType === 'SHORT_ANSWER'"
            class="short-answer-preview"
          >
            开始练习后可填写简答题答案
          </div>
        </div>
      </BaseCard>

      <BaseCard>
        <div class="exercise-actions">
          <div class="action-hint">
            <template v-if="!answering">
              请确认练习内容后开始作答。
            </template>
            <template v-else>
              已完成 {{ answeredCount }} 道，
              共 {{ exercise.questions.length }} 道题。
            </template>
          </div>

          <el-button
            v-if="!answering"
            type="primary"
            :loading="starting"
            @click="startAnswering"
          >
            开始答题
          </el-button>
          <el-button
            v-else
            type="primary"
            :loading="submitting"
            @click="submitAnswers"
          >
            提交练习
          </el-button>
        </div>
      </BaseCard>
    </template>
  </div>
</template>

<style scoped>
.student-exercise-detail {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.back-button {
  align-self: flex-start;
  color: var(--sc-text-secondary);
}

.exercise-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 16px;
}

.exercise-course {
  color: var(--sc-primary);
  font-size: 12px;
  margin-bottom: 9px;
}

.exercise-header h2 {
  margin: 0;
  color: var(--sc-text);
  font-size: 21px;
}

.exercise-description {
  margin: 10px 0 0;
  color: var(--sc-text-secondary);
  font-size: 13px;
  line-height: 1.7;
}

.exercise-meta {
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

.question-knowledge {
  margin-top: 12px;
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

.exercise-actions {
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
  .exercise-header {
    flex-direction: column;
  }

  .exercise-actions {
    align-items: stretch;
    flex-direction: column;
  }

  .exercise-actions .el-button {
    width: 100%;
  }
}
</style>
