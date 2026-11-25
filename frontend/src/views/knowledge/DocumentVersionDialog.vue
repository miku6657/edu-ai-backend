<script setup lang="ts">
import { computed, ref, watch } from 'vue'

import {
  listDocumentVersions,
  uploadDocumentVersion,
  versionStatusLabel,
} from '@/api/knowledge'
import type {
  DocumentVersion,
  KnowledgeDoc,
} from '@/api/knowledge'
import {
  documentStatusLabel,
  documentStatusTagType,
  formatFileSize,
  uploadFileError,
  UPLOAD_ACCEPT,
} from '@/views/knowledge/knowledge-form'
import {
  notifyError,
  notifySuccess,
  notifyWarning,
} from '@/utils/notify'

const props = defineProps<{
  modelValue: boolean
  document: KnowledgeDoc | null
  canManage: boolean
  refreshKey: string
}>()

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  changed: []
  preview: [
    document: Pick<
      KnowledgeDoc,
      'id' | 'originalName'
    >,
  ]
}>()

const versions = ref<DocumentVersion[]>([])
const loading = ref(false)
const uploading = ref(false)
const loadError = ref('')
const selectedFile = ref<File | null>(null)
const fileInput = ref<HTMLInputElement | null>(null)

let requestVersion = 0

const currentVersion = computed(() =>
  versions.value.find(
    version =>
      version.versionStatus === 'CURRENT'
      && version.status === 'READY',
  ) ?? null,
)

const pendingVersion = computed(() =>
  versions.value.find(
    version =>
      version.versionStatus === 'PENDING'
      && version.status !== 'DELETED',
  ) ?? null,
)

const canUploadVersion = computed(() =>
  props.canManage
  && currentVersion.value !== null
  && pendingVersion.value === null
  && !loading.value
  && !uploading.value
  && !loadError.value,
)

async function loadVersions(): Promise<void> {
  const doc = props.document
  const sequence = ++requestVersion

  if (
    !props.modelValue
    || !doc
    || !props.canManage
  ) {
    versions.value = []
    loading.value = false
    return
  }

  loading.value = true
  loadError.value = ''

  try {
    const result = await listDocumentVersions(doc.id)

    if (
      sequence !== requestVersion
      || !props.modelValue
      || props.document?.resourceId !== doc.resourceId
    ) {
      return
    }

    versions.value = [...result].sort(
      (a, b) => b.versionNo - a.versionNo,
    )
  } catch (error) {
    if (sequence !== requestVersion) return

    loadError.value =
      error instanceof Error ? error.message : '版本记录加载失败'
    notifyError(error)
  } finally {
    if (sequence === requestVersion) {
      loading.value = false
    }
  }
}

function chooseFile(): void {
  if (!canUploadVersion.value) return

  fileInput.value?.click()
}

function onFileSelected(event: Event): void {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]

  input.value = ''
  if (!file) return

  const error = uploadFileError(file)
  if (error !== null) {
    selectedFile.value = null
    notifyWarning(error)
    return
  }

  selectedFile.value = file
}

async function submitVersion(): Promise<void> {
  const current = currentVersion.value
  const file = selectedFile.value
  const resourceId = props.document?.resourceId

  if (
    !canUploadVersion.value
    || !current
    || !file
  ) {
    return
  }

  uploading.value = true

  try {
    const uploaded = await uploadDocumentVersion(
      current.documentId,
      file,
    )

    notifySuccess(
      `V${uploaded.versionNo} 上传成功，等待解析及索引`,
    )
    emit('changed')

    if (
      props.modelValue
      && props.document?.resourceId === resourceId
    ) {
      selectedFile.value = null
      await loadVersions()
    }
  } catch (error) {
    notifyError(error)

    if (
      props.modelValue
      && props.document?.resourceId === resourceId
    ) {
      await loadVersions()
    }
  } finally {
    uploading.value = false
  }
}

function previewVersion(version: DocumentVersion): void {
  if (version.status !== 'READY') {
    notifyWarning('该版本尚未完成处理')
    return
  }

  emit('preview', {
    id: version.documentId,
    originalName: version.originalName,
  })
}

function closeDialog(): void {
  if (uploading.value) return

  emit('update:modelValue', false)
}

function onDialogUpdate(visible: boolean): void {
  if (!visible) closeDialog()
}

function versionTagType(
  status: DocumentVersion['versionStatus'],
): 'success' | 'warning' | 'info' {
  switch (status) {
    case 'CURRENT':
      return 'success'
    case 'PENDING':
      return 'warning'
    default:
      return 'info'
  }
}

function formatTime(value: string): string {
  return value
    ? new Date(value).toLocaleString()
    : '—'
}

watch(
  () => [
    props.modelValue,
    props.document?.resourceId,
  ] as const,
  ([visible]) => {
    requestVersion++
    selectedFile.value = null
    versions.value = []
    loadError.value = ''
    loading.value = false

    if (visible) {
      void loadVersions()
    }
  },
  { immediate: true },
)

watch(
  () => props.refreshKey,
  () => {
    if (
      props.modelValue
      && props.canManage
      && !uploading.value
    ) {
      void loadVersions()
    }
  },
)
</script>

