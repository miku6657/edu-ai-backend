import { computed, ref } from 'vue'

import { defineStore } from 'pinia'

import type { BigIntId } from '@/api/types'
import type { UserRole } from '@/api/auth'
import { useCourseStore } from '@/stores/course'
import { browserStorage } from '@/utils/browser-storage'

export interface AuthUser {
  user_id: BigIntId
  username: string
  nickname: string
  role: UserRole
}

export interface AuthSession {
  token: string
  user: AuthUser | null
}

const STORAGE_KEY = 'edu05.auth.session'

export const useAuthStore = defineStore('auth', () => {
  const restored = browserStorage.read<AuthSession>(STORAGE_KEY)

  const token = ref<string | null>(restored?.token ?? null)
  const user = ref<AuthUser | null>(restored?.user ?? null)

  const isAuthenticated = computed(() => Boolean(token.value))

  const displayName = computed(
    () => user.value?.nickname || user.value?.username || '未登录',
  )

  const role = computed(() => user.value?.role ?? null)

  const isAdmin = computed(() => role.value === 'ADMIN')
  const isUser = computed(() => role.value === 'USER')

  function setSession(session: AuthSession): void {
    token.value = session.token
    user.value = session.user
    browserStorage.write(STORAGE_KEY, session)
  }

  function clearSession(): void {
    token.value = null
    user.value = null
    browserStorage.remove(STORAGE_KEY)
    useCourseStore().clear()
  }

  function getToken(): string | null {
    return token.value
  }

  return {
    token,
    user,
    role,
    isAuthenticated,
    displayName,
    isAdmin,
    isUser,
    setSession,
    clearSession,
    getToken,
  }
})
