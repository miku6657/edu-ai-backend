<script setup lang="ts">
import {
  computed,
  onMounted,
  ref,
} from 'vue'

import { useRouter } from 'vue-router'

import {
  getContentOperationOverview,
} from '@/api/content-operation-overview'

import type {
  ContentOperationOverview,
} from '@/api/content-operation-overview'

import {
  listAccessibleCourses,
} from '@/api/course-access'

import type { Course } from '@/api/course'
import type { BigIntId } from '@/api/types'

import BaseCard from '@/components/ui/BaseCard.vue'

import { useAuthStore } from '@/stores/auth'

import {
  notifyError,
} from '@/utils/notify'

const router = useRouter()
const auth = useAuthStore()

const courses = ref<Course[]>([])

const courseId = ref<BigIntId | ''>('')

const dateRange = ref<[string, string] | null>(null)

const overview = ref<ContentOperationOverview | null>(null)

const loading = ref(false)
const loadingCourses = ref(false)

let requestVersion = 0

const documentMetrics = computed(() => {
  const data = overview.value?.documents

  if (!data) return []

  return [
    {
      label: '当前已发布资料',
      value: data.publishedCount,
    },
    {
      label: '即将到期资料',
      value: data.expiringCount,
    },
    {
      label: '已过期资料',
      value: data.expiredCount,
    },
    {
      label: '暂停检索资料',
      value: data.suspendedCount,
    },
    {
      label: '已有确认摘要的资料',
      value: data.confirmedSummaryCount,
    },
  ]
})

const qualityMetrics = computed(() => {
  const data = overview.value?.quality

  if (!data) return []

  return [
    {
      label: '待整改问题',
      value: data.openIssueCount,
    },
    {
      label: '待复核问题',
      value: data.resolvedIssueCount,
    },
  ]
})

const hotQuestionMetrics = computed(() => {
  const data = overview.value?.hotQuestions

  if (!data) return []

  return [
    {
      label: '待整理草稿',
      value: data.draftCount,
    },
    {
      label: '待审核问答',
      value: data.pendingReviewCount,
    },
    {
      label: '已发布问答',
      value: data.publishedCount,
    },
    {
      label: '已驳回问答',
      value: data.rejectedCount,
    },
    {
      label: '已下架问答',
      value: data.offlineCount,
    },
  ]
})

const usageMetrics = computed(() => {
  const data = overview.value?.usage

  if (!data) return []

  return [
    {
      label: '资料预览次数',
      value: data.previewCount,
    },
    {
      label: '资料下载次数',
      value: data.downloadCount,
    },
    {
      label: '资料使用学生',
      value: data.materialStudentCount,
    },
    {
      label: '学生提问次数',
      value: data.questionCount,
    },
    {
      label: '提问学生人数',
      value: data.questionStudentCount,
    },
    {
      label: '推荐曝光次数',
      value: data.recommendationExposureCount,
    },
    {
      label: '推荐点击次数',
      value: data.recommendationClickCount,
    },
    {
      label: '推荐点击率',
      value: formatRate(
        data.recommendationClickRate,
      ),
    },
    {
      label: '内容运营操作次数',
      value: data.operationCount,
    },
  ]
})

const summaryCoverageRate = computed(() => {

  const documents = overview.value?.documents

  if (!documents || documents.publishedCount === 0) {
    return null
  }

  return Math.round(
    documents.confirmedSummaryCount
      * 100
      / documents.publishedCount,
  )
})

const maxDailyActivity = computed(() => {

  const rows = overview.value?.dailyUsage ?? []

  return Math.max(
    1,
    ...rows.map(row =>
      row.previewCount
      + row.downloadCount
      + row.questionCount,
    ),
  )
})

function formatRate(
  value: number | null,
): string {
  return value === null
    ? '—'
    : `${value.toFixed(2)}%`
}

function formatTime(value: string): string {
  return value
    .replace('T', ' ')
    .slice(0, 19)
}

function operationPercent(count: number): number {

  const total =
    overview.value?.usage.operationCount ?? 0

  return total === 0
    ? 0
    : Math.round(count * 10000 / total) / 100
}

function activityWidth(count: number): string {

  return (
    `${count * 100 / maxDailyActivity.value}%`
  )
}

