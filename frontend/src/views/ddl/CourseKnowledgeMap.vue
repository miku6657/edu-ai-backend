<script setup lang="ts">
import { computed, ref, watch } from 'vue'

import {
  knowledgeDifficultyLabel,
} from '@/api/learning-path'
import type {
  CourseKnowledgeMap,
  KnowledgeMapPoint,
} from '@/api/learning-path'

const props = defineProps<{
  data: CourseKnowledgeMap
}>()

interface MapNode {
  key: string
  label: string
  kind: 'course' | 'chapter' | 'point'
  difficulty?: number
  point?: KnowledgeMapPoint
  children: MapNode[]
}

interface LayoutNode {
  node: MapNode
  x: number
  y: number
}

interface MapEdge {
  key: string
  from: LayoutNode
  to: LayoutNode
}

const selectedPointId = ref('')
const showDependencies = ref(true)
const zoom = ref(100)

const NODE_WIDTH = 150
const NODE_HEIGHT = 48
const COLUMN_GAP = 205
const ROW_GAP = 100
const PADDING = 25

const difficultyColors: Record<number, string> = {
  1: '#16a34a',
  2: '#2563eb',
  3: '#d97706',
}

const selectedPoint = computed(() =>
  props.data.points.find(
    point => point.id === selectedPointId.value,
  ) ?? null,
)

const selectedChapter = computed(() =>
  props.data.chapters.find(
    chapter => chapter.id === selectedPoint.value?.chapterId,
  ) ?? null,
)

const selectedParent = computed(() =>
  props.data.points.find(
    point => point.id === selectedPoint.value?.parentId,
  ) ?? null,
)

const selectedPrerequisites = computed(() => {
  const ids = new Set(
    props.data.dependencies
      .filter(edge =>
        edge.pointId === selectedPointId.value,
      )
      .map(edge => edge.prerequisiteId),
  )

  return props.data.points.filter(
    point => ids.has(point.id),
  )
})

function sortPoints(
  a: KnowledgeMapPoint,
  b: KnowledgeMapPoint,
): number {
  return a.sortOrder - b.sortOrder
    || a.id.localeCompare(b.id)
}

/**
 * 将后端返回的扁平数据转换为层级树。
 */
const tree = computed<MapNode>(() => {
  const points = [...props.data.points].sort(sortPoints)
  const childrenMap = new Map<string, KnowledgeMapPoint[]>()
  const pointMap = new Map(
    points.map(point => [point.id, point]),
  )

  for (const point of points) {
    if (!point.parentId) continue

    const parent = pointMap.get(point.parentId)

    if (
      !parent
      || parent.chapterId !== point.chapterId
      || parent.id === point.id
    ) {
      continue
    }

    const children = childrenMap.get(point.parentId) ?? []
    children.push(point)
    childrenMap.set(point.parentId, children)
  }

  const visited = new Set<string>()

  function buildPoint(point: KnowledgeMapPoint): MapNode {
    visited.add(point.id)

    return {
      key: `point:${point.id}`,
      label: point.title,
      kind: 'point',
      difficulty: point.difficulty,
      point,
      children: (childrenMap.get(point.id) ?? [])
        .filter(child => !visited.has(child.id))
        .map(buildPoint),
    }
  }

  const chapters: MapNode[] = []
  const knownChapterIds = new Set(
    props.data.chapters.map(chapter => chapter.id),
  )

  for (const chapter of props.data.chapters) {
    const chapterPoints = points.filter(
      point => point.chapterId === chapter.id,
    )

    const roots = chapterPoints.filter(point => {
      const parent = point.parentId
        ? pointMap.get(point.parentId)
        : null

      return !parent
        || parent.chapterId !== point.chapterId
        || parent.id === point.id
    })

    const children = roots
      .filter(point => !visited.has(point.id))
      .map(buildPoint)

    for (const point of chapterPoints) {
      if (!visited.has(point.id)) {
        children.push(buildPoint(point))
      }
    }

    chapters.push({
      key: `chapter:${chapter.id}`,
      label: chapter.name,
      kind: 'chapter',
      children,
    })
  }

  const unclassified = points.filter(
    point =>
      !point.chapterId
      || !knownChapterIds.has(point.chapterId),
  )

  if (unclassified.length > 0) {
    const roots = unclassified.filter(point => {
      const parent = point.parentId
        ? pointMap.get(point.parentId)
        : null

      return !parent
        || parent.chapterId !== point.chapterId
        || parent.id === point.id
    })

    const children = roots
      .filter(point => !visited.has(point.id))
      .map(buildPoint)

    for (const point of unclassified) {
      if (!visited.has(point.id)) {
        children.push(buildPoint(point))
      }
    }

    chapters.push({
      key: 'chapter:unclassified',
      label: '未分类知识点',
      kind: 'chapter',
      children,
    })
  }

  return {
    key: `course:${props.data.courseId}`,
    label: '课程知识体系',
    kind: 'course',
    children: chapters,
  }
})

