import { ApiError, CLIENT_ERROR_CODES } from '@/api/error'
import type { ApiResult } from '@/api/types'
import { SUCCESS_CODE } from '@/api/types'

export type HttpMethod = 'GET' | 'POST' | 'PUT' | 'PATCH' | 'DELETE'

export interface ApiClientHooks {
  tokenProvider?: () => string | null
  unauthorizedHandler?: () => void
}

export interface ApiRequestOptions {
  method?: HttpMethod
  body?: unknown
  formData?: FormData
  signal?: AbortSignal
  extraHeaders?: Record<string, string>
}

let tokenProvider: () => string | null = () => null
let unauthorizedHandler: () => void = () => undefined

export function configureApiClient(hooks: ApiClientHooks): void {
  if (hooks.tokenProvider) {
    tokenProvider = hooks.tokenProvider
  }

  if (hooks.unauthorizedHandler) {
    unauthorizedHandler = hooks.unauthorizedHandler
  }
}

export function resetApiClient(): void {
  tokenProvider = () => null
  unauthorizedHandler = () => undefined
}

/** EDU-05 API 统一前缀 */
export function resolveApiBaseUrl(): string {
  const configured = import.meta.env.VITE_API_BASE_URL

  if (!configured || configured.trim() === '') {
    return '/api'
  }

  return configured.trim().replace(/\/+$/, '')
}

/** 普通 JSON 请求 */
export async function apiRequest<T>(
  path: string,
  options: ApiRequestOptions = {},
): Promise<T> {
  const method = options.method ?? 'GET'

  const headers: Record<string, string> = {
    Accept: 'application/json',
    ...options.extraHeaders,
  }

  const token = tokenProvider()

  if (token) {
    headers.Authorization = `Bearer ${token}`
  }

  let payload: string | FormData | undefined

  if (options.formData) {
    payload = options.formData
  } else if (options.body !== undefined) {
    headers['Content-Type'] = 'application/json'
    payload = JSON.stringify(options.body)
  }

  let response: Response

  try {
    response = await fetch(buildUrl(path), {
      method,
      headers,
      body: payload,
      signal: options.signal ?? null,
    })
  } catch (error) {
    throw new ApiError({
      code: CLIENT_ERROR_CODES.NETWORK,
      message: '无法连接到 EDU-05 服务端，请检查后端是否启动。',
      cause: error,
    })
  }

  if (response.status === 401) {
    unauthorizedHandler()
  }

  const result = await parseEnvelope<T>(response)

  if (!response.ok || result.code !== SUCCESS_CODE) {
    throw new ApiError({
      code: String(result.code),
      message: result.message || '请求失败',
      httpStatus: response.status,
    })
  }

  return result.data as T
}

/** SSE 流式请求 */
export async function apiStream(
  path: string,
  options: ApiRequestOptions = {},
): Promise<Response> {
  const headers: Record<string, string> = {
    Accept: 'text/event-stream',
    ...options.extraHeaders,
  }

  const token = tokenProvider()

  if (token) {
    headers.Authorization = `Bearer ${token}`
  }

  let payload: string | undefined

  if (options.body !== undefined) {
    headers['Content-Type'] = 'application/json'
    payload = JSON.stringify(options.body)
  }

  let response: Response

  try {
    response = await fetch(buildUrl(path), {
      method: options.method ?? 'POST',
      headers,
      body: payload,
      signal: options.signal ?? null,
    })
  } catch (error) {
    throw new ApiError({
      code: CLIENT_ERROR_CODES.NETWORK,
      message: '无法连接到 EDU-05 服务端。',
      cause: error,
    })
  }

  if (response.status === 401) {
    unauthorizedHandler()
  }

  if (!response.ok) {
    const result = await parseEnvelope<unknown>(response)

    throw new ApiError({
      code: String(result.code),
      message: result.message || '流式请求失败',
      httpStatus: response.status,
    })
  }

  const contentType = response.headers.get('Content-Type') ?? ''

  if (!contentType.toLowerCase().includes('text/event-stream')) {
    throw new ApiError({
      code: CLIENT_ERROR_CODES.MALFORMED_RESPONSE,
      message: '服务端没有返回 SSE 流式响应。',
      httpStatus: response.status,
    })
  }

  return response
}

/** 构造完整请求地址 */
function buildUrl(path: string): string {
  return `${resolveApiBaseUrl()}${path.startsWith('/') ? path : `/${path}`}`
}

/** 解析 Java Result<T> */
async function parseEnvelope<T>(
  response: Response,
): Promise<ApiResult<T>> {
  let parsed: unknown

  try {
    parsed = await response.json()
  } catch (error) {
    throw new ApiError({
      code: CLIENT_ERROR_CODES.MALFORMED_RESPONSE,
      message: '服务端返回的数据不是合法 JSON。',
      httpStatus: response.status,
      cause: error,
    })
  }

  if (!isApiResult<T>(parsed)) {
    throw new ApiError({
      code: CLIENT_ERROR_CODES.MALFORMED_RESPONSE,
      message: '服务端响应结构不符合 EDU-05 Result 格式。',
      httpStatus: response.status,
    })
  }

  return parsed
}

/** 校验 Java 响应结构 */
function isApiResult<T>(value: unknown): value is ApiResult<T> {
  if (typeof value !== 'object' || value === null) {
    return false
  }

  const candidate = value as Record<string, unknown>

  return (
    typeof candidate.code === 'number' &&
    typeof candidate.message === 'string' &&
    'data' in candidate
  )
}
