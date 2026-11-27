<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'

import {
  addTeacherLearningReportComment,
  getTeacherLearningReport,
  listTeacherLearningReports,
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
  name: 'TeacherLearningReportsView',
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
const detailLoading = ref(false)
const submitting = ref(false)

const detailVisible = ref(false)
const commentContent = ref('')

const totalReports = computed(() => reports.value.length)

const averageAccuracy = computed(() => {
  const values = reports.value
    .map(item => item.exerciseAccuracyRate)
    .filter((item): item is number => item !== null)

  if (values.length === 0) return null

  return Number(
    (
      values.reduce((sum, item) => sum + item, 0)
      / values.length
    ).toFixed(2),
  )
})

const totalWeakPoints = computed(() => {
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
  courseStore.select(selectedCourseId.value)

  selectedReport.value = null
  reports.value = []

  await loadReports()
}

async function loadReports(): Promise<void> {
  const courseId = selectedCourseId.value

  if (!courseId) {
    reports.value = []
    return
  }

  loading.value = true

  try {
    const result = await listTeacherLearningReports(courseId)

    if (courseId === selectedCourseId.value) {
      reports.value = result
    }
  } catch (error) {
    notifyError(error)
  } finally {
    loading.value = false
  }
}

async function openDetail(row: LearningReport): Promise<void> {
  const courseId = selectedCourseId.value

  if (!courseId) return

  detailVisible.value = true
  detailLoading.value = true
  selectedReport.value = null
  commentContent.value = ''

  try {
    selectedReport.value = await getTeacherLearningReport(
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

async function submitComment(): Promise<void> {
  const courseId = selectedCourseId.value
  const report = selectedReport.value
  const content = commentContent.value.trim()

  if (!courseId || !report) {
    return
  }

  if (!content) {
    notifyWarning('请输入批注内容')
    return
  }

  if (content.length > 2000) {
    notifyWarning('批注内容不能超过2000字')
    return
  }

  submitting.value = true

  try {
    await addTeacherLearningReportComment(
      courseId,
      report.id,
      { content },
    )

    // 重新查询报告详情，获取最新批注历史。
    selectedReport.value = await getTeacherLearningReport(
      courseId,
      report.id,
    )

    commentContent.value = ''

    notifySuccess('教师批注已保存')
  } catch (error) {
    notifyError(error)
  } finally {
    submitting.value = false
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
  <div class="teacher-report-page">

    <div class="teacher-report-header">
      <div>
        <div class="report-eyebrow">
          COURSE LEARNING REPORT
        </div>

        <h2>课程学习报告</h2>

        <p>
          查看学生阶段学习情况、薄弱知识点和改进建议，
          为学生提供针对性指导。
        </p>
      </div>

      <el-button
        type="primary"
        :loading="loading"
        @click="loadReports"
      >
        刷新数据
      </el-button>
    </div>

    <BaseCard title="课程筛选">
      <div class="course-filter">
        <el-select
          v-model="selectedCourseId"
          placeholder="请选择课程"
          style="width: 300px"
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
      </div>
    </BaseCard>

    <el-row
      :gutter="14"
      class="teacher-report-stats"
    >
      <el-col :xs="12" :sm="8">
        <BaseCard>
          <div class="teacher-stat">
            <span>学生报告数量</span>
            <strong>{{ totalReports }}</strong>
            <small>当前课程已生成的报告</small>
          </div>
        </BaseCard>
      </el-col>

      <el-col :xs="12" :sm="8">
        <BaseCard>
          <div class="teacher-stat">
            <span>报告平均客观题正确率</span>
            <strong>{{ accuracyLabel(averageAccuracy) }}</strong>
            <small>按报告计算的简单平均值</small>
          </div>
        </BaseCard>
      </el-col>

      <el-col :xs="12" :sm="8">
        <BaseCard>
          <div class="teacher-stat">
            <span>薄弱知识点记录</span>
            <strong>{{ totalWeakPoints }}</strong>
            <small>报告快照累计数量</small>
          </div>
        </BaseCard>
      </el-col>
    </el-row>

    <BaseCard title="学生阶段学习报告">
      <el-table
        v-loading="loading"
        :data="reports"
        stripe
        style="width: 100%"
      >
        <el-table-column
          prop="studentName"
          label="学生"
          min-width="120"
        />

        <el-table-column
          prop="title"
          label="报告名称"
          min-width="210"
        />

        <el-table-column
          label="统计周期"
          min-width="200"
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
          label="正确率"
          width="110"
          align="center"
        >
          <template #default="{ row }">
            {{ accuracyLabel(row.exerciseAccuracyRate) }}
          </template>
        </el-table-column>

        <el-table-column
          label="任务完成"
          width="120"
          align="center"
        >
          <template #default="{ row }">
            {{ row.completedTaskCount }}/{{ row.totalTaskCount }}
          </template>
        </el-table-column>

        <el-table-column
          prop="weakPointCount"
          label="薄弱知识点"
          width="110"
          align="center"
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
              @click="openDetail(row)"
            >
              查看并批注
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-empty
        v-if="!loading && reports.length === 0"
        description="当前课程还没有学生生成阶段学习报告"
      />
    </BaseCard>

    <el-dialog
      v-model="detailVisible"
      title="学生阶段学习报告"
      width="880px"
      top="4vh"
      destroy-on-close
    >
      <el-skeleton
        v-if="detailLoading"
        :rows="8"
        animated
      />

      <template v-else-if="selectedReport">

        <div class="student-report-title">
          <h3>{{ selectedReport.studentName }}</h3>

          <p>{{ selectedReport.title }}</p>

          <small>
            {{ selectedReport.courseName }}
            ·
            {{ formatDate(selectedReport.periodStart) }}
            至
            {{ formatDate(selectedReport.periodEnd) }}
          </small>
        </div>

        <el-row
          :gutter="12"
          class="report-detail-stats"
        >
          <el-col :span="6">
            <div class="report-detail-stat">
              <span>练习次数</span>
              <strong>{{ selectedReport.exerciseCount }}</strong>
            </div>
          </el-col>

          <el-col :span="6">
            <div class="report-detail-stat">
              <span>客观题正确率</span>
              <strong>
                {{ accuracyLabel(selectedReport.exerciseAccuracyRate) }}
              </strong>
            </div>
          </el-col>

          <el-col :span="6">
            <div class="report-detail-stat">
              <span>课程提问</span>
              <strong>{{ selectedReport.questionCount }}</strong>
            </div>
          </el-col>

          <el-col :span="6">
            <div class="report-detail-stat">
              <span>薄弱知识点</span>
              <strong>{{ selectedReport.weakPointCount }}</strong>
            </div>
          </el-col>
        </el-row>

        <div class="report-detail-section">
          <h4>任务完成情况</h4>

          <div class="progress-heading">
            <span>
              {{ selectedReport.completedTaskCount }}
              /
              {{ selectedReport.totalTaskCount }}
            </span>

            <strong>{{ completionRate(selectedReport) }}%</strong>
          </div>

          <el-progress
            :percentage="completionRate(selectedReport)"
            :stroke-width="10"
          />
        </div>

        <div class="report-detail-section">
          <h4>阶段学习总结</h4>
          <div class="report-content">
            {{ selectedReport.reportSummary }}
          </div>
        </div>

        <div class="report-detail-section">
          <h4>薄弱知识点诊断</h4>

          <div
            v-if="selectedReport.weakPoints.length"
            class="diagnosis-list"
          >
            <div
              v-for="point in selectedReport.weakPoints"
              :key="point.pointId"
              class="diagnosis-item"
            >
              <div class="diagnosis-heading">
                <strong>{{ point.title }}</strong>

                <el-tag
                  :type="diagnosisTagType(point.diagnosisLevel)"
                  size="small"
                >
                  {{ point.diagnosisLabel }}
                </el-tag>
              </div>

              <p>{{ point.diagnosisReason }}</p>

              <p class="diagnosis-suggestion">
                改进建议：{{ point.suggestion }}
              </p>

              <small>
                提问 {{ point.effectiveQuestionCount }} 次
                ·
                练习 {{ point.practiceQuestionCount }} 题
              </small>
            </div>
          </div>

          <el-empty
            v-else
            description="本阶段没有识别到明显薄弱知识点"
            :image-size="75"
          />
        </div>

        <div class="report-detail-section">
          <h4>阶段改进建议</h4>

          <div class="report-content">
            {{ selectedReport.improvementSuggestions }}
          </div>
        </div>

        <div class="report-detail-section">
          <h4>教师批注历史</h4>

          <div
            v-if="selectedReport.comments.length"
            class="comment-history"
          >
            <div
              v-for="comment in selectedReport.comments"
              :key="comment.id"
              class="comment-history-item"
            >
              <div class="comment-history-heading">
                <strong>{{ comment.teacherName }}</strong>
                <small>{{ formatDateTime(comment.createdAt) }}</small>
              </div>

              <p>{{ comment.content }}</p>
            </div>
          </div>

          <el-empty
            v-else
            description="暂时没有教师批注"
            :image-size="70"
          />
        </div>

        <div class="report-detail-section comment-editor">
          <h4>添加教师批注</h4>

          <el-input
            v-model="commentContent"
            type="textarea"
            :rows="4"
            maxlength="2000"
            show-word-limit
            resize="vertical"
            placeholder="请输入针对该学生本阶段学习情况的指导意见..."
          />

          <div class="comment-actions">
            <el-button
              type="primary"
              :loading="submitting"
              @click="submitComment"
            >
              保存批注
            </el-button>
          </div>
        </div>

        <p class="snapshot-tip">
          注：薄弱知识点采用报告生成时的诊断快照，提问依据为最近30天统计。
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
.teacher-report-page {
  display: flex;
  flex-direction: column;
  gap: 18px;
  min-width: 0;
}

.teacher-report-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}

.report-eyebrow {
  color: var(--sc-primary);
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 2px;
}

.teacher-report-header h2 {
  margin-top: 7px;
  color: var(--sc-text);
  font-size: 22px;
}

.teacher-report-header p {
  margin-top: 7px;
  color: var(--sc-text-secondary);
  font-size: 13px;
}

.course-filter {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
}

.teacher-report-stats {
  row-gap: 12px;
}

.teacher-stat {
  display: flex;
  flex-direction: column;
  gap: 9px;
  min-height: 90px;
}

.teacher-stat span {
  color: var(--sc-text-secondary);
  font-size: 13px;
}

.teacher-stat strong {
  color: var(--sc-text);
  font-size: 27px;
}

.teacher-stat small {
  color: var(--sc-text-placeholder);
  font-size: 12px;
}

.student-report-title h3 {
  color: var(--sc-text);
  font-size: 19px;
}

.student-report-title p {
  margin-top: 8px;
  color: var(--sc-text);
  font-size: 14px;
}

.student-report-title small {
  display: block;
  margin-top: 7px;
  color: var(--sc-text-placeholder);
  font-size: 12px;
}

.report-detail-stats {
  margin-top: 20px;
}

.report-detail-stat {
  display: flex;
  flex-direction: column;
  gap: 9px;
  padding: 14px;
  border-radius: 9px;
  background: var(--sc-surface-muted);
}

.report-detail-stat span {
  color: var(--sc-text-secondary);
  font-size: 12px;
}

.report-detail-stat strong {
  color: var(--sc-text);
  font-size: 23px;
}

.report-detail-section {
  margin-top: 24px;
}

.report-detail-section h4 {
  margin-bottom: 12px;
  color: var(--sc-text);
  font-size: 15px;
}

.progress-heading {
  display: flex;
  justify-content: space-between;
  margin-bottom: 9px;
  color: var(--sc-text-secondary);
  font-size: 13px;
}

.report-content {
  padding: 14px;
  border-radius: 9px;
  background: var(--sc-surface-muted);
  color: var(--sc-text);
  font-size: 13px;
  line-height: 1.9;
  white-space: pre-line;
}

.diagnosis-list,
.comment-history {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.diagnosis-item,
.comment-history-item {
  padding: 14px;
  border: 1px solid var(--sc-border);
  border-radius: 9px;
}

.diagnosis-heading,
.comment-history-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}

.diagnosis-heading strong,
.comment-history-heading strong {
  color: var(--sc-text);
  font-size: 14px;
}

.diagnosis-item p,
.comment-history-item p {
  margin-top: 10px;
  color: var(--sc-text-secondary);
  font-size: 13px;
  line-height: 1.7;
  white-space: pre-line;
}

.diagnosis-item .diagnosis-suggestion {
  color: var(--sc-primary);
}

.diagnosis-item small,
.comment-history-heading small {
  display: block;
  margin-top: 9px;
  color: var(--sc-text-placeholder);
  font-size: 12px;
}

.comment-actions {
  display: flex;
  justify-content: flex-end;
  margin-top: 12px;
}

.snapshot-tip {
  margin-top: 20px;
  color: var(--sc-text-placeholder);
  font-size: 12px;
}

@media (max-width: 720px) {
  .teacher-report-header {
    align-items: flex-start;
    flex-direction: column;
  }

  .course-filter > * {
    width: 100% !important;
  }

  .report-detail-stats .el-col {
    margin-bottom: 10px;
  }
}
</style>
