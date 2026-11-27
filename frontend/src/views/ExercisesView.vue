<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { ElMessageBox } from 'element-plus'

import type { Course } from '@/api/course'
import { listAccessibleCourses } from '@/api/course-access'
import type { BigIntId } from '@/api/types'
import {
  closeExercise,
  createExercise,
  deleteExerciseDraft,
  getExercise,
  listExerciseKnowledgePoints,
  listManagedExercises,
  publishExercise,
  updateExercise,
} from '@/api/exercises'
import type {
  Exercise,
  ExerciseKnowledgePoint,
  ExerciseQuestionRequest,
  ExerciseQuestionType,
  ExerciseSaveRequest,
} from '@/api/exercises'
import BaseCard from '@/components/ui/BaseCard.vue'
import EmptyState from '@/components/ui/EmptyState.vue'
import { useAuthStore } from '@/stores/auth'
import { notifyError, notifySuccess, notifyWarning } from '@/utils/notify'

const auth = useAuthStore()
const courses = ref<Course[]>([])
const selectedCourseId = ref<BigIntId>('')
const exercises = ref<Exercise[]>([])
const points = ref<ExerciseKnowledgePoint[]>([])
const loading = ref(false)
const saving = ref(false)
const dialogVisible = ref(false)
const editingId = ref<BigIntId | null>(null)
const searchKeyword = ref('')

const questionTypeOptions: Array<{ label: string; value: ExerciseQuestionType }> = [
  { label: '单选题', value: 'SINGLE_CHOICE' },
  { label: '多选题', value: 'MULTIPLE_CHOICE' },
  { label: '判断题', value: 'TRUE_FALSE' },
  { label: '简答题', value: 'SHORT_ANSWER' },
]

function emptyQuestion(): ExerciseQuestionRequest {
  return {
    knowledgePointId: '',
    questionType: 'SINGLE_CHOICE',
    stem: '',
    options: [
      { label: 'A', content: '' },
      { label: 'B', content: '' },
      { label: 'C', content: '' },
      { label: 'D', content: '' },
    ],
    correctAnswer: 'A',
    explanation: '',
    score: 5,
  }
}

function emptyForm(): ExerciseSaveRequest {
  return {
    title: '',
    description: '',
    difficulty: 2,
    estimatedMinutes: 15,
    questions: [emptyQuestion()],
  }
}

const form = ref<ExerciseSaveRequest>(emptyForm())
const canManage = computed(() => auth.isTeacher || auth.isAdmin)
const filteredExercises = computed(() => {
  const keyword = searchKeyword.value.trim().toLowerCase()
  if (!keyword) return exercises.value
  return exercises.value.filter(item =>
    item.title.toLowerCase().includes(keyword)
    || (item.description ?? '').toLowerCase().includes(keyword),
  )
})
const totalQuestions = computed(() =>
  exercises.value.reduce((sum, item) => sum + item.questionCount, 0),
)

onMounted(async () => {
  if (!canManage.value) return
  await loadCourses()
})

watch(selectedCourseId, () => {
  if (selectedCourseId.value) void loadExercises()
  else exercises.value = []
})

async function loadCourses(): Promise<void> {
  loading.value = true
  try {
    courses.value = await listAccessibleCourses()
    if (!selectedCourseId.value && courses.value.length > 0) {
      selectedCourseId.value = courses.value[0].id
    }
  } catch (error) {
    notifyError(error)
  } finally {
    loading.value = false
  }
}

async function loadExercises(): Promise<void> {
  if (!selectedCourseId.value) return
  loading.value = true
  try {
    exercises.value = await listManagedExercises(selectedCourseId.value)
  } catch (error) {
    notifyError(error)
  } finally {
    loading.value = false
  }
}

async function loadPoints(): Promise<void> {
  if (!selectedCourseId.value) return
  points.value = await listExerciseKnowledgePoints(selectedCourseId.value)
  if (points.value.length === 0) {
    notifyWarning('当前课程还没有知识点，请先到「DDL任务-知识点体系」维护知识点。')
  }
}

async function openCreate(): Promise<void> {
  if (!selectedCourseId.value) {
    notifyWarning('请先选择课程。')
    return
  }
  try {
    await loadPoints()
    editingId.value = null
    form.value = emptyForm()
    if (points.value.length > 0) {
      form.value.questions[0].knowledgePointId = points.value[0].id
    }
    dialogVisible.value = true
  } catch (error) {
    notifyError(error)
  }
}

