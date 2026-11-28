<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'

import {
  getTeacherCourseLearningAnalysis,
  getTeacherStudentWeakPointDiagnosis,
} from '@/api/teacher-learning-analysis'

import type {
  StudentProgress,
  TeacherCourseLearningAnalysis,
} from '@/api/teacher-learning-analysis'

import type {
  StudentWeakPointDiagnostic,
} from '@/api/learning-path'

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

import BaseCard from '@/components/ui/BaseCard.vue'
import { useCourseStore } from '@/stores/course'
import { notifyError, notifySuccess, notifyWarning } from '@/utils/notify'

defineOptions({
  name: 'TeacherCourseLearningAnalysisView',
})

const router = useRouter()

const courseStore = useCourseStore()

const courses = ref<Course[]>([])

const selectedCourseId = ref<BigIntId | null>(
  courseStore.selectedCourseId,
)

const analysis = ref<TeacherCourseLearningAnalysis | null>(null)

const loadingCourses = ref(false)
const loading = ref(false)

// 学生个人详情
const studentDetailVisible = ref(false)
const studentDetailLoading = ref(false)

const selectedStudent = ref<StudentProgress | null>(null)

const studentWeakPoints = ref<StudentWeakPointDiagnostic[]>([])
const studentReports = ref<LearningReport[]>([])

// 阶段报告详情
const reportDetailVisible = ref(false)
const reportDetailLoading = ref(false)

const selectedReport = ref<LearningReportDetail | null>(null)

const commentContent = ref('')
const commentSubmitting = ref(false)

const studentKeyword = ref('')

const studentStatusFilter = ref<
  'ALL' | 'ATTENTION' | 'NO_DATA' | 'NORMAL'
>('ALL')

const filteredStudents = computed(() => {
  const students = analysis.value?.students ?? []

  const keyword = studentKeyword.value.trim().toLowerCase()

  return students.filter(student => {

    const matchesKeyword =
      !keyword
      || student.studentName.toLowerCase().includes(keyword)
      || student.username.toLowerCase().includes(keyword)

    const matchesStatus =
      studentStatusFilter.value === 'ALL'
      || student.learningStatus === studentStatusFilter.value

    return matchesKeyword && matchesStatus
  })
})

const lowCompletionCount = computed(
  () => analysis.value?.lowCompletionChapters.length ?? 0,
)

const attentionStudentCount = computed(() => {
  return (analysis.value?.students ?? []).filter(
    student => student.learningStatus === 'ATTENTION',
  ).length
})

const noDataStudentCount = computed(() => {
  return (analysis.value?.students ?? []).filter(
    student => student.learningStatus === 'NO_DATA',
  ).length
})

const materialUsageRate = computed(() => {
  const summary = analysis.value?.materialSummary

  if (!summary || summary.documentCount === 0) {
    return null
  }

  return Math.round(
    summary.accessedDocumentCount
      / summary.documentCount
      * 100,
  )
})

onMounted(() => {
  void loadCourses()
})

async function loadCourses(): Promise<void> {
  loadingCourses.value = true

  try {
    courses.value = await listAccessibleCourses()

    const currentExists = courses.value.some(
      item => item.id === selectedCourseId.value,
    )

    if (!currentExists) {
      selectedCourseId.value = courses.value[0]?.id ?? null
    }

    if (selectedCourseId.value) {
      courseStore.select(selectedCourseId.value)
      await loadAnalysis()
    } else {
      analysis.value = null
    }
  } catch (error) {
    notifyError(error)
  } finally {
    loadingCourses.value = false
  }
}

async function handleCourseChange(): Promise<void> {
  courseStore.select(selectedCourseId.value)
  await loadAnalysis()
}

async function loadAnalysis(): Promise<void> {
  const courseId = selectedCourseId.value

  if (!courseId) {
    analysis.value = null
    return
  }

  loading.value = true

  try {
    const result = await getTeacherCourseLearningAnalysis(courseId)

    if (courseId === selectedCourseId.value) {
      analysis.value = result
    }
  } catch (error) {
    analysis.value = null
    notifyError(error)
  } finally {
    loading.value = false
  }
}

/**
 * 跳转到教师问答分析，并筛选指定知识点。
 */
function openKnowledgePointQuestions(
  pointId: string | number,
): void {
  if (!analysis.value) {
    return
  }

  void router.push({
    name: 'teacher-questions',
    query: {
      courseId: String(analysis.value.courseId),
      pointId: String(pointId),
    },
  })
}

function formatRate(value: number | null): string {
  return value === null ? '--' : `${value.toFixed(2)}%`
}

function formatSelfAssessment(
  value: number | null,
): string {
  return value === null
    ? '--'
    : `${value.toFixed(2)} / 3`
}

function learningStatusLabel(
  status: 'NORMAL' | 'ATTENTION' | 'NO_DATA',
): string {
  switch (status) {
    case 'ATTENTION':
      return '需要关注'

    case 'NO_DATA':
      return '暂无记录'

    default:
      return '正常'
  }
}

function learningStatusType(
  status: 'NORMAL' | 'ATTENTION' | 'NO_DATA',
): 'success' | 'warning' | 'info' {
  switch (status) {
    case 'ATTENTION':
      return 'warning'

    case 'NO_DATA':
      return 'info'

    default:
      return 'success'
  }
}

function formatDateTime(value: string | null): string {
  if (!value) return '暂无访问记录'

  return value.replace('T', ' ').slice(0, 16)
}

