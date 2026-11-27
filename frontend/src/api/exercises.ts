import { apiRequest } from '@/api/client'
import type { BigIntId } from '@/api/types'

export type ExerciseStatus = 'DRAFT' | 'PUBLISHED' | 'CLOSED'
export type ExerciseQuestionType =
  | 'SINGLE_CHOICE'
  | 'MULTIPLE_CHOICE'
  | 'TRUE_FALSE'
  | 'SHORT_ANSWER'

export interface ExerciseOption {
  label: string
  content: string
}

export interface ExerciseQuestionRequest {
  knowledgePointId: BigIntId
  questionType: ExerciseQuestionType
  stem: string
  options: ExerciseOption[]
  correctAnswer: string
  explanation: string
  score: number
}

export interface ExerciseSaveRequest {
  title: string
  description: string
  difficulty: number
  estimatedMinutes: number
  questions: ExerciseQuestionRequest[]
}

export interface ExerciseKnowledgePoint {
  id: BigIntId
  title: string
  difficulty: number
}

export interface ExerciseQuestion {
  id: BigIntId
  knowledgePointId: BigIntId
  knowledgePointTitle: string
  questionType: ExerciseQuestionType
  stem: string
  options: ExerciseOption[] | null
  correctAnswer: string | null
  explanation: string | null
  score: number
  sortOrder: number
}

export interface Exercise {
  id: BigIntId
  courseId: BigIntId
  courseName: string
  title: string
  description: string | null
  difficulty: number
  estimatedMinutes: number
  status: ExerciseStatus
  createdBy: BigIntId
  publishedAt: string | null
  createdAt: string
  updatedAt: string
  questionCount: number
  questions: ExerciseQuestion[]
}

export function listManagedExercises(courseId?: BigIntId): Promise<Exercise[]> {
  const query = courseId
    ? `?courseId=${encodeURIComponent(courseId)}`
    : ''
  return apiRequest<Exercise[]>(`/v1/exercises/manage${query}`)
}

export function listExerciseKnowledgePoints(
  courseId: BigIntId,
): Promise<ExerciseKnowledgePoint[]> {
  return apiRequest<ExerciseKnowledgePoint[]>(
    `/v1/exercises/courses/${encodeURIComponent(courseId)}/knowledge-points`,
  )
}

export function getExercise(exerciseId: BigIntId): Promise<Exercise> {
  return apiRequest<Exercise>(`/v1/exercises/${encodeURIComponent(exerciseId)}`)
}

export function createExercise(
  courseId: BigIntId,
  request: ExerciseSaveRequest,
): Promise<Exercise> {
  return apiRequest<Exercise>(
    `/v1/exercises/courses/${encodeURIComponent(courseId)}`,
    { method: 'POST', body: request },
  )
}

export function updateExercise(
  exerciseId: BigIntId,
  request: ExerciseSaveRequest,
): Promise<Exercise> {
  return apiRequest<Exercise>(
    `/v1/exercises/${encodeURIComponent(exerciseId)}`,
    { method: 'PUT', body: request },
  )
}

export function publishExercise(exerciseId: BigIntId): Promise<Exercise> {
  return apiRequest<Exercise>(
    `/v1/exercises/${encodeURIComponent(exerciseId)}/publish`,
    { method: 'POST' },
  )
}

export function closeExercise(exerciseId: BigIntId): Promise<Exercise> {
  return apiRequest<Exercise>(
    `/v1/exercises/${encodeURIComponent(exerciseId)}/close`,
    { method: 'POST' },
  )
}

export function deleteExerciseDraft(exerciseId: BigIntId): Promise<void> {
  return apiRequest<void>(`/v1/exercises/${encodeURIComponent(exerciseId)}`, {
    method: 'DELETE',
  })
}

export interface StudentExercise {
  id: BigIntId
  courseId: BigIntId
  courseName: string
  title: string
  description: string | null
  difficulty: number
  estimatedMinutes: number
  questionCount: number
  attemptCount: number
  latestStatus: string | null
  questions: StudentExerciseQuestion[]
}

export interface StudentExerciseQuestion {
  id: BigIntId
  knowledgePointId: BigIntId
  knowledgePointTitle: string
  questionType: ExerciseQuestionType
  stem: string
  options: ExerciseOption[]
  score: number
  sortOrder: number
}

export function listStudentExercises(
  courseId?: BigIntId,
): Promise<StudentExercise[]> {
  const query = courseId
    ? `?courseId=${encodeURIComponent(courseId)}`
    : ''

  return apiRequest<StudentExercise[]>(
    `/v1/student/exercises${query}`,
  )
}

