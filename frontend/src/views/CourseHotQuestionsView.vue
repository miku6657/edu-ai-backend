<script setup lang="ts">
import {
  computed,
  onMounted,
  reactive,
  ref,
} from 'vue'

import { ElMessageBox } from 'element-plus'

import { useRoute } from 'vue-router'

import {
  listHotQuestions,
} from '@/api/teacher-questions'

import type {
  HotQuestion,
} from '@/api/teacher-questions'

import {
  createCourseHotQuestion,
  updateCourseHotQuestion,
  submitCourseHotQuestion,
  reviewCourseHotQuestion,
  offlineCourseHotQuestion,
  listManagedHotQuestions,
  listPublishedHotQuestions,
} from '@/api/course-hot-questions'

import type {
  CourseHotQuestion,
  CourseHotQuestionStatus,
  PublishedCourseHotQuestion,
} from '@/api/course-hot-questions'

import { listAccessibleCourses } from '@/api/course-access'
import type { Course } from '@/api/course'

import type { BigIntId } from '@/api/types'

import BaseCard from '@/components/ui/BaseCard.vue'

import { useAuthStore } from '@/stores/auth'
import { useCourseStore } from '@/stores/course'

import {
  notifyError,
  notifySuccess,
  notifyWarning,
} from '@/utils/notify'

const auth = useAuthStore()
const courseStore = useCourseStore()

const route = useRoute()

const courses = ref<Course[]>([])

const selectedCourseId = ref<BigIntId | null>(
  courseStore.selectedCourseId,
)

const loadingCourses = ref(false)
const loading = ref(false)
const saving = ref(false)

const candidates = ref<HotQuestion[]>([])
const managedItems = ref<CourseHotQuestion[]>([])
const publishedItems =
  ref<PublishedCourseHotQuestion[]>([])

const statusFilter = ref<CourseHotQuestionStatus | ''>('')

const searchKeyword = ref('')
const categoryFilter = ref('')

const dialogVisible = ref(false)
const reviewDialogVisible = ref(false)

const editingId = ref<BigIntId | null>(null)

const reviewingItem = ref<CourseHotQuestion | null>(null)

const reviewComment = ref('')

let requestVersion = 0

const canManage = computed(
  () => auth.isAdmin,
)

const canReview = computed(
  () => auth.isTeacher,
)

const isStudent = computed(
  () => auth.isStudent,
)

const hasCourse = computed(
  () => selectedCourseId.value !== null,
)

const filteredPublishedItems = computed(() => {

  const keyword = searchKeyword.value
    .trim()
    .toLowerCase()

  return publishedItems.value.filter(item => {

    if (
      categoryFilter.value
      && item.category !== categoryFilter.value
    ) {
      return false
    }

    if (!keyword) {
      return true
    }

    return (
      item.questionText.toLowerCase().includes(keyword)
      || item.answerText.toLowerCase().includes(keyword)
      || (item.knowledgePointTitle || '')
        .toLowerCase()
        .includes(keyword)
    )
  })
})

const categoryOptions = computed(() => {

  return [
    ...new Set(
      publishedItems.value.map(item => item.category),
    ),
  ]
})

const form = reactive({

  sourceQuestionText: '',

  knowledgePointId: '' as BigIntId | '',

  knowledgePointTitle: '',

  questionText: '',

  answerText: '',

  category: '其他',

})

const categoryPresets = [
  '课程基础',
  '重点难点',
  '操作实践',
  '考试相关',
  'Java集合',
  '其他',
]

const statusOptions: Array<{
  label: string
  value: CourseHotQuestionStatus | ''
}> = [
  { label: '全部状态', value: '' },
  { label: '草稿', value: 'DRAFT' },
  { label: '待审核', value: 'PENDING_REVIEW' },
  { label: '已发布', value: 'PUBLISHED' },
  { label: '已驳回', value: 'REJECTED' },
  { label: '已下架', value: 'OFFLINE' },
]

