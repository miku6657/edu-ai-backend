<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { ElMessageBox } from 'element-plus'
import { RefreshCw } from '@lucide/vue'

import type { Course } from '@/api/course'
import { listJoinedCourses } from '@/api/course'
import {
  confirmLearningGoal,
  getCourseLearningGoal,
  saveLearningGoalDraft,
} from '@/api/learning-goal'
import type { LearningGoal } from '@/api/learning-goal'
import {
  generateLearningPath,
  getLearningPath,
  getLearningPathByGoal,
  getLearningPathProgress,
  previewLearningPathAdjustment,
  applyLearningPathAdjustment,
} from '@/api/learning-path'
import type {
  AdjustmentPreview,
  LearningPath,
  LearningPathProgress,
} from '@/api/learning-path'
import BaseCard from '@/components/ui/BaseCard.vue'
import StudentKnowledgeMasteryPanel from '@/views/ddl/StudentKnowledgeMasteryPanel.vue'
import {
  notifyError,
  notifySuccess,
  notifyWarning,
} from '@/utils/notify'

const courses = ref<Course[]>([])
const courseId = ref('')
const goal = ref<LearningGoal | null>(null)
const loading = ref(false)
const saving = ref(false)
const queryVersion = ref(0)
const generatedPath = ref<LearningPath | null>(null)
const generating = ref(false)
const learningPathProgress = ref<LearningPathProgress | null>(null)
const adjustment = ref<AdjustmentPreview | null>(null)
const adjusting = ref(false)
const adjustmentVisible = ref(false)

const currentWeekLabel = computed(() => {
  const path = generatedPath.value
  if (path === null) return ''

  const currentItem = path.items.find((item) => item.status === 'PENDING')
  if (currentItem !== undefined) {
    return `第 ${currentItem.weekNo} 周`
  }

  return path.items.length === 0 ? '暂无阶段' : '已完成'
})

const form = ref({
  goalText: '',
  weeklyHours: 6,
  targetDate: '',
})

onMounted(async () => {
  try {
    courses.value = await listJoinedCourses()
    courseId.value = String(courses.value[0]?.id ?? '')
  } catch (error) {
    notifyError(error)
  }
})

watch(courseId, async (id) => {
  const version = ++queryVersion.value

  goal.value = null
  generatedPath.value = null
  learningPathProgress.value = null
  adjustment.value = null
  adjustmentVisible.value = false
  form.value = {
    goalText: '',
    weeklyHours: 6,
    targetDate: '',
  }

  if (!id) {
    loading.value = false
    return
  }

  loading.value = true
  try {
    const result = await getCourseLearningGoal(id)

    if (version !== queryVersion.value) return

    goal.value = result

    if (result) {
      form.value = {
        goalText: result.goalText,
        weeklyHours: result.weeklyHours,
        targetDate: result.targetDate,
      }

      if (result.status === 'CONFIRMED') {
        const existingPath = await getLearningPathByGoal(result.id)

        if (version !== queryVersion.value) return

        generatedPath.value = existingPath

        if (existingPath) {
          learningPathProgress.value =
            await getLearningPathProgress(existingPath.pathId)

          if (version !== queryVersion.value) return
        }
      }
    }
  } catch (error) {
    if (version === queryVersion.value) {
      notifyError(error)
    }
  } finally {
    if (version === queryVersion.value) {
      loading.value = false
    }
  }
})

async function saveDraft(): Promise<void> {
  const selectedCourseId = courseId.value

  if (!selectedCourseId) {
    notifyWarning('请先选择课程')
    return
  }

  if (!form.value.goalText.trim()) {
    notifyWarning('请输入学习目标')
    return
  }

  if (!form.value.targetDate) {
    notifyWarning('请选择目标完成日期')
    return
  }

  saving.value = true

  try {
    const result = await saveLearningGoalDraft(
      selectedCourseId,
      {
        goalText: form.value.goalText.trim(),
        weeklyHours: form.value.weeklyHours,
        targetDate: form.value.targetDate,
      },
    )

    if (courseId.value === selectedCourseId) {
      goal.value = result
    }

    notifySuccess('学习目标草稿已保存')
  } catch (error) {
    notifyError(error)
  } finally {
    saving.value = false
  }
}