export interface ExerciseAttempt {
  id: BigIntId
  exerciseId: BigIntId
  attemptNo: number
  status: 'IN_PROGRESS' | 'PENDING_REVIEW' | 'GRADED'
  totalScore: number
  earnedScore: number
  startedAt: string
  submittedAt: string | null
}

export interface ExerciseAnswerRequest {
  questionId: BigIntId
  answer: string
}

export interface ExerciseSubmitResult {
  attemptId: BigIntId
  exerciseId: BigIntId
  attemptNo: number
  status: 'PENDING_REVIEW' | 'GRADED'
  totalScore: number
  earnedScore: number
  pendingReviewCount: number
  submittedAt: string
  answers: ExerciseAnswerResult[]
}

export interface ExerciseAnswerResult {
  questionId: BigIntId
  questionType: ExerciseQuestionType
  stem: string
  studentAnswer: string
  correct: boolean | null
  correctAnswer: string | null
  explanation: string | null
  score: number
  earnedScore: number
  pendingReview: boolean
  teacherFeedback: string | null
}

export function getStudentExercise(
  exerciseId: BigIntId,
): Promise<StudentExercise> {
  return apiRequest<StudentExercise>(
    `/v1/student/exercises/${encodeURIComponent(exerciseId)}`,
  )
}

export function startStudentExercise(
  exerciseId: BigIntId,
): Promise<ExerciseAttempt> {
  return apiRequest<ExerciseAttempt>(
    `/v1/student/exercises/${encodeURIComponent(exerciseId)}/attempts`,
    { method: 'POST' },
  )
}

export function submitStudentExercise(
  attemptId: BigIntId,
  answers: ExerciseAnswerRequest[],
): Promise<ExerciseSubmitResult> {
  return apiRequest<ExerciseSubmitResult>(
    `/v1/student/exercises/attempts/${encodeURIComponent(attemptId)}/submit`,
    {
      method: 'POST',
      body: { answers },
    },
  )
}

export function getStudentExerciseResult(
  attemptId: BigIntId,
): Promise<ExerciseSubmitResult> {
  return apiRequest<ExerciseSubmitResult>(
    `/v1/student/exercises/attempts/${encodeURIComponent(attemptId)}`,
  )
}

export interface ExerciseReviewItem {
  attemptId: BigIntId
  exerciseId: BigIntId
  exerciseTitle: string
  courseId: BigIntId
  courseName: string
  studentId: BigIntId
  studentName: string
  attemptNo: number
  pendingReviewCount: number
  totalScore: number
  earnedScore: number
  submittedAt: string
}

export interface ExerciseReviewAnswer {
  answerId: BigIntId
  questionId: BigIntId
  knowledgePointTitle: string | null
  questionType: ExerciseQuestionType
  stem: string
  studentAnswer: string
  correctAnswer: string
  explanation: string
  maxScore: number
  earnedScore: number
  teacherFeedback: string | null
  pendingReview: boolean
}

export interface ExerciseReviewDetail {
  attemptId: BigIntId
  exerciseId: BigIntId
  exerciseTitle: string
  studentId: BigIntId
  studentName: string
  attemptNo: number
  status: string
  totalScore: number
  earnedScore: number
  submittedAt: string
  answers: ExerciseReviewAnswer[]
}

export interface ExerciseGradeItem {
  answerId: BigIntId
  score: number
  feedback?: string
}

export function listPendingExerciseReviews(
  courseId?: BigIntId,
): Promise<ExerciseReviewItem[]> {
  const query = courseId
    ? `?courseId=${encodeURIComponent(courseId)}`
    : ''

  return apiRequest<ExerciseReviewItem[]>(
    `/v1/exercises/reviews/pending${query}`,
  )
}

export function getExerciseReview(
  attemptId: BigIntId,
): Promise<ExerciseReviewDetail> {
  return apiRequest<ExerciseReviewDetail>(
    `/v1/exercises/reviews/${encodeURIComponent(attemptId)}`,
  )
}

export function gradeExerciseAttempt(
  attemptId: BigIntId,
  answers: ExerciseGradeItem[],
): Promise<ExerciseReviewDetail> {
  return apiRequest<ExerciseReviewDetail>(
    `/v1/exercises/reviews/${encodeURIComponent(attemptId)}/grade`,
    {
      method: 'POST',
      body: { answers },
    },
  )
}

