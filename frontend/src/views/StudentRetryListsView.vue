<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'

import { RotateCcw } from '@lucide/vue'

import { listStudentRetryLists } from '@/api/exercises'
import type { StudentRetryList } from '@/api/exercises'
import BaseCard from '@/components/ui/BaseCard.vue'
import EmptyState from '@/components/ui/EmptyState.vue'
import { notifyError } from '@/utils/notify'

defineOptions({ name: 'StudentRetryListsView' })

const router = useRouter()

const lists = ref<StudentRetryList[]>([])
const loading = ref(false)

const pendingCount = computed(
  () => lists.value.filter(item => item.status === 'PENDING').length,
)

const completedCount = computed(
  () => lists.value.filter(item => item.status === 'COMPLETED').length,
)

const totalItems = computed(
  () => lists.value.reduce((total, item) => total + item.itemCount, 0),
)

onMounted(() => {
  void loadLists()
})

async function loadLists(): Promise<void> {
  loading.value = true

  try {
    lists.value = await listStudentRetryLists()
  } catch (error) {
    notifyError(error)
  } finally {
    loading.value = false
  }
}

function statusLabel(status: string): string {
  return status === 'COMPLETED' ? '已完成' : '待重练'
}

function statusType(status: string): 'success' | 'warning' {
  return status === 'COMPLETED' ? 'success' : 'warning'
}

function latestStatusLabel(status: string | null): string {
  switch (status) {
    case 'IN_PROGRESS':
      return '进行中'
    case 'PENDING_SELF_REVIEW':
      return '待自我核对'
    case 'GRADED':
      return '已完成判分'
    default:
      return '未开始'
  }
}

function formatTime(value: string): string {
  if (!value) return '-'

  const date = new Date(value.replace(' ', 'T'))
  if (Number.isNaN(date.getTime())) return value

  return date.toLocaleString('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
  })
}

function openList(item: StudentRetryList): void {
  void router.push({
    name: 'student-retry-detail',
    params: {
      listId: item.id,
    },
  })
}

function goToProgress(): void {
  void router.push({
    name: 'my-retry-progress',
  })
}
</script>

<template>
  <div class="student-retry-lists-page">
    <BaseCard class="retry-hero">
      <div class="retry-hero__content">
        <div>
          <div class="retry-eyebrow">MISTAKE RETRY</div>
          <h2>错题重练</h2>
          <p>
            把薄弱知识点整理成重练清单，
            通过重复作答巩固易错题目。
          </p>
        </div>

        <div class="retry-hero__icon">
          <RotateCcw :size="32" />
        </div>
      </div>
    </BaseCard>

    <div class="retry-stats">
      <BaseCard>
        <div class="stat-card">
          <span>重练清单</span>
          <strong>{{ lists.length }}</strong>
          <small>全部清单数量</small>
        </div>
      </BaseCard>

      <BaseCard>
        <div class="stat-card">
          <span>待重练</span>
          <strong>{{ pendingCount }}</strong>
          <small>尚未完成的清单</small>
        </div>
      </BaseCard>

      <BaseCard>
        <div class="stat-card">
          <span>已完成</span>
          <strong>{{ completedCount }}</strong>
          <small>已完成判分的清单</small>
        </div>
      </BaseCard>

      <BaseCard>
        <div class="stat-card">
          <span>错题数量</span>
          <strong>{{ totalItems }}</strong>
          <small>所有清单的题目数</small>
        </div>
      </BaseCard>
    </div>

    <BaseCard>
      <template #header>
        <div class="retry-list__header">
          <div>
            <h3>我的重练清单</h3>
            <p>选择清单进入重练，重新完成错题并巩固薄弱知识。</p>
          </div>
          <div class="retry-list__actions">
            <el-button
              @click="goToProgress"
            >
              学习进步
            </el-button>

            <el-button
              :loading="loading"
              @click="loadLists"
            >
              刷新
            </el-button>
          </div>
        </div>
      </template>

      <el-skeleton
        v-if="loading && lists.length === 0"
        :rows="4"
        animated
      />
      <EmptyState
        v-else-if="lists.length === 0"
        title="还没有重练清单"
        description="在「错题回顾」中选择错题生成重练清单后，会在这里展示。"
      />
      <div v-else class="retry-list">
        <article
          v-for="item in lists"
          :key="item.id"
          class="retry-item"
          @click="openList(item)"
        >
          <div class="retry-item__main">
            <div class="retry-item__title">
              <h4>{{ item.title }}</h4>
              <el-tag :type="statusType(item.status)" size="small">
                {{ statusLabel(item.status) }}
              </el-tag>
            </div>

            <div class="retry-item__meta">
              <span>{{ item.itemCount }} 道错题</span>
              <span>已重练 {{ item.attemptCount }} 次</span>
              <span>{{ latestStatusLabel(item.latestAttemptStatus) }}</span>
              <span>{{ formatTime(item.createdAt) }}</span>
            </div>
          </div>

          <div class="retry-item__action">
            <el-button
              type="primary"
              plain
              @click.stop="openList(item)"
            >
              {{ item.status === 'COMPLETED' ? '查看详情' : '开始重练' }}
            </el-button>
          </div>
        </article>
      </div>

      <div
        v-if="lists.length > 0"
        class="retry-list__footer"
      >
        共 {{ lists.length }} 份清单
      </div>
    </BaseCard>
  </div>