/**
 * 根据叶子节点数量自动分配地图坐标。
 */
const layout = computed(() => {
  const nodes: LayoutNode[] = []
  const edges: MapEdge[] = []
  let leafIndex = 0
  let maxDepth = 0

  function visit(
    node: MapNode,
    depth: number,
  ): LayoutNode {
    maxDepth = Math.max(maxDepth, depth)

    const childLayouts = node.children.map(
      child => visit(child, depth + 1),
    )

    let x: number

    if (childLayouts.length === 0) {
      x =
        PADDING
        + NODE_WIDTH / 2
        + leafIndex * COLUMN_GAP
      leafIndex++
    } else {
      const first = childLayouts[0]
      const last = childLayouts[childLayouts.length - 1]

      x = first && last
        ? (first.x + last.x) / 2
        : PADDING + NODE_WIDTH / 2
    }

    const item: LayoutNode = {
      node,
      x,
      y:
        PADDING
        + NODE_HEIGHT / 2
        + depth * ROW_GAP,
    }

    nodes.push(item)

    for (const child of childLayouts) {
      edges.push({
        key: `${item.node.key}-${child.node.key}`,
        from: item,
        to: child,
      })
    }

    return item
  }

  visit(tree.value, 0)

  const nodeMap = new Map(
    nodes.map(node => [
      node.node.key,
      node,
    ]),
  )

  const dependencyEdges: MapEdge[] = []

  for (const edge of props.data.dependencies) {
    const from = nodeMap.get(
      `point:${edge.prerequisiteId}`,
    )
    const to = nodeMap.get(
      `point:${edge.pointId}`,
    )

    if (from && to) {
      dependencyEdges.push({
        key: `${edge.prerequisiteId}-${edge.pointId}`,
        from,
        to,
      })
    }
  }

  return {
    nodes,
    edges,
    dependencyEdges,
    width: Math.max(
      500,
      PADDING * 2
        + NODE_WIDTH
        + Math.max(0, leafIndex - 1) * COLUMN_GAP,
    ),
    height:
      PADDING * 2
      + NODE_HEIGHT
      + maxDepth * ROW_GAP,
  }
})

function hierarchyPath(edge: MapEdge): string {
  const { from, to } = edge
  const startY = from.y + NODE_HEIGHT / 2
  const endY = to.y - NODE_HEIGHT / 2
  const middleY = (startY + endY) / 2

  return [
    `M ${from.x} ${startY}`,
    `V ${middleY}`,
    `H ${to.x}`,
    `V ${endY}`,
  ].join(' ')
}

function dependencyPath(edge: MapEdge): string {
  const { from, to } = edge
  const rightward = to.x >= from.x
  const startX =
    from.x + (rightward ? 1 : -1) * NODE_WIDTH / 2
  const endX =
    to.x - (rightward ? 1 : -1) * NODE_WIDTH / 2
  const bend = Math.max(40, Math.abs(to.y - from.y) / 2)
  const controlX = (startX + endX) / 2

  return [
    `M ${startX} ${from.y}`,
    `C ${controlX} ${from.y - bend}`,
    `${controlX} ${to.y - bend}`,
    `${endX} ${to.y}`,
  ].join(' ')
}

