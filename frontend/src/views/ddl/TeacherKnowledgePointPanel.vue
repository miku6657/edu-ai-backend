<script setup lang="ts">
import {
  computed,
  onMounted,
  ref,
  watch,
} from 'vue'

import { ElMessageBox } from 'element-plus'

import type { Course } from '@/api/course'
import { listCourses } from '@/api/course'
import {
  addKnowledgeDependency,
  addKnowledgePoint,
  deleteKnowledgePoint,
  getCourseKnowledgeMap,
  knowledgeDifficultyLabel,
  removeKnowledgeDependency,
  updateKnowledgePoint,
} from '@/api/learning-path'
import type {
  CourseKnowledgeMap,
  KnowledgeMapDependency,
  KnowledgeMapPoint,
  UpdateKnowledgePointRequest,
} from '@/api/learning-path'
import BaseCard from '@/components/ui/BaseCard.vue'
import CourseKnowledgeMapView from '@/views/ddl/CourseKnowledgeMap.vue'
import {
  notifyError,
  notifySuccess,
  notifyWarning,
} from '@/utils/notify'

interface PointTreeRow {
  key: string
  label: string
  kind: 'chapter' | 'point'
  chapterId: string | null
  point?: KnowledgeMapPoint
  children: PointTreeRow[]
}

const courses = ref<Course[]>([])
const courseId = ref('')
const mapData = ref<CourseKnowledgeMap | null>(null)
const loading = ref(false)
const saving = ref(false)
const activeTab = ref('points')
const dialogVisible = ref(false)
const editingPointId = ref<string | null>(null)
const dependencyForm = ref({
  pointId: '',
  prerequisiteId: '',
})

let requestVersion = 0

function emptyPointForm(): UpdateKnowledgePointRequest {
  return {
    title: '',
    estimatedMinutes: 60,
    sortOrder: 0,
    matchKeywords: '',
    chapterId: null,
    parentId: null,
    difficulty: 2,
  }
}

const pointForm = ref<UpdateKnowledgePointRequest>(
  emptyPointForm(),
)

const points = computed(
  () => mapData.value?.points ?? [],
)
const chapters = computed(
  () => mapData.value?.chapters ?? [],
)
const dependencies = computed(
  () => mapData.value?.dependencies ?? [],
)
const pointMap = computed(() =>
  new Map(
    points.value.map(point => [
      point.id,
      point,
    ]),
  ),
)
const chapterMap = computed(() =>
  new Map(
    chapters.value.map(chapter => [
      chapter.id,
      chapter.name,
    ]),
  ),
)

const excludedParentIds = computed(() => {
  const id = editingPointId.value
  const excluded = new Set<string>()

  if (!id) return excluded

  const queue = [id]

  while (queue.length > 0) {
    const current = queue.shift()

    if (current === undefined || excluded.has(current)) {
      continue
    }

    excluded.add(current)

    for (const point of points.value) {
      if (point.parentId === current) {
        queue.push(point.id)
      }
    }
  }

  return excluded
})

const parentOptions = computed(() =>
  points.value.filter(
    point =>
      point.chapterId === pointForm.value.chapterId
      && !excludedParentIds.value.has(point.id),
  ),
)

const editingHasChildren = computed(() =>
  editingPointId.value !== null
  && points.value.some(
    point => point.parentId === editingPointId.value,
  ),
)

function pointLabel(point: KnowledgeMapPoint): string {
  const chapter = point.chapterId
    ? chapterMap.value.get(point.chapterId)
    : null
  const parent = point.parentId
    ? pointMap.value.get(point.parentId)?.title
    : null

  return [
    chapter ?? '未分类',
    parent,
    point.title,
  ]
    .filter(Boolean)
    .join(' / ')
}