function resourceTypeLabel(value: string | null): string {
  if (!value) return '未分类'

  const labels: Record<string, string> = {
    LESSON_PLAN: '教案',
    COURSEWARE: '课件',
    EXERCISE: '习题',
    PRACTICAL_GUIDE: '实训指导',
    OTHER: '其他资料',
  }

  return labels[value] ?? value
}

function progressStatus(
  value: number | null,
): 'success' | 'warning' | 'exception' {

  if (value === null) return 'warning'
  if (value < 40) return 'exception'
  if (value < 60) return 'warning'

  return 'success'
}

function formatLearningTime(value: string | null): string {
  return value
    ? value.replace('T', ' ').slice(0, 16)
    : '暂无学习记录'
}

function formatReportPeriod(report: LearningReport): string {
  return `${report.periodStart} 至 ${report.periodEnd}`
}

function diagnosisTagType(
  level: number,
): 'danger' | 'warning' | 'success' {
  if (level === 0) return 'danger'
  if (level === 1) return 'warning'
  return 'success'
}

/**
 * 点击学生，加载该学生的诊断和报告历史。
 */
async function openStudentDetail(
  student: StudentProgress,
): Promise<void> {

  const courseId = selectedCourseId.value

  if (!courseId) return

  selectedStudent.value = student

  studentDetailVisible.value = true
  studentDetailLoading.value = true

  studentWeakPoints.value = []
  studentReports.value = []

  try {
    try {
      const diagnosis =
        await getTeacherStudentWeakPointDiagnosis(
          courseId,
          student.studentId,
        )

      if (
        String(selectedStudent.value?.studentId)
        !== String(student.studentId)
      ) {
        return
      }

      studentWeakPoints.value = diagnosis

    } catch (error) {
      notifyError(error)
    }

    if (
      String(selectedStudent.value?.studentId)
      !== String(student.studentId)
    ) {
      return
    }

    try {
      const reports =
        await listTeacherLearningReports(courseId)

      if (
        String(selectedStudent.value?.studentId)
        !== String(student.studentId)
      ) {
        return
      }

      studentReports.value = reports
        .filter(
          item => String(item.studentId)
            === String(student.studentId),
        )
        .sort(
          (a, b) => b.createdAt.localeCompare(a.createdAt),
        )

    } catch (error) {
      notifyError(error)
    }

  } finally {
    if (
      String(selectedStudent.value?.studentId)
      === String(student.studentId)
    ) {
      studentDetailLoading.value = false
    }
  }
}

/**
 * 查看已有阶段报告。
 */
async function openStudentReport(
  report: LearningReport,
): Promise<void> {

  const courseId = selectedCourseId.value

  if (!courseId) return

  reportDetailVisible.value = true
  reportDetailLoading.value = true

  selectedReport.value = null
  commentContent.value = ''

  try {
    selectedReport.value = await getTeacherLearningReport(
      courseId,
      report.id,
    )

  } catch (error) {
    reportDetailVisible.value = false
    notifyError(error)

  } finally {
    reportDetailLoading.value = false
  }
}

/**
 * 教师添加阶段报告批注。
 */
async function submitStudentReportComment(): Promise<void> {

  const courseId = selectedCourseId.value
  const report = selectedReport.value
  const content = commentContent.value.trim()

  if (!courseId || !report) return

  if (!content) {
    notifyWarning('请输入批注内容')
    return
  }

  if (content.length > 2000) {
    notifyWarning('批注内容不能超过2000字')
    return
  }

  commentSubmitting.value = true

  try {
    await addTeacherLearningReportComment(
      courseId,
      report.id,
      { content },
    )

    // 重新获取详情，刷新批注历史
    selectedReport.value = await getTeacherLearningReport(
      courseId,
      report.id,
    )

    commentContent.value = ''

    notifySuccess('教师批注已保存')

  } catch (error) {
    notifyError(error)

  } finally {
    commentSubmitting.value = false
  }
}
</script>

