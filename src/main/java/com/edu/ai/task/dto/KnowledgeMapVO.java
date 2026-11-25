package com.edu.ai.task.dto;

import java.util.List;

/**
 * 课程知识地图。
 */
public record KnowledgeMapVO(

        String courseId,

        List<ChapterNode> chapters,

        List<PointNode> points,

        List<DependencyEdge> dependencies,

        Statistics statistics

) {

    /**
     * 章节节点。
     */
    public record ChapterNode(

            String id,

            String name,

            int sortOrder

    ) {}

    /**
     * 知识点节点。
     */
    public record PointNode(

            String id,

            String title,

            String chapterId,

            String parentId,

            int difficulty,

            int estimatedMinutes,

            int sortOrder,

            String matchKeywords

    ) {}

    /**
     * 先修依赖关系。
     *
     * prerequisiteId -> pointId
     *
     * 表示学习pointId之前，
     * 应当先学习prerequisiteId。
     */
    public record DependencyEdge(

            String pointId,

            String prerequisiteId

    ) {}

    /**
     * 知识地图统计信息。
     */
    public record Statistics(

            int chapterCount,

            int pointCount,

            int dependencyCount,

            int unclassifiedCount,

            int basicCount,

            int intermediateCount,

            int advancedCount,

            long totalEstimatedMinutes

    ) {}
}
