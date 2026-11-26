<script setup lang="ts">
import { computed, ref, watch } from 'vue'

import type { BigIntId } from '@/api/types'
import { getChunkKnowledgeProgress } from '@/api/document-chunk'
import type { ChunkKnowledgeProgress as ChunkKnowledgeProgressData } from '@/api/document-chunk'
import { notifyError } from '@/utils/notify'

const props = defineProps<{
  courseId: BigIntId | null
  documentId: BigIntId | null
  refreshKey: number
}>()

const progress = ref<ChunkKnowledgeProgressData | null>(null)
const loading = ref(false)
const errorMessage = ref('')
let requestVersion = 0

const completedChunks = computed(
  () =>
    (progress.value?.confirmedChunks ?? 0)
    + (progress.value?.notApplicableChunks ?? 0),
)

const progressStatus = computed<
  'success' | 'warning' | undefined
>(() => {
  if (!progress.value) return undefined
  if (progress.value.completed) return 'success'
  if (progress.value.completionPercent === 100) return 'warning'
  return undefined
})

async function loadProgress(): Promise<void> {
  const version = ++requestVersion
  const courseId = props.courseId
  const documentId = props.documentId
  errorMessage.value = ''

  if (!courseId || !documentId) {
    progress.value = null
    loading.value = false
    return
  }

  loading.value = true
  try {
    const result = await getChunkKnowledgeProgress(
      courseId,
      documentId,
    )
    if (
      version !== requestVersion
      || props.courseId !== courseId
      || props.documentId !== documentId
    ) {
      return
    }
    progress.value = result
  } catch (error) {
    if (
      version !== requestVersion
      || props.courseId !== courseId
      || props.documentId !== documentId
    ) {
      return
    }
    progress.value = null
    errorMessage.value = '知识点关联进度加载失败'
    notifyError(error, errorMessage.value)
  } finally {
    if (version === requestVersion) loading.value = false
  }
}

watch(
  () => [
    props.courseId,
    props.documentId,
    props.refreshKey,
  ] as const,
  (current, previous) => {
    if (
      !previous
      || current[0] !== previous[0]
      || current[1] !== previous[1]
    ) {
      progress.value = null
    }
    void loadProgress()
  },
  { immediate: true },
)
</script>

<template>
  <div
    v-loading="loading"
    class="knowledge-progress-panel"
  >
    <div class="panel-header">
      <div>
        <div class="panel-title">
          知识点关联进度
        </div>
        <div class="panel-description">
          统计整份资料的知识切片处理情况
        </div>
      </div>
      <el-button
        size="small"
        :disabled="loading"
        @click="loadProgress"
      >
        刷新进度
      </el-button>
    </div>

    <el-alert
      v-if="errorMessage"
      :title="errorMessage"
      type="error"
      :closable="false"
      show-icon
    />

    <template v-if="progress">
      <div class="statistics-grid">
        <div class="statistic-card">
          <span>切片总数</span>
          <strong>{{ progress.totalChunks }}</strong>
        </div>
        <div class="statistic-card">
          <span>未处理</span>
          <strong class="text-secondary">
            {{ progress.unprocessedChunks }}
          </strong>
        </div>
        <div class="statistic-card">
          <span>待处理</span>
          <strong class="text-warning">
            {{ progress.recommendedChunks }}
          </strong>
        </div>
        <div class="statistic-card">
          <span>已确认</span>
          <strong class="text-success">
            {{ progress.confirmedChunks }}
          </strong>
        </div>
        <div class="statistic-card">
          <span>无需关联</span>
          <strong class="text-secondary">
            {{ progress.notApplicableChunks }}
          </strong>
        </div>
      </div>

      <div class="progress-section">
        <div class="progress-header">
          <span>
            已处理 {{ completedChunks }} / {{ progress.totalChunks }} 个切片
          </span>
          <strong>{{ progress.completionPercent }}%</strong>
        </div>
        <el-progress
          :percentage="progress.completionPercent"
          :status="progressStatus"
          :stroke-width="10"
          :show-text="false"
        />
      </div>

      <div class="relation-summary">
        <span>
          待确认关联：
          <strong class="text-warning">
            {{ progress.pendingRelations }}
          </strong>
          条
        </span>
        <span>
          已确认关联：
          <strong class="text-success">
            {{ progress.confirmedRelations }}
          </strong>
          条
        </span>
      </div>

      <el-alert
        v-if="progress.completed"
        title="当前资料的知识点关联工作已全部完成"
        type="success"
        :closable="false"
        show-icon
      />
      <el-alert
        v-else-if="progress.totalChunks === 0"
        title="当前资料暂无知识切片"
        type="info"
        :closable="false"
        show-icon
      />
      <el-alert
        v-else-if="progress.completionPercent === 100"
        title="切片处理进度已达到100%，但仍存在待确认关联或关联数据不一致"
        type="warning"
        :closable="false"
        show-icon
      />
      <el-alert
        v-else
        title="当前资料尚未完成全部知识点关联"
        type="warning"
        :closable="false"
        show-icon
      />
    </template>
  </div>
</template>

<style scoped>
.knowledge-progress-panel {
  display: flex;
  flex-direction: column;
  gap: 16px;
  padding: 16px;
  border: 1px solid var(--el-border-color);
  border-radius: 10px;
}

.panel-header,
.progress-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.panel-title {
  font-size: 15px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.panel-description {
  margin-top: 4px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.statistics-grid {
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  gap: 10px;
}

.statistic-card {
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding: 12px;
  border-radius: 8px;
  background: var(--el-fill-color-light);
}

.statistic-card span {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.statistic-card strong {
  font-size: 24px;
  line-height: 1.2;
}

.progress-section {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.progress-header {
  font-size: 13px;
}

.relation-summary {
  display: flex;
  flex-wrap: wrap;
  gap: 20px;
  font-size: 13px;
}

.text-secondary {
  color: var(--el-text-color-secondary);
}

.text-success {
  color: var(--el-color-success);
}

.text-warning {
  color: var(--el-color-warning);
}

@media (max-width: 768px) {
  .statistics-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
</style>
