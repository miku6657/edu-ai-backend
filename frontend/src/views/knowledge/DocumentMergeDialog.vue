<script setup lang="ts">
import { computed, ref, watch } from 'vue'

import type { BigIntId } from '@/api/types'
import {
  mergeDocuments,
  resourceTypeLabel,
} from '@/api/knowledge'
import type { KnowledgeDoc } from '@/api/knowledge'
import {
  notifyError,
  notifySuccess,
  notifyWarning,
} from '@/utils/notify'

const props = defineProps<{
  modelValue: boolean
  courseId: BigIntId | null
  documents: KnowledgeDoc[]
}>()

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  changed: []
}>()

const selectedIds = ref<BigIntId[]>([])
const filename = ref('')
const submitting = ref(false)

const maxSize = 30 * 1024 * 1024

const availableDocs = computed(() =>
  props.documents.filter(doc =>
    doc.courseId === props.courseId
    && doc.status === 'READY'
    && doc.versionStatus === 'CURRENT',
  ),
)

const selectedDocs = computed(() =>
  selectedIds.value
    .map(id =>
      availableDocs.value.find(doc => doc.id === id),
    )
    .filter(
      (doc): doc is KnowledgeDoc => doc !== undefined,
    ),
)

const selectedType = computed(
  () => selectedDocs.value[0]?.resourceType ?? null,
)

const totalSize = computed(() =>
  selectedDocs.value.reduce(
    (sum, doc) => sum + doc.fileSize,
    0,
  ),
)

const sameType = computed(() =>
  selectedDocs.value.every(
    doc => doc.resourceType === selectedType.value,
  ),
)

const canSubmit = computed(() =>
  props.courseId !== null
  && !submitting.value
  && selectedIds.value.length === selectedDocs.value.length
  && selectedDocs.value.length >= 2
  && selectedDocs.value.length <= 10
  && sameType.value
  && totalSize.value <= maxSize
  && filename.value.trim().length > 0,
)

function optionDisabled(doc: KnowledgeDoc): boolean {
  if (selectedIds.value.includes(doc.id)) {
    return false
  }

  return selectedType.value !== null
    && selectedType.value !== doc.resourceType
}

function moveDocument(
  index: number,
  direction: number,
): void {
  const target = index + direction

  if (
    submitting.value
    || target < 0
    || target >= selectedIds.value.length
  ) {
    return
  }

  const ids = [...selectedIds.value]
  const first = ids[index]
  const second = ids[target]

  if (first === undefined || second === undefined) {
    return
  }

  ids[index] = second
  ids[target] = first
  selectedIds.value = ids
}

function removeDocument(id: BigIntId): void {
  selectedIds.value = selectedIds.value.filter(
    value => value !== id,
  )
}

function closeDialog(): void {
  if (submitting.value) return

  emit('update:modelValue', false)
}

function onDialogModelChange(visible: boolean): void {
  if (!visible) {
    closeDialog()
  }
}

async function submit(): Promise<void> {
  const courseId = props.courseId

  if (!courseId || !canSubmit.value) {
    return
  }

  let name = filename.value.trim()

  if (
    name.includes('/')
    || name.includes('\\')
    || /[\x00-\x1f\x7f]/.test(name)
  ) {
    notifyWarning('文件名称包含非法字符')
    return
  }

  if (!name.toLowerCase().endsWith('.pdf')) {
    name += '.pdf'
  }

  if (name.length > 255) {
    notifyWarning('文件名称不能超过255个字符')
    return
  }

  submitting.value = true

  try {
    await mergeDocuments(courseId, {
      documentIds: [...selectedIds.value],
      filename: name,
    })

    notifySuccess('PDF合并成功，等待解析和索引')
    emit('changed')

    if (props.courseId === courseId) {
      emit('update:modelValue', false)
    }
  } catch (error) {
    notifyError(error)
  } finally {
    submitting.value = false
  }
}

watch(
  () => [
    props.modelValue,
    props.courseId,
  ] as const,
  () => {
    selectedIds.value = []
    filename.value = ''
  },
  { immediate: true },
)

watch(availableDocs, docs => {
  const validIds = new Set(
    docs.map(doc => doc.id),
  )

  selectedIds.value = selectedIds.value.filter(
    id => validIds.has(id),
  )
})
</script>

