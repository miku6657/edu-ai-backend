<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'

import {
  generateStudentLearningReport,
  getStudentLearningReport,
  listStudentLearningReports,
} from '@/api/learning-report'

import type {
  LearningReport,
  LearningReportDetail,
} from '@/api/learning-report'

import { listAccessibleCourses } from '@/api/course-access'
import type { Course } from '@/api/course'
import type { BigIntId } from '@/api/types'

import { useCourseStore } from '@/stores/course'
import BaseCard from '@/components/ui/BaseCard.vue'

import {
  notifyError,
  notifySuccess,
  notifyWarning,
} from '@/utils/notify'

defineOptions({
  name: 'StudentLearningReportsView',
})

const courseStore = useCourseStore()

const courses = ref<Course[]>([])
const selectedCourseId = ref<BigIntId | null>(
  courseStore.selectedCourseId,
)

const reports = ref<LearningReport[]>([])
const selectedReport = ref<LearningReportDetail | null>(null)

const loadingCourses = ref(false)
const loading = ref(false)
const generating = ref(false)
const detailLoading = ref(false)

const detailVisible = ref(false)

function formatLocalDate(date: Date): string {
  const year = date.getFullYear()
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')

  return `${year}-${month}-${day}`
}

function getDefaultDateRange(): [string, string] {
  const end = new Date()
  const start = new Date()

  start.setDate(start.getDate() - 6)

  return [
    formatLocalDate(start),
    formatLocalDate(end),
  ]
}

const dateRange = ref<[string, string]>(
  getDefaultDateRange(),
)

const hasCourse = computed(
  () => selectedCourseId.value !== null,
)

const weakPointCount = computed(() => {
  return reports.value.reduce(
    (sum, item) => sum + item.weakPointCount,
    0,
  )
})

onMounted(() => {
  void loadCourses()
})

async function loadCourses(): Promise<void> {
  loadingCourses.value = true

  try {
    courses.value = await listAccessibleCourses()

    const exists = courses.value.some(
      item => item.id === selectedCourseId.value,
    )

    if (!exists) {
      selectedCourseId.value = courses.value[0]?.id ?? null
    }

    if (selectedCourseId.value) {
      courseStore.select(selectedCourseId.value)
      await loadReports()
    }
  } catch (error) {
    notifyError(error)
  } finally {
    loadingCourses.value = false
  }
}

async function handleCourseChange(): Promise<void> {
  const courseId = selectedCourseId.value

  courseStore.select(courseId)

  reports.value = []
  selectedReport.value = null

  if (courseId) {
    await loadReports()
  }
}

async function loadReports(): Promise<void> {
  const courseId = selectedCourseId.value

  if (!courseId) {
    reports.value = []
    return
  }

  loading.value = true

  try {
    const result = await listStudentLearningReports(courseId)

    if (courseId === selectedCourseId.value) {
      reports.value = result
    }
  } catch (error) {
    notifyError(error)
  } finally {
    loading.value = false
  }
}

async function generateReport(): Promise<void> {
  const courseId = selectedCourseId.value

  if (!courseId) {
    notifyWarning('请先选择课程')
    return
  }

  if (!dateRange.value || dateRange.value.length !== 2) {
    notifyWarning('请选择统计日期范围')
    return
  }

  const [startDate, endDate] = dateRange.value

  if (endDate < startDate) {
    notifyWarning('结束日期不能早于开始日期')
    return
  }

  generating.value = true

  try {
    const result = await generateStudentLearningReport(
      courseId,
      {
        startDate,
        endDate,
      },
    )

    notifySuccess('阶段学习报告生成成功')

    await loadReports()

    selectedReport.value = result
    detailVisible.value = true
  } catch (error) {
    notifyError(error)
  } finally {
    generating.value = false
  }
}

async function openDetail(row: LearningReport): Promise<void> {
  const courseId = selectedCourseId.value

  if (!courseId) return

  detailVisible.value = true
  detailLoading.value = true
  selectedReport.value = null

  try {
    selectedReport.value = await getStudentLearningReport(
      courseId,
      row.id,
    )
  } catch (error) {
    detailVisible.value = false
    notifyError(error)
  } finally {
    detailLoading.value = false
  }
}

