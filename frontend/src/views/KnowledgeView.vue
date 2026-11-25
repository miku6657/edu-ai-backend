<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'

import { ElMessageBox } from 'element-plus'

import { listAccessibleCourses } from '@/api/course-access'
import type { Course } from '@/api/course'
import {
  listCourseChapters,
  listDocumentClassifications,
} from '@/api/document-classification'
import type {
  CourseChapter,
  DocumentClassification,
} from '@/api/document-classification'
import { listKnowledgePoints } from '@/api/learning-path'
import type { KnowledgePoint } from '@/api/learning-path'
import {
  deleteDoc,
  listDocs,
  RESOURCE_TYPE_OPTIONS,
  resourceTypeLabel,
  retryDoc,
  uploadDoc,
  versionStatusLabel,
} from '@/api/knowledge'
import type {
  KnowledgeDoc,
  DocumentStatus,
  ResourceType,
  ResourceTypeSelection,
} from '@/api/knowledge'
import type { BigIntId } from '@/api/types'
import BaseCard from '@/components/ui/BaseCard.vue'
import EmptyState from '@/components/ui/EmptyState.vue'
import TagBadge from '@/components/ui/TagBadge.vue'
import { useAuthStore } from '@/stores/auth'
import { useCourseStore } from '@/stores/course'
import { notifyError, notifySuccess, notifyWarning } from '@/utils/notify'
import DocumentClassificationPanel from '@/views/knowledge/DocumentClassificationPanel.vue'
import DocumentPreviewDialog from '@/views/knowledge/DocumentPreviewDialog.vue'
import DocumentVersionDialog from '@/views/knowledge/DocumentVersionDialog.vue'
import {
  documentStatusLabel,
  documentStatusTagType,
  formatFileSize,
  hasPendingDocs,
  uploadFileError,
  UPLOAD_ACCEPT,
} from '@/views/knowledge/knowledge-form'

/**
 * 课程知识库（P3，参考图 `docs/ui_picture/我的课程.png` 与基调图中的知识库页）。
 *
 * 所有文档状态、计数、诊断与操作都来自真实接口；上传成功后进入异步解析状态，
 * 不在前端伪造 `READY`。新增拖拽上传区，仍复用同一套 `uploadDoc` 与文件校验。
 */
type Tone = 'primary' | 'success' | 'warning' | 'danger' | 'neutral'

const courseStore = useCourseStore()
const auth = useAuthStore()
const canManageDocuments = computed(
  () => auth.isTeacher || auth.isAdmin,
)

const courses = ref<Course[]>([])
const docs = ref<KnowledgeDoc[]>([])
const selectedCourseId = ref<BigIntId | null>(
  courseStore.selectedCourseId,
)
const filterStatus = ref<DocumentStatus | ''>('')
const uploadResourceType =
  ref<ResourceTypeSelection>('AUTO')
const uploadSourceInfo = ref('')
const filterResourceType =
  ref<ResourceType | ''>('')
const searchKeyword = ref('')
const filterChapterId = ref('')
const filterPointId = ref('')
const chapters = ref<CourseChapter[]>([])
const knowledgePoints = ref<KnowledgePoint[]>([])
const documentClassifications = ref<DocumentClassification[]>([])
const classificationLoading = ref(false)
const classificationError = ref('')
const previewVisible = ref(false)
const previewDoc = ref<
  Pick<KnowledgeDoc, 'id' | 'originalName'> | null
>(null)
const versionDialogVisible = ref(false)
const versionDocument = ref<KnowledgeDoc | null>(null)
const resourceFilterOptions = [
  { value: '', label: '全部类型' },
  ...RESOURCE_TYPE_OPTIONS.filter(
    (item) => item.value !== 'AUTO',
  ),
]
const loadingCourses = ref(true)
const loadingDocs = ref(false)
const uploading = ref(false)
const dragActive = ref(false)
const actionDocId = ref<BigIntId | null>(null)
const loadError = ref('')
const fileInput = ref<HTMLInputElement | null>(null)
const compact = ref(false)
const POLL_INTERVAL_MS = 4000
let pollTimer: number | null = null
let classificationVersion = 0
let classificationDocumentKey: string | null = null

const resourceVersionKey = computed(() => {
  const resourceId = versionDocument.value?.resourceId

  if (!resourceId) {
    return ''
  }

  return docs.value
    .filter(doc => doc.resourceId === resourceId)
    .map(doc => [
      doc.id,
      doc.status,
      doc.versionStatus,
      doc.versionNo,
    ].join(':'))
    .sort()
    .join('|')
})

const compactQuery =
  typeof window === 'undefined' ? null : window.matchMedia('(max-width: 640px)')
function syncCompact(): void {
  compact.value = compactQuery?.matches ?? false
}

const currentCourse = computed(
  () =>
    courses.value.find(
      (item) => item.id === selectedCourseId.value,
    ) ?? null,
)

const classificationMap = computed(() => new Map(
  documentClassifications.value.map(
    item => [String(item.documentId), item] as const,
  ),
))

function classificationOf(
  docId: BigIntId,
): DocumentClassification | undefined {
  return classificationMap.value.get(String(docId))
}

