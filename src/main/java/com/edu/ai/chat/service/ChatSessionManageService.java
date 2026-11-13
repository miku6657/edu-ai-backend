package com.edu.ai.chat.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.edu.ai.chat.entity.ChatSession;
import com.edu.ai.chat.entity.ChatMessage;
import com.edu.ai.chat.mapper.ChatSessionMapper;
import com.edu.ai.chat.mapper.ChatMessageMapper;
import com.edu.ai.common.BusinessException;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;

@Service
public class ChatSessionManageService {

    private final ChatSessionMapper sessionMapper;
    private final ChatMessageMapper messageMapper;
    private final ChatStreamRegistry streamRegistry;
    private final TransactionTemplate transactionTemplate;

    public ChatSessionManageService(
            ChatSessionMapper sessionMapper,
            ChatMessageMapper messageMapper,
            ChatStreamRegistry streamRegistry,
            TransactionTemplate transactionTemplate) {

        this.sessionMapper = sessionMapper;
        this.messageMapper = messageMapper;
        this.streamRegistry = streamRegistry;
        this.transactionTemplate = transactionTemplate;
    }

    // 修改会话标题
    @Transactional(rollbackFor = Exception.class)
    public void rename(
            Long sessionId,
            Long userId,
            String title) {

        if (title == null || title.isBlank()) {
            throw new BusinessException(
                    400, "会话标题不能为空");
        }

        String cleaned = title.trim();

        if (cleaned.length() > 100) {
            throw new BusinessException(
                    400, "会话标题不能超过100个字符");
        }

        requireOwnedSession(sessionId, userId);

        int updated = sessionMapper.update(
                null,
                new LambdaUpdateWrapper<ChatSession>()
                        .eq(ChatSession::getId, sessionId)
                        .eq(ChatSession::getUserId, userId)
                        .set(ChatSession::getTitle, cleaned)
                        .set(
                                ChatSession::getUpdateTime,
                                LocalDateTime.now()
                        )
        );

        if (updated != 1) {
            throw new BusinessException(
                    409, "会话状态已发生变化");
        }
    }

    // 删除会话及全部消息
    public void delete(Long sessionId, Long userId) {

        streamRegistry.runIfIdle(sessionId, () -> {

            transactionTemplate.executeWithoutResult(status -> {

                requireOwnedSession(sessionId, userId);

                messageMapper.delete(
                        new LambdaQueryWrapper<ChatMessage>()
                                .eq(
                                        ChatMessage::getSessionId,
                                        sessionId
                                )
                );

                int deleted = sessionMapper.delete(
                        new LambdaQueryWrapper<ChatSession>()
                                .eq(
                                        ChatSession::getId,
                                        sessionId
                                )
                                .eq(
                                        ChatSession::getUserId,
                                        userId
                                )
                );

                if (deleted != 1) {
                    throw new BusinessException(
                            409, "会话删除失败");
                }
            });
        });
    }

    private ChatSession requireOwnedSession(
            Long sessionId,
            Long userId) {

        if (sessionId == null || sessionId <= 0) {
            throw new BusinessException(
                    400, "会话ID不合法");
        }

        ChatSession session =
                sessionMapper.selectById(sessionId);

        if (session == null) {
            throw new BusinessException(
                    404, "会话不存在");
        }

        if (!userId.equals(session.getUserId())) {
            throw new BusinessException(
                    403, "无权操作其他用户的会话");
        }

        return session;
    }
}
