package com.edu.ai.chat.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.edu.ai.chat.dto.ChatMessageVO;
import com.edu.ai.chat.dto.ChatRequest;
import com.edu.ai.chat.entity.ChatMessage;
import com.edu.ai.chat.entity.ChatSession;
import com.edu.ai.chat.mapper.ChatMessageMapper;
import com.edu.ai.chat.mapper.ChatSessionMapper;
import com.edu.ai.common.BusinessException;
import com.edu.ai.course.entity.Course;
import com.edu.ai.course.mapper.CourseMapper;
import com.edu.ai.course.service.CoursePermissionService;
import com.edu.ai.document.entity.CourseDocument;
import com.edu.ai.document.mapper.CourseDocumentMapper;
import com.edu.ai.qa.service.QaTicketService;
import com.edu.ai.system.service.UserModelResolver;
import com.edu.ai.task.service.QuestionKnowledgeService;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;

import com.edu.ai.chat.service.ChatStreamRegistry.RunningStream;

@Service
public class
ChatService {

    private static final Logger log =
            LoggerFactory.getLogger(ChatService.class);

    private final ChatSessionMapper sessionMapper;
    private final ChatMessageMapper messageMapper;
    private final CourseMapper courseMapper;
    private final CourseDocumentMapper documentMapper;
    private final CoursePermissionService coursePermissionService;
    private final UserModelResolver userModelResolver;
    private final ThreadPoolTaskExecutor executor;
    private final ObjectMapper objectMapper;
    private final ChatStreamRegistry streamRegistry;
    private final QuestionKnowledgeService questionKnowledgeService;
    private final QaTicketService qaTicketService;
    private final ChatCitationVersionService citationVersionService;
    private final HttpClient httpClient;

    private final String serviceUrl;
    private final String internalKey;

    public ChatService(
            ChatSessionMapper sessionMapper,
            ChatMessageMapper messageMapper,
            CourseMapper courseMapper,
            CourseDocumentMapper documentMapper,
            UserModelResolver userModelResolver,
            CoursePermissionService coursePermissionService,
            @Qualifier("chatStreamExecutor")
            ThreadPoolTaskExecutor executor,
            ObjectMapper objectMapper,
            ChatStreamRegistry streamRegistry,
            QuestionKnowledgeService questionKnowledgeService,
            QaTicketService qaTicketService,
            ChatCitationVersionService citationVersionService,
            @Value("${ai.service.url}") String serviceUrl,
            @Value("${ai.service.internal-key}") String internalKey) {

        this.sessionMapper = sessionMapper;
        this.messageMapper = messageMapper;
        this.courseMapper = courseMapper;
        this.documentMapper = documentMapper;
        this.userModelResolver = userModelResolver;
        this.coursePermissionService = coursePermissionService;
        this.executor = executor;
        this.objectMapper = objectMapper;
        this.streamRegistry = streamRegistry;
        this.questionKnowledgeService = questionKnowledgeService;
        this.qaTicketService = qaTicketService;
        this.citationVersionService = citationVersionService;
        this.serviceUrl = serviceUrl;
        this.internalKey = internalKey;

        this.httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    /**
     * 课程访问权限：
     * 管理员、课程教师、已加入且课程正常开放的学生。
     */
    private void checkAccess(Long courseId, Long userId) {

        coursePermissionService.requireActiveRead(
                courseId,
                userId
        );
    }

    // 获取或新建会话
    private ChatSession resolveSession(
            ChatRequest request, Long userId) {

        if (request.getSessionId() != null) {

            ChatSession existing =
                    sessionMapper.selectById(
                            request.getSessionId());

            if (existing == null
                    || !userId.equals(existing.getUserId())
                    || !request.getCourseId()
                            .equals(existing.getCourseId())) {

                throw new BusinessException(
                        403, "无权访问该会话");
            }

            return existing;
        }

        ChatSession session = new ChatSession();

        session.setCourseId(request.getCourseId());
        session.setUserId(userId);

        String question = request.getQuestion().trim();

        session.setTitle(
                question.substring(
                        0, Math.min(50, question.length()))
        );

        session.setCreateTime(LocalDateTime.now());
        session.setUpdateTime(LocalDateTime.now());

        sessionMapper.insert(session);

        return session;
    }

    // 保存普通用户消息
    private Long saveMessage(
            Long sessionId,
            String role,
            String content) {

        return saveMessage(sessionId, role, content, null);
    }

    // 保存用户或AI消息及引用来源
    private Long saveMessage(
            Long sessionId,
            String role,
            String content,
            String sourcesJson) {

        ChatMessage message = new ChatMessage();

        message.setSessionId(sessionId);
        message.setRole(role);
        message.setContent(content);
        message.setSourcesJson(sourcesJson);
        message.setCreateTime(LocalDateTime.now());

        messageMapper.insert(message);

        if (message.getId() == null) {
            throw new IllegalStateException(
                    "保存聊天消息后未获取到消息ID"
            );
        }

        return message.getId();
    }

    /**
     * 兼容字符串和整数类型的文档ID。
     */
    private String readCitationDocumentId(JsonNode source) {

        if (source == null || !source.isObject()) {
            return null;
        }

        JsonNode idNode = source.get("documentId");

        if (idNode == null || idNode.isNull()) {
            return null;
        }

        if (idNode.isString()) {
            return idNode.asString();
        }

        if (idNode.isIntegralNumber()) {
            return idNode.toString();
        }

        return null;
    }

    /**
     * 补充引用资料名称。
     *
     * 只允许查询当前课程下的资料，
     * 避免通过引用数据访问其他课程的资源。
     */
    private ArrayNode enrichCitationSources(
            Long courseId,
            JsonNode sources) {

        ArrayNode result = objectMapper.createArrayNode();

        if (sources == null || !sources.isArray()) {
            return result;
        }

        Set<Long> documentIds = new LinkedHashSet<>();

        for (JsonNode source : sources) {

            String idText = readCitationDocumentId(source);

            if (idText == null
                    || !idText.matches("[1-9][0-9]*")) {
                continue;
            }

            try {
                documentIds.add(Long.parseLong(idText));
            } catch (NumberFormatException ignored) {
                // 忽略非法文档ID
            }
        }

        Map<Long, String> documentNames = new HashMap<>();

        if (!documentIds.isEmpty()) {

            List<CourseDocument> documents =
                    documentMapper.selectList(
                            new LambdaQueryWrapper<CourseDocument>()
                                    .eq(
                                            CourseDocument::getCourseId,
                                            courseId
                                    )
                                    .in(
                                            CourseDocument::getId,
                                            documentIds
                                    )
                    );

            for (CourseDocument document : documents) {
                documentNames.put(
                        document.getId(),
                        document.getOriginalName()
                );
            }
        }

        for (JsonNode source : sources) {

            if (!source.isObject()) {
                continue;
            }

            ObjectNode citation = (ObjectNode) source.deepCopy();

            String idText = readCitationDocumentId(citation);

            if (idText == null
                    || !idText.matches("[1-9][0-9]*")) {
                continue;
            }

            String documentName = "未知资料";

            try {
                Long documentId = Long.parseLong(idText);

                documentName = documentNames.getOrDefault(
                        documentId,
                        "文档 " + idText
                );

            } catch (NumberFormatException ignored) {
                continue;
            }

            citation.put("docName", documentName);

            result.add(citation);
        }

        return result;
    }

    // 获取本次提问之前的最近10条消息
    private List<ChatMessage> getRecentHistory(
            Long sessionId) {

        List<ChatMessage> reversed =
                messageMapper.selectList(
                        new LambdaQueryWrapper<ChatMessage>()
                                .eq(ChatMessage::getSessionId,
                                        sessionId)
                                .orderByDesc(ChatMessage::getId)
                                .last("LIMIT 10")
                );

        java.util.Collections.reverse(reversed);

        return reversed;
    }

    // 开始流式聊天
    public SseEmitter stream(
            ChatRequest request,
            Long userId) {

        if (request == null
                || request.getCourseId() == null
                || request.getCourseId() <= 0
                || request.getQuestion() == null
                || request.getQuestion().isBlank()) {

            throw new BusinessException(
                    400, "课程ID和问题不能为空");
        }

        String question = request.getQuestion().trim();

        if (question.length() > 2000) {
            throw new BusinessException(
                    400, "问题不能超过2000个字符");
        }

        // 校验当前用户是否有课程访问权限
        checkAccess(
                request.getCourseId(),
                userId
        );

        Course course = courseMapper.selectById(
                request.getCourseId()
        );

        if (course == null) {
            throw new BusinessException(404, "课程不存在");
        }

        int embeddingVersion =
                course.getEmbeddingVersion() == null
                        ? 1
                        : course.getEmbeddingVersion();

        // 对话模型统一用课程创建教师的配置；
        // Embedding 模型用课程活动索引版本对应的固定配置。
        UserModelResolver.UserModelConfig modelConfig =
                userModelResolver.resolveForCourse(
                        course.getUserId(),
                        request.getCourseId(),
                        embeddingVersion
                );

        ChatSession session =
                resolveSession(request, userId);

        Long sessionId = session.getId();

        // 阻止同一会话并发生成
        RunningStream running =
                streamRegistry.register(sessionId);

        try {
            // 先读取历史，再保存本次问题
            List<ChatMessage> history =
                    getRecentHistory(sessionId);

            Long questionMessageId = saveMessage(
                    sessionId,
                    "user",
                    question
            );

            SseEmitter emitter = new SseEmitter(300000L);

            // 客户端断开或流式超时，停止上游请求
            emitter.onTimeout(() -> {
                streamRegistry.cancel(sessionId);
                emitter.complete();
            });

            emitter.onError(error ->
                    streamRegistry.cancel(sessionId));

            emitter.onCompletion(() -> {
                if (!running.isSuccessful()) {
                    streamRegistry.cancel(sessionId);
                }
            });

            Future<?> future = executor.submit(() -> {

                try {
                    // 只有 QUEUED 状态才能进入 RUNNING
                    if (!running.markStarted()) {
                        return;
                    }

                    forwardStream(
                            emitter,
                            session,
                            question,
                            questionMessageId,
                            history,
                            running,
                            modelConfig,
                            embeddingVersion
                    );

                } finally {
                    // 只释放当前任务
                    streamRegistry.release(sessionId, running);
                }
            });

            running.setFuture(future);

            return emitter;

        } catch (RuntimeException e) {

            streamRegistry.cancel(sessionId);
            streamRegistry.release(sessionId, running);
            throw e;
        }
    }

    private void forwardStream(
            SseEmitter emitter,
            ChatSession session,
            String question,
            Long questionMessageId,
            List<ChatMessage> history,
            RunningStream running,
            UserModelResolver.UserModelConfig modelConfig,
            int embeddingVersion) {

        StringBuilder answer = new StringBuilder();

        String sourcesJson = "[]";

        boolean completed = false;

        // 本次RAG是否明确返回无依据事件
        boolean noEvidence = false;

        // 无依据原因
        String noEvidenceReason = "NO_RETRIEVAL_MATCH";

        try {
            ObjectNode body =
                    objectMapper.createObjectNode();

            body.put("course_id", session.getCourseId());
            body.put("question", question);

            // 查询当前课程所有已经生效的资源版本。
            // 白名单必须由Java后端生成，不能由前端指定。
            List<Long> allowedDocumentIds =
                    documentMapper.selectReadyDocumentIds(
                            session.getCourseId()
                    );

            ArrayNode allowedDocuments =
                    body.putArray("allowed_document_ids");

            for (Long documentId : allowedDocumentIds) {
                allowedDocuments.add(documentId.toString());
            }

            body.put(
                    "embedding_version",
                    embeddingVersion
            );

            body.put(
                    "chat_model",
                    modelConfig.chatModel()
            );

            body.put(
                    "chat_api_key",
                    modelConfig.chatApiKey()
            );

            body.put(
                    "chat_base_url",
                    modelConfig.chatBaseUrl()
            );

            body.put(
                    "embedding_model",
                    modelConfig.embeddingModel()
            );

            body.put(
                    "embedding_api_key",
                    modelConfig.embeddingApiKey()
            );

            body.put(
                    "embedding_base_url",
                    modelConfig.embeddingBaseUrl()
            );

            if (modelConfig.collectionName() == null
                    || modelConfig.collectionName().isBlank()) {

                throw new IllegalStateException(
                        "课程索引缺少活动Collection配置"
                );
            }

            body.put(
                    "collection_name",
                    modelConfig.collectionName()
            );

            body.put(
                    "embedding_dimension",
                    modelConfig.embeddingDimension()
            );

            ArrayNode messages = body.putArray("history");

            for (ChatMessage message : history) {

                ObjectNode item = messages.addObject();

                item.put("role", message.getRole());
                item.put("content", message.getContent());
            }

            HttpRequest httpRequest =
                    HttpRequest.newBuilder()
                            .uri(URI.create(
                                    serviceUrl
                                            + "/internal/chat/stream"))
                            .timeout(Duration.ofMinutes(5))
                            .header("Content-Type",
                                    "application/json")
                            .header("Accept",
                                    "text/event-stream")
                            .header("X-Internal-Key",
                                    internalKey)
                            .POST(
                                    HttpRequest.BodyPublishers.ofString(
                                            objectMapper.writeValueAsString(
                                                    body)))
                            .build();

            HttpResponse<java.io.InputStream> response =
                    httpClient.send(
                            httpRequest,
                            HttpResponse.BodyHandlers.ofInputStream()
                    );

            running.setUpstream(response.body());

            // 取消状态可能在 HTTP 请求等待期间发生
            if (running.isCancelled()) {
                return;
            }

            if (response.statusCode() != 200) {

                try (var unused = response.body()) {
                    // 关闭错误响应流
                }

                throw new IOException(
                        "Python service HTTP "
                                + response.statusCode());
            }

            // 将会话ID首先返回给前端
            send(emitter, "session", objectMapper
                    .createObjectNode()
                    .put("sessionId", session.getId().toString()));

            try (BufferedReader reader =
                         new BufferedReader(
                                 new InputStreamReader(
                                         response.body(),
                                         StandardCharsets.UTF_8))) {

                String line;
                String event = "message";
                StringBuilder data = new StringBuilder();

                while (!running.isCancelled()
                        && (line = reader.readLine()) != null) {

                    if (line.startsWith("event:")) {
                        event = line.substring(6).trim();
                    } else if (line.startsWith("data:")) {
                        if (!data.isEmpty()) {
                            data.append("\n");
                        }
                        data.append(line.substring(5).trim());
                    } else if (line.isEmpty()) {

                        if (!data.isEmpty()) {

                            JsonNode payload =
                                    objectMapper.readTree(data.toString());

                            JsonNode outboundPayload = payload;

                            if ("token".equals(event)) {
                                answer.append(
                                        payload.path("content").asString()
                                );
                            }

                            // 识别无依据拒答
                            if ("no_evidence".equals(event)) {

                                noEvidence = true;

                                String reason =
                                        payload.path("reason").asString();

                                if (reason != null && !reason.isBlank()) {
                                    noEvidenceReason = reason;
                                }
                            }

                            if ("sources".equals(event)) {

                                JsonNode sources = payload.path("sources");

                                if (sources.isArray()) {

                                    ArrayNode enrichedSources =
                                            enrichCitationSources(
                                                    session.getCourseId(),
                                                    sources
                                            );

                                    sourcesJson = objectMapper.writeValueAsString(
                                            enrichedSources
                                    );

                                    ObjectNode enrichedPayload =
                                            (ObjectNode) payload.deepCopy();

                                    enrichedPayload.set(
                                            "sources",
                                            enrichedSources
                                    );

                                    outboundPayload = enrichedPayload;
                                }
                            }

                            if ("error".equals(event)) {
                                throw new IOException(
                                        "Python RAG stream failed"
                                );
                            }

                            if ("done".equals(event)) {
                                completed = true;
                            }

                            if (!"done".equals(event)
                                    && !"no_evidence".equals(event)) {

                                send(
                                        emitter,
                                        event,
                                        outboundPayload
                                );
                            }
                        }

                        event = "message";
                        data.setLength(0);

                        if (completed) {
                            break;
                        }
                    }
                }
            }

            if (running.isCancelled()) {
                return;
            }

            if (!completed) {
                throw new IOException(
                        "Python SSE stream ended unexpectedly");
            }

            // 第一步：尝试进入 COMPLETING
            // 如果用户取消已经成功，这里就不能继续保存
            if (!running.beginCompletion()) {
                return;
            }

            // 第二步：保存完整 AI 回答
            Long assistantMessageId = saveMessage(
                    session.getId(),
                    "assistant",
                    answer.toString(),
                    sourcesJson
            );

            // 如果是无依据拒答，保存拒答标记。
            // 注意：标记保存在 AI 回答消息上，
            // 并关联对应的学生问题消息。
            if (noEvidence) {

                ChatMessage rejectionMessage = new ChatMessage();

                rejectionMessage.setId(assistantMessageId);

                rejectionMessage.setNoEvidenceReason(
                        noEvidenceReason
                );

                rejectionMessage.setQuestionMessageId(
                        questionMessageId
                );

                messageMapper.updateById(rejectionMessage);
            }

            // 第三步：更新会话最后活动时间
            ChatSession update = new ChatSession();

            update.setId(session.getId());
            update.setUpdateTime(LocalDateTime.now());

            sessionMapper.updateById(update);

            // 第四步：确认任务完成
            if (!running.markSuccessful()) {
                throw new IllegalStateException(
                        "聊天任务状态异常，无法标记成功");
            }

            if (noEvidence) {

                // 无依据拒答只向前端发送申请邀请。
                // 此处绝对不能创建工单。

                ObjectNode rejectionPayload =
                        objectMapper.createObjectNode();

                rejectionPayload.put(
                        "user_message_id",
                        questionMessageId.toString()
                );

                rejectionPayload.put(
                        "notice_text",
                        answer.toString().isBlank()
                                ? "资料中未找到相关内容。"
                                : answer.toString()
                );

                rejectionPayload.put(
                        "reason",
                        noEvidenceReason
                );

                rejectionPayload.put(
                        "has_general_supplement",
                        false
                );

                send(
                        emitter,
                        "rejection",
                        rejectionPayload
                );

                log.info(
                        "AI拒答，等待学生确认是否创建工单，questionMessageId={}",
                        questionMessageId
                );

            } else {

                // 只有正常检索回答才计入知识点提问统计
                try {

                    questionKnowledgeService.recordSuccessfulQuestion(
                            questionMessageId,
                            session.getId(),
                            session.getUserId(),
                            session.getCourseId(),
                            question
                    );

                } catch (Exception e) {

                    log.warn(
                            "知识点提问统计失败, messageId={}",
                            questionMessageId,
                            e
                    );
                }
            }

            // 第五步：通知前端
            ObjectNode donePayload =
                    objectMapper.createObjectNode();

            donePayload.put(
                    "sessionId",
                    session.getId().toString()
            );

            send(
                    emitter,
                    "done",
                    donePayload
            );

            emitter.complete();

        } catch (Exception e) {

            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }

            // 尝试将任务标记为 FAILED
            // 已取消、已成功的任务不会被覆盖
            boolean failed = running.markFailed();

            if (failed) {
                try {
                    send(
                            emitter,
                            "error",
                            objectMapper.createObjectNode()
                                    .put(
                                            "message",
                                            "AI回答失败或连接中断"
                                    )
                    );
                } catch (Exception ignored) {
                    // 客户端可能已经断开
                }
            }

            emitter.complete();
        }
    }

    private void send(
            SseEmitter emitter,
            String event,
            Object data) throws IOException {

        emitter.send(
                SseEmitter.event()
                        .name(event)
                        .data(data)
        );
    }

    /**
     * 查询当前用户的历史会话。
     * 只返回当前用户有权访问的课程会话。
     */
    public List<ChatSession> listSessions(Long userId) {

        List<ChatSession> sessions =
                sessionMapper.selectList(
                        new LambdaQueryWrapper<ChatSession>()
                                .eq(ChatSession::getUserId, userId)
                                .orderByDesc(ChatSession::getUpdateTime)
                );

        return sessions.stream()
                .filter(session -> {
                    try {
                        checkAccess(
                                session.getCourseId(),
                                userId
                        );
                        return true;
                    } catch (BusinessException e) {
                        return false;
                    }
                })
                .toList();
    }

    /**
     * 校验当前用户是否有权访问指定会话。
     */
    private ChatSession requireReadableSession(
            Long sessionId,
            Long userId) {

        if (userId == null) {
            throw new BusinessException(
                    401, "登录状态无效"
            );
        }

        ChatSession session =
                sessionMapper.selectById(sessionId);

        if (session == null) {
            throw new BusinessException(
                    404, "会话不存在"
            );
        }

        if (!userId.equals(session.getUserId())) {
            throw new BusinessException(
                    403, "无权访问该会话"
            );
        }

        checkAccess(
                session.getCourseId(),
                userId
        );

        return session;
    }

    /**
     * 查询会话历史消息，并补充引用版本和人工答疑状态。
     */
    public List<ChatMessageVO> listMessages(
            Long sessionId,
            Long userId) {

        ChatSession session =
                requireReadableSession(
                        sessionId,
                        userId
                );

        List<ChatMessage> messages =
                messageMapper.selectList(
                        new LambdaQueryWrapper<ChatMessage>()
                                .eq(
                                        ChatMessage::getSessionId,
                                        sessionId
                                )
                                .orderByAsc(
                                        ChatMessage::getId
                                )
                );

        // 先补充引用版本
        List<ChatMessageVO> result =
                citationVersionService.enrichMessages(
                        session.getCourseId(),
                        messages
                );

        // 再补充已经创建的工单状态
        for (int i = 0; i < messages.size(); i++) {

            ChatMessage message = messages.get(i);
            ChatMessageVO vo = result.get(i);

            if (!"assistant".equalsIgnoreCase(message.getRole())) {
                continue;
            }

            if (message.getNoEvidenceReason() == null
                    || message.getQuestionMessageId() == null) {
                continue;
            }

            QaTicketService.CreatedTicket ticket =
                    qaTicketService.findExistingForQuestion(
                            message.getQuestionMessageId(),
                            userId
                    );

            if (ticket != null) {

                vo.setTicketId(ticket.ticketId());

                vo.setTicketStatus(ticket.status());
            }
        }

        return result;
    }

    // 停止当前用户正在生成的回答
    public void cancelStream(
            Long sessionId,
            Long userId) {

        ChatSession session =
                sessionMapper.selectById(sessionId);

        if (session == null) {
            throw new BusinessException(
                    404, "会话不存在");
        }

        if (!userId.equals(session.getUserId())) {
            throw new BusinessException(
                    403, "无权操作此会话");
        }

        ChatStreamRegistry.StreamState state =
                streamRegistry.getState(sessionId);

        if (state == null) {
            throw new BusinessException(
                    409, "当前会话没有正在运行的回答");
        }

        if (state == ChatStreamRegistry.StreamState.COMPLETING) {
            throw new BusinessException(
                    409, "AI回答已完成，正在保存，无法取消");
        }

        if (state == ChatStreamRegistry.StreamState.SUCCEEDED) {
            throw new BusinessException(
                    409, "AI回答已经完成");
        }

        if (state == ChatStreamRegistry.StreamState.CANCELLED
                || state == ChatStreamRegistry.StreamState.FAILED) {
            throw new BusinessException(
                    409, "当前任务已经结束");
        }

        if (!streamRegistry.cancel(sessionId)) {
            throw new BusinessException(
                    409, "任务状态已发生变化，请刷新后重试");
        }
    }
}