function statusLabel(status: string): string {

  const labels: Record<string, string> = {
    DRAFT: '草稿',
    PENDING_REVIEW: '待审核',
    PUBLISHED: '已发布',
    REJECTED: '已驳回',
    OFFLINE: '已下架',
  }

  return labels[status] || status
}

function statusType(
  status: string,
): 'info' | 'warning' | 'success' | 'danger' {

  switch (status) {
    case 'PUBLISHED':
      return 'success'

    case 'PENDING_REVIEW':
      return 'warning'

    case 'REJECTED':
      return 'danger'

    case 'OFFLINE':
      return 'info'

    default:
      return 'info'
  }
}

// =====================================================
// 1. 加载课程
// =====================================================

async function loadCourses(): Promise<void> {

  loadingCourses.value = true

  try {

    courses.value = await listAccessibleCourses()

    const exists = courses.value.some(
      item => String(item.id) === String(selectedCourseId.value),
    )

    if (!exists) {
      selectedCourseId.value =
        courses.value[0]?.id ?? null
    }

    courseStore.select(selectedCourseId.value)

  } catch (error) {

    notifyError(error, '课程列表加载失败')

  } finally {

    loadingCourses.value = false
  }
}

// =====================================================
// 2. 加载页面数据
// =====================================================

async function loadData(): Promise<void> {

  const courseId = selectedCourseId.value

  const version = ++requestVersion

  if (!courseId) {

    candidates.value = []
    managedItems.value = []
    publishedItems.value = []

    return
  }

  loading.value = true

  try {

    if (isStudent.value) {

      const result = await listPublishedHotQuestions(
        courseId,
      )

      if (
        version !== requestVersion
        || String(courseId) !== String(selectedCourseId.value)
      ) {
        return
      }

      publishedItems.value = result

    } else {

      const tasks: Array<Promise<unknown>> = [

        listManagedHotQuestions(
          courseId,
          statusFilter.value || undefined,
          searchKeyword.value.trim() || undefined,
        ),

      ]

      if (canManage.value) {

        tasks.push(
          listHotQuestions(
            courseId,
            {
              limit: 20,
            },
          ),
        )
      }

      const results = await Promise.all(tasks)

      if (
        version !== requestVersion
        || String(courseId) !== String(selectedCourseId.value)
      ) {
        return
      }

      managedItems.value =
        results[0] as CourseHotQuestion[]

      if (canManage.value) {
        candidates.value =
          results[1] as HotQuestion[]
      } else {
        candidates.value = []
      }
    }

  } catch (error) {

    if (version === requestVersion) {
      notifyError(error, '热门问题加载失败')
    }

  } finally {

    if (version === requestVersion) {
      loading.value = false
    }
  }
}

function handleCourseChange(): void {

  courseStore.select(selectedCourseId.value)

  statusFilter.value = ''

  searchKeyword.value = ''

  categoryFilter.value = ''

  void loadData()
}

function handleSearch(): void {
  void loadData()
}

// =====================================================
// 3. 根据高频问题打开整理窗口
// =====================================================

function openCreate(candidate: HotQuestion): void {

  if (!canManage.value) {
    return
  }

  editingId.value = null

  form.sourceQuestionText =
    candidate.questionText

  form.knowledgePointId =
    candidate.knowledgePointId ?? ''

  form.knowledgePointTitle =
    candidate.knowledgePointTitle ?? ''

  form.questionText =
    candidate.questionText

  form.answerText = ''

  form.category = '其他'

  dialogVisible.value = true
}

// =====================================================
// 4. 修改已有标准问答
// =====================================================

function openEdit(item: CourseHotQuestion): void {

  if (!canManage.value) {
    return
  }

  editingId.value = item.id

  form.sourceQuestionText =
    item.sourceQuestionText

  form.knowledgePointId =
    item.knowledgePointId ?? ''

  form.knowledgePointTitle =
    item.knowledgePointTitle ?? ''

  form.questionText =
    item.questionText

  form.answerText =
    item.answerText

  form.category =
    item.category || '其他'

  dialogVisible.value = true
}

