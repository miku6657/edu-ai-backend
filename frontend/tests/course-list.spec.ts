import { describe, expect, it } from 'vitest'

import type { Course } from '@/api/course'
import { filterCourses } from '@/views/course/course-list'

function course(
  id: string,
  name: string,
  description: string | null,
): Course {
  return {
    id,
    name,
    description,
    ownerId: '10001',
    status: 1,
    createTime: '2026-10-01T10:00:00',
    updateTime: '2026-10-01T10:00:00',
  }
}

const COURSES: Course[] = [
  course('1', '数据结构与算法分析', '2025-2026 学年 第 1 学期'),
  course('2', '计算机网络', 'Computer Networks'),
  course('3', '操作系统', null),
]

describe('course filter', () => {
  it('空查询返回全部课程', () => {
    expect(filterCourses(COURSES, '')).toHaveLength(3)
    expect(filterCourses(COURSES, '   ')).toHaveLength(3)
  })

  it('按课程名称和描述过滤', () => {
    expect(
      filterCourses(COURSES, '网络').map(item => item.name),
    ).toEqual(['计算机网络'])

    expect(
      filterCourses(COURSES, 'computer').map(item => item.name),
    ).toEqual(['计算机网络'])

    expect(filterCourses(COURSES, '2025-2026')).toHaveLength(1)
  })

  it('无匹配时返回空数组且不修改原数组', () => {
    expect(filterCourses(COURSES, '不存在')).toEqual([])
    expect(COURSES).toHaveLength(3)
  })
})
