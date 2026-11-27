<script setup lang="ts">
import { computed } from 'vue'

import { useRoute } from 'vue-router'

import AppSidebar from '@/components/layout/AppSidebar.vue'
import AppTopbar from '@/components/layout/AppTopbar.vue'
import { useShellCounters } from '@/composables/use-shell-counters'
import { useUiStore } from '@/stores/ui'
import NotificationCenter from '@/views/notifications/NotificationCenter.vue'

/**
 * 工作台外壳（P1）。
 *
 * 布局铁律（Prompt §3）：
 * * `.app-shell` 固定 `100dvh`，整页不滚动；
 * * 侧栏固定宽度、内部独立滚动；
 * * 只有 `.app-content` 滚动，顶栏保持可见。
 *
 * `<1024px` 侧栏由 `AppSidebar` 自己折叠成图标栏；`<640px` 隐藏静态侧栏，
 * 由顶栏菜单按钮打开抽屉（仍复用同一个 `AppSidebar`）。
 */
const PAGE_META: Record<string, { title: string; subtitle?: string }> = {
  home: { title: '首页', subtitle: '连接课程知识库、DDL 与 AI 助手' },
  rag: { title: 'AI 学习', subtitle: '基于课程知识库的智能问答与学习规划' },
  'qa-tickets': { title: '课程答疑', subtitle: '学生问题提交、教师人工答疑与处理跟踪' },
  'teacher-questions': { title: '问答分析', subtitle: '分析学生提问、高频问题与知识点薄弱环节' },
  knowledge: { title: '知识库', subtitle: '上传课件并跟踪真实解析状态' },
  courses: { title: '我的课程', subtitle: '管理课程空间与 Embedding 绑定' },
  ddl: { title: 'DDL 任务', subtitle: '通知提取、待确认收件箱与任务闭环' },
  exercises: { title: '练习管理', subtitle: '教师手动组织课程练习与题目' },
  'my-exercises': {
    title: '我的练习',
    subtitle: '查看课程练习与个人参与情况',
  },
  'my-mistakes': {
    title: '错题回顾',
    subtitle: '按照课程知识点归集错题，巩固薄弱知识',
  },
  'student-exercise-detail': {
    title: '练习详情',
    subtitle: '完成课程练习，检验知识点掌握程度',
  },
  'student-exercise-result': {
    title: '练习结果',
    subtitle: '查看答题成绩、错题和答案解析',
  },
  'exercise-reviews': {
    title: '练习批改',
    subtitle: '查看学生提交的简答题，完成评分与反馈',
  },
  settings: { title: '设置', subtitle: '模型配置、偏好与账号安全' },
  guide: { title: '使用指南', subtitle: '功能说明、模型配置与常见问题' },
  login: { title: '登录', subtitle: '登录后开始使用 ScholarClaw' },
}

const route = useRoute()
const ui = useUiStore()
const { ddlPendingCount } = useShellCounters()

const pageMeta = computed(() => {
  const key = String(route.name ?? '')
  const fallbackTitle = typeof route.meta.title === 'string' ? route.meta.title : 'ScholarClaw'
  return PAGE_META[key] ?? { title: fallbackTitle }
})
</script>

<template>
  <div class="app-shell">
    <AppSidebar
      class="app-shell__sidebar"
      :unread-count="ui.unreadNotificationCount"
      :ddl-pending-count="ddlPendingCount"
    />

    <div class="app-main">
      <AppTopbar :title="pageMeta.title" :subtitle="pageMeta.subtitle" />
      <main class="app-content">
        <div class="sc-page">
          <router-view />
        </div>
      </main>
    </div>

    <el-drawer
      v-model="ui.mobileSidebarOpen"
      class="app-mobile-drawer"
      direction="ltr"
      size="264px"
      :with-header="false"
    >
      <AppSidebar
        expanded
        :unread-count="ui.unreadNotificationCount"
        :ddl-pending-count="ddlPendingCount"
        @navigate="ui.closeMobileSidebar()"
      />
    </el-drawer>

    <NotificationCenter />
  </div>
</template>

<style scoped>
.app-shell {
  display: flex;
  height: 100dvh;
  overflow: hidden;
  background: var(--sc-bg);
}

.app-shell__sidebar {
  flex: none;
}

.app-main {
  display: flex;
  flex: 1;
  flex-direction: column;
  min-width: 0;
}

.app-content {
  flex: 1;
  padding: 20px var(--sc-page-pad) 32px;
  overflow-x: hidden;
  overflow-y: auto;
}

:global(.app-mobile-drawer .el-drawer__body) {
  padding: 0;
}

@media (max-width: 639px) {
  .app-shell__sidebar {
    display: none;
  }

  .app-content {
    padding: 14px 14px 24px;
  }
}
</style>
