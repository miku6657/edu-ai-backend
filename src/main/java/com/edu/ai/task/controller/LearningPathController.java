package com.edu.ai.task.controller;

import com.edu.ai.common.BusinessException;
import com.edu.ai.common.Result;
import com.edu.ai.course.service.CoursePermissionService;
import com.edu.ai.task.dto.KnowledgeMapVO;
import com.edu.ai.task.service.LearningPathService;
import com.edu.ai.task.service.LearningPathService.PathProgress;
import com.edu.ai.task.service.LearningPathService.PathResult;
import com.edu.ai.task.service.QuestionKnowledgeService;

import jakarta.validation.constraints.NotNull;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/learning-paths")
public class LearningPathController {

    private final LearningPathService service;
    private final QuestionKnowledgeService questionKnowledgeService;
    private final CoursePermissionService coursePermissionService;

    public LearningPathController(
            LearningPathService service,
            QuestionKnowledgeService questionKnowledgeService,
            CoursePermissionService coursePermissionService) {
        this.service = service;
        this.questionKnowledgeService = questionKnowledgeService;
        this.coursePermissionService = coursePermissionService;
    }

    public record PointRequest(
            String title,
            Integer estimatedMinutes,
            Integer sortOrder,
            String matchKeywords,
            Long chapterId,
            Long parentId,
            Integer difficulty
    ) {}

    public record DependencyRequest(
            @NotNull Long pointId,
            @NotNull Long prerequisiteId
    ) {}

    public record MasteryRequest(
            @NotNull Integer level
    ) {}

    public record PointVO(
            String id,
            String courseId,
            String title,
            int estimatedMinutes,
            int sortOrder,
            String matchKeywords,
            String chapterId,
            String parentId,
            int difficulty
    ) {}

    public record KeywordsRequest(String matchKeywords) {}

    private Long userId(Authentication auth) {
        if (auth == null
                || !(auth.getPrincipal()
                instanceof Long id)) {
            throw new BusinessException(
                    401, "登录状态无效");
        }
        return id;
    }

    /**
     * 教师创建课程知识点。
     */
    @PostMapping("/courses/{courseId}/points")
    public Result<Map<String, String>> addPoint(
            @PathVariable Long courseId,
            @RequestBody PointRequest request,
            Authentication auth) {

        Long id = service.addPoint(
                courseId,
                userId(auth),
                request.title(),
                request.estimatedMinutes(),
                request.sortOrder(),
                request.matchKeywords(),
                request.chapterId(),
                request.parentId(),
                request.difficulty()
        );

        return Result.success(
                Map.of("pointId", id.toString())
        );
    }

    /**
     * 教师修改知识点完整信息。
     */
    @PutMapping("/courses/{courseId}/points/{pointId}")
    public Result<String> updatePoint(
            @PathVariable Long courseId,
            @PathVariable Long pointId,
            @RequestBody PointRequest request,
            Authentication auth) {

        service.updatePoint(
                courseId,
                pointId,
                userId(auth),
                request.title(),
                request.estimatedMinutes(),
                request.sortOrder(),
                request.matchKeywords(),
                request.chapterId(),
                request.parentId(),
                request.difficulty()
        );

        return Result.success(
                "知识点修改成功"
        );
    }

    /**
     * 教师安全删除课程知识点。
     */
    @DeleteMapping("/courses/{courseId}/points/{pointId}")
    public Result<String> deletePoint(
            @PathVariable Long courseId,
            @PathVariable Long pointId,
            Authentication auth) {

        service.deletePoint(
                courseId,
                pointId,
                userId(auth)
        );

        return Result.success(
                "知识点删除成功"
        );
    }

    /**
     * 教师解除知识点先修依赖。
     */
    @DeleteMapping(
            "/courses/{courseId}/dependencies/"
            + "{pointId}/{prerequisiteId}"
    )
    public Result<String> removeDependency(
            @PathVariable Long courseId,
            @PathVariable Long pointId,
            @PathVariable Long prerequisiteId,
            Authentication auth) {

        service.removeDependency(
                courseId,
                userId(auth),
                pointId,
                prerequisiteId
        );

        return Result.success(
                "先修依赖关系已解除"
        );
    }

    /**
     * 查询课程知识地图。
     */
    @GetMapping("/courses/{courseId}/knowledge-map")
    public Result<KnowledgeMapVO> getKnowledgeMap(
            @PathVariable Long courseId,
            Authentication auth) {

        return Result.success(
                service.getKnowledgeMap(
                        courseId,
                        userId(auth)
                )
        );
    }

