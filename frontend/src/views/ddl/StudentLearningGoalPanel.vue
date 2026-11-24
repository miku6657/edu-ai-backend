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
  previewWeakReview,
  previewWeakReviewPlan,
  applyWeakReviewPlan,
} from '@/api/learning-path'
import type {
  AdjustmentPreview,
  LearningPath,
  LearningPathProgress,
  WeakReviewPreview,
  WeakReviewPlan,
} from '@/api/learning-path'
import BaseCard from '@/components/ui/BaseCard.vue'
import StudentKnowledgeMasteryPanel from '@/views/ddl/StudentKnowledgeMasteryPanel.vue'
import StudentWeakPointPanel from '@/views/ddl/StudentWeakPointPanel.vue'
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
const weakReview = ref<WeakReviewPreview | null>(null)
const weakReviewLoading = ref(false)
const weakPlan = ref<WeakReviewPlan | null>(null)
const weakPlanLoading = ref(false)
let weakReviewRequestVersion = 0
let weakPlanRequestVersion = 0

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
  weakReview.value = null
  weakPlan.value = null
  weakReviewRequestVersion++
  weakPlanRequestVersion++
  weakReviewLoading.value = false
  weakPlanLoading.value = false
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

async function loadWeakReview(): Promise<void> {
  const pathId = generatedPath.value?.pathId
  if (!pathId) return

  weakPlanRequestVersion++
  weakPlan.value = null
  weakPlanLoading.value = false
  const requestVersion = ++weakReviewRequestVersion
  weakReviewLoading.value = true
  weakReview.value = null

  try {
    const result = await previewWeakReview(pathId)

    if (
      requestVersion !== weakReviewRequestVersion ||
      generatedPath.value?.pathId !== pathId
    ) return

    weakReview.value = result
  } catch (error) {
    if (
      requestVersion === weakReviewRequestVersion &&
      generatedPath.value?.pathId === pathId
    ) {
      notifyError(error)
    }
  } finally {
    if (requestVersion === weakReviewRequestVersion) {
      weakReviewLoading.value = false
    }
  }
}

/**
 * 预览系统新增复习任务后的实际排期
 */
async function previewWeakPlan(): Promise<void> {
  const pathId = generatedPath.value?.pathId

  if (!pathId) {
    notifyWarning('请先生成学习路径')
    return
  }

  const requestVersion = ++weakPlanRequestVersion
  weakPlanLoading.value = true
  weakPlan.value = null

  try {
    const result = await previewWeakReviewPlan(pathId)

    if (
      requestVersion !== weakPlanRequestVersion ||
      generatedPath.value?.pathId !== pathId
    ) {
      return
    }

    weakPlan.value = result

    if (!result.feasible) {
      notifyWarning(result.message)
    }
  } catch (error) {
    if (
      requestVersion === weakPlanRequestVersion &&
      generatedPath.value?.pathId === pathId
    ) {
      notifyError(error)
    }
  } finally {
    if (requestVersion === weakPlanRequestVersion) {
      weakPlanLoading.value = false
    }
  }
}

/**
 * 学生确认追加复习任务
 */
