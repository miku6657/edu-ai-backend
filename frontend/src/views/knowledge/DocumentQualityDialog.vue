<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { ElMessageBox } from 'element-plus'

import type { BigIntId } from '@/api/types'
import type { KnowledgeDoc } from '@/api/knowledge'
import { useAuthStore } from '@/stores/auth'
import {
  completeQualityTask,
  createQualityTask,
  getQualityDetail,
  listQualityAssignees,
  listQualityTasks,
  runDocumentQualityScan,
  qualityIssueStatusLabel,
  qualityIssueTagType,
  qualityIssueTypeLabel,
  qualitySampleStatusLabel,
  qualitySampleTagType,
  qualityTaskStatusLabel,
} from '@/api/document-quality'
import type {
  QualityAssignee,
  QualityDetail,
  QualityIssue,
  QualityIssueStatus,
  QualitySample,
  QualitySampleStatus,
  QualityTask,
} from '@/api/document-quality'
import {
  notifyError,
  notifySuccess,
  notifyWarning,
} from '@/utils/notify'
import QualitySampleCheckDialog from './QualitySampleCheckDialog.vue'
import QualityIssueDialog from './QualityIssueDialog.vue'

const props = defineProps<{
  modelValue: boolean
  courseId: BigIntId | null
  document: KnowledgeDoc | null
  documents: KnowledgeDoc[]
}>()

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  changed: []
  preview: [document: KnowledgeDoc]
}>()

const auth = useAuthStore()
const loading = ref(false)
const saving = ref(false)
const tasks = ref<QualityTask[]>([])
const assignees = ref<QualityAssignee[]>([])
const selectedTaskId = ref<BigIntId>('')
const detail = ref<QualityDetail | null>(null)
const createVisible = ref(false)
const taskTitle = ref('')
const sampleRate = ref(20)
const activeTab = ref('samples')
const sampleFilter = ref<QualitySampleStatus | ''>('')
const issueFilter = ref<QualityIssueStatus | ''>('')
const onlyMyIssues = ref(false)
const samplePage = ref(1)
const issuePage = ref(1)
const pageSize = 10
const sampleDialogVisible = ref(false)
const selectedSample = ref<QualitySample | null>(null)
const issueDialogVisible = ref(false)
const selectedIssue = ref<QualityIssue | null>(null)
const errorMessage = ref('')
let requestVersion = 0

const busy = computed(() => loading.value || saving.value)
const progress = computed(() => detail.value?.progress ?? null)
const activeTask = computed(
  () => detail.value?.task.status === 'IN_PROGRESS',
)
const hasActiveTask = computed(
  () => tasks.value.some(task => task.status === 'IN_PROGRESS'),
)
const canCreateTask = computed(
  () =>
    props.document?.status === 'READY'
    && props.document.versionStatus !== 'HISTORY'
    && props.document.reviewStatus !== 'PENDING'
    && !hasActiveTask.value
    && !busy.value,
)
const canRunAutomaticScan = computed(
  () =>
    props.document?.status === 'READY'
    && props.document.versionStatus === 'CURRENT'
    && props.document.reviewStatus === 'APPROVED'
    && !hasActiveTask.value
    && !busy.value,
)
const filteredSamples = computed(() => {
  const samples = detail.value?.samples ?? []
  return sampleFilter.value
    ? samples.filter(item => item.status === sampleFilter.value)
    : samples
})
const pagedSamples = computed(() => {
  const start = (samplePage.value - 1) * pageSize
  return filteredSamples.value.slice(start, start + pageSize)
})
const filteredIssues = computed(() => {
  let issues = detail.value?.issues ?? []
  if (issueFilter.value) {
    issues = issues.filter(item => item.status === issueFilter.value)
  }
  if (onlyMyIssues.value) {
    issues = issues.filter(item => item.assigneeId === auth.user?.user_id)
  }
  return issues
})
const pagedIssues = computed(() => {
  const start = (issuePage.value - 1) * pageSize
  return filteredIssues.value.slice(start, start + pageSize)
})

watch(sampleFilter, () => {
  samplePage.value = 1
})

watch([issueFilter, onlyMyIssues], () => {
  issuePage.value = 1
})

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

