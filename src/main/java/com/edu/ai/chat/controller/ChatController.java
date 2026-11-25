package com.edu.ai.chat.controller;

import com.edu.ai.chat.dto.ChatMessageVO;
import com.edu.ai.chat.dto.ChatRequest;
import com.edu.ai.chat.dto.ChatSessionUpdateRequest;
import com.edu.ai.chat.dto.ChatSessionVO;
import com.edu.ai.chat.service.ChatService;
import com.edu.ai.chat.service.ChatSessionManageService;
import com.edu.ai.common.BusinessException;
import com.edu.ai.common.Result;

import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

@RestController
@RequestMapping("/api/chat")
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
                getUserId(authentication)
        );
    }

    // 查询当前用户的历史会话
    @GetMapping("/sessions")
    public Result<List<ChatSessionVO>> sessions(
            Authentication authentication) {

        List<ChatSessionVO> result =
                chatService.listSessions(
                        getUserId(authentication)
                )
                .stream()
                .map(ChatSessionVO::from)
                .toList();

        return Result.success(result);
    }

    /**
     * 查询会话历史消息及引用资料版本信息。
     */
    @GetMapping("/sessions/{sessionId}/messages")
    public Result<List<ChatMessageVO>> messages(
            @PathVariable Long sessionId,
            Authentication authentication) {

        return Result.success(
                chatService.listMessages(
                        sessionId,
                        getUserId(authentication)
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

    // 从 JWT 认证信息中获取用户 ID
    private Long getUserId(Authentication authentication) {

        if (authentication == null
                || !(authentication.getPrincipal()
                instanceof Long userId)) {

            throw new BusinessException(
                    401,
                    "登录状态无效"
            );
        }

        return userId;
    }
}
