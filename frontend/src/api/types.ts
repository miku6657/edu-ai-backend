/**
 * EDU-05 Java 后端统一响应结构
 *
 * 对应 com.edu.ai.common.Result<T>
 */
export interface ApiResult<T> {
  code: number
  message: string
  data: T | null
}

/** Java 后端成功状态码 */
export const SUCCESS_CODE = 200

/**
 * MySQL BIGINT ID
 * 前端统一使用字符串，避免 JS Number 精度丢失。
 */
export type BigIntId = string
