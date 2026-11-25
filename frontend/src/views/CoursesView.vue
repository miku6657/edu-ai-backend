<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { Plus, Search, BookOpen, MoreHorizontal } from '@lucide/vue'
import { ElMessageBox } from 'element-plus'

import {
  adminGetCourseInviteCode,
  adminListCourseStudents,
  adminRegenerateCourseInviteCode,
  adminRemoveCourseStudent,
  createCourse,
  deleteCourse,
  getCourseInviteCode,
  joinCourse,
  listCourseStudents,
  regenerateCourseInviteCode,
  removeCourseStudent,
  updateCourse,
} from '@/api/course'
import type { Course, CourseMember } from '@/api/course'
import { listAccessibleCourses } from '@/api/course-access'
import type { BigIntId } from '@/api/types'
import { useAuthStore } from '@/stores/auth'
import BaseCard from '@/components/ui/BaseCard.vue'
import EmptyState from '@/components/ui/EmptyState.vue'
import { notifyError, notifySuccess, notifyWarning } from '@/utils/notify'
import {
  CourseFormValidationError,
  toCourseRequest,
} from '@/views/course/course-form'
import type { CourseFormValues } from '@/views/course/course-form'

const loading = ref(false)
const loadError = ref('')
const auth = useAuthStore()
const courses = ref<Course[]>([])
const searchQuery = ref('')

const creating = ref(false)
const editing = ref(false)
const submitting = ref(false)
const deletingId = ref<BigIntId | null>(null)
const membersVisible = ref(false)
const membersLoading = ref(false)
const selectedMemberCourse = ref<Course | null>(null)
const members = ref<CourseMember[]>([])

const emptyCourseForm = (): CourseFormValues => ({
  courseCode: '',
  name: '',
  description: '',
  credits: 3,
  applicableMajor: '',
  semester: '',
})

const createForm = ref<CourseFormValues>(emptyCourseForm())

const editForm = ref<CourseFormValues & {
  id: BigIntId
}>({
  id: '',
  ...emptyCourseForm(),
})

const hasCourses = computed(() => courses.value.length > 0)

const filteredCourses = computed(() => {
  const keyword = searchQuery.value.trim().toLowerCase()

  if (!keyword) {
    return courses.value
  }

  return courses.value.filter(course =>
    course.name.toLowerCase().includes(keyword)
    || (course.description ?? '').toLowerCase().includes(keyword)
    || (course.courseCode ?? '').toLowerCase().includes(keyword)
    || (course.applicableMajor ?? '').toLowerCase().includes(keyword)
    || (course.semester ?? '').toLowerCase().includes(keyword),
  )
})

const summary = computed(() => ({
  courses: courses.value.length,
}))

onMounted(() => {
  void loadCourses()
})

/**
 * 获取课程列表
 */
async function loadCourses(): Promise<void> {
  loading.value = true
  loadError.value = ''

  try {
    courses.value = await listAccessibleCourses()
  } catch (error) {
    loadError.value =
      error instanceof Error ? error.message : '课程列表读取失败'
    notifyError(error)
  } finally {
    loading.value = false
  }
}

/**
 * 打开创建课程弹窗
 */
function openCreate(): void {
  createForm.value = emptyCourseForm()

  creating.value = true
}

/**
 * 创建课程
 */
async function submitCreate(): Promise<void> {
  if (submitting.value) return

  let request
  try {
    request = toCourseRequest(createForm.value)
  } catch (error) {
    if (error instanceof CourseFormValidationError) {
      notifyWarning(error.message)
    } else {
      notifyError(error)
    }
    return
  }

  submitting.value = true

  try {
    const created = await createCourse(request)

    creating.value = false
    notifySuccess(`课程「${created.name}」创建成功`)

    await loadCourses()
  } catch (error) {
    notifyError(error)
  } finally {
    submitting.value = false
  }
}

/**
 * 打开编辑课程弹窗
 */