async function openEdit(item: Exercise): Promise<void> {
  if (item.status !== 'DRAFT') return
  try {
    const [detail] = await Promise.all([
      getExercise(item.id),
      loadPoints(),
    ])
    editingId.value = item.id
    form.value = {
      title: detail.title,
      description: detail.description ?? '',
      difficulty: detail.difficulty,
      estimatedMinutes: detail.estimatedMinutes,
      questions: detail.questions.map(question => ({
        knowledgePointId: question.knowledgePointId,
        questionType: question.questionType,
        stem: question.stem,
        options: question.options ?? [],
        correctAnswer: question.correctAnswer ?? '',
        explanation: question.explanation ?? '',
        score: Number(question.score),
      })),
    }
    dialogVisible.value = true
  } catch (error) {
    notifyError(error)
  }
}

function addQuestion(): void {
  const question = emptyQuestion()
  question.knowledgePointId = points.value[0]?.id ?? ''
  form.value.questions.push(question)
}

function removeQuestion(index: number): void {
  if (form.value.questions.length <= 1) {
    notifyWarning('一个练习至少保留一道题目。')
    return
  }
  form.value.questions.splice(index, 1)
}

function changeQuestionType(question: ExerciseQuestionRequest): void {
  if (question.questionType === 'SINGLE_CHOICE'
    || question.questionType === 'MULTIPLE_CHOICE') {
    question.options = [
      { label: 'A', content: '' },
      { label: 'B', content: '' },
      { label: 'C', content: '' },
      { label: 'D', content: '' },
    ]
    question.correctAnswer = question.questionType === 'SINGLE_CHOICE' ? 'A' : 'A,B'
  } else if (question.questionType === 'TRUE_FALSE') {
    question.options = []
    question.correctAnswer = 'TRUE'
  } else {
    question.options = []
    question.correctAnswer = ''
  }
}

function addOption(question: ExerciseQuestionRequest): void {
  if (question.options.length >= 8) return
  const next = String.fromCharCode(65 + question.options.length)
  question.options.push({ label: next, content: '' })
}

function removeOption(question: ExerciseQuestionRequest, index: number): void {
  if (question.options.length <= 2) {
    notifyWarning('选择题至少保留两个选项。')
    return
  }
  question.options.splice(index, 1)
  question.options.forEach((option, optionIndex) => {
    option.label = String.fromCharCode(65 + optionIndex)
  })
}

function validateForm(): boolean {
  if (!form.value.title.trim()) {
    notifyWarning('请填写练习名称。')
    return false
  }
  if (form.value.questions.length === 0) {
    notifyWarning('请至少添加一道题目。')
    return false
  }
  for (let index = 0; index < form.value.questions.length; index++) {
    const question = form.value.questions[index]
    if (!question.knowledgePointId) {
      notifyWarning(`第${index + 1}题尚未关联知识点。`)
      return false
    }
    if (!question.stem.trim()) {
      notifyWarning(`请填写第${index + 1}题的题目内容。`)
      return false
    }
    if (!question.correctAnswer.trim()) {
      notifyWarning(`请填写第${index + 1}题的参考答案。`)
      return false
    }
    if ((question.questionType === 'SINGLE_CHOICE'
      || question.questionType === 'MULTIPLE_CHOICE')
      && (question.options.length < 2
        || question.options.some(option => !option.content.trim()))) {
      notifyWarning(`请完善第${index + 1}题的选项。`)
      return false
    }
  }
  return true
}

async function saveDraft(): Promise<void> {
  if (!selectedCourseId.value || !validateForm()) return
  saving.value = true
  try {
    if (editingId.value) {
      await updateExercise(editingId.value, form.value)
      notifySuccess('练习草稿已更新。')
    } else {
      await createExercise(selectedCourseId.value, form.value)
      notifySuccess('练习草稿已保存。')
    }
    dialogVisible.value = false
    await loadExercises()
  } catch (error) {
    notifyError(error)
  } finally {
    saving.value = false
  }
}

async function publish(item: Exercise): Promise<void> {
  try {
    await ElMessageBox.confirm(
      '发布后，已加入该课程的学生可以查看这份练习。发布后不能再修改题目。',
      '确认发布练习',
      { type: 'warning', confirmButtonText: '确认发布', cancelButtonText: '再检查一下' },
    )
    await publishExercise(item.id)
    notifySuccess('练习已发布。')
    await loadExercises()
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') notifyError(error)
  }
}