async function confirmWeakPlan(): Promise<void> {
  const pathId = generatedPath.value?.pathId
  const plan = weakPlan.value

  if (!pathId || !plan) {
    return
  }

  if (!plan.feasible || plan.additionalMinutes <= 0) {
    notifyWarning('当前没有可执行的复习计划')
    return
  }

  try {
    await ElMessageBox.confirm(
      `将新增 ${plan.additions.length} 个复习阶段，`
        + `共 ${plan.additionalMinutes} 分钟。`
        + `同时可能调整 ${plan.moves.length} 个未完成任务的截止日期。`
        + '是否确认？',
      '确认追加复习任务',
      {
        confirmButtonText: '确认添加',
        cancelButtonText: '取消',
        type: 'warning',
      },
    )
  } catch {
    return
  }

  if (
    generatedPath.value?.pathId !== pathId ||
    weakPlan.value?.fingerprint !== plan.fingerprint
  ) {
    return
  }

  const requestVersion = ++weakPlanRequestVersion
  weakPlanLoading.value = true

  try {
    await applyWeakReviewPlan(
      pathId,
      plan.fingerprint,
    )

    if (
      requestVersion !== weakPlanRequestVersion ||
      generatedPath.value?.pathId !== pathId
    ) {
      return
    }

    weakPlan.value = null
    adjustment.value = null

    await refreshPathProgress()

    if (generatedPath.value?.pathId !== pathId) return

    weakPlanLoading.value = false
    await loadWeakReview()
    notifySuccess('复习阶段已加入个人DDL')
  } catch (error) {
    if (
      requestVersion === weakPlanRequestVersion &&
      generatedPath.value?.pathId === pathId
    ) {
      weakPlan.value = null
      notifyError(error)
    }
  } finally {
    if (requestVersion === weakPlanRequestVersion) {
      weakPlanLoading.value = false
    }
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
            :disabled="saving || generating || adjusting || weakPlanLoading"
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

      <StudentWeakPointPanel
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

        <el-divider content-position="left">
          高频提问复习建议
        </el-divider>

        <el-alert
          type="info"
          :closable="false"
          title="系统根据最近30天的提问频率，分析可能需要加强复习的知识点。该结果仅供学习规划参考。"
        />

        <div class="goal-weak-review__action">
          <el-button
            :loading="weakReviewLoading"
            :disabled="weakPlanLoading"
            @click="loadWeakReview"
          >
            分析薄弱知识点
          </el-button>
        </div>

        <div
          v-if="weakReview"
          class="goal-weak-review__result"
        >
          <el-alert
            :title="weakReview.message"
            :closable="false"
            type="warning"
          />

          <el-table
            :data="weakReview.items"
            empty-text="当前没有需要追加时间的复习建议"
          >
            <el-table-column
              prop="title"
              label="知识点"
              min-width="160"
            />

            <el-table-column
              prop="effectiveCount"
              label="有效提问"
              width="100"
            />

            <el-table-column
              prop="currentMinutes"
              label="已安排(分钟)"
              width="130"
            />

            <el-table-column
              prop="recommendedMinutes"
              label="建议总时长(分钟)"
              width="150"
            />

            <el-table-column
              label="建议增加"
              width="120"
            >
              <template #default="{ row }">
                <el-tag type="warning">
                  +{{ row.additionalMinutes }} 分钟
                </el-tag>
              </template>
            </el-table-column>
          </el-table>

          <p class="goal-weak-review__hint">
            此处为复习建议预览，尚未新增任务或改变 DDL 截止日期。
          </p>
        </div>

        <div
          v-if="weakReview"
          class="weak-review-plan"
        >
          <el-divider content-position="left">
            自动生成复习任务
          </el-divider>

          <el-alert
            title="系统会优先保留已有学习任务，再根据可用学习时间安排新增复习阶段。"
            type="info"
            :closable="false"
          />

          <div class="weak-review-plan__actions">
            <el-button
              type="primary"
              plain
              :loading="weakPlanLoading"
              :disabled="weakReview.additionalTotalMinutes <= 0"
              @click="previewWeakPlan"
            >
              预览实际排期
            </el-button>
          </div>

          <div
            v-if="weakPlan"
            class="weak-review-plan__result"
          >
            <el-alert
              :title="weakPlan.message"
              :type="weakPlan.feasible ? 'success' : 'warning'"
              :closable="false"
              show-icon
            />

            <div
              v-if="weakPlan.feasible"
              class="weak-review-plan__summary"
            >
              <el-statistic
                title="新增复习阶段"
                :value="weakPlan.additions.length"
              />

              <el-statistic
                title="新增学习时间（分钟）"
                :value="weakPlan.additionalMinutes"
              />

              <el-statistic
                title="需要顺延的任务"
                :value="weakPlan.moves.length"
              />
            </div>

            <template
              v-if="weakPlan.feasible &&
                weakPlan.additions.length > 0"
            >
              <h4>新增复习阶段</h4>

              <el-table
                :data="weakPlan.additions"
                style="width: 100%"
              >
                <el-table-column
                  prop="title"
                  label="知识点"
                  min-width="170"
                />

                <el-table-column
                  prop="segmentNo"
                  label="阶段"
                  width="90"
                />

                <el-table-column
                  prop="plannedMinutes"
                  label="学习时间（分钟）"
                  width="140"
                />

                <el-table-column
                  prop="weekNo"
                  label="安排周次"
                  width="110"
                />

                <el-table-column
                  prop="dueDate"
                  label="截止日期"
                  width="140"
                />
              </el-table>
            </template>

            <template
              v-if="weakPlan.feasible &&
                weakPlan.moves.length > 0"
            >
              <h4>现有任务调整</h4>

              <el-table
                :data="weakPlan.moves"
                style="width: 100%"
              >
                <el-table-column
                  prop="itemId"
                  label="阶段任务ID"
                  min-width="150"
                />

                <el-table-column
                  prop="oldDueDate"
                  label="原截止日期"
                  width="140"
                />

                <el-table-column
                  prop="newDueDate"
                  label="新截止日期"
                  width="140"
                />
              </el-table>
            </template>

            <div class="weak-review-plan__actions">
              <el-button
                type="primary"
                :loading="weakPlanLoading"
                :disabled="
                  !weakPlan.feasible ||
                  weakPlan.additionalMinutes <= 0
                "
                @click="confirmWeakPlan"
              >
                确认增加复习任务
              </el-button>
            </div>
          </div>
        </div>

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

.goal-weak-review__action,
.goal-weak-review__result {
  margin-top: 12px;
}

.goal-weak-review__hint {
  margin: 12px 0 0;
  font-size: 13px;
}

.weak-review-plan {
  margin-top: 20px;
  display: grid;
  gap: 14px;
}

.weak-review-plan__actions {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  margin-top: 12px;
}

.weak-review-plan__result {
  display: grid;
  gap: 16px;
}

.weak-review-plan__summary {
  display: flex;
  flex-wrap: wrap;
  gap: 28px;
}
</style>