function openEdit(course: Course): void {
  editForm.value = {
    id: course.id,
    courseCode: course.courseCode ?? '',
    name: course.name,
    description: course.description ?? '',
    credits: course.credits ?? 3,
    applicableMajor: course.applicableMajor ?? '',
    semester: course.semester ?? '',
  }

  editing.value = true
}

/**
 * 修改课程
 */
async function submitEdit(): Promise<void> {
  if (submitting.value) return

  let request
  try {
    request = toCourseRequest(editForm.value)
  } catch (error) {
    if (error instanceof CourseFormValidationError) {
      notifyWarning(error.message)
    } else {
      notifyError(error)
    }
    return
  }

  submitting.value = true

  try {
    await updateCourse(editForm.value.id, request)

    editing.value = false
    notifySuccess('课程修改成功')

    await loadCourses()
  } catch (error) {
    notifyError(error)
  } finally {
    submitting.value = false
  }
}

/**
 * 停用课程
 */
async function disableCourse(course: Course): Promise<void> {
  try {
    await ElMessageBox.confirm(
      `确定停用课程「${course.name}」吗？停用后该课程将不能继续正常使用。`,
      '停用课程',
      {
        type: 'warning',
        confirmButtonText: '确认停用',
        cancelButtonText: '取消',
      },
    )
  } catch {
    return
  }

  deletingId.value = course.id

  try {
    await deleteCourse(course.id)

    notifySuccess('课程已停用')
    await loadCourses()
  } catch (error) {
    notifyError(error)
  } finally {
    deletingId.value = null
  }
}

async function openJoinCourse(): Promise<void> {
  try {
    const { value } = await ElMessageBox.prompt(
      '请输入教师提供的课程邀请码',
      '加入课程',
      {
        confirmButtonText: '加入课程',
        cancelButtonText: '取消',
        inputPlaceholder: '例如 K7M9X2PA',
        inputPattern: /^[A-Za-z0-9]{6,12}$/,
        inputErrorMessage: '邀请码应为6至12位字母或数字',
      },
    )

    const course = await joinCourse({
      inviteCode: value.trim().toUpperCase(),
    })

    notifySuccess(`成功加入课程「${course.name}」`)
    await loadCourses()
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') {
      notifyError(error)
    }
  }
}

async function showInviteCode(course: Course): Promise<void> {
  try {
    const result = auth.isAdmin
      ? await adminGetCourseInviteCode(course.id)
      : await getCourseInviteCode(course.id)

    await ElMessageBox.alert(
      `课程邀请码：${result.inviteCode}`,
      `「${course.name}」的邀请码`,
      {
        confirmButtonText: '知道了',
      },
    )
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') {
      notifyError(error)
    }
  }
}

async function resetInviteCode(course: Course): Promise<void> {
  try {
    await ElMessageBox.confirm(
      '重置后旧邀请码立即失效，已加入的学生不会被移除。确定继续吗？',
      '重置课程邀请码',
      {
        type: 'warning',
        confirmButtonText: '确认重置',
        cancelButtonText: '取消',
      },
    )
  } catch {
    return
  }

  try {
    const result = auth.isAdmin
      ? await adminRegenerateCourseInviteCode(course.id)
      : await regenerateCourseInviteCode(course.id)

    await ElMessageBox.alert(
      `新邀请码：${result.inviteCode}`,
      '重置成功',
    )
  } catch (error) {
    notifyError(error)
  }
}

async function openMembers(course: Course): Promise<void> {
  selectedMemberCourse.value = course
  membersVisible.value = true
  membersLoading.value = true

  try {
    members.value = auth.isAdmin
      ? await adminListCourseStudents(course.id)
      : await listCourseStudents(course.id)
  } catch (error) {
    notifyError(error)
  } finally {
    membersLoading.value = false
  }
}

async function removeMember(member: CourseMember): Promise<void> {
  const course = selectedMemberCourse.value
  if (!course) return

  try {
    await ElMessageBox.confirm(
      `确认将「${member.nickname || member.username}」移出课程吗？`,
      '移除学生',
      {
        type: 'warning',
        confirmButtonText: '移除',
        cancelButtonText: '取消',
      },
    )
  } catch {
    return
  }

  try {
    if (auth.isAdmin) {
      await adminRemoveCourseStudent(course.id, member.studentId)
    } else {
      await removeCourseStudent(course.id, member.studentId)
    }
    notifySuccess('学生已移出课程')
    await openMembers(course)
  } catch (error) {
    notifyError(error)
  }
}
</script>

