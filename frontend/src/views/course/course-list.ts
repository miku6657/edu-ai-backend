import type { Course } from '@/api/course'

/**
 * 课程列表客户端搜索。
 * 根据课程名称和描述过滤。
 */
export function filterCourses(
  courses: Course[],
  query: string,
): Course[] {
  const keyword = query.trim().toLowerCase()

  if (keyword === '') {
    return courses
  }

  return courses.filter((course) => {
    const name = course.name.toLowerCase()
    const description = (course.description ?? '').toLowerCase()

    return (
      name.includes(keyword) ||
      description.includes(keyword)
    )
  })
}