    // 教师添加先修依赖
    @PostMapping("/courses/{courseId}/dependencies")
    public Result<String> addDependency(
            @PathVariable Long courseId,
            @RequestBody DependencyRequest request,
            Authentication auth) {

        if (request.pointId() == null
                || request.prerequisiteId() == null) {
            throw new BusinessException(
                    400, "知识点ID不能为空");
        }

        service.addDependency(
                courseId,
                userId(auth),
                request.pointId(),
                request.prerequisiteId()
        );

        return Result.success("先修关系已保存");
    }

    // 教师修改知识点匹配关键词
    @PutMapping("/courses/{courseId}/points/{pointId}/keywords")
    public Result<String> updatePointKeywords(
            @PathVariable Long courseId,
            @PathVariable Long pointId,
            @RequestBody KeywordsRequest request,
            Authentication auth) {

        service.updatePointKeywords(
                courseId,
                pointId,
                userId(auth),
                request.matchKeywords()
        );

        return Result.success("知识点匹配关键词已更新");
    }

    /**
     * 课程成员查询知识点体系。
     */
    @GetMapping("/courses/{courseId}/points")
    public Result<List<PointVO>> listPoints(
            @PathVariable Long courseId,
            Authentication auth) {

        return Result.success(
                service.listPoints(
                        courseId,
                        userId(auth)
                )
                .stream()
                .map(point -> new PointVO(

                        point.id().toString(),

                        point.courseId().toString(),

                        point.title(),

                        point.estimatedMinutes(),

                        point.sortOrder(),

                        point.matchKeywords(),

                        point.chapterId() == null
                                ? null
                                : point.chapterId().toString(),

                        point.parentId() == null
                                ? null
                                : point.parentId().toString(),

                        point.difficulty()
                ))
                .toList()
        );
    }

    // 学生查看自己的掌握程度自评
    @GetMapping("/courses/{courseId}/mastery")
    public Result<Map<String, Integer>> myMastery(
            @PathVariable Long courseId,
            Authentication auth) {

        return Result.success(
                service.listMyMastery(
                        courseId,
                        userId(auth)
                )
        );
    }

    // 学生查看自己的知识点薄弱统计
    @GetMapping("/courses/{courseId}/weak-points")
    @PreAuthorize("hasRole('STUDENT')")
    public Result<List<QuestionKnowledgeService.WeakPoint>> weakPoints(
            @PathVariable Long courseId,
            Authentication auth) {

        Long studentId = userId(auth);

        coursePermissionService.requireActiveRead(
                courseId,
                studentId
        );

        return Result.success(
                questionKnowledgeService.listWeakPoints(
                        courseId,
                        studentId
                )
        );
    }

    // 学生标记个人掌握程度
    @PutMapping("/courses/{courseId}/points/{pointId}/mastery")
    public Result<String> setMastery(
            @PathVariable Long courseId,
            @PathVariable Long pointId,
            @RequestBody MasteryRequest request,
            Authentication auth) {

        if (request.level() == null) {
            throw new BusinessException(
                    400, "掌握程度不能为空");
        }

        service.setMastery(
                courseId,
                pointId,
                userId(auth),
                request.level()
        );

        return Result.success("掌握程度已更新");
    }

    // 根据已确认的学习目标生成路径
    @PostMapping("/goals/{goalId}/generate")
    public Result<PathResult> generate(
            @PathVariable Long goalId,
            Authentication auth) {

        return Result.success(
                service.generate(goalId, userId(auth))
        );
    }

    /**
     * 查询目标已有路径，不触发生成。
     */
    @GetMapping("/goals/{goalId}")
    public Result<PathResult> getByGoal(
            @PathVariable Long goalId,
            Authentication auth) {

        return Result.success(
                service.getByGoal(
                        goalId,
                        userId(auth)
                )
        );
    }

    // 学生查看自己的学习路径
    @GetMapping("/{pathId}")
    public Result<PathResult> getPath(
            @PathVariable Long pathId,
            Authentication auth) {

        return Result.success(
                service.getPath(pathId, userId(auth))
        );
    }

    /**
     * 学生查询个人学习路径完成进度。
     *
     * GET /api/v1/learning-paths/{pathId}/progress
     */
    @GetMapping("/{pathId}/progress")
    public Result<PathProgress> getProgress(
            @PathVariable Long pathId,
            Authentication auth) {

        return Result.success(
                service.getProgress(
                        pathId,
                        userId(auth)
                )
        );
    }
}