const visibleDocs = computed(() => {
  const keywords = searchKeyword.value
    .trim()
    .toLowerCase()
    .split(/\s+/)
    .filter(Boolean)

  return docs.value.filter((doc) => {
    if (
      filterStatus.value !== ''
      && doc.status !== filterStatus.value
    ) {
      return false
    }

    if (
      filterResourceType.value !== ''
      && doc.resourceType !== filterResourceType.value
    ) {
      return false
    }

    const classification = classificationOf(doc.id)
    const docChapters = classification?.chapters ?? []
    const docPoints = classification?.points ?? []

    if (
      filterChapterId.value !== ''
      && !docChapters.some(
        chapter =>
          String(chapter.id) === String(filterChapterId.value),
      )
    ) {
      return false
    }

    if (
      filterPointId.value !== ''
      && !docPoints.some(
        point =>
          String(point.id) === String(filterPointId.value),
      )
    ) {
      return false
    }

    const searchableText = [
      doc.originalName,
      doc.sourceInfo ?? '',
      resourceTypeLabel(doc.resourceType),
      ...docChapters.map(chapter => chapter.name),
      ...docPoints.map(point => point.name),
    ]
      .join(' ')
      .toLowerCase()

    return keywords.every(keyword => searchableText.includes(keyword))
  })
})

const pendingCount = computed(
  () => docs.value.filter(doc => hasPendingDocs([doc])).length,
)

const statusOptions: Array<{ value: DocumentStatus | ''; label: string }> = [
  { value: '', label: '全部课件' },
  { value: 'UPLOADED', label: '待解析' },
  { value: 'PARSING', label: '解析中' },
  { value: 'PROCESSING', label: '解析中' },
  { value: 'READY', label: '已就绪' },
  { value: 'FAILED', label: '处理失败' },
  { value: 'DELETING', label: '删除中' },
  { value: 'DELETE_FAILED', label: '删除失败' },
  { value: 'DELETED', label: '已删除' },
]

let requestSequence = 0

watch(
  selectedCourseId,
  () => {
    searchKeyword.value = ''
    filterChapterId.value = ''
    filterPointId.value = ''
    chapters.value = []
    knowledgePoints.value = []
    documentClassifications.value = []
    classificationDocumentKey = null
    previewVisible.value = false
    previewDoc.value = null
    versionDialogVisible.value = false
    versionDocument.value = null
    void loadResourceClassifications()
  },
  { immediate: true },
)

onMounted(() => {
  syncCompact()
  compactQuery?.addEventListener('change', syncCompact)
  void bootstrap()
})

onUnmounted(() => {
  compactQuery?.removeEventListener('change', syncCompact)
  stopPolling()
})

function syncPolling(): void {
  if (pendingCount.value > 0) {
    if (pollTimer === null) {
      pollTimer = window.setInterval(() => {
        void loadDocs()
      }, POLL_INTERVAL_MS)
    }
  } else {
    stopPolling()
  }
}

function stopPolling(): void {
  if (pollTimer !== null) {
    window.clearInterval(pollTimer)
    pollTimer = null
  }
}

async function bootstrap(): Promise<void> {
  await loadCourses()
  if (selectedCourseId.value !== null) {
    await loadDocs()
  }
}

async function loadCourses(): Promise<void> {
  loadingCourses.value = true
  loadError.value = ''
  try {
    courses.value = await listAccessibleCourses()
    const exists = courses.value.some(
      (item) => item.id === selectedCourseId.value,
    )
    if (!exists) {
      selectedCourseId.value = courses.value[0]
        ? courses.value[0].id
        : null
    }
    courseStore.select(selectedCourseId.value)
  } catch (error) {
    loadError.value = error instanceof Error ? error.message : '课程列表读取失败。'
    notifyError(error)
  } finally {
    loadingCourses.value = false
  }
}

async function loadDocs(): Promise<void> {
  const courseId = selectedCourseId.value
  const sequence = ++requestSequence
  if (courseId === null) {
    docs.value = []
    stopPolling()
    return
  }
  loadingDocs.value = true
  try {
    const result = await listDocs(courseId)
    if (sequence !== requestSequence || courseId !== selectedCourseId.value) {
      return
    }
    const nextClassificationKey = classificationKeyFor(result)
    docs.value = result
    syncPolling()
    if (nextClassificationKey !== classificationDocumentKey) {
      classificationDocumentKey = nextClassificationKey
      void loadResourceClassifications()
    }
  } catch (error) {
    if (sequence !== requestSequence || courseId !== selectedCourseId.value) {
      return
    }
    docs.value = []
    notifyError(error)
    syncPolling()
  } finally {
    if (sequence === requestSequence) {
      loadingDocs.value = false
    }
  }
}

function classificationKeyFor(documents: KnowledgeDoc[]): string {
  const documentIds = documents
    .map(doc => String(doc.id))
    .sort()
  const readyDocumentIds = documents
    .filter(doc => doc.status === 'READY')
    .map(doc => String(doc.id))
    .sort()

  return JSON.stringify([documentIds, readyDocumentIds])
}