function formatDate(value: string): string {
  if (!value) return '--'

  return value.slice(0, 10)
}

function formatDateTime(value: string): string {
  if (!value) return '--'

  return value.replace('T', ' ').slice(0, 16)
}

function accuracyLabel(value: number | null): string {
  return value === null ? '--' : `${value}%`
}

function completionLabel(report: LearningReport): string {
  return `${report.completedTaskCount}/${report.totalTaskCount}`
}

function completionRate(report: LearningReport): number {
  if (report.totalTaskCount === 0) return 0

  return Math.round(
    report.completedTaskCount
      / report.totalTaskCount
      * 100,
  )
}

function diagnosisTagType(
  level: number,
): 'danger' | 'warning' | 'success' | 'info' {
  if (level === 0) return 'danger'
  if (level === 1) return 'warning'

  return 'success'
}
</script>

<template>
  <div class="learning-report-page">

    <div class="report-header">
      <div>
        <div class="report-eyebrow">
          LEARNING REPORT
        </div>

        <h2>阶段学习报告</h2>

        <p>
          汇总阶段练习、课程提问、任务完成情况和薄弱知识点，
          形成个人学习总结。
        </p>
      </div>

      <el-button
        type="primary"
        :loading="loading"
        @click="loadReports"
      >
        刷新报告
      </el-button>
    </div>

    <BaseCard title="生成学习报告">
      <div class="report-filter">

        <el-select
          v-model="selectedCourseId"
          placeholder="选择课程"
          style="width: 260px"
          :loading="loadingCourses"
          @change="handleCourseChange"
        >
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
          start-placeholder="统计开始日期"
          end-placeholder="统计结束日期"
          range-separator="至"
          :clearable="false"
        />

        <el-button
          type="primary"
          :loading="generating"
          :disabled="!hasCourse"
          @click="generateReport"
        >
          生成阶段报告
        </el-button>
      </div>

      <p class="report-tip">
        报告生成后会保存统计快照，历史报告不会随着后续学习数据变化而改变。
      </p>
    </BaseCard>

    <el-row
      :gutter="14"
      class="report-overview"
    >
      <el-col :xs="12" :sm="8">
        <BaseCard>
          <div class="overview-item">
            <span>报告总数</span>
            <strong>{{ reports.length }}</strong>
            <small>已生成的阶段报告</small>
          </div>
        </BaseCard>
      </el-col>

      <el-col :xs="12" :sm="8">
        <BaseCard>
          <div class="overview-item">
            <span>薄弱知识点累计</span>
            <strong>{{ weakPointCount }}</strong>
            <small>各份报告中的诊断记录数</small>
          </div>
        </BaseCard>
      </el-col>

      <el-col :xs="12" :sm="8">
        <BaseCard>
          <div class="overview-item">
            <span>当前课程</span>
            <strong class="course-name">
              {{
                courses.find(
                  item => item.id === selectedCourseId,
                )?.name ?? '未选择'
              }}
            </strong>
            <small>当前报告查询范围</small>
          </div>
        </BaseCard>
      </el-col>
    </el-row>

    <BaseCard title="我的报告历史">
      <el-table
        v-loading="loading"
        :data="reports"
        stripe
        style="width: 100%"
      >
        <el-table-column
          prop="title"
          label="报告名称"
          min-width="220"
        />

        <el-table-column
          label="统计周期"
          min-width="220"
        >
          <template #default="{ row }">
            {{ formatDate(row.periodStart) }}
            至
            {{ formatDate(row.periodEnd) }}
          </template>
        </el-table-column>

        <el-table-column
          prop="exerciseCount"
          label="练习次数"
          width="100"
          align="center"
        />

        <el-table-column
          label="客观题正确率"
          width="130"
          align="center"
        >
          <template #default="{ row }">
            {{ accuracyLabel(row.exerciseAccuracyRate) }}
          </template>
        </el-table-column>

        <el-table-column
          label="任务完成"
          width="110"
          align="center"
        >
          <template #default="{ row }">
            {{ completionLabel(row) }}
          </template>
        </el-table-column>

        <el-table-column
          prop="weakPointCount"
          label="薄弱知识点"
          width="110"
          align="center"
        />

        <el-table-column
          label="生成时间"
          min-width="160"
        >
          <template #default="{ row }">
            {{ formatDateTime(row.createdAt) }}
          </template>
        </el-table-column>

        <el-table-column
          label="操作"
          width="110"
          fixed="right"
        >
          <template #default="{ row }">
            <el-button
              link
              type="primary"
              @click="openDetail(row)"
            >
              查看报告
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-empty
        v-if="!loading && reports.length === 0"
        description="还没有学习报告，选择统计周期后生成第一份报告吧"
      />
    </BaseCard>

    <el-dialog
      v-model="detailVisible"
      title="阶段学习报告详情"
      width="850px"
      top="5vh"
      destroy-on-close
    >
      <el-skeleton
        v-if="detailLoading"
        :rows="8"
        animated
      />

      <template v-else-if="selectedReport">
        <div class="detail-title">
          <h3>{{ selectedReport.title }}</h3>

          <p>
            {{ selectedReport.courseName }}
            ·
            {{ formatDate(selectedReport.periodStart) }}
            至
            {{ formatDate(selectedReport.periodEnd) }}
          </p>
        </div>

        <el-row
          :gutter="12"
          class="detail-stats"
        >
          <el-col :span="6">
            <div class="detail-stat">
              <span>练习次数</span>
              <strong>{{ selectedReport.exerciseCount }}</strong>
            </div>
          </el-col>

          <el-col :span="6">
            <div class="detail-stat">
              <span>客观题正确率</span>
              <strong>
                {{ accuracyLabel(selectedReport.exerciseAccuracyRate) }}
              </strong>
            </div>
          </el-col>

          <el-col :span="6">
            <div class="detail-stat">
              <span>课程提问</span>
              <strong>{{ selectedReport.questionCount }}</strong>
            </div>
          </el-col>

          <el-col :span="6">
            <div class="detail-stat">
              <span>薄弱知识点</span>
              <strong>{{ selectedReport.weakPointCount }}</strong>
            </div>
          </el-col>
        </el-row>

        <div class="detail-section">
          <h4>学习任务完成情况</h4>

          <div class="completion-line">
            <span>
              {{ selectedReport.completedTaskCount }}
              /
              {{ selectedReport.totalTaskCount }}
            </span>

            <strong>
              {{ completionRate(selectedReport) }}%
            </strong>
          </div>

          <el-progress
            :percentage="completionRate(selectedReport)"
            :stroke-width="10"
          />
        </div>

        <div class="detail-section">
          <h4>阶段学习总结</h4>
          <div class="report-text">
            {{ selectedReport.reportSummary }}
          </div>
        </div>

        <div class="detail-section">
          <h4>薄弱知识点诊断</h4>

          <div
            v-if="selectedReport.weakPoints.length"
            class="weak-point-list"
          >
            <div
              v-for="point in selectedReport.weakPoints"
              :key="point.pointId"
              class="weak-point-item"
            >
              <div class="weak-point-heading">
                <strong>{{ point.title }}</strong>

                <el-tag
                  :type="diagnosisTagType(point.diagnosisLevel)"
                  size="small"
                >
                  {{ point.diagnosisLabel }}
                </el-tag>
              </div>

              <p>{{ point.diagnosisReason }}</p>

              <p class="suggestion">
                建议：{{ point.suggestion }}
              </p>

              <small>
                提问 {{ point.effectiveQuestionCount }} 次
                ·
                练习 {{ point.practiceQuestionCount }} 题
                ·
                掌握率
                {{
                  point.practiceMasteryRate === null
                    ? '--'
                    : `${point.practiceMasteryRate}%`
                }}
              </small>
            </div>
          </div>

          <el-empty
            v-else
            description="本次报告没有识别到明显薄弱知识点"
            :image-size="80"
          />
        </div>

        <div class="detail-section">
          <h4>阶段改进建议</h4>
          <div class="report-text suggestions">
            {{ selectedReport.improvementSuggestions }}
          </div>
        </div>

        <div class="detail-section">
          <h4>教师批注</h4>

          <div
            v-if="selectedReport.comments.length"
            class="comment-list"
          >
            <div
              v-for="comment in selectedReport.comments"
              :key="comment.id"
              class="comment-item"
            >
              <div class="comment-heading">
                <strong>{{ comment.teacherName }}</strong>
                <small>{{ formatDateTime(comment.createdAt) }}</small>
              </div>

              <p>{{ comment.content }}</p>
            </div>
          </div>

          <el-empty
            v-else
            description="教师暂未添加批注"
            :image-size="70"
          />
        </div>

        <p class="snapshot-tip">
          注：薄弱知识点采用报告生成时的诊断快照；提问依据为最近30天统计。
        </p>
      </template>

      <template #footer>
        <el-button @click="detailVisible = false">
          关闭
        </el-button>
      </template>
    </el-dialog>

  </div>