// =====================================================
// 5. 保存标准问答
// =====================================================

async function saveQuestion(): Promise<void> {

  const courseId = selectedCourseId.value

  if (!courseId || saving.value) {
    return
  }

  if (!form.questionText.trim()) {
    notifyWarning('请输入标准问题')
    return
  }

  if (!form.answerText.trim()) {
    notifyWarning('请输入标准答案')
    return
  }

  saving.value = true

  try {

    if (editingId.value) {

      await updateCourseHotQuestion(
        editingId.value,
        {
          knowledgePointId:
            form.knowledgePointId || null,

          questionText:
            form.questionText.trim(),

          answerText:
            form.answerText.trim(),

          category:
            form.category,
        },
      )

      notifySuccess('标准问答修改成功，已重新转为草稿')

    } else {

      await createCourseHotQuestion(
        courseId,
        {
          sourceQuestionText:
            form.sourceQuestionText,

          knowledgePointId:
            form.knowledgePointId || null,

          questionText:
            form.questionText.trim(),

          answerText:
            form.answerText.trim(),

          category:
            form.category,
        },
      )

      notifySuccess('标准问答创建成功')
    }

    dialogVisible.value = false

    await loadData()

  } catch (error) {

    notifyError(error, '标准问答保存失败')

  } finally {

    saving.value = false
  }
}

// =====================================================
// 6. 提交审核
// =====================================================

async function submitQuestion(
  item: CourseHotQuestion,
): Promise<void> {

  try {

    await ElMessageBox.confirm(
      '提交后将由课程负责人审核，审核通过后学生才能查看。',
      '提交审核',
      {
        type: 'info',
        confirmButtonText: '提交',
        cancelButtonText: '取消',
      },
    )

  } catch {
    return
  }

  saving.value = true

  try {

    await submitCourseHotQuestion(item.id)

    notifySuccess('已提交课程负责人审核')

    await loadData()

  } catch (error) {

    notifyError(error, '提交审核失败')

  } finally {

    saving.value = false
  }
}

// =====================================================
// 7. 课程负责人审核
// =====================================================

function openReview(item: CourseHotQuestion): void {

  reviewingItem.value = item

  reviewComment.value = ''

  reviewDialogVisible.value = true
}

async function decideReview(
  approved: boolean,
): Promise<void> {

  const item = reviewingItem.value

  if (!item || saving.value) {
    return
  }

  if (!approved && !reviewComment.value.trim()) {
    notifyWarning('驳回时必须填写原因')
    return
  }

  try {

    await ElMessageBox.confirm(
      approved
        ? '确认通过该标准问答并向学生发布？'
        : '确认驳回该标准问答？',
      approved ? '审核通过' : '审核驳回',
      {
        type: approved ? 'success' : 'warning',
        confirmButtonText: '确认',
        cancelButtonText: '取消',
      },
    )

  } catch {
    return
  }

  saving.value = true

  try {

    await reviewCourseHotQuestion(
      item.id,
      {
        approved,
        comment: reviewComment.value.trim(),
      },
    )

    notifySuccess(
      approved
        ? '审核通过，标准问答已发布'
        : '已驳回，请运营人员修改',
    )

    reviewDialogVisible.value = false

    reviewingItem.value = null

    await loadData()

  } catch (error) {

    notifyError(error, '审核操作失败')

  } finally {

    saving.value = false
  }
}

// =====================================================
// 8. 下架
// =====================================================

async function offlineQuestion(
  item: CourseHotQuestion,
): Promise<void> {

  try {

    await ElMessageBox.confirm(
      '下架后学生将无法查看该标准问答，是否继续？',
      '下架热门问题',
      {
        type: 'warning',
        confirmButtonText: '确认下架',
        cancelButtonText: '取消',
      },
    )

  } catch {
    return
  }

  saving.value = true

  try {

    await offlineCourseHotQuestion(item.id)

    notifySuccess('热门问题已下架')

    await loadData()

  } catch (error) {

    notifyError(error, '下架失败')

  } finally {

    saving.value = false
  }
}