</template>

<style scoped>
.student-retry-lists-page {
  display: flex;
  flex-direction: column;
  gap: 16px;
  min-width: 0;
}

.retry-hero__content {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 20px;
}

.retry-eyebrow {
  margin-bottom: 8px;
  color: var(--sc-primary);
  font-size: 10px;
  font-weight: 700;
  letter-spacing: 2px;
}

.retry-hero h2 {
  margin: 0 0 8px;
  color: var(--sc-text);
  font-size: 22px;
  font-weight: 650;
}

.retry-hero p {
  margin: 0;
  color: var(--sc-text-secondary);
  font-size: 13px;
  line-height: 1.7;
}

.retry-hero__icon {
  display: grid;
  flex: none;
  place-items: center;
  width: 64px;
  height: 64px;
  border-radius: 18px;
  color: var(--sc-primary);
  background: var(--sc-primary-soft);
}

.retry-stats {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 14px;
}

.stat-card {
  display: flex;
  flex-direction: column;
  gap: 7px;
}

.stat-card span {
  color: var(--sc-text-secondary);
  font-size: 12px;
}

.stat-card strong {
  color: var(--sc-text);
  font-size: 27px;
  font-weight: 650;
  line-height: 1.2;
}

.stat-card small {
  color: var(--sc-text-placeholder);
  font-size: 11px;
}

.retry-list__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.retry-list__header h3 {
  margin: 0 0 5px;
  color: var(--sc-text);
  font-size: 15px;
  font-weight: 650;
}

.retry-list__header p {
  margin: 0;
  color: var(--sc-text-secondary);
  font-size: 12px;
}

.retry-list__actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

.retry-list {
  display: flex;
  flex-direction: column;
}

.retry-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 18px 4px;
  border-top: 1px solid var(--sc-border);
  cursor: pointer;
}

.retry-item__main {
  flex: 1;
  min-width: 0;
}

.retry-item__action {
  flex: none;
}

.retry-item__title {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 10px;
}

.retry-item__title h4 {
  margin: 0;
  color: var(--sc-text);
  font-size: 14px;
  font-weight: 600;
}

.retry-item__meta {
  display: flex;
  flex-wrap: wrap;
  gap: 18px;
  margin-top: 12px;
  color: var(--sc-text-placeholder);
  font-size: 11px;
}

.retry-list__footer {
  padding-top: 14px;
  border-top: 1px solid var(--sc-border);
  color: var(--sc-text-placeholder);
  font-size: 11px;
  text-align: right;
}

@media (max-width: 800px) {
  .retry-stats {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 700px) {
  .retry-hero__icon {
    display: none;
  }

  .retry-item {
    align-items: flex-start;
  }

  .retry-item__action {
    width: 100%;
  }

  .retry-item__action .el-button {
    width: 100%;
  }

  .retry-item__meta {
    gap: 10px;
  }
}
</style>