<template>
  <div class="courses-page">
    <div class="courses-main">

      <!-- 页面顶部 -->
      <section class="courses-hero">
        <div class="courses-hero__copy">
          <span class="courses-hero__eyebrow">
            COURSE MANAGEMENT
          </span>

          <h2>我的课程</h2>

          <p>
            创建和管理课程，上传课件，构建属于自己的课程知识库。
          </p>

          <div class="courses-hero__summary">
            <span>我的课程 {{ summary.courses }}</span>
          </div>
        </div>

        <div class="courses-hero__side">
          <div class="courses-hero__art">
            <img
              src="/illustrations/course-header-books.png"
              alt=""
            />
          </div>

          <el-button
            v-if="auth.isTeacher"
            type="primary"
            :icon="Plus"
            @click="openCreate"
          >
            新建课程
          </el-button>
          <el-button
            v-else-if="auth.isStudent"
            type="primary"
            :icon="Plus"
            @click="openJoinCourse"
          >
            加入课程
          </el-button>
        </div>
      </section>

      <!-- 搜索工具栏 -->
      <div class="courses-toolbar">
        <div class="courses-tabs">
          <button
            type="button"
            class="course-tab course-tab--active"
          >
            全部课程
            <span class="course-tab__count">
              {{ courses.length }}
            </span>
          </button>
        </div>

        <div class="courses-tools">
          <label class="courses-search">
            <Search :size="15" />

            <input
              v-model="searchQuery"
              type="search"
              placeholder="搜索课程..."
              aria-label="搜索课程"
            />
          </label>
        </div>
      </div>

      <!-- 加载状态 -->
      <div
        v-if="loading"
        class="courses-grid"
        aria-hidden="true"
      >
        <div
          v-for="index in 6"
          :key="index"
          class="course-skeleton"
        ></div>
      </div>

      <!-- 错误状态 -->
      <BaseCard
        v-else-if="loadError"
        title="课程读取失败"
      >
        <div class="module-error">
          <p>{{ loadError }}</p>

          <el-button
            size="small"
            @click="loadCourses"
          >
            重新加载
          </el-button>
        </div>
      </BaseCard>

      <!-- 空状态 -->
      <BaseCard v-else-if="!hasCourses">
        <EmptyState
          image="/illustrations/scene-study-desk.png"
          :title="auth.isAdmin ? '暂无课程' : '还没有课程'"
          :description="auth.isTeacher
            ? '创建第一门课程，上传课件后即可开始使用 AI 学习助手。'
            : auth.isStudent
              ? '输入教师提供的邀请码即可加入课程。'
              : ''"
        >
          <template #action>
            <el-button
              v-if="auth.isTeacher"
              type="primary"
              @click="openCreate"
            >
              新建课程
            </el-button>
            <el-button
              v-else-if="auth.isStudent"
              type="primary"
              @click="openJoinCourse"
            >
              加入课程
            </el-button>
          </template>

        </EmptyState>
      </BaseCard>

      <!-- 搜索无结果 -->
      <BaseCard v-else-if="filteredCourses.length === 0">
        <EmptyState
          title="没有匹配的课程"
          :description="`没有找到与「${searchQuery}」匹配的课程。`"
        >
          <template #action>
            <el-button @click="searchQuery = ''">
              清除搜索
            </el-button>
          </template>
        </EmptyState>
      </BaseCard>

      <!-- 课程列表 -->
      <div v-else class="courses-grid">
        <div
          v-for="course in filteredCourses"
          :key="course.id"
          class="course-item"
          :class="{
            'is-deleting': deletingId === course.id
          }"
        >
          <div class="course-item__header">
            <div class="course-item__icon">
              <BookOpen :size="24" />
            </div>

            <el-dropdown v-if="auth.isTeacher || auth.isAdmin" trigger="click">
              <button
                type="button"
                class="course-menu"
                aria-label="课程操作"
              >
                <MoreHorizontal :size="18" />
              </button>

              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item
                    v-if="auth.isTeacher"
                    @click="openEdit(course)"
                  >
                    编辑课程
                  </el-dropdown-item>

                  <el-dropdown-item
                    v-if="auth.isTeacher && course.status === 1"
                    divided
                    @click="disableCourse(course)"
                  >
                    停用课程
                  </el-dropdown-item>

                  <el-dropdown-item @click="showInviteCode(course)">
                    查看邀请码
                  </el-dropdown-item>

                  <el-dropdown-item @click="resetInviteCode(course)">
                    重置邀请码
                  </el-dropdown-item>

                  <el-dropdown-item @click="openMembers(course)">
                    管理学生
                  </el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
          </div>

          <div class="course-item__body">
            <h3>{{ course.name }}</h3>

            <div class="course-item__meta">
              <span v-if="course.courseCode">
                编码：{{ course.courseCode }}
              </span>

              <span v-if="course.credits != null">
                {{ course.credits }} 学分
              </span>

              <span v-if="course.semester">
                {{ course.semester }}
              </span>
            </div>

            <p v-if="course.applicableMajor">
              适用专业：{{ course.applicableMajor }}
            </p>

            <p>
              {{ course.description || '暂无课程描述' }}
            </p>
          </div>

          <div class="course-item__footer">
            <span
              class="course-status"
              :class="{
                'is-disabled': course.status === 0
              }"
            >
              {{ course.status === 1 ? '正常开放' : '已停用' }}
            </span>

            <span class="course-date">
              {{ course.createTime?.slice(0, 10) || '' }}
            </span>
          </div>
        </div>
      </div>

    </div>

    <!-- 右侧栏 -->
    <aside class="courses-rail">

      <BaseCard
        title="课程快捷操作"
        subtitle="快速进入学习功能"
      >
        <ul class="rail-actions">
          <li>
            <router-link
              class="rail-action"
              :to="{ name: 'knowledge' }"
            >
              <span class="rail-action__icon">
                <BookOpen :size="16" />
              </span>

              <span class="rail-action__text">
                <strong>上传学习资料</strong>
                <small>上传课件，构建课程知识库</small>
              </span>
            </router-link>
          </li>

          <li>
            <router-link
              class="rail-action"
              :to="{ name: 'rag' }"
            >
              <span class="rail-action__icon">
                <BookOpen :size="16" />
              </span>

              <span class="rail-action__text">
                <strong>AI 智能问答</strong>
                <small>基于课程知识进行提问</small>
              </span>
            </router-link>
          </li>
        </ul>
      </BaseCard>

      <div class="rail-quote">
        <img
          src="/illustrations/scene-study-desk.png"
          alt=""
        />

        <p>
          在知识的海洋里
          <br />
          找到属于你的方向
        </p>
      </div>

    </aside>

    <el-dialog
      v-model="membersVisible"
      :title="`课程成员 - ${selectedMemberCourse?.name ?? ''}`"
      width="680px"
    >
      <el-table
        v-loading="membersLoading"
        :data="members"
        empty-text="暂无学生"
      >
        <el-table-column prop="username" label="用户名" />
        <el-table-column prop="nickname" label="昵称" />
        <el-table-column prop="joinTime" label="加入时间" />
        <el-table-column label="操作" width="100">
          <template #default="{ row }">
            <el-button
              type="danger"
              text
              @click="removeMember(row)"
            >
              移除
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-dialog>

    <!-- 创建课程弹窗 -->
    <el-dialog
      v-model="creating"
      title="新建课程"
      width="520px"
    >
      <el-form label-width="100px">
        <el-form-item label="课程编码" required>
          <el-input
            v-model="createForm.courseCode"
            maxlength="32"
            placeholder="例如 JAVA101"
          />
        </el-form-item>

        <el-form-item label="课程名称" required>
          <el-input
            v-model="createForm.name"
            maxlength="100"
            show-word-limit
            placeholder="请输入课程名称"
          />
        </el-form-item>

        <el-form-item label="课程学分" required>
          <el-input-number
            v-model="createForm.credits"
            :min="0.1"
            :max="99.9"
            :step="0.5"
            :precision="1"
          />
        </el-form-item>

        <el-form-item label="适用专业" required>
          <el-input
            v-model="createForm.applicableMajor"
            maxlength="200"
            placeholder="例如 软件工程"
          />
        </el-form-item>

        <el-form-item label="开课学期" required>
          <el-input
            v-model="createForm.semester"
            maxlength="50"
            placeholder="例如 2026-2027-1"
          />
        </el-form-item>

        <el-form-item label="课程描述">
          <el-input
            v-model="createForm.description"
            type="textarea"
            :rows="4"
            maxlength="500"
            show-word-limit
            placeholder="介绍一下这门课程..."
          />
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="creating = false">
          取消
        </el-button>

        <el-button
          type="primary"
          :loading="submitting"
          @click="submitCreate"
        >
          创建课程
        </el-button>
      </template>
    </el-dialog>

    <!-- 编辑课程弹窗 -->
    <el-dialog
      v-model="editing"
      title="编辑课程"
      width="520px"
    >
      <el-form label-width="100px">
        <el-form-item label="课程编码" required>
          <el-input
            v-model="editForm.courseCode"
            maxlength="32"
          />
        </el-form-item>

        <el-form-item label="课程名称" required>
          <el-input
            v-model="editForm.name"
            maxlength="100"
            show-word-limit
          />
        </el-form-item>

        <el-form-item label="课程学分" required>
          <el-input-number
            v-model="editForm.credits"
            :min="0.1"
            :max="99.9"
            :step="0.5"
            :precision="1"
          />
        </el-form-item>

        <el-form-item label="适用专业" required>
          <el-input
            v-model="editForm.applicableMajor"
            maxlength="200"
          />
        </el-form-item>

        <el-form-item label="开课学期" required>
          <el-input
            v-model="editForm.semester"
            maxlength="50"
          />
        </el-form-item>

        <el-form-item label="课程描述">
          <el-input
            v-model="editForm.description"
            type="textarea"
            maxlength="500"
            show-word-limit
            :rows="4"
          />
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="editing = false">
          取消
        </el-button>

        <el-button
          type="primary"
          :loading="submitting"
          @click="submitEdit"
        >
          保存修改
        </el-button>
      </template>
    </el-dialog>

  </div>
