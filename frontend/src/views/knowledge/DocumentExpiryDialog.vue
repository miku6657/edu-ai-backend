<script setup lang="ts">
import { computed, ref, watch } from 'vue'

import type { BigIntId } from '@/api/types'
import type { KnowledgeDoc } from '@/api/knowledge'

import {
  getDocumentExpiry,
  updateDocumentExpiry,
} from '@/api/document-expiry'

import type {
  DocumentExpiryVO,
  DocumentExpiryStatus,
} from '@/api/document-expiry'

import {
  listQualityAssignees,
} from '@/api/document-quality'

import type {
  QualityAssignee,
} from '@/api/document-quality'

import {
  notifyError,
  notifySuccess,
  notifyWarning,
} from '@/utils/notify'

const props = defineProps<{
  modelValue: boolean
  courseId: BigIntId | null
  courseOwnerId: BigIntId | null
  document: KnowledgeDoc | null
  canManage: boolean
}>()

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  changed: []
}>()

const loading = ref(false)
const saving = ref(false)

const detail = ref<DocumentExpiryVO | null>(null)
const assignees = ref<QualityAssignee[]>([])

const validUntil = ref<string | null>(null)
const responsibleId = ref<BigIntId | ''>('')
const expiryRemindDays = ref(7)

let requestVersion = 0

const canEdit = computed(() =>
  props.canManage
  && props.document?.status === 'READY'
  && props.document.versionStatus !== 'HISTORY'
)

const busy = computed(() =>
  loading.value || saving.value
)

const responsibleOptions = computed(() => {
  const result = [...assignees.value]

  if (
    props.courseOwnerId
    && !result.some(
      item => item.id === props.courseOwnerId,
    )
  ) {
    result.unshift({
      id: props.courseOwnerId,
      username: '课程负责人',
      role: 'TEACHER',
    })
  }

  return result
})

const currentResponsibleName = computed(() => {
  const selected = responsibleOptions.value.find(
    item => item.id === responsibleId.value,
  )

  return selected?.username ?? '课程负责人'
})

function expiryStatusLabel(
  status: DocumentExpiryStatus,
): string {
  switch (status) {
    case 'UNLIMITED':
      return '长期有效'
    case 'VALID':
      return '正常有效'
    case 'EXPIRING':
      return '即将到期'
    case 'EXPIRED':
      return '已经过期'
    default:
      return '未知状态'
  }
}

function expiryStatusType(
  status: DocumentExpiryStatus,
): 'success' | 'warning' | 'danger' | 'info' {
  switch (status) {
    case 'VALID':
      return 'success'
    case 'EXPIRING':
      return 'warning'
    case 'EXPIRED':
      return 'danger'
    default:
      return 'info'
  }
}

function assigneeLabel(person: QualityAssignee): string {
  if (person.id === props.courseOwnerId) {
    return `${person.username}（课程负责人）`
  }

  if (person.role === 'ADMIN') {
    return `${person.username}（管理员）`
  }

  return person.username
}

function currentContext(): {
  courseId: BigIntId
  documentId: BigIntId
} | null {
  if (
    !props.modelValue
    || !props.courseId
    || !props.document
    || props.document.courseId !== props.courseId
  ) {
    return null
  }

  return {
    courseId: props.courseId,
    documentId: props.document.id,
  }
}

function isCurrentContext(
  context: {
    courseId: BigIntId
    documentId: BigIntId
  },
): boolean {
  return (
    props.modelValue
    && props.courseId === context.courseId
    && props.document?.id === context.documentId
  )
}

function resetForm(): void {
  detail.value = null
  assignees.value = []

  validUntil.value = null
  responsibleId.value = props.courseOwnerId ?? ''
  expiryRemindDays.value = 7
}

async function loadData(): Promise<void> {
  const context = currentContext()

  const version = ++requestVersion

  if (!context || !props.canManage) {
    resetForm()
    loading.value = false
    return
  }

  loading.value = true

  try {
    const [expiry, people] = await Promise.all([
      getDocumentExpiry(
        context.courseId,
        context.documentId,
      ),
      listQualityAssignees(context.courseId),
    ])

    if (
      version !== requestVersion
      || !isCurrentContext(context)
    ) {
      return
    }

    detail.value = expiry
    assignees.value = people

    validUntil.value = expiry.validUntil

    responsibleId.value =
      expiry.updateResponsibleId
      ?? props.courseOwnerId
      ?? ''

    expiryRemindDays.value =
      expiry.expiryRemindDays ?? 7

  } catch (error) {
    if (
      version === requestVersion
      && isCurrentContext(context)
    ) {
      notifyError(error, '资料有效期信息加载失败')
    }
  } finally {
    if (version === requestVersion) {
      loading.value = false
    }
  }
}

async function submit(): Promise<void> {
  const context = currentContext()

  if (
    !context
    || !canEdit.value
    || saving.value
  ) {
    return
  }

  if (
    expiryRemindDays.value < 0
    || expiryRemindDays.value > 90
  ) {
    notifyWarning('提前提醒天数必须在0至90天之间')
    return
  }

  if (!responsibleId.value) {
    notifyWarning('请选择资料更新责任人')
    return
  }

  const version = requestVersion

  saving.value = true

  try {
    const result = await updateDocumentExpiry(
      context.courseId,
      context.documentId,
      {
        validUntil: validUntil.value || null,

        // 选择课程负责人时使用默认责任人配置。
        updateResponsibleId:
          responsibleId.value === props.courseOwnerId
            ? null
            : responsibleId.value,

        expiryRemindDays: expiryRemindDays.value,
      },
    )

    if (
      version !== requestVersion
      || !isCurrentContext(context)
    ) {
      return
    }

    detail.value = result

    notifySuccess('资料有效期配置已保存')

    emit('changed')
    emit('update:modelValue', false)

  } catch (error) {
    if (
      version === requestVersion
      && isCurrentContext(context)
    ) {
      notifyError(error, '资料有效期保存失败')
    }
  } finally {
    saving.value = false
  }
}

