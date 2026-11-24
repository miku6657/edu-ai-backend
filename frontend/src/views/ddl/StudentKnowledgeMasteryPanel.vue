<script setup lang="ts">
import { ref, watch } from 'vue'

import {
  getMyKnowledgeMastery,
  listKnowledgePoints,
  updateKnowledgeMastery,
} from '@/api/learning-path'
import type { KnowledgePoint } from '@/api/learning-path'
import { notifyError, notifySuccess } from '@/utils/notify'

const props = defineProps<{
  courseId: string
}>()

const points = ref<KnowledgePoint[]>([])
const levels = ref<Record<string, number>>({})
const loading = ref(false)
const savingId = ref<string | null>(null)

let version = 0

watch(
  () => props.courseId,
  async (courseId) => {
    const current = ++version
    points.value = []
    levels.value = {}

    if (!courseId) {
      loading.value = false
      return
    }

    loading.value = true

    try {
      const [list, mastery] = await Promise.all([
        listKnowledgePoints(courseId),
        getMyKnowledgeMastery(courseId),
      ])

      if (current !== version) return

      points.value = list
      levels.value = mastery
    } catch (error) {
      if (current === version) notifyError(error)
    } finally {
      if (current === version) loading.value = false
    }
  },
  { immediate: true },
)

async function changeLevel(
  point: KnowledgePoint,
  level: number,
): Promise<void> {
  const id = String(point.id)
  const selectedCourse = props.courseId
  const oldLevel = levels.value[id] ?? 2

  levels.value = { ...levels.value, [id]: level }
  savingId.value = id

  try {
    await updateKnowledgeMastery(
      selectedCourse,
      id,
      level,
    )
    if (props.courseId === selectedCourse) {
      notifySuccess('掌握程度已保存')
    }
  } catch (error) {
    if (props.courseId === selectedCourse) {
      levels.value = { ...levels.value, [id]: oldLevel }
    }
    notifyError(error)
  } finally {
    savingId.value = null
  }
}
</script>

<template>
  <section v-loading="loading" class="mastery-panel">
    <el-divider content-position="left">
      知识点掌握程度
    </el-divider>

    <el-alert
      type="info"
      :closable="false"
      title="请根据自己的实际情况标记掌握程度。薄弱知识点将获得更多学习时间。"
    />

    <el-empty
      v-if="!loading && points.length === 0"
      description="教师尚未添加课程知识点"
    />

    <div
      v-for="point in points"
      :key="point.id"
      class="mastery-item"
    >
      <div class="mastery-item__info">
        <strong>{{ point.title }}</strong>
        <span>
          基础学习时间：{{ point.estimatedMinutes }} 分钟
        </span>
      </div>

      <el-select
        :model-value="levels[String(point.id)] ?? 2"
        style="width: 145px"
        :disabled="savingId !== null"
        @change="(value: number) => changeLevel(point, value)"
      >
        <el-option label="薄弱" :value="0" />
        <el-option label="不熟练" :value="1" />
        <el-option label="一般" :value="2" />
        <el-option label="熟练" :value="3" />
      </el-select>
    </div>
  </section>
</template>

<style scoped>
.mastery-panel {
  display: grid;
  gap: 14px;
}

.mastery-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 16px;
  border: 1px solid var(--sc-border);
  border-radius: 10px;
  padding: 12px 14px;
}

.mastery-item__info {
  display: grid;
  gap: 5px;
}

.mastery-item__info span {
  font-size: 12px;
  color: var(--sc-text-secondary);
}
</style>
