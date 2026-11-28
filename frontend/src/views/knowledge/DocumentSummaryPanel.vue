<script setup lang="ts">
import {
  ref,
  watch,
} from 'vue'

import { CircleCheck } from '@element-plus/icons-vue'

import type { BigIntId } from '@/api/types'

import {
  getDocumentSummary,
  generateDocumentSummary,
  confirmDocumentSummary,
} from '@/api/document-summary'

import type {
  DocumentSummary,
} from '@/api/document-summary'

import {
  notifyError,
  notifySuccess,
} from '@/utils/notify'

const props = defineProps<{
  visible: boolean
  courseId: BigIntId | null
  documentId: BigIntId | null
  canManage: boolean
}>()

const loading = ref(false)
const generating = ref(false)
const confirming = ref(false)

const summary = ref<DocumentSummary | null>(null)

let requestVersion = 0

async function loadSummary(): Promise<void> {

  const version = ++requestVersion

  if (
    !props.visible
    || !props.courseId
    || !props.documentId
  ) {
    summary.value = null
    return
  }

  loading.value = true

  try {

    const result = await getDocumentSummary(
      props.courseId,
      props.documentId,
    )

    if (version !== requestVersion) return

    summary.value = result

  } catch (error) {

    if (version === requestVersion) {
      notifyError(error, '资料摘要加载失败')
    }

  } finally {

    if (version === requestVersion) {
      loading.value = false
    }
  }
}

/**
 * 生成摘要。
 */
async function generate(): Promise<void> {

  if (
    !props.canManage
    || !props.courseId
    || !props.documentId
    || generating.value
    || confirming.value
  ) {
    return
  }

  generating.value = true

  try {

    const result = await generateDocumentSummary(
      props.courseId,
      props.documentId,
    )

    summary.value = result

    notifySuccess(
      '摘要和章节大纲生成成功，请检查后确认发布',
    )

  } catch (error) {

    notifyError(error, 'AI摘要生成失败')

  } finally {

    generating.value = false
  }
}

/**
 * 确认并发布摘要。
 */
async function confirm(): Promise<void> {

  if (
    !props.canManage
    || !props.courseId
    || !props.documentId
    || !summary.value
    || summary.value.status !== 'DRAFT'
    || confirming.value
    || generating.value
  ) {
    return
  }

  confirming.value = true

  try {

    const result = await confirmDocumentSummary(
      props.courseId,
      props.documentId,
      summary.value.id,
    )

    summary.value = result

    notifySuccess('摘要已确认并发布，学生现在可以查看')

  } catch (error) {

    notifyError(error, '摘要确认失败')

  } finally {

    confirming.value = false
  }
}

function statusLabel(status: string): string {

  return status === 'DRAFT'
    ? '待确认'
    : status === 'CONFIRMED'
      ? '已发布'
      : status
}

watch(
  () => [
    props.visible,
    props.courseId,
    props.documentId,
  ] as const,

  () => {
    void loadSummary()
  },

  { immediate: true },
)
</script>

