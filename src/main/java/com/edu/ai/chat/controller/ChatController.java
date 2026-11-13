package com.edu.ai.chat.controller;

import com.edu.ai.chat.dto.ChatRequest;
import com.edu.ai.chat.dto.ChatSessionUpdateRequest;
import com.edu.ai.chat.entity.ChatMessage;
import com.edu.ai.chat.entity.ChatSession;
import com.edu.ai.chat.service.ChatService;
import com.edu.ai.chat.service.ChatSessionManageService;
import com.edu.ai.common.BusinessException;
import com.edu.ai.common.Result;

import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

@RestController
@RequestMapping("/api/chat")
@PreAuthorize("hasAnyRole('STUDENT', 'OWNER')")
public class ChatController {

    private final ChatService chatService;
    private final ChatSessionManageService manageService;

    public ChatController(
            ChatService chatService,
            ChatSessionManageService manageService) {

        this.chatService = chatService;
        this.manageService = manageService;
    }

    // RAG SSE 流式聊天
    @PostMapping(
            value = "/stream",
            produces = MediaType.TEXT_EVENT_STREAM_VALUE
    )
    public SseEmitter stream(
            @RequestBody ChatRequest request,
            Authentication authentication) {

        return chatService.stream(
                request,
                getUserId(authentication),
                getRole(authentication)
        );
    }

    // 我的历史会话
    @GetMapping("/sessions")
    public Result<List<ChatSession>> sessions(
            Authentication authentication) {

        return Result.success(
                chatService.listSessions(
                        getUserId(authentication),
                        getRole(authentication)
                )
        );
    }

    // 会话历史消息
    @GetMapping("/sessions/{sessionId}/messages")
    public Result<List<ChatMessage>> messages(
            @PathVariable Long sessionId,
            Authentication authentication) {

        return Result.success(
                chatService.listMessages(
                        sessionId,
                        getUserId(authentication),
                        getRole(authentication)
                )
        );
    }

    // 修改会话标题
    @PutMapping("/sessions/{sessionId}")
    public Result<String> rename(
            @PathVariable Long sessionId,
            @RequestBody ChatSessionUpdateRequest request,
            Authentication authentication) {

        manageService.rename(
                sessionId,
                getUserId(authentication),
                request.getTitle()
        );

        return Result.success("会话标题修改成功");
    }

    // 删除会话
    @DeleteMapping("/sessions/{sessionId}")
    public Result<String> delete(
            @PathVariable Long sessionId,
            Authentication authentication) {

        manageService.delete(
                sessionId,
                getUserId(authentication)
        );

        return Result.success("会话删除成功");
    }

    // 停止 AI 生成
    @PostMapping("/sessions/{sessionId}/cancel")
    public Result<String> cancel(
            @PathVariable Long sessionId,
            Authentication authentication) {

        chatService.cancelStream(
                sessionId,
                getUserId(authentication)
        );

        return Result.success("已请求停止生成");
    }

    private Long getUserId(
            Authentication authentication) {

        if (authentication == null
                || !(authentication.getPrincipal()
                instanceof Long userId)) {

            throw new BusinessException(
                    401, "登录状态无效");
        }

        return userId;
    }

    private String getRole(
            Authentication authentication) {

        if (authentication.getAuthorities().stream()
                .anyMatch(a ->
                        a.getAuthority().equals("ROLE_STUDENT"))) {
            return "STUDENT";
        }

        if (authentication.getAuthorities().stream()
                .anyMatch(a ->
                        a.getAuthority().equals("ROLE_OWNER"))) {
            return "OWNER";
        }

        throw new BusinessException(
                403, "无权使用课程问答");
    }
}