<template>
  <div class="course-analysis-page">

    <!-- 页面标题 -->
    <div class="page-header">
      <div>
        <div class="page-eyebrow">
          COURSE LEARNING ANALYSIS
        </div>

        <h2>课程学习分析</h2>

        <p>
          查看学生综合学习进度、课程资料使用情况和章节练习覆盖率，
          及时发现学习参与不足的学生和章节。
        </p>
      </div>

      <el-button
        type="primary"
        :loading="loading"
        :disabled="!selectedCourseId"
        @click="loadAnalysis"
      >
        刷新数据
      </el-button>
    </div>

    <!-- 课程筛选 -->
    <BaseCard title="课程筛选">
      <el-select
        v-model="selectedCourseId"
        placeholder="请选择课程"
        style="width: 320px"
        :loading="loadingCourses"
        :disabled="loadingCourses || courses.length === 0"
        @change="handleCourseChange"
      >
        <el-option
          v-for="course in courses"
          :key="course.id"
          :label="course.name"
          :value="course.id"
        />
      </el-select>
    </BaseCard>

    <el-skeleton
      v-if="loading && !analysis"
      :rows="8"
      animated
    />

    <template v-else-if="analysis">

      <!-- 一、数据概况 -->
      <div class="overview-grid">

        <BaseCard>
          <div class="overview-item">
            <span>课程学生人数</span>
            <strong>{{ analysis.studentCount }}</strong>
            <small>当前加入课程的学生</small>
          </div>
        </BaseCard>

        <BaseCard>
          <div class="overview-item">
            <span>任务完成率</span>

            <strong>
              {{ formatRate(analysis.taskCompletionRate) }}
            </strong>

            <small>
              已完成 {{ analysis.completedTaskAssignmentCount }}
              /
              {{ analysis.taskAssignmentCount }} 人次任务
            </small>
          </div>
        </BaseCard>

        <BaseCard>
          <div class="overview-item">
            <span>练习参与学生</span>
            <strong>{{ analysis.participatingStudentCount }}</strong>

            <small>
              已发布 {{ analysis.publishedExerciseCount }} 份练习，
              已提交 {{ analysis.submittedAttemptCount }} 次
            </small>
          </div>
        </BaseCard>

        <BaseCard>
          <div class="overview-item">
            <span>资料使用覆盖率</span>

            <strong>
              {{
                materialUsageRate === null
                  ? '--'
                  : `${materialUsageRate}%`
              }}
            </strong>

            <small>
              {{ analysis.materialSummary.accessedDocumentCount }}
              /
              {{ analysis.materialSummary.documentCount }}
              份资料有学生访问
            </small>
          </div>
        </BaseCard>

      </div>

      <!-- 二、班级整体进度 -->
      <BaseCard title="班级学习进度">

        <div class="progress-overview">

          <div class="progress-card">

            <div class="progress-card__heading">
              <span>课程任务完成情况</span>

              <strong>
                {{ formatRate(analysis.taskCompletionRate) }}
              </strong>
            </div>

            <el-progress
              :percentage="analysis.taskCompletionRate ?? 0"
              :status="progressStatus(analysis.taskCompletionRate)"
              :stroke-width="12"
            />

            <small>
              {{ analysis.activeTaskCount }} 项有效任务，
              {{ analysis.studentCount }} 名在课学生
            </small>

          </div>

          <div class="progress-card">

            <div class="progress-card__heading">
              <span>练习参与情况</span>

              <strong>
                {{ formatRate(analysis.exerciseParticipationRate) }}
              </strong>
            </div>

            <el-progress
              :percentage="analysis.exerciseParticipationRate ?? 0"
              :status="progressStatus(analysis.exerciseParticipationRate)"
              :stroke-width="12"
            />

            <small>
              {{ analysis.participatingStudentCount }}
              名学生至少提交过一次练习
            </small>

          </div>

        </div>

      </BaseCard>

      <!-- 三、学生综合学习情况 -->
      <BaseCard title="学生综合学习情况">

        <div class="student-table-toolbar">

          <div class="student-table-description">

            <div>
              查看每名学生的练习、提问、任务完成和资料使用情况。
            </div>

            <div class="student-risk-summary">

              <el-tag type="warning" size="small">
                需要关注 {{ attentionStudentCount }} 人
              </el-tag>

              <el-tag type="info" size="small">
                暂无记录 {{ noDataStudentCount }} 人
              </el-tag>

            </div>

          </div>

          <div class="student-table-actions">

            <el-select
              v-model="studentStatusFilter"
              placeholder="学习状态"
              style="width: 140px"
            >
              <el-option label="全部学生" value="ALL" />
              <el-option label="需要关注" value="ATTENTION" />
              <el-option label="暂无记录" value="NO_DATA" />
              <el-option label="学习正常" value="NORMAL" />
            </el-select>

            <el-input
              v-model="studentKeyword"
              placeholder="搜索学生姓名或账号"
              clearable
              style="width: 220px"
            />

          </div>

        </div>

        <el-table
          :data="filteredStudents"
          stripe
          style="width: 100%"
          row-key="studentId"
        >

          <el-table-column
            label="学生"
            min-width="130"
            fixed="left"
          >
            <template #default="{ row }">
              <div class="student-name-cell">
                <strong>{{ row.studentName }}</strong>
                <small>{{ row.username }}</small>
              </div>
            </template>
          </el-table-column>

          <el-table-column
            label="学习状态"
            width="125"
            align="center"
          >
            <template #default="{ row }">

              <el-tooltip
                v-if="row.statusReasons.length > 0"
                :content="row.statusReasons.join('；')"
                placement="top"
              >
                <el-tag
                  :type="learningStatusType(row.learningStatus)"
                  size="small"
                >
                  {{ learningStatusLabel(row.learningStatus) }}
                </el-tag>
              </el-tooltip>

              <el-tag
                v-else
                :type="learningStatusType(row.learningStatus)"
                size="small"
              >
                {{ learningStatusLabel(row.learningStatus) }}
              </el-tag>

            </template>
          </el-table-column>

          <el-table-column
            label="提交练习"
            width="100"
            align="center"
          >
            <template #default="{ row }">
              {{ row.submittedAttemptCount }}
            </template>
          </el-table-column>

          <el-table-column
            label="客观题正确率"
            width="130"
            align="center"
          >
            <template #default="{ row }">
              {{ formatRate(row.exerciseAccuracyRate) }}
            </template>
          </el-table-column>

          <el-table-column
            label="课程提问"
            width="100"
            align="center"
          >
            <template #default="{ row }">
              {{ row.questionCount }}
            </template>
          </el-table-column>

          <el-table-column
            label="任务完成"
            width="110"
            align="center"
          >
            <template #default="{ row }">
              {{ row.completedTaskCount }}/{{ row.totalTaskCount }}
            </template>
          </el-table-column>

          <el-table-column
            label="任务完成率"
            min-width="160"
          >
            <template #default="{ row }">

              <div class="student-progress-cell">

                <span>
                  {{ formatRate(row.taskCompletionRate) }}
                </span>

                <el-progress
                  :percentage="row.taskCompletionRate ?? 0"
                  :status="progressStatus(row.taskCompletionRate)"
                  :show-text="false"
                  :stroke-width="7"
                />

              </div>

            </template>
          </el-table-column>

          <el-table-column
            label="资料访问"
            width="100"
            align="center"
          >
            <template #default="{ row }">
              {{ row.materialAccessCount }} 次
            </template>
          </el-table-column>

          <el-table-column
            label="最近学习时间"
            min-width="165"
          >
            <template #default="{ row }">
              {{ formatDateTime(row.lastActiveAt) }}
            </template>
          </el-table-column>

          <el-table-column
            label="操作"
            width="110"
            fixed="right"
            align="center"
          >
            <template #default="{ row }">
              <el-button
                type="primary"
                link
                @click.stop="openStudentDetail(row)"
              >
                查看详情
              </el-button>
            </template>
          </el-table-column>

        </el-table>

        <el-empty
          v-if="analysis.students.length === 0"
          description="当前课程暂无有效学生"
          :image-size="75"
        />

        <el-empty
          v-else-if="filteredStudents.length === 0"
          description="没有找到符合条件的学生"
          :image-size="75"
        />

        <p class="student-table-note">
          预警规则：任务完成率或客观题正确率低于60%，
          或连续14天没有学习活动。
          没有可统计学习活动的学生单独标记为暂无记录。
          正确率仅统计已判分客观题。
        </p>

      </BaseCard>

      <!-- 四、低完成率章节 -->
      <BaseCard>

        <template #header>
          <div class="section-heading">
            <div>
              <h3>低完成率章节</h3>
              <p>
                章节练习覆盖率低于 60% 的章节需要重点关注。
              </p>
            </div>

            <el-tag
              :type="lowCompletionCount > 0 ? 'warning' : 'success'"
            >
              {{ lowCompletionCount }} 个待关注章节
            </el-tag>
          </div>
        </template>

        <el-table
          :data="analysis.lowCompletionChapters"
          stripe
          style="width: 100%"
        >

          <el-table-column
            prop="chapterName"
            label="章节名称"
            min-width="180"
          />

          <el-table-column
            label="练习题数"
            width="100"
            align="center"
          >
            <template #default="{ row }">
              {{ row.questionCount }}
            </template>
          </el-table-column>

          <el-table-column
            label="已完成学生-题目数"
            min-width="160"
            align="center"
          >
            <template #default="{ row }">
              {{ row.completedStudentQuestionCount }}
              /
              {{ row.totalExpectedAnswers }}
            </template>
          </el-table-column>

          <el-table-column
            label="练习覆盖率"
            min-width="180"
          >
            <template #default="{ row }">
              <div class="rate-cell">

                <span>
                  {{ formatRate(row.completionRate) }}
                </span>

                <el-progress
                  :percentage="row.completionRate ?? 0"
                  :status="progressStatus(row.completionRate)"
                  :show-text="false"
                  :stroke-width="8"
                />

              </div>
            </template>
          </el-table-column>

          <el-table-column
            label="状态"
            width="110"
            align="center"
          >
            <template #default>
              <el-tag type="warning" size="small">
                需要关注
              </el-tag>
            </template>
          </el-table-column>

        </el-table>

        <el-empty
          v-if="analysis.lowCompletionChapters.length === 0"
          description="目前没有练习覆盖率低于 60% 的章节"
          :image-size="80"
        />

      </BaseCard>

      <!-- 五、知识点综合学习情况 -->
      <BaseCard>

        <template #header>
          <div class="section-heading">
            <div>
              <h3>知识点综合学习情况</h3>
              <p>
                综合学生提问、练习表现和自评数据，识别需要重点关注的知识点。
              </p>
            </div>
          </div>
        </template>

        <el-table
          :data="analysis.knowledgePoints"
          stripe
          style="width: 100%"
          row-key="pointId"
        >

          <el-table-column
            prop="title"
            label="知识点"
            min-width="160"
            fixed="left"
          />

          <el-table-column
            label="提问次数"
            width="100"
            align="center"
          >
            <template #default="{ row }">
              {{ row.questionCount }}
            </template>
          </el-table-column>

          <el-table-column
            label="提问学生"
            width="110"
            align="center"
          >
            <template #default="{ row }">
              {{ row.questionStudentCount }}
            </template>
          </el-table-column>

          <el-table-column
            label="练习记录"
            width="110"
            align="center"
          >
            <template #default="{ row }">
              {{ row.practiceQuestionCount }}
            </template>
          </el-table-column>

          <el-table-column
            label="掌握题数"
            width="100"
            align="center"
          >
            <template #default="{ row }">
              {{ row.masteredQuestionCount }}
            </template>
          </el-table-column>

          <el-table-column
            label="练习掌握率"
            min-width="165"
          >
            <template #default="{ row }">

              <div class="rate-cell">

                <span>
                  {{ formatRate(row.practiceMasteryRate) }}
                </span>

                <el-progress
                  v-if="row.practiceMasteryRate !== null"
                  :percentage="row.practiceMasteryRate"
                  :status="progressStatus(row.practiceMasteryRate)"
                  :show-text="false"
                  :stroke-width="8"
                />

                <span v-else class="empty-rate">
                  暂无练习数据
                </span>

              </div>

            </template>
          </el-table-column>

          <el-table-column
            label="练习学生"
            width="100"
            align="center"
          >
            <template #default="{ row }">
              {{ row.practiceStudentCount }}
            </template>
          </el-table-column>

          <el-table-column
            label="自评人数"
            width="100"
            align="center"
          >
            <template #default="{ row }">
              {{ row.selfAssessmentCount }}
            </template>
          </el-table-column>

          <el-table-column
            label="平均自评等级"
            width="130"
            align="center"
          >
            <template #default="{ row }">
              {{ formatSelfAssessment(row.averageSelfAssessmentLevel) }}
            </template>
          </el-table-column>

          <el-table-column
            label="最近提问"
            min-width="160"
          >
            <template #default="{ row }">
              {{ formatDateTime(row.latestAskedAt) }}
            </template>
          </el-table-column>

          <el-table-column
            label="最近练习"
            min-width="160"
          >
            <template #default="{ row }">
              {{ formatDateTime(row.lastPracticedAt) }}
            </template>
          </el-table-column>

          <el-table-column
            label="学习情况"
            width="110"
            fixed="right"
            align="center"
          >
            <template #default="{ row }">

              <el-tag
                v-if="
                  row.practiceMasteryRate !== null
                  && row.practiceMasteryRate < 60
                "
                type="warning"
                size="small"
              >
                需要巩固
              </el-tag>

              <el-tag
                v-else-if="row.practiceMasteryRate !== null"
                type="success"
                size="small"
              >
                练习正常
              </el-tag>

              <el-tag
                v-else
                type="info"
                size="small"
              >
                暂无练习
              </el-tag>

            </template>
          </el-table-column>

          <el-table-column
            label="操作"
            width="110"
            fixed="right"
            align="center"
          >
            <template #default="{ row }">
              <el-button
                text
                type="primary"
                @click.stop="openKnowledgePointQuestions(row.pointId)"
              >
                查看提问
              </el-button>
            </template>
          </el-table-column>

        </el-table>

        <el-empty
          v-if="analysis.knowledgePoints.length === 0"
          description="当前课程尚未建立知识点体系"
          :image-size="80"
        />

        <p class="tracking-note">
          练习掌握率按每名学生每道题的最近一次练习结果计算；
          提问次数仅统计成功关联知识点的课程普通提问；
          自评等级范围为 0～3，分别代表薄弱、不熟练、一般、熟练。
        </p>

      </BaseCard>

      <!-- 六、全部章节情况 -->
      <BaseCard title="全部章节练习情况">

        <el-table
          :data="analysis.chapters"
          stripe
          style="width: 100%"
        >

          <el-table-column
            prop="chapterName"
            label="章节名称"
            min-width="180"
          />

          <el-table-column
            prop="questionCount"
            label="已发布题目数"
            width="130"
            align="center"
          />

          <el-table-column
            prop="studentCount"
            label="学生人数"
            width="100"
            align="center"
          />

          <el-table-column
            label="已作答组合"
            min-width="150"
            align="center"
          >
            <template #default="{ row }">
              {{ row.completedStudentQuestionCount }}
              /
              {{ row.totalExpectedAnswers }}
            </template>
          </el-table-column>

          <el-table-column
            label="练习覆盖率"
            min-width="150"
            align="center"
          >
            <template #default="{ row }">
              {{ formatRate(row.completionRate) }}
            </template>
          </el-table-column>

          <el-table-column
            label="诊断"
            width="110"
            align="center"
          >
            <template #default="{ row }">

              <el-tag
                v-if="row.lowCompletion"
                type="warning"
                size="small"
              >
                低覆盖
              </el-tag>

              <el-tag
                v-else-if="row.completionRate !== null"
                type="success"
                size="small"
              >
                正常
              </el-tag>

              <el-tag
                v-else
                type="info"
                size="small"
              >
                暂无练习
              </el-tag>

            </template>
          </el-table-column>

        </el-table>

        <el-empty
          v-if="analysis.chapters.length === 0"
          description="当前课程还没有建立章节"
          :image-size="80"
        />

      </BaseCard>

      <!-- 七、课程资料使用情况 -->
      <BaseCard title="课程资料使用情况">

        <div class="material-summary">

          <div>
            <span>当前可用资料</span>
            <strong>{{ analysis.materialSummary.documentCount }}</strong>
          </div>

          <div>
            <span>预览次数</span>
            <strong>{{ analysis.materialSummary.previewCount }}</strong>
          </div>

          <div>
            <span>下载次数</span>
            <strong>{{ analysis.materialSummary.downloadCount }}</strong>
          </div>

          <div>
            <span>使用学生数</span>
            <strong>{{ analysis.materialSummary.activeStudentCount }}</strong>
          </div>

        </div>

        <el-table
          :data="analysis.materials"
          stripe
          style="width: 100%"
        >

          <el-table-column
            prop="documentName"
            label="资料名称"
            min-width="220"
            show-overflow-tooltip
          />

          <el-table-column
            label="资料类型"
            width="120"
            align="center"
          >
            <template #default="{ row }">
              {{ resourceTypeLabel(row.resourceType) }}
            </template>
          </el-table-column>

          <el-table-column
            label="所属章节"
            min-width="180"
            show-overflow-tooltip
          >
            <template #default="{ row }">
              {{ row.chapterNames || '未关联章节' }}
            </template>
          </el-table-column>

          <el-table-column
            prop="previewCount"
            label="预览次数"
            width="100"
            align="center"
          />

          <el-table-column
            prop="downloadCount"
            label="下载次数"
            width="100"
            align="center"
          />

          <el-table-column
            prop="studentCount"
            label="使用学生"
            width="100"
            align="center"
          />

          <el-table-column
            label="最近访问"
            min-width="160"
          >
            <template #default="{ row }">
              {{ formatDateTime(row.lastAccessAt) }}
            </template>
          </el-table-column>

        </el-table>

        <el-empty
          v-if="analysis.materials.length === 0"
          description="当前课程暂无已完成索引的生效资料"
          :image-size="80"
        />

        <p class="tracking-note">
          资料访问统计从启用本功能并执行数据库迁移后开始累计，
          不会补算此前的预览和下载行为；
          只有学生成功预览或下载才会计数。
        </p>

      </BaseCard>

      <div class="generated-at">
        数据统计时间：{{ formatDateTime(analysis.generatedAt) }}
      </div>

    </template>

    <el-empty
      v-else
      description="请选择课程查看学习分析，或检查当前账号是否拥有课程管理权限。"
      :image-size="100"
    />

    <!-- 学生个人学习详情 -->
    <el-dialog
      v-model="studentDetailVisible"
      :title="selectedStudent
        ? `${selectedStudent.studentName} - 学习详情`
        : '学生学习详情'"
      width="960px"
      top="4vh"
      destroy-on-close
    >
      <el-skeleton
        v-if="studentDetailLoading"
        :rows="7"
        animated
      />

      <template v-else-if="selectedStudent">

        <div class="student-detail-summary">

          <div>
            <span>学生账号</span>
            <strong>{{ selectedStudent.username }}</strong>
          </div>

          <div>
            <span>练习正确率</span>
            <strong>
              {{ formatRate(selectedStudent.exerciseAccuracyRate) }}
            </strong>
          </div>

          <div>
            <span>任务完成率</span>
            <strong>
              {{ formatRate(selectedStudent.taskCompletionRate) }}
            </strong>
          </div>

          <div>
            <span>课程提问</span>
            <strong>{{ selectedStudent.questionCount }}</strong>
          </div>

          <div>
            <span>资料访问</span>
            <strong>{{ selectedStudent.materialAccessCount }} 次</strong>
          </div>

          <div>
            <span>最近学习</span>
            <strong>
              {{ formatLearningTime(selectedStudent.lastActiveAt) }}
            </strong>
          </div>

        </div>

        <!-- F6-02 -->
        <section class="student-detail-section">

          <div class="student-detail-section-heading">
            <h3>薄弱知识点诊断</h3>
            <el-tag type="info" size="small">F6-02</el-tag>
          </div>

          <el-table
            :data="studentWeakPoints"
            stripe
            max-height="330"
            style="width: 100%"
          >

            <el-table-column
              prop="title"
              label="知识点"
              min-width="130"
            />

            <el-table-column
              label="有效提问"
              width="95"
              align="center"
            >
              <template #default="{ row }">
                {{ row.effectiveQuestionCount }}
              </template>
            </el-table-column>

            <el-table-column
              label="练习题数"
              width="95"
              align="center"
            >
              <template #default="{ row }">
                {{ row.practiceQuestionCount }}
              </template>
            </el-table-column>

            <el-table-column
              label="练习掌握率"
              width="115"
              align="center"
            >
              <template #default="{ row }">
                {{ formatRate(row.practiceMasteryRate) }}
              </template>
            </el-table-column>

            <el-table-column
              label="诊断"
              width="115"
              align="center"
            >
              <template #default="{ row }">
                <el-tag
                  :type="diagnosisTagType(row.diagnosisLevel)"
                  size="small"
                >
                  {{ row.diagnosisLabel }}
                </el-tag>
              </template>
            </el-table-column>

            <el-table-column
              label="诊断依据"
              min-width="230"
            >
              <template #default="{ row }">
                <el-tooltip
                  :content="row.diagnosisReason"
                  placement="top"
                >
                  <span class="diagnosis-reason-cell">
                    {{ row.diagnosisReason }}
                  </span>
                </el-tooltip>
              </template>
            </el-table-column>

            <el-table-column
              label="改进建议"
              min-width="230"
            >
              <template #default="{ row }">
                <span class="diagnosis-suggestion-cell">
                  {{ row.suggestion }}
                </span>
              </template>
            </el-table-column>

          </el-table>

          <el-empty
            v-if="studentWeakPoints.length === 0"
            description="该学生当前没有可展示的知识点诊断记录"
            :image-size="70"
          />

        </section>

        <!-- F6-03 -->
        <section class="student-detail-section">

          <div class="student-detail-section-heading">
            <h3>阶段学习报告</h3>
            <el-tag type="info" size="small">F6-03</el-tag>
          </div>

          <el-table
            :data="studentReports"
            stripe
            max-height="260"
            style="width: 100%"
          >

            <el-table-column
              prop="title"
              label="报告名称"
              min-width="180"
            />

            <el-table-column
              label="统计周期"
              min-width="190"
            >
              <template #default="{ row }">
                {{ formatReportPeriod(row) }}
              </template>
            </el-table-column>

            <el-table-column
              label="练习正确率"
              width="115"
              align="center"
            >
              <template #default="{ row }">
                {{ formatRate(row.exerciseAccuracyRate) }}
              </template>
            </el-table-column>

            <el-table-column
              prop="weakPointCount"
              label="薄弱点"
              width="85"
              align="center"
            />

            <el-table-column
              label="生成时间"
              min-width="155"
            >
              <template #default="{ row }">
                {{ formatDateTime(row.createdAt) }}
              </template>
            </el-table-column>

            <el-table-column
              label="操作"
              width="100"
              fixed="right"
            >
              <template #default="{ row }">
                <el-button
                  type="primary"
                  link
                  @click="openStudentReport(row)"
                >
                  查看报告
                </el-button>
              </template>
            </el-table-column>

          </el-table>

          <el-empty
            v-if="studentReports.length === 0"
            description="该学生尚未生成阶段学习报告，请学生在学生端生成后再查看"
            :image-size="70"
          />

        </section>

      </template>

      <template #footer>
        <el-button @click="studentDetailVisible = false">
          关闭
        </el-button>
      </template>
    </el-dialog>

    <!-- 阶段报告详情及教师批注 -->
    <el-dialog
      v-model="reportDetailVisible"
      title="阶段学习报告详情"
      width="850px"
      top="5vh"
      destroy-on-close
    >
      <el-skeleton
        v-if="reportDetailLoading"
        :rows="7"
        animated
      />

      <template v-else-if="selectedReport">

        <div class="report-detail-heading">
          <h3>{{ selectedReport.title }}</h3>

          <p>
            {{ selectedReport.studentName }}
            ·
            {{ selectedReport.courseName }}
          </p>

          <small>
            {{ formatReportPeriod(selectedReport) }}
          </small>
        </div>

        <div class="report-detail-metrics">

          <div>
            <span>练习次数</span>
            <strong>{{ selectedReport.exerciseCount }}</strong>
          </div>

          <div>
            <span>客观题正确率</span>
            <strong>
              {{ formatRate(selectedReport.exerciseAccuracyRate) }}
            </strong>
          </div>

          <div>
            <span>课程提问</span>
            <strong>{{ selectedReport.questionCount }}</strong>
          </div>

          <div>
            <span>任务完成</span>
            <strong>
              {{ selectedReport.completedTaskCount }}
              /
              {{ selectedReport.totalTaskCount }}
            </strong>
          </div>

        </div>

        <section class="student-detail-section">
          <h4>阶段学习总结</h4>

          <div class="report-detail-content">
            {{ selectedReport.reportSummary }}
          </div>
        </section>

        <section class="student-detail-section">
          <h4>薄弱知识点快照</h4>

          <el-table
            :data="selectedReport.weakPoints"
            stripe
            max-height="220"
            style="width: 100%"
          >
            <el-table-column
              prop="title"
              label="知识点"
              min-width="130"
            />

            <el-table-column
              prop="diagnosisLabel"
              label="诊断结果"
              width="120"
            />

            <el-table-column
              label="练习掌握率"
              width="115"
              align="center"
            >
              <template #default="{ row }">
                {{ formatRate(row.practiceMasteryRate) }}
              </template>
            </el-table-column>

            <el-table-column
              prop="diagnosisReason"
              label="诊断依据"
              min-width="230"
            />
          </el-table>

          <el-empty
            v-if="selectedReport.weakPoints.length === 0"
            description="报告没有薄弱知识点快照"
            :image-size="60"
          />
        </section>

        <section class="student-detail-section">
          <h4>阶段改进建议</h4>

          <div class="report-detail-content">
            {{ selectedReport.improvementSuggestions }}
          </div>
        </section>

        <section class="student-detail-section">
          <h4>教师批注历史</h4>

          <div
            v-if="selectedReport.comments.length"
            class="teacher-comment-list"
          >
            <div
              v-for="comment in selectedReport.comments"
              :key="comment.id"
              class="teacher-comment-item"
            >
              <div class="teacher-comment-heading">
                <strong>{{ comment.teacherName }}</strong>
                <small>
                  {{ formatDateTime(comment.createdAt) }}
                </small>
              </div>

              <p>{{ comment.content }}</p>
            </div>
          </div>

          <el-empty
            v-else
            description="暂无教师批注"
            :image-size="60"
          />
        </section>

        <section class="student-detail-section">
          <h4>添加教师批注</h4>

          <el-input
            v-model="commentContent"
            type="textarea"
            :rows="4"
            maxlength="2000"
            show-word-limit
            placeholder="请输入针对该学生阶段学习情况的指导意见"
          />

          <div class="report-comment-actions">
            <el-button
              type="primary"
              :loading="commentSubmitting"
              @click="submitStudentReportComment"
            >
              保存批注
            </el-button>
          </div>
        </section>

      </template>

      <template #footer>
        <el-button @click="reportDetailVisible = false">
          关闭
        </el-button>
      </template>
    </el-dialog>

  </div>
