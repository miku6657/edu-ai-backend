<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'

import {
  getQuestionAnalyticsSummary,
  listHotQuestions,
  listKnowledgePointQuestionStats,
  listTeacherQuestionRecords,
} from '@/api/teacher-questions'
import type {
  HotQuestion,
  KnowledgePointQuestionStat,
  QuestionAnalyticsSummary,
  TeacherQuestionRecord,
} from '@/api/teacher-questions'
import { listAccessibleCourses } from '@/api/course-access'
import type { Course } from '@/api/course'
import { listKnowledgePoints } from '@/api/learning-path'
import type { KnowledgePoint } from '@/api/learning-path'
import type { BigIntId } from '@/api/types'
import BaseCard from '@/components/ui/BaseCard.vue'
import { useAuthStore } from '@/stores/auth'
import { useCourseStore } from '@/stores/course'
import { notifyError, notifyWarning } from '@/utils/notify'

const router = useRouter()
const auth = useAuthStore()
const courseStore = useCourseStore()
const canView = computed(
  () => auth.isTeacher || auth.isAdmin,
)

const courses = ref<Course[]>([])
const points = ref<KnowledgePoint[]>([])
const selectedCourseId = ref<BigIntId | null>(
  courseStore.selectedCourseId,
)
const selectedPointId = ref('')
const keyword = ref('')
const studentKeyword = ref('')
const dateRange = ref<[string, string] | null>(null)
const summary = ref<QuestionAnalyticsSummary | null>(null)
const hotQuestions = ref<HotQuestion[]>([])
const pointStats = ref<KnowledgePointQuestionStat[]>([])
const records = ref<TeacherQuestionRecord[]>([])
const selectedRecord = ref<TeacherQuestionRecord | null>(null)
const detailVisible = ref(false)
const loadingCourses = ref(false)
const loading = ref(false)
const loadingPoints = ref(false)
const page = ref(1)
const pageSize = 20
const total = ref(0)

const hasCourse = computed(
  () => selectedCourseId.value !== null,
)
const pendingCount = computed(
  () => summary.value?.pendingTickets ?? 0,
)
const answeredCount = computed(
  () => summary.value?.answeredTickets ?? 0,
)
const mappedRate = computed(() => {
  const value = summary.value
  if (!value || value.totalQuestions === 0) {
    return 0
  }
  return Math.round(
    value.mappedQuestions / value.totalQuestions * 100,
  )
})

function queryDates() {
  return {
    startDate: dateRange.value?.[0],
    endDate: dateRange.value?.[1],
  }
}

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
  } catch (error) {
    notifyError(error)
  } finally {
    loadingCourses.value = false
  }
}

async function loadPoints(): Promise<void> {
  const courseId = selectedCourseId.value
  if (!courseId) {
    points.value = []
    return
  }

  loadingPoints.value = true
  try {
    const result = await listKnowledgePoints(courseId)
    if (selectedCourseId.value === courseId) {
      points.value = result
    }
  } catch (error) {
    if (selectedCourseId.value === courseId) {
      points.value = []
      notifyError(error)
    }
  } finally {
    if (selectedCourseId.value === courseId) {
      loadingPoints.value = false
    }
  }
}

async function loadAnalytics(): Promise<void> {
  const courseId = selectedCourseId.value
  if (!courseId) {
    summary.value = null
    hotQuestions.value = []
    pointStats.value = []
    records.value = []
    total.value = 0
    return
  }

  loading.value = true
  try {
    const dates = queryDates()
    const pointId = selectedPointId.value || undefined
    const [
      summaryResult,
      hotResult,
      pointResult,
      recordResult,
    ] = await Promise.all([
      getQuestionAnalyticsSummary(courseId, dates),
      listHotQuestions(courseId, { ...dates, pointId }),
      listKnowledgePointQuestionStats(courseId, dates),
      listTeacherQuestionRecords(courseId, {
        ...dates,
        pointId,
        keyword: keyword.value.trim() || undefined,
        studentKeyword: studentKeyword.value.trim() || undefined,
        page: page.value,
        size: pageSize,
      }),
    ])

    if (courseId !== selectedCourseId.value) {
      return
    }

    summary.value = summaryResult
    hotQuestions.value = hotResult
    pointStats.value = pointResult
    records.value = recordResult.items
    total.value = recordResult.total
  } catch (error) {
    if (courseId === selectedCourseId.value) {
      notifyError(error)
    }
  } finally {
    if (courseId === selectedCourseId.value) {
      loading.value = false
    }
  }
}

