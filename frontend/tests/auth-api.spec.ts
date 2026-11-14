import { afterEach, beforeEach, describe, expect, it, vi, type Mock } from 'vitest'

import { getCurrentUser, login, logout, register, toAuthSession } from '@/api/auth'
import { resetApiClient } from '@/api/client'

function jsonResponse(body: unknown): Response {
  return new Response(JSON.stringify(body), {
    status: 200,
    headers: { 'Content-Type': 'application/json' },
  })
}

function result<T>(data: T): Record<string, unknown> {
  return { code: 200, message: '操作成功', data }
}

function captured(fetchMock: Mock, index = 0): { url: string; method?: string; body?: string } {
  const init = fetchMock.mock.calls[index]?.[1] as RequestInit | undefined
  return {
    url: String(fetchMock.mock.calls[index]?.[0]),
    method: init?.method,
    body: typeof init?.body === 'string' ? init.body : undefined,
  }
}

const AUTH_RESPONSE = {
  token: 'jwt-token-value',
  tokenType: 'Bearer',
  userId: 10001,
  username: 'zhangsan_cs',
  role: 'STUDENT' as const,
}

describe('auth api', () => {
  beforeEach(() => {
    resetApiClient()
  })

  afterEach(() => {
    vi.unstubAllGlobals()
    resetApiClient()
  })

  it('登录使用 POST /api/auth/login 并解析 Java Result', async () => {
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse(result(AUTH_RESPONSE)))
    vi.stubGlobal('fetch', fetchMock)

    const response = await login({ username: 'zhangsan_cs', password: 'Passw0rd!' })

    expect(captured(fetchMock)).toEqual({
      url: '/api/auth/login',
      method: 'POST',
      body: JSON.stringify({ username: 'zhangsan_cs', password: 'Passw0rd!' }),
    })
    expect(response).toEqual(AUTH_RESPONSE)
  })

  it('获取当前用户使用 GET /api/auth/me', async () => {
    const currentUser = {
      id: 10001,
      username: 'zhangsan_cs',
      nickname: '张三',
      role: 'STUDENT',
    }
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse(result(currentUser)))
    vi.stubGlobal('fetch', fetchMock)

    await expect(getCurrentUser()).resolves.toEqual(currentUser)

    expect(captured(fetchMock).url).toBe('/api/auth/me')
    expect(captured(fetchMock).method).toBe('GET')
  })

  it('注册暂未开放且不会发起请求', async () => {
    const fetchMock = vi.fn()
    vi.stubGlobal('fetch', fetchMock)

    await expect(register({ username: 'zhangsan_cs', password: 'Passw0rd!' })).rejects.toMatchObject({
      code: 'CLIENT_MALFORMED_RESPONSE',
      message: '注册功能暂未开放，请先使用已有账号登录。',
    })
    expect(fetchMock).not.toHaveBeenCalled()
  })

  it('登出暂时仅由前端清理会话，不发起请求', async () => {
    const fetchMock = vi.fn()
    vi.stubGlobal('fetch', fetchMock)

    await expect(logout()).resolves.toBeUndefined()

    expect(fetchMock).not.toHaveBeenCalled()
  })

  it('toAuthSession 将 Java 登录响应转成含角色的会话', () => {
    expect(toAuthSession(AUTH_RESPONSE)).toEqual({
      token: 'jwt-token-value',
      user: {
        user_id: '10001',
        username: 'zhangsan_cs',
        nickname: 'zhangsan_cs',
        role: 'STUDENT',
      },
    })
  })
})