</template>

<style scoped>
.course-analysis-page {
  display: flex;
  flex-direction: column;
  gap: 16px;
  min-width: 0;
}

.page-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}

.page-eyebrow {
  color: var(--sc-primary);
  font-size: 10px;
  font-weight: 700;
  letter-spacing: 2px;
}

.page-header h2 {
  margin-top: 7px;
  color: var(--sc-text);
  font-size: 22px;
}

.page-header p {
  margin-top: 7px;
  color: var(--sc-text-secondary);
  font-size: 13px;
  line-height: 1.7;
}

.overview-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
}

.overview-item {
  display: flex;
  flex-direction: column;
  gap: 9px;
  min-height: 92px;
}

.overview-item span,
.material-summary span {
  color: var(--sc-text-secondary);
  font-size: 12px;
}

.overview-item strong,
.material-summary strong {
  color: var(--sc-text);
  font-size: 26px;
}

.overview-item small {
  color: var(--sc-text-placeholder);
  font-size: 11px;
  line-height: 1.5;
}

.progress-overview {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 16px;
}

.progress-card {
  padding: 16px;
  border-radius: 10px;
  background: var(--sc-surface-muted);
}

.progress-card__heading {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 14px;
  color: var(--sc-text-secondary);
  font-size: 13px;
}

