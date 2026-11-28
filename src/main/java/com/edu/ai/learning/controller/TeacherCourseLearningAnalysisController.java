package com.edu.ai.learning.controller;

import com.edu.ai.common.BusinessException;
import com.edu.ai.common.Result;
import com.edu.ai.learning.dto.TeacherCourseLearningAnalysisVO;
import com.edu.ai.learning.service.TeacherCourseLearningAnalysisService;
import com.edu.ai.task.dto.StudentWeakPointDiagnosticVO;
import com.edu.ai.task.service.StudentWeakPointDiagnosticService;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/teacher/courses/{courseId}/learning-analysis")
@PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
public class TeacherCourseLearningAnalysisController {

    private final TeacherCourseLearningAnalysisService service;

    private final StudentWeakPointDiagnosticService diagnosticService;

    public TeacherCourseLearningAnalysisController(
            TeacherCourseLearningAnalysisService service,
            StudentWeakPointDiagnosticService diagnosticService) {

        this.service = service;
        this.diagnosticService = diagnosticService;
    }

    /**
     * 课程综合学习分析。
     */
    @GetMapping
    public Result<TeacherCourseLearningAnalysisVO> overview(
            @PathVariable Long courseId,
            Authentication authentication) {

        return Result.success(
                service.getCourseAnalysis(
                        courseId,
                        currentUserId(authentication)
                )
        );
    }

    /**
     * 教师查看指定学生的知识点综合诊断。
     */
    @GetMapping("/students/{studentId}/weak-point-diagnosis")
    public Result<List<StudentWeakPointDiagnosticVO>> studentWeakPointDiagnosis(
            @PathVariable Long courseId,
            @PathVariable Long studentId,
            Authentication authentication) {

        return Result.success(
                diagnosticService.diagnoseForTeacher(
                        courseId,
                        studentId,
                        currentUserId(authentication)
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