async function close(item: Exercise): Promise<void> {
  try {
    await ElMessageBox.confirm('关闭后学生将无法再进入该练习。', '确认关闭练习', { type: 'warning' })
    await closeExercise(item.id)
    notifySuccess('练习已关闭。')
    await loadExercises()
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') notifyError(error)
  }
}

async function remove(item: Exercise): Promise<void> {
  try {
    await ElMessageBox.confirm('删除后练习草稿及其题目将一并删除，无法恢复。', '删除练习草稿', { type: 'warning' })
    await deleteExerciseDraft(item.id)
    notifySuccess('练习草稿已删除。')
    await loadExercises()
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') notifyError(error)
  }
}

function statusLabel(status: Exercise['status']): string {
  return status === 'DRAFT' ? '草稿' : status === 'PUBLISHED' ? '已发布' : '已关闭'
}

function statusType(status: Exercise['status']): 'info' | 'success' | 'warning' {
  return status === 'DRAFT' ? 'info' : status === 'PUBLISHED' ? 'success' : 'warning'
}

function difficultyLabel(value: number): string {
  return value === 1 ? '基础' : value === 3 ? '进阶' : '中等'
}
</script>

<template>
  <div class="exercise-page">
    <template v-if="canManage">
      <BaseCard class="exercise-hero">
        <div class="exercise-hero__main">
          <div>
            <div class="exercise-eyebrow">PRACTICE MANAGEMENT</div>
            <h2>练习管理</h2>
            <p>教师自主录入题目，按课程知识点组织练习，无需上传任何文件。</p>
          </div>
          <el-button type="primary" :disabled="!selectedCourseId" @click="openCreate">
            新建练习
          </el-button>
        </div>
        <div class="exercise-filters">
          <el-select v-model="selectedCourseId" placeholder="选择课程" style="width: 280px">
            <el-option
              v-for="course in courses"
              :key="course.id"
              :label="course.name"
              :value="course.id"
            />
          </el-select>
          <el-input v-model="searchKeyword" clearable placeholder="搜索练习名称" style="width: 240px" />
          <el-button @click="loadExercises">刷新</el-button>
        </div>
      </BaseCard>

      <div class="exercise-stats">
        <BaseCard><span>练习总数</span><strong>{{ exercises.length }}</strong></BaseCard>
        <BaseCard><span>已发布</span><strong>{{ exercises.filter(item => item.status === 'PUBLISHED').length }}</strong></BaseCard>
        <BaseCard><span>草稿</span><strong>{{ exercises.filter(item => item.status === 'DRAFT').length }}</strong></BaseCard>
        <BaseCard><span>题目总数</span><strong>{{ totalQuestions }}</strong></BaseCard>
      </div>

      <BaseCard>
        <div class="exercise-list-head">
          <div>
            <h3>课程练习</h3>
            <p>保存为草稿后可以继续编辑，检查无误后再发布给学生。</p>
          </div>
          <el-tag type="info">{{ filteredExercises.length }} 份练习</el-tag>
        </div>
        <el-skeleton v-if="loading" :rows="4" animated />
        <EmptyState
          v-else-if="!selectedCourseId"
          title="暂无可管理课程"
          description="请先创建课程，或确认当前教师账号拥有课程管理权限。"
        />
        <EmptyState
          v-else-if="filteredExercises.length === 0"
          title="还没有练习"
          description="点击右上角「新建练习」，手动填写练习信息和题目。"
        />
        <div v-else class="exercise-list">
          <article v-for="item in filteredExercises" :key="item.id" class="exercise-row">
            <div class="exercise-row__body">
              <div class="exercise-row__title-line">
                <h4>{{ item.title }}</h4>
                <el-tag :type="statusType(item.status)" size="small">{{ statusLabel(item.status) }}</el-tag>
              </div>
              <p v-if="item.description">{{ item.description }}</p>
              <div class="exercise-row__meta">
                <span>{{ item.questionCount }} 道题</span>
                <span>{{ difficultyLabel(item.difficulty) }}难度</span>
                <span>预计 {{ item.estimatedMinutes }} 分钟</span>
                <span>更新于 {{ item.updatedAt?.replace('T', ' ').slice(0, 16) }}</span>
              </div>
            </div>
            <div class="exercise-row__actions">
              <el-button v-if="item.status === 'DRAFT'" link type="primary" @click="openEdit(item)">编辑</el-button>
              <el-button v-if="item.status === 'DRAFT'" link type="success" @click="publish(item)">发布</el-button>
              <el-button v-if="item.status === 'PUBLISHED'" link type="warning" @click="close(item)">关闭</el-button>
              <el-button v-if="item.status === 'DRAFT'" link type="danger" @click="remove(item)">删除</el-button>
            </div>
          </article>
        </div>
      </BaseCard>

      <el-dialog
        v-model="dialogVisible"
        :title="editingId ? '编辑练习草稿' : '新建练习'"
        width="min(960px, 94vw)"
        top="4vh"
        destroy-on-close
        class="exercise-dialog"
      >
        <div class="exercise-form">
          <section class="exercise-form__section">
            <h3>练习基本信息</h3>
            <el-form label-position="top">
              <el-form-item label="练习名称" required>
                <el-input v-model="form.title" maxlength="160" show-word-limit placeholder="例如：Java 面向对象基础练习" />
              </el-form-item>
              <el-form-item label="练习说明">
                <el-input v-model="form.description" type="textarea" :rows="2" maxlength="1000" show-word-limit placeholder="说明练习范围、要求或注意事项" />
              </el-form-item>
              <div class="exercise-form__grid">
                <el-form-item label="难度">
                  <el-select v-model="form.difficulty" style="width: 100%">
                    <el-option label="基础" :value="1" />
                    <el-option label="中等" :value="2" />
                    <el-option label="进阶" :value="3" />
                  </el-select>
                </el-form-item>
                <el-form-item label="预计用时（分钟）">
                  <el-input-number v-model="form.estimatedMinutes" :min="1" :max="240" style="width: 100%" />
                </el-form-item>
              </div>
            </el-form>
          </section>

          <section class="exercise-form__section">
            <div class="exercise-list-head">
              <div><h3>练习题目</h3><p>每道题都需要关联当前课程的一个知识点。</p></div>
              <el-button type="primary" plain @click="addQuestion">添加题目</el-button>
            </div>

            <article v-for="(question, index) in form.questions" :key="index" class="question-editor">
              <div class="question-editor__head">
                <strong>第 {{ index + 1 }} 题</strong>
                <el-button link type="danger" @click="removeQuestion(index)">删除题目</el-button>
              </div>
              <el-form label-position="top">
                <div class="exercise-form__grid exercise-form__grid--question">
                  <el-form-item label="题型" required>
                    <el-select
                      v-model="question.questionType"
                      style="width: 100%"
                      @change="changeQuestionType(question)"
                    >
                      <el-option v-for="option in questionTypeOptions" :key="option.value" :label="option.label" :value="option.value" />
                    </el-select>
                  </el-form-item>
                  <el-form-item label="所属知识点" required>
                    <el-select v-model="question.knowledgePointId" filterable style="width: 100%" placeholder="选择知识点">
                      <el-option v-for="point in points" :key="point.id" :label="point.title" :value="point.id" />
                    </el-select>
                  </el-form-item>
                  <el-form-item label="分值">
                    <el-input-number v-model="question.score" :min="0.5" :max="100" :step="0.5" :precision="1" />
                  </el-form-item>
                </div>
                <el-form-item label="题目内容" required>
                  <el-input v-model="question.stem" type="textarea" :rows="3" maxlength="5000" show-word-limit placeholder="填写题干，可以包含代码或场景描述" />
                </el-form-item>

                <div v-if="question.questionType === 'SINGLE_CHOICE' || question.questionType === 'MULTIPLE_CHOICE'" class="question-options">
                  <div class="question-options__head"><strong>选项</strong><el-button link type="primary" @click="addOption(question)">添加选项</el-button></div>
                  <div v-for="(option, optionIndex) in question.options" :key="optionIndex" class="question-option-row">
                    <el-tag>{{ option.label }}</el-tag>
                    <el-input v-model="option.content" :placeholder="`填写选项 ${option.label} 的内容`" />
                    <el-button link type="danger" @click="removeOption(question, optionIndex)">移除</el-button>
                  </div>
                  <el-form-item :label="question.questionType === 'SINGLE_CHOICE' ? '正确选项' : '正确选项（多个用逗号分隔）'" required>
                    <el-input v-model="question.correctAnswer" :placeholder="question.questionType === 'SINGLE_CHOICE' ? '例如：A' : '例如：A,C'" />
                  </el-form-item>
                </div>
                <el-form-item v-else-if="question.questionType === 'TRUE_FALSE'" label="正确答案" required>
                  <el-radio-group v-model="question.correctAnswer">
                    <el-radio value="TRUE">正确</el-radio>
                    <el-radio value="FALSE">错误</el-radio>
                  </el-radio-group>
                </el-form-item>
                <el-form-item v-else label="参考答案（供教师批改使用）" required>
                  <el-input v-model="question.correctAnswer" type="textarea" :rows="2" placeholder="填写参考答案或评分要点" />
                </el-form-item>
                <el-form-item label="答案解析">
                  <el-input v-model="question.explanation" type="textarea" :rows="2" maxlength="5000" placeholder="填写解题思路、知识点说明或易错点" />
                </el-form-item>
              </el-form>
            </article>
          </section>
        </div>
        <template #footer>
          <el-button @click="dialogVisible = false">取消</el-button>
          <el-button type="primary" :loading="saving" @click="saveDraft">保存为草稿</el-button>
        </template>
      </el-dialog>
    </template>

    <BaseCard v-else>
      <EmptyState title="当前账号没有练习管理权限" description="练习创建和发布由课程教师负责，学生练习入口将在后续答题模块中开放。" />
    </BaseCard>
  </div>
