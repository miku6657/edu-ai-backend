import { defineComponent } from 'vue'

import { createPinia, setActivePinia } from 'pinia'
import { createMemoryHistory, createRouter, type RouteRecordRaw, type Router } from 'vue-router'
import { beforeEach, describe, expect, it } from 'vitest'

import { installAuthGuard, LOGIN_ROUTE_NAME } from '@/router/guard'
import { useAuthStore } from '@/stores/auth'
import { browserStorage } from '@/utils/browser-storage'

const StubComponent = defineComponent({ template: '<div />' })

/**
 * 鉴权守卫测试（`06` TASK-103）。
 *
 * 骨架里的公开路由不触发守卫，因此这里用合成路由把守卫的行为锁死：
 * 未登录访问受保护路由 → 重定向到登录并带上 `redirect`；已登录 → 放行。
 * TASK-205 接入登录页与业务路由后，守卫即成为真实生效路径。
 */
function buildRouter(): Router {
  const routes: RouteRecordRaw[] = [
    { path: '/', name: 'home', component: StubComponent },
    { path: '/login', name: LOGIN_ROUTE_NAME, component: StubComponent, meta: { guestOnly: true } },
    { path: '/settings', name: 'settings', component: StubComponent, meta: { requiresAuth: true } },
    { path: '/my-exercises', name: 'my-exercises', component: StubComponent, meta: { requiresAuth: true } },
    { path: '/exercise-detail', name: 'student-exercise-detail', component: StubComponent, meta: { requiresAuth: true } },
    { path: '/exercise-result', name: 'student-exercise-result', component: StubComponent, meta: { requiresAuth: true } },
    { path: '/exercise-reviews', name: 'exercise-reviews', component: StubComponent, meta: { requiresAuth: true } },
    { path: '/my-mistakes', name: 'my-mistakes', component: StubComponent, meta: { requiresAuth: true } },
    { path: '/workspace', name: 'workspace', component: StubComponent, meta: { requiresAuth: true } },
  ]
  const router = createRouter({ history: createMemoryHistory(), routes })
  installAuthGuard(router)
  return router
}

describe('installAuthGuard', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    browserStorage.remove('edu05.auth.session')
  })

  it('公开路由无需登录即可访问', async () => {
    const router = buildRouter()

    await router.push('/')

    expect(router.currentRoute.value.name).toBe('home')
  })

  it('未登录访问受保护路由会重定向到登录页并保留原始目标', async () => {
    const router = buildRouter()

    await router.push('/workspace')

    expect(router.currentRoute.value.name).toBe(LOGIN_ROUTE_NAME)
    expect(router.currentRoute.value.query.redirect).toBe('/workspace')
  })

  it('已登录访问受保护路由直接放行', async () => {
    useAuthStore().setSession({ token: 'jwt-token-value', user: null })
    const router = buildRouter()

    await router.push('/workspace')

    expect(router.currentRoute.value.name).toBe('workspace')
  })

  it('登录后重定向参数可用于回跳（TASK-205 使用）', async () => {
    useAuthStore().setSession({ token: 'jwt-token-value', user: null })
    const router = buildRouter()

    await router.push({ name: LOGIN_ROUTE_NAME, query: { redirect: '/workspace' } })
    await router.push('/workspace')

    expect(router.currentRoute.value.fullPath).toBe('/workspace')
  })

  it('已登录访问登录页会回到首页，避免重复登录', async () => {
    useAuthStore().setSession({ token: 'jwt-token-value', user: null })
    const router = buildRouter()

    await router.push('/login')

    expect(router.currentRoute.value.name).toBe('home')
  })

  it('非管理员访问设置页会回到首页', async () => {
    useAuthStore().setSession({
      token: 'jwt-token-value',
      user: {
        user_id: '10001',
        username: 'student',
        nickname: '学生',
        role: 'STUDENT',
      },
    })
    const router = buildRouter()

    await router.push('/settings')

    expect(router.currentRoute.value.name).toBe('home')
  })

  it.each([
    ['/my-exercises', 'my-exercises'],
    ['/exercise-detail', 'student-exercise-detail'],
    ['/exercise-result', 'student-exercise-result'],
    ['/my-mistakes', 'my-mistakes'],
  ] as const)('学生可以访问 %s', async (path, routeName) => {
    useAuthStore().setSession({
      token: 'jwt-token-value',
      user: {
        user_id: '10001',
        username: 'student',
        nickname: '学生',
        role: 'STUDENT',
      },
    })
    const router = buildRouter()

    await router.push(path)

    expect(router.currentRoute.value.name).toBe(routeName)
  })

  it.each([
    ['TEACHER', '/my-exercises'],
    ['TEACHER', '/exercise-detail'],
    ['TEACHER', '/exercise-result'],
    ['TEACHER', '/my-mistakes'],
    ['ADMIN', '/my-exercises'],
    ['ADMIN', '/exercise-detail'],
    ['ADMIN', '/exercise-result'],
    ['ADMIN', '/my-mistakes'],
  ] as const)('%s 不能访问 %s', async (role, path) => {
    useAuthStore().setSession({
      token: 'jwt-token-value',
      user: {
        user_id: '10001',
        username: role.toLowerCase(),
        nickname: role,
        role,
      },
    })
    const router = buildRouter()

    await router.push(path)

    expect(router.currentRoute.value.name).toBe('home')
  })

  it('学生不能访问练习批改页', async () => {
    useAuthStore().setSession({
      token: 'jwt-token-value',
      user: {
        user_id: '10001',
        username: 'student',
        nickname: '学生',
        role: 'STUDENT',
      },
    })
    const router = buildRouter()

    await router.push('/exercise-reviews')

    expect(router.currentRoute.value.name).toBe('home')
  })

  it.each(['TEACHER', 'ADMIN'] as const)('%s 可以访问练习批改页', async (role) => {
    useAuthStore().setSession({
      token: 'jwt-token-value',
      user: {
        user_id: '10001',
        username: role.toLowerCase(),
        nickname: role,
        role,
      },
    })
    const router = buildRouter()

    await router.push('/exercise-reviews')

    expect(router.currentRoute.value.name).toBe('exercise-reviews')
  })

  it('管理员可以访问设置页', async () => {
    useAuthStore().setSession({
      token: 'jwt-token-value',
      user: {
        user_id: '10001',
        username: 'admin',
        nickname: '管理员',
        role: 'ADMIN',
      },
    })
    const router = buildRouter()

    await router.push('/settings')

    expect(router.currentRoute.value.name).toBe('settings')
  })

  it('未登录访问登录页正常放行', async () => {
    const router = buildRouter()

    await router.push('/login')

    expect(router.currentRoute.value.name).toBe(LOGIN_ROUTE_NAME)
  })
})
