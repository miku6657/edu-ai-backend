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
  updateDocumentSummary,
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
const editing = ref(false)
const savingEdit = ref(false)

const editableSummary = ref('')

const editableOutline = ref<Array<{
  title: string
  page: number | null
  description: string
  keyPointsText: string
}>>([])

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
    cancelEdit()
    return
  }

  loading.value = true

  try {

    const result = await getDocumentSummary(
      props.courseId,
      props.documentId,
    )

    if (version !== requestVersion) {
      return
    }

    summary.value = result
    cancelEdit()

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
 * AI生成摘要。
 */
async function generate(): Promise<void> {

  if (
    !props.canManage
    || !props.courseId
    || !props.documentId
    || generating.value
    || confirming.value
    || editing.value
    || savingEdit.value
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
 * 开始人工编辑待确认摘要。
 */
function startEdit(): void {

  if (
    !summary.value
    || summary.value.status !== 'DRAFT'
  ) {
    return
  }

  editableSummary.value = summary.value.summary

  editableOutline.value =
    summary.value.outline.map(item => ({
      title: item.title,
      page: item.page,
      description: item.description,
      keyPointsText: item.keyPoints.join('\n'),
    }))

  editing.value = true
}

/**
 * 取消编辑。
 */
function cancelEdit(): void {

  editing.value = false

  editableSummary.value = ''

  editableOutline.value = []
}

/**
 * 保存人工编辑后的摘要和大纲。
 */
async function saveEdit(): Promise<void> {

  if (
    !props.canManage
    || !props.courseId
    || !props.documentId
    || !summary.value
    || summary.value.status !== 'DRAFT'
    || savingEdit.value
  ) {
    return
  }

  const text = editableSummary.value.trim()

  if (!text) {
    notifyError(
      new Error('摘要内容不能为空'),
      '保存失败',
    )
    return
  }

  if (text.length > 10000) {
    notifyError(
      new Error('摘要不能超过10000个字符'),
      '保存失败',
    )
    return
  }

  const outline = editableOutline.value.map(item => ({
    title: item.title.trim(),

    page: item.page === null || item.page === undefined
      ? null
      : Number(item.page),

    description: item.description.trim(),

    keyPoints: item.keyPointsText
      .split('\n')
      .map(point => point.trim())
      .filter(Boolean)
      .slice(0, 5),
  }))

  if (
    !outline.length
    || outline.length > 30
    || outline.some(item => !item.title)
  ) {
    notifyError(
      new Error('章节大纲必须包含1至30项，且标题不能为空'),
      '保存失败',
    )
    return
  }

  if (outline.some(item =>
    item.page !== null
    && (
      !Number.isInteger(item.page)
      || item.page < 1
      || item.page > 10000
    ),
  )) {
    notifyError(
      new Error('章节页码必须为1至10000之间的整数'),
      '保存失败',
    )
    return
  }

  if (outline.some(item =>
    item.description.length > 1000
    || item.keyPoints.some(point => point.length > 300),
  )) {
    notifyError(
      new Error('章节描述或知识点长度超过限制'),
      '保存失败',
    )
    return
  }

  savingEdit.value = true

  try {

    summary.value = await updateDocumentSummary(
      props.courseId,
      props.documentId,
      summary.value.id,
      {
        summary: text,
        outline,
      },
    )

    editing.value = false

    notifySuccess(
      '摘要和章节大纲已保存，请检查后确认发布',
    )

  } catch (error) {

    notifyError(error, '摘要修改失败')

  } finally {

    savingEdit.value = false
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
    || editing.value
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

    notifySuccess(
      '摘要已确认并发布，学生现在可以查看',
    )

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

  {
    immediate: true,
  },
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

      <!-- 文档摘要 -->

      <div class="summary-content">

        <h4>文档摘要</h4>

        <el-input
          v-if="editing"
          v-model="editableSummary"
          type="textarea"
          :rows="7"
          maxlength="10000"
          show-word-limit
          placeholder="编辑文档摘要"
        />

        <p
          v-else
          class="summary-text"
        >
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
            生成时间：
            {{ summary.generatedAt.replace('T', ' ') }}
          </span>

        </div>

      </div>

      <!-- 章节大纲 -->

      <div class="outline-content">

        <h4>章节大纲</h4>

        <template v-if="editing">

          <div
            v-for="(item, index) in editableOutline"
            :key="`edit-${index}`"
            class="outline-item outline-edit-item"
          >

            <div class="outline-edit-heading">

              <strong>
                第{{ index + 1 }}项
              </strong>

              <el-button
                type="danger"
                link
                @click="editableOutline.splice(index, 1)"
              >
                删除
              </el-button>

            </div>

            <el-input
              v-model="item.title"
              placeholder="章节标题"
              maxlength="200"
            />

            <el-input-number
              v-model="item.page"
              :min="1"
              :max="10000"
              :value-on-clear="null"
              controls-position="right"
              placeholder="页码"
            />

            <el-input
              v-model="item.description"
              type="textarea"
              :rows="2"
              maxlength="1000"
              placeholder="章节内容概述"
            />

            <el-input
              v-model="item.keyPointsText"
              type="textarea"
              :rows="3"
              placeholder="核心知识点，每行一项，最多5项"
            />

          </div>

          <el-button
            v-if="editableOutline.length < 30"
            plain
            type="primary"
            @click="editableOutline.push({
              title: '',
              page: null,
              description: '',
              keyPointsText: '',
            })"
          >
            添加章节
          </el-button>

        </template>

        <template v-else>

          <div
            v-for="(item, index) in summary.outline"
            :key="`${index}-${item.title}`"
            class="outline-item"
          >

            <div class="outline-title">

              <span class="outline-number">
                {{ index + 1 }}
              </span>

              <strong>
                {{ item.title }}
              </strong>

              <el-tag
                v-if="item.page"
                size="small"
                type="info"
              >
                第{{ item.page }}页
              </el-tag>

            </div>

            <p>
              {{ item.description }}
            </p>

            <ul v-if="item.keyPoints.length">

              <li
                v-for="(point, pointIndex) in item.keyPoints"
                :key="pointIndex"
              >
                {{ point }}
              </li>

            </ul>

          </div>

        </template>

      </div>

      <!-- 待确认提示 -->

      <div
        v-if="summary.status === 'DRAFT' && canManage"
        class="summary-warning"
      >
        当前摘要尚未发布，学生暂时无法查看。
        请检查摘要内容和章节大纲。
      </div>

      <!-- 已发布提示 -->

      <div
        v-if="summary.status === 'CONFIRMED'"
        class="summary-published"
      >
        <el-icon>
          <CircleCheck />
        </el-icon>

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

    <!-- 操作按钮 -->

    <div
      v-if="canManage"
      class="summary-actions"
    >

      <el-button
        type="primary"
        :loading="generating"
        :disabled="
          loading
          || confirming
          || editing
          || savingEdit
        "
        @click="generate"
      >
        {{ summary ? '重新生成摘要' : 'AI生成摘要与大纲' }}
      </el-button>

      <template v-if="summary?.status === 'DRAFT'">

        <template v-if="editing">

          <el-button
            :disabled="savingEdit"
            @click="cancelEdit"
          >
            取消编辑
          </el-button>

          <el-button
            type="primary"
            :loading="savingEdit"
            @click="saveEdit"
          >
            保存修改
          </el-button>

        </template>

        <el-button
          v-else
          :disabled="generating || confirming"
          @click="startEdit"
        >
          人工编辑
        </el-button>

        <el-button
          type="success"
          :loading="confirming"
          :disabled="
            generating
            || editing
            || savingEdit
          "
          @click="confirm"
        >
          确认并发布
        </el-button>

      </template>

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
  overflow-wrap: anywhere;
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

.outline-edit-item {
  display: flex;
  flex-direction: column;
  align-items: stretch;
  gap: 9px;
}

.outline-edit-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  color: var(--sc-text);
  font-size: 13px;
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
  display: flex;
  align-items: center;
  gap: 8px;
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

@media (max-width: 640px) {
  .summary-actions {
    justify-content: flex-start;
  }

  .summary-actions .el-button {
    margin-left: 0;
  }
}
</style>