const treeRows = computed<PointTreeRow[]>(() => {
  const visited = new Set<string>()
  const orderedPoints = [...points.value].sort(
    (a, b) =>
      a.sortOrder - b.sortOrder
      || a.id.localeCompare(b.id),
  )
  const childrenMap = new Map<string, KnowledgeMapPoint[]>()

  for (const point of orderedPoints) {
    if (!point.parentId) continue

    const parent = pointMap.value.get(point.parentId)

    if (
      !parent
      || parent.chapterId !== point.chapterId
      || parent.id === point.id
    ) {
      continue
    }

    const children = childrenMap.get(parent.id) ?? []
    children.push(point)
    childrenMap.set(parent.id, children)
  }

  function buildPoint(point: KnowledgeMapPoint): PointTreeRow {
    visited.add(point.id)

    return {
      key: `point:${point.id}`,
      label: point.title,
      kind: 'point',
      chapterId: point.chapterId,
      point,
      children: (childrenMap.get(point.id) ?? [])
        .filter(child => !visited.has(child.id))
        .map(buildPoint),
    }
  }

  function buildGroup(
    groupPoints: KnowledgeMapPoint[],
  ): PointTreeRow[] {
    const roots = groupPoints.filter(point => {
      const parent = point.parentId
        ? pointMap.value.get(point.parentId)
        : null

      return !parent
        || parent.chapterId !== point.chapterId
        || parent.id === point.id
    })

    const result = roots
      .filter(point => !visited.has(point.id))
      .map(buildPoint)

    for (const point of groupPoints) {
      if (!visited.has(point.id)) {
        result.push(buildPoint(point))
      }
    }

    return result
  }

  const result: PointTreeRow[] = chapters.value.map(chapter => ({
    key: `chapter:${chapter.id}`,
    label: chapter.name,
    kind: 'chapter',
    chapterId: chapter.id,
    children: buildGroup(
      orderedPoints.filter(
        point => point.chapterId === chapter.id,
      ),
    ),
  }))

  const unclassified = orderedPoints.filter(
    point =>
      !point.chapterId
      || !chapterMap.value.has(point.chapterId),
  )

  if (unclassified.length > 0) {
    result.push({
      key: 'chapter:unclassified',
      label: '未分类知识点',
      kind: 'chapter',
      chapterId: null,
      children: buildGroup(unclassified),
    })
  }

  return result
})

const dependencyRows = computed(() =>
  dependencies.value.map(edge => ({
    ...edge,
    pointTitle:
      pointMap.value.get(edge.pointId)?.title ?? edge.pointId,
    prerequisiteTitle:
      pointMap.value.get(edge.prerequisiteId)?.title
      ?? edge.prerequisiteId,
  })),
)

onMounted(async () => {
  try {
    const result = await listCourses()
    courses.value = result.filter(course => course.status === 1)
    courseId.value = String(courses.value[0]?.id ?? '')
  } catch (error) {
    notifyError(error)
  }
})

async function refreshKnowledgeMap(): Promise<void> {
  const id = courseId.value
  const version = ++requestVersion

  if (!id) {
    mapData.value = null
    loading.value = false
    return
  }

  loading.value = true

  try {
    const result = await getCourseKnowledgeMap(id)

    if (
      requestVersion === version
      && courseId.value === id
    ) {
      mapData.value = result
    }
  } catch (error) {
    if (requestVersion === version) {
      notifyError(error)
    }
  } finally {
    if (requestVersion === version) {
      loading.value = false
    }
  }
}

watch(courseId, () => {
  requestVersion++
  mapData.value = null
  dialogVisible.value = false
  editingPointId.value = null
  pointForm.value = emptyPointForm()
  dependencyForm.value = {
    pointId: '',
    prerequisiteId: '',
  }
  void refreshKnowledgeMap()
})

function openCreatePoint(
  chapterId: string | null = null,
  parentId: string | null = null,
): void {
  if (!courseId.value || saving.value) return

  editingPointId.value = null
  pointForm.value = {
    ...emptyPointForm(),
    chapterId,
    parentId,
  }
  dialogVisible.value = true
}

function openEditPoint(point: KnowledgeMapPoint): void {
  if (saving.value) return

  editingPointId.value = point.id
  pointForm.value = {
    title: point.title,
    estimatedMinutes: point.estimatedMinutes,
    sortOrder: point.sortOrder,
    matchKeywords: point.matchKeywords ?? '',
    chapterId: point.chapterId,
    parentId: point.parentId,
    difficulty: point.difficulty,
  }
  dialogVisible.value = true
}

