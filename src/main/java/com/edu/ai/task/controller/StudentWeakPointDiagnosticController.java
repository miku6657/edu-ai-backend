package com.edu.ai.task.controller;

import com.edu.ai.common.BusinessException;
import com.edu.ai.common.Result;
import com.edu.ai.task.dto.StudentWeakPointDiagnosticVO;
import com.edu.ai.task.service.StudentWeakPointDiagnosticService;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping(
        "/api/v1/learning-paths/courses/{courseId}"
)
@PreAuthorize("hasRole('STUDENT')")
public class StudentWeakPointDiagnosticController {

    private final StudentWeakPointDiagnosticService service;

    public StudentWeakPointDiagnosticController(
            StudentWeakPointDiagnosticService service) {
        this.service = service;
    }

    /**
     * 获取课程知识点综合诊断。
     *
     * GET /api/v1/learning-paths/courses/{courseId}/weak-point-diagnosis
     */
    @GetMapping("/weak-point-diagnosis")
    public Result<List<StudentWeakPointDiagnosticVO>> diagnose(
            @PathVariable Long courseId,
            Authentication authentication) {

        Long studentId = userId(authentication);

        return Result.success(
                service.diagnose(courseId, studentId)
        );
    }

    private Long userId(Authentication authentication) {

        if (authentication == null
                || !(authentication.getPrincipal() instanceof Long id)) {

            throw new BusinessException(
                    401,
                    "登录状态无效"
            );
        }

        return id;
    }
}