onMounted(async () => {

  await loadCourses()

  const requestedCourseId =
    typeof route.query.courseId === 'string'
      ? route.query.courseId
      : ''

  if (requestedCourseId) {

    const target = courses.value.find(
      item => String(item.id) === requestedCourseId,
    )

    if (target) {
      selectedCourseId.value = target.id
    }
  }

  courseStore.select(selectedCourseId.value)

  await loadData()
})
</script>

<template>
  <div class="hot-question-page">

    <BaseCard
      title="课程热门问题"
      subtitle="整理课程中的高频问题，建立标准答案并经审核后发布"
    >

      <div class="course-toolbar">

        <el-select
          v-model="selectedCourseId"
          placeholder="选择课程"
          :loading="loadingCourses"
          style="width: 240px"
          @change="handleCourseChange"
        >
          <el-option
            v-for="course in courses"
            :key="course.id"
            :label="course.name"
            :value="course.id"
          />
        </el-select>

        <el-button
          :loading="loading"
          @click="loadData"
        >
          刷新
        </el-button>

      </div>

    </BaseCard>

    <el-empty
      v-if="!hasCourse && !loadingCourses"
      description="当前没有可访问的课程"
    />

    <template v-if="hasCourse">

      <!-- ================= 学生端 ================= -->

      <BaseCard
        v-if="isStudent"
        title="课程标准问答"
        subtitle="查看课程中经过负责人审核的常见问题和标准答案"
      >

        <div class="student-filters">

          <el-input
            v-model="searchKeyword"
            placeholder="搜索问题、答案或知识点"
            clearable
            style="max-width: 340px"
          />

          <el-select
            v-model="categoryFilter"
            placeholder="全部分类"
            clearable
            style="width: 180px"
          >
            <el-option
              v-for="category in categoryOptions"
              :key="category"
              :label="category"
              :value="category"
            />
          </el-select>

        </div>

        <div
          v-loading="loading"
          class="student-question-list"
        >

          <el-collapse
            v-if="filteredPublishedItems.length"
            accordion
          >

            <el-collapse-item
              v-for="item in filteredPublishedItems"
              :key="item.id"
              :name="String(item.id)"
            >

              <template #title>
                <div class="student-question-title">

                  <strong>{{ item.questionText }}</strong>

                  <el-tag size="small" type="success">
                    {{ item.category }}
                  </el-tag>

                </div>
              </template>

              <div class="student-answer">

                <div class="answer-meta">

                  <el-tag
                    v-if="item.knowledgePointTitle"
                    type="info"
                    size="small"
                  >
                    {{ item.knowledgePointTitle }}
                  </el-tag>

                </div>

                <div class="answer-content">
                  {{ item.answerText }}
                </div>

              </div>

            </el-collapse-item>

          </el-collapse>

          <el-empty
            v-else
            description="当前课程暂时没有已发布的热门问题"
          />

        </div>

      </BaseCard>

      <!-- ================= 管理员候选问题 ================= -->

      <BaseCard
        v-if="canManage"
        title="高频问题候选"
        subtitle="从已有课程提问记录中筛选高频问题，整理为标准问答"
      >

        <el-table
          v-loading="loading"
          :data="candidates"
          row-key="questionText"
          empty-text="当前没有达到高频条件的问题"
          stripe
        >

          <el-table-column
            prop="questionText"
            label="原始问题"
            min-width="250"
            show-overflow-tooltip
          />

          <el-table-column
            prop="knowledgePointTitle"
            label="知识点"
            min-width="130"
          >
            <template #default="{ row }">
              {{ row.knowledgePointTitle || '未分类' }}
            </template>
          </el-table-column>

          <el-table-column
            prop="questionCount"
            label="提问次数"
            width="100"
          />

          <el-table-column
            prop="studentCount"
            label="涉及学生"
            width="100"
          />

          <el-table-column
            prop="lastAskedAt"
            label="最近提问"
            min-width="170"
          />

          <el-table-column
            label="操作"
            width="140"
            fixed="right"
          >

            <template #default="{ row }">

              <el-button
                type="primary"
                link
                :disabled="saving"
                @click="openCreate(row)"
              >
                整理为标准问答
              </el-button>

            </template>

          </el-table-column>

        </el-table>

      </BaseCard>

      <!-- ================= 管理与审核列表 ================= -->

      <BaseCard
        v-if="!isStudent"
        title="标准问答管理"
        subtitle="查看草稿、审核进度、已发布和已下架的标准问答"
      >

        <div class="management-filters">

          <el-select
            v-model="statusFilter"
            placeholder="全部状态"
            style="width: 170px"
          >
            <el-option
              v-for="option in statusOptions"
              :key="option.value"
              :label="option.label"
              :value="option.value"
            />
          </el-select>

          <el-input
            v-model="searchKeyword"
            clearable
            placeholder="搜索标准问题或答案"
            style="max-width: 280px"
            @keyup.enter="handleSearch"
          />

          <el-button
            type="primary"
            :loading="loading"
            @click="handleSearch"
          >
            查询
          </el-button>

        </div>

        <el-table
          v-loading="loading"
          :data="managedItems"
          row-key="id"
          stripe
          empty-text="暂无标准问答记录"
        >

          <el-table-column
            prop="questionText"
            label="标准问题"
            min-width="220"
            show-overflow-tooltip
          />

          <el-table-column
            prop="knowledgePointTitle"
            label="知识点"
            min-width="120"
          >
            <template #default="{ row }">
              {{ row.knowledgePointTitle || '未分类' }}
            </template>
          </el-table-column>

          <el-table-column
            prop="category"
            label="分类"
            width="110"
          />

          <el-table-column
            label="来源热度"
            width="120"
          >
            <template #default="{ row }">
              {{ row.questionCount }} 次 / {{ row.studentCount }} 人
            </template>
          </el-table-column>

          <el-table-column
            label="状态"
            width="115"
          >
            <template #default="{ row }">
              <el-tag :type="statusType(row.status)">
                {{ statusLabel(row.status) }}
              </el-tag>
            </template>
          </el-table-column>

          <el-table-column
            prop="updatedAt"
            label="更新时间"
            min-width="170"
          />

          <el-table-column
            label="操作"
            width="210"
            fixed="right"
          >

            <template #default="{ row }">

              <template v-if="canManage">

                <el-button
                  v-if="
                    ['DRAFT', 'REJECTED', 'OFFLINE']
                      .includes(row.status)
                  "
                  type="primary"
                  link
                  @click="openEdit(row)"
                >
                  编辑
                </el-button>

                <el-button
                  v-if="row.status === 'DRAFT'"
                  type="primary"
                  link
                  :disabled="saving"
                  @click="submitQuestion(row)"
                >
                  提交审核
                </el-button>

                <el-button
                  v-if="row.status === 'PUBLISHED'"
                  type="danger"
                  link
                  :disabled="saving"
                  @click="offlineQuestion(row)"
                >
                  下架
                </el-button>

              </template>

              <el-button
                v-if="
                  canReview
                  && row.status === 'PENDING_REVIEW'
                "
                type="success"
                link
                @click="openReview(row)"
              >
                审核
              </el-button>

            </template>

          </el-table-column>

        </el-table>

      </BaseCard>

    </template>

    <!-- ================= 编辑弹窗 ================= -->

    <el-dialog
      v-model="dialogVisible"
      :title="editingId ? '修改标准问答' : '整理标准问答'"
      width="650px"
      destroy-on-close
    >

      <el-form
        label-position="top"
      >

        <el-form-item
          v-if="!editingId"
          label="来源高频问题"
        >

          <el-input
            v-model="form.sourceQuestionText"
            type="textarea"
            :rows="2"
            disabled
          />

        </el-form-item>

        <el-form-item label="关联知识点">

          <el-input
            :model-value="form.knowledgePointTitle || '未分类'"
            disabled
          />

        </el-form-item>

        <el-form-item label="标准问题" required>

          <el-input
            v-model="form.questionText"
            maxlength="500"
            show-word-limit
            placeholder="整理后的标准问题"
          />

        </el-form-item>

        <el-form-item label="标准答案" required>

          <el-input
            v-model="form.answerText"
            type="textarea"
            :rows="7"
            maxlength="5000"
            show-word-limit
            placeholder="填写经过课程资料核对的标准答案"
          />

        </el-form-item>

        <el-form-item label="问题分类">

          <el-select
            v-model="form.category"
            allow-create
            filterable
            default-first-option
            style="width: 100%"
          >

            <el-option
              v-for="item in categoryPresets"
              :key="item"
              :label="item"
              :value="item"
            />

          </el-select>

        </el-form-item>

      </el-form>

      <template #footer>

        <el-button
          @click="dialogVisible = false"
        >
          取消
        </el-button>

        <el-button
          type="primary"
          :loading="saving"
          @click="saveQuestion"
        >
          保存草稿
        </el-button>

      </template>

    </el-dialog>

    <!-- ================= 审核弹窗 ================= -->

    <el-dialog
      v-model="reviewDialogVisible"
      title="审核热门标准问答"
      width="600px"
    >

      <template v-if="reviewingItem">

        <div class="review-question">
          <strong>{{ reviewingItem.questionText }}</strong>
        </div>

        <div class="review-answer">
          {{ reviewingItem.answerText }}
        </div>

        <el-form-item
          label="审核意见"
          class="review-comment"
        >

          <el-input
            v-model="reviewComment"
            type="textarea"
            :rows="4"
            maxlength="1000"
            show-word-limit
            placeholder="通过时可以填写建议；驳回时必须填写原因"
          />

        </el-form-item>

      </template>

      <template #footer>

        <el-button
          @click="reviewDialogVisible = false"
        >
          取消
        </el-button>

        <el-button
          type="danger"
          :loading="saving"
          @click="decideReview(false)"
        >
          驳回
        </el-button>

        <el-button
          type="success"
          :loading="saving"
          @click="decideReview(true)"
        >
          审核通过
        </el-button>

      </template>

    </el-dialog>

  </div>
