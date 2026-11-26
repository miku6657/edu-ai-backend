<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { ElMessageBox } from 'element-plus'

import type { BigIntId } from '@/api/types'
import {
  checkQualitySample,
  QUALITY_ISSUE_OPTIONS,
  qualitySampleStatusLabel,
  qualitySampleTagType,
} from '@/api/document-quality'
import type {
  QualityAssignee,
  QualityDetail,
  QualityIssueInput,
  QualitySample,
} from '@/api/document-quality'
import {
  notifyError,
  notifySuccess,
  notifyWarning,
} from '@/utils/notify'

const props = defineProps<{
  modelValue: boolean
  courseId: BigIntId | null
  taskId: BigIntId | null
  sample: QualitySample | null
  assignees: QualityAssignee[]
  editable: boolean
}>()

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  changed: [detail: QualityDetail]
  preview: []
}>()

const saving = ref(false)
const result = ref<'PASS' | 'FAIL'>('PASS')
const comment = ref('')
const issues = ref<QualityIssueInput[]>([])
const canEdit = computed(
  () =>
    props.editable
    && props.sample?.status === 'PENDING'
    && !saving.value,
)

function createIssue(): QualityIssueInput {
  return {
    issueType: 'OTHER',
    description: '',
    assigneeId: props.assignees[0]?.id ?? '',
  }
}

function resetForm(): void {
  result.value =
    props.sample?.status === 'FAIL'
      ? 'FAIL'
      : 'PASS'

  comment.value =
    props.sample?.checkComment ?? ''

  issues.value = []
}

watch(
  () => [props.modelValue, props.sample?.id] as const,
  ([visible]) => {
    if (visible) resetForm()
  },
  { immediate: true },
)

watch(result, value => {
  if (value === 'FAIL' && canEdit.value && issues.value.length === 0) {
    issues.value = [createIssue()]
  }
})

function addIssue(): void {
  if (issues.value.length >= 10) {
    notifyWarning('单个样本最多登记10个质量问题')
    return
  }
  issues.value.push(createIssue())
}

function removeIssue(index: number): void {
  issues.value.splice(index, 1)
}

function validate(): boolean {
  if (comment.value.trim().length > 1000) {
    notifyWarning('检查说明不能超过1000个字符')
    return false
  }
  if (result.value === 'PASS') return true
  if (!issues.value.length) {
    notifyWarning('不合格样本至少需要登记一个问题')
    return false
  }

  const assigneeIds = new Set(props.assignees.map(item => item.id))
  for (const issue of issues.value) {
    if (!issue.description.trim()) {
      notifyWarning('请填写问题描述')
      return false
    }
    if (issue.description.trim().length > 2000) {
      notifyWarning('问题描述不能超过2000个字符')
      return false
    }
    if (!assigneeIds.has(issue.assigneeId)) {
      notifyWarning('请选择有效的整改责任人')
      return false
    }
  }
  return true
}

async function saveCheck(): Promise<void> {
  if (
    !canEdit.value
    || !props.courseId
    || !props.taskId
    || !props.sample
    || !validate()
  ) {
    return
  }

  const courseId = props.courseId
  const taskId = props.taskId
  const sampleId = props.sample.id
  saving.value = true
  try {
    await ElMessageBox.confirm(
      '检查结果保存后不能直接修改，确定提交吗？',
      '保存抽检结果',
      { type: 'warning' },
    )
    if (
      !props.modelValue
      || props.courseId !== courseId
      || props.taskId !== taskId
      || props.sample?.id !== sampleId
    ) {
      return
    }

    const detail = await checkQualitySample(
      courseId,
      taskId,
      sampleId,
      {
        result: result.value,
        comment: comment.value.trim(),
        issues: result.value === 'FAIL'
          ? issues.value.map(issue => ({
              issueType: issue.issueType,
              description: issue.description.trim(),
              assigneeId: issue.assigneeId,
            }))
          : [],
      },
    )
    notifySuccess('样本检查结果已保存')
    emit('changed', detail)
    emit('update:modelValue', false)
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') {
      notifyError(error, '保存抽检结果失败')
    }
  } finally {
    saving.value = false
  }
}

function closeDialog(): void {
  if (!saving.value) emit('update:modelValue', false)
}

function onDialogUpdate(value: boolean): void {
  if (!value) closeDialog()
}
</script>

