<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'

import type { Course } from '@/api/course'
import { listJoinedCourses } from '@/api/course'
import type { BigIntId } from '@/api/types'
import {
  createStudentMistakeRetryList,
  getStudentMistakes,
} from '@/api/exercises'
import type {
  StudentMistakeRetryList,
  StudentMistakeSummary,
} from '@/api/exercises'
import StudentMistakeExplain from '@/components/exercise/StudentMistakeExplain.vue'
import BaseCard from '@/components/ui/BaseCard.vue'
import EmptyState from '@/components/ui/EmptyState.vue'
import {
  notifyError,
  notifySuccess,
  notifyWarning,
} from '@/utils/notify'

defineOptions({ name: 'StudentMistakesView' })

const router = useRouter()

const courses = ref<Course[]>([])
const selectedCourseId = ref<BigIntId>('')
const summary = ref<StudentMistakeSummary>({
  totalMistakes: 0,
  knowledgePointCount: 0,
  knowledgePoints: [],
})
const selectedAnswerIds = ref<string[]>([])
const expandedGroups = ref<string[]>([])
const loading = ref(false)
const loadFailed = ref(false)
const submitting = ref(false)
const retryDialogVisible = ref(false)
const retryTitle = ref('我的错题重练')
const createdRetryList = ref<StudentMistakeRetryList | null>(null)

const selectedMistakes = computed(() => {
  const selected = new Set(selectedAnswerIds.value)

  return summary.value.knowledgePoints.flatMap(group =>
    group.mistakes.filter(item => selected.has(String(item.answerId))),
  )
})

const mistakeCount = computed(() => summary.value.totalMistakes)

const unansweredCount = computed(() =>
  summary.value.knowledgePoints
    .flatMap(group => group.mistakes)
    .filter(item => item.mistakeType === 'UNANSWERED').length,
)

const objectiveWrongCount = computed(() =>
  summary.value.knowledgePoints
    .flatMap(group => group.mistakes)
    .filter(item => item.mistakeType === 'OBJECTIVE_WRONG').length,
)

const shortAnswerWrongCount = computed(() =>
  summary.value.knowledgePoints
    .flatMap(group => group.mistakes)
    .filter(item => item.mistakeType === 'SHORT_ANSWER_INCOMPLETE').length,
)

onMounted(async () => {
  await loadCourses()
})

watch(selectedCourseId, () => {
  void loadMistakes()
})

async function loadCourses(): Promise<void> {
  try {
    courses.value = await listJoinedCourses()
  } catch (error) {
    notifyError(error)
  }

  await loadMistakes()
}

async function loadMistakes(): Promise<void> {
  loading.value = true
  loadFailed.value = false

  try {
    summary.value = await getStudentMistakes(
      selectedCourseId.value || undefined,
    )

    const availableIds = new Set(
      summary.value.knowledgePoints.flatMap(
        group => group.mistakes.map(item => String(item.answerId)),
      ),
    )

    selectedAnswerIds.value = selectedAnswerIds.value.filter(
      id => availableIds.has(id),
    )

    expandedGroups.value = summary.value.knowledgePoints.map(
      group => group.knowledgePointId
        ? String(group.knowledgePointId)
        : 'unassigned',
    )
  } catch (error) {
    loadFailed.value = true
    notifyError(error)
  } finally {
    loading.value = false
  }
}

function toggleMistake(answerId: BigIntId, checked: boolean): void {
  const id = String(answerId)

  if (checked) {
    if (!selectedAnswerIds.value.includes(id)) {
      selectedAnswerIds.value.push(id)
    }
  } else {
    selectedAnswerIds.value = selectedAnswerIds.value.filter(
      item => item !== id,
    )
  }
}

function mistakeTypeLabel(type: string): string {
  const labels: Record<string, string> = {
    UNANSWERED: '未作答',
    OBJECTIVE_WRONG: '客观题错误',
    SHORT_ANSWER_INCOMPLETE: '简答题未完全掌握',
  }

  return labels[type] ?? '答题错误'
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

function formatTime(value: string): string {
  if (!value) return '-'

  const date = new Date(value.replace(' ', 'T'))
  if (Number.isNaN(date.getTime())) return value

  return date.toLocaleString('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
  })
}

function openRetryDialog(): void {
  if (selectedMistakes.value.length === 0) {
    notifyWarning('请先选择需要重练的错题')
    return
  }

  retryTitle.value = '我的错题重练'
  createdRetryList.value = null
  retryDialogVisible.value = true
}

