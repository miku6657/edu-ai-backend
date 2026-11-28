<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'

import {
  listContentOperationLogs,
} from '@/api/content-operation-logs'

import type {
  ContentOperationLogItem,
  ContentOperationLogQuery,
} from '@/api/content-operation-logs'

import type { BigIntId } from '@/api/types'

import BaseCard from '@/components/ui/BaseCard.vue'

import { useAuthStore } from '@/stores/auth'

import { notifyError } from '@/utils/notify'

const auth = useAuthStore()

const loading = ref(false)

const records = ref<ContentOperationLogItem[]>([])

const total = ref(0)

const current = ref(1)

const size = ref(20)

const dateRange = ref<[string, string] | null>(null)

const filters = reactive({

  courseId: '' as BigIntId | '',

  operatorId: '' as BigIntId | '',

  operationType: '',

})

const operationOptions = [

  { label: '全部操作', value: '' },

  { label: '资料上传', value: 'DOCUMENT_UPLOAD' },

  { label: '资料合并', value: 'DOCUMENT_MERGE' },

  { label: '新版本上传', value: 'DOCUMENT_VERSION_UPLOAD' },

  { label: '首次发布', value: 'DOCUMENT_PUBLISH' },

  { label: '版本发布', value: 'DOCUMENT_VERSION_PUBLISH' },

  { label: '资料删除', value: 'DOCUMENT_DELETE' },

  { label: '提交审核', value: 'DOCUMENT_REVIEW_SUBMIT' },

  { label: '审核驳回', value: 'DOCUMENT_REVIEW_REJECT' },

  { label: '撤回审核', value: 'DOCUMENT_REVIEW_WITHDRAW' },

  { label: '修改有效期', value: 'DOCUMENT_EXPIRY_UPDATE' },

  { label: '创建质量抽检', value: 'QUALITY_TASK_CREATE' },

  { label: '检查质量样本', value: 'QUALITY_SAMPLE_CHECK' },

  { label: '分配整改责任人', value: 'QUALITY_ISSUE_ASSIGN' },

  { label: '提交整改结果', value: 'QUALITY_ISSUE_RESOLVE' },

  { label: '复核整改结果', value: 'QUALITY_ISSUE_REVIEW' },

  { label: '完成质量抽检', value: 'QUALITY_TASK_COMPLETE' },

  { label: '暂停资料检索', value: 'DOCUMENT_SUSPEND' },

  { label: '恢复资料检索', value: 'DOCUMENT_RESUME' },

]

function operationTagType(
  type: string,
): 'success' | 'warning' | 'danger' | 'info' {

  if (
    type.includes('DELETE')
    || type.includes('REJECT')
    || type.includes('SUSPEND')
  ) {
    return 'danger'
  }

  if (
    type.includes('PUBLISH')
    || type.includes('RESUME')
  ) {
    return 'success'
  }

  if (
    type.includes('QUALITY')
    || type.includes('EXPIRY')
  ) {
    return 'warning'
  }

  return 'info'
}

function buildQuery(): ContentOperationLogQuery {

  return {

    current: current.value,

    size: size.value,

    courseId: filters.courseId || undefined,

    operatorId: filters.operatorId || undefined,

    operationType: filters.operationType || undefined,

    startTime: dateRange.value?.[0],

    endTime: dateRange.value?.[1],

  }
}

async function loadLogs(): Promise<void> {

  if (!auth.isAdmin) return

  loading.value = true

  try {

    const result =
      await listContentOperationLogs(buildQuery())

    records.value = result.records ?? []

    total.value = result.total ?? 0

  } catch (error) {

    notifyError(error, '内容运营日志加载失败')

  } finally {

    loading.value = false

  }
}

function search(): void {

  current.value = 1

  void loadLogs()
}

function reset(): void {

  filters.courseId = ''

  filters.operatorId = ''

  filters.operationType = ''

  dateRange.value = null

  current.value = 1

  void loadLogs()
}

function handlePageChange(page: number): void {

  current.value = page

  void loadLogs()
}

function handleSizeChange(value: number): void {

  size.value = value

  current.value = 1

  void loadLogs()
}

onMounted(() => {

  void loadLogs()

})
</script>