</template>

<style scoped>
.courses-page {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 300px;
  gap: var(--sc-card-gap);
  align-items: start;
}

.courses-main {
  display: flex;
  flex-direction: column;
  gap: var(--sc-card-gap);
  min-width: 0;
}

.courses-hero {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 24px;
  padding: 26px 30px;
  overflow: hidden;
  border: 1px solid var(--sc-border);
  border-radius: 16px;
  background: linear-gradient(
    120deg,
    var(--sc-primary-soft) 0%,
    var(--sc-surface) 55%,
    var(--sc-accent-soft) 100%
  );
  box-shadow: var(--sc-shadow);
}

.courses-hero__copy {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 6px;
  min-width: 0;
}

.courses-hero__eyebrow {
  color: var(--sc-primary-strong);
  font-size: var(--sc-font-hint);
  font-weight: 700;
  letter-spacing: 1.2px;
}

.courses-hero h2 {
  color: var(--sc-text);
  font-size: var(--sc-font-title);
  font-weight: var(--sc-font-title-weight);
  letter-spacing: -0.3px;
}

.courses-hero p {
  color: var(--sc-text-secondary);
  font-size: var(--sc-font-caption);
  line-height: 1.7;
}

.courses-hero__summary {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 6px;
}

.courses-hero__summary span {
  padding: 2px 9px;
  border-radius: var(--sc-radius-pill);
  color: var(--sc-text-secondary);
  background: var(--sc-surface);
  font-size: var(--sc-font-hint);
}

