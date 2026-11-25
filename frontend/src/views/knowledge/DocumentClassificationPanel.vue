<script setup lang="ts">
import { ref, watch } from 'vue'

import type { BigIntId } from '@/api/types'
import type { KnowledgeDoc } from '@/api/knowledge'
import {
  batchAssignDocumentTags,
  createCourseChapter,
  getDocumentTags,
  listCourseChapters,
} from '@/api/document-classification'
import type { CourseChapter } from '@/api/document-classification'
import {
  listKnowledgePoints,
} from '@/api/learning-path'
import type { KnowledgePoint } from '@/api/learning-path'
import { notifyError, notifySuccess, notifyWarning } from '@/utils/notify'

const props = defineProps<{
  courseId: BigIntId | null
  documents: KnowledgeDoc[]
  canManage: boolean
}>()
const emit = defineEmits<{
  changed: []
}>()

const chapters = ref<CourseChapter[]>([])
const points = ref<KnowledgePoint[]>([])

const selectedDocs = ref<BigIntId[]>([])
const selectedChapters = ref<BigIntId[]>([])
const selectedPoints = ref<BigIntId[]>([])

const chapterName = ref('')
const chapterOrder = ref(0)

const loading = ref(false)
const saving = ref(false)

let version = 0

async function loadClassification(): Promise<void> {
  const id = props.courseId
  const current = ++version

  chapters.value = []
  points.value = []

  if (!id) {
    loading.value = false
    return
  }

  loading.value = true

  try {
    const [chapterResult, pointResult] = await Promise.all([
      listCourseChapters(id),
      listKnowledgePoints(id),
    ])

    if (current !== version) return

    chapters.value = chapterResult
    points.value = pointResult
  } catch (error) {
    if (current === version) notifyError(error)
  } finally {
    if (current === version) loading.value = false
  }
}

watch(
  () => props.courseId,
  () => {
    selectedDocs.value = []
    selectedChapters.value = []
    selectedPoints.value = []
    void loadClassification()
  },
  { immediate: true },
)

watch(
  () => props.documents,
  (documents) => {
    const allowed = new Set(documents.map(document => document.id))
    selectedDocs.value = selectedDocs.value.filter(
      id => allowed.has(id),
    )
  },
)

async function addChapter(): Promise<void> {
  const id = props.courseId
  const name = chapterName.value.trim()

  if (!id || !name) {
    notifyWarning('请输入章节名称')
    return
  }

  saving.value = true

  try {
    await createCourseChapter(id, {
      name,
      sortOrder: chapterOrder.value,
    })

    chapterName.value = ''
    notifySuccess('章节创建成功')
    emit('changed')
    await loadClassification()
  } catch (error) {
    notifyError(error)
  } finally {
    saving.value = false
  }
}

async function loadSelectedTags(): Promise<void> {
  const course = props.courseId

  if (!course || selectedDocs.value.length !== 1) {
    notifyWarning('请选择一份文档读取已有分类')
    return
  }

  const documentId = selectedDocs.value[0]
  if (documentId === undefined) {
    notifyWarning('请选择一份文档读取已有分类')
    return
  }

  const current = version
  loading.value = true

  try {
    const result = await getDocumentTags(course, documentId)

    if (
      current !== version ||
      props.courseId !== course ||
      selectedDocs.value.length !== 1 ||
      selectedDocs.value[0] !== documentId
    ) return

    selectedChapters.value = result.chapterIds
    selectedPoints.value = result.pointIds
  } catch (error) {
    if (current === version) notifyError(error)
  } finally {
    if (current === version) loading.value = false
  }
}

