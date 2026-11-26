<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'

import {
  getQaTicket,
  listAssignedQaTickets,
  listMyQaTickets,
  replyQaTicket,
} from '@/api/qa-tickets'
import type {
  QaTicket,
  QaTicketStatus,
} from '@/api/qa-tickets'
import BaseCard from '@/components/ui/BaseCard.vue'
import { useAuthStore } from '@/stores/auth'
import { notifyError, notifySuccess, notifyWarning } from '@/utils/notify'

const auth = useAuthStore()
const isTeacher = computed(
  () => auth.isTeacher || auth.isAdmin,
)

const loading = ref(false)
const submitting = ref(false)
const items = ref<QaTicket[]>([])
const selectedTicket = ref<QaTicket | null>(null)
const dialogVisible = ref(false)
const replyText = ref('')
const statusFilter = ref<'ALL' | QaTicketStatus>('PENDING')
const page = ref(1)
const pageSize = 10
const total = ref(0)

const canReply = computed(
  () => isTeacher.value
    && selectedTicket.value?.status === 'PENDING',
)

async function loadTickets(): Promise<void> {
  loading.value = true
  try {
    const query = {
      status: statusFilter.value === 'ALL'
        ? undefined
        : statusFilter.value,
      page: page.value,
      size: pageSize,
    }

    const result = isTeacher.value
      ? await listAssignedQaTickets(query)
      : await listMyQaTickets(query)

    items.value = result.items
    total.value = result.total
  } catch (error) {
    notifyError(error)
  } finally {
    loading.value = false
  }
}

async function openDetail(row: QaTicket): Promise<void> {
  try {
    selectedTicket.value = await getQaTicket(row.id)
    replyText.value = ''
    dialogVisible.value = true
  } catch (error) {
    notifyError(error)
  }
}

function handleStatusChange(): void {
  page.value = 1
  void loadTickets()
}

async function submitReply(): Promise<void> {
  const ticket = selectedTicket.value
  if (!ticket || !canReply.value || submitting.value) {
    return
  }

  const content = replyText.value.trim()
  if (!content) {
    notifyWarning('请输入答疑内容')
    return
  }
  if (content.length > 5000) {
    notifyWarning('答复不能超过5000个字符')
    return
  }

  submitting.value = true
  try {
    await replyQaTicket(ticket.id, content)
    notifySuccess('答疑回复提交成功')
    selectedTicket.value = await getQaTicket(ticket.id)
    replyText.value = ''
    await loadTickets()
  } catch (error) {
    notifyError(error)
  } finally {
    submitting.value = false
  }
}

function statusLabel(status: QaTicketStatus): string {
  return status === 'PENDING' ? '待教师答复' : '已答复'
}

function statusType(
  status: QaTicketStatus,
): 'warning' | 'success' {
  return status === 'PENDING' ? 'warning' : 'success'
}

function reasonLabel(reason: string): string {
  if (reason === 'NO_ACTIVE_DOCUMENTS') {
    return '课程暂无生效资料'
  }
  if (reason === 'NO_RETRIEVAL_MATCH') {
    return '知识库未检索到相关内容'
  }
  return '课程资料缺少相关依据'
}

function handlePageChange(value: number): void {
  page.value = value
  void loadTickets()
}

onMounted(() => {
  void loadTickets()
})
</script>

