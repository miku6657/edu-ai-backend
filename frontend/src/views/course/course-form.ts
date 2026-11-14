import type { CreateCourseRequest } from '@/api/course'

/** 课程表单校验异常 */
export class CourseFormValidationError extends Error {}

/**
 * 创建课程请求参数转换。
 * 去除首尾空格，空描述不提交。
 */
export function toCreateCourseRequest(
  name: string,
  description: string,
): CreateCourseRequest {
  const courseName = name.trim()

  if (courseName === '') {
    throw new CourseFormValidationError('请填写课程名称。')
  }

  if (courseName.length > 100) {
    throw new CourseFormValidationError('课程名称不能超过100个字符。')
  }

  const trimmedDescription = description.trim()

  if (trimmedDescription === '') {
    return {
      name: courseName,
    }
  }

  return {
    name: courseName,
    description: trimmedDescription,
  }
}