<template>
  <el-dialog
    :model-value="modelValue"
    title="合并同类型课程资料"
    width="min(720px, 94vw)"
    :close-on-click-modal="!submitting"
    :close-on-press-escape="!submitting"
    :show-close="!submitting"
    @update:model-value="onDialogModelChange"
  >
    <div class="merge-panel">
      <el-alert
        title="合并结果将作为新资源入库，原始PDF和历史问答引用不会被删除。"
        type="info"
        :closable="false"
        show-icon
      />

      <el-form label-position="top">
        <el-form-item label="选择需要合并的资料" required>
          <el-select
            v-model="selectedIds"
            multiple
            filterable
            collapse-tags
            :multiple-limit="10"
            :disabled="submitting"
            placeholder="选择2至10份同类型PDF"
            style="width: 100%"
          >
            <el-option
              v-for="doc in availableDocs"
              :key="doc.id"
              :label="
                `${doc.originalName} · ${resourceTypeLabel(doc.resourceType)}`
              "
              :value="doc.id"
              :disabled="optionDisabled(doc)"
            />
          </el-select>
        </el-form-item>

        <div
          v-if="selectedDocs.length"
          class="merge-order"
        >
          <strong>PDF合并顺序</strong>

          <div
            v-for="(doc, index) in selectedDocs"
            :key="doc.id"
            class="merge-file"
          >
            <span class="merge-index">
              {{ index + 1 }}
            </span>

            <div class="merge-file-info">
              <strong>{{ doc.originalName }}</strong>
              <span>
                {{ resourceTypeLabel(doc.resourceType) }}
                · V{{ doc.versionNo }}
                · {{ (doc.fileSize / 1024 / 1024).toFixed(2) }} MB
              </span>
            </div>

            <div class="merge-actions">
              <el-button
                text
                size="small"
                :disabled="index === 0 || submitting"
                @click="moveDocument(index, -1)"
              >
                上移
              </el-button>
              <el-button
                text
                size="small"
                :disabled="
                  index === selectedDocs.length - 1
                  || submitting
                "
                @click="moveDocument(index, 1)"
              >
                下移
              </el-button>
              <el-button
                text
                type="danger"
                size="small"
                :disabled="submitting"
                @click="removeDocument(doc.id)"
              >
                移除
              </el-button>
            </div>
          </div>
        </div>

        <div class="merge-summary">
          已选择 {{ selectedDocs.length }} 份资料，
          总大小
          {{ (totalSize / 1024 / 1024).toFixed(2) }} MB
        </div>

        <el-alert
          v-if="totalSize > maxSize"
          title="所选PDF总大小超过30MB，请减少文件数量。"
          type="error"
          :closable="false"
        />

        <el-alert
          v-if="!sameType"
          title="只能合并相同资源类型的PDF。"
          type="error"
          :closable="false"
        />

        <el-form-item label="合并后的文件名称" required>
          <el-input
            v-model="filename"
            maxlength="251"
            show-word-limit
            :disabled="submitting"
            placeholder="例如：Java集合框架完整课件.pdf"
          />
        </el-form-item>
      </el-form>
    </div>

    <template #footer>
      <el-button
        :disabled="submitting"
        @click="closeDialog"
      >
        取消
      </el-button>
      <el-button
        type="primary"
        :disabled="!canSubmit"
        :loading="submitting"
        @click="submit"
      >
        确认合并
      </el-button>
    </template>
  </el-dialog>
</template>

<style scoped>
.merge-panel {
  display: grid;
  gap: 16px;
}

.merge-order {
  display: grid;
  gap: 8px;
  margin-bottom: 12px;
}

.merge-file {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px;
  border: 1px solid var(--sc-border);
  border-radius: 8px;
}

.merge-index {
  min-width: 24px;
  font-size: 13px;
  color: var(--sc-text-secondary);
}

.merge-file-info {
  display: grid;
  gap: 5px;
  flex: 1;
  min-width: 0;
  overflow-wrap: anywhere;
}

.merge-file-info strong {
  font-size: 13px;
}

.merge-file-info span,
.merge-summary {
  font-size: 12px;
  color: var(--sc-text-secondary);
}

.merge-actions {
  display: flex;
  flex-wrap: wrap;
}

.merge-actions :deep(.el-button) {
  margin-left: 0;
}

.merge-summary {
  margin: 12px 0;
}
</style>
