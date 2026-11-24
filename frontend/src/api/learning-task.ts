import { apiRequest } from '@/api/client'
import type { BigIntId } from '@/api/types'

/** 学生视角：公共任务及自己的完成状态 */
export interface StudentLearningTask {
  id: BigIntId
  courseId: BigIntId
  title: string
  description: string | null
  deadline: string
  completed: boolean
  completedTime: string | null
  overdue: boolean
}

/** 教师发布、修改公共任务的请求 */
export interface LearningTaskRequest {
  title: string
  description: string | null
  deadline: string
}

/** 全班任务完成统计 */
export interface LearningTaskProgress {
  taskId: BigIntId
  courseId: BigIntId
  totalStudents: number
  completedStudents: number
  pendingStudents: number
  completionRate: number
}

/** 查询课程公共任务 */
export function listCourseLearningTasks(
  courseId: BigIntId,
): Promise<StudentLearningTask[]> {
  return apiRequest<StudentLearningTask[]>(
    `/tasks/course/${encodeURIComponent(courseId)}`,
  )
}

/** 教师发布公共课程任务 */
export function createCourseLearningTask(
  courseId: BigIntId,
  data: LearningTaskRequest,
): Promise<StudentLearningTask> {
  return apiRequest<StudentLearningTask>(
    `/tasks/course/${encodeURIComponent(courseId)}`,
    { method: 'POST', body: data },
  )
}

/** 教师修改公共任务 */
export function updateCourseLearningTask(
  taskId: BigIntId,
  data: LearningTaskRequest,
): Promise<string> {
  return apiRequest<string>(
    `/tasks/${encodeURIComponent(taskId)}`,
    { method: 'PUT', body: data },
  )
}

/** 教师取消公共任务 */
export function cancelCourseLearningTask(
  taskId: BigIntId,
): Promise<string> {
  return apiRequest<string>(
    `/tasks/${encodeURIComponent(taskId)}`,
    { method: 'DELETE' },
  )
}

/** 学生标记完成或撤销完成 */
export function setCourseLearningTaskCompletion(
  taskId: BigIntId,
  completed: boolean,
): Promise<string> {
  return apiRequest<string>(
    `/tasks/${encodeURIComponent(taskId)}/${
      completed ? 'complete' : 'uncomplete'
    }`,
    { method: 'PUT' },
  )
}

/** 教师查看全班完成进度 */
export function getCourseLearningTaskProgress(
  taskId: BigIntId,
): Promise<LearningTaskProgress> {
  return apiRequest<LearningTaskProgress>(
    `/tasks/${encodeURIComponent(taskId)}/progress`,
  )
}
