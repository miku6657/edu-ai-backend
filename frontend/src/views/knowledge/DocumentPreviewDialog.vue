<script setup lang="ts">
import { onUnmounted, ref, watch } from 'vue'

import {
  downloadDocumentFile,
  previewDocument,
} from '@/api/knowledge'
import type { KnowledgeDoc } from '@/api/knowledge'
import { notifyError } from '@/utils/notify'

const props = defineProps<{
  modelValue: boolean
  document: KnowledgeDoc | null
}>()

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
}>()

const loading = ref(false)
const downloading = ref(false)
const previewUrl = ref('')
const errorMessage = ref('')

let controller: AbortController | null = null
let downloadController: AbortController | null = null
let requestVersion = 0

function releasePreview(): void {
  requestVersion++

  controller?.abort()
  controller = null

  if (previewUrl.value) {
    URL.revokeObjectURL(previewUrl.value)
    previewUrl.value = ''
  }

  loading.value = false
}

async function loadPreview(): Promise<void> {
  releasePreview()
  errorMessage.value = ''

  const doc = props.document
  if (!props.modelValue || !doc) {
    return
  }

  const current = requestVersion
  const abort = new AbortController()

  controller = abort
  loading.value = true

  try {
    const blob = await previewDocument(doc.id, abort.signal)

    if (
      current !== requestVersion
      || abort.signal.aborted
      || !props.modelValue
      || props.document?.id !== doc.id
    ) {
      return
    }

    previewUrl.value = URL.createObjectURL(blob)
  } catch (error) {
    if (abort.signal.aborted || current !== requestVersion) {
      return
    }

    errorMessage.value =
      error instanceof Error ? error.message : 'PDF文件加载失败'
    notifyError(error)
  } finally {
    if (current === requestVersion) {
      loading.value = false
      controller = null
    }
  }
}

function closeDialog(): void {
  releasePreview()
  errorMessage.value = ''
  emit('update:modelValue', false)
}

function onDialogUpdate(visible: boolean): void {
  if (!visible) {
    closeDialog()
  }
}

async function downloadFile(): Promise<void> {
  const doc = props.document
  if (!doc || downloading.value) {
    return
  }

  const abort = new AbortController()
  downloadController = abort
  downloading.value = true

  try {
    const blob = await downloadDocumentFile(doc.id, abort.signal)
    if (abort.signal.aborted) return

    const url = URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    link.download = doc.originalName

    document.body.appendChild(link)
    link.click()
    link.remove()

    window.setTimeout(() => {
      URL.revokeObjectURL(url)
    }, 1000)
  } catch (error) {
    if (!abort.signal.aborted) {
      notifyError(error)
    }
  } finally {
    downloading.value = false
    downloadController = null
  }
}

watch(
  () => [
    props.modelValue,
    props.document?.id,
  ] as const,
  ([visible]) => {
    if (visible) {
      void loadPreview()
    } else {
      releasePreview()
      errorMessage.value = ''
    }
  },
  { immediate: true },
)

onUnmounted(() => {
  releasePreview()
  downloadController?.abort()
})
</script>

<template>
  <el-dialog
    :model-value="modelValue"
    :title="document?.originalName || 'PDF在线预览'"
    width="min(1100px, 94vw)"
    top="4vh"
    destroy-on-close
    @update:model-value="onDialogUpdate"
  >
    <div
      v-loading="loading"
      class="pdf-preview-body"
    >
      <iframe
        v-if="previewUrl"
        :src="previewUrl"
        title="课程PDF资料预览"
        class="pdf-preview-frame"
        referrerpolicy="no-referrer"
      />

      <el-empty
        v-else-if="!loading"
        :description="errorMessage || '暂时无法预览该文件'"
      >
        <el-button
          type="primary"
          @click="loadPreview"
        >
          重新加载
        </el-button>
      </el-empty>

      <p v-if="loading" class="pdf-preview-hint">
        正在加载课程资料……
      </p>
    </div>

    <template #footer>
      <div class="pdf-preview-footer">
        <span class="pdf-preview-hint">
          如果浏览器不支持PDF预览，可下载后阅读。
        </span>

        <el-button
          :loading="downloading"
          :disabled="!document"
          type="primary"
          @click="downloadFile"
        >
          下载PDF
        </el-button>

        <el-button @click="closeDialog">
          关闭
        </el-button>
      </div>
    </template>
  </el-dialog>
</template>

<style scoped>
.pdf-preview-body {
  min-height: 300px;
  height: 70vh;
  overflow: hidden;
  border: 1px solid var(--sc-border);
  border-radius: 8px;
  background: var(--sc-surface-muted);
}

.pdf-preview-frame {
  display: block;
  width: 100%;
  height: 100%;
  border: 0;
}

.pdf-preview-footer {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  flex-wrap: wrap;
  gap: 12px;
}

.pdf-preview-hint {
  color: var(--sc-text-secondary);
  font-size: 12px;
}

.pdf-preview-footer .pdf-preview-hint {
  margin-right: auto;
}
</style>
