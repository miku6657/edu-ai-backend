<script setup lang="ts">
import { computed } from 'vue'
import { ChevronRight, BookOpen } from '@lucide/vue'

import type { Course } from '@/api/course'
import BaseCard from '@/components/ui/BaseCard.vue'
import EmptyState from '@/components/ui/EmptyState.vue'
import type { ModuleState } from '@/views/home/use-home-data'

const props = defineProps<{
  state: ModuleState<Course[]>
  ddlCounts: Record<string, number> | null
  authenticated: boolean
}>()

defineEmits<{ retry: [] }>()

const showCourses = computed(() => props.state.data.slice(0, 4))

function ddlCountFor(courseId: number): number | null {
  return props.ddlCounts === null
    ? null
    : (props.ddlCounts[String(courseId)] ?? 0)
}
</script>

<template>
  <BaseCard title="我的课程" subtitle="管理课程与学习资料">
    <template #actions>
      <router-link class="card-link" :to="{ name: 'courses' }">
        查看全部
        <ChevronRight :size="14" :stroke-width="1.9" />
      </router-link>
    </template>

    <div
      v-if="state.status === 'loading'"
      class="course-skeleton"
      aria-hidden="true"
    >
      <div v-for="index in 3" :key="index" class="course-skeleton__row">
        <span class="course-skeleton__icon"></span>
        <span class="course-skeleton__text"></span>
      </div>
    </div>

    <div v-else-if="state.status === 'error'" class="module-error">
      <p>{{ state.error }}</p>
      <el-button size="small" @click="$emit('retry')">重试</el-button>
    </div>

    <EmptyState
      v-else-if="state.status === 'empty'"
      image="/illustrations/scene-study-desk.png"
      :title="authenticated ? '还没有课程' : '登录后查看课程'"
      :description="
        authenticated
          ? '创建课程并上传课件，开始使用 AI 学习助手。'
          : '登录后即可创建课程和管理学习资料。'
      "
      compact
    >
      <template #action>
        <router-link
          class="card-link"
          :to="{ name: authenticated ? 'courses' : 'login' }"
        >
          {{ authenticated ? '创建课程' : '立即登录' }}
        </router-link>
      </template>
    </EmptyState>

    <div v-else class="course-list">
      <router-link
        v-for="course in showCourses"
        :key="course.id"
        :to="{ name: 'courses' }"
        class="course-item"
      >
        <div class="course-item__icon">
          <BookOpen :size="19" />
        </div>

        <div class="course-item__info">
          <strong>{{ course.name }}</strong>
          <span>{{ course.description || '暂无课程描述' }}</span>
        </div>

        <div class="course-item__meta">
          <span class="course-status">
            {{ course.status === 1 ? '正常' : '已停用' }}
          </span>
          <span class="course-ddl">
            DDL {{ ddlCountFor(course.id) ?? '—' }}
          </span>
        </div>
      </router-link>
    </div>
  </BaseCard>
</template>

<style scoped>
.card-link {
  display: inline-flex;
  align-items: center;
  gap: 2px;
  color: var(--sc-primary);
  font-size: var(--sc-font-caption);
  text-decoration: none;
}

.card-link:hover {
  color: var(--sc-primary-strong);
}

.course-list {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.course-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px 8px;
  border-radius: 10px;
  color: inherit;
  text-decoration: none;
  transition: background 0.2s;
}

.course-item:hover {
  background: var(--sc-surface-hover);
}

.course-item__icon {
  display: grid;
  flex: none;
  place-items: center;
  width: 38px;
  height: 38px;
  border-radius: 10px;
  color: var(--sc-primary);
  background: var(--sc-primary-soft);
}

.course-item__info {
  display: flex;
  flex: 1;
  flex-direction: column;
  gap: 4px;
  min-width: 0;
}

.course-item__info strong {
  overflow: hidden;
  color: var(--sc-text);
  font-size: var(--sc-font-body);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.course-item__info span {
  overflow: hidden;
  color: var(--sc-text-placeholder);
  font-size: var(--sc-font-hint);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.course-item__meta {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 4px;
  flex: none;
}

.course-status,
.course-ddl {
  color: var(--sc-text-secondary);
  font-size: var(--sc-font-hint);
}

.course-status {
  color: var(--sc-success);
}

.module-error {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 14px 4px;
  color: var(--sc-text-secondary);
  font-size: var(--sc-font-caption);
}

.course-skeleton {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.course-skeleton__row {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 10px 8px;
}

.course-skeleton__icon {
  width: 38px;
  height: 38px;
  border-radius: 10px;
  background: var(--sc-surface-muted);
}

.course-skeleton__text {
  flex: 1;
  height: 14px;
  border-radius: 7px;
  background: var(--sc-surface-muted);
}
</style>