async function handleCourseChange(): Promise<void> {
  courseStore.select(selectedCourseId.value)
  selectedPointId.value = ''
  page.value = 1
  await loadPoints()
  await loadAnalytics()
}

function handleSearch(): void {
  if (
    dateRange.value
    && dateRange.value[1] < dateRange.value[0]
  ) {
    notifyWarning('结束日期不能早于开始日期')
    return
  }
  page.value = 1
  void loadAnalytics()
}

function handleReset(): void {
  selectedPointId.value = ''
  keyword.value = ''
  studentKeyword.value = ''
  dateRange.value = null
  page.value = 1
  void loadAnalytics()
}

function handlePageChange(value: number): void {
  page.value = value
  void loadAnalytics()
}

function handlePointStatClick(
  row: KnowledgePointQuestionStat,
): void {
  selectedPointId.value = row.pointId
  page.value = 1
  void loadAnalytics()
}

function openRecord(row: TeacherQuestionRecord): void {
  selectedRecord.value = row
  detailVisible.value = true
}

function handlingLabel(
  status: TeacherQuestionRecord['handlingStatus'],
): string {
  switch (status) {
    case 'HUMAN_PENDING':
      return '待教师答复'
    case 'HUMAN_ANSWERED':
      return '教师已答复'
    default:
      return 'AI 已处理'
  }
}

function handlingType(
  status: TeacherQuestionRecord['handlingStatus'],
): 'warning' | 'success' | 'info' {
  switch (status) {
    case 'HUMAN_PENDING':
      return 'warning'
    case 'HUMAN_ANSWERED':
      return 'success'
    default:
      return 'info'
  }
}

function reasonLabel(reason: string | null): string {
  if (reason === 'NO_ACTIVE_DOCUMENTS') {
    return '课程暂无生效资料'
  }
  if (reason === 'NO_RETRIEVAL_MATCH') {
    return '知识库未检索到相关内容'
  }
  return reason || '无'
}

function goToTickets(): void {
  void router.push({ name: 'qa-tickets' })
}

function goToKnowledge(): void {
  void router.push({ name: 'knowledge' })
}

watch(selectedCourseId, () => {
  summary.value = null
  hotQuestions.value = []
  pointStats.value = []
  records.value = []
  total.value = 0
  selectedRecord.value = null
  detailVisible.value = false
})

onMounted(async () => {
  if (!canView.value) {
    return
  }
  await loadCourses()
  if (selectedCourseId.value) {
    courseStore.select(selectedCourseId.value)
    await loadPoints()
    await loadAnalytics()
  }
})
</script>

