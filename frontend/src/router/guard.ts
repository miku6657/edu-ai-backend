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

    if (to.name === 'settings' && !auth.isAdmin) {
      return { name: 'home' }
    }

    return true
  })
}
