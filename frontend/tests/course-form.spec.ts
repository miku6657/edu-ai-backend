import { describe, expect, it } from 'vitest'

import {
  CourseFormValidationError,
  toCreateCourseRequest,
} from '@/views/course/course-form'

describe('course form', () => {
  it('创建课程时清理名称和描述', () => {
    expect(
      toCreateCourseRequest('  操作系统原理  ', '  2026 秋  '),
    ).toEqual({
      name: '操作系统原理',
      description: '2026 秋',
    })
  })

  it('空描述不提交', () => {
    expect(
      toCreateCourseRequest('数据库', '   '),
    ).toEqual({
      name: '数据库',
    })
  })

  it('拒绝空课程名称', () => {
    expect(
      () => toCreateCourseRequest('   ', ''),
    ).toThrow(CourseFormValidationError)
  })

  it('拒绝超过100个字符的课程名称', () => {
    expect(
      () => toCreateCourseRequest('a'.repeat(101), ''),
    ).toThrow(CourseFormValidationError)
  })
})