<template>
  <div class="operation-log-page">

    <div class="page-heading">
      <div>
        <h1>内容运营日志</h1>
        <p>
          统一查看课程资料上传、审核、版本、
          有效期和质量整改操作。
        </p>
      </div>

      <el-tag type="info">
        管理员专用
      </el-tag>
    </div>

    <BaseCard title="筛选条件">

      <el-form
        :inline="true"
        class="filter-form"
        @submit.prevent="search"
      >

        <el-form-item label="课程ID">
          <el-input
            v-model="filters.courseId"
            clearable
            placeholder="输入课程ID"
            style="width: 150px"
            @keyup.enter="search"
          />
        </el-form-item>

        <el-form-item label="操作人ID">
          <el-input
            v-model="filters.operatorId"
            clearable
            placeholder="输入操作人ID"
            style="width: 150px"
            @keyup.enter="search"
          />
        </el-form-item>

        <el-form-item label="操作类型">
          <el-select
            v-model="filters.operationType"
            clearable
            placeholder="全部操作"
            style="width: 190px"
          >
            <el-option
              v-for="option in operationOptions"
              :key="option.value"
              :label="option.label"
              :value="option.value"
            />
          </el-select>
        </el-form-item>

        <el-form-item label="操作时间">
          <el-date-picker
            v-model="dateRange"
            type="datetimerange"
            value-format="YYYY-MM-DDTHH:mm:ss"
            format="YYYY-MM-DD HH:mm"
            range-separator="至"
            start-placeholder="开始时间"
            end-placeholder="结束时间"
            style="width: 340px"
          />
        </el-form-item>

        <el-form-item>
          <el-button
            type="primary"
            @click="search"
          >
            查询
          </el-button>

          <el-button @click="reset">
            重置
          </el-button>
        </el-form-item>

      </el-form>

    </BaseCard>

    <BaseCard title="操作记录">

      <template #header>
        <div class="table-heading">
          <div>
            <h3>操作记录</h3>
            <p>共 {{ total }} 条记录</p>
          </div>

          <el-button
            :loading="loading"
            @click="loadLogs"
          >
            刷新
          </el-button>
        </div>
      </template>

      <el-table
        v-loading="loading"
        :data="records"
        row-key="id"
        stripe
        style="width: 100%"
        empty-text="暂无内容运营日志"
      >

        <el-table-column type="expand">

          <template #default="{ row }">
            <div class="log-detail">

              <p>
                <strong>操作说明：</strong>
                {{ row.description || '—' }}
              </p>

              <p>
                <strong>变更前：</strong>
                {{ row.beforeValue || '—' }}
              </p>

              <p>
                <strong>变更后：</strong>
                {{ row.afterValue || '—' }}
              </p>

            </div>
          </template>

        </el-table-column>

        <el-table-column
          prop="createTime"
          label="操作时间"
          width="180"
        />

        <el-table-column
          label="操作内容"
          min-width="160"
        >
          <template #default="{ row }">
            <el-tag
              :type="operationTagType(row.operationType)"
              effect="light"
            >
              {{ row.operationName }}
            </el-tag>
          </template>
        </el-table-column>

        <el-table-column
          label="课程"
          min-width="150"
        >
          <template #default="{ row }">
            <div>{{ row.courseName }}</div>
            <small class="muted">
              ID：{{ row.courseId }}
            </small>
          </template>
        </el-table-column>

        <el-table-column
          label="资料"
          min-width="180"
          show-overflow-tooltip
        >
          <template #default="{ row }">
            <div>
              {{ row.documentName || '课程级操作' }}
            </div>

            <small
              v-if="row.documentId"
              class="muted"
            >
              ID：{{ row.documentId }}
            </small>
          </template>
        </el-table-column>

        <el-table-column
          label="操作人"
          width="150"
        >
          <template #default="{ row }">
            <div>{{ row.operatorName }}</div>
            <small class="muted">
              ID：{{ row.operatorId }}
            </small>
          </template>
        </el-table-column>

        <el-table-column
          prop="operationType"
          label="事件编码"
          min-width="190"
        />

      </el-table>

      <div class="pagination-wrap">

        <el-pagination
          :current-page="current"
          :page-size="size"
          :page-sizes="[10, 20, 50, 100]"
          :total="total"
          layout="total, sizes, prev, pager, next, jumper"
          @current-change="handlePageChange"
          @size-change="handleSizeChange"
        />

      </div>

    </BaseCard>

  </div>
</template>

<style scoped>
.operation-log-page {
  display: flex;
  flex-direction: column;
  gap: 18px;
}

.page-heading,
.table-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}

.page-heading h1 {
  margin: 0;
  color: var(--sc-text);
  font-size: 24px;
}

.page-heading p,
.table-heading p {
  margin: 7px 0 0;
  color: var(--sc-text-secondary);
  font-size: 13px;
}

.table-heading h3 {
  margin: 0;
  color: var(--sc-text);
  font-size: 16px;
}

.filter-form {
  display: flex;
  flex-wrap: wrap;
  gap: 0 8px;
}

.log-detail {
  padding: 8px 28px;
  color: var(--sc-text-secondary);
  font-size: 13px;
  line-height: 1.8;
  overflow-wrap: anywhere;
}

.log-detail p {
  margin: 4px 0;
}

.muted {
  color: var(--sc-text-secondary);
  font-size: 11px;
}

.pagination-wrap {
  display: flex;
  justify-content: flex-end;
  margin-top: 18px;
}

@media (max-width: 720px) {
  .page-heading,
  .table-heading {
    align-items: flex-start;
  }

  .pagination-wrap {
    overflow-x: auto;
    justify-content: flex-start;
  }
}
</style>