function isCurrentContext(context: {
  courseId: BigIntId
  documentId: BigIntId
}): boolean {
  return (
    props.modelValue
    && props.courseId === context.courseId
    && props.document?.id === context.documentId
  )
}

function resetState(): void {
  tasks.value = []
  assignees.value = []
  selectedTaskId.value = ''
  detail.value = null
  createVisible.value = false
  taskTitle.value = ''
  sampleRate.value = 20
  activeTab.value = 'samples'
  sampleFilter.value = ''
  issueFilter.value = ''
  onlyMyIssues.value = false
  samplePage.value = 1
  issuePage.value = 1
  selectedSample.value = null
  sampleDialogVisible.value = false
  selectedIssue.value = null
  issueDialogVisible.value = false
  errorMessage.value = ''
}

function applyDetail(result: QualityDetail): void {
  detail.value = result
  selectedTaskId.value = result.task.id
  const index = tasks.value.findIndex(item => item.id === result.task.id)
  if (index >= 0) {
    tasks.value[index] = result.task
  } else {
    tasks.value.unshift(result.task)
  }
  if (selectedSample.value) {
    selectedSample.value = result.samples.find(
      item => item.id === selectedSample.value?.id,
    ) ?? null
  }
  if (selectedIssue.value) {
    selectedIssue.value = result.issues.find(
      item => item.id === selectedIssue.value?.id,
    ) ?? null
  }
}

async function loadAll(): Promise<void> {
  const context = currentContext()
  if (!context) return

  const version = ++requestVersion
  loading.value = true
  errorMessage.value = ''
  try {
    const [taskList, people] = await Promise.all([
      listQualityTasks(context.courseId, context.documentId),
      listQualityAssignees(context.courseId),
    ])
    if (version !== requestVersion || !isCurrentContext(context)) return

    tasks.value = taskList
    assignees.value = people
    const target =
      taskList.find(item => item.id === selectedTaskId.value)
      ?? taskList.find(item => item.status === 'IN_PROGRESS')
      ?? taskList[0]
    if (!target) {
      detail.value = null
      selectedTaskId.value = ''
      return
    }

    const result = await getQualityDetail(context.courseId, target.id)
    if (version !== requestVersion || !isCurrentContext(context)) return
    applyDetail(result)
  } catch (error) {
    if (version === requestVersion && isCurrentContext(context)) {
      errorMessage.value = '质量抽检数据加载失败'
      notifyError(error, errorMessage.value)
    }
  } finally {
    if (version === requestVersion) loading.value = false
  }
}

async function selectTask(taskId: BigIntId): Promise<void> {
  const context = currentContext()
  if (!context || !taskId || busy.value) return

  const version = ++requestVersion
  selectedTaskId.value = taskId
  detail.value = null
  loading.value = true
  try {
    const result = await getQualityDetail(context.courseId, taskId)
    if (
      version !== requestVersion
      || !isCurrentContext(context)
      || selectedTaskId.value !== taskId
    ) {
      return
    }
    applyDetail(result)
  } catch (error) {
    if (version === requestVersion) notifyError(error, '加载抽检任务失败')
  } finally {
    if (version === requestVersion) loading.value = false
  }
}

function openCreateDialog(): void {
  if (!canCreateTask.value) return
  taskTitle.value =
    `${props.document?.originalName ?? '课程资料'}质量抽检`.slice(0, 200)
  sampleRate.value = 20
  createVisible.value = true
}

async function saveTask(): Promise<void> {
  const context = currentContext()
  if (!context || saving.value || !canCreateTask.value) return

  const title = taskTitle.value.trim()
  if (!title) {
    notifyWarning('请输入抽检任务名称')
    return
  }
  if (title.length > 200) {
    notifyWarning('任务名称不能超过200个字符')
    return
  }
  if (
    !Number.isInteger(sampleRate.value)
    || sampleRate.value < 1
    || sampleRate.value > 100
  ) {
    notifyWarning('抽检比例必须为1至100之间的整数')
    return
  }

  saving.value = true
  try {
    const result = await createQualityTask(
      context.courseId,
      context.documentId,
      { title, sampleRate: sampleRate.value },
    )
    if (!isCurrentContext(context)) return
    applyDetail(result)
    createVisible.value = false
    activeTab.value = 'samples'
    notifySuccess(`抽检任务创建成功，共抽取${result.task.sampleCount}个样本`)
    emit('changed')
  } catch (error) {
    if (isCurrentContext(context)) notifyError(error, '创建抽检任务失败')
  } finally {
    saving.value = false
  }
}

