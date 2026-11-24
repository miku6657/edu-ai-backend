<script setup lang="ts">
import { computed, ref, watch } from 'vue'

import {
  listWeakKnowledgePoints,
} from '@/api/learning-path'
import type { WeakKnowledgePoint } from '@/api/learning-path'
import { notifyError } from '@/utils/notify'

const props = defineProps<{
  courseId: string
}>()

const points = ref<WeakKnowledgePoint[]>([])
const loading = ref(false)
let version = 0

const totalQuestions = computed(() =>
  points.value.reduce(
    (sum, point) => sum + point.effectiveCount,
    0,
  ),
)

const weakCount = computed(() =>
  points.value.filter(
    (point) => point.inferredLevel < 2,
  ).length,
)

function levelLabel(level: number): string {
  if (level === 0) return '重点复习候选'
  if (level === 1) return '疑似不熟练'
  return '暂无明显薄弱证据'
}

function levelType(level: number): 'danger' | 'warning' | 'info' {
  if (level === 0) return 'danger'
  if (level === 1) return 'warning'
  return 'info'
}

async function refresh(): Promise<void> {
  const course = props.courseId
  const current = ++version

  points.value = []
  if (!course) {
    loading.value = false
    return
  }

  loading.value = true

  try {
    const result = await listWeakKnowledgePoints(course)
    if (current === version) {
      points.value = result
    }
  } catch (error) {
    if (current === version) {
      notifyError(error)
    }
  } finally {
    if (current === version) {
      loading.value = false
    }
  }
}

watch(
  () => props.courseId,
  () => { void refresh() },
  { immediate: true },
)

defineExpose({ refresh })
</script>

<template>
  <section v-loading="loading" class="weak-panel">
    <el-divider content-position="left">
      AI 提问薄弱点分析
    </el-divider>

    <el-alert
      type="info"
      :closable="false"
      title="根据最近30天的有效提问频率推断疑似薄弱知识点。提问多并不一定代表掌握较差。"
    />

    <div class="weak-summary">
      <el-statistic
        title="有效提问次数"
        :value="totalQuestions"
      />
      <el-statistic
        title="疑似薄弱知识点"
        :value="weakCount"
      />
      <el-button
        :disabled="!courseId"
        :loading="loading"
        @click="refresh"
      >
        刷新分析
      </el-button>
    </div>

    <el-table
      :data="points"
      empty-text="当前课程暂无知识点"
    >
      <el-table-column
        prop="title"
        label="知识点"
        min-width="150"
      />

      <el-table-column
        prop="effectiveCount"
        label="有效提问"
        width="100"
      />

      <el-table-column
        prop="sessionCount"
        label="涉及会话"
        width="100"
      />

      <el-table-column
        label="分析结果"
        min-width="150"
      >
        <template #default="{ row }">
          <el-tag :type="levelType(row.inferredLevel)">
            {{ levelLabel(row.inferredLevel) }}
          </el-tag>
        </template>
      </el-table-column>
    </el-table>
  </section>
</template>

<style scoped>
.weak-panel {
  display: grid;
  gap: 16px;
}

.weak-summary {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 24px;
}
</style>