export interface StudentMistakeItem {
  answerId: BigIntId
  attemptId: BigIntId
  exerciseId: BigIntId
  exerciseTitle: string
  courseId: BigIntId
  courseName: string
  knowledgePointTitle: string | null
  questionId: BigIntId
  questionType: ExerciseQuestionType
  stem: string
  studentAnswer: string
  correctAnswer: string
  explanation: string
  mistakeType: 'UNANSWERED' | 'OBJECTIVE_WRONG' | 'SHORT_ANSWER_INCOMPLETE'
  teacherFeedback: string | null
  maxScore: number
  earnedScore: number
  answeredAt: string
}

export interface StudentMistakeGroup {
  knowledgePointId: BigIntId | null
  knowledgePointTitle: string
  mistakeCount: number
  mistakes: StudentMistakeItem[]
}

export interface StudentMistakeSummary {
  totalMistakes: number
  knowledgePointCount: number
  knowledgePoints: StudentMistakeGroup[]
}

export interface StudentMistakeDetail extends StudentMistakeItem {
  knowledgePointId: BigIntId | null
  knowledgePointTitle: string | null
}

export interface StudentMistakeRetryList {
  id: BigIntId
  title: string
  itemCount: number
  createdAt: string
}

export function getStudentMistakes(
  courseId?: BigIntId,
): Promise<StudentMistakeSummary> {
  const query = courseId
    ? `?courseId=${encodeURIComponent(courseId)}`
    : ''

  return apiRequest<StudentMistakeSummary>(
    `/v1/student/exercises/mistakes${query}`,
  )
}

export function getStudentMistake(
  answerId: BigIntId,
): Promise<StudentMistakeDetail> {
  return apiRequest<StudentMistakeDetail>(
    `/v1/student/exercises/mistakes/${encodeURIComponent(answerId)}`,
  )
}

export function createStudentMistakeRetryList(
  answerIds: BigIntId[],
  title?: string,
): Promise<StudentMistakeRetryList> {
  return apiRequest<StudentMistakeRetryList>(
    '/v1/student/exercises/mistakes/retry-lists',
    {
      method: 'POST',
      body: {
        answerIds,
        title,
      },
    },
  )
}

/* ==========================================
 * F5-03 学生错题重练
 * ========================================== */

export interface StudentRetryList {
  id: BigIntId
  title: string
  itemCount: number
  status: 'PENDING' | 'COMPLETED'
  attemptCount: number
  latestAttemptStatus: string | null
  createdAt: string
}

export interface StudentRetryOption {
  label: string
  content: string
}

export interface StudentRetryItem {
  id: BigIntId
  sourceAnswerId: BigIntId
  questionId: BigIntId
  courseName: string
  exerciseTitle: string
  knowledgePointTitle: string | null
  questionType: ExerciseQuestionType
  stem: string
  options: StudentRetryOption[]
  score: number
  sortOrder: number
}

export interface StudentRetryDetail {
  id: BigIntId
  title: string
  itemCount: number
  items: StudentRetryItem[]
}

export interface StudentRetryAttempt {
  id: BigIntId
  retryListId: BigIntId
  attemptNo: number
  status: 'IN_PROGRESS' | 'PENDING_SELF_REVIEW' | 'GRADED'
  totalScore: number
  earnedScore: number
  startedAt: string
  submittedAt: string | null
}

export interface StudentRetryAnswerRequest {
  retryItemId: BigIntId
  answer: string
}

export interface StudentRetryAnswerResult {
  retryItemId: BigIntId
  questionId: BigIntId

  courseId: BigIntId
  courseName: string
  knowledgePointTitle: string | null

  questionType: ExerciseQuestionType
  stem: string
  studentAnswer: string
  correct: boolean | null
  correctAnswer: string | null
  explanation: string | null
  score: number
  earnedScore: number
  selfReviewUnderstood: boolean | null
  pendingSelfReview: boolean
}

export interface StudentRetryResult {
  attemptId: BigIntId
  retryListId: BigIntId
  attemptNo: number
  status: 'IN_PROGRESS' | 'PENDING_SELF_REVIEW' | 'GRADED'
  totalScore: number
  earnedScore: number
  pendingSelfReviewCount: number
  submittedAt: string | null
  answers: StudentRetryAnswerResult[]
}

export interface StudentRetrySelfReviewItem {
  retryItemId: BigIntId
  understood: boolean
}

/** 查询学生重练清单 */
export function listStudentRetryLists(): Promise<StudentRetryList[]> {
  return apiRequest<StudentRetryList[]>(
    '/v1/student/exercises/retry-lists',
  )
}