</template>

<style scoped>
.hot-question-page {
  display: flex;
  flex-direction: column;
  gap: 16px;
  min-width: 0;
}

.course-toolbar,
.student-filters,
.management-filters {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 10px;
}

.student-question-list {
  margin-top: 16px;
}

.student-question-title {
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 0;
  padding-right: 10px;
}

.student-question-title strong {
  overflow: hidden;
  color: var(--sc-text);
  text-overflow: ellipsis;
}

.student-answer {
  padding: 8px 14px 14px;
}

.answer-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 12px;
}

.answer-content {
  color: var(--sc-text);
  font-size: 13px;
  line-height: 1.9;
  white-space: pre-wrap;
  overflow-wrap: anywhere;
}

.review-question {
  padding: 12px;
  border-radius: 8px;
  background: var(--sc-surface-muted);
  color: var(--sc-text);
  line-height: 1.8;
}

.review-answer {
  margin: 14px 0;
  padding: 14px;
  border: 1px solid var(--sc-border);
  border-radius: 8px;
  color: var(--sc-text-secondary);
  font-size: 13px;
  line-height: 1.8;
  white-space: pre-wrap;
}

.review-comment {
  margin-top: 18px;
}

@media (max-width: 640px) {
  .course-toolbar,
  .student-filters,
  .management-filters {
    align-items: stretch;
  }

  .course-toolbar > *,
  .student-filters > *,
  .management-filters > * {
    width: 100%;
    max-width: none !important;
  }
}
</style>
