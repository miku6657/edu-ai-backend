package com.edu.ai.learning.controller;

import com.edu.ai.common.BusinessException;
import com.edu.ai.common.Result;
import com.edu.ai.learning.dto.LearningReportCommentRequest;
import com.edu.ai.learning.dto.StudentLearningReportDetailVO;
import com.edu.ai.learning.dto.StudentLearningReportDetailVO.CommentVO;
import com.edu.ai.learning.dto.StudentLearningReportVO;
import com.edu.ai.learning.service.StudentLearningReportService;

import jakarta.validation.Valid;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * F6-03：教师查看课程学习报告并添加文字批注。
 */
@RestController
@RequestMapping("/api/v1/teacher/courses/{courseId}/learning-reports")
@PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
public class TeacherLearningReportController {

    private final StudentLearningReportService service;

    public TeacherLearningReportController(
            StudentLearningReportService service) {
        this.service = service;
    }

    /**
     * 查询当前教师管理课程中的学生报告。
     */
    @GetMapping
    public Result<List<StudentLearningReportVO>> list(
            @PathVariable Long courseId,
            Authentication authentication) {

        return Result.success(
                service.listForTeacher(
                        courseId,
                        currentUserId(authentication)
                )
        );
    }

    /**
     * 查看某个学生的报告详情。
     */
    @GetMapping("/{reportId}")
    public Result<StudentLearningReportDetailVO> detail(
            @PathVariable Long courseId,
            @PathVariable Long reportId,
            Authentication authentication) {

        return Result.success(
                service.getForTeacher(
                        courseId,
                        reportId,
                        currentUserId(authentication)
                )
        );
    }

    /**
     * 对报告追加教师文字批注。
     */
    @PostMapping("/{reportId}/comments")
    public Result<CommentVO> addComment(
            @PathVariable Long courseId,
            @PathVariable Long reportId,
            @Valid @RequestBody LearningReportCommentRequest request,
            Authentication authentication) {

        return Result.success(
                service.addTeacherComment(
                        courseId,
                        reportId,
                        currentUserId(authentication),
                        request
                )
        );
    }

    private Long currentUserId(Authentication authentication) {

        if (authentication == null
                || !(authentication.getPrincipal() instanceof Long userId)) {

            throw new BusinessException(401, "登录状态无效");
        }

        return userId;
    }
}
