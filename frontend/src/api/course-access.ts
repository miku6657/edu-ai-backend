import { apiRequest } from '@/api/client'
import {
  listCourses,
  listJoinedCourses,
  type Course,
} from '@/api/course'
import { useAuthStore } from '@/stores/auth'

interface AdminCoursePage {
  current: number
  size: number
  total: number
  pages: number
  records: Course[]
}

export async function listAccessibleCourses(): Promise<Course[]> {
  const auth = useAuthStore()

  if (auth.isTeacher) {
    return listCourses()
  }

  if (auth.isStudent) {
    return listJoinedCourses()
  }

  if (auth.isAdmin) {
    const first = await apiRequest<AdminCoursePage>(
      '/admin/courses?current=1&size=100',
    )

    const courses = [...first.records]

    for (let page = 2; page <= first.pages; page++) {
      const result = await apiRequest<AdminCoursePage>(
        `/admin/courses?current=${page}&size=100`,
      )
      courses.push(...result.records)
    }

    return courses
  }

  return []
}