async function saveAssignments(): Promise<void> {
  const course = props.courseId

  if (!course || selectedDocs.value.length === 0) {
    notifyWarning('请先选择需要分类的文档')
    return
  }

  saving.value = true

  try {
    const result = await batchAssignDocumentTags(
      course,
      {
        documentIds: [...selectedDocs.value],
        chapterIds: [...selectedChapters.value],
        pointIds: [...selectedPoints.value],
      },
    )

    notifySuccess(
      `已更新 ${result.updatedDocuments} 份文档的分类`,
    )
    emit('changed')
    selectedDocs.value = []
    selectedChapters.value = []
    selectedPoints.value = []
  } catch (error) {
    notifyError(error)
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <div class="classification-panel" v-loading="loading">
    <div class="classification-heading">
      <h3>课程资源分类</h3>
      <p>按章节和知识点组织课程 PDF 资料</p>
    </div>

    <template v-if="courseId">
      <div v-if="canManage" class="classification-section">
        <h4>创建课程章节</h4>

        <el-input
          v-model="chapterName"
          maxlength="200"
          placeholder="例如：第一章 Java 基础"
          :disabled="saving"
        />

        <el-input-number
          v-model="chapterOrder"
          :min="0"
          :disabled="saving"
        />

        <el-button
          type="primary"
          :loading="saving"
          :disabled="saving"
          @click="addChapter"
        >
          添加章节
        </el-button>
      </div>

      <div class="classification-section">
        <h4>章节列表</h4>

        <el-empty
          v-if="chapters.length === 0"
          description="当前还没有课程章节"
          :image-size="65"
        />

        <el-tag
          v-for="chapter in chapters"
          :key="chapter.id"
          style="margin-right: 8px; margin-bottom: 8px"
        >
          {{ chapter.name }}
        </el-tag>
      </div>

      <div v-if="canManage" class="classification-section">
        <h4>批量挂接资料</h4>

        <el-alert
          title="保存会替换所选文档原有的章节与知识点关联。编辑单份文档时，可先读取其已有分类。"
          type="info"
          :closable="false"
        />

        <el-form label-position="top">
          <el-form-item label="选择 PDF 文档">
            <el-select
              v-model="selectedDocs"
              multiple
              filterable
              collapse-tags
              style="width: 100%"
              placeholder="可同时选择多份资料"
              :disabled="saving"
            >
              <el-option
                v-for="doc in documents"
                :key="doc.id"
                :label="doc.originalName"
                :value="doc.id"
              />
            </el-select>
          </el-form-item>

          <el-button
            :disabled="selectedDocs.length !== 1 || loading || saving"
            @click="loadSelectedTags"
          >
            读取所选文档已有分类
          </el-button>

          <el-form-item label="挂接章节">
            <el-select
              v-model="selectedChapters"
              multiple
              filterable
              collapse-tags
              style="width: 100%"
              placeholder="选择一个或多个章节"
              :disabled="saving"
            >
              <el-option
                v-for="chapter in chapters"
                :key="chapter.id"
                :label="chapter.name"
                :value="chapter.id"
              />
            </el-select>
          </el-form-item>

          <el-form-item label="挂接知识点">
            <el-select
              v-model="selectedPoints"
              multiple
              filterable
              collapse-tags
              style="width: 100%"
              placeholder="选择一个或多个知识点"
              :disabled="saving"
            >
              <el-option
                v-for="point in points"
                :key="point.id"
                :label="point.title"
                :value="point.id"
              />
            </el-select>
          </el-form-item>

          <el-button
            type="primary"
            :loading="saving"
            :disabled="selectedDocs.length === 0 || saving"
            @click="saveAssignments"
          >
            保存分类挂接
          </el-button>
        </el-form>
      </div>
    </template>
  </div>
</template>

<style scoped>
.classification-panel {
  display: grid;
  gap: 18px;
  padding: 18px;
}

.classification-heading h3 {
  margin: 0 0 6px;
  font-size: 17px;
}

.classification-heading p {
  margin: 0;
  color: var(--sc-text-secondary);
  font-size: 13px;
}

.classification-section {
  display: grid;
  gap: 12px;
}

.classification-section h4 {
  margin: 0;
  font-size: 14px;
}

.classification-section :deep(.el-form-item) {
  margin-bottom: 12px;
}
</style>