async function createRetryList(): Promise<void> {
  if (submitting.value) return

  const answerIds = selectedMistakes.value.map(item => item.answerId)

  if (answerIds.length === 0) {
    notifyWarning('请先选择错题')
    return
  }

  submitting.value = true

  try {
    createdRetryList.value = await createStudentMistakeRetryList(
      answerIds,
      retryTitle.value.trim() || '我的错题重练',
    )

    notifySuccess('重练清单生成成功')
    selectedAnswerIds.value = []
  } catch (error) {
    notifyError(error)
  } finally {
    submitting.value = false
  }
}

function closeRetryDialog(): void {
  if (submitting.value) return

  retryDialogVisible.value = false
  createdRetryList.value = null
}

function goToRetryLists(): void {
  retryDialogVisible.value = false

  void router.push({
    name: 'my-retry-lists',
  })
}
</script>

<template>
  <div class="student-mistakes-page">
    <BaseCard>
      <div class="mistake-hero">
        <div>
          <div class="mistake-eyebrow">MISTAKE REVIEW</div>
          <h2>错题回顾</h2>
          <p>
            按照课程知识点整理练习中的错误，
            通过错题回顾巩固薄弱知识。
          </p>
        </div>
        <div class="mistake-hero__count">
          <strong>{{ mistakeCount }}</strong>
          <span>累计错题</span>
        </div>
      </div>
    </BaseCard>

    <div class="mistake-stats">
      <BaseCard>
        <div class="mistake-stat">
          <span>错题总数</span>
          <strong>{{ mistakeCount }}</strong>
        </div>
      </BaseCard>
      <BaseCard>
        <div class="mistake-stat">
          <span>客观题错误</span>
          <strong>{{ objectiveWrongCount }}</strong>
        </div>
      </BaseCard>
      <BaseCard>
        <div class="mistake-stat">
          <span>简答题未完全掌握</span>
          <strong>{{ shortAnswerWrongCount }}</strong>
        </div>
      </BaseCard>
      <BaseCard>
        <div class="mistake-stat">
          <span>未作答</span>
          <strong>{{ unansweredCount }}</strong>
        </div>
      </BaseCard>
    </div>

    <BaseCard>
      <div class="mistake-toolbar">
        <div>
          <h3>我的错题</h3>
          <p>共 {{ summary.knowledgePointCount }} 个知识点涉及错误。</p>
        </div>
        <el-select
          v-model="selectedCourseId"
          clearable
          placeholder="全部课程"
          style="width: 220px"
        >
          <el-option
            v-for="course in courses"
            :key="course.id"
            :label="course.name"
            :value="course.id"
          />
        </el-select>
      </div>

      <el-skeleton
        v-if="loading"
        :rows="5"
        animated
      />
      <EmptyState
        v-else-if="loadFailed"
        title="错题加载失败"
        description="请检查网络连接后重试。"
      >
        <template #action>
          <el-button type="primary" plain @click="loadMistakes">
            重新加载
          </el-button>
        </template>
      </EmptyState>
      <EmptyState
        v-else-if="mistakeCount === 0"
        title="暂时没有错题"
        description="完成课程练习后，系统会自动归集判分错误的题目。"
      />
      <template v-else>
        <el-collapse v-model="expandedGroups" class="mistake-groups">
          <el-collapse-item
            v-for="group in summary.knowledgePoints"
            :key="group.knowledgePointId ?? 'unassigned'"
            :name="group.knowledgePointId
              ? String(group.knowledgePointId)
              : 'unassigned'"
          >
            <template #title>
              <div class="knowledge-group-title">
                <span class="knowledge-group-name">
                  {{ group.knowledgePointTitle }}
                </span>
                <el-tag type="warning" size="small">
                  {{ group.mistakeCount }} 道错题
                </el-tag>
              </div>
            </template>

            <div class="mistake-list">
              <article
                v-for="item in group.mistakes"
                :key="item.answerId"
                class="mistake-item"
              >
                <div class="mistake-item__heading">
                  <el-checkbox
                    :model-value="selectedAnswerIds.includes(String(item.answerId))"
                    @update:model-value="
                      toggleMistake(item.answerId, Boolean($event))
                    "
                  >
                    加入重练清单
                  </el-checkbox>
                  <div class="mistake-tags">
                    <el-tag size="small">
                      {{ questionTypeLabel(item.questionType) }}
                    </el-tag>
                    <el-tag size="small" type="danger">
                      {{ mistakeTypeLabel(item.mistakeType) }}
                    </el-tag>
                  </div>
                </div>

                <div class="mistake-item__source">
                  {{ item.courseName }} · {{ item.exerciseTitle }}
                </div>
                <div class="mistake-question">{{ item.stem }}</div>

                <div class="mistake-answer mistake-answer--wrong">
                  <span>我的答案</span>
                  <p>{{ item.studentAnswer || '未作答' }}</p>
                </div>
                <div class="mistake-answer mistake-answer--correct">
                  <span>参考答案</span>
                  <p>{{ item.correctAnswer || '未设置参考答案' }}</p>
                </div>

                <div
                  v-if="item.explanation"
                  class="mistake-explanation"
                >
                  <strong>答案解析</strong>
                  <p>{{ item.explanation }}</p>
                </div>
                <div
                  v-if="item.teacherFeedback"
                  class="mistake-feedback"
                >
                  <strong>教师评语</strong>
                  <p>{{ item.teacherFeedback }}</p>
                </div>

                <!-- F5-04 AI知识点讲解 -->
                <div class="mistake-ai-action">
                  <StudentMistakeExplain
                    :mistake="item"
                    button-label="AI 知识点讲解"
                  />
                </div>

                <div class="mistake-item__footer">
                  <span>得分：{{ item.earnedScore }} / {{ item.maxScore }}</span>
                  <span>{{ formatTime(item.answeredAt) }}</span>
                </div>
              </article>
            </div>
          </el-collapse-item>
        </el-collapse>
      </template>

      <div v-if="mistakeCount > 0" class="mistake-footer">
        <span>已选择 {{ selectedAnswerIds.length }} 道错题</span>
        <el-button
          type="primary"
          :disabled="selectedAnswerIds.length === 0"
          @click="openRetryDialog"
        >
          生成重练清单
        </el-button>
      </div>
    </BaseCard>

    <el-dialog
      v-model="retryDialogVisible"
      title="生成错题重练清单"
      width="min(600px, 94vw)"
      :close-on-click-modal="false"
      @closed="closeRetryDialog"
    >
      <template v-if="!createdRetryList">
        <el-form label-position="top">
          <el-form-item label="清单名称">
            <el-input
              v-model="retryTitle"
              maxlength="100"
              show-word-limit
              placeholder="请输入重练清单名称"
            />
          </el-form-item>
        </el-form>

        <div class="retry-preview">
          <div class="retry-preview__header">
            已选择 {{ selectedMistakes.length }} 道错题
          </div>
          <div
            v-for="(item, index) in selectedMistakes"
            :key="item.answerId"
            class="retry-preview__item"
          >
            <strong>第 {{ index + 1 }} 题</strong>
            <p>{{ item.stem }}</p>
            <span>{{ item.knowledgePointTitle || '未关联知识点' }}</span>
          </div>
        </div>
      </template>

      <div v-else class="retry-created">
        <el-result
          icon="success"
          title="重练清单生成成功"
          :sub-title="`已保存 ${createdRetryList.itemCount} 道错题`"
        />
        <div class="retry-created__info">
          <span>清单名称</span>
          <strong>{{ createdRetryList.title }}</strong>
        </div>
        <div class="retry-created__info">
          <span>清单编号</span>
          <strong>{{ createdRetryList.id }}</strong>
        </div>
      </div>

      <template #footer>
        <el-button @click="closeRetryDialog">
          {{ createdRetryList ? '完成' : '取消' }}
        </el-button>
        <el-button
          v-if="createdRetryList"
          type="primary"
          @click="goToRetryLists"
        >
          前往错题重练
        </el-button>
        <el-button
          v-if="!createdRetryList"
          type="primary"
          :loading="submitting"
          @click="createRetryList"
        >
          确认生成
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.student-mistakes-page {
  display: flex;
  flex-direction: column;
  gap: 16px;
  min-width: 0;
}