async function loadCourses(): Promise<void> {

  loadingCourses.value = true

  try {

    courses.value = await listAccessibleCourses()

  } catch (error) {

    notifyError(error, '课程列表加载失败')

  } finally {

    loadingCourses.value = false
  }
}

async function loadOverview(): Promise<void> {

  if (!auth.isAdmin) return

  const version = ++requestVersion

  loading.value = true

  // 防止筛选条件已变化，却显示旧统计数据。
  overview.value = null

  try {

    const result =
      await getContentOperationOverview({

        courseId:
          courseId.value || undefined,

        startDate:
          dateRange.value?.[0],

        endDate:
          dateRange.value?.[1],

      })

    if (version !== requestVersion) {
      return
    }

    overview.value = result

  } catch (error) {

    if (version === requestVersion) {

      notifyError(
        error,
        '内容运营统计加载失败',
      )
    }

  } finally {

    if (version === requestVersion) {

      loading.value = false
    }
  }
}

function resetFilters(): void {

  courseId.value = ''

  dateRange.value = null

  void loadOverview()
}

function goToLogs(): void {

  void router.push({
    name: 'admin-content-operation-logs',
    query: courseId.value
      ? { courseId: courseId.value }
      : {},
  })
}

function goToKnowledge(): void {

  void router.push({
    name: 'knowledge',
  })
}

function goToHotQuestions(): void {

  void router.push({
    name: 'course-hot-questions',
    query: courseId.value
      ? { courseId: courseId.value }
      : {},
  })
}

onMounted(() => {

  if (!auth.isAdmin) return

  void loadCourses()

  void loadOverview()

})
</script>