async function confirmGoal(): Promise<void> {
  const current = goal.value
  const selectedCourseId = courseId.value

  if (!current || current.status !== 'DRAFT') return

  try {
    await ElMessageBox.confirm(
      '确认后目标单将锁定，后续学习路径会以此为依据。确定确认吗？',
      '确认学习目标',
      {
        confirmButtonText: '确认目标',
        cancelButtonText: '返回修改',
        type: 'warning',
      },
    )
  } catch {
    return
  }

  saving.value = true

  try {
    const result = await confirmLearningGoal(current.id)

    if (courseId.value === selectedCourseId) {
      goal.value = result
    }

    notifySuccess('学习目标已确认')
  } catch (error) {
    notifyError(error)
  } finally {
    saving.value = false
  }
}

async function generatePath(): Promise<void> {
  if (!goal.value || goal.value.status !== 'CONFIRMED') {
    notifyWarning('请先确认学习目标')
    return
  }

  adjustment.value = null
  adjustmentVisible.value = false
  generating.value = true

  try {
    generatedPath.value = await generateLearningPath(
      goal.value.id,
    )

    learningPathProgress.value = null
    await refreshPathProgress()
    notifySuccess('学习路径生成成功')
  } catch (error) {
    notifyError(error)
  } finally {
    generating.value = false
  }
}

async function refreshPathProgress(): Promise<void> {
  const pathId = generatedPath.value?.pathId
  if (!pathId) return

  try {
    const [path, progress] = await Promise.all([
      getLearningPath(pathId),
      getLearningPathProgress(pathId),
    ])

    if (generatedPath.value?.pathId !== pathId) return

    generatedPath.value = path
    learningPathProgress.value = progress
  } catch (error) {
    notifyError(error)
  }
}

async function previewAdjustment(): Promise<void> {
  const pathId = generatedPath.value?.pathId
  if (!pathId) return

  adjusting.value = true
  adjustment.value = null
  adjustmentVisible.value = false

  try {
    adjustment.value =
      await previewLearningPathAdjustment(pathId)

    adjustmentVisible.value = true
  } catch (error) {
    notifyError(error)
  } finally {
    adjusting.value = false
  }
}

async function applyAdjustment(): Promise<void> {
  const pathId = generatedPath.value?.pathId
  const current = adjustment.value

  if (!pathId || !current?.feasible || current.changedCount === 0) {
    return
  }

  adjusting.value = true

  try {
    await applyLearningPathAdjustment(
      pathId,
      current.fingerprint,
    )

    generatedPath.value = await getLearningPath(pathId)
    await refreshPathProgress()

    adjustmentVisible.value = false
    adjustment.value = null

    notifySuccess('学习路径已调整')
  } catch (error) {
    notifyError(error)
    adjustmentVisible.value = false
    adjustment.value = null
  } finally {
    adjusting.value = false
  }
}

defineExpose({ refreshPathProgress })
</script>

