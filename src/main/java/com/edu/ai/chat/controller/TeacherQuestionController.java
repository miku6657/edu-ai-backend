package com.edu.ai.chat.controller;

import com.edu.ai.chat.dto.HotQuestionVO;
import com.edu.ai.chat.dto.KnowledgePointQuestionStatVO;
import com.edu.ai.chat.dto.QuestionAnalyticsSummaryVO;
import com.edu.ai.chat.service.TeacherQuestionAnalyticsService;
import com.edu.ai.chat.service.TeacherQuestionAnalyticsService.QuestionPage;
import com.edu.ai.common.BusinessException;
import com.edu.ai.common.Result;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/v1/teacher-questions")
@PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
public class TeacherQuestionController {

    private final TeacherQuestionAnalyticsService service;

    public TeacherQuestionController(
            TeacherQuestionAnalyticsService service) {
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
     * 查询课程问答概况。
     */
    @GetMapping("/courses/{courseId}/summary")
    public Result<QuestionAnalyticsSummaryVO> summary(
            @PathVariable Long courseId,

            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate startDate,

            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate endDate,

            Authentication auth) {

        return Result.success(
                service.summary(
                        courseId,
                        userId(auth),
                        startDate,
                        endDate
                )
        );
    }

    /**
     * 分页查询学生问题。
     */
    @GetMapping("/courses/{courseId}/records")
    public Result<QuestionPage> records(
            @PathVariable Long courseId,

            @RequestParam(required = false)
            Long pointId,

            @RequestParam(required = false)
            String studentKeyword,

            @RequestParam(required = false)
            String keyword,

            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate startDate,

            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate endDate,

            @RequestParam(defaultValue = "1")
            int page,

            @RequestParam(defaultValue = "20")
            int size,

            Authentication auth) {

        return Result.success(
                service.listRecords(
                        courseId,
                        userId(auth),
                        pointId,
                        studentKeyword,
                        keyword,
                        startDate,
                        endDate,
                        page,
                        size
                )
        );
    }

    /**
     * 查询重复出现的高频问题。
     */
    @GetMapping("/courses/{courseId}/hot-questions")
    public Result<List<HotQuestionVO>> hotQuestions(
            @PathVariable Long courseId,

            @RequestParam(required = false)
            Long pointId,

            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate startDate,

            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate endDate,

            @RequestParam(defaultValue = "10")
            int limit,

            Authentication auth) {

        return Result.success(
                service.listHotQuestions(
                        courseId,
                        userId(auth),
                        pointId,
                        startDate,
                        endDate,
                        limit
                )
        );
    }

    /**
     * 查询知识点提问热度。
     */
    @GetMapping("/courses/{courseId}/knowledge-points")
    public Result<List<KnowledgePointQuestionStatVO>>
    knowledgePointStats(
            @PathVariable Long courseId,

            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate startDate,

            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate endDate,

            Authentication auth) {

        return Result.success(
                service.listKnowledgePointStats(
                        courseId,
                        userId(auth),
                        startDate,
                        endDate
                )
        );
    }
}