<template>
  <section
    class="document-summary-panel"
    v-loading="loading"
  >
    <div class="summary-heading">

      <div>
        <h3>AI文档摘要与章节大纲</h3>

        <p>
          基于当前课程资料自动整理，
          帮助学生快速了解文档内容。
        </p>
      </div>

      <el-tag
        v-if="summary"
        :type="summary.status === 'CONFIRMED' ? 'success' : 'warning'"
      >
        {{ statusLabel(summary.status) }}
      </el-tag>

    </div>

    <template v-if="summary">

      <div class="summary-content">

        <h4>文档摘要</h4>

        <p class="summary-text">
          {{ summary.summary }}
        </p>

        <div class="summary-meta">
          <span>
            生成版本：V{{ summary.revision }}
          </span>

          <span>
            依据切片：{{ summary.sourceChunkCount }} 条
          </span>

          <span>
            生成时间：{{ summary.generatedAt.replace('T', ' ') }}
          </span>
        </div>

      </div>

      <div class="outline-content">

        <h4>章节大纲</h4>

        <div
          v-for="(item, index) in summary.outline"
          :key="`${index}-${item.title}`"
          class="outline-item"
        >
          <div class="outline-title">

            <span class="outline-number">
              {{ index + 1 }}
            </span>

            <strong>{{ item.title }}</strong>

            <el-tag
              v-if="item.page"
              size="small"
              type="info"
            >
              第{{ item.page }}页
            </el-tag>

          </div>

          <p>{{ item.description }}</p>

          <ul v-if="item.keyPoints.length">
            <li
              v-for="(point, pointIndex) in item.keyPoints"
              :key="pointIndex"
            >
              {{ point }}
            </li>
          </ul>

        </div>

      </div>

      <div
        v-if="summary.status === 'DRAFT' && canManage"
        class="summary-warning"
      >
        当前摘要尚未发布，学生暂时无法查看。
        请检查摘要内容和章节大纲。
      </div>

      <div
        v-if="summary.status === 'CONFIRMED'"
        class="summary-published"
      >
        <el-icon><CircleCheck /></el-icon>
        该摘要已经确认发布。
      </div>

    </template>

    <el-empty
      v-else
      :image-size="65"
      :description="
        canManage
          ? '当前资料还没有摘要'
          : '课程负责人尚未发布资料摘要'
      "
    />

    <div
      v-if="canManage"
      class="summary-actions"
    >

      <el-button
        type="primary"
        :loading="generating"
        :disabled="loading || confirming"
        @click="generate"
      >
        {{ summary ? '重新生成摘要' : 'AI生成摘要与大纲' }}
      </el-button>

      <el-button
        v-if="summary?.status === 'DRAFT'"
        type="success"
        :loading="confirming"
        :disabled="generating"
        @click="confirm"
      >
        确认并发布
      </el-button>

    </div>

  </section>
</template>

<style scoped>
.document-summary-panel {
  margin-top: 18px;
  padding: 18px;
  border: 1px solid var(--sc-border);
  border-radius: 10px;
  background: var(--sc-surface);
}

.summary-heading {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 12px;
}

.summary-heading h3 {
  margin: 0;
  font-size: 16px;
  color: var(--sc-text);
}

.summary-heading p {
  margin-top: 7px;
  color: var(--sc-text-secondary);
  font-size: 12px;
  line-height: 1.6;
}

.summary-content,
.outline-content {
  margin-top: 20px;
}

.summary-content h4,
.outline-content h4 {
  margin-bottom: 12px;
  font-size: 14px;
  color: var(--sc-text);
}

.summary-text {
  margin: 0;
  color: var(--sc-text);
  font-size: 13px;
  line-height: 1.9;
  white-space: pre-line;
}

.summary-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  margin-top: 14px;
  color: var(--sc-text-secondary);
  font-size: 11px;
}

.outline-item {
  padding: 13px 0;
  border-bottom: 1px solid var(--sc-border);
}

.outline-title {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 9px;
  color: var(--sc-text);
  font-size: 13px;
}

.outline-number {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 23px;
  height: 23px;
  border-radius: 50%;
  background: var(--sc-surface-muted);
  font-size: 11px;
}

.outline-item p {
  margin: 9px 0;
  color: var(--sc-text-secondary);
  font-size: 12px;
  line-height: 1.7;
}

.outline-item ul {
  padding-left: 35px;
  margin: 7px 0;
  color: var(--sc-text);
  font-size: 12px;
  line-height: 1.8;
}

.summary-warning,
.summary-published {
  margin-top: 14px;
  padding: 11px;
  border-radius: 8px;
  background: var(--sc-surface-muted);
  color: var(--sc-text-secondary);
  font-size: 12px;
  line-height: 1.7;
}

.summary-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  justify-content: flex-end;
  margin-top: 18px;
}
</style>
