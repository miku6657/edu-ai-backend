<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'

import {
  getStudentRetryMastery,
  listStudentRetryHistory,
} from '@/api/exercises'

import type {
  StudentRetryHistory,
  StudentRetryMastery,
} from '@/api/exercises'

import BaseCard from '@/components/ui/BaseCard.vue'

import {
  notifyError,
} from '@/utils/notify'

defineOptions({
  name: 'StudentRetryProgressView',
})

const router = useRouter()

const loading = ref(false)

const history = ref<StudentRetryHistory[]>([])

const mastery = ref<StudentRetryMastery>({
  totalKnowledgePoints: 0,
  masteredKnowledgePoints: 0,
  needsPracticeKnowledgePoints: 0,
  knowledgePoints: [],
})

const completedAttempts = computed(() =>
  history.value.filter(item => item.status === 'GRADED'),
)

const totalAttempts = computed(
  () => history.value.length,
)

const averageAccuracy = computed(() => {
  const records = completedAttempts.value.filter(
    item => item.objectiveCount > 0,
  )

  const total = records.reduce(
    (sum, item) => sum + item.objectiveCount,
    0,
  )

  if (total === 0) return 0

  const correct = records.reduce(
    (sum, item) => sum + item.correctCount,
    0,
  )

  return Math.round(correct * 100 / total)
})

const overallMasteryRate = computed(() => {
  const points = mastery.value.knowledgePoints

  if (points.length === 0) return 0

  const totalQuestions = points.reduce(
    (sum, item) => sum + item.questionCount,
    0,
  )

  const masteredQuestions = points.reduce(
    (sum, item) => sum + item.masteredCount,
    0,
  )

  if (totalQuestions === 0) return 0

  return Math.round(masteredQuestions * 100 / totalQuestions)
})

onMounted(() => {
  void loadProgress()
})

async function loadProgress(): Promise<void> {
  loading.value = true

  try {
    const [historyData, masteryData] = await Promise.all([
      listStudentRetryHistory(),
      getStudentRetryMastery(),
    ])

    history.value = historyData
    mastery.value = masteryData
  } catch (error) {
    notifyError(error)
  } finally {
    loading.value = false
  }
}

function statusLabel(status: string): string {
  switch (status) {
    case 'IN_PROGRESS':
      return '进行中'

    case 'PENDING_SELF_REVIEW':
      return '待自我核对'

    case 'GRADED':
      return '已完成'

    default:
      return '未知状态'
  }
}

function statusType(
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

function masteryLabel(status: string): string {
  return status === 'MASTERED'
    ? '已经掌握'
    : '仍需巩固'
}

function masteryType(
  status: string,
): 'success' | 'warning' {
  return status === 'MASTERED'
    ? 'success'
    : 'warning'
}

function formatTime(value: string | null): string {
  if (!value) return '-'

  const date = new Date(value.replace(' ', 'T'))

  if (Number.isNaN(date.getTime())) {
    return value
  }

  return date.toLocaleString('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
  })
}

/**
 * 进行中的重练返回答题页。
 * 已提交或待核对的重练进入结果页。
 */
function openAttempt(item: StudentRetryHistory): void {
  if (item.status === 'IN_PROGRESS') {
    void router.push({
      name: 'student-retry-detail',
      params: {
        listId: item.retryListId,
      },
    })

    return
  }

  void router.push({
    name: 'student-retry-result',
    params: {
      attemptId: item.attemptId,
    },
  })
}

function backToLists(): void {
  void router.push({
    name: 'my-retry-lists',
  })
}
</script>

