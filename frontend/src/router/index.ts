import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'

import AppLayout from '@/layouts/AppLayout.vue'
import { installAuthGuard } from '@/router/guard'
import CoursesView from '@/views/CoursesView.vue'
import DdlView from '@/views/DdlView.vue'
import ExerciseReviewsView from '@/views/ExerciseReviewsView.vue'
import ExercisesView from '@/views/ExercisesView.vue'
import GuideView from '@/views/GuideView.vue'
import HomeView from '@/views/HomeView.vue'
import KnowledgeView from '@/views/KnowledgeView.vue'
import LoginView from '@/views/LoginView.vue'
import NotFoundView from '@/views/NotFoundView.vue'
import QaTicketsView from '@/views/QaTicketsView.vue'
import RagView from '@/views/RagView.vue'
import SettingsView from '@/views/SettingsView.vue'
import StudentExerciseDetailView from '@/views/StudentExerciseDetailView.vue'
import StudentExerciseResultView from '@/views/StudentExerciseResultView.vue'
import StudentExercisesView from '@/views/StudentExercisesView.vue'
import StudentMistakesView from '@/views/StudentMistakesView.vue'
import StudentRetryListsView from '@/views/StudentRetryListsView.vue'
import StudentRetryDetailView from '@/views/StudentRetryDetailView.vue'
import StudentRetryResultView from '@/views/StudentRetryResultView.vue'
import StudentRetryProgressView from '@/views/StudentRetryProgressView.vue'
import TeacherQuestionsView from '@/views/TeacherQuestionsView.vue'

/**
 * 路由表（`06` TASK-103 / TASK-205）。
 *
 * | 视图 | 落地任务 |
 * |---|---|
 * | 登录、注册与个人设置（BYOK） | TASK-205（已落地） |
 * | 课程知识库管理（左侧栏 + 文档抽屉） | TASK-404 |
 * | RAG 溯源问答主工作区（`/rag`） | TASK-605（已落地） |
 * | DDL 任务看板与待确认收件箱（`/ddl`） | TASK-705（已落地） |
 *
 * 需要登录的路由声明 `meta.requiresAuth = true`；登录页声明 `meta.guestOnly = true`，
 * 已登录用户访问时直接回到设置页，避免出现"已登录还能看到登录表单"的割裂状态。
 */

const routes: RouteRecordRaw[] = [
  {
    path: '/',
    component: AppLayout,
    children: [
      {
        path: '',
        name: 'home',
        component: HomeView,
        meta: { title: '工程骨架' },
      },
      {
        path: 'login',
        name: 'login',
        component: LoginView,
        meta: { title: '登录', guestOnly: true },
      },
      {
        path: 'courses',
        name: 'courses',
        component: CoursesView,
        meta: { title: '课程空间', requiresAuth: true },
      },
      {
        path: 'knowledge',
        name: 'knowledge',
        component: KnowledgeView,
        meta: { title: '课程知识库', requiresAuth: true },
      },
      {
        path: 'rag',
        name: 'rag',
        component: RagView,
        meta: { title: '课程问答', requiresAuth: true },
      },
      {
        path: 'qa-tickets',
        name: 'qa-tickets',
        component: QaTicketsView,
        meta: {
          title: '课程答疑',
          requiresAuth: true,
        },
      },
      {
        path: 'teacher-questions',
        name: 'teacher-questions',
        component: TeacherQuestionsView,
        meta: {
          title: '教师问答分析',
          requiresAuth: true,
        },
      },
      {
        path: 'ddl',
        name: 'ddl',
        component: DdlView,
        meta: { title: 'DDL 待办', requiresAuth: true },
      },
      {
        path: 'exercises',
        name: 'exercises',
        component: ExercisesView,
        meta: { title: '练习管理', requiresAuth: true },
      },
      {
        path: 'exercise-reviews',
        name: 'exercise-reviews',
        component: ExerciseReviewsView,
        meta: {
          title: '练习批改',
          requiresAuth: true,
        },
      },
      {
        path: 'my-exercises',
        name: 'my-exercises',
        component: StudentExercisesView,
        meta: {
          title: '我的练习',
          requiresAuth: true,
        },
      },
      {
        path: 'my-mistakes',
        name: 'my-mistakes',
        component: StudentMistakesView,
        meta: {
          title: '错题回顾',
          requiresAuth: true,
        },
      },
      {
        path: 'my-retry-lists',
        name: 'my-retry-lists',
        component: StudentRetryListsView,
        meta: {
          title: '错题重练',
          requiresAuth: true,
        },
      },
      {
        path: 'my-retry-progress',
        name: 'my-retry-progress',
        component: StudentRetryProgressView,
        meta: {
          title: '学习进步',
          requiresAuth: true,
        },
      },
      {
        path: 'my-retry-lists/:listId',
        name: 'student-retry-detail',
        component: StudentRetryDetailView,
        meta: {
          title: '重练答题',
          requiresAuth: true,
        },
      },
      {
        path: 'my-retry-attempts/:attemptId/result',
        name: 'student-retry-result',
        component: StudentRetryResultView,
        meta: {
          title: '重练结果',
          requiresAuth: true,
        },
      },
      {
        path: 'my-exercises/attempts/:attemptId/result',
        name: 'student-exercise-result',
        component: StudentExerciseResultView,
        meta: {
          title: '练习结果',
          requiresAuth: true,
        },
      },
      {
        path: 'my-exercises/:exerciseId',
        name: 'student-exercise-detail',
        component: StudentExerciseDetailView,
        meta: {
          title: '练习详情',
          requiresAuth: true,
        },
      },
      {
        path: 'settings',
        name: 'settings',
        component: SettingsView,
        meta: { title: '系统设置', requiresAuth: true },
      },
      {
        // 使用指南：不要求登录，未注册的人也能先看功能说明
        path: 'guide',
        name: 'guide',
        component: GuideView,
        meta: { title: '使用指南' },
      },
    ],
  },
  {
    path: '/:pathMatch(.*)*',
    name: 'not-found',
    component: NotFoundView,
    meta: { title: '页面不存在' },
  },
]

export const router = createRouter({
  history: createWebHistory(),
  routes,
})

installAuthGuard(router)
