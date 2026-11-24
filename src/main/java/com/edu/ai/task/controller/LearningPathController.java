package com.edu.ai.task.controller;

import com.edu.ai.common.BusinessException;
import com.edu.ai.common.Result;
import com.edu.ai.task.service.LearningPathService;
import com.edu.ai.task.service.LearningPathService.PathResult;

import jakarta.validation.constraints.NotNull;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/learning-paths")
public class LearningPathController {

    private final LearningPathService service;

    public LearningPathController(
            LearningPathService service) {
        this.service = service;
    }

    public record PointRequest(
            String title,
            Integer estimatedMinutes,
            Integer sortOrder
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
            int sortOrder
    ) {}

    private Long userId(Authentication auth) {
        if (auth == null
                || !(auth.getPrincipal()
                instanceof Long id)) {
            throw new BusinessException(
                    401, "登录状态无效");
        }
        return id;
    }

    // 教师添加课程知识点
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
                request.sortOrder()
        );

        return Result.success(
                Map.of("pointId", id.toString())
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

    // 教师及课程成员查看知识点
    @GetMapping("/courses/{courseId}/points")
    public Result<List<PointVO>> listPoints(
            @PathVariable Long courseId,
            Authentication auth) {

        return Result.success(
                service.listPoints(courseId, userId(auth))
                        .stream()
                        .map(point -> new PointVO(
                                point.id().toString(),
                                point.courseId().toString(),
                                point.title(),
                                point.estimatedMinutes(),
                                point.sortOrder()
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
}