.progress-card__heading strong {
  color: var(--sc-text);
}

.progress-card small {
  display: block;
  margin-top: 10px;
  color: var(--sc-text-placeholder);
  font-size: 12px;
}

.section-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  width: 100%;
}

.section-heading h3 {
  color: var(--sc-text);
  font-size: 15px;
}

.section-heading p {
  margin-top: 5px;
  color: var(--sc-text-secondary);
  font-size: 12px;
}

.rate-cell {
  display: flex;
  flex-direction: column;
  gap: 7px;
}

.empty-rate {
  color: var(--sc-text-placeholder);
  font-size: 11px;
}

.material-summary {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
  margin-bottom: 18px;
  padding: 15px;
  border-radius: 10px;
  background: var(--sc-surface-muted);
}

.material-summary > div {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.tracking-note {
  margin-top: 13px;
  color: var(--sc-text-placeholder);
  font-size: 12px;
  line-height: 1.7;
}

.generated-at {
  color: var(--sc-text-placeholder);
  font-size: 11px;
  text-align: right;
}

.student-table-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 12px;
  margin-bottom: 16px;
}

.student-table-description {
  color: var(--sc-text-secondary);
  font-size: 12px;
  line-height: 1.7;
}

.student-name-cell {
  display: flex;
  flex-direction: column;
  gap: 5px;
}