async function loadResourceClassifications(): Promise<void> {
  const courseId = selectedCourseId.value
  const version = ++classificationVersion

  classificationError.value = ''

  if (!courseId) {
    chapters.value = []
    knowledgePoints.value = []
    documentClassifications.value = []
    classificationLoading.value = false
    return
  }

  classificationLoading.value = true

  try {
    const [
      chapterResult,
      pointResult,
      classificationResult,
    ] = await Promise.all([
      listCourseChapters(courseId),
      listKnowledgePoints(courseId),
      listDocumentClassifications(courseId),
    ])

    if (
      version !== classificationVersion
      || selectedCourseId.value !== courseId
    ) {
      return
    }

    chapters.value = chapterResult
    knowledgePoints.value = pointResult
    documentClassifications.value = classificationResult
  } catch (error) {
    if (version !== classificationVersion) {
      return
    }

    classificationError.value =
      error instanceof Error ? error.message : '资源分类加载失败'
    notifyError(error)
  } finally {
    if (version === classificationVersion) {
      classificationLoading.value = false
    }
  }
}

function selectCourse(courseId: BigIntId): void {
  if (selectedCourseId.value === courseId) {
    return
  }
  selectedCourseId.value = courseId
  courseStore.select(courseId)
  docs.value = []
  void loadDocs()
}

function onFilterChange(): void {
  // 当前 Java 接口不支持服务端状态筛选，因此只应用前端本地筛选。
}

function openPreview(doc: KnowledgeDoc): void {
  if (doc.status !== 'READY') {
    notifyWarning('资料尚未完成处理，暂时无法预览')
    return
  }

  previewDoc.value = doc
  previewVisible.value = true
}

function openVersionManager(doc: KnowledgeDoc): void {
  if (!canManageDocuments.value) {
    notifyWarning('无权管理课程资源版本')
    return
  }

  versionDocument.value = doc
  versionDialogVisible.value = true
}

function openHistoricalPreview(
  doc: Pick<KnowledgeDoc, 'id' | 'originalName'>,
): void {
  previewDoc.value = doc
  previewVisible.value = true
}

async function onVersionChanged(): Promise<void> {
  await loadDocs()
}

function resetResourceFilters(): void {
  searchKeyword.value = ''
  filterStatus.value = ''
  filterResourceType.value = ''
  filterChapterId.value = ''
  filterPointId.value = ''
}

function chooseFile(): void {
  if (
    currentCourse.value === null ||
    currentCourse.value.status !== 1 ||
    uploading.value
  ) {
    return
  }
  fileInput.value?.click()
}

async function onFileSelected(event: Event): Promise<void> {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  input.value = ''
  if (file === undefined) {
    return
  }
  await uploadFile(file)
}

async function uploadFile(file: File): Promise<void> {
  const course = currentCourse.value
  if (course === null || course.status !== 1 || uploading.value) {
    return
  }
  const error = uploadFileError(file)
  if (error !== null) {
    notifyWarning(error)
    return
  }
  if (uploadSourceInfo.value.trim().length > 255) {
    notifyWarning('资源来源不能超过255个字符')
    return
  }
  uploading.value = true
  try {
    const uploaded = await uploadDoc(
      course.id,
      file,
      {
        resourceType: uploadResourceType.value,
        sourceInfo: uploadSourceInfo.value,
      },
    )
    notifySuccess(
      `《${uploaded.originalName}》上传成功，当前状态：${uploaded.status}`,
    )
    await loadDocs()
  } catch (uploadError) {
    notifyError(uploadError)
  } finally {
    uploading.value = false
  }
}

function onDragOver(): void {
  if (currentCourse.value !== null && currentCourse.value.status === 1 && !uploading.value) {
    dragActive.value = true
  }
}

function onDragLeave(): void {
  dragActive.value = false
}

function onDrop(event: DragEvent): void {
  dragActive.value = false
  const file = event.dataTransfer?.files?.[0]
  if (file !== undefined) {
    void uploadFile(file)
  }
}

async function onDelete(doc: KnowledgeDoc): Promise<void> {
  try {
    await ElMessageBox.confirm(
      `确定删除课件《${doc.originalName}》吗？`,
      '删除课件',
      { type: 'warning', confirmButtonText: '确认删除', cancelButtonText: '取消' },
    )
  } catch {
    return
  }
  actionDocId.value = doc.id
  try {
    await deleteDoc(doc.id)
    notifySuccess('课件删除成功')
    await loadDocs()
    await loadResourceClassifications()
  } catch (error) {
    notifyError(error)
  } finally {
    actionDocId.value = null
  }
}

async function onRetry(doc: KnowledgeDoc): Promise<void> {
  actionDocId.value = doc.id
  try {
    await retryDoc(doc.id)
    notifySuccess(`《${doc.originalName}》已提交重新处理`)
    await loadDocs()
  } catch (error) {
    notifyError(error)
  } finally {
    actionDocId.value = null
  }
}

function formatTime(value: string | null): string {
  return value
    ? new Date(value).toLocaleString()
    : '—'
}

function canRetry(doc: KnowledgeDoc): boolean {
  return doc.status === 'FAILED'
}