<template>
  <el-dialog
    :model-value="modelValue"
    title="质量样本检查"
    width="min(780px, 96vw)"
    append-to-body
    destroy-on-close
    :close-on-click-modal="!saving"
    :close-on-press-escape="!saving"
    @update:model-value="onDialogUpdate"
  >
    <div
      v-if="sample"
      v-loading="saving"
      class="sample-panel"
    >
      <div class="sample-header">
        <div>
          <strong>
            第 {{ sample.pageNo }} 页 · 切片 {{ sample.chunkIndex + 1 }}
          </strong>
          <div class="muted">
            {{ sample.sectionTitle || '未识别章节' }}
          </div>
        </div>
        <el-tag :type="qualitySampleTagType(sample.status)">
          {{ qualitySampleStatusLabel(sample.status) }}
        </el-tag>
      </div>

      <el-button size="small" @click="emit('preview')">
        预览 PDF 原文
      </el-button>

      <el-alert
        v-if="sample.duplicateCount > 1"
        title="发现疑似重复内容"
        type="warning"
        :closable="false"
        show-icon
      >
        当前文档中存在 {{ sample.duplicateCount }} 个正文哈希相同的切片，
        请结合原始 PDF 判断是否属于异常重复。
      </el-alert>

      <div class="sample-content">
        {{ sample.content }}
      </div>

      <el-form label-position="top">
        <el-form-item label="检查结果">
          <el-radio-group v-model="result" :disabled="!canEdit">
            <el-radio value="PASS">
              合格
            </el-radio>
            <el-radio value="FAIL">
              不合格
            </el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="检查说明">
          <el-input
            v-model="comment"
            type="textarea"
            :rows="3"
            maxlength="1000"
            show-word-limit
            :disabled="!canEdit"
            placeholder="填写检查说明"
          />
        </el-form-item>

        <template v-if="result === 'FAIL' && canEdit">
          <div class="issue-heading">
            <strong>质量问题登记</strong>
            <el-button
              size="small"
              type="primary"
              plain
              :disabled="issues.length >= 10"
              @click="addIssue"
            >
              添加问题
            </el-button>
          </div>
          <div
            v-for="(issue, index) in issues"
            :key="index"
            class="issue-card"
          >
            <div class="issue-heading">
              <strong>问题 {{ index + 1 }}</strong>
              <el-button
                type="danger"
                link
                @click="removeIssue(index)"
              >
                删除
              </el-button>
            </div>
            <el-form-item label="问题类型">
              <el-select v-model="issue.issueType" style="width: 100%">
                <el-option
                  v-for="option in QUALITY_ISSUE_OPTIONS"
                  :key="option.value"
                  :label="option.label"
                  :value="option.value"
                />
              </el-select>
            </el-form-item>
            <el-form-item label="问题描述">
              <el-input
                v-model="issue.description"
                type="textarea"
                :rows="3"
                maxlength="2000"
                show-word-limit
              />
            </el-form-item>
            <el-form-item label="整改责任人">
              <el-select
                v-model="issue.assigneeId"
                filterable
                placeholder="选择责任人"
                style="width: 100%"
              >
                <el-option
                  v-for="person in assignees"
                  :key="person.id"
                  :label="`${person.username}（${person.role}）`"
                  :value="person.id"
                />
              </el-select>
            </el-form-item>
          </div>
        </template>
      </el-form>

      <el-alert
        v-if="sample.status !== 'PENDING'"
        title="该样本已完成检查，当前为历史检查记录"
        type="info"
        :closable="false"
      />
    </div>

    <template #footer>
      <el-button
        :disabled="saving"
        @click="closeDialog"
      >
        关闭
      </el-button>
      <el-button
        v-if="canEdit"
        type="primary"
        :loading="saving"
        @click="saveCheck"
      >
        保存检查结果
      </el-button>
    </template>
  </el-dialog>
</template>

<style scoped>
.sample-panel {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.sample-header,
.issue-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.sample-content {
  max-height: 320px;
  overflow-y: auto;
  padding: 16px;
  border: 1px solid var(--el-border-color);
  border-radius: 8px;
  background: var(--el-fill-color-light);
  line-height: 1.8;
  white-space: pre-wrap;
  overflow-wrap: anywhere;
}

.issue-card {
  margin-top: 12px;
  padding: 16px;
  border: 1px solid var(--el-border-color);
  border-radius: 8px;
}

.muted {
  margin-top: 5px;
  color: var(--el-text-color-secondary);
  font-size: 12px;
}
</style>
