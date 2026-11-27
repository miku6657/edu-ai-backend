import type { Router } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

export const LOGIN_ROUTE_NAME = 'login'

export function installAuthGuard(target: Router): void {
  target.beforeEach((to) => {
    const auth = useAuthStore()

    if (to.meta.guestOnly === true && auth.isAuthenticated) {
      return { name: 'home' }
    }

    if (to.meta.requiresAuth !== true) {
      return true
    }

    if (!auth.isAuthenticated) {
      return {
        name: LOGIN_ROUTE_NAME,
        query: { redirect: to.fullPath },
      }
    }

    const studentRoutes = [
      'my-exercises',
      'student-exercise-detail',
      'student-exercise-result',
      'student-learning-analysis',
      'my-mistakes',
      'my-retry-lists',
      'my-retry-progress',
      'student-retry-detail',
      'student-retry-result',
    ]

    if (studentRoutes.includes(String(to.name)) && !auth.isStudent) {
      return { name: 'home' }
    }

    if (
      to.name === 'exercise-reviews'
      && !auth.isTeacher
      && !auth.isAdmin
    ) {
      return { name: 'home' }
    }

    if (to.name === 'settings' && !auth.isAdmin) {
      return { name: 'home' }
    }

    return true
  })
}