<template>
  <el-dialog
    :model-value="modelValue"
    title="课程资源版本管理"
    width="min(900px, 94vw)"
    :close-on-click-modal="!uploading"
    :close-on-press-escape="!uploading"
    :show-close="!uploading"
    @update:model-value="onDialogUpdate"
  >
    <div
      v-loading="loading"
      class="version-dialog"
    >
      <div class="version-header">
        <div>
          <h3>{{ document?.originalName }}</h3>
          <p>资源 ID：{{ document?.resourceId }}</p>
        </div>

        <el-tag
          v-if="currentVersion"
          type="success"
        >
          当前生效 V{{ currentVersion.versionNo }}
        </el-tag>
      </div>

      <el-alert
        v-if="loadError"
        :title="loadError"
        type="error"
        :closable="false"
      />

      <div class="version-section">
        <div class="version-section-heading">
          <h4>上传新版本</h4>

          <el-button
            text
            type="primary"
            :disabled="loading || uploading"
            @click="loadVersions"
          >
            刷新版本状态
          </el-button>
        </div>

        <el-alert
          title="新版本索引成功后才会替换当前版本，原有PDF和历史引用将继续保留。"
          type="info"
          :closable="false"
          show-icon
        />

        <el-alert
          v-if="pendingVersion"
          :title="`V${pendingVersion.versionNo} 尚未完成处理，当前状态：${documentStatusLabel(pendingVersion.status)}`"
          type="warning"
          :closable="false"
          show-icon
        />

        <p
          v-if="pendingVersion?.status === 'FAILED'"
          class="version-hint"
        >
          请关闭窗口，在知识库资源列表中重试
          或删除该失败版本后，再上传新版本。
        </p>

        <input
          ref="fileInput"
          type="file"
          :accept="UPLOAD_ACCEPT"
          hidden
          @change="onFileSelected"
        />

        <div class="version-upload-actions">
          <el-button
            :disabled="!canUploadVersion"
            @click="chooseFile"
          >
            选择新版PDF
          </el-button>

          <el-button
            type="primary"
            :loading="uploading"
            :disabled="!selectedFile || !canUploadVersion"
            @click="submitVersion"
          >
            上传新版本
          </el-button>

          <el-button
            v-if="selectedFile"
            text
            :disabled="uploading"
            @click="selectedFile = null"
          >
            取消选择
          </el-button>
        </div>

        <div
          v-if="selectedFile"
          class="version-selected-file"
        >
          <strong>{{ selectedFile.name }}</strong>
          <span>{{ formatFileSize(selectedFile.size) }}</span>
        </div>

        <p class="version-hint">
          仅支持PDF，单个文件不得超过30MB。
          新版本继承原资源的章节和知识点关联。
        </p>
      </div>

      <div class="version-section">
        <h4>版本历史</h4>

        <el-empty
          v-if="versions.length === 0 && !loading"
          description="暂无版本记录"
          :image-size="70"
        />

        <el-table
          v-else
          :data="versions"
          row-key="documentId"
          size="small"
        >
          <el-table-column label="版本" width="75">
            <template #default="{ row }">
              V{{ row.versionNo }}
            </template>
          </el-table-column>

          <el-table-column
            prop="originalName"
            label="文件名称"
            min-width="160"
            show-overflow-tooltip
          />

          <el-table-column label="版本状态" width="115">
            <template #default="{ row }">
              <el-tag
                :type="versionTagType(row.versionStatus)"
                size="small"
              >
                {{ versionStatusLabel(row.versionStatus) }}
              </el-tag>
            </template>
          </el-table-column>

          <el-table-column label="处理状态" width="100">
            <template #default="{ row }">
              <el-tag
                :type="documentStatusTagType(row.status)"
                size="small"
              >
                {{ documentStatusLabel(row.status) }}
              </el-tag>
            </template>
          </el-table-column>

          <el-table-column label="上传时间" width="170">
            <template #default="{ row }">
              {{ formatTime(row.createTime) }}
            </template>
          </el-table-column>

          <el-table-column label="操作" width="80">
            <template #default="{ row }">
              <el-button
                v-if="row.status === 'READY'"
                text
                type="primary"
                :disabled="uploading"
                @click="previewVersion(row)"
              >
                预览
              </el-button>
            </template>
          </el-table-column>
        </el-table>
      </div>
    </div>

    <template #footer>
      <el-button
        :disabled="uploading"
        @click="closeDialog"
      >
        关闭
      </el-button>
    </template>
  </el-dialog>
</template>

<style scoped>
.version-dialog {
  display: grid;
  gap: 20px;
}

.version-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 12px;
}

.version-header h3 {
  margin: 0 0 6px;
  font-size: 16px;
  overflow-wrap: anywhere;
}

.version-header p {
  margin: 0;
  color: var(--sc-text-secondary);
  font-size: 12px;
}

.version-section {
  display: grid;
  gap: 12px;
  padding: 14px;
  border: 1px solid var(--sc-border);
  border-radius: 10px;
}

.version-section h4 {
  margin: 0;
  font-size: 14px;
}

.version-section-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.version-upload-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.version-upload-actions :deep(.el-button) {
  margin-left: 0;
}

.version-selected-file {
  display: flex;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 8px;
  padding: 10px;
  border-radius: 6px;
  background: var(--sc-surface-muted);
  font-size: 13px;
  overflow-wrap: anywhere;
}

.version-hint {
  margin: 0;
  color: var(--sc-text-secondary);
  font-size: 12px;
  line-height: 1.7;
}
</style>