function canDelete(doc: KnowledgeDoc): boolean {
  if (doc.versionStatus === 'HISTORY') {
    return false
  }

  if (
    doc.status === 'DELETING'
    || doc.status === 'DELETED'
    || doc.status === 'PROCESSING'
    || doc.status === 'PARSING'
  ) {
    return false
  }

  if (
    doc.versionStatus === 'CURRENT'
    && docs.value.some(
      other =>
        other.id !== doc.id
        && other.resourceId === doc.resourceId
        && other.status !== 'DELETED',
    )
  ) {
    return false
  }

  return true
}

function documentTone(doc: KnowledgeDoc): Tone {
  const tone = documentStatusTagType(doc.status)
  return tone === 'info' ? 'neutral' : tone
}
</script>

<template>
  <div class="knowledge-page">
    <section class="knowledge-hero">
      <div class="knowledge-hero__copy">
        <span class="knowledge-hero__eyebrow">COURSE KNOWLEDGE</span>
        <h2>{{ currentCourse?.name ?? '课程知识库' }}</h2>
        <p>
          选择课程并上传学习资料，系统会自动解析课件并建立知识库，
          为 AI 智能问答提供知识支持。
        </p>
      </div>

      <div class="knowledge-hero__metrics">
        <div class="knowledge-metric">
          <span>课件总数</span>
          <strong>{{ docs.length }}</strong>
        </div>

        <div class="knowledge-metric">
          <span>解析中</span>
          <strong>{{ pendingCount }}</strong>
        </div>

        <div class="knowledge-metric">
          <span>已就绪</span>
          <strong>{{ docs.filter((doc) => doc.status === 'READY').length }}</strong>
        </div>

        <div class="knowledge-metric">
          <span>处理失败</span>
          <strong>{{ docs.filter((doc) => doc.status === 'FAILED').length }}</strong>
        </div>
      </div>
    </section>

    <div class="knowledge-layout">
      <aside class="knowledge-rail">
        <BaseCard title="我的课程">
          <template #actions>
            <router-link class="card-link" :to="{ name: 'courses' }">管理课程</router-link>
          </template>

          <div v-if="loadingCourses" class="rail-skeleton" aria-hidden="true">
            <div v-for="index in 3" :key="index" class="rail-skeleton__row" />
          </div>

          <div v-else-if="loadError !== ''" class="module-error">
            <p>{{ loadError }}</p>
            <el-button size="small" @click="loadCourses">重新加载</el-button>
          </div>

          <EmptyState
            v-else-if="courses.length === 0"
            image="/illustrations/scene-study-desk.png"
            title="还没有课程空间"
            :description="canManageDocuments
              ? '先创建课程，再上传课件。'
              : '加入课程后即可查看课程资料。'"
            compact
          >
            <template #action>
              <router-link class="card-link" :to="{ name: 'courses' }">
                {{ canManageDocuments ? '去创建课程' : '去课程空间' }}
              </router-link>
            </template>
          </EmptyState>

          <div v-else class="rail-courses">
            <button
              v-for="item in courses"
              :key="item.id"
              type="button"
              class="rail-course"
              :class="{ 'rail-course--active': item.id === selectedCourseId }"
              @click="selectCourse(item.id)"
            >
              <span class="rail-course__top">
                <strong>{{ item.name }}</strong>
                <TagBadge
                  :label="item.status === 1 ? '正常' : '已停用'"
                  :tone="item.status === 1 ? 'success' : 'neutral'"
                />
              </span>
              <span class="rail-course__meta">
                {{ item.description || '暂无课程描述' }}
              </span>
            </button>
          </div>
        </BaseCard>
      </aside>

      <section class="knowledge-main">
        <BaseCard padding="none">
          <template #header>
            <div class="knowledge-header">
              <div class="knowledge-header__title">
                <span>{{ currentCourse?.name ?? '未选择课程' }}</span>
              </div>
              <div class="knowledge-header__actions">
                <el-select
                  v-model="filterStatus"
                  size="small"
                  class="knowledge-filter"
                  placeholder="全部课件"
                  @change="onFilterChange"
                >
                  <el-option
                    v-for="item in statusOptions"
                    :key="item.value"
                    :label="item.label"
                    :value="item.value"
                  />
                </el-select>
                <el-select
                  v-model="filterResourceType"
                  size="small"
                  class="knowledge-filter"
                  placeholder="全部类型"
                >
                  <el-option
                    v-for="item in resourceFilterOptions"
                    :key="item.value"
                    :label="item.label"
                    :value="item.value"
                  />
                </el-select>
                <el-button size="small" :disabled="loadingDocs" @click="loadDocs">刷新</el-button>
                <el-button
                  v-if="canManageDocuments"
                  type="primary"
                  size="small"
                  :loading="uploading"
                  :disabled="currentCourse === null || currentCourse.status !== 1"
                  @click="chooseFile"
                >
                  上传课件
                </el-button>
              </div>
            </div>
          </template>

          <div class="knowledge-body">
            <EmptyState
              v-if="currentCourse === null"
              image="/illustrations/scene-study-desk.png"
              title="请先选择课程"
              description="在左侧选择或创建课程后，即可上传与管理课件。"
            >
              <template #action>
                <router-link class="card-link" :to="{ name: 'courses' }">去课程空间</router-link>
              </template>
            </EmptyState>

            <template v-else>
              <div
                v-if="canManageDocuments"
                class="resource-upload-options"
              >
                <div class="resource-upload-field">
                  <span>资料类型</span>

                  <el-select
                    v-model="uploadResourceType"
                    :disabled="uploading"
                    style="width: 200px"
                  >
                    <el-option
                      v-for="option in RESOURCE_TYPE_OPTIONS"
                      :key="option.value"
                      :value="option.value"
                      :label="option.label"
                    />
                  </el-select>
                </div>

                <div class="resource-upload-field">
                  <span>资料来源</span>

                  <el-input
                    v-model="uploadSourceInfo"
                    :disabled="uploading"
                    maxlength="255"
                    show-word-limit
                    placeholder="例如：教师自编 / 教材配套资料"
                  />
                </div>

                <p class="knowledge-muted">
                  选择“自动识别”时，将根据 PDF 文件名判断资料类型。
                  如需准确分类，可以直接指定类型。
                </p>
              </div>

              <div
                v-if="canManageDocuments"
                class="upload-zone"
                :class="{
                  'upload-zone--active': dragActive,
                  'upload-zone--disabled':
                    uploading || currentCourse.status !== 1,
                }"
                role="button"
                tabindex="0"
                aria-label="上传课件"
                @click="chooseFile"
                @keydown.enter.prevent="chooseFile"
                @keydown.space.prevent="chooseFile"
                @dragover.prevent="onDragOver"
                @dragleave.prevent="onDragLeave"
                @drop.prevent="onDrop"
              >
                <span class="upload-zone__art">
                  <img src="/illustrations/status-loading.png" alt="" loading="lazy" />
                </span>
                <span class="upload-zone__text">
                  <strong>{{ uploading ? '正在上传…' : '拖拽课件到此处，或点击上传' }}</strong>
                  <small>支持 PDF；扩展名与大小由服务端再次校验</small>
                </span>
                <el-button
                  type="primary"
                  plain
                  size="small"
                  :loading="uploading"
                  :disabled="currentCourse.status !== 1"
                  @click.stop="chooseFile"
                >
                  选择文件
                </el-button>
              </div>

              <input
                v-if="canManageDocuments"
                ref="fileInput"
                type="file"
                :accept="UPLOAD_ACCEPT"
                hidden
                @change="onFileSelected"
              />

              <div class="docs-header">
                <span>课件列表</span>
                <span class="docs-header__count">{{ visibleDocs.length }}</span>
              </div>

              <div class="resource-search-panel">
                <el-input
                  v-model="searchKeyword"
                  clearable
                  maxlength="100"
                  placeholder="搜索文件名、章节、知识点或资料来源"
                  class="resource-search-input"
                />

                <div class="resource-search-filters">
                  <el-select
                    v-model="filterChapterId"
                    clearable
                    filterable
                    :value-on-clear="''"
                    :loading="classificationLoading"
                    placeholder="全部章节"
                  >
                    <el-option
                      label="全部章节"
                      value=""
                    />

                    <el-option
                      v-for="chapter in chapters"
                      :key="chapter.id"
                      :label="chapter.name"
                      :value="chapter.id"
                    />
                  </el-select>

                  <el-select
                    v-model="filterPointId"
                    clearable
                    filterable
                    :value-on-clear="''"
                    :loading="classificationLoading"
                    placeholder="全部知识点"
                  >
                    <el-option
                      label="全部知识点"
                      value=""
                    />

                    <el-option
                      v-for="point in knowledgePoints"
                      :key="point.id"
                      :label="point.title"
                      :value="point.id"
                    />
                  </el-select>

                  <el-button @click="resetResourceFilters">
                    重置筛选
                  </el-button>
                </div>

                <el-alert
                  v-if="classificationError"
                  :title="classificationError"
                  type="warning"
                  :closable="false"
                />

                <el-button
                  v-if="classificationError"
                  text
                  type="primary"
                  @click="loadResourceClassifications"
                >
                  重新加载课程分类
                </el-button>

                <div class="resource-search-count">
                  共找到 {{ visibleDocs.length }} 份资料
                </div>
              </div>

              <el-skeleton v-if="loadingDocs" :rows="4" animated />
              <EmptyState
                v-else-if="visibleDocs.length === 0"
                image="/illustrations/scene-study-desk.png"
                :title="docs.length === 0 ? '当前课程还没有课件' : '没有符合条件的课件'"
                :description="canManageDocuments
                  ? '上传 PDF 课件后，系统会自动处理课件。'
                  : '当前课程暂无可查看的课件。'"
                compact
              >
                <template #action>
                  <el-button
                    v-if="docs.length === 0 && canManageDocuments"
                    type="primary"
                    size="small"
                    @click="chooseFile"
                  >
                    上传课件
                  </el-button>
                  <el-button
                    v-else-if="docs.length > 0"
                    size="small"
                    @click="resetResourceFilters"
                  >
                    查看全部
                  </el-button>
                </template>
              </EmptyState>
              <el-table v-else :data="visibleDocs" row-key="id" size="small">
                <el-table-column label="课件" :min-width="compact ? 120 : 220">
                  <template #default="{ row }">
                    <div>{{ row.originalName }}</div>
                    <div class="knowledge-muted">
                      {{ formatFileSize(row.fileSize) }}
                    </div>
                  </template>
                </el-table-column>
                <el-table-column
                  label="资料类型"
                  :width="compact ? 100 : 120"
                >
                  <template #default="{ row }">
                    <el-tag type="info" size="small">
                      {{ resourceTypeLabel(row.resourceType) }}
                    </el-tag>
                  </template>
                </el-table-column>
                <el-table-column
                  label="所属章节"
                  min-width="160"
                >
                  <template #default="{ row }">
                    <div class="resource-tags">
                      <el-tag
                        v-for="chapter in (classificationOf(row.id)?.chapters ?? [])"
                        :key="chapter.id"
                        size="small"
                        type="info"
                      >
                        {{ chapter.name }}
                      </el-tag>

                      <span
                        v-if="!classificationOf(row.id)?.chapters.length"
                        class="knowledge-muted"
                      >
                        未挂接
                      </span>
                    </div>
                  </template>
                </el-table-column>
                <el-table-column
                  label="知识点"
                  min-width="180"
                >
                  <template #default="{ row }">
                    <div class="resource-tags">
                      <el-tag
                        v-for="point in (classificationOf(row.id)?.points ?? [])"
                        :key="point.id"
                        size="small"
                        type="success"
                      >
                        {{ point.name }}
                      </el-tag>

                      <span
                        v-if="!classificationOf(row.id)?.points.length"
                        class="knowledge-muted"
                      >
                        未挂接
                      </span>
                    </div>
                  </template>
                </el-table-column>
                <el-table-column
                  v-if="!compact"
                  label="资料来源"
                  min-width="150"
                >
                  <template #default="{ row }">
                    {{ row.sourceInfo || '未填写' }}
                  </template>
                </el-table-column>
                <el-table-column
                  v-if="!compact"
                  label="上传人 ID"
                  width="120"
                >
                  <template #default="{ row }">
                    {{ row.uploaderId }}
                  </template>
                </el-table-column>
                <el-table-column
                  label="版本"
                  :width="compact ? 90 : 125"
                >
                  <template #default="{ row }">
                    <div class="resource-version-cell">
                      <strong>V{{ row.versionNo }}</strong>

                      <el-tag
                        :type="
                          row.versionStatus === 'CURRENT'
                            ? 'success'
                            : row.versionStatus === 'PENDING'
                              ? 'warning'
                              : 'info'
                        "
                        size="small"
                      >
                        {{ versionStatusLabel(row.versionStatus) }}
                      </el-tag>
                    </div>
                  </template>
                </el-table-column>
                <el-table-column label="状态" :width="compact ? 85 : 100">
                  <template #default="{ row }">
                    <TagBadge
                      :label="documentStatusLabel(row.status)"
                      :tone="documentTone(row)"
                    />
                  </template>
                </el-table-column>
                <el-table-column v-if="!compact" label="上传时间" width="170">
                  <template #default="{ row }">{{ formatTime(row.createTime) }}</template>
                </el-table-column>
                <el-table-column v-if="!compact" label="处理信息" min-width="150">
                  <template #default="{ row }">
                    <el-tooltip
                      v-if="row.errorMessage"
                      :content="row.errorMessage"
                      placement="top"
                    >
                      <span class="knowledge-error">
                        {{ row.errorMessage }}
                      </span>
                    </el-tooltip>
                    <span v-else class="knowledge-muted">—</span>
                  </template>
                </el-table-column>
                <el-table-column
                  label="操作"
                  :width="compact ? 180 : 260"
                  fixed="right"
                >
                  <template #default="{ row }">
                    <div class="resource-row-actions">
                      <el-button
                        v-if="row.status === 'READY'"
                        text
                        type="primary"
                        @click="openPreview(row)"
                      >
                        预览
                      </el-button>

                      <el-button
                        v-if="
                          canManageDocuments &&
                          (
                            row.versionStatus === 'CURRENT' ||
                            row.versionStatus === 'PENDING'
                          )
                        "
                        text
                        type="primary"
                        @click="openVersionManager(row)"
                      >
                        版本管理
                      </el-button>

                      <el-button
                        v-if="canManageDocuments && canRetry(row)"
                        text
                        type="primary"
                        :loading="actionDocId === row.id"
                        @click="onRetry(row)"
                      >
                        重试
                      </el-button>

                      <el-button
                        v-if="canManageDocuments && canDelete(row)"
                        text
                        type="danger"
                        :loading="actionDocId === row.id"
                        @click="onDelete(row)"
                      >
                        删除
                      </el-button>

                      <span
                        v-if="row.status === 'DELETING'"
                        class="knowledge-muted"
                      >
                        清理中
                      </span>

                      <span
                        v-if="!canManageDocuments && row.status !== 'READY'"
                        class="knowledge-muted"
                      >
                        暂不可预览
                      </span>
                    </div>
                  </template>
                </el-table-column>
              </el-table>
              <DocumentClassificationPanel
                :course-id="selectedCourseId"
                :documents="docs"
                :can-manage="canManageDocuments"
                @changed="loadResourceClassifications"
              />
            </template>
          </div>
        </BaseCard>
      </section>
    </div>
    <DocumentVersionDialog
      v-model="versionDialogVisible"
      :document="versionDocument"
      :can-manage="canManageDocuments"
      :refresh-key="resourceVersionKey"
      @changed="onVersionChanged"
      @preview="openHistoricalPreview"
    />
    <DocumentPreviewDialog
      v-model="previewVisible"
      :document="previewDoc"
    />
  </div>
