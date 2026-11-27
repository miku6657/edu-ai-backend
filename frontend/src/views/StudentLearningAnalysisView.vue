<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'

import {
  getStudentLearningAnalysis,
} from '@/api/learning-analysis'

import type {
  StudentLearningAnalysis,
} from '@/api/learning-analysis'

import BaseCard from '@/components/ui/BaseCard.vue'
import { notifyError } from '@/utils/notify'

defineOptions({
  name: 'StudentLearningAnalysisView',
})

const loading = ref(false)

const analysis = ref<StudentLearningAnalysis | null>(null)

const accuracyLabel = computed(() => {
  const rate = analysis.value?.exerciseAccuracyRate

  return rate === null || rate === undefined
    ? '--'
    : `${rate}%`
})

const taskCompletionLabel = computed(() => {
  if (!analysis.value) return '0 / 0'

  return `${analysis.value.completedTaskCount} / ${analysis.value.totalTaskCount}`
})

const taskCompletionRate = computed(() => {
  const data = analysis.value

  if (!data || data.totalTaskCount === 0) return 0

  return Math.round(
    data.completedTaskCount * 100 / data.totalTaskCount,
  )
})

const weeklyTotal = computed(() => {
  const records = analysis.value?.weeklyRecords ?? []

  return records.reduce(
    (total, item) => total
      + item.exerciseCount
      + item.questionCount
      + item.completedTaskCount,
    0,
  )
})

onMounted(() => {
  void loadAnalysis()
})

async function loadAnalysis(): Promise<void> {
  loading.value = true

  try {
    analysis.value = await getStudentLearningAnalysis()
  } catch (error) {
    notifyError(error)
  } finally {
    loading.value = false
  }
}

function formatDate(value: string): string {
  const date = new Date(`${value}T00:00:00`)

  if (Number.isNaN(date.getTime())) return value

  return date.toLocaleDateString('zh-CN', {
    month: '2-digit',
    day: '2-digit',
  })
}

function weekdayLabel(value: string): string {
  const date = new Date(`${value}T00:00:00`)

  if (Number.isNaN(date.getTime())) return ''

  return [
    '周日',
    '周一',
    '周二',
    '周三',
    '周四',
    '周五',
    '周六',
  ][date.getDay()]
}
</script>