function onChapterChange(
  value: string | null | undefined,
): void {
  pointForm.value.chapterId = value || null
  pointForm.value.parentId = null
}

function onParentChange(
  value: string | null | undefined,
): void {
  pointForm.value.parentId = value || null
}

function difficultyTagType(
  difficulty: number,
): 'success' | 'primary' | 'warning' | 'info' {
  switch (difficulty) {
    case 1:
      return 'success'
    case 2:
      return 'primary'
    case 3:
      return 'warning'
    default:
      return 'info'
  }
}

async function savePoint(): Promise<void> {
  const id = courseId.value

  if (!id || saving.value) return

  const form = {
    ...pointForm.value,
    title: pointForm.value.title.trim(),
    matchKeywords: pointForm.value.matchKeywords.trim(),
    chapterId: pointForm.value.chapterId || null,
    parentId: pointForm.value.parentId || null,
  }

  if (!form.title || form.title.length > 200) {
    notifyWarning('知识点名称不能为空且不能超过200个字符')
    return
  }

  if (
    !Number.isInteger(form.estimatedMinutes)
    || form.estimatedMinutes < 1
    || form.estimatedMinutes > 10080
  ) {
    notifyWarning('请输入有效的预计学习时间')
    return
  }

  if (
    !Number.isInteger(form.sortOrder)
    || form.sortOrder < 0
    || form.sortOrder > 2147483647
  ) {
    notifyWarning('请输入有效的非负整数排序')
    return
  }

  if (![1, 2, 3].includes(form.difficulty)) {
    notifyWarning('请选择知识点难度')
    return
  }

  if (form.matchKeywords.length > 500) {
    notifyWarning('匹配关键词不能超过500字')
    return
  }

  if (
    form.parentId
    && !parentOptions.value.some(
      point => point.id === form.parentId,
    )
  ) {
    notifyWarning('所选父级知识点不合法')
    return
  }

  const pointId = editingPointId.value
  saving.value = true

  try {
    if (pointId) {
      await updateKnowledgePoint(id, pointId, form)
      notifySuccess('知识点修改成功')
    } else {
      await addKnowledgePoint(id, form)
      notifySuccess('知识点创建成功')
    }

    if (courseId.value === id) {
      dialogVisible.value = false
      await refreshKnowledgeMap()
    }
  } catch (error) {
    notifyError(error)
  } finally {
    saving.value = false
  }
}

async function removePoint(
  point: KnowledgeMapPoint,
): Promise<void> {
  const id = courseId.value
  if (!id || saving.value) return

  try {
    await ElMessageBox.confirm(
      `确定删除知识点“${point.title}”吗？`
      + '已被课程资料、学习路径或学生学习记录'
      + '使用的知识点无法直接删除。',
      '删除知识点',
      {
        type: 'warning',
        confirmButtonText: '确认删除',
        cancelButtonText: '取消',
      },
    )
  } catch {
    return
  }

  if (courseId.value !== id || saving.value) return

  saving.value = true

  try {
    await deleteKnowledgePoint(id, point.id)
    notifySuccess('知识点删除成功')

    if (
      dependencyForm.value.pointId === point.id
      || dependencyForm.value.prerequisiteId === point.id
    ) {
      dependencyForm.value = {
        pointId: '',
        prerequisiteId: '',
      }
    }

    await refreshKnowledgeMap()
  } catch (error) {
    notifyError(error)
  } finally {
    saving.value = false
  }
}

async function createDependency(): Promise<void> {
  const id = courseId.value
  const { pointId, prerequisiteId } = dependencyForm.value

  if (
    !id
    || !pointId
    || !prerequisiteId
    || saving.value
  ) {
    notifyWarning('请选择完整的先修关系')
    return
  }

  if (pointId === prerequisiteId) {
    notifyWarning('知识点不能依赖自身')
    return
  }

  const exists = dependencies.value.some(
    edge =>
      edge.pointId === pointId
      && edge.prerequisiteId === prerequisiteId,
  )

  if (exists) {
    notifyWarning('该先修关系已经存在')
    return
  }

  saving.value = true

  try {
    await addKnowledgeDependency(id, pointId, prerequisiteId)
    notifySuccess('先修关系已保存')
    dependencyForm.value = {
      pointId: '',
      prerequisiteId: '',
    }
    await refreshKnowledgeMap()
  } catch (error) {
    notifyError(error)
  } finally {
    saving.value = false
  }
}

