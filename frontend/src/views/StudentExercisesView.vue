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
import { listStudentExercises } from '@/api/exercises'
import type { StudentExercise } from '@/api/exercises'
import BaseCard from '@/components/ui/BaseCard.vue'
import EmptyState from '@/components/ui/EmptyState.vue'
import { notifyError } from '@/utils/notify'

defineOptions({ name: 'StudentExercisesView' })

const router = useRouter()
const courses = ref<Course[]>([])
const exercises = ref<StudentExercise[]>([])
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
  loading.value = true

  try {
    exercises.value = await listStudentExercises(
      selectedCourseId.value || undefined,
    )
  } catch (error) {
    notifyError(error)
  } finally {
    loading.value = false
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
</style>
