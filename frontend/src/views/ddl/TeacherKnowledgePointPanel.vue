<script setup lang="ts">
import { onMounted, ref, watch } from 'vue'

import type { Course } from '@/api/course'
import { listCourses } from '@/api/course'
import {
  addKnowledgeDependency,
  addKnowledgePoint,
  listKnowledgePoints,
  updateKnowledgePointKeywords,
} from '@/api/learning-path'
import type { KnowledgePoint } from '@/api/learning-path'
import BaseCard from '@/components/ui/BaseCard.vue'
import {
  notifyError,
  notifySuccess,
  notifyWarning,
} from '@/utils/notify'

const courses = ref<Course[]>([])
const courseId = ref('')
const points = ref<KnowledgePoint[]>([])

const loading = ref(false)
const saving = ref(false)

const pointForm = ref({
  title: '',
  estimatedMinutes: 60,
  sortOrder: 0,
  matchKeywords: '',
})

const editingPointId = ref('')
const editingKeywords = ref('')

const dependencyForm = ref({
  pointId: '',
  prerequisiteId: '',
})

let requestVersion = 0

onMounted(async () => {
  try {
    courses.value = await listCourses()
    courseId.value = String(courses.value[0]?.id ?? '')
  } catch (error) {
    notifyError(error)
  }
})

watch(courseId, async (id) => {
  const version = ++requestVersion
  points.value = []
  dependencyForm.value = {
    pointId: '',
    prerequisiteId: '',
  }
  editingPointId.value = ''
  editingKeywords.value = ''

  if (!id) {
    loading.value = false
    return
  }

  loading.value = true
  try {
    const result = await listKnowledgePoints(id)
    if (version === requestVersion) {
      points.value = result
    }
  } catch (error) {
    if (version === requestVersion) {
      notifyError(error)
    }
  } finally {
    if (version === requestVersion) {
      loading.value = false
    }
  }
})

async function refreshPoints(): Promise<void> {
  const id = courseId.value
  if (!id) return

  const version = ++requestVersion
  loading.value = true
  try {
    const result = await listKnowledgePoints(id)
    if (version === requestVersion && courseId.value === id) {
      points.value = result
    }
  } catch (error) {
    if (version === requestVersion) {
      notifyError(error)
    }
  } finally {
    if (version === requestVersion) {
      loading.value = false
    }
  }
}

async function createPoint(): Promise<void> {
  const id = courseId.value
  const title = pointForm.value.title.trim()

  if (!id || !title) {
    notifyWarning('请先选择课程并输入知识点名称')
    return
  }

  if (pointForm.value.estimatedMinutes < 1) {
    notifyWarning('预计学习时间必须大于0')
    return
  }

  saving.value = true
  try {
    await addKnowledgePoint(id, {
      title,
      estimatedMinutes: pointForm.value.estimatedMinutes,
      sortOrder: pointForm.value.sortOrder,
      matchKeywords: pointForm.value.matchKeywords.trim(),
    })

    notifySuccess('知识点已添加')
    pointForm.value.title = ''
    pointForm.value.matchKeywords = ''

    await refreshPoints()
  } catch (error) {
    notifyError(error)
  } finally {
    saving.value = false
  }
}

function startEditKeywords(point: KnowledgePoint): void {
  editingPointId.value = String(point.id)
  editingKeywords.value = point.matchKeywords ?? ''
}

async function saveKeywords(): Promise<void> {
  const selectedCourse = courseId.value
  const pointId = editingPointId.value

  if (!selectedCourse || !pointId) return

  saving.value = true

  try {
    await updateKnowledgePointKeywords(
      selectedCourse,
      pointId,
      editingKeywords.value.trim(),
    )

    if (courseId.value === selectedCourse) {
      editingPointId.value = ''
      editingKeywords.value = ''
      await refreshPoints()
    }

    notifySuccess('匹配关键词已更新')
  } catch (error) {
    notifyError(error)
  } finally {
    saving.value = false
  }
}