/**
 * 执行PDF自动质量检查。
 */
async function runAutomaticScan(): Promise<void> {

  const context = currentContext()

  if (
    !context
    || !canRunAutomaticScan.value
    || saving.value
  ) {
    return
  }

  try {

    await ElMessageBox.confirm(
      [
        '本次检查将扫描PDF全部页面和知识切片。',
        '检查内容包括：',
        '1. 无法提取文本的页面。',
        '2. PDF页面解析异常。',
        '3. 同课程其他资料中的重复内容。',
        '4. 资料有效期。',
        '',
        '发现的问题将自动生成整改任务，并分配给课程负责人。',
      ].join('\n'),
      '开始自动质量检查',
      {
        type: 'warning',
        confirmButtonText: '开始检查',
        cancelButtonText: '取消',
      },
    )

  } catch {
    return
  }

  saving.value = true

  try {

    const result = await runDocumentQualityScan(
      context.courseId,
      context.documentId,
    )

    if (!isCurrentContext(context)) return

    applyDetail(result)

    activeTab.value =
      result.issues.length > 0
        ? 'issues'
        : 'samples'

    if (result.issues.length > 0) {

      notifyWarning(
        `自动检查完成，发现${result.issues.length}项质量问题，请及时整改`,
      )

    } else {

      notifySuccess(
        '自动检查完成，暂未发现质量问题',
      )
    }

    emit('changed')

  } catch (error) {

    if (isCurrentContext(context)) {

      notifyError(
        error,
        '自动质量检查失败',
      )
    }

  } finally {

    saving.value = false
  }
}

function openSample(sample: QualitySample): void {
  if (busy.value) return
  selectedSample.value = sample
  sampleDialogVisible.value = true
}

function openIssue(issue: QualityIssue): void {
  if (busy.value) return
  selectedIssue.value = issue
  issueDialogVisible.value = true
}

function onDetailChanged(result: QualityDetail): void {
  if (
    result.task.courseId !== props.courseId
    || result.task.documentId !== props.document?.id
    || result.task.id !== selectedTaskId.value
  ) {
    return
  }
  applyDetail(result)
  emit('changed')
}

async function finishTask(): Promise<void> {
  const context = currentContext()
  const current = detail.value
  if (
    !context
    || !current
    || saving.value
    || !current.progress.canComplete
  ) {
    return
  }

  const taskId = current.task.id
  saving.value = true
  try {
    await ElMessageBox.confirm(
      '所有样本均已检查，所有质量问题均已关闭。确定完成本次抽检吗？',
      '完成质量抽检',
      { type: 'warning' },
    )
    if (!isCurrentContext(context) || selectedTaskId.value !== taskId) return

    const result = await completeQualityTask(context.courseId, taskId)
    if (!isCurrentContext(context)) return
    applyDetail(result)
    notifySuccess('质量抽检任务已完成')
    emit('changed')
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') {
      notifyError(error, '完成抽检失败')
    }
  } finally {
    saving.value = false
  }
}

function previewCurrentDocument(): void {
  if (props.document) emit('preview', props.document)
}

function assigneeName(id: BigIntId): string {
  return assignees.value.find(person => person.id === id)?.username
    ?? `用户 ${id}`
}

function samplePageNo(sampleId: BigIntId): string {
  const sample = detail.value?.samples.find(item => item.id === sampleId)
  return sample ? `第 ${sample.pageNo} 页` : '—'
}

function closeDialog(): void {
  if (!saving.value) emit('update:modelValue', false)
}

function onDialogUpdate(value: boolean): void {
  if (!value) closeDialog()
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
    resetState()
    if (visible) void loadAll()
  },
  { immediate: true },
)
</script>