.student-name-cell strong {
  color: var(--sc-text);
  font-size: 13px;
}

.student-name-cell small {
  color: var(--sc-text-placeholder);
  font-size: 11px;
}

.student-progress-cell {
  display: flex;
  flex-direction: column;
  gap: 7px;
}

.student-progress-cell span {
  color: var(--sc-text-secondary);
  font-size: 12px;
}

.student-table-note {
  margin-top: 12px;
  color: var(--sc-text-placeholder);
  font-size: 11px;
  line-height: 1.7;
}

.student-risk-summary {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 9px;
}

.student-table-actions {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 10px;
}

@media (max-width: 640px) {
  .student-table-actions {
    width: 100%;
    flex-direction: column;
    align-items: stretch;
  }

  .student-table-actions .el-select,
  .student-table-actions .el-input {
    width: 100% !important;
  }
}

@media (max-width: 640px) {
  .student-table-toolbar {
    align-items: stretch;
    flex-direction: column;
  }

  .student-table-toolbar .el-input {
    width: 100% !important;
  }
}

@media (max-width: 900px) {
  .overview-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 640px) {
  .page-header {
    align-items: flex-start;
    flex-direction: column;
  }

  .page-header > .el-button {
    width: 100%;
  }

  .overview-grid,
  .progress-overview,
  .material-summary {
    grid-template-columns: 1fr;
  }
}