<template>
  <el-empty
    v-if="!canView"
    description="问答分析仅对教师和管理员开放"
  />

  <div
    v-else
    class="teacher-question-page"
  >
      <BaseCard
        title="课程问答分析"
        subtitle="查看学生提问、分析知识点薄弱环节和高频问题"
      >
        <div class="question-filters">
          <el-select
            v-model="selectedCourseId"
            placeholder="选择课程"
            :loading="loadingCourses"
            style="width: 220px"
            @change="handleCourseChange"
          >
            <el-option
              v-for="course in courses"
              :key="course.id"
              :label="course.name"
              :value="course.id"
            />
          </el-select>

          <el-select
            v-model="selectedPointId"
            placeholder="全部知识点"
            clearable
            filterable
            :loading="loadingPoints"
            style="width: 200px"
          >
            <el-option
              v-for="point in points"
              :key="point.id"
              :label="point.title"
              :value="point.id"
            />
          </el-select>

          <el-input
            v-model="keyword"
            placeholder="搜索学生问题"
            clearable
            style="width: 190px"
            @keyup.enter="handleSearch"
          />

          <el-input
            v-model="studentKeyword"
            placeholder="学生姓名或账号"
            clearable
            style="width: 170px"
            @keyup.enter="handleSearch"
          />

          <el-date-picker
            v-model="dateRange"
            type="daterange"
            value-format="YYYY-MM-DD"
            start-placeholder="开始日期"
            end-placeholder="结束日期"
            range-separator="至"
            clearable
          />

          <el-button
            type="primary"
            :loading="loading"
            @click="handleSearch"
          >
            查询
          </el-button>
          <el-button @click="handleReset">
            重置
          </el-button>
        </div>
      </BaseCard>

      <el-empty
        v-if="!hasCourse && !loadingCourses"
        description="当前没有可分析的课程"
      />

      <template v-if="hasCourse">
        <el-row
          :gutter="12"
          class="question-stat-cards"
        >
          <el-col :xs="12" :sm="8" :lg="4">
            <el-card shadow="never">
              <el-statistic
                title="学生提问总数"
                :value="summary?.totalQuestions ?? 0"
              />
            </el-card>
          </el-col>
          <el-col :xs="12" :sm="8" :lg="4">
            <el-card shadow="never">
              <el-statistic
                title="参与学生"
                :value="summary?.participatingStudents ?? 0"
              />
            </el-card>
          </el-col>
          <el-col :xs="12" :sm="8" :lg="4">
            <el-card shadow="never">
              <el-statistic
                title="知识点关联率"
                :value="mappedRate"
                suffix="%"
              />
            </el-card>
          </el-col>
          <el-col :xs="12" :sm="8" :lg="4">
            <el-card shadow="never">
              <el-statistic
                title="未分类问题"
                :value="summary?.unclassifiedQuestions ?? 0"
              />
            </el-card>
          </el-col>
          <el-col :xs="12" :sm="8" :lg="4">
            <el-card shadow="never">
              <el-statistic
                title="待人工答疑"
                :value="pendingCount"
              />
            </el-card>
          </el-col>
          <el-col :xs="12" :sm="8" :lg="4">
            <el-card shadow="never">
              <el-statistic
                title="已答复工单"
                :value="answeredCount"
              />
            </el-card>
          </el-col>
        </el-row>

        <el-row :gutter="16">
          <el-col :xs="24" :lg="12">
            <BaseCard
              title="高频问题"
              subtitle="相同问题被不同学生重复提出的情况"
            >
              <el-table
                v-loading="loading"
                :data="hotQuestions"
                empty-text="暂无重复出现的问题"
                size="small"
              >
                <el-table-column
                  prop="questionText"
                  label="问题内容"
                  min-width="220"
                  show-overflow-tooltip
                />
                <el-table-column
                  prop="knowledgePointTitle"
                  label="知识点"
                  min-width="110"
                />
                <el-table-column
                  prop="questionCount"
                  label="提问次数"
                  width="90"
                />
                <el-table-column
                  prop="studentCount"
                  label="涉及学生"
                  width="90"
                />
              </el-table>
            </BaseCard>
          </el-col>

          <el-col :xs="24" :lg="12">
            <BaseCard
              title="知识点提问热度"
              subtitle="根据已关联知识点的问题数量排序"
            >
              <el-table
                v-loading="loading"
                :data="pointStats"
                size="small"
                empty-text="暂无知识点统计"
                highlight-current-row
                @row-click="handlePointStatClick"
              >
                <el-table-column
                  prop="title"
                  label="知识点"
                  min-width="150"
                />
                <el-table-column
                  prop="questionCount"
                  label="提问次数"
                  width="90"
                />
                <el-table-column
                  prop="studentCount"
                  label="涉及学生"
                  width="90"
                />
                <el-table-column
                  label="操作"
                  width="90"
                >
                  <template #default>
                    <el-button
                      text
                      type="primary"
                    >
                      查看问题
                    </el-button>
                  </template>
                </el-table-column>
              </el-table>
            </BaseCard>
          </el-col>
        </el-row>

        <BaseCard
          title="学生提问记录"
          subtitle="支持按照知识点、学生、关键词和日期筛选"
        >
          <template #actions>
            <el-button
              type="primary"
              plain
              @click="goToTickets"
            >
              查看人工答疑
            </el-button>
            <el-button @click="goToKnowledge">
              前往知识库
            </el-button>
          </template>

          <el-table
            v-loading="loading"
            :data="records"
            row-key="messageId"
            empty-text="当前筛选条件下没有提问记录"
            @row-click="openRecord"
          >
            <el-table-column
              prop="studentName"
              label="学生"
              width="120"
            />
            <el-table-column
              prop="questionText"
              label="提问内容"
              min-width="240"
              show-overflow-tooltip
            />
            <el-table-column
              prop="knowledgePointTitle"
              label="所属知识点"
              min-width="140"
              show-overflow-tooltip
            >
              <template #default="{ row }">
                {{ row.knowledgePointTitle || '未分类' }}
              </template>
            </el-table-column>
            <el-table-column
              label="处理状态"
              width="125"
            >
              <template #default="{ row }">
                <el-tag :type="handlingType(row.handlingStatus)">
                  {{ handlingLabel(row.handlingStatus) }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column
              prop="askedAt"
              label="提问时间"
              min-width="175"
            />
            <el-table-column
              label="操作"
              width="90"
            >
              <template #default>
                <el-button
                  text
                  type="primary"
                >
                  查看详情
                </el-button>
              </template>
            </el-table-column>
          </el-table>

          <div class="question-pagination">
            <el-pagination
              :current-page="page"
              :page-size="pageSize"
              :total="total"
              layout="total, prev, pager, next"
              @current-change="handlePageChange"
            />
          </div>
        </BaseCard>
      </template>

      <el-dialog
        v-model="detailVisible"
        title="学生提问详情"
        width="650px"
      >
        <template v-if="selectedRecord">
          <el-descriptions
            :column="2"
            border
          >
            <el-descriptions-item label="学生">
              {{ selectedRecord.studentName }}
            </el-descriptions-item>
            <el-descriptions-item label="提问时间">
              {{ selectedRecord.askedAt }}
            </el-descriptions-item>
            <el-descriptions-item label="知识点">
              {{ selectedRecord.knowledgePointTitle || '未分类' }}
            </el-descriptions-item>
            <el-descriptions-item label="处理状态">
              {{ handlingLabel(selectedRecord.handlingStatus) }}
            </el-descriptions-item>
          </el-descriptions>

          <h4 class="detail-heading">
            学生问题
          </h4>
          <div class="question-detail">
            {{ selectedRecord.questionText }}
          </div>

          <template v-if="selectedRecord.aiAnswerPreview">
            <h4 class="detail-heading">
              AI 回答
            </h4>
            <div class="question-detail">
              {{ selectedRecord.aiAnswerPreview }}
            </div>
          </template>

          <template v-if="selectedRecord.ticketId">
            <h4 class="detail-heading">
              人工答疑
            </h4>
            <el-tag
              :type="selectedRecord.ticketStatus === 'PENDING'
                ? 'warning'
                : 'success'"
            >
              {{ selectedRecord.ticketStatus === 'PENDING'
                ? '等待教师答复'
                : '教师已答复' }}
            </el-tag>
            <div
              v-if="selectedRecord.teacherReply"
              class="question-detail"
            >
              {{ selectedRecord.teacherReply }}
            </div>
            <p class="reason-text">
              转人工原因：
              {{ reasonLabel(selectedRecord.noEvidenceReason) }}
            </p>
          </template>
        </template>

        <template #footer>
          <el-button
            v-if="selectedRecord?.ticketId"
            type="primary"
            @click="goToTickets"
          >
            进入工单管理
          </el-button>
          <el-button @click="detailVisible = false">
            关闭
          </el-button>
        </template>
      </el-dialog>
  </div>
</template>

<style scoped>
.teacher-question-page {
  display: flex;
  flex-direction: column;
  gap: 16px;
  min-width: 0;
}

.question-filters {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 10px;
}

.question-stat-cards {
  row-gap: 12px;
}

.question-pagination {
  display: flex;
  justify-content: flex-end;
  margin-top: 18px;
}

.detail-heading {
  margin: 20px 0 10px;
  color: var(--sc-text);
  font-size: 14px;
  font-weight: 600;
}

.question-detail {
  padding: 14px;
  border-radius: 8px;
  background: var(--sc-bg);
  color: var(--sc-text);
  line-height: 1.8;
  white-space: pre-wrap;
  overflow-wrap: anywhere;
}

.reason-text {
  margin-top: 10px;
  color: var(--sc-text-secondary);
  font-size: 13px;
}

@media (max-width: 700px) {
  .question-filters {
    align-items: stretch;
  }

  .question-filters > * {
    width: 100% !important;
  }
}
</style>