.mistake-hero {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 18px;
}

.mistake-eyebrow {
  margin-bottom: 8px;
  color: var(--sc-primary);
  font-size: 10px;
  font-weight: 700;
  letter-spacing: 2px;
}

.mistake-hero h2 {
  margin: 0 0 8px;
  color: var(--sc-text);
  font-size: 22px;
}

.mistake-hero p {
  margin: 0;
  color: var(--sc-text-secondary);
  font-size: 13px;
  line-height: 1.7;
}

.mistake-hero__count {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  flex: none;
  width: 82px;
  height: 82px;
  border-radius: 18px;
  background: var(--sc-bg);
}

.mistake-hero__count strong {
  color: var(--sc-primary);
  font-size: 27px;
}

.mistake-hero__count span {
  color: var(--sc-text-secondary);
  font-size: 11px;
}

.mistake-stats {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
}

.mistake-stat {
  display: flex;
  flex-direction: column;
  gap: 9px;
}

.mistake-stat span {
  color: var(--sc-text-secondary);
  font-size: 12px;
}

.mistake-stat strong {
  color: var(--sc-text);
  font-size: 25px;
}

.mistake-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 12px;
  margin-bottom: 16px;
}

.mistake-toolbar h3 {
  margin: 0 0 6px;
  color: var(--sc-text);
  font-size: 15px;
}