<template>
  <div class="learning-analysis-page">

    <!-- 页面标题 -->
    <div class="analysis-header">
      <div>
        <div class="analysis-eyebrow">
          LEARNING ANALYSIS
        </div>

        <h2>我的学习情况</h2>

        <p>
          汇总课程练习、学习提问和任务完成情况，
          帮助你了解近期学习进度。
        </p>
      </div>

      <el-button
        type="primary"
        :loading="loading"
        @click="loadAnalysis"
      >
        刷新数据
      </el-button>
    </div>

    <el-skeleton
      v-if="loading && !analysis"
      :rows="6"
      animated
    />

    <template v-else-if="analysis">

      <!-- 学习数据概况 -->
      <div class="analysis-stats">

        <BaseCard>
          <div class="analysis-stat">
            <span>练习次数</span>
            <strong>{{ analysis.exerciseCount }}</strong>
            <small>已提交的练习记录</small>
          </div>
        </BaseCard>

        <BaseCard>
          <div class="analysis-stat">
            <span>练习正确率</span>
            <strong>{{ accuracyLabel }}</strong>
            <small>
              {{
                analysis.objectiveAnswerCount > 0
                  ? `已统计 ${analysis.objectiveAnswerCount} 道已判分客观题`
                  : '暂无已判分的客观题'
              }}
            </small>
          </div>
        </BaseCard>

        <BaseCard>
          <div class="analysis-stat">
            <span>课程提问次数</span>
            <strong>{{ analysis.questionCount }}</strong>
            <small>普通课程问答记录</small>
          </div>
        </BaseCard>

        <BaseCard>
          <div class="analysis-stat">
            <span>学习任务完成</span>
            <strong>{{ taskCompletionLabel }}</strong>
            <small>当前有效课程任务</small>
          </div>
        </BaseCard>

      </div>

      <!-- 周度学习记录 -->
      <BaseCard>

        <template #header>
          <div class="section-heading">
            <div>
              <h3>本周学习记录</h3>

              <p>
                {{ formatDate(analysis.weekStart) }}
                至
                {{ formatDate(analysis.weekEnd) }}
                ，按自然周统计。
              </p>
            </div>

            <el-tag type="info" effect="plain">
              本周活动 {{ weeklyTotal }} 次
            </el-tag>
          </div>
        </template>

        <!-- 周度汇总 -->
        <div class="weekly-summary">

          <div class="weekly-summary__item">
            <span>本周练习</span>
            <strong>
              {{
                analysis.weeklyRecords.reduce(
                  (sum, item) => sum + item.exerciseCount,
                  0,
                )
              }}
            </strong>
          </div>

          <div class="weekly-summary__item">
            <span>本周提问</span>
            <strong>
              {{
                analysis.weeklyRecords.reduce(
                  (sum, item) => sum + item.questionCount,
                  0,
                )
              }}
            </strong>
          </div>

          <div class="weekly-summary__item">
            <span>本周完成任务</span>
            <strong>
              {{
                analysis.weeklyRecords.reduce(
                  (sum, item) => sum + item.completedTaskCount,
                  0,
                )
              }}
            </strong>
          </div>

          <div class="weekly-summary__item">
            <span>当前任务完成率</span>
            <strong>{{ taskCompletionRate }}%</strong>
          </div>

        </div>

        <!-- 每日学习记录 -->
        <el-table
          :data="analysis.weeklyRecords"
          stripe
          style="width: 100%"
        >

          <el-table-column
            label="日期"
            min-width="130"
          >
            <template #default="{ row }">
              <div class="date-cell">
                <strong>{{ weekdayLabel(row.date) }}</strong>
                <span>{{ formatDate(row.date) }}</span>
              </div>
            </template>
          </el-table-column>

          <el-table-column
            prop="exerciseCount"
            label="练习次数"
            min-width="110"
            align="center"
          />

          <el-table-column
            prop="questionCount"
            label="提问次数"
            min-width="110"
            align="center"
          />

          <el-table-column
            prop="completedTaskCount"
            label="完成任务"
            min-width="110"
            align="center"
          />

          <el-table-column
            label="当日活动"
            min-width="110"
            align="center"
          >
            <template #default="{ row }">
              {{
                row.exerciseCount
                  + row.questionCount
                  + row.completedTaskCount
              }}
            </template>
          </el-table-column>

        </el-table>

      </BaseCard>

    </template>

    <el-empty
      v-else
      description="暂时无法获取学习分析数据，请稍后刷新。"
    >
      <el-button
        type="primary"
        :loading="loading"
        @click="loadAnalysis"
      >
        重新加载
      </el-button>
    </el-empty>

  </div>
</template>

<style scoped>
.learning-analysis-page {
  display: flex;
  flex-direction: column;
  gap: 16px;
  min-width: 0;
}

.analysis-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  flex-wrap: wrap;
}

.analysis-eyebrow {
  color: var(--sc-primary);
  font-size: 10px;
  font-weight: 700;
  letter-spacing: 2px;
}

.analysis-header h2 {
  margin: 7px 0;
  color: var(--sc-text);
  font-size: 23px;
}

.analysis-header p,
.section-heading p {
  margin: 0;
  color: var(--sc-text-secondary);
  font-size: 12px;
  line-height: 1.7;
}

.analysis-stats {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
}

.analysis-stat {
  display: flex;
  flex-direction: column;
  gap: 7px;
}

.analysis-stat span {
  color: var(--sc-text-secondary);
  font-size: 12px;
}

.analysis-stat strong {
  color: var(--sc-text);
  font-size: 27px;
  font-weight: 700;
}

.analysis-stat small {
  color: var(--sc-text-placeholder);
  font-size: 11px;
  line-height: 1.5;
}

.section-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.section-heading h3 {
  margin: 0 0 5px;
  color: var(--sc-text);
  font-size: 15px;
}

.weekly-summary {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
  margin-bottom: 18px;
}

.weekly-summary__item {
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding: 14px;
  border-radius: 10px;
  background: var(--sc-bg);
}

.weekly-summary__item span {
  color: var(--sc-text-secondary);
  font-size: 12px;
}

.weekly-summary__item strong {
  color: var(--sc-text);
  font-size: 22px;
}

.date-cell {
  display: flex;
  flex-direction: column;
  gap: 3px;
}

.date-cell strong {
  color: var(--sc-text);
  font-size: 13px;
}

.date-cell span {
  color: var(--sc-text-placeholder);
  font-size: 11px;
}

@media (max-width: 900px) {
  .analysis-stats {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .weekly-summary {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 600px) {
  .analysis-header {
    align-items: flex-start;
  }

  .section-heading {
    align-items: flex-start;
    flex-direction: column;
  }
}
</style>