<template>
  <div class="retry-progress-page">

    <div class="progress-header">
      <div>
        <div class="progress-eyebrow">
          LEARNING PROGRESS
        </div>

        <h2>学习进步</h2>

        <p>
          通过重练记录了解自己的学习情况，
          持续巩固薄弱知识点。
        </p>
      </div>

      <div class="progress-actions">
        <el-button @click="backToLists">
          返回重练清单
        </el-button>

        <el-button
          type="primary"
          :loading="loading"
          @click="loadProgress"
        >
          刷新数据
        </el-button>
      </div>
    </div>

    <el-skeleton
      v-if="loading && history.length === 0"
      :rows="6"
      animated
    />

    <template v-else>

      <!-- 总体学习数据 -->
      <div class="progress-stats">

        <BaseCard>
          <div class="progress-stat">
            <span>重练次数</span>
            <strong>{{ totalAttempts }}</strong>
            <small>全部答题记录</small>
          </div>
        </BaseCard>

        <BaseCard>
          <div class="progress-stat">
            <span>完成次数</span>
            <strong>{{ completedAttempts.length }}</strong>
            <small>已经完成判分</small>
          </div>
        </BaseCard>

        <BaseCard>
          <div class="progress-stat">
            <span>客观题正确率</span>
            <strong>{{ averageAccuracy }}%</strong>
            <small>按已完成重练题量加权</small>
          </div>
        </BaseCard>

        <BaseCard>
          <div class="progress-stat">
            <span>知识点掌握率</span>
            <strong>{{ overallMasteryRate }}%</strong>
            <small>按最近一次重练结果统计</small>
          </div>
        </BaseCard>

      </div>

      <!-- 知识点掌握情况 -->
      <BaseCard>

        <template #header>
          <div class="section-heading">
            <div>
              <h3>知识点掌握情况</h3>
              <p>
                每道题只统计最近一次已完成的重练结果。
              </p>
            </div>
          </div>
        </template>

        <div class="mastery-summary">

          <div class="mastery-summary__item">
            <span>统计知识点</span>
            <strong>{{ mastery.totalKnowledgePoints }}</strong>
          </div>

          <div class="mastery-summary__item">
            <span>已经掌握</span>
            <strong class="success-text">
              {{ mastery.masteredKnowledgePoints }}
            </strong>
          </div>

          <div class="mastery-summary__item">
            <span>仍需巩固</span>
            <strong class="warning-text">
              {{ mastery.needsPracticeKnowledgePoints }}
            </strong>
          </div>

        </div>

        <el-empty
          v-if="mastery.knowledgePoints.length === 0"
          description="完成至少一轮重练后，这里会展示知识点掌握情况"
        />

        <div v-else class="mastery-list">

          <article
            v-for="item in mastery.knowledgePoints"
            :key="`${item.courseId}-${item.knowledgePointId ?? 'none'}`"
            class="mastery-item"
          >

            <div class="mastery-item__header">

              <div>
                <span class="mastery-course">
                  {{ item.courseName }}
                </span>

                <h4>{{ item.knowledgePointTitle }}</h4>
              </div>

              <el-tag
                :type="masteryType(item.status)"
                size="small"
              >
                {{ masteryLabel(item.status) }}
              </el-tag>

            </div>

            <div class="mastery-progress">
              <el-progress
                :percentage="item.masteryRate"
                :stroke-width="9"
              />
            </div>

            <div class="mastery-meta">
              <span>
                共 {{ item.questionCount }} 道题
              </span>

              <span class="success-text">
                已掌握 {{ item.masteredCount }}
              </span>

              <span class="warning-text">
                待巩固 {{ item.needsPracticeCount }}
              </span>

              <span>
                最近练习：{{ formatTime(item.lastPracticedAt) }}
              </span>
            </div>

          </article>

        </div>

      </BaseCard>

      <!-- 重练历史 -->
      <BaseCard>

        <template #header>
          <div class="section-heading">
            <div>
              <h3>重练历史记录</h3>
              <p>
                查看每次重练成绩和答题情况。
              </p>
            </div>
          </div>
        </template>

        <el-empty
          v-if="history.length === 0"
          description="暂时没有重练历史"
        />

        <el-table
          v-else
          :data="history"
          stripe
          style="width: 100%"
        >

          <el-table-column
            prop="retryListTitle"
            label="重练清单"
            min-width="160"
            show-overflow-tooltip
          />

          <el-table-column
            label="次数"
            width="90"
          >
            <template #default="{ row }">
              第 {{ row.attemptNo }} 次
            </template>
          </el-table-column>

          <el-table-column
            label="成绩"
            width="120"
          >
            <template #default="{ row }">
              {{ row.earnedScore }} / {{ row.totalScore }}
            </template>
          </el-table-column>

          <el-table-column
            label="客观题正确率"
            width="130"
          >
            <template #default="{ row }">
              {{ row.objectiveCount > 0
                ? `${row.accuracyRate}%`
                : '-'
              }}
            </template>
          </el-table-column>

          <el-table-column
            label="答题进度"
            width="110"
          >
            <template #default="{ row }">
              {{ row.answeredCount }} / {{ row.itemCount }}
            </template>
          </el-table-column>

          <el-table-column
            label="状态"
            width="130"
          >
            <template #default="{ row }">
              <el-tag
                :type="statusType(row.status)"
                size="small"
              >
                {{ statusLabel(row.status) }}
              </el-tag>
            </template>
          </el-table-column>

          <el-table-column
            label="开始时间"
            min-width="155"
          >
            <template #default="{ row }">
              {{ formatTime(row.startedAt) }}
            </template>
          </el-table-column>

          <el-table-column
            label="操作"
            width="120"
            fixed="right"
          >
            <template #default="{ row }">
              <el-button
                type="primary"
                link
                @click="openAttempt(row)"
              >
                {{
                  row.status === 'IN_PROGRESS'
                    ? '继续重练'
                    : '查看结果'
                }}
              </el-button>
            </template>
          </el-table-column>

        </el-table>

      </BaseCard>

    </template>
  </div>