/** 查询清单详情 */
export function getStudentRetryDetail(
  listId: BigIntId,
): Promise<StudentRetryDetail> {
  return apiRequest<StudentRetryDetail>(
    `/v1/student/exercises/retry-lists/${encodeURIComponent(listId)}`,
  )
}

/** 开始或恢复重练 */
export function startStudentRetry(
  listId: BigIntId,
): Promise<StudentRetryAttempt> {
  return apiRequest<StudentRetryAttempt>(
    `/v1/student/exercises/retry-lists/${encodeURIComponent(listId)}/attempts`,
    { method: 'POST' },
  )
}

/** 提交重练答案 */
export function submitStudentRetry(
  attemptId: BigIntId,
  answers: StudentRetryAnswerRequest[],
): Promise<StudentRetryResult> {
  return apiRequest<StudentRetryResult>(
    `/v1/student/exercises/retry-lists/attempts/${encodeURIComponent(attemptId)}/submit`,
    {
      method: 'POST',
      body: { answers },
    },
  )
}

/** 查询重练结果 */
export function getStudentRetryResult(
  attemptId: BigIntId,
): Promise<StudentRetryResult> {
  return apiRequest<StudentRetryResult>(
    `/v1/student/exercises/retry-lists/attempts/${encodeURIComponent(attemptId)}`,
  )
}

/** 简答题自我核对 */
export function selfReviewStudentRetry(
  attemptId: BigIntId,
  answers: StudentRetrySelfReviewItem[],
): Promise<StudentRetryResult> {
  return apiRequest<StudentRetryResult>(
    `/v1/student/exercises/retry-lists/attempts/${encodeURIComponent(attemptId)}/self-review`,
    {
      method: 'POST',
      body: { answers },
    },
  )
}

export interface StudentRetryDraftAnswer {
  retryItemId: BigIntId
  answer: string
}

export interface StudentRetryDraft {
  attemptId: BigIntId
  answers: StudentRetryDraftAnswer[]
}

/** 获取重练草稿 */
export function getStudentRetryDraft(
  attemptId: BigIntId,
): Promise<StudentRetryDraft> {
  return apiRequest<StudentRetryDraft>(
    `/v1/student/exercises/retry-lists/attempts/${encodeURIComponent(attemptId)}/draft`,
  )
}

/** 保存重练草稿 */
export function saveStudentRetryDraft(
  attemptId: BigIntId,
  answers: StudentRetryDraftAnswer[],
): Promise<StudentRetryDraft> {
  return apiRequest<StudentRetryDraft>(
    `/v1/student/exercises/retry-lists/attempts/${encodeURIComponent(attemptId)}/draft`,
    {
      method: 'PUT',
      body: { answers },
    },
  )
}

/* ==========================================
 * F5-03 重练历史与知识点掌握情况
 * ========================================== */

export interface StudentRetryHistory {
  attemptId: BigIntId
  retryListId: BigIntId
  retryListTitle: string
  attemptNo: number
  status: 'IN_PROGRESS' | 'PENDING_SELF_REVIEW' | 'GRADED'
  totalScore: number
  earnedScore: number
  itemCount: number
  answeredCount: number
  objectiveCount: number
  correctCount: number
  accuracyRate: number
  shortAnswerCount: number
  selfReviewedMasteredCount: number
  startedAt: string
  submittedAt: string | null
  gradedAt: string | null
}

export interface StudentRetryKnowledgePoint {
  courseId: BigIntId
  courseName: string
  knowledgePointId: BigIntId | null
  knowledgePointTitle: string
  questionCount: number
  masteredCount: number
  needsPracticeCount: number
  masteryRate: number
  status: 'MASTERED' | 'NEEDS_PRACTICE'
  lastPracticedAt: string | null
}

export interface StudentRetryMastery {
  totalKnowledgePoints: number
  masteredKnowledgePoints: number
  needsPracticeKnowledgePoints: number
  knowledgePoints: StudentRetryKnowledgePoint[]
}

/** 查询重练历史 */
export function listStudentRetryHistory(): Promise<StudentRetryHistory[]> {
  return apiRequest<StudentRetryHistory[]>(
    '/v1/student/exercises/retry-lists/history',
  )
}

/** 查询知识点掌握情况 */
export function getStudentRetryMastery(
  courseId?: BigIntId,
): Promise<StudentRetryMastery> {
  const query = courseId
    ? `?courseId=${encodeURIComponent(courseId)}`
    : ''

  return apiRequest<StudentRetryMastery>(
    `/v1/student/exercises/retry-lists/mastery${query}`,
  )
}