<template>
  <div class="qa-ticket-page">
    <BaseCard
      title="课程答疑工单"
      subtitle="查看学生问题、处理人工答疑并跟踪处理结果"
    >
      <template #actions>
        <el-button
          :loading="loading"
          @click="loadTickets"
        >
          刷新
        </el-button>
      </template>

      <div class="qa-ticket-toolbar">
        <span class="qa-ticket-toolbar__label">
          工单状态
        </span>
        <el-radio-group
          v-model="statusFilter"
          size="small"
          @change="handleStatusChange"
        >
          <el-radio-button value="ALL">
            全部
          </el-radio-button>
          <el-radio-button value="PENDING">
            待答复
          </el-radio-button>
          <el-radio-button value="ANSWERED">
            已答复
          </el-radio-button>
        </el-radio-group>
      </div>

      <el-table
        v-loading="loading"
        :data="items"
        row-key="id"
        empty-text="暂无答疑工单"
        @row-click="openDetail"
      >
        <el-table-column
          prop="courseName"
          label="课程"
          min-width="130"
          show-overflow-tooltip
        />
        <el-table-column
          v-if="isTeacher"
          prop="studentName"
          label="提问学生"
          width="120"
        />
        <el-table-column
          prop="questionText"
          label="学生问题"
          min-width="220"
          show-overflow-tooltip
        />
        <el-table-column
          label="转人工原因"
          min-width="160"
        >
          <template #default="{ row }">
            {{ reasonLabel(row.reason) }}
          </template>
        </el-table-column>
        <el-table-column
          label="状态"
          width="120"
        >
          <template #default="{ row }">
            <el-tag :type="statusType(row.status)">
              {{ statusLabel(row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column
          prop="createdAt"
          label="提交时间"
          min-width="170"
        />
        <el-table-column
          label="操作"
          width="100"
          fixed="right"
        >
          <template #default="{ row }">
            <el-button
              text
              type="primary"
              @click.stop="openDetail(row)"
            >
              查看详情
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="qa-ticket-pagination">
        <el-pagination
          v-model:current-page="page"
          :page-size="pageSize"
          :total="total"
          layout="total, prev, pager, next"
          @current-change="handlePageChange"
        />
      </div>
    </BaseCard>

    <el-dialog
      v-model="dialogVisible"
      title="答疑工单详情"
      width="650px"
      destroy-on-close
    >
      <template v-if="selectedTicket">
        <div class="qa-ticket-detail">
          <div class="qa-ticket-detail__meta">
            <span>
              课程：{{ selectedTicket.courseName }}
            </span>
            <span>
              提问学生：{{ selectedTicket.studentName }}
            </span>
            <el-tag :type="statusType(selectedTicket.status)">
              {{ statusLabel(selectedTicket.status) }}
            </el-tag>
          </div>

          <el-divider content-position="left">
            学生问题
          </el-divider>
          <div class="qa-ticket-question">
            {{ selectedTicket.questionText }}
          </div>

          <el-descriptions
            :column="1"
            border
            size="small"
          >
            <el-descriptions-item label="转人工原因">
              {{ reasonLabel(selectedTicket.reason) }}
            </el-descriptions-item>
            <el-descriptions-item label="提交时间">
              {{ selectedTicket.createdAt }}
            </el-descriptions-item>
            <el-descriptions-item
              v-if="selectedTicket.answeredAt"
              label="答复时间"
            >
              {{ selectedTicket.answeredAt }}
            </el-descriptions-item>
          </el-descriptions>

          <el-divider content-position="left">
            教师答复
          </el-divider>
          <div
            v-if="selectedTicket.teacherReply"
            class="qa-ticket-reply"
          >
            {{ selectedTicket.teacherReply }}
          </div>
          <el-empty
            v-else
            description="教师暂未答复"
            :image-size="60"
          />

          <template v-if="canReply">
            <el-divider content-position="left">
              提交答复
            </el-divider>
            <el-input
              v-model="replyText"
              type="textarea"
              :rows="6"
              maxlength="5000"
              show-word-limit
              placeholder="请输入对学生问题的解答..."
            />
          </template>
        </div>
      </template>

      <template #footer>
        <el-button @click="dialogVisible = false">
          关闭
        </el-button>
        <el-button
          v-if="canReply"
          type="primary"
          :loading="submitting"
          @click="submitReply"
        >
          提交答复
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.qa-ticket-page {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.qa-ticket-toolbar {
  display: flex;
  align-items: center;
  gap: 14px;
  margin-bottom: 16px;
}

.qa-ticket-toolbar__label {
  color: var(--sc-text-secondary);
  font-size: 13px;
}

.qa-ticket-pagination {
  display: flex;
  justify-content: flex-end;
  margin-top: 18px;
}

.qa-ticket-detail {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.qa-ticket-detail__meta {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 12px;
  color: var(--sc-text-secondary);
  font-size: 13px;
}

.qa-ticket-question,
.qa-ticket-reply {
  padding: 14px;
  border-radius: 8px;
  background: var(--sc-bg);
  color: var(--sc-text);
  line-height: 1.8;
  white-space: pre-wrap;
  overflow-wrap: anywhere;
}
</style>