</template>

<style scoped>
.retry-progress-page {
  display: flex;
  flex-direction: column;
  gap: 16px;
  min-width: 0;
}

.progress-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  flex-wrap: wrap;
}

.progress-eyebrow {
  color: var(--sc-primary);
  font-size: 10px;
  font-weight: 700;
  letter-spacing: 2px;
}

.progress-header h2 {
  margin: 7px 0;
  color: var(--sc-text);
  font-size: 23px;
}

.progress-header p,
.section-heading p {
  margin: 0;
  color: var(--sc-text-secondary);
  font-size: 12px;
  line-height: 1.7;
}

.progress-actions {
  display: flex;
  gap: 8px;
}

.progress-stats {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
}

.progress-stat {
  display: flex;
  flex-direction: column;
  gap: 7px;
}

.progress-stat span {
  color: var(--sc-text-secondary);
  font-size: 12px;
}

.progress-stat strong {
  color: var(--sc-text);
  font-size: 27px;
  font-weight: 700;
}

.progress-stat small {
  color: var(--sc-text-placeholder);
  font-size: 11px;
}

.section-heading h3 {
  margin: 0 0 5px;
  color: var(--sc-text);
  font-size: 15px;
}

.mastery-summary {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
  margin-bottom: 18px;
}

.mastery-summary__item {
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding: 14px;
  border-radius: 10px;
  background: var(--sc-bg);
}

.mastery-summary__item span {
  color: var(--sc-text-secondary);
  font-size: 12px;
}

.mastery-summary__item strong {
  font-size: 23px;
}

.mastery-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.mastery-item {
  padding: 15px;
  border: 1px solid var(--sc-border);
  border-radius: 12px;
}

.mastery-item__header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 12px;
}

.mastery-course {
  color: var(--sc-text-placeholder);
  font-size: 11px;
}

.mastery-item h4 {
  margin: 5px 0 0;
  color: var(--sc-text);
  font-size: 14px;
}

.mastery-progress {
  margin: 15px 0 10px;
}

.mastery-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  color: var(--sc-text-secondary);
  font-size: 11px;
}

.success-text {
  color: var(--sc-success, #16a34a);
}

.warning-text {
  color: var(--sc-warning, #d97706);
}

@media (max-width: 760px) {
  .progress-stats {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .mastery-summary {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }

  .progress-header {
    align-items: flex-start;
  }

  .mastery-meta {
    gap: 8px;
  }
}
</style>
