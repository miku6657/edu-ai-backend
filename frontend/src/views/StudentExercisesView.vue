<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'

import {
  BookOpen,
  CalendarClock,
  ClipboardList,
  RefreshCw,
  Search,
} from '@lucide/vue'

import type { Course } from '@/api/course'
import { listJoinedCourses } from '@/api/course'
import type { BigIntId } from '@/api/types'
import {
  getStudentExerciseRecommendations,
  listStudentExercises,
} from '@/api/exercises'

import type {
  StudentExercise,
  StudentExerciseRecommendation,
} from '@/api/exercises'
import BaseCard from '@/components/ui/BaseCard.vue'
import EmptyState from '@/components/ui/EmptyState.vue'
import { notifyError } from '@/utils/notify'

defineOptions({ name: 'StudentExercisesView' })

const router = useRouter()
const courses = ref<Course[]>([])
const exercises = ref<StudentExercise[]>([])
// F5-02 个性化推荐
const recommendations = ref<StudentExerciseRecommendation[]>([])

const recommendationWeakCount = ref(0)

let loadSequence = 0
const selectedCourseId = ref<BigIntId>('')
const searchKeyword = ref('')
const loading = ref(false)
const initialized = ref(false)

const totalQuestions = computed(() =>
  exercises.value.reduce(
    (total, item) => total + item.questionCount,
    0,
  ),
)

const totalAttempts = computed(() =>
  exercises.value.reduce(
    (total, item) => total + item.attemptCount,
    0,
  ),
)

const filteredExercises = computed(() => {
  const keyword = searchKeyword.value.trim().toLowerCase()

  if (!keyword) {
    return exercises.value
  }

  return exercises.value.filter(item =>
    item.title.toLowerCase().includes(keyword)
    || (item.description ?? '').toLowerCase().includes(keyword)
    || item.courseName.toLowerCase().includes(keyword),
  )
})

onMounted(async () => {
  await loadCourses()
})

watch(selectedCourseId, () => {
  if (initialized.value) {
    void loadExercises()
  }
})

async function loadCourses(): Promise<void> {
  loading.value = true

  try {
    courses.value = await listJoinedCourses()
    initialized.value = true
    await loadExercises()
  } catch (error) {
    notifyError(error)
  } finally {
    loading.value = false
  }
}

async function loadExercises(): Promise<void> {
  const sequence = ++loadSequence

  const courseId = selectedCourseId.value || undefined

  loading.value = true

  try {
    // 同时加载普通练习和个性化推荐
    const [exerciseResult, recommendationResult] =
      await Promise.all([
        listStudentExercises(courseId),

        getStudentExerciseRecommendations(courseId).catch(
          error => {
            if (sequence === loadSequence) {
              console.error('加载个性化推荐失败：', error)
            }

            return null
          },
        ),
      ])

    // 防止切换课程后，旧请求覆盖新数据
    if (sequence !== loadSequence) {
      return
    }

    exercises.value = exerciseResult

    recommendations.value =
      recommendationResult?.recommendations ?? []

    recommendationWeakCount.value =
      recommendationResult?.weakKnowledgePointCount ?? 0

  } catch (error) {
    if (sequence === loadSequence) {
      notifyError(error)
    }
  } finally {
    if (sequence === loadSequence) {
      loading.value = false
    }
  }
}

function statusLabel(status: string | null): string {
  switch (status) {
    case 'IN_PROGRESS':
      return '进行中'
    case 'PENDING_REVIEW':
      return '待教师批改'
    case 'GRADED':
      return '已完成'
    default:
      return '未参加'
  }
}

function statusType(
  status: string | null,
): 'info' | 'success' | 'warning' | 'primary' {
  switch (status) {
    case 'IN_PROGRESS':
      return 'primary'
    case 'PENDING_REVIEW':
      return 'warning'
    case 'GRADED':
      return 'success'
    default:
      return 'info'
  }
}

function difficultyLabel(value: number): string {
  switch (value) {
    case 1:
      return '基础'
    case 2:
      return '中等'
    case 3:
      return '进阶'
    default:
      return '未设置'
  }
}

function openExercise(item: StudentExercise): void {
  void router.push({
    name: 'student-exercise-detail',
    params: {
      exerciseId: item.id,
    },
  })
}

function openRecommendedExercise(
  item: StudentExerciseRecommendation,
): void {
  void router.push({
    name: 'student-exercise-detail',
    params: {
      exerciseId: item.exerciseId,
    },
  })
}

function recommendationDifficultyLabel(
  difficulty: number,
): string {
  switch (difficulty) {
    case 1:
      return '基础巩固'
    case 2:
      return '强化练习'
    case 3:
      return '进阶提升'
    default:
      return '综合练习'
  }
}
</script>

