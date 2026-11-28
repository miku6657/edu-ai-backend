<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'

import {
  getTeacherCourseLearningAnalysis,
} from '@/api/teacher-learning-analysis'

import type {
  TeacherCourseLearningAnalysis,
} from '@/api/teacher-learning-analysis'

import { listAccessibleCourses } from '@/api/course-access'
import type { Course } from '@/api/course'
import type { BigIntId } from '@/api/types'

import BaseCard from '@/components/ui/BaseCard.vue'
import { useCourseStore } from '@/stores/course'
import { notifyError } from '@/utils/notify'

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

const studentKeyword = ref('')

const filteredStudents = computed(() => {
  const students = analysis.value?.students ?? []

  const keyword = studentKeyword.value.trim().toLowerCase()

  if (!keyword) {
    return students
  }

  return students.filter(student => {
    return (
      student.studentName.toLowerCase().includes(keyword)
      || student.username.toLowerCase().includes(keyword)
    )
  })
})

const lowCompletionCount = computed(
  () => analysis.value?.lowCompletionChapters.length ?? 0,
)

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
            查看每名学生的练习、提问、任务完成和资料使用情况。
          </div>

          <el-input
            v-model="studentKeyword"
            placeholder="搜索学生姓名或账号"
            clearable
            style="width: 260px"
          />

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
          说明：正确率只统计已判分客观题；没有练习或任务数据时显示“--”。
          资料访问次数从启用访问记录功能后开始累计。
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
</style>