function nodeColor(node: MapNode): string {
  if (node.kind !== 'point') {
    return '#64748b'
  }

  return difficultyColors[node.difficulty ?? 2]
    ?? '#64748b'
}

function shortLabel(label: string): string {
  return label.length > 13
    ? `${label.slice(0, 12)}…`
    : label
}

function selectNode(node: MapNode): void {
  if (node.point) {
    selectedPointId.value = node.point.id
  }
}

watch(
  () => props.data.courseId,
  () => {
    selectedPointId.value = ''
    zoom.value = 100
  },
)

watch(
  () => props.data.points,
  points => {
    if (
      !points.some(
        point => point.id === selectedPointId.value,
      )
    ) {
      selectedPointId.value = ''
    }
  },
)
</script>

<template>
  <div class="knowledge-map">
    <div class="map-statistics">
      <div class="map-statistic">
        <span>课程章节</span>
        <strong>{{ data.statistics.chapterCount }}</strong>
      </div>
      <div class="map-statistic">
        <span>知识点总数</span>
        <strong>{{ data.statistics.pointCount }}</strong>
      </div>
      <div class="map-statistic">
        <span>先修关系</span>
        <strong>{{ data.statistics.dependencyCount }}</strong>
      </div>
      <div class="map-statistic">
        <span>预计学时</span>
        <strong>
          {{ (data.statistics.totalEstimatedMinutes / 60).toFixed(1) }}
          小时
        </strong>
      </div>
    </div>

    <div class="map-toolbar">
      <div class="map-legend">
        <span>
          <i style="background: #16a34a" />
          基础
        </span>
        <span>
          <i style="background: #2563eb" />
          中等
        </span>
        <span>
          <i style="background: #d97706" />
          进阶
        </span>
      </div>

      <el-checkbox v-model="showDependencies">
        显示先修关系
      </el-checkbox>

      <div class="map-zoom">
        <el-button
          size="small"
          :disabled="zoom <= 50"
          @click="zoom = Math.max(50, zoom - 10)"
        >
          −
        </el-button>
        <span>{{ zoom }}%</span>
        <el-button
          size="small"
          :disabled="zoom >= 200"
          @click="zoom = Math.min(200, zoom + 10)"
        >
          +
        </el-button>
        <el-button
          size="small"
          @click="zoom = 100"
        >
          重置
        </el-button>
      </div>
    </div>

    <el-empty
      v-if="data.points.length === 0"
      description="当前课程尚未建立知识点体系"
    />

    <div
      v-else
      class="map-viewport"
    >
      <svg
        :width="layout.width * zoom / 100"
        :height="layout.height * zoom / 100"
        :viewBox="`0 0 ${layout.width} ${layout.height}`"
      >
        <defs>
          <marker
            id="knowledge-dependency-arrow"
            markerWidth="8"
            markerHeight="8"
            refX="7"
            refY="4"
            orient="auto"
          >
            <path
              d="M 0 0 L 8 4 L 0 8 Z"
              fill="#d97706"
            />
          </marker>
        </defs>

        <path
          v-for="edge in layout.edges"
          :key="edge.key"
          :d="hierarchyPath(edge)"
          fill="none"
          stroke="#94a3b8"
          stroke-width="1.5"
        />

        <g v-if="showDependencies">
          <path
            v-for="edge in layout.dependencyEdges"
            :key="`dependency:${edge.key}`"
            :d="dependencyPath(edge)"
            fill="none"
            stroke="#d97706"
            stroke-width="1.5"
            stroke-dasharray="6 4"
            marker-end="url(#knowledge-dependency-arrow)"
          />
        </g>

        <g
          v-for="item in layout.nodes"
          :key="item.node.key"
          :transform="`translate(${item.x}, ${item.y})`"
          :class="{
            'map-clickable': item.node.kind === 'point',
          }"
          @click="selectNode(item.node)"
        >
          <title>{{ item.node.label }}</title>

          <rect
            :x="-NODE_WIDTH / 2"
            :y="-NODE_HEIGHT / 2"
            :width="NODE_WIDTH"
            :height="NODE_HEIGHT"
            rx="9"
            :fill="nodeColor(item.node)"
            fill-opacity="0.08"
            :stroke="nodeColor(item.node)"
            :stroke-width="
              item.node.point?.id === selectedPointId
                ? 3
                : 1.3
            "
          />

          <text
            x="0"
            :y="item.node.kind === 'point' ? -2 : 5"
            text-anchor="middle"
            class="map-node-label"
          >
            {{ shortLabel(item.node.label) }}
          </text>

          <text
            v-if="item.node.kind === 'point'"
            x="0"
            y="14"
            text-anchor="middle"
            font-size="10"
            :fill="nodeColor(item.node)"
          >
            {{ knowledgeDifficultyLabel(item.node.difficulty ?? 2) }}
            · {{ item.node.point?.estimatedMinutes }} 分钟
          </text>
        </g>
      </svg>
    </div>

    <div class="map-hint">
      实线表示知识点层级，橙色虚线箭头表示先修关系。
      点击知识点可查看详细信息。
    </div>

    <el-select
      v-if="data.points.length"
      v-model="selectedPointId"
      placeholder="选择知识点查看详情"
      filterable
      clearable
      style="width: 100%"
    >
      <el-option
        v-for="point in data.points"
        :key="point.id"
        :value="point.id"
        :label="point.title"
      />
    </el-select>

    <el-descriptions
      v-if="selectedPoint"
      :title="selectedPoint.title"
      :column="2"
      border
    >
      <el-descriptions-item label="所属章节">
        {{ selectedChapter?.name ?? '未分类' }}
      </el-descriptions-item>
      <el-descriptions-item label="父级知识点">
        {{ selectedParent?.title ?? '无' }}
      </el-descriptions-item>
      <el-descriptions-item label="难度">
        {{ knowledgeDifficultyLabel(selectedPoint.difficulty) }}
      </el-descriptions-item>
      <el-descriptions-item label="预计学习时间">
        {{ selectedPoint.estimatedMinutes }} 分钟
      </el-descriptions-item>
      <el-descriptions-item
        label="匹配关键词"
        :span="2"
      >
        {{ selectedPoint.matchKeywords || '未配置' }}
      </el-descriptions-item>
      <el-descriptions-item
        label="先修知识点"
        :span="2"
      >
        {{
          selectedPrerequisites
            .map(point => point.title)
            .join('、')
          || '无'
        }}
      </el-descriptions-item>
    </el-descriptions>
  </div>