</template>

<style scoped>
.learning-report-page {
  display: flex;
  flex-direction: column;
  gap: 18px;
  min-width: 0;
}

.report-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 16px;
}

.report-eyebrow {
  color: var(--sc-primary);
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 2px;
}

.report-header h2 {
  margin-top: 7px;
  color: var(--sc-text);
  font-size: 22px;
}

.report-header p {
  margin-top: 7px;
  color: var(--sc-text-secondary);
  font-size: 13px;
}

.report-filter {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 12px;
}

.report-tip {
  margin-top: 13px;
  color: var(--sc-text-placeholder);
  font-size: 12px;
}

.report-overview {
  row-gap: 12px;
}

.overview-item {
  display: flex;
  flex-direction: column;
  gap: 9px;
  min-height: 92px;
}

.overview-item span {
  color: var(--sc-text-secondary);
  font-size: 13px;
}

.overview-item strong {
  color: var(--sc-text);
  font-size: 27px;
}

.overview-item small {
  color: var(--sc-text-placeholder);
  font-size: 12px;
}

.overview-item .course-name {
  overflow: hidden;
  font-size: 19px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.detail-title h3 {
  font-size: 19px;
  color: var(--sc-text);
}

.detail-title p {
  margin-top: 7px;
  color: var(--sc-text-secondary);
  font-size: 13px;
}

.detail-stats {
  margin-top: 20px;
}

.detail-stat {
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding: 15px;
  border-radius: 10px;
  background: var(--sc-surface-muted);
}

.detail-stat span {
  color: var(--sc-text-secondary);
  font-size: 12px;
}

.detail-stat strong {
  color: var(--sc-text);
  font-size: 23px;
}

.detail-section {
  margin-top: 25px;
}

.detail-section h4 {
  margin-bottom: 12px;
  color: var(--sc-text);
  font-size: 15px;
}

.completion-line {
  display: flex;
  justify-content: space-between;
  margin-bottom: 9px;
  color: var(--sc-text-secondary);
  font-size: 13px;
}

.report-text {
  padding: 15px;
  border-radius: 9px;
  background: var(--sc-surface-muted);
  color: var(--sc-text);
  font-size: 13px;
  line-height: 1.9;
  white-space: pre-line;
}

.suggestions {
  color: var(--sc-text-secondary);
}

.weak-point-list,
.comment-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.weak-point-item,
.comment-item {
  padding: 14px;
  border: 1px solid var(--sc-border);
  border-radius: 10px;
}

.weak-point-heading,
.comment-heading {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
}

.weak-point-heading strong,
.comment-heading strong {
  color: var(--sc-text);
  font-size: 14px;
}

.weak-point-item p,
.comment-item p {
  margin-top: 10px;
  color: var(--sc-text-secondary);
  font-size: 13px;
  line-height: 1.7;
  white-space: pre-line;
}

.weak-point-item .suggestion {
  color: var(--sc-primary);
}

.weak-point-item small,
.comment-heading small {
  display: block;
  margin-top: 9px;
  color: var(--sc-text-placeholder);
  font-size: 12px;
}

.snapshot-tip {
  margin-top: 20px;
  color: var(--sc-text-placeholder);
  font-size: 12px;
}

@media (max-width: 720px) {
  .report-header {
    align-items: flex-start;
    flex-direction: column;
  }

  .report-filter {
    align-items: stretch;
    flex-direction: column;
  }

  .report-filter > * {
    width: 100% !important;
  }

  .detail-stats .el-col {
    margin-bottom: 10px;
  }
}
</style>