.student-detail-summary,
.report-detail-metrics {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 10px;
  margin-bottom: 20px;
}

.student-detail-summary > div,
.report-detail-metrics > div {
  display: flex;
  flex-direction: column;
  gap: 7px;
  padding: 12px;
  border-radius: 9px;
  background: var(--sc-surface-muted);
}

.student-detail-summary span,
.report-detail-metrics span {
  color: var(--sc-text-secondary);
  font-size: 11px;
}

.student-detail-summary strong,
.report-detail-metrics strong {
  color: var(--sc-text);
  font-size: 16px;
  overflow-wrap: anywhere;
}

.student-detail-section {
  margin-top: 22px;
}

.student-detail-section h3,
.student-detail-section h4 {
  margin-bottom: 12px;
  color: var(--sc-text);
  font-size: 15px;
}

.student-detail-section-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 12px;
}

.student-detail-section-heading h3 {
  margin-bottom: 0;
}

.diagnosis-reason-cell,
.diagnosis-suggestion-cell {
  display: -webkit-box;
  overflow: hidden;
  line-height: 1.6;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
}

.diagnosis-reason-cell {
  color: var(--sc-text-secondary);
}

.diagnosis-suggestion-cell {
  color: var(--sc-primary);
}

.report-detail-heading h3 {
  color: var(--sc-text);
  font-size: 18px;
}