</template>

<style scoped>
.knowledge-map {
  display: grid;
  gap: 16px;
}

.map-statistics {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
}

.map-statistic {
  display: grid;
  gap: 8px;
  padding: 14px;
  border: 1px solid var(--sc-border);
  border-radius: 9px;
}

.map-statistic span {
  font-size: 12px;
  color: var(--sc-text-secondary);
}

.map-statistic strong {
  font-size: 21px;
}

.map-toolbar {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 14px;
}

.map-legend {
  display: flex;
  gap: 12px;
  flex-wrap: wrap;
}

.map-legend span {
  display: flex;
  align-items: center;
  gap: 5px;
  font-size: 12px;
}

.map-legend i {
  display: inline-block;
  width: 9px;
  height: 9px;
  border-radius: 50%;
}

.map-zoom {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-left: auto;
  font-size: 12px;
}

.map-zoom :deep(.el-button) {
  margin-left: 0;
}

.map-viewport {
  overflow: auto;
  max-height: 650px;
  min-height: 240px;
  border: 1px solid var(--sc-border);
  border-radius: 10px;
  background: var(--sc-surface);
}

.map-viewport svg {
  display: block;
}

.map-clickable {
  cursor: pointer;
}

.map-clickable:hover rect {
  fill-opacity: 0.18;
}

.map-node-label {
  font-size: 12px;
  font-weight: 500;
  fill: currentColor;
}

.map-hint {
  font-size: 12px;
  color: var(--sc-text-secondary);
  line-height: 1.6;
}

@media (max-width: 640px) {
  .map-statistics {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .map-zoom {
    margin-left: 0;
  }
}
</style>