async function createDependency(): Promise<void> {
  const id = courseId.value
  const { pointId, prerequisiteId } = dependencyForm.value

  if (!id || !pointId || !prerequisiteId) {
    notifyWarning('请选择知识点及其前置知识点')
    return
  }

  if (pointId === prerequisiteId) {
    notifyWarning('知识点不能依赖自身')
    return
  }

  saving.value = true
  try {
    await addKnowledgeDependency(id, pointId, prerequisiteId)
    notifySuccess('先修关系已保存')
    dependencyForm.value = {
      pointId: '',
      prerequisiteId: '',
    }
  } catch (error) {
    notifyError(error)
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <BaseCard
    title="课程知识点管理"
    subtitle="配置知识点及先修关系，供学生生成学习路径"
  >
    <div class="knowledge-panel">
      <el-select
        v-model="courseId"
        placeholder="请选择需要管理的课程"
        style="width: 100%"
        :disabled="saving"
      >
        <el-option
          v-for="course in courses"
          :key="course.id"
          :label="course.name"
          :value="String(course.id)"
        />
      </el-select>

      <el-divider content-position="left">添加知识点</el-divider>

      <el-form label-position="top">
        <el-form-item label="知识点名称">
          <el-input
            v-model="pointForm.title"
            maxlength="200"
            placeholder="例如：Java 集合框架"
          />
        </el-form-item>

        <el-form-item label="匹配关键词">
          <el-input
            v-model="pointForm.matchKeywords"
            type="textarea"
            :rows="2"
            maxlength="500"
            show-word-limit
            placeholder="例如：HashMap,ArrayList,LinkedList,ConcurrentHashMap"
          />
          <div class="keyword-hint">
            多个关键词用逗号分隔。学生在 AI 问答中提到这些关键词时，
            系统会尝试关联当前知识点。
          </div>
        </el-form-item>

        <el-form-item label="预计学习时间（分钟）">
          <el-input-number
            v-model="pointForm.estimatedMinutes"
            :min="1"
            :max="10080"
          />
        </el-form-item>

        <el-form-item label="默认排序">
          <el-input-number
            v-model="pointForm.sortOrder"
            :min="0"
          />
        </el-form-item>

        <el-button
          type="primary"
          :disabled="!courseId"
          :loading="saving"
          @click="createPoint"
        >
          添加知识点
        </el-button>
      </el-form>

      <el-table
        v-loading="loading"
        :data="points"
        empty-text="当前课程尚未配置知识点"
      >
        <el-table-column
          prop="title"
          label="知识点"
          min-width="180"
        />
        <el-table-column
          label="匹配关键词"
          min-width="240"
        >
          <template #default="{ row }">
            <div v-if="editingPointId === String(row.id)">
              <el-input
                v-model="editingKeywords"
                type="textarea"
                :rows="2"
                maxlength="500"
                :disabled="saving"
              />
              <div class="keyword-actions">
                <el-button
                  type="primary"
                  size="small"
                  :loading="saving"
                  @click="saveKeywords"
                >
                  保存
                </el-button>
                <el-button
                  size="small"
                  :disabled="saving"
                  @click="editingPointId = ''"
                >
                  取消
                </el-button>
              </div>
            </div>

            <div v-else>
              <span>{{ row.matchKeywords || '未配置' }}</span>
              <el-button
                link
                type="primary"
                size="small"
                :disabled="saving"
                @click="startEditKeywords(row)"
              >
                编辑
              </el-button>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="预计学时" width="120">
          <template #default="{ row }">
            {{ (row.estimatedMinutes / 60).toFixed(1) }} 小时
          </template>
        </el-table-column>
        <el-table-column
          prop="sortOrder"
          label="排序"
          width="90"
        />
      </el-table>

      <el-divider content-position="left">设置先修关系</el-divider>

      <el-alert
        type="info"
        :closable="false"
        title="例如：学习集合框架之前，需要先掌握面向对象"
      />

      <el-form label-position="top">
        <el-form-item label="要学习的知识点">
          <el-select
            v-model="dependencyForm.pointId"
            placeholder="选择知识点"
            style="width: 100%"
            :disabled="saving || points.length < 2"
          >
            <el-option
              v-for="point in points"
              :key="point.id"
              :label="point.title"
              :value="String(point.id)"
            />
          </el-select>
        </el-form-item>

        <el-form-item label="必须先学的知识点">
          <el-select
            v-model="dependencyForm.prerequisiteId"
            placeholder="选择前置知识点"
            style="width: 100%"
            :disabled="saving || points.length < 2"
          >
            <el-option
              v-for="point in points"
              :key="point.id"
              :label="point.title"
              :value="String(point.id)"
              :disabled="String(point.id) === dependencyForm.pointId"
            />
          </el-select>
        </el-form-item>

        <el-button
          type="primary"
          :loading="saving"
          :disabled="points.length < 2"
          @click="createDependency"
        >
          保存先修关系
        </el-button>
      </el-form>
    </div>
  </BaseCard>
</template>

<style scoped>
.knowledge-panel {
  display: grid;
  gap: 14px;
}

.knowledge-panel :deep(.el-form-item) {
  margin-bottom: 14px;
}

.keyword-hint {
  margin-top: 6px;
  color: var(--sc-text-secondary);
  font-size: 12px;
  line-height: 1.5;
}

.keyword-actions {
  display: flex;
  gap: 8px;
  margin-top: 8px;
}
</style>
