import type {
  CreateCourseRequest,
  UpdateCourseRequest,
} from '@/api/course'

export class CourseFormValidationError extends Error {}

export interface CourseFormValues {
  courseCode: string
  name: string
  description: string
  credits: number
  applicableMajor: string
  semester: string
}

/**
 * 创建与编辑使用相同的课程信息校验。
 */
export function toCourseRequest(
  form: CourseFormValues,
): CreateCourseRequest & UpdateCourseRequest {
  const courseCode = form.courseCode
    .trim()
    .toUpperCase()

  const name = form.name.trim()
  const description = form.description.trim()
  const applicableMajor = form.applicableMajor.trim()
  const semester = form.semester.trim()

  if (!/^[A-Z0-9_-]{2,32}$/.test(courseCode)) {
    throw new CourseFormValidationError(
      '课程编码应为2-32位字母、数字、下划线或横线',
    )
  }

  if (!name || name.length > 100) {
    throw new CourseFormValidationError(
      '课程名称不能为空且不能超过100个字符',
    )
  }

  if (description.length > 500) {
    throw new CourseFormValidationError(
      '课程描述不能超过500个字符',
    )
  }

  if (
    !Number.isFinite(form.credits)
    || form.credits <= 0
    || form.credits > 99.9
    || !Number.isInteger(form.credits * 10)
  ) {
    throw new CourseFormValidationError(
      '学分应大于0、不超过99.9，最多一位小数',
    )
  }

  if (!applicableMajor || applicableMajor.length > 200) {
    throw new CourseFormValidationError(
      '适用专业不能为空且不能超过200个字符',
    )
  }

  if (!semester || semester.length > 50) {
    throw new CourseFormValidationError(
      '开课学期不能为空且不能超过50个字符',
    )
  }

  return {
    courseCode,
    name,
    description,
    credits: form.credits,
    applicableMajor,
    semester,
  }
}

/**
 * 保留统一的创建请求入口。
 */
export function toCreateCourseRequest(
  form: CourseFormValues,
): CreateCourseRequest {
  return toCourseRequest(form)
}
