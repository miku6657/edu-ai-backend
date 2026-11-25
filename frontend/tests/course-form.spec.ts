import { describe, expect, it } from 'vitest'

import {
  CourseFormValidationError,
  toCourseRequest,
  type CourseFormValues,
} from '@/views/course/course-form'

const validForm = (): CourseFormValues => ({
  courseCode: 'java101',
  name: '  Java程序设计  ',
  description: '  Java基础课程  ',
  credits: 3.5,
  applicableMajor: '软件工程',
  semester: '2026-2027-1',
})

describe('课程信息维护 F1-01', () => {
  it('正确转换并清理课程信息', () => {
    expect(toCourseRequest(validForm())).toEqual({
      courseCode: 'JAVA101',
      name: 'Java程序设计',
      description: 'Java基础课程',
      credits: 3.5,
      applicableMajor: '软件工程',
      semester: '2026-2027-1',
    })
  })

  it('拒绝无效课程编码', () => {
    const form = validForm()
    form.courseCode = '无效编码'

    expect(() => toCourseRequest(form)).toThrow(CourseFormValidationError)
  })

  it('拒绝空课程名称', () => {
    const form = validForm()
    form.name = '   '

    expect(() => toCourseRequest(form)).toThrow(CourseFormValidationError)
  })

  it('拒绝不合法的学分', () => {
    const form = validForm()
    form.credits = -1

    expect(() => toCourseRequest(form)).toThrow(CourseFormValidationError)
  })

  it('拒绝空适用专业', () => {
    const form = validForm()
    form.applicableMajor = ''

    expect(() => toCourseRequest(form)).toThrow(CourseFormValidationError)
  })

  it('拒绝空开课学期', () => {
    const form = validForm()
    form.semester = ''

    expect(() => toCourseRequest(form)).toThrow(CourseFormValidationError)
  })
})
