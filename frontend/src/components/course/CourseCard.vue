<script setup lang="ts">
import { computed } from 'vue'
import type { Component } from 'vue'

import {
  BookOpen,
  CalendarClock,
  ChevronRight,
  Code2,
  Cpu,
  Database,
  FileText,
  Network,
} from '@lucide/vue'

import type { Course } from '@/api/course'
import TagBadge from '@/components/ui/TagBadge.vue'
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

const TONE_ICONS: Record<Tone, Component> = {
  blue: Code2,
  green: Database,
  purple: Network,
  orange: Cpu,
  pink: BookOpen,
}

const courseStore = useCourseStore()

const icon = computed(() => TONE_ICONS[props.tone])

const courseStatus = computed(() => {
  return props.course.status === 1
    ? '正常运行'
    : '已停用'
})

const statusTone = computed(() => {
  return props.course.status === 1
    ? 'success' as const
    : 'neutral' as const
})

function selectCourse(): void {
  courseStore.select(String(props.course.id))
}
</script>

<template>
  <article
    class="course-card"
    :class="[
      `course-card--${tone}`,
      { 'course-card--compact': compact },
    ]"
  >
    <router-link
      class="course-card__body"
      :to="{ name: 'knowledge' }"
      @click="selectCourse"
    >
      <div class="course-card__top">
        <span class="course-card__icon">
          <component
            :is="icon"
            :size="19"
            :stroke-width="1.8"
          />
        </span>

        <span class="course-card__title">
          <strong>{{ course.name }}</strong>

          <small v-if="!compact && course.description">
            {{ course.description }}
          </small>
        </span>

        <TagBadge
          :label="courseStatus"
          :tone="statusTone"
        />
      </div>

      <div class="course-card__description" v-if="!compact">
        <span>
          {{ course.status === 1
            ? '可以上传课件并使用 AI 知识库'
            : '课程已停用，暂不能上传课件'
          }}
        </span>
      </div>
    </router-link>

    <div class="course-card__foot">
      <span>
        <FileText
          class="course-card__foot-icon"
          :size="13"
        />
        课程 ID {{ course.id }}
      </span>

      <span>
        <CalendarClock
          class="course-card__foot-icon"
          :size="13"
        />
        DDL
        {{ ddlCount === null ? '—' : ddlCount }}
      </span>

      <router-link
        class="course-card__more"
        :to="{ name: 'knowledge' }"
        @click="selectCourse"
      >
        查看
        <ChevronRight :size="13" :stroke-width="2" />
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
  transition: border-color 0.18s ease, box-shadow 0.18s ease, transform 0.18s ease;
}

.course-card:hover,
.course-card:focus-within {
  border-color: var(--sc-primary);
  box-shadow: var(--sc-shadow);
  transform: translateY(-1px);
}

.course-card__body {
  display: flex;
  flex-direction: column;
  gap: 10px;
  min-width: 0;
  color: inherit;
  text-decoration: none;
}

.course-card__body:focus-visible {
  outline: 2px solid var(--sc-primary);
  outline-offset: 4px;
  border-radius: 10px;
}

.course-card__top {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  min-width: 0;
}

.course-card__icon {
  display: grid;
  flex: none;
  place-items: center;
  width: 38px;
  height: 38px;
  border-radius: 11px;
}

.course-card--blue .course-card__icon {
  color: var(--sc-primary);
  background: var(--sc-primary-soft);
}

.course-card--green .course-card__icon {
  color: var(--sc-success);
  background: var(--sc-success-soft);
}

.course-card--purple .course-card__icon {
  color: var(--sc-accent);
  background: var(--sc-accent-soft);
}

.course-card--orange .course-card__icon {
  color: var(--sc-warning);
  background: var(--sc-warning-soft);
}

.course-card--pink .course-card__icon {
  color: #db2777;
  background: #fdf2f8;
}

.course-card__title {
  display: flex;
  flex: 1;
  flex-direction: column;
  gap: 3px;
  min-width: 0;
}

.course-card__title strong {
  display: -webkit-box;
  overflow: hidden;
  color: var(--sc-text);
  font-size: 13px;
  font-weight: 600;
  line-height: 1.4;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
}

.course-card__title small {
  display: -webkit-box;
  overflow: hidden;
  color: var(--sc-text-placeholder);
  font-size: var(--sc-font-hint);
  line-height: 1.5;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
}

.course-card__description {
  color: var(--sc-text-secondary);
  font-size: var(--sc-font-hint);
  line-height: 1.5;
}

.course-card__foot {
  display: flex;
  align-items: center;
  gap: 14px;
  padding-top: 9px;
  border-top: 1px solid var(--sc-border);
  color: var(--sc-text-secondary);
  font-size: var(--sc-font-hint);
}

.course-card__foot span {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  white-space: nowrap;
}

.course-card__foot-icon {
  flex: none;
  color: var(--sc-text-placeholder);
}

.course-card__more {
  display: inline-flex;
  align-items: center;
  gap: 1px;
  margin-left: auto;
  color: var(--sc-primary);
  text-decoration: none;
}

.course-card__more:hover {
  color: var(--sc-primary-strong);
}

.course-card--compact {
  padding: 10px 12px;
  border: 0;
  background: transparent;
}

.course-card--compact:hover {
  background: var(--sc-surface-hover);
  box-shadow: none;
  transform: none;
}

.course-card--compact .course-card__icon {
  width: 34px;
  height: 34px;
  border-radius: 10px;
}

.course-card--compact .course-card__title strong {
  display: block;
  -webkit-line-clamp: unset;
  white-space: nowrap;
  text-overflow: ellipsis;
}

.course-card--compact .course-card__foot {
  padding-top: 7px;
}
</style>