<template>
  <div class="student-exercises-page">
    <BaseCard class="exercise-hero">
      <div class="exercise-hero__content">
        <div>
          <div class="exercise-eyebrow">PERSONAL PRACTICE</div>
          <h2>我的练习</h2>
          <p>
            围绕课程知识点开展针对性练习，
            检验学习成果，巩固薄弱知识。
          </p>
        </div>

        <div class="exercise-hero__icon">
          <ClipboardList :size="32" />
        </div>
      </div>
    </BaseCard>

    <div class="exercise-stats">
      <BaseCard>
        <div class="stat-card">
          <span>可参加练习</span>
          <strong>{{ exercises.length }}</strong>
          <small>当前已发布</small>
        </div>
      </BaseCard>

      <BaseCard>
        <div class="stat-card">
          <span>练习题目</span>
          <strong>{{ totalQuestions }}</strong>
          <small>全部练习题数量</small>
        </div>
      </BaseCard>

      <BaseCard>
        <div class="stat-card">
          <span>累计参与次数</span>
          <strong>{{ totalAttempts }}</strong>
          <small>个人练习记录</small>
        </div>
      </BaseCard>
    </div>

    <BaseCard>
      <template #header>
        <div class="exercise-list__header">
          <div>
            <h3>课程练习</h3>
            <p>查看教师发布的练习及个人参与情况。</p>
          </div>
        </div>
      </template>

      <div class="exercise-toolbar">
        <el-select
          v-model="selectedCourseId"
          placeholder="全部课程"
          style="width: 240px"
        >
          <el-option label="全部课程" value="" />
          <el-option
            v-for="course in courses"
            :key="course.id"
            :label="course.name"
            :value="course.id"
          />
        </el-select>

        <el-input
          v-model="searchKeyword"
          clearable
          placeholder="搜索练习名称或课程"
          style="width: 260px"
        >
          <template #prefix>
            <Search :size="15" />
          </template>
        </el-input>

        <el-button :loading="loading" @click="loadExercises">
          <RefreshCw :size="16" />
          刷新
        </el-button>
      </div>

      <!-- F5-02 个性化练习推荐 -->
      <section
        v-if="recommendations.length > 0"
        class="recommendation-section"
      >
        <div class="recommendation-header">
          <div>
            <div class="recommendation-eyebrow">
              PERSONALIZED FOR YOU
            </div>

            <h3>为你推荐</h3>

            <p>
              根据最近答题情况，匹配需要巩固的知识点。
            </p>
          </div>

          <div class="recommendation-summary">
            <strong>{{ recommendations.length }}</strong>
            <span>份推荐练习</span>
          </div>
        </div>

        <div
          v-if="recommendationWeakCount > 0"
          class="recommendation-tip"
        >
          当前发现
          <strong>{{ recommendationWeakCount }}</strong>
          个尚未充分掌握或尚无练习记录的知识点，
          建议优先完成以下练习。
        </div>

        <div class="recommendation-list">
          <article
            v-for="(item, index) in recommendations"
            :key="item.exerciseId"
            class="recommendation-item"
          >
            <div class="recommendation-item__top">
              <div class="recommendation-item__title">
                <span class="recommendation-rank">
                  {{ String(index + 1).padStart(2, '0') }}
                </span>

                <div>
                  <h4>{{ item.title }}</h4>

                  <span class="recommendation-course">
                    {{ item.courseName }}
                  </span>
                </div>
              </div>

              <el-tag
                :type="
                  item.difficulty === 1
                    ? 'success'
                    : item.difficulty === 2
                      ? 'warning'
                      : 'danger'
                "
                size="small"
              >
                {{ recommendationDifficultyLabel(item.difficulty) }}
              </el-tag>
            </div>

            <p
              v-if="item.description"
              class="recommendation-description"
            >
              {{ item.description }}
            </p>

            <div class="recommendation-reason">
              <strong>推荐理由</strong>
              <p>{{ item.recommendationReason }}</p>
            </div>

            <div class="recommendation-points">
              <span class="recommendation-points__label">
                覆盖知识点
              </span>

              <div class="recommendation-point-tags">
                <el-tag
                  v-for="point in item.matchedKnowledgePoints"
                  :key="point.knowledgePointId"
                  size="small"
                  effect="plain"
                >
                  {{ point.title }}

                  <template v-if="point.masteryRate !== null">
                    · {{ point.masteryRate }}%
                  </template>

                  <template v-else>
                    · 暂无记录
                  </template>
                </el-tag>
              </div>
            </div>

            <div class="recommendation-item__footer">
              <div class="recommendation-meta">
                <span>{{ item.questionCount }} 道题</span>
                <span>预计 {{ item.estimatedMinutes }} 分钟</span>
                <span>{{ item.recommendationLevel }}</span>
              </div>

              <el-button
                type="primary"
                size="small"
                @click="openRecommendedExercise(item)"
              >
                开始练习
              </el-button>
            </div>
          </article>
        </div>
      </section>

      <el-skeleton
        v-if="loading && exercises.length === 0"
        :rows="4"
        animated
      />
      <EmptyState
        v-else-if="courses.length === 0"
        title="暂未加入课程"
        description="加入课程后，可以查看教师发布的练习。"
      />
      <EmptyState
        v-else-if="exercises.length === 0"
        title="暂无可参加的练习"
        description="当前课程还没有发布练习，请等待教师发布。"
      />
      <EmptyState
        v-else-if="filteredExercises.length === 0"
        title="没有找到相关练习"
        description="请更换搜索关键词后重试。"
      />
      <div v-else class="exercise-list">
        <article
          v-for="item in filteredExercises"
          :key="item.id"
          class="exercise-item"
        >
          <div class="exercise-item__main">
            <div class="exercise-item__title">
              <h4>{{ item.title }}</h4>
              <el-tag :type="statusType(item.latestStatus)" size="small">
                {{ statusLabel(item.latestStatus) }}
              </el-tag>
            </div>

            <p
              v-if="item.description"
              class="exercise-item__description"
            >
              {{ item.description }}
            </p>

            <div class="exercise-item__course">
              <BookOpen :size="15" />
              {{ item.courseName }}
            </div>

            <div class="exercise-item__meta">
              <span>
                <ClipboardList :size="14" />
                {{ item.questionCount }} 道题
              </span>
              <span>
                <CalendarClock :size="14" />
                预计 {{ item.estimatedMinutes }} 分钟
              </span>
              <span>
                已参加 {{ item.attemptCount }} 次
              </span>
              <span>{{ difficultyLabel(item.difficulty) }}难度</span>
            </div>
          </div>

          <div class="exercise-item__action">
            <el-button
              type="primary"
              plain
              @click="openExercise(item)"
            >
              查看练习
            </el-button>
          </div>
        </article>
      </div>

      <div
        v-if="filteredExercises.length > 0"
        class="exercise-list__footer"
      >
        共 {{ filteredExercises.length }} 份练习
      </div>
    </BaseCard>
  </div>
