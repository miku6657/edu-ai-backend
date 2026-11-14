import { apiRequest } from '@/api/client'
import type { BigIntId } from '@/api/types'

/** EDU-05 用户角色 */
export type UserRole = 'ADMIN' | 'USER'

/** 登录请求 */
export interface LoginRequest {
  username: string
  password: string
}

/** 登录、注册统一响应 */
export interface AuthResponse {
  token: string
  tokenType: string
  userId: BigIntId
  username: string
  role: UserRole
}

/** 当前用户信息 */
export interface CurrentUser {
  id: BigIntId
  username: string
  nickname: string
  role: UserRole
}

/** 注册请求 */
export interface RegisterRequest {
  username: string
  password: string
}

/** 前端保存的登录会话 */
export interface AuthSession {
  token: string
  user: {
    user_id: BigIntId
    username: string
    nickname: string
    role: UserRole
  }
}

/**
 * 登录接口
 * POST /api/auth/login
 */
export function login(request: LoginRequest): Promise<AuthResponse> {
  return apiRequest<AuthResponse>('/auth/login', {
    method: 'POST',
    body: request,
  })
}

/**
 * 获取当前登录用户
 * GET /api/auth/me
 */
export function getCurrentUser(): Promise<CurrentUser> {
  return apiRequest<CurrentUser>('/auth/me')
}

/** 注册并取得登录会话 */
export function register(request: RegisterRequest): Promise<AuthResponse> {
  return apiRequest<AuthResponse>('/auth/register', {
    method: 'POST',
    body: request,
  })
}

/**
 * 退出登录。
 *
 * 当前 EDU-05 使用无状态 JWT，
 * 后端暂未提供 Token 黑名单或注销接口。
 *
 * 因此暂时采用前端清除本地会话的方式退出。
 * 保留此函数是为了兼容 AppTopbar.vue。
 */
export async function logout(): Promise<void> {
  // 不调用不存在的后端注销接口。
  // 实际清理工作由 auth.clearSession() 完成。
  return Promise.resolve()
}

/**
 * 将 Java 登录响应转换为前端 Store 所需格式。
 */
export function toAuthSession(response: AuthResponse): AuthSession {
  if (!response.token) {
    throw new Error('认证失败：服务端没有返回 JWT Token')
  }

  if (!['ADMIN', 'USER'].includes(response.role)) {
    throw new Error('认证失败：服务端返回了未知用户角色')
  }

  return {
    token: response.token,
    user: {
      user_id: response.userId,
      username: response.username,
      nickname: response.username,
      role: response.role,
    },
  }
}
