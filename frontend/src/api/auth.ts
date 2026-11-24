import { apiRequest } from '@/api/client'
import type { BigIntId } from '@/api/types'

/** EDU-05 全局用户角色 */
export type UserRole =
  | 'ADMIN'
  | 'TEACHER'
  | 'STUDENT'

export interface LoginRequest {
  username: string
  password: string
}

export interface AuthResponse {
  token: string
  tokenType: string
  userId: BigIntId
  username: string
  role: UserRole
}

export interface CurrentUser {
  id: BigIntId
  username: string
  nickname: string
  role: UserRole
}

export interface RegisterRequest {
  username: string
  password: string
}

export interface AuthSession {
  token: string
  user: {
    user_id: BigIntId
    username: string
    nickname: string
    role: UserRole
  }
}

export function login(
  request: LoginRequest,
): Promise<AuthResponse> {

  return apiRequest<AuthResponse>('/auth/login', {
    method: 'POST',
    body: request,
  })
}

export function getCurrentUser(): Promise<CurrentUser> {
  return apiRequest<CurrentUser>('/auth/me')
}

export function register(
  request: RegisterRequest,
): Promise<AuthResponse> {

  return apiRequest<AuthResponse>('/auth/register', {
    method: 'POST',
    body: request,
  })
}

export async function logout(): Promise<void> {
  return Promise.resolve()
}

export function toAuthSession(
  response: AuthResponse,
): AuthSession {

  if (!response.token) {
    throw new Error('认证失败：服务端没有返回 JWT Token')
  }

  if (!['ADMIN', 'TEACHER', 'STUDENT'].includes(response.role)) {
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