async function deleteDependency(
  edge: KnowledgeMapDependency,
): Promise<void> {
  const id = courseId.value
  if (!id || saving.value) return

  try {
    await ElMessageBox.confirm(
      '确定解除该先修关系吗？'
      + '解除后不会修改学生已经生成的学习路径。',
      '解除先修关系',
      {
        type: 'warning',
        confirmButtonText: '确认解除',
        cancelButtonText: '取消',
      },
    )
  } catch {
    return
  }

  if (courseId.value !== id || saving.value) return

  saving.value = true

  try {
    await removeKnowledgeDependency(
      id,
      edge.pointId,
      edge.prerequisiteId,
    )
    notifySuccess('先修关系已解除')
    await refreshKnowledgeMap()
  } catch (error) {
    notifyError(error)
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <BaseCard
    title="课程知识点体系"
    subtitle="维护课程章节知识点、难度、先修关系及知识地图"
  >
    <div
      v-loading="loading"
      class="knowledge-panel"
    >
      <div class="knowledge-toolbar">
        <el-select
          v-model="courseId"
          placeholder="请选择需要管理的课程"
          :disabled="saving"
          class="course-selector"
        >
          <el-option
            v-for="course in courses"
            :key="course.id"
            :label="course.name"
            :value="String(course.id)"
          />
        </el-select>

        <el-button
          :disabled="!courseId || saving || loading"
          @click="refreshKnowledgeMap"
        >
          刷新
        </el-button>
      </div>

      <el-empty
        v-if="!courseId"
        description="暂无可管理的课程"
      />

      <el-tabs
        v-else
        v-model="activeTab"
      >
        <el-tab-pane
          label="知识点维护"
          name="points"
        >
          <div class="section-toolbar">
            <span class="muted">
              共 {{ points.length }} 个知识点
              · {{ chapters.length }} 个章节
            </span>
            <el-button
              type="primary"
              :disabled="saving || !mapData || loading"
              @click="openCreatePoint()"
            >
              新增知识点
            </el-button>
          </div>

          <el-alert
            v-if="chapters.length === 0"
            title="当前课程尚未创建章节，可先在知识库的资源分类功能中创建课程章节。"
            type="info"
            :closable="false"
            class="section-alert"
          />

          <el-table
            :data="treeRows"
            row-key="key"
            default-expand-all
            :tree-props="{ children: 'children' }"
            empty-text="当前课程尚未配置知识点"
          >
            <el-table-column
              label="章节 / 知识点"
              min-width="210"
            >
              <template #default="{ row }">
                <strong v-if="row.kind === 'chapter'">
                  {{ row.label }}
                </strong>
                <span v-else>
                  {{ row.label }}
                </span>
              </template>
            </el-table-column>

            <el-table-column
              label="难度"
              width="90"
            >
              <template #default="{ row }">
                <el-tag
                  v-if="row.point"
                  :type="difficultyTagType(row.point.difficulty)"
                  size="small"
                >
                  {{ knowledgeDifficultyLabel(row.point.difficulty) }}
                </el-tag>
                <span v-else>—</span>
              </template>
            </el-table-column>

            <el-table-column
              label="学习时间"
              width="100"
            >
              <template #default="{ row }">
                <span v-if="row.point">
                  {{ row.point.estimatedMinutes }} 分钟
                </span>
                <span v-else>—</span>
              </template>
            </el-table-column>

            <el-table-column
              label="关键词"
              min-width="150"
              show-overflow-tooltip
            >
              <template #default="{ row }">
                {{ row.point?.matchKeywords || '—' }}
              </template>
            </el-table-column>

            <el-table-column
              label="排序"
              width="70"
            >
              <template #default="{ row }">
                {{ row.point?.sortOrder ?? '—' }}
              </template>
            </el-table-column>

            <el-table-column
              label="操作"
              width="190"
              fixed="right"
            >
              <template #default="{ row }">
                <div class="point-actions">
                  <el-button
                    v-if="row.kind === 'chapter'"
                    type="primary"
                    link
                    size="small"
                    :disabled="saving || loading"
                    @click="openCreatePoint(row.chapterId)"
                  >
                    添加知识点
                  </el-button>

                  <template v-if="row.point">
                    <el-button
                      type="primary"
                      link
                      size="small"
                      :disabled="saving || loading"
                      @click="
                        openCreatePoint(
                          row.point.chapterId,
                          row.point.id,
                        )
                      "
                    >
                      添加子级
                    </el-button>
                    <el-button
                      type="primary"
                      link
                      size="small"
                      :disabled="saving || loading"
                      @click="openEditPoint(row.point)"
                    >
                      编辑
                    </el-button>
                    <el-button
                      type="danger"
                      link
                      size="small"
                      :disabled="saving || loading"
                      @click="removePoint(row.point)"
                    >
                      删除
                    </el-button>
                  </template>
                </div>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>

        <el-tab-pane
          label="先修关系"
          name="dependencies"
        >
          <el-alert
            type="info"
            :closable="false"
            title="先修关系表示学习顺序，例如学习HashMap之前需要先掌握数组和链表。它与知识点父子层级是不同的关系。"
            class="section-alert"
          />

          <el-form
            label-position="top"
            :disabled="saving || loading"
          >
            <div class="dependency-form">
              <el-form-item
                label="要学习的知识点"
                required
              >
                <el-select
                  v-model="dependencyForm.pointId"
                  filterable
                  placeholder="选择知识点"
                  style="width: 100%"
                >
                  <el-option
                    v-for="point in points"
                    :key="point.id"
                    :label="pointLabel(point)"
                    :value="point.id"
                  />
                </el-select>
              </el-form-item>

              <el-form-item
                label="必须先学的知识点"
                required
              >
                <el-select
                  v-model="dependencyForm.prerequisiteId"
                  filterable
                  placeholder="选择先修知识点"
                  style="width: 100%"
                >
                  <el-option
                    v-for="point in points"
                    :key="point.id"
                    :label="pointLabel(point)"
                    :value="point.id"
                    :disabled="point.id === dependencyForm.pointId"
                  />
                </el-select>
              </el-form-item>
            </div>

            <el-button
              type="primary"
              :loading="saving"
              :disabled="
                points.length < 2
                || !dependencyForm.pointId
                || !dependencyForm.prerequisiteId
                || dependencyForm.pointId
                  === dependencyForm.prerequisiteId
              "
              @click="createDependency"
            >
              保存先修关系
            </el-button>
          </el-form>

          <el-divider content-position="left">
            已配置的先修关系
          </el-divider>

          <el-table
            :data="dependencyRows"
            empty-text="当前课程尚未配置先修关系"
          >
            <el-table-column
              prop="prerequisiteTitle"
              label="先修知识点"
              min-width="150"
            />
            <el-table-column
              label="学习顺序"
              width="100"
            >
              <template #default>
                →
              </template>
            </el-table-column>
            <el-table-column
              prop="pointTitle"
              label="后续知识点"
              min-width="150"
            />
            <el-table-column
              label="操作"
              width="100"
            >
              <template #default="{ row }">
                <el-button
                  type="danger"
                  link
                  :disabled="saving || loading"
                  @click="deleteDependency(row)"
                >
                  解除关系
                </el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>

        <el-tab-pane
          label="知识地图"
          name="map"
          lazy
        >
          <CourseKnowledgeMapView
            v-if="mapData"
            :data="mapData"
          />
          <el-empty
            v-else
            description="暂无知识地图数据"
          />
        </el-tab-pane>
      </el-tabs>
    </div>

    <el-dialog
      v-model="dialogVisible"
      :title="editingPointId ? '编辑知识点' : '新增知识点'"
      width="min(620px, 94vw)"
      append-to-body
      :close-on-click-modal="!saving"
      :close-on-press-escape="!saving"
      :show-close="!saving"
    >
      <el-form
        label-position="top"
        :disabled="saving"
      >
        <el-form-item
          label="知识点名称"
          required
        >
          <el-input
            v-model="pointForm.title"
            maxlength="200"
            show-word-limit
            placeholder="例如：HashMap底层原理"
          />
        </el-form-item>

        <div class="point-form-grid">
          <el-form-item label="所属章节">
            <el-select
              :model-value="pointForm.chapterId"
              placeholder="未分类知识点"
              clearable
              filterable
              :disabled="editingHasChildren"
              style="width: 100%"
              @change="onChapterChange"
            >
              <el-option
                v-for="chapter in chapters"
                :key="chapter.id"
                :label="chapter.name"
                :value="chapter.id"
              />
            </el-select>
            <div
              v-if="editingHasChildren"
              class="form-hint"
            >
              包含子知识点，暂不能直接跨章节移动。
            </div>
          </el-form-item>

          <el-form-item label="父级知识点">
            <el-select
              :model-value="pointForm.parentId"
              placeholder="根知识点"
              clearable
              filterable
              style="width: 100%"
              @change="onParentChange"
            >
              <el-option
                v-for="point in parentOptions"
                :key="point.id"
                :label="pointLabel(point)"
                :value="point.id"
              />
            </el-select>
          </el-form-item>
        </div>

        <el-form-item
          label="知识点难度"
          required
        >
          <el-radio-group v-model="pointForm.difficulty">
            <el-radio-button :value="1">基础</el-radio-button>
            <el-radio-button :value="2">中等</el-radio-button>
            <el-radio-button :value="3">进阶</el-radio-button>
          </el-radio-group>
        </el-form-item>

        <div class="point-form-grid">
          <el-form-item
            label="预计学习时间（分钟）"
            required
          >
            <el-input-number
              v-model="pointForm.estimatedMinutes"
              :min="1"
              :max="10080"
              :precision="0"
              style="width: 100%"
            />
          </el-form-item>
          <el-form-item label="默认排序">
            <el-input-number
              v-model="pointForm.sortOrder"
              :min="0"
              :max="2147483647"
              :precision="0"
              style="width: 100%"
            />
          </el-form-item>
        </div>

        <el-form-item label="匹配关键词">
          <el-input
            v-model="pointForm.matchKeywords"
            type="textarea"
            :rows="3"
            maxlength="500"
            show-word-limit
            placeholder="例如：HashMap,哈希冲突,扩容,红黑树"
          />
          <div class="form-hint">
            多个关键词用逗号分隔。
            用于学生提问匹配、薄弱知识点分析，
            以及后续资料切片知识点推荐。
          </div>
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button
          :disabled="saving"
          @click="dialogVisible = false"
        >
          取消
        </el-button>
        <el-button
          type="primary"
          :loading="saving"
          @click="savePoint"
        >
          {{ editingPointId ? '保存修改' : '创建知识点' }}
        </el-button>
      </template>
    </el-dialog>
  </BaseCard>
</template>

<style scoped>
.knowledge-panel {
  display: grid;
  gap: 16px;
}

.knowledge-toolbar {
  display: flex;
  align-items: center;
  gap: 10px;
}

.course-selector {
  flex: 1;
  max-width: 420px;
}

.section-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 12px;
  margin-bottom: 16px;
}

.section-alert {
  margin-bottom: 16px;
}

.muted {
  font-size: 13px;
  color: var(--sc-text-secondary);
}

.point-actions {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 6px 9px;
}

.point-actions :deep(.el-button) {
  margin-left: 0;
}

.point-form-grid,
.dependency-form {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 16px;
}

.form-hint {
  margin-top: 6px;
  font-size: 12px;
  line-height: 1.6;
  color: var(--sc-text-secondary);
}

@media (max-width: 640px) {
  .point-form-grid,
  .dependency-form {
    grid-template-columns: 1fr;
    gap: 0;
  }
}
</style>