.courses-hero__summary span.is-danger {
  color: var(--sc-danger);
  background: var(--sc-danger-soft);
}

.courses-hero__side {
  display: flex;
  flex: none;
  align-items: center;
  gap: 16px;
}

.courses-hero__art {
  display: grid;
  place-items: center;
  width: 116px;
  height: 100px;
  padding: 6px;
  border-radius: 14px;
  background: #f1f6ff;
}

.courses-hero__art img {
  display: block;
  max-width: 100%;
  max-height: 100%;
  object-fit: contain;
}

.courses-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
  padding-bottom: 2px;
  border-bottom: 1px solid var(--sc-border);
}

.courses-tabs {
  display: flex;
  align-items: center;
  gap: 18px;
  min-width: 0;
}

.course-tab {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 8px 2px 10px;
  border: 0;
  border-bottom: 2px solid transparent;
  color: var(--sc-text-secondary);
  background: transparent;
  font-family: var(--sc-font-sans);
  font-size: var(--sc-font-body);
  cursor: pointer;
}

.course-tab--active {
  border-bottom-color: var(--sc-primary);
  color: var(--sc-primary-strong);
  font-weight: 600;
}

.course-tab:disabled {
  cursor: not-allowed;
  opacity: 0.7;
}

.course-tab__count {
  min-width: 18px;
  padding: 0 5px;
  border-radius: var(--sc-radius-pill);
  color: var(--sc-primary-strong);
  background: var(--sc-primary-soft);
  font-size: var(--sc-font-hint);
  line-height: 18px;
  text-align: center;
}

