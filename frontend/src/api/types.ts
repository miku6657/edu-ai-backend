/**
 * EDU-05 后端统一响应结构
 *
 * 支持两种服务端约定：
 * 1. Java `Result<T>`：`code` 为数字，`data` 为真实数据
 * 2. DDL / 任务类接口：`code` 可为字符串成功码（如 `OK`）或稳定错误码
 */
export interface ApiResult<T> {
  code: number | string
  message: string
  data: T | null
  trace_id?: string | null
  timestamp?: string | null
}

/** Java 后端成功状态码 */
export const SUCCESS_CODE = 200
export const SUCCESS_CODES = new Set<number | string>([SUCCESS_CODE, 'OK', 'SUCCESS'])

/**
 * MySQL BIGINT ID
 * 前端统一使用字符串，避免 JS Number 精度丢失。
 */
export type BigIntId = string