<template>
  <BaseCard title="我的学习目标">
    <div class="goal-panel" v-loading="loading">
      <el-alert
        type="info"
        :closable="false"
        title="先设置并确认学习目标，再生成个人学习路径"
      />

      <el-form label-position="top">
        <el-form-item label="目标课程" required>
          <el-select
            v-model="courseId"
            placeholder="选择已加入课程"
            style="width: 100%"
            :disabled="saving || generating || adjusting"
          >
            <el-option
              v-for="course in courses"
              :key="course.id"
              :label="course.name"
              :value="String(course.id)"
            />
          </el-select>
        </el-form-item>

        <el-form-item label="学习目标" required>
          <el-input
            v-model="form.goalText"
            type="textarea"
            :rows="3"
            maxlength="1000"
            show-word-limit
            placeholder="例如：四周内掌握 Java 集合与异常处理"
            :disabled="goal?.status === 'CONFIRMED'"
          />
        </el-form-item>

        <el-form-item label="每周可用学习时间" required>
          <el-input-number
            v-model="form.weeklyHours"
            :min="1"
            :max="80"
            :step="1"
            :disabled="goal?.status === 'CONFIRMED'"
          />
          <span style="margin-left: 8px">小时 / 周</span>
        </el-form-item>

        <el-form-item label="目标完成日期" required>
          <el-date-picker
            v-model="form.targetDate"
            type="date"
            value-format="YYYY-MM-DD"
            format="YYYY-MM-DD"
            placeholder="选择日期"
            :disabled="goal?.status === 'CONFIRMED'"
          />
        </el-form-item>
      </el-form>

      <div class="goal-panel__footer">
        <el-tag
          v-if="goal"
          :type="goal.status === 'CONFIRMED' ? 'success' : 'warning'"
        >
          {{ goal.status === 'CONFIRMED' ? '已确认' : '待确认草稿' }}
        </el-tag>

        <el-button
          v-if="goal?.status !== 'CONFIRMED'"
          :disabled="!courseId"
          :loading="saving"
          @click="saveDraft"
        >
          保存草稿
        </el-button>

        <el-button
          v-if="goal?.status === 'DRAFT'"
          type="primary"
          :loading="saving"
          @click="confirmGoal"
        >
          确认目标
        </el-button>
      </div>

      <p
        v-if="goal?.status === 'CONFIRMED'"
        class="goal-panel__hint"
      >
        学习目标已确认。下一阶段将根据目标、每周可用时间和课程知识点生成学习路径。
      </p>

      <StudentKnowledgeMasteryPanel
        v-if="courseId"
        :course-id="courseId"
      />

      <el-button
        v-if="goal?.status === 'CONFIRMED' && !generatedPath"
        type="primary"
        :loading="generating"
        @click="generatePath"
      >
        生成学习路径
      </el-button>

      <div v-if="generatedPath">
        <el-divider content-position="left">
          我的学习路径
        </el-divider>

        <el-alert
          type="success"
          :closable="false"
          :title="`共 ${generatedPath.totalWeeks} 周，预计 ${(
            generatedPath.totalMinutes / 60
          ).toFixed(1)} 小时`"
        />

        <div
          v-if="learningPathProgress"
          class="goal-path-progress"
        >
          <div class="goal-path-progress__item">
            <strong>{{ learningPathProgress.total }}</strong>
            <span>总阶段任务数</span>
          </div>
          <div class="goal-path-progress__item">
            <strong>{{ learningPathProgress.completed }}</strong>
            <span>已完成任务数</span>
          </div>
          <div class="goal-path-progress__item">
            <strong>{{ learningPathProgress.percentage }}%</strong>
            <span>完成百分比</span>
          </div>
          <div class="goal-path-progress__item">
            <strong>{{ currentWeekLabel }}</strong>
            <span>当前学习进度</span>
          </div>
        </div>

        <el-timeline style="margin-top: 20px">
          <el-timeline-item
            v-for="item in generatedPath.items"
            :key="item.id"
            :timestamp="`第 ${item.weekNo} 周 · ${item.plannedDueDate}`"
          >
            <strong>{{ item.title }}</strong>

            <div>
              预计学习 {{ item.plannedMinutes }} 分钟
            </div>
          </el-timeline-item>
        </el-timeline>

        <el-button
          :icon="RefreshCw"
          :loading="adjusting"
          @click="previewAdjustment"
        >
          调整学习路径
        </el-button>

        <el-dialog
          v-model="adjustmentVisible"
          title="学习路径调整建议"
          width="680px"
        >
          <template v-if="adjustment">
            <el-alert
              :type="adjustment.feasible ? 'info' : 'warning'"
              :title="adjustment.message"
              :closable="false"
              show-icon
            />

            <el-table
              v-if="adjustment.items.length"
              :data="adjustment.items"
              style="width: 100%; margin-top: 16px"
            >
              <el-table-column
                prop="itemId"
                label="阶段任务 ID"
                min-width="150"
              />
              <el-table-column
                prop="oldDueDate"
                label="原截止日期"
                width="140"
              />
              <el-table-column
                prop="newDueDate"
                label="建议截止日期"
                width="140"
              />
              <el-table-column label="调整周次" width="130">
                <template #default="{ row }">
                  第 {{ row.oldWeek }} 周
                  →
                  第 {{ row.newWeek }} 周
                </template>
              </el-table-column>
            </el-table>
          </template>

          <template #footer>
            <el-button @click="adjustmentVisible = false">
              返回
            </el-button>

            <el-button
              type="primary"
              :disabled="
                !adjustment?.feasible ||
                adjustment.changedCount === 0
              "
              :loading="adjusting"
              @click="applyAdjustment"
            >
              确认调整
            </el-button>
          </template>
        </el-dialog>
      </div>
    </div>
  </BaseCard>
</template>

<style scoped>
.goal-panel {
  display: grid;
  gap: 16px;
}

.goal-panel__footer {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  flex-wrap: wrap;
  gap: 8px;
}

.goal-panel__hint {
  margin: 0;
  font-size: 13px;
  color: var(--sc-text-secondary);
}

.goal-path-progress {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(120px, 1fr));
  gap: 12px;
  margin-top: 16px;
}

.goal-path-progress__item {
  display: grid;
  gap: 4px;
  padding: 12px;
  border: 1px solid var(--sc-border);
  border-radius: 8px;
}

.goal-path-progress__item strong {
  color: var(--sc-text);
  font-size: 18px;
}

.goal-path-progress__item span {
  color: var(--sc-text-secondary);
  font-size: 12px;
}
</style>