.courses-tools {
  display: flex;
  align-items: center;
  gap: 10px;
}

.courses-search {
  display: flex;
  align-items: center;
  gap: 8px;
  height: 34px;
  padding: 0 12px;
  border: 1px solid var(--sc-border);
  border-radius: var(--sc-radius-pill);
  color: var(--sc-text-placeholder);
  background: var(--sc-surface);
}

.courses-search:focus-within {
  border-color: var(--sc-primary);
  box-shadow: 0 0 0 3px var(--sc-primary-soft);
}

.courses-search input {
  width: 150px;
  border: 0;
  outline: 0;
  color: var(--sc-text);
  background: transparent;
  font-family: var(--sc-font-sans);
  font-size: var(--sc-font-caption);
}

.courses-search input::-webkit-search-cancel-button {
  display: none;
}

.courses-term {
  width: 168px;
}

.courses-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 14px;
}

.courses-grid__item {
  min-width: 0;
}

.courses-grid__item.is-deleting {
  opacity: 0.55;
  pointer-events: none;
}

.course-skeleton {
  height: 188px;
  border: 1px solid var(--sc-border);
  border-radius: var(--sc-radius-card);
  background: var(--sc-surface-muted);
}

.course-menu {
  display: grid;
  place-items: center;
  width: 26px;
  height: 26px;
  padding: 0;
  border: 1px solid var(--sc-border);
  border-radius: 7px;
  color: var(--sc-text-placeholder);
  background: var(--sc-surface);
  cursor: pointer;
}

.course-menu:hover {
  border-color: var(--sc-primary);
  color: var(--sc-primary);
}

.courses-rail {
  display: flex;
  flex-direction: column;
  gap: var(--sc-card-gap);
  min-width: 0;
}

.rail-placeholder {
  display: flex;
  align-items: center;
  gap: 12px;
}

.rail-placeholder__art {
  display: grid;
  flex: none;
  place-items: center;
  width: 64px;
  height: 64px;
  padding: 6px;
  border-radius: 12px;
  background: #f1f6ff;
}

.rail-placeholder__art img {
  display: block;
  max-width: 100%;
  max-height: 100%;
  object-fit: contain;
}

.rail-placeholder p {
  color: var(--sc-text-placeholder);
  font-size: var(--sc-font-hint);
  line-height: 1.6;
}

.rail-actions {
  display: flex;
  flex-direction: column;
  gap: 4px;
  margin: 0;
  padding: 0;
  list-style: none;
}

.rail-action {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px;
  border-radius: 10px;
  color: inherit;
  text-decoration: none;
  transition: background-color 0.18s ease;
}