.report-detail-heading p {
  margin-top: 8px;
  color: var(--sc-text-secondary);
  font-size: 13px;
}

.report-detail-heading small {
  display: block;
  margin-top: 6px;
  color: var(--sc-text-placeholder);
  font-size: 12px;
}

.report-detail-content {
  padding: 14px;
  border-radius: 9px;
  background: var(--sc-surface-muted);
  color: var(--sc-text);
  font-size: 13px;
  line-height: 1.8;
  white-space: pre-line;
}

.teacher-comment-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.teacher-comment-item {
  padding: 12px;
  border: 1px solid var(--sc-border);
  border-radius: 9px;
}

.teacher-comment-heading {
  display: flex;
  justify-content: space-between;
  gap: 12px;
}

.teacher-comment-heading strong {
  color: var(--sc-text);
  font-size: 13px;
}

.teacher-comment-heading small {
  color: var(--sc-text-placeholder);
  font-size: 11px;
}

.teacher-comment-item p {
  margin-top: 9px;
  color: var(--sc-text-secondary);
  font-size: 13px;
  line-height: 1.7;
  white-space: pre-line;
}

.report-comment-actions {
  display: flex;
  justify-content: flex-end;
  margin-top: 12px;
}

@media (max-width: 640px) {
  .student-detail-summary,
  .report-detail-metrics {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .teacher-comment-heading {
    flex-direction: column;
  }
}
</style>
