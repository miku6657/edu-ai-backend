<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { ElMessageBox } from 'element-plus'

import { useAuthStore } from '@/stores/auth'
import type { BigIntId } from '@/api/types'
import type { KnowledgeDoc } from '@/api/knowledge'
import {
  assignQualityIssue,
  qualityIssueStatusLabel,
  qualityIssueTagType,
  qualityIssueTypeLabel,
  resolveQualityIssue,
  reviewQualityIssue,
} from '@/api/document-quality'
import type {
  QualityAssignee,
  QualityDetail,
  QualityIssue,
  QualityTask,
} from '@/api/document-quality'
import {
  notifyError,
  notifySuccess,
  notifyWarning,
} from '@/utils/notify'

const props = defineProps<{
  modelValue: boolean
  courseId: BigIntId | null
  task: QualityTask | null
  issue: QualityIssue | null
  assignees: QualityAssignee[]
  documents: KnowledgeDoc[]
}>()

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  changed: [detail: QualityDetail]
  preview: [document: KnowledgeDoc]
}>()

const auth = useAuthStore()
const saving = ref(false)
const assigneeId = ref<BigIntId>('')
const resolution = ref('')
const replacementDocumentId = ref<BigIntId>('')
const reviewComment = ref('')
const canManage = computed(() => auth.isTeacher || auth.isAdmin)
const activeTask = computed(() => props.task?.status === 'IN_PROGRESS')
const canAssign = computed(
  () =>
    canManage.value
    && activeTask.value
    && props.issue?.status === 'OPEN'
    && !saving.value,
)
const canResolve = computed(
  () =>
    activeTask.value
    && props.issue?.status === 'OPEN'
    && props.issue.assigneeId === auth.user?.user_id
    && !saving.value,
)
const canReview = computed(
  () =>
    canManage.value
    && activeTask.value
    && props.issue?.status === 'RESOLVED'
    && !saving.value,
)
const replacementDocuments = computed(() => {
  const task = props.task
  if (!task) return []

  return props.documents.filter(
    doc =>
      doc.courseId === task.courseId
      && doc.resourceId === task.resourceId
      && doc.versionNo > task.versionNo
      && doc.status === 'READY',
  )
})
const selectedReplacement = computed(
  () =>
    replacementDocuments.value.find(
      doc => doc.id === replacementDocumentId.value,
    ) ?? null,
)

function resetForm(): void {
  assigneeId.value = props.issue?.assigneeId ?? ''
  resolution.value = ''
  replacementDocumentId.value = ''
  reviewComment.value = ''
}

watch(
  () => [props.modelValue, props.issue?.id] as const,
  ([visible]) => {
    if (visible) resetForm()
  },
  { immediate: true },
)

function personName(id: BigIntId | null | undefined): string {
  if (!id) return '—'
  return props.assignees.find(person => person.id === id)?.username
    ?? `用户 ${id}`
}

function validate(
  action: 'ASSIGN' | 'RESOLVE' | 'APPROVE' | 'REJECT',
): boolean {
  if (action === 'ASSIGN') {
    if (!canAssign.value) return false
    if (!props.assignees.some(person => person.id === assigneeId.value)) {
      notifyWarning('请选择有效的整改责任人')
      return false
    }
    return true
  }

  if (action === 'RESOLVE') {
    if (!canResolve.value) return false
    if (!resolution.value.trim()) {
      notifyWarning('请填写整改说明')
      return false
    }
    if (resolution.value.trim().length > 2000) {
      notifyWarning('整改说明不能超过2000个字符')
      return false
    }
    if (replacementDocumentId.value && !selectedReplacement.value) {
      notifyWarning('请选择有效的整改资源版本')
      return false
    }
    return true
  }

  if (!canReview.value) return false
  if (action === 'REJECT' && !reviewComment.value.trim()) {
    notifyWarning('驳回整改时必须填写原因')
    return false
  }
  if (reviewComment.value.trim().length > 1000) {
    notifyWarning('复核意见不能超过1000个字符')
    return false
  }
  return true
}

type IssueAction = 'ASSIGN' | 'RESOLVE' | 'APPROVE' | 'REJECT'