<template>
  <div class="content-operation-page">

    <BaseCard
      title="内容运营概览"
      subtitle="统一分析课程资料质量、更新状态、热门问答及内容使用情况"
    >

      <template #actions>
        <el-button
          type="primary"
          plain
          @click="goToLogs"
        >
          查看运营日志
        </el-button>
      </template>

      <div class="overview-filters">

        <el-select
          v-model="courseId"
          placeholder="全部课程"
          filterable
          :loading="loadingCourses"
          :disabled="loading"
          style="width: 240px"
        >

          <el-option
            label="全部课程"
            value=""
          />

          <el-option
            v-for="course in courses"
            :key="course.id"
            :label="course.name"
            :value="course.id"
          />

        </el-select>

        <el-date-picker
          v-model="dateRange"
          type="daterange"
          value-format="YYYY-MM-DD"
          range-separator="至"
          start-placeholder="开始日期"
          end-placeholder="结束日期"
          :disabled="loading"
          style="width: 300px"
        />

        <el-button
          type="primary"
          :loading="loading"
          @click="loadOverview"
        >
          查询统计
        </el-button>

        <el-button
          :disabled="loading"
          @click="resetFilters"
        >
          重置
        </el-button>

      </div>

      <p class="statistics-note">
        默认统计近30个自然日。
        资料状态、质量问题及标准问答状态为当前快照，
        不受日期范围限制。
      </p>

    </BaseCard>

    <el-skeleton
      v-if="loading"
      :rows="10"
      animated
    />

    <template v-else-if="overview">

      <!-- 一、资料状态 -->

      <BaseCard
        title="课程资料运营"
        subtitle="当前资料发布、有效期及摘要覆盖情况"
      >

        <template #actions>
          <el-button
            text
            type="primary"
            @click="goToKnowledge"
          >
            管理资料
          </el-button>
        </template>

        <div class="metric-grid">

          <div
            v-for="item in documentMetrics"
            :key="item.label"
            class="metric-card"
          >
            <span>{{ item.label }}</span>

            <strong>{{ item.value }}</strong>
          </div>

        </div>

        <div class="summary-coverage">

          <div class="section-label">
            <span>已确认摘要覆盖率</span>

            <strong>
              {{
                summaryCoverageRate === null
                  ? '—'
                  : `${summaryCoverageRate}%`
              }}
            </strong>
          </div>

          <el-progress
            v-if="summaryCoverageRate !== null"
            :percentage="summaryCoverageRate"
            :stroke-width="10"
          />

        </div>

      </BaseCard>

      <!-- 二、质量运营 -->

      <BaseCard
        title="资料质量管理"
        subtitle="当前进行中的质量任务及待处理问题"
      >

        <div class="metric-grid quality-grid">

          <div
            v-for="item in qualityMetrics"
            :key="item.label"
            class="metric-card"
          >
            <span>{{ item.label }}</span>

            <strong
              :class="{
                'attention-text': item.value > 0,
              }"
            >
              {{ item.value }}
            </strong>
          </div>

        </div>

      </BaseCard>

      <!-- 三、热门问题 -->

      <BaseCard
        title="热门问题运营"
        subtitle="标准问答整理、审核和发布状态"
      >

        <template #actions>
          <el-button
            type="primary"
            text
            @click="goToHotQuestions"
          >
            热门问题维护
          </el-button>
        </template>

        <div class="metric-grid">

          <div
            v-for="item in hotQuestionMetrics"
            :key="item.label"
            class="metric-card"
          >
            <span>{{ item.label }}</span>

            <strong>{{ item.value }}</strong>
          </div>

        </div>

      </BaseCard>

      <!-- 四、使用统计 -->

      <BaseCard
        title="内容使用统计"
        :subtitle="`${overview.startDate} 至 ${overview.endDate}`"
      >

        <div class="metric-grid usage-grid">

          <div
            v-for="item in usageMetrics"
            :key="item.label"
            class="metric-card"
          >
            <span>{{ item.label }}</span>

            <strong>{{ item.value }}</strong>
          </div>

        </div>

        <p class="statistics-note">
          推荐点击率以统计周期内产生的曝光记录为分母，
          只计算这些曝光在截止时间前对应的点击。
          历史行为从各统计功能正式启用后开始累计。
        </p>

      </BaseCard>

      <!-- 五、每日活动趋势 -->

      <BaseCard
        title="每日内容使用趋势"
        subtitle="展示资料预览、下载和学生课程提问的每日变化"
      >

        <div class="trend-legend">

          <span>
            <i class="legend-preview"></i>
            资料预览
          </span>

          <span>
            <i class="legend-download"></i>
            资料下载
          </span>

          <span>
            <i class="legend-question"></i>
            学生提问
          </span>

        </div>

        <div class="trend-container">

          <div
            v-for="item in overview.dailyUsage"
            :key="item.date"
            class="trend-row"
          >

            <span class="trend-date">
              {{ item.date }}
            </span>

            <div class="trend-track">

              <div
                class="trend-segment legend-preview"
                :style="{
                  width: activityWidth(
                    item.previewCount,
                  ),
                }"
                :title="`预览 ${item.previewCount} 次`"
              />

              <div
                class="trend-segment legend-download"
                :style="{
                  width: activityWidth(
                    item.downloadCount,
                  ),
                }"
                :title="`下载 ${item.downloadCount} 次`"
              />

              <div
                class="trend-segment legend-question"
                :style="{
                  width: activityWidth(
                    item.questionCount,
                  ),
                }"
                :title="`提问 ${item.questionCount} 次`"
              />

            </div>

            <span class="trend-total">

              {{
                item.previewCount
                + item.downloadCount
                + item.questionCount
              }}

            </span>

          </div>

        </div>

      </BaseCard>

      <!-- 六、每日详细统计 -->

      <BaseCard
        title="每日使用明细"
      >

        <el-table
          :data="overview.dailyUsage"
          row-key="date"
          stripe
          max-height="420"
          style="width: 100%"
        >

          <el-table-column
            prop="date"
            label="统计日期"
            width="125"
          />

          <el-table-column
            prop="previewCount"
            label="资料预览"
            min-width="100"
          />

          <el-table-column
            prop="downloadCount"
            label="资料下载"
            min-width="100"
          />

          <el-table-column
            prop="questionCount"
            label="学生提问"
            min-width="100"
          />

          <el-table-column
            prop="recommendationExposureCount"
            label="推荐曝光"
            min-width="100"
          />

          <el-table-column
            prop="recommendationClickCount"
            label="推荐点击"
            min-width="100"
          />

          <el-table-column
            prop="operationCount"
            label="运营操作"
            min-width="100"
          />

        </el-table>

      </BaseCard>

      <!-- 七、运营日志分布 -->

      <BaseCard
        title="运营操作分布"
        subtitle="按操作类型统计当前日期范围内的运营行为"
      >

        <el-table
          :data="overview.operationTypes"
          stripe
          style="width: 100%"
          empty-text="当前统计周期暂无运营操作"
        >

          <el-table-column
            prop="operationName"
            label="操作名称"
            min-width="180"
          />

          <el-table-column
            prop="operationType"
            label="操作编码"
            min-width="210"
          />

          <el-table-column
            prop="operationCount"
            label="操作次数"
            width="110"
          />

          <el-table-column
            label="操作占比"
            min-width="180"
          >

            <template #default="{ row }">

              <el-progress
                :percentage="
                  operationPercent(
                    row.operationCount,
                  )
                "
                :stroke-width="8"
              />

            </template>

          </el-table-column>

        </el-table>

      </BaseCard>

      <div class="generated-time">
        统计生成时间：
        {{ formatTime(overview.generatedAt) }}
      </div>

    </template>

    <el-empty
      v-else
      description="暂无统计数据，请重新查询"
    />

  </div>