.mistake-toolbar p {
  margin: 0;
  color: var(--sc-text-secondary);
  font-size: 12px;
}

.knowledge-group-title {
  display: flex;
  align-items: center;
  gap: 10px;
}

.knowledge-group-name {
  color: var(--sc-text);
  font-size: 13px;
  font-weight: 600;
}

.mistake-list {
  display: flex;
  flex-direction: column;
}

.mistake-item {
  padding: 17px 5px;
  border-bottom: 1px solid var(--sc-border);
}

.mistake-item:last-child {
  border-bottom: 0;
}

.mistake-item__heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 10px;
}

.mistake-tags {
  display: flex;
  gap: 7px;
}

.mistake-item__source {
  margin-top: 13px;
  color: var(--sc-text-placeholder);
  font-size: 11px;
}

.mistake-question {
  margin: 13px 0;
  color: var(--sc-text);
  font-size: 14px;
  font-weight: 500;
  line-height: 1.8;
  white-space: pre-wrap;
}

.mistake-answer {
  padding: 11px 13px;
  margin: 9px 0;
  border-radius: 8px;
  background: var(--sc-bg);
}

.mistake-answer span {
  color: var(--sc-text-secondary);
  font-size: 11px;
}

.mistake-answer p {
  margin: 7px 0 0;
  font-size: 13px;
  line-height: 1.7;
  white-space: pre-wrap;
}

.mistake-answer--wrong p {
  color: var(--sc-danger);
}

.mistake-answer--correct p {
  color: var(--sc-success, #16a34a);
}

.mistake-explanation,
.mistake-feedback {
  padding: 12px 14px;
  margin-top: 10px;
  border-radius: 8px;
  background: var(--sc-bg);
}

.mistake-explanation strong,
.mistake-feedback strong {
  color: var(--sc-text);
  font-size: 12px;
}

.mistake-explanation p,
.mistake-feedback p {
  margin: 7px 0 0;
  color: var(--sc-text-secondary);
  font-size: 12px;
  line-height: 1.8;
  white-space: pre-wrap;
}

.mistake-feedback {
  border-left: 3px solid var(--sc-primary);
}

.mistake-item__footer {
  display: flex;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 10px;
  margin-top: 13px;
  color: var(--sc-text-placeholder);
  font-size: 11px;
}

.mistake-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding-top: 15px;
  border-top: 1px solid var(--sc-border);
  color: var(--sc-text-secondary);
  font-size: 12px;
}

.retry-preview {
  max-height: 360px;
  overflow-y: auto;
}

.retry-preview__header {
  margin-bottom: 12px;
  color: var(--sc-text-secondary);
  font-size: 12px;
}

.retry-preview__item {
  padding: 12px;
  margin-bottom: 8px;
  border: 1px solid var(--sc-border);
  border-radius: 8px;
}

.retry-preview__item strong {
  color: var(--sc-text);
  font-size: 12px;
}

.retry-preview__item p {
  color: var(--sc-text);
  font-size: 13px;
  line-height: 1.7;
}

.retry-preview__item span {
  color: var(--sc-text-placeholder);
  font-size: 11px;
}

.retry-created__info {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  padding: 12px 0;
  border-top: 1px solid var(--sc-border);
  font-size: 12px;
}

.retry-created__info span {
  color: var(--sc-text-secondary);
}

.retry-created__info strong {
  color: var(--sc-text);
  overflow-wrap: anywhere;
}

@media (max-width: 800px) {
  .mistake-stats {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 600px) {
  .mistake-hero__count {
    width: 65px;
    height: 65px;
  }

  .mistake-toolbar {
    align-items: stretch;
    flex-direction: column;
  }

  .mistake-toolbar :deep(.el-select) {
    width: 100% !important;
  }

  .mistake-footer {
    align-items: stretch;
    flex-direction: column;
  }

  .mistake-footer .el-button {
    width: 100%;
  }
}

.mistake-ai-action {
  display: flex;
  align-items: center;
  margin-top: 12px;
}
</style>