</template>

<style scoped>
.knowledge-page {
  display: flex;
  flex-direction: column;
  gap: var(--sc-card-gap);
}

.knowledge-hero {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 24px;
  padding: 24px 28px;
  border: 1px solid var(--sc-border);
  border-radius: 16px;
  background: linear-gradient(
    120deg,
    var(--sc-primary-soft) 0%,
    var(--sc-surface) 58%,
    var(--sc-accent-soft) 100%
  );
  box-shadow: var(--sc-shadow);
}

.knowledge-hero__copy {
  display: flex;
  flex-direction: column;
  gap: 6px;
  max-width: 720px;
  min-width: 0;
}

.knowledge-hero__eyebrow {
  color: var(--sc-primary-strong);
  font-size: var(--sc-font-hint);
  font-weight: 700;
  letter-spacing: 1.2px;
}

.knowledge-hero h2 {
  color: var(--sc-text);
  font-size: var(--sc-font-title);
  font-weight: var(--sc-font-title-weight);
  letter-spacing: -0.3px;
}

.knowledge-hero p {
  color: var(--sc-text-secondary);
  font-size: var(--sc-font-caption);
  line-height: 1.7;
}

.knowledge-hero__metrics {
  display: grid;
  flex: none;
  grid-template-columns: repeat(4, minmax(64px, 1fr));
  gap: 8px;
}

