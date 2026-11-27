<script setup lang="ts">
import { computed, ref, watch } from 'vue'

import {
  getStudentWeakPointDiagnosis,
} from '@/api/learning-path'

import type {
  StudentWeakPointDiagnostic,
} from '@/api/learning-path'

import { notifyError } from '@/utils/notify'

const props = defineProps<{
  courseId: string
}>()

const points = ref<StudentWeakPointDiagnostic[]>([])

const loading = ref(false)

let version = 0

// 知识点总数
const totalPoints = computed(() =>
  points.value.length,
)

// 需要加强的知识点
const weakCount = computed(() =>
  points.value.filter(
    point => point.diagnosisLevel < 2,
  ).length,
)

// 有练习记录的知识点
const practiceCount = computed(() =>
  points.value.filter(
    point => point.practiceQuestionCount > 0,
  ).length,
)

// 存在疑似薄弱提问信号的知识点
const questionWeakCount = computed(() =>
  points.value.filter(
    point => point.questionInferredLevel < 2,
  ).length,
)

function diagnosisType(
  level: number,
): 'danger' | 'warning' | 'info' {

  if (level === 0) return 'danger'

  if (level === 1) return 'warning'

  return 'info'
}

function questionLevelLabel(level: number): string {

  if (level === 0) return '重点复习候选'

  if (level === 1) return '疑似不熟练'

  return '暂无明显薄弱证据'
}

function selfAssessmentLabel(level: number): string {

  const labels: Record<number, string> = {
    0: '薄弱',
    1: '不熟练',
    2: '一般',
    3: '熟练',
  }

  return labels[level] ?? '未标记'
}

function practiceLabel(
  point: StudentWeakPointDiagnostic,
): string {

  if (point.practiceMasteryRate === null) {
    return '暂无练习记录'
  }

  return `${point.practiceMasteryRate}%`
}

async function refresh(): Promise<void> {

  const course = props.courseId

  const current = ++version

  points.value = []

  if (!course) {
    loading.value = false
    return
  }

  loading.value = true

  try {

    const result =
      await getStudentWeakPointDiagnosis(course)

    if (current === version) {
      points.value = result
    }

  } catch (error) {

    if (current === version) {
      notifyError(error)
    }

  } finally {

    if (current === version) {
      loading.value = false
    }
  }
}

watch(
  () => props.courseId,
  () => {
    void refresh()
  },
  { immediate: true },
)

defineExpose({ refresh })
</script>

<template>
  <section
    v-loading="loading"
    class="weak-panel"
  >

    <el-divider content-position="left">
      练习与提问综合诊断
    </el-divider>

    <el-alert
      type="info"
      :closable="false"
      title="系统结合最近一次有效练习结果、最近30天的提问频率进行规则诊断。学生自评单独展示，作为辅助参考。"
    />

    <!-- 统计概况 -->
    <div class="weak-summary">

      <el-statistic
        title="课程知识点"
        :value="totalPoints"
      />

      <el-statistic
        title="需要加强"
        :value="weakCount"
      />

      <el-statistic
        title="有练习记录"
        :value="practiceCount"
      />

      <el-statistic
        title="提问疑似薄弱"
        :value="questionWeakCount"
      />

      <el-button
        :disabled="!courseId"
        :loading="loading"
        @click="refresh"
      >
        刷新诊断
      </el-button>

    </div>

    <!-- 综合诊断表 -->
    <el-table
      :data="points"
      row-key="pointId"
      empty-text="当前课程暂无知识点"
      style="width: 100%"
    >

      <el-table-column
        prop="title"
        label="知识点"
        min-width="145"
        fixed="left"
      />

      <el-table-column
        label="练习掌握率"
        min-width="145"
        align="center"
      >
        <template #default="{ row }">

          <div class="diagnosis-value">

            <strong>
              {{ practiceLabel(row) }}
            </strong>

            <span
              v-if="row.practiceQuestionCount > 0"
            >
              {{ row.masteredQuestionCount }}
              /
              {{ row.practiceQuestionCount }}
              道题掌握
            </span>

          </div>

        </template>
      </el-table-column>

      <el-table-column
        label="提问情况"
        min-width="140"
        align="center"
      >
        <template #default="{ row }">

          <div class="diagnosis-value">

            <strong>
              {{ row.effectiveQuestionCount }} 次
            </strong>

            <span>
              {{ row.questionSessionCount }} 个会话
            </span>

          </div>

        </template>
      </el-table-column>

      <el-table-column
        label="提问推断"
        min-width="145"
        align="center"
      >
        <template #default="{ row }">

          <el-tag
            :type="diagnosisType(row.questionInferredLevel)"
          >
            {{ questionLevelLabel(row.questionInferredLevel) }}
          </el-tag>

        </template>
      </el-table-column>

      <el-table-column
        label="学生自评"
        min-width="100"
        align="center"
      >
        <template #default="{ row }">

          {{ selfAssessmentLabel(row.selfAssessmentLevel) }}

        </template>
      </el-table-column>

      <el-table-column
        label="综合诊断"
        min-width="155"
      >
        <template #default="{ row }">

          <div class="diagnosis-result">

            <el-tag
              :type="diagnosisType(row.diagnosisLevel)"
            >
              {{ row.diagnosisLabel }}
            </el-tag>

            <p>
              {{ row.diagnosisReason }}
            </p>

            <span>
              {{ row.suggestion }}
            </span>

          </div>

        </template>
      </el-table-column>

    </el-table>

    <div class="weak-panel__footer">
      综合诊断仅依据现有练习和提问数据进行规则分析，
      不代表大语言模型生成的诊断结论。
    </div>

  </section>
</template>

<style scoped>
.weak-panel {
  display: grid;
  gap: 16px;
  min-width: 0;
}

.weak-summary {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 24px;
}

.diagnosis-value {
  display: flex;
  flex-direction: column;
  gap: 5px;
}

.diagnosis-value strong {
  color: var(--sc-text);
  font-size: 14px;
}

.diagnosis-value span {
  color: var(--sc-text-secondary);
  font-size: 11px;
}

.diagnosis-result {
  display: grid;
  justify-items: start;
  gap: 6px;
  padding: 5px 0;
}

.diagnosis-result p {
  margin: 0;
  color: var(--sc-text);
  font-size: 12px;
  line-height: 1.7;
}

.diagnosis-result span {
  color: var(--sc-text-secondary);
  font-size: 11px;
  line-height: 1.6;
}

.weak-panel__footer {
  color: var(--sc-text-secondary);
  font-size: 11px;
  line-height: 1.6;
}
</style>