</template>

<style scoped>
.student-exercises-page {
  display: flex;
  flex-direction: column;
  gap: 16px;
  min-width: 0;
}

.exercise-hero__content {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 20px;
}

.exercise-eyebrow {
  margin-bottom: 8px;
  color: var(--sc-primary);
  font-size: 10px;
  font-weight: 700;
  letter-spacing: 2px;
}

.exercise-hero h2 {
  margin: 0 0 8px;
  color: var(--sc-text);
  font-size: 22px;
  font-weight: 650;
}

.exercise-hero p {
  margin: 0;
  color: var(--sc-text-secondary);
  font-size: 13px;
  line-height: 1.7;
}

.exercise-hero__icon {
  display: grid;
  flex: none;
  place-items: center;
  width: 64px;
  height: 64px;
  border-radius: 18px;
  color: var(--sc-primary);
  background: var(--sc-primary-soft);
}

.exercise-stats {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 14px;
}

.stat-card {
  display: flex;
  flex-direction: column;
  gap: 7px;
}

.stat-card span {
  color: var(--sc-text-secondary);
  font-size: 12px;
}

.stat-card strong {
  color: var(--sc-text);
  font-size: 27px;
  font-weight: 650;
  line-height: 1.2;
}

.stat-card small {
  color: var(--sc-text-placeholder);
  font-size: 11px;
}

.exercise-list__header h3 {
  margin: 0 0 5px;
  color: var(--sc-text);
  font-size: 15px;
  font-weight: 650;
}

.exercise-list__header p {
  margin: 0;
  color: var(--sc-text-secondary);
  font-size: 12px;
}

.exercise-toolbar {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin-bottom: 16px;
}

.exercise-list {
  display: flex;
  flex-direction: column;
}

.exercise-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 18px 4px;
  border-top: 1px solid var(--sc-border);
}

.exercise-item__main {
  flex: 1;
  min-width: 0;
}

.exercise-item__action {
  flex: none;
}

.exercise-item__title {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 10px;
}

.exercise-item__title h4 {
  margin: 0;
  color: var(--sc-text);
  font-size: 14px;
  font-weight: 600;
}

.exercise-item__description {
  margin: 9px 0;
  color: var(--sc-text-secondary);
  font-size: 12px;
  line-height: 1.6;
}