.rail-action:hover {
  background: var(--sc-surface-hover);
}

.rail-action__icon {
  display: grid;
  flex: none;
  place-items: center;
  width: 32px;
  height: 32px;
  border-radius: 9px;
  color: var(--sc-primary);
  background: var(--sc-primary-soft);
}

.rail-action__text {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
}

.rail-action__text strong {
  color: var(--sc-text);
  font-size: 13px;
  font-weight: 600;
}

.rail-action__text small {
  overflow: hidden;
  color: var(--sc-text-placeholder);
  font-size: var(--sc-font-hint);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.rail-quote {
  position: relative;
  min-height: 112px;
  overflow: hidden;
  border-radius: var(--sc-radius-card);
  background: #eaf2ff;
}

.rail-quote img {
  display: block;
  width: 100%;
  height: 132px;
  object-fit: cover;
  opacity: 0.9;
}

.rail-quote p {
  position: absolute;
  left: 14px;
  bottom: 14px;
  color: #1f2937;
  font-size: var(--sc-font-caption);
  font-weight: 600;
  line-height: 1.6;
}

.module-error {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  color: var(--sc-text-secondary);
  font-size: var(--sc-font-caption);
}

.course-switch-form {
  margin-top: 16px;
}

@media (max-width: 1360px) {
  .courses-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 1199px) {
  .courses-page {
    grid-template-columns: 1fr;
  }

  .courses-rail {
    display: grid;
    grid-template-columns: repeat(2, minmax(0, 1fr));
    align-items: start;
  }
}

@media (max-width: 860px) {
  .courses-grid {
    grid-template-columns: 1fr;
  }

  .courses-rail {
    grid-template-columns: 1fr;
  }

  .courses-hero {
    padding: 20px;
  }

  .courses-hero__art {
    display: none;
  }
}

@media (max-width: 639px) {
  .courses-toolbar {
    align-items: stretch;
  }

  .courses-tabs {
    gap: 10px;
    overflow-x: auto;
  }

  .courses-tools {
    width: 100%;
  }

  .courses-search {
    flex: 1;
  }

  .courses-search input {
    width: 100%;
  }

  .courses-term {
    flex: none;
    width: 120px;
  }
}

/* 新版课程卡片 */

.course-item {
  display: flex;
  flex-direction: column;
  min-height: 190px;
  padding: 18px;
  border: 1px solid var(--sc-border);
  border-radius: var(--sc-radius-card);
  background: var(--sc-surface);
  box-shadow: var(--sc-shadow);
  transition: transform 0.2s, box-shadow 0.2s;
}

.course-item:hover {
  transform: translateY(-3px);
  box-shadow: var(--sc-shadow-hover);
}

.course-item.is-deleting {
  opacity: 0.55;
  pointer-events: none;
}

.course-item__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.course-item__icon {
  display: grid;
  place-items: center;
  width: 44px;
  height: 44px;
  border-radius: 12px;
  color: var(--sc-primary);
  background: var(--sc-primary-soft);
}

.course-item__body {
  flex: 1;
  min-width: 0;
  margin-top: 16px;
}

.course-item__body h3 {
  overflow: hidden;
  color: var(--sc-text);
  font-size: 16px;
  font-weight: 600;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.course-item__meta {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: 8px;
}

.course-item__meta span {
  padding: 3px 8px;
  border-radius: 6px;
  background: var(--sc-primary-soft);
  color: var(--sc-primary-strong);
  font-size: 12px;
}

.course-item__body p {
  display: -webkit-box;
  overflow: hidden;
  margin-top: 8px;
  color: var(--sc-text-secondary);
  font-size: var(--sc-font-caption);
  line-height: 1.6;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
}

.course-item__footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  margin-top: 16px;
  padding-top: 12px;
  border-top: 1px solid var(--sc-border);
}

.course-status {
  color: var(--sc-success);
  font-size: var(--sc-font-hint);
}

.course-status.is-disabled {
  color: var(--sc-text-placeholder);
}

.course-date {
  color: var(--sc-text-placeholder);
  font-size: var(--sc-font-hint);
}
</style>