function closeDialog(): void {
  if (busy.value) return

  emit('update:modelValue', false)
}

function onDialogUpdate(value: boolean): void {
  if (!value) {
    closeDialog()
  }
}

watch(
  () => [
    props.modelValue,
    props.courseId,
    props.document?.id,
  ] as const,

  ([visible]) => {
    requestVersion++

    loading.value = false

    resetForm()

    if (visible) {
      void loadData()
    }
  },

  { immediate: true },
)
</script>

<template>
  <el-dialog
    :model-value="modelValue"
    title="资料有效期管理"
    width="min(620px, 94vw)"
    append-to-body
    destroy-on-close
    :close-on-click-modal="!busy"
    :close-on-press-escape="!busy"
    @update:model-value="onDialogUpdate"
  >
    <div
      v-loading="loading"
      class="expiry-dialog"
    >
      <div class="expiry-document-header">
        <div>
          <strong>
            {{ document?.originalName ?? '课程资料' }}
          </strong>

          <p>
            资源版本 V{{ document?.versionNo ?? '-' }}
          </p>
        </div>

        <el-tag
          v-if="detail"
          :type="expiryStatusType(detail.expiryStatus)"
          size="small"
        >
          {{ expiryStatusLabel(detail.expiryStatus) }}
        </el-tag>
      </div>

      <el-alert
        v-if="document?.versionStatus === 'HISTORY'"
        title="历史版本仅供查看，不能修改有效期。"
        type="info"
        :closable="false"
      />

      <el-alert
        v-else-if="document?.status !== 'READY'"
        title="资料尚未完成处理，暂时不能配置有效期。"
        type="warning"
        :closable="false"
      />

      <template v-if="detail">
        <el-form
          label-position="top"
          class="expiry-form"
        >
          <el-form-item label="有效截止日期">
            <el-date-picker
              v-model="validUntil"
              type="date"
              value-format="YYYY-MM-DD"
              format="YYYY-MM-DD"
              clearable
              :disabled="!canEdit || saving"
              placeholder="选择截止日期"
              style="width: 100%"
            />

            <div class="expiry-form-hint">
              留空表示长期有效。截止日期当天仍可正常访问，
              次日起停止学生访问和知识库检索。
            </div>
          </el-form-item>

          <el-form-item label="资料更新责任人">
            <el-select
              v-model="responsibleId"
              :disabled="!canEdit || saving"
              style="width: 100%"
              placeholder="请选择更新责任人"
            >
              <el-option
                v-for="person in responsibleOptions"
                :key="person.id"
                :value="person.id"
                :label="assigneeLabel(person)"
              />
            </el-select>

            <div class="expiry-form-hint">
              默认由课程负责人承担更新责任。
              目前可选课程负责人和管理员。
            </div>
          </el-form-item>

          <el-form-item label="提前提醒天数">
            <el-input-number
              v-model="expiryRemindDays"
              :min="0"
              :max="90"
              :disabled="!canEdit || saving"
              controls-position="right"
              style="width: 100%"
            />

            <div class="expiry-form-hint">
              系统会在有效截止日期前的指定天数提醒责任人。
              设置为0表示仅在截止日当天提醒。
            </div>
          </el-form-item>
        </el-form>

        <div class="expiry-preview">
          <div>
            <span>当前责任人</span>
            <strong>{{ currentResponsibleName }}</strong>
          </div>

          <div>
            <span>剩余有效天数</span>
            <strong>
              {{
                !validUntil
                  ? '长期有效'
                  : validUntil !== detail.validUntil
                    ? '保存后计算'
                    : detail.remainingDays === null
                      ? '—'
                      : detail.remainingDays < 0
                        ? `已过期 ${Math.abs(detail.remainingDays)} 天`
                        : detail.remainingDays === 0
                          ? '今日到期'
                          : `剩余 ${detail.remainingDays} 天`
              }}
            </strong>
          </div>
        </div>
      </template>
    </div>

    <template #footer>
      <el-button
        :disabled="saving"
        @click="closeDialog"
      >
        取消
      </el-button>

      <el-button
        type="primary"
        :loading="saving"
        :disabled="!canEdit || loading"
        @click="submit"
      >
        保存配置
      </el-button>
    </template>
  </el-dialog>
</template>

<style scoped>
.expiry-dialog {
  min-height: 180px;
}

.expiry-document-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 22px;
  padding: 14px;
  border: 1px solid var(--sc-border);
  border-radius: 10px;
  background: var(--sc-surface-muted);
}

.expiry-document-header strong {
  color: var(--sc-text);
  font-size: 14px;
  overflow-wrap: anywhere;
}

.expiry-document-header p {
  margin-top: 7px;
  color: var(--sc-text-secondary);
  font-size: 12px;
}

.expiry-form {
  margin-top: 20px;
}

.expiry-form-hint {
  margin-top: 7px;
  color: var(--sc-text-secondary);
  font-size: 12px;
  line-height: 1.6;
}

.expiry-preview {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
  margin-top: 18px;
}

.expiry-preview > div {
  display: flex;
  flex-direction: column;
  gap: 7px;
  padding: 13px;
  border-radius: 9px;
  background: var(--sc-surface-muted);
}

.expiry-preview span {
  color: var(--sc-text-secondary);
  font-size: 11px;
}

.expiry-preview strong {
  color: var(--sc-text);
  font-size: 14px;
  overflow-wrap: anywhere;
}

@media (max-width: 480px) {
  .expiry-preview {
    grid-template-columns: 1fr;
  }
}
</style>