</template>

<style scoped>
.content-operation-page {
  display: flex;
  flex-direction: column;
  gap: 16px;
  min-width: 0;
}

.overview-filters {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 12px;
}

.metric-grid {
  display: grid;
  grid-template-columns: repeat(
    5,
    minmax(0, 1fr)
  );
  gap: 12px;
}

.quality-grid {
  grid-template-columns: repeat(
    2,
    minmax(0, 1fr)
  );
}

.usage-grid {
  grid-template-columns: repeat(
    3,
    minmax(0, 1fr)
  );
}

.metric-card {
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding: 16px;
  border: 1px solid var(--sc-border);
  border-radius: 10px;
  background: var(--sc-surface-muted);
}

.metric-card span {
  font-size: 12px;
  color: var(--sc-text-secondary);
}

.metric-card strong {
  font-size: 27px;
  font-weight: 650;
  color: var(--sc-text);
  overflow-wrap: anywhere;
}

.metric-card .attention-text {
  color: var(--el-color-warning);
}

.summary-coverage {
  margin-top: 20px;
  padding: 14px;
  border-radius: 9px;
  background: var(--sc-surface-muted);
}

.section-label {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
  font-size: 13px;
  color: var(--sc-text-secondary);
}

.section-label strong {
  color: var(--sc-text);
}

.statistics-note {
  margin-top: 13px;
  color: var(--sc-text-secondary);
  font-size: 12px;
  line-height: 1.8;
}

.trend-legend {
  display: flex;
  flex-wrap: wrap;
  gap: 16px;
  margin-bottom: 18px;
}

.trend-legend span {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  color: var(--sc-text-secondary);
}

.trend-legend i {
  width: 10px;
  height: 10px;
  border-radius: 2px;
}

.legend-preview {
  background: #6096e8;
}

.legend-download {
  background: #53b591;
}

.legend-question {
  background: #e4b46a;
}

.trend-container {
  max-height: 480px;
  overflow-y: auto;
  padding-right: 10px;
}

.trend-row {
  display: grid;
  grid-template-columns: 95px minmax(0, 1fr) 45px;
  align-items: center;
  gap: 12px;
  min-height: 32px;
}

.trend-date {
  color: var(--sc-text-secondary);
  font-size: 11px;
}

.trend-track {
  display: flex;
  height: 12px;
  border-radius: 4px;
  overflow: hidden;
  background: var(--sc-surface-muted);
}

.trend-segment {
  height: 100%;
  flex-shrink: 0;
}

.trend-total {
  text-align: right;
  color: var(--sc-text);
  font-size: 12px;
}

.generated-time {
  color: var(--sc-text-placeholder);
  font-size: 11px;
  text-align: right;
}

@media (max-width: 1000px) {
  .metric-grid {
    grid-template-columns: repeat(
      3,
      minmax(0, 1fr)
    );
  }
}

@media (max-width: 640px) {
  .metric-grid {
    grid-template-columns: repeat(
      2,
      minmax(0, 1fr)
    );
  }

  .overview-filters > * {
    width: 100% !important;
  }

  .metric-card strong {
    font-size: 22px;
  }

  .trend-row {
    grid-template-columns: 85px minmax(0, 1fr) 30px;
    gap: 7px;
  }
}
</style>