.exercise-item__course {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-top: 10px;
  color: var(--sc-primary);
  font-size: 12px;
}

.exercise-item__meta {
  display: flex;
  flex-wrap: wrap;
  gap: 18px;
  margin-top: 12px;
  color: var(--sc-text-placeholder);
  font-size: 11px;
}

.exercise-item__meta span {
  display: inline-flex;
  align-items: center;
  gap: 5px;
}

.exercise-list__footer {
  padding-top: 14px;
  border-top: 1px solid var(--sc-border);
  color: var(--sc-text-placeholder);
  font-size: 11px;
  text-align: right;
}

@media (max-width: 700px) {
  .exercise-stats {
    grid-template-columns: 1fr;
  }

  .exercise-toolbar {
    flex-direction: column;
  }

  .exercise-toolbar :deep(.el-select),
  .exercise-toolbar :deep(.el-input) {
    width: 100% !important;
  }

  .exercise-hero__icon {
    display: none;
  }

  .exercise-item {
    align-items: flex-start;
  }

  .exercise-item__action {
    width: 100%;
  }

  .exercise-item__action .el-button {
    width: 100%;
  }

  .exercise-item__meta {
    gap: 10px;
  }
}

.recommendation-section {
  margin-bottom: 22px;
  padding: 17px;
  border: 1px solid var(--sc-border);
  border-radius: 12px;
  background: var(--sc-surface);
}

.recommendation-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 15px;
}

.recommendation-eyebrow {
  margin-bottom: 6px;
  color: var(--sc-primary);
  font-size: 9px;
  font-weight: 700;
  letter-spacing: 1.6px;
}

.recommendation-header h3 {
  margin: 0 0 6px;
  color: var(--sc-text);
  font-size: 16px;
}

.recommendation-header p {
  margin: 0;
  color: var(--sc-text-secondary);
  font-size: 12px;
  line-height: 1.6;
}

.recommendation-summary {
  display: flex;
  flex-direction: column;
  align-items: center;
  flex: none;
}

.recommendation-summary strong {
  color: var(--sc-primary);
  font-size: 24px;
}

.recommendation-summary span {
  color: var(--sc-text-secondary);
  font-size: 10px;
}

.recommendation-tip {
  margin-top: 14px;
  padding: 10px 12px;
  border-radius: 8px;
  color: var(--sc-text-secondary);
  background: var(--sc-surface-muted);
  font-size: 11px;
  line-height: 1.7;
}

.recommendation-tip strong {
  color: var(--sc-primary);
}

.recommendation-list {
  display: flex;
  flex-direction: column;
  gap: 11px;
  margin-top: 14px;
}

.recommendation-item {
  padding: 14px;
  border: 1px solid var(--sc-border);
  border-radius: 10px;
  transition: border-color 0.2s ease;
}

.recommendation-item:hover {
  border-color: var(--sc-primary);
}

.recommendation-item__top {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
}

.recommendation-item__title {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  min-width: 0;
}

.recommendation-rank {
  display: grid;
  flex: none;
  place-items: center;
  width: 29px;
  height: 29px;
  border-radius: 8px;
  color: var(--sc-primary);
  background: var(--sc-primary-soft);
  font-size: 11px;
  font-weight: 700;
}

.recommendation-item__title h4 {
  margin: 1px 0 5px;
  color: var(--sc-text);
  font-size: 13px;
  font-weight: 650;
}

.recommendation-course {
  color: var(--sc-text-placeholder);
  font-size: 10px;
}

.recommendation-description {
  margin: 10px 0;
  color: var(--sc-text-secondary);
  font-size: 11px;
  line-height: 1.7;
}

.recommendation-reason {
  margin-top: 12px;
}

.recommendation-reason strong,
.recommendation-points__label {
  color: var(--sc-text-secondary);
  font-size: 11px;
  font-weight: 650;
}

.recommendation-reason p {
  margin: 5px 0 0;
  color: var(--sc-text);
  font-size: 11px;
  line-height: 1.8;
}

.recommendation-points {
  display: flex;
  flex-direction: column;
  gap: 7px;
  margin-top: 12px;
}

.recommendation-point-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.recommendation-item__footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-top: 13px;
  padding-top: 11px;
  border-top: 1px solid var(--sc-border);
}

.recommendation-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  color: var(--sc-text-placeholder);
  font-size: 10px;
}

@media (max-width: 600px) {
  .recommendation-section {
    padding: 12px;
  }

  .recommendation-item__footer {
    align-items: flex-start;
    flex-direction: column;
  }

  .recommendation-item__footer .el-button {
    width: 100%;
  }
}
</style>