</template>

<style scoped>
.exercise-page { display: flex; flex-direction: column; gap: 16px; }
.exercise-hero__main, .exercise-list-head, .question-editor__head { display: flex; align-items: center; justify-content: space-between; gap: 16px; }
.exercise-eyebrow { color: var(--sc-primary); font-size: 10px; font-weight: 700; letter-spacing: .12em; }
.exercise-hero h2 { margin: 5px 0 4px; color: var(--sc-text); font-size: 22px; }
.exercise-hero p, .exercise-list-head p { margin: 0; color: var(--sc-text-secondary); font-size: 12px; }
.exercise-filters { display: flex; flex-wrap: wrap; gap: 10px; margin-top: 20px; }
.exercise-stats { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 12px; }
.exercise-stats :deep(.base-card) { display: flex; flex-direction: column; gap: 8px; }
.exercise-stats span { color: var(--sc-text-secondary); font-size: 12px; }
.exercise-stats strong { color: var(--sc-text); font-size: 24px; font-weight: 650; }
.exercise-list-head { margin-bottom: 16px; }
.exercise-list-head h3, .exercise-form__section h3 { margin: 0 0 5px; color: var(--sc-text); font-size: 15px; }
.exercise-list { display: flex; flex-direction: column; }
.exercise-row { display: flex; justify-content: space-between; align-items: center; gap: 16px; padding: 16px 4px; border-top: 1px solid var(--sc-border); }
.exercise-row__body { min-width: 0; }
.exercise-row__title-line { display: flex; align-items: center; flex-wrap: wrap; gap: 10px; }
.exercise-row h4 { margin: 0; color: var(--sc-text); font-size: 14px; }
.exercise-row p { margin: 7px 0 0; color: var(--sc-text-secondary); font-size: 12px; }
.exercise-row__meta { display: flex; flex-wrap: wrap; gap: 14px; margin-top: 9px; color: var(--sc-text-tertiary); font-size: 11px; }
.exercise-row__actions { display: flex; flex-wrap: wrap; flex: none; }
.exercise-form { max-height: 70vh; overflow: auto; padding-right: 6px; }
.exercise-form__section + .exercise-form__section { margin-top: 22px; }
.exercise-form__grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 12px; }
.exercise-form__grid--question { grid-template-columns: 1fr 2fr 120px; }
.question-editor { margin-top: 14px; padding: 16px; border: 1px solid var(--sc-border); border-radius: 12px; background: var(--sc-bg-subtle, var(--sc-surface)); }
.question-editor__head { margin-bottom: 12px; }
.question-options { margin: 6px 0 12px; }
.question-options__head { display: flex; justify-content: space-between; align-items: center; margin-bottom: 10px; color: var(--sc-text); font-size: 13px; }
.question-option-row { display: grid; grid-template-columns: 38px minmax(0, 1fr) 54px; align-items: center; gap: 8px; margin-bottom: 8px; }
@media (max-width: 760px) { .exercise-stats { grid-template-columns: repeat(2, minmax(0, 1fr)); } .exercise-row { align-items: flex-start; flex-direction: column; } .exercise-form__grid, .exercise-form__grid--question { grid-template-columns: 1fr; gap: 0; } }
</style>
