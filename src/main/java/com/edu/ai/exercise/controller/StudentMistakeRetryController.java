package com.edu.ai.exercise.controller;

import com.edu.ai.common.BusinessException;
import com.edu.ai.common.Result;

import com.edu.ai.exercise.dto.StudentRetryAttemptVO;
import com.edu.ai.exercise.dto.StudentRetryDetailVO;
import com.edu.ai.exercise.dto.StudentRetryDraftSaveRequest;
import com.edu.ai.exercise.dto.StudentRetryDraftVO;
import com.edu.ai.exercise.dto.StudentRetryHistoryVO;
import com.edu.ai.exercise.dto.StudentRetryListVO;
import com.edu.ai.exercise.dto.StudentRetryMasteryVO;
import com.edu.ai.exercise.dto.StudentRetryResultVO;
import com.edu.ai.exercise.dto.StudentRetrySelfReviewRequest;
import com.edu.ai.exercise.dto.StudentRetrySubmitRequest;

import com.edu.ai.exercise.service.StudentMistakeRetryService;

import jakarta.validation.Valid;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/student/exercises/retry-lists")
@PreAuthorize("hasRole('STUDENT')")
public class StudentMistakeRetryController {

    private final StudentMistakeRetryService service;

    public StudentMistakeRetryController(
            StudentMistakeRetryService service) {
        this.service = service;
    }

    /**
     * 查询自己的重练清单。
     */
    @GetMapping
    public Result<List<StudentRetryListVO>> list(
            Authentication authentication) {

        return Result.success(
                service.listLists(userId(authentication))
        );
    }

    /**
     * 查询学生全部重练历史。
     */
    @GetMapping("/history")
    public Result<List<StudentRetryHistoryVO>> history(
            Authentication authentication) {

        return Result.success(
                service.listHistory(userId(authentication))
        );
    }

    /**
     * 查询学生知识点掌握情况。
     *
     * courseId 可选：
     * 不传时查询所有已加入课程；
     * 传入时只查询指定课程。
     */
    @GetMapping("/mastery")
    public Result<StudentRetryMasteryVO> mastery(
            @RequestParam(required = false) Long courseId,
            Authentication authentication) {

        return Result.success(
                service.getMastery(
                        userId(authentication),
                        courseId
                )
        );
    }

    /**
     * 查询重练清单详情。
     */
    @GetMapping("/{listId}")
    public Result<StudentRetryDetailVO> detail(
            @PathVariable Long listId,
            Authentication authentication) {

        return Result.success(
                service.detail(
                        listId,
                        userId(authentication)
                )
        );
    }

    /**
     * 开始或恢复重练。
     */
    @PostMapping("/{listId}/attempts")
    public Result<StudentRetryAttemptVO> start(
            @PathVariable Long listId,
            Authentication authentication) {

        return Result.success(
                service.startAttempt(
                        listId,
                        userId(authentication)
                )
        );
    }

    /**
     * 提交重练答案。
     */
    @PostMapping("/attempts/{attemptId}/submit")
    public Result<StudentRetryResultVO> submit(
            @PathVariable Long attemptId,

            @Valid
            @RequestBody StudentRetrySubmitRequest request,

            Authentication authentication) {

        return Result.success(
                service.submit(
                        attemptId,
                        userId(authentication),
                        request
                )
        );
    }

    /**
     * 查询重练草稿。
     */
    @GetMapping("/attempts/{attemptId}/draft")
    public Result<StudentRetryDraftVO> draft(
            @PathVariable Long attemptId,
            Authentication authentication) {

        return Result.success(
                service.getDraft(
                        attemptId,
                        userId(authentication)
                )
        );
    }

    /**
     * 保存重练草稿。
     */
    @PutMapping("/attempts/{attemptId}/draft")
    public Result<StudentRetryDraftVO> saveDraft(
            @PathVariable Long attemptId,

            @Valid
            @RequestBody StudentRetryDraftSaveRequest request,

            Authentication authentication) {

        return Result.success(
                service.saveDraft(
                        attemptId,
                        userId(authentication),
                        request
                )
        );
    }

    /**
     * 查询重练成绩。
     */
    @GetMapping("/attempts/{attemptId}")
    public Result<StudentRetryResultVO> result(
            @PathVariable Long attemptId,
            Authentication authentication) {

        return Result.success(
                service.getResult(
                        attemptId,
                        userId(authentication)
                )
        );
    }

    /**
     * 简答题自我核对。
     */
    @PostMapping("/attempts/{attemptId}/self-review")
    public Result<StudentRetryResultVO> selfReview(
            @PathVariable Long attemptId,

            @Valid
            @RequestBody StudentRetrySelfReviewRequest request,

            Authentication authentication) {

        return Result.success(
                service.selfReview(
                        attemptId,
                        userId(authentication),
                        request
                )
        );
    }

    private Long userId(Authentication authentication) {

        if (authentication == null
                || !(authentication.getPrincipal() instanceof Long id)) {

            throw new BusinessException(401, "登录状态无效");
        }

        return id;
    }
}