async function executeAction(action: IssueAction): Promise<void> {
  const courseId = props.courseId
  const taskId = props.task?.id
  const issueId = props.issue?.id
  if (
    saving.value
    || !courseId
    || !taskId
    || !issueId
    || !validate(action)
  ) {
    return
  }

  const actionNames: Record<IssueAction, string> = {
    ASSIGN: '重新分配责任人',
    RESOLVE: '提交整改',
    APPROVE: '复核通过',
    REJECT: '驳回整改',
  }

  saving.value = true
  try {
    await ElMessageBox.confirm(
      `确定${actionNames[action]}吗？`,
      '质量问题处理',
      { type: 'warning' },
    )
    if (
      !props.modelValue
      || props.courseId !== courseId
      || props.task?.id !== taskId
      || props.issue?.id !== issueId
    ) {
      return
    }

    let detail: QualityDetail
    switch (action) {
      case 'ASSIGN':
        detail = await assignQualityIssue(
          courseId,
          taskId,
          issueId,
          { assigneeId: assigneeId.value },
        )
        break
      case 'RESOLVE':
        detail = await resolveQualityIssue(
          courseId,
          taskId,
          issueId,
          {
            resolution: resolution.value.trim(),
            replacementDocumentId: replacementDocumentId.value || undefined,
          },
        )
        break
      case 'APPROVE':
      case 'REJECT':
        detail = await reviewQualityIssue(
          courseId,
          taskId,
          issueId,
          {
            decision: action,
            comment: reviewComment.value.trim(),
          },
        )
        break
    }

    notifySuccess(`${actionNames[action]}成功`)
    emit('changed', detail)
    emit('update:modelValue', false)
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') {
      notifyError(error, `${actionNames[action]}失败`)
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

function previewReplacement(): void {
  if (selectedReplacement.value) emit('preview', selectedReplacement.value)
}
</script>

<template>
  <el-dialog
    :model-value="modelValue"
    title="质量问题处理"
    width="min(760px, 96vw)"
    append-to-body
    destroy-on-close
    :close-on-click-modal="!saving"
    :close-on-press-escape="!saving"
    @update:model-value="onDialogUpdate"
  >
    <div
      v-if="issue && task"
      v-loading="saving"
      class="issue-panel"
    >
      <div class="section-heading">
        <strong>{{ qualityIssueTypeLabel(issue.issueType) }}</strong>
        <el-tag :type="qualityIssueTagType(issue.status)">
          {{ qualityIssueStatusLabel(issue.status) }}
        </el-tag>
      </div>

      <el-descriptions :column="2" border size="small">
        <el-descriptions-item label="发现人">
          {{ personName(issue.reporterId) }}
        </el-descriptions-item>
        <el-descriptions-item label="整改责任人">
          {{ personName(issue.assigneeId) }}
        </el-descriptions-item>
        <el-descriptions-item label="原始版本">
          V{{ task.versionNo }}
        </el-descriptions-item>
        <el-descriptions-item label="登记时间">
          {{ issue.createTime?.replace('T', ' ').slice(0, 19) || '—' }}
        </el-descriptions-item>
      </el-descriptions>

      <section class="issue-section">
        <strong>问题描述</strong>
        <div class="content">
          {{ issue.description }}
        </div>
      </section>

      <section v-if="canAssign" class="issue-section">
        <strong>重新分配责任人</strong>
        <el-select v-model="assigneeId" filterable style="width: 100%">
          <el-option
            v-for="person in assignees"
            :key="person.id"
            :label="`${person.username}（${person.role}）`"
            :value="person.id"
          />
        </el-select>
        <el-button
          type="primary"
          plain
          :disabled="!assigneeId || assigneeId === issue.assigneeId"
          @click="executeAction('ASSIGN')"
        >
          保存责任人
        </el-button>
      </section>

      <section v-if="canResolve" class="issue-section">
        <strong>提交整改结果</strong>
        <el-input
          v-model="resolution"
          type="textarea"
          :rows="4"
          maxlength="2000"
          show-word-limit
          placeholder="说明具体整改措施和结果"
        />
        <el-select
          v-model="replacementDocumentId"
          filterable
          clearable
          placeholder="选择整改后的新版本（选填）"
          style="width: 100%"
        >
          <el-option
            v-for="doc in replacementDocuments"
            :key="doc.id"
            :label="`V${doc.versionNo} · ${doc.originalName}`"
            :value="doc.id"
          />
        </el-select>
        <div class="muted">
          如果整改需要更新 PDF，请先上传新版本并等待解析完成。
        </div>
        <el-button
          v-if="selectedReplacement"
          @click="previewReplacement"
        >
          预览整改资源
        </el-button>
        <el-button type="primary" @click="executeAction('RESOLVE')">
          提交整改
        </el-button>
      </section>

      <el-alert
        v-if="issue.status === 'OPEN' && !canResolve && !canAssign && activeTask"
        title="当前问题尚未完成整改，请联系指定责任人"
        type="info"
        :closable="false"
      />

      <section v-if="issue.resolution" class="issue-section">
        <strong>整改记录</strong>
        <div class="content">
          {{ issue.resolution }}
        </div>
        <div class="muted">
          整改时间：
          {{ issue.resolvedTime?.replace('T', ' ').slice(0, 19) || '—' }}
        </div>
        <div v-if="issue.replacementDocumentId" class="muted">
          整改资源 ID：{{ issue.replacementDocumentId }}
        </div>
      </section>

      <section v-if="canReview" class="issue-section">
        <strong>整改复核</strong>
        <el-input
          v-model="reviewComment"
          type="textarea"
          :rows="3"
          maxlength="1000"
          show-word-limit
          placeholder="填写复核意见，驳回时必填"
        />
        <div class="action-row">
          <el-button
            type="success"
            @click="executeAction('APPROVE')"
          >
            复核通过
          </el-button>
          <el-button
            type="danger"
            plain
            @click="executeAction('REJECT')"
          >
            驳回整改
          </el-button>
        </div>
      </section>

      <section v-if="issue.reviewedTime" class="issue-section">
        <strong>最近复核记录</strong>
        <div class="muted">
          复核人：{{ personName(issue.reviewerId) }}
          · {{ issue.reviewedTime.replace('T', ' ').slice(0, 19) }}
        </div>
        <div class="content">
          {{ issue.reviewComment || '未填写复核意见' }}
        </div>
      </section>
    </div>

    <template #footer>
      <el-button :disabled="saving" @click="closeDialog">
        关闭
      </el-button>
    </template>
  </el-dialog>
</template>

<style scoped>
.issue-panel {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.section-heading {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.issue-section {
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding: 16px;
  border: 1px solid var(--el-border-color);
  border-radius: 8px;
}

.content {
  padding: 12px;
  border-radius: 6px;
  background: var(--el-fill-color-light);
  line-height: 1.7;
  white-space: pre-wrap;
  overflow-wrap: anywhere;
}

.muted {
  color: var(--el-text-color-secondary);
  font-size: 12px;
}

.action-row {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.action-row :deep(.el-button) {
  margin-left: 0;
}
</style>
