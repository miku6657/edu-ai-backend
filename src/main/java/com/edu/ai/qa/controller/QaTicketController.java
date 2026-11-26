package com.edu.ai.qa.controller;

import com.edu.ai.common.BusinessException;
import com.edu.ai.common.Result;
import com.edu.ai.qa.dto.QaTicketCreateRequest;
import com.edu.ai.qa.dto.QaTicketReplyRequest;
import com.edu.ai.qa.dto.QaTicketVO;
import com.edu.ai.qa.service.QaTicketService;

import jakarta.validation.Valid;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/qa-tickets")
public class QaTicketController {

    private final QaTicketService service;

    public QaTicketController(QaTicketService service) {
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
     * 学生确认后申请教师人工答疑。
     *
     * POST /api/v1/qa-tickets/from-chat
     */
    @PostMapping("/from-chat")
    @PreAuthorize("hasRole('STUDENT')")
    public Result<QaTicketService.CreatedTicket> createFromChat(
            @Valid
            @RequestBody QaTicketCreateRequest request,
            Authentication auth) {

        QaTicketService.CreatedTicket ticket =
                service.createFromStudentConfirmation(
                        request.questionMessageId(),
                        userId(auth)
                );

        return Result.success(ticket);
    }

    /**
     * 学生查询自己的答疑工单。
     *
     * GET /api/v1/qa-tickets/mine
     */
    @GetMapping("/mine")
    @PreAuthorize("hasRole('STUDENT')")
    public Result<QaTicketService.TicketPage> mine(
            @RequestParam(required = false)
            String status,

            @RequestParam(defaultValue = "1")
            int page,

            @RequestParam(defaultValue = "10")
            int size,

            Authentication auth) {

        return Result.success(
                service.listMine(
                        userId(auth),
                        status,
                        page,
                        size
                )
        );
    }

    /**
     * 教师查询分配给自己的工单。
     * 管理员可以查询全部工单。
     *
     * GET /api/v1/qa-tickets/assigned
     */
    @GetMapping("/assigned")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    public Result<QaTicketService.TicketPage> assigned(
            @RequestParam(required = false)
            Long courseId,

            @RequestParam(required = false)
            String status,

            @RequestParam(defaultValue = "1")
            int page,

            @RequestParam(defaultValue = "10")
            int size,

            Authentication auth) {

        return Result.success(
                service.listAssigned(
                        userId(auth),
                        courseId,
                        status,
                        page,
                        size
                )
        );
    }

    /**
     * 查询工单详情。
     *
     * GET /api/v1/qa-tickets/{ticketId}
     */
    @GetMapping("/{ticketId}")
    public Result<QaTicketVO> detail(
            @PathVariable Long ticketId,
            Authentication auth) {

        return Result.success(
                service.detail(
                        ticketId,
                        userId(auth)
                )
        );
    }

    /**
     * 教师提交答复。
     *
     * POST /api/v1/qa-tickets/{ticketId}/reply
     */
    @PostMapping("/{ticketId}/reply")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    public Result<String> reply(
            @PathVariable Long ticketId,

            @Valid
            @RequestBody QaTicketReplyRequest request,

            Authentication auth) {

        service.reply(
                ticketId,
                userId(auth),
                request.reply()
        );

        return Result.success("答疑回复提交成功");
    }
}
