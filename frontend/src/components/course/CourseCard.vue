<script setup lang="ts">
import { computed } from 'vue'

import { BookOpen, CalendarClock, ChevronRight } from '@lucide/vue'

import type { Course } from '@/api/course'
import { useCourseStore } from '@/stores/course'

type Tone = 'blue' | 'green' | 'purple' | 'orange' | 'pink'

const props = withDefaults(
  defineProps<{
    course: Course
    ddlCount?: number | null
    compact?: boolean
    tone?: Tone
  }>(),
  {
    ddlCount: null,
    compact: false,
    tone: 'blue',
  },
)

const courseStore = useCourseStore()

const courseStatus = computed(() =>
  props.course.status === 1 ? '正常' : '已停用',
)

function selectCourse(): void {
  courseStore.select(String(props.course.id))
}
</script>

<template>
  <article
    class="course-card"
    :class="[`course-card--${tone}`, { 'course-card--compact': compact }]"
  >
    <router-link
      class="course-card__body"
      :to="{ name: 'knowledge' }"
      @click="selectCourse"
    >
      <div class="course-card__top">
        <span class="course-card__icon">
          <BookOpen :size="19" />
        </span>

        <span class="course-card__title">
          <strong>{{ course.name }}</strong>
          <small v-if="!compact && course.description">
            {{ course.description }}
          </small>
        </span>

        <span class="course-status">
          {{ courseStatus }}
        </span>
      </div>
    </router-link>

    <div class="course-card__foot">
      <span>
        <CalendarClock :size="13" />
        DDL {{ ddlCount === null ? '—' : ddlCount }}
      </span>

      <router-link
        class="course-card__more"
        :to="{ name: 'knowledge' }"
        @click="selectCourse"
      >
        查看
        <ChevronRight :size="13" />
      </router-link>

      <slot name="actions" />
    </div>
  </article>
</template>

<style scoped>
.course-card {
  display: flex;
  flex-direction: column;
  gap: 10px;
  min-width: 0;
  padding: 14px;
  border: 1px solid var(--sc-border);
  border-radius: var(--sc-radius-card);
  background: var(--sc-surface);
  color: inherit;
}

.course-card__body {
  color: inherit;
  text-decoration: none;
}

.course-card__top {
  display: flex;
  align-items: center;
  gap: 12px;
  min-width: 0;
}

.course-card__icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 36px;
  height: 36px;
  flex-shrink: 0;
  border-radius: 10px;
  background: var(--sc-surface-muted);
}

.course-card__title {
  display: flex;
  flex: 1;
  flex-direction: column;
  min-width: 0;
  gap: 4px;
}

.course-card__title strong {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.course-card__title small {
  overflow: hidden;
  color: var(--sc-text-secondary);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.course-status {
  color: var(--sc-text-secondary);
  font-size: 12px;
  flex-shrink: 0;
}

.course-card__foot {
  display: flex;
  align-items: center;
  gap: 12px;
  padding-top: 10px;
  border-top: 1px solid var(--sc-border);
  color: var(--sc-text-secondary);
  font-size: 12px;
}

.course-card__foot span {
  display: inline-flex;
  align-items: center;
  gap: 4px;
}

.course-card__more {
  display: inline-flex;
  align-items: center;
  gap: 2px;
  margin-left: auto;
  color: var(--sc-primary);
  text-decoration: none;
}
</style>
