package com.edu.ai.hotquestion.controller;

import com.edu.ai.common.BusinessException;
import com.edu.ai.common.Result;

import com.edu.ai.hotquestion.dto.CourseHotQuestionCreateRequest;
import com.edu.ai.hotquestion.dto.CourseHotQuestionPublicVO;
import com.edu.ai.hotquestion.dto.CourseHotQuestionReviewRequest;
import com.edu.ai.hotquestion.dto.CourseHotQuestionUpdateRequest;
import com.edu.ai.hotquestion.dto.CourseHotQuestionVO;

import com.edu.ai.hotquestion.service.CourseHotQuestionService;

import jakarta.validation.Valid;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/course-hot-questions")
public class CourseHotQuestionController {

    private final CourseHotQuestionService service;

    public CourseHotQuestionController(
            CourseHotQuestionService service) {
        this.service = service;
    }

    private Long userId(Authentication auth) {

        if (auth == null
                || !(auth.getPrincipal() instanceof Long id)) {

            throw new BusinessException(
                    401,
                    "登录状态无效"
            );
        }

        return id;
    }

    /**
     * 学生、教师、管理员查询已发布的热门问答。
     */
    @GetMapping("/courses/{courseId}/published")
    @PreAuthorize("hasAnyRole('STUDENT','TEACHER','ADMIN')")
    public Result<List<CourseHotQuestionPublicVO>> published(

            @PathVariable Long courseId,

            @RequestParam(required = false)
            String keyword,

            @RequestParam(required = false)
            String category,

            Authentication auth) {

        return Result.success(
                service.listPublished(
                        courseId,
                        userId(auth),
                        keyword,
                        category
                )
        );
    }

    /**
     * 管理员、课程负责人查看标准问答管理列表。
     */
    @GetMapping("/courses/{courseId}/manage")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    public Result<List<CourseHotQuestionVO>> manage(

            @PathVariable Long courseId,

            @RequestParam(required = false)
            String status,

            @RequestParam(required = false)
            String keyword,

            Authentication auth) {

        return Result.success(
                service.listForManage(
                        courseId,
                        userId(auth),
                        status,
                        keyword
                )
        );
    }

    /**
     * 管理员根据高频问题整理标准问答。
     */
    @PostMapping("/courses/{courseId}")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<CourseHotQuestionVO> create(

            @PathVariable Long courseId,

            @Valid
            @RequestBody CourseHotQuestionCreateRequest request,

            Authentication auth) {

        return Result.success(
                service.create(
                        courseId,
                        userId(auth),
                        request
                )
        );
    }

    /**
     * 管理员修改标准问答草稿。
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<CourseHotQuestionVO> update(

            @PathVariable Long id,

            @Valid
            @RequestBody CourseHotQuestionUpdateRequest request,

            Authentication auth) {

        return Result.success(
                service.update(
                        id,
                        userId(auth),
                        request
                )
        );
    }

    /**
     * 管理员提交审核。
     */
    @PostMapping("/{id}/submit")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<CourseHotQuestionVO> submit(

            @PathVariable Long id,

            Authentication auth) {

        return Result.success(
                service.submit(
                        id,
                        userId(auth)
                )
        );
    }

    /**
     * 课程负责人审核。
     */
    @PostMapping("/{id}/review")
    @PreAuthorize("hasRole('TEACHER')")
    public Result<CourseHotQuestionVO> review(

            @PathVariable Long id,

            @Valid
            @RequestBody CourseHotQuestionReviewRequest request,

            Authentication auth) {

        return Result.success(
                service.review(
                        id,
                        userId(auth),
                        request
                )
        );
    }

    /**
     * 管理员下架已发布的热门问题。
     */
    @PostMapping("/{id}/offline")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<CourseHotQuestionVO> offline(

            @PathVariable Long id,

            Authentication auth) {

        return Result.success(
                service.offline(
                        id,
                        userId(auth)
                )
        );
    }
}