<template>
  <el-dialog
    :model-value="modelValue"
    title="知识库质量抽检"
    width="min(1180px, 96vw)"
    top="4vh"
    append-to-body
    destroy-on-close
    :close-on-click-modal="false"
    :close-on-press-escape="!saving"
    @update:model-value="onDialogUpdate"
  >
    <div v-loading="busy" class="quality-panel">
      <div class="document-header">
        <div>
          <strong class="document-name">
            {{ document?.originalName }}
          </strong>
          <div class="muted">
            资源版本 V{{ document?.versionNo }}
          </div>
        </div>
        <div class="actions">
          <el-button :disabled="busy" @click="previewCurrentDocument">
            预览 PDF
          </el-button>
          <el-button :disabled="busy" @click="loadAll">
            刷新
          </el-button>
          <el-button
            type="warning"
            :disabled="!canRunAutomaticScan"
            :loading="saving"
            @click="runAutomaticScan"
          >
            自动质量检查
          </el-button>
          <el-button
            type="primary"
            :disabled="!canCreateTask"
            @click="openCreateDialog"
          >
            创建抽检任务
          </el-button>
        </div>
      </div>

      <el-alert
        title="自动检查会扫描全部PDF页面及知识切片，发现的问题将进入现有质量整改流程。页面无文本不代表物理缺页，需要人工核对。"
        type="info"
        :closable="false"
        show-icon
      />
      <el-alert
        v-if="hasActiveTask"
        title="当前资料存在进行中的抽检任务"
        type="info"
        :closable="false"
      />
      <el-alert
        v-if="document?.reviewStatus === 'PENDING'"
        title="资料正在等待发布审核，请撤回审核后再进行质量抽检"
        type="warning"
        :closable="false"
      />
      <el-alert
        v-if="errorMessage"
        :title="errorMessage"
        type="error"
        :closable="false"
      />

      <div v-if="tasks.length" class="task-selector">
        <strong>抽检任务</strong>
        <el-select
          :model-value="selectedTaskId"
          :disabled="busy"
          style="flex: 1"
          placeholder="请选择抽检任务"
          @change="selectTask"
        >
          <el-option
            v-for="task in tasks"
            :key="task.id"
            :label="`${task.title} · ${qualityTaskStatusLabel(task.status)}`"
            :value="task.id"
          />
        </el-select>
      </div>

      <el-empty
        v-if="!loading && !errorMessage && !tasks.length"
        description="当前资料尚未创建质量抽检任务"
      >
        <el-button
          type="primary"
          :disabled="!canCreateTask"
          @click="openCreateDialog"
        >
          创建首次抽检
        </el-button>
      </el-empty>

      <template v-if="detail && progress">
        <section class="quality-section">
          <div class="section-heading">
            <strong>{{ detail.task.title }}</strong>
            <el-tag :type="activeTask ? 'warning' : 'success'">
              {{ qualityTaskStatusLabel(detail.task.status) }}
            </el-tag>
          </div>
          <div class="muted">
            原始切片 {{ detail.task.totalChunks }} 个
            · 抽检比例 {{ detail.task.sampleRate }}%
            · 实际样本 {{ detail.task.sampleCount }} 个
          </div>
          <div class="statistics-grid">
            <div class="stat">
              <span>抽检样本</span>
              <strong>{{ progress.totalSamples }}</strong>
            </div>
            <div class="stat">
              <span>已检查</span>
              <strong>{{ progress.checkedSamples }}</strong>
            </div>
            <div class="stat">
              <span>待检查</span>
              <strong>{{ progress.pendingSamples }}</strong>
            </div>
            <div class="stat">
              <span>初检合格</span>
              <strong class="success">{{ progress.passedSamples }}</strong>
            </div>
            <div class="stat">
              <span>初检不合格</span>
              <strong class="danger">{{ progress.failedSamples }}</strong>
            </div>
            <div class="stat">
              <span>初检合格率</span>
              <strong>
                {{ progress.checkedSamples ? `${progress.passRate}%` : '—' }}
              </strong>
            </div>
          </div>
          <el-progress
            :percentage="progress.checkPercent"
            :status="progress.checkPercent === 100 ? 'success' : undefined"
          />
          <div class="issue-statistics">
            <span>质量问题：{{ progress.totalIssues }}</span>
            <span class="danger">待整改：{{ progress.openIssues }}</span>
            <span class="warning">待复核：{{ progress.resolvedIssues }}</span>
            <span class="success">已关闭：{{ progress.closedIssues }}</span>
          </div>
        </section>

        <el-tabs v-model="activeTab">
          <el-tab-pane
            :label="`抽检样本（${progress.totalSamples}）`"
            name="samples"
          >
            <el-select
              v-model="sampleFilter"
              style="width: 160px; margin-bottom: 12px"
            >
              <el-option label="全部样本" value="" />
              <el-option label="待检查" value="PENDING" />
              <el-option label="合格" value="PASS" />
              <el-option label="不合格" value="FAIL" />
            </el-select>
            <el-table :data="pagedSamples" row-key="id" border>
              <el-table-column label="页码" prop="pageNo" width="80" />
              <el-table-column
                label="章节"
                min-width="120"
                show-overflow-tooltip
              >
                <template #default="{ row }">
                  {{ row.sectionTitle || '未识别' }}
                </template>
              </el-table-column>
              <el-table-column label="切片正文" min-width="240">
                <template #default="{ row }">
                  <div class="content-summary">
                    {{ row.content }}
                  </div>
                </template>
              </el-table-column>
              <el-table-column label="重复检测" width="115">
                <template #default="{ row }">
                  <el-tag v-if="row.duplicateCount > 1" type="warning">
                    重复 {{ row.duplicateCount }} 处
                  </el-tag>
                  <span v-else class="muted">未发现重复</span>
                </template>
              </el-table-column>
              <el-table-column label="检查结果" width="100">
                <template #default="{ row }">
                  <el-tag :type="qualitySampleTagType(row.status)">
                    {{ qualitySampleStatusLabel(row.status) }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column label="操作" width="90">
                <template #default="{ row }">
                  <el-button
                    link
                    type="primary"
                    :disabled="busy"
                    @click="openSample(row)"
                  >
                    {{ activeTask && row.status === 'PENDING' ? '检查' : '查看' }}
                  </el-button>
                </template>
              </el-table-column>
            </el-table>
            <el-pagination
              v-model:current-page="samplePage"
              :page-size="pageSize"
              :total="filteredSamples.length"
              layout="total, prev, pager, next"
              class="pagination"
            />
          </el-tab-pane>

          <el-tab-pane
            :label="`质量问题（${progress.totalIssues}）`"
            name="issues"
          >
            <div class="filter-row">
              <el-select v-model="issueFilter" style="width: 160px">
                <el-option label="全部问题" value="" />
                <el-option label="待整改" value="OPEN" />
                <el-option label="待复核" value="RESOLVED" />
                <el-option label="已关闭" value="CLOSED" />
              </el-select>
              <el-checkbox v-model="onlyMyIssues">
                只看分配给我的问题
              </el-checkbox>
            </div>
            <el-table :data="pagedIssues" row-key="id" border>
              <el-table-column label="问题类型" min-width="130">
                <template #default="{ row }">
                  {{ qualityIssueTypeLabel(row.issueType) }}
                </template>
              </el-table-column>
              <el-table-column label="原始位置" width="100">
                <template #default="{ row }">
                  {{ samplePageNo(row.sampleId) }}
                </template>
              </el-table-column>
              <el-table-column label="问题描述" min-width="220">
                <template #default="{ row }">
                  <div class="content-summary">
                    {{ row.description }}
                  </div>
                </template>
              </el-table-column>
              <el-table-column label="整改责任人" min-width="120">
                <template #default="{ row }">
                  {{ assigneeName(row.assigneeId) }}
                </template>
              </el-table-column>
              <el-table-column label="问题状态" width="100">
                <template #default="{ row }">
                  <el-tag :type="qualityIssueTagType(row.status)">
                    {{ qualityIssueStatusLabel(row.status) }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column label="操作" width="90">
                <template #default="{ row }">
                  <el-button
                    link
                    type="primary"
                    :disabled="busy"
                    @click="openIssue(row)"
                  >
                    {{ activeTask && row.status !== 'CLOSED' ? '处理' : '详情' }}
                  </el-button>
                </template>
              </el-table-column>
            </el-table>
            <el-pagination
              v-model:current-page="issuePage"
              :page-size="pageSize"
              :total="filteredIssues.length"
              layout="total, prev, pager, next"
              class="pagination"
            />
          </el-tab-pane>
        </el-tabs>

        <el-alert
          v-if="detail.task.status === 'COMPLETED'"
          title="本次抽检已完成，历史记录仅供查看"
          type="success"
          :closable="false"
        />
        <el-alert
          v-else-if="progress.canComplete"
          title="所有样本及质量问题均已处理，可以完成抽检"
          type="success"
          :closable="false"
        />
        <el-alert
          v-else
          title="仍存在未检查样本或未关闭问题，暂不能完成抽检"
          type="warning"
          :closable="false"
        />
      </template>
    </div>

    <template #footer>
      <el-button :disabled="saving" @click="closeDialog">
        关闭
      </el-button>
      <el-button
        v-if="activeTask"
        type="success"
        :loading="saving"
        :disabled="!progress?.canComplete || busy"
        @click="finishTask"
      >
        完成抽检
      </el-button>
    </template>

    <el-dialog
      v-model="createVisible"
      title="创建质量抽检任务"
      width="min(520px, 94vw)"
      append-to-body
      :close-on-click-modal="!saving"
      :close-on-press-escape="!saving"
    >
      <el-form label-position="top">
        <el-form-item label="抽检任务名称">
          <el-input
            v-model="taskTitle"
            maxlength="200"
            show-word-limit
            :disabled="saving"
          />
        </el-form-item>
        <el-form-item label="抽检比例（%）">
          <el-input-number
            v-model="sampleRate"
            :min="1"
            :max="100"
            :step="5"
            :precision="0"
            :disabled="saving"
          />
        </el-form-item>
        <el-alert
          title="系统将随机抽取切片，至少抽取一个样本，并保存抽检正文快照"
          type="info"
          :closable="false"
        />
      </el-form>
      <template #footer>
        <el-button :disabled="saving" @click="createVisible = false">
          取消
        </el-button>
        <el-button type="primary" :loading="saving" @click="saveTask">
          创建任务
        </el-button>
      </template>
    </el-dialog>

    <QualitySampleCheckDialog
      v-model="sampleDialogVisible"
      :course-id="courseId"
      :task-id="detail?.task.id ?? null"
      :sample="selectedSample"
      :assignees="assignees"
      :editable="activeTask"
      @changed="onDetailChanged"
      @preview="previewCurrentDocument"
    />
    <QualityIssueDialog
      v-model="issueDialogVisible"
      :course-id="courseId"
      :task="detail?.task ?? null"
      :issue="selectedIssue"
      :assignees="assignees"
      :documents="documents"
      @changed="onDetailChanged"
      @preview="document => emit('preview', document)"
    />
  </el-dialog>
</template>

<style scoped>
.quality-panel {
  display: flex;
  flex-direction: column;
  gap: 16px;
  min-height: 180px;
}

.document-header,
.section-heading,
.task-selector {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.document-header {
  flex-wrap: wrap;
}

.document-name {
  font-size: 17px;
  overflow-wrap: anywhere;
}

.actions,
.filter-row,
.issue-statistics {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 10px;
}

.quality-section {
  display: flex;
  flex-direction: column;
  gap: 14px;
  padding: 16px;
  border: 1px solid var(--el-border-color);
  border-radius: 8px;
}

.statistics-grid {
  display: grid;
  grid-template-columns: repeat(6, minmax(0, 1fr));
  gap: 10px;
}

.stat {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 12px;
  border-radius: 8px;
  background: var(--el-fill-color-light);
}

.stat span,
.muted {
  color: var(--el-text-color-secondary);
  font-size: 12px;
}

.stat strong {
  font-size: 22px;
}

.success {
  color: var(--el-color-success);
}

.warning {
  color: var(--el-color-warning);
}

.danger {
  color: var(--el-color-danger);
}

.issue-statistics {
  font-size: 13px;
}

.content-summary {
  display: -webkit-box;
  overflow: hidden;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 3;
  overflow-wrap: anywhere;
  line-height: 1.6;
}

.pagination {
  justify-content: flex-end;
  margin-top: 16px;
}

@media (max-width: 768px) {
  .statistics-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
  .document-header {
    align-items: flex-start;
    flex-direction: column;
  }
}
</style>