.knowledge-metric {
  min-width: 64px;
  padding: 8px 10px;
  border: 1px solid var(--sc-border);
  border-radius: 10px;
  background: var(--sc-surface);
  text-align: center;
}

.knowledge-metric span {
  display: block;
  color: var(--sc-text-placeholder);
  font-size: var(--sc-font-hint);
}

.knowledge-metric strong {
  display: block;
  margin-top: 2px;
  color: var(--sc-text);
  font-size: 18px;
  font-weight: 600;
}

.knowledge-metric--danger strong {
  color: var(--sc-danger);
}

.knowledge-hero__art {
  display: grid;
  flex: none;
  place-items: center;
  width: 100px;
  height: 88px;
  padding: 6px;
  border-radius: 14px;
  background: #f1f6ff;
}

.knowledge-hero__art img {
  max-width: 100%;
  max-height: 100%;
  object-fit: contain;
}

.knowledge-layout {
  display: grid;
  grid-template-columns: 280px minmax(0, 1fr);
  gap: var(--sc-card-gap);
  align-items: start;
}

.knowledge-rail {
  min-width: 0;
}

.knowledge-main {
  min-width: 0;
}

.knowledge-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
}

.knowledge-header__title {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
  color: var(--sc-text);
  font-size: var(--sc-font-body);
  font-weight: 600;
}

.knowledge-header__actions {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.knowledge-filter {
  width: 152px;
}

.knowledge-body {
  display: flex;
  flex-direction: column;
  gap: 14px;
  padding: 14px var(--sc-card-pad) var(--sc-card-pad);
}

.knowledge-summary {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 16px;
  padding: 12px 14px;
  border: 1px solid var(--sc-border);
  border-radius: var(--sc-radius-card);
  background: var(--sc-surface-muted);
}

.knowledge-summary > :first-child {
  flex: 1;
}

.knowledge-summary__counts {
  display: flex;
  flex: none;
  gap: 12px;
  color: var(--sc-text-placeholder);
  font-size: var(--sc-font-hint);
  white-space: nowrap;
}

.upload-zone {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 14px 16px;
  border: 1px dashed var(--sc-border-strong);
  border-radius: var(--sc-radius-card);
  background: var(--sc-surface-muted);
  cursor: pointer;
  transition: border-color 0.18s ease, background-color 0.18s ease;
}

.upload-zone:hover,
.upload-zone:focus-visible,
.upload-zone--active {
  border-color: var(--sc-primary);
  background: var(--sc-primary-soft);
  outline: none;
}

.upload-zone--disabled {
  cursor: not-allowed;
  opacity: 0.6;
}

.upload-zone__art {
  display: grid;
  flex: none;
  place-items: center;
  width: 54px;
  height: 54px;
  padding: 5px;
  border-radius: 11px;
  background: #f1f6ff;
}

.upload-zone__art img {
  max-width: 100%;
  max-height: 100%;
  object-fit: contain;
}

.upload-zone__text {
  display: flex;
  flex: 1;
  flex-direction: column;
  gap: 3px;
  min-width: 0;
}

.upload-zone__text strong {
  color: var(--sc-text);
  font-size: var(--sc-font-caption);
  font-weight: 600;
}

.upload-zone__text small {
  color: var(--sc-text-placeholder);
  font-size: var(--sc-font-hint);
  line-height: 1.5;
}

.docs-header {
  display: flex;
  align-items: center;
  gap: 8px;
  color: var(--sc-text);
  font-size: var(--sc-font-caption);
  font-weight: 600;
}

.docs-header__count {
  min-width: 18px;
  padding: 0 5px;
  border-radius: var(--sc-radius-pill);
  color: var(--sc-primary-strong);
  background: var(--sc-primary-soft);
  font-size: var(--sc-font-hint);
  line-height: 18px;
  text-align: center;
}

.rail-courses {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.rail-course {
  display: flex;
  flex-direction: column;
  gap: 4px;
  width: 100%;
  padding: 10px 11px;
  border: 1px solid transparent;
  border-radius: 10px;
  color: inherit;
  background: transparent;
  font-family: var(--sc-font-sans);
  text-align: left;
  cursor: pointer;
  transition: background-color 0.18s ease, border-color 0.18s ease;
}

.rail-course:hover {
  background: var(--sc-surface-hover);
}

.rail-course--active {
  border-color: var(--sc-primary);
  background: var(--sc-active);
}

.rail-course__top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  min-width: 0;
}

.rail-course__top strong {
  overflow: hidden;
  color: var(--sc-text);
  font-size: 13px;
  font-weight: 600;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.rail-course__meta {
  color: var(--sc-text-placeholder);
  font-size: var(--sc-font-hint);
}

.rail-course__counts {
  color: var(--sc-text-secondary);
  font-size: var(--sc-font-hint);
}

.rail-skeleton {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.rail-skeleton__row {
  height: 54px;
  border-radius: 10px;
  background: var(--sc-surface-muted);
}

.knowledge-muted {
  color: var(--sc-text-placeholder);
  font-size: var(--sc-font-hint);
}

.knowledge-error {
  color: var(--sc-danger);
  cursor: help;
  font-size: var(--sc-font-hint);
}

.card-link {
  color: var(--sc-primary);
  font-size: var(--sc-font-caption);
  text-decoration: none;
}

.card-link:hover {
  color: var(--sc-primary-strong);
}

.module-error {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  color: var(--sc-text-secondary);
  font-size: var(--sc-font-caption);
}

@media (max-width: 1100px) {
  .knowledge-layout {
    grid-template-columns: 1fr;
  }

  .rail-courses {
    display: grid;
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 860px) {
  .knowledge-hero {
    flex-direction: column;
    align-items: flex-start;
    padding: 20px;
  }

  .knowledge-hero__metrics {
    width: 100%;
  }

  .knowledge-hero__art {
    display: none;
  }

  .rail-courses {
    display: flex;
    flex-direction: row;
    gap: 8px;
    overflow-x: auto;
    overflow-y: hidden;
    padding-bottom: 4px;
  }

  .rail-course {
    flex: 0 0 220px;
  }
}

@media (max-width: 639px) {
  .knowledge-summary {
    flex-direction: column;
    align-items: stretch;
    gap: 8px;
  }

  .knowledge-summary__counts {
    justify-content: space-between;
  }

  .upload-zone {
    align-items: flex-start;
    flex-wrap: wrap;
  }

  .upload-zone__text {
    flex-basis: calc(100% - 68px);
  }

  .upload-zone > :last-child {
    margin-left: 68px;
  }

  .knowledge-hero__metrics {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

.knowledge-hero__metrics {
  grid-template-columns: repeat(4, minmax(64px, 1fr));
}

@media (max-width: 640px) {
  .knowledge-hero {
    flex-direction: column;
    align-items: stretch;
    padding: 18px;
  }

  .knowledge-hero__metrics {
    width: 100%;
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .knowledge-header {
    align-items: flex-start;
  }

  .knowledge-header__actions {
    width: 100%;
  }

  .knowledge-filter {
    flex: 1;
    min-width: 110px;
  }

  .upload-zone {
    flex-wrap: wrap;
  }
}

.resource-upload-options {
  display: grid;
  gap: 12px;
  padding: 14px;
  border: 1px solid var(--sc-border);
  border-radius: 10px;
  background: var(--sc-surface-muted);
}

.resource-upload-field {
  display: flex;
  align-items: center;
  gap: 14px;
}

.resource-upload-field > span {
  flex: 0 0 72px;
  color: var(--sc-text-secondary);
  font-size: 13px;
}

@media (max-width: 639px) {
  .resource-upload-field {
    flex-direction: column;
    align-items: stretch;
  }

  .resource-upload-field > span {
    flex: none;
  }
}

.resource-search-panel {
  display: grid;
  gap: 12px;
  padding: 14px;
  border: 1px solid var(--sc-border);
  border-radius: 10px;
  background: var(--sc-surface-muted);
}

.resource-search-input {
  width: 100%;
}

.resource-search-filters {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 10px;
}

.resource-search-filters :deep(.el-select) {
  width: 180px;
}

.resource-search-count {
  color: var(--sc-text-secondary);
  font-size: 12px;
}

.resource-tags {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 5px;
}

@media (max-width: 640px) {
  .resource-search-filters :deep(.el-select) {
    flex: 1;
    min-width: 130px;
  }
}

.resource-version-cell {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 5px;
}

.resource-version-cell strong {
  font-size: 13px;
}

.resource-row-actions {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 4px 8px;
}

.resource-row-actions :deep(.el-button) {
  margin-left: 0;
}
</style>
