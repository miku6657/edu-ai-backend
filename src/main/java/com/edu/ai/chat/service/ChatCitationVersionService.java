package com.edu.ai.chat.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.edu.ai.chat.dto.ChatMessageVO;
import com.edu.ai.chat.entity.ChatMessage;
import com.edu.ai.document.entity.CourseDocument;
import com.edu.ai.document.mapper.CourseDocumentMapper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.*;

@Service
public class ChatCitationVersionService {

    private static final Logger log =
            LoggerFactory.getLogger(
                    ChatCitationVersionService.class
            );

    private final CourseDocumentMapper documentMapper;
    private final ObjectMapper objectMapper;

    public ChatCitationVersionService(
            CourseDocumentMapper documentMapper,
            ObjectMapper objectMapper) {

        this.documentMapper = documentMapper;
        this.objectMapper = objectMapper;
    }

    /**
     * 为历史聊天消息补充引用版本信息。
     *
     * 调用前必须完成会话归属及课程访问权限校验。
     */
    @Transactional(readOnly = true)
    public List<ChatMessageVO> enrichMessages(
            Long courseId,
            List<ChatMessage> messages) {

        if (messages == null || messages.isEmpty()) {
            return List.of();
        }

        Map<Long, Set<Long>> messageDocumentIds =
                new HashMap<>();

        Set<Long> allDocumentIds =
                new LinkedHashSet<>();

        // 1. 收集所有历史回答引用的文档ID
        for (ChatMessage message : messages) {

            if (!"assistant".equalsIgnoreCase(
                    message.getRole())) {
                continue;
            }

            Set<Long> ids = parseDocumentIds(
                    message.getSourcesJson()
            );

            messageDocumentIds.put(
                    message.getId(),
                    ids
            );

            allDocumentIds.addAll(ids);
        }

        // 没有文档引用时直接返回。
        if (allDocumentIds.isEmpty()) {
            return messages.stream()
                    .map(ChatMessageVO::from)
                    .toList();
        }

        // 2. 批量查询原始引用文档。
        // 限制courseId，防止跨课程查询。
        List<CourseDocument> referencedDocuments =
                documentMapper.selectList(
                        new LambdaQueryWrapper<CourseDocument>()
                                .eq(
                                        CourseDocument::getCourseId,
                                        courseId
                                )
                                .in(
                                        CourseDocument::getId,
                                        allDocumentIds
                                )
                );

        Map<Long, CourseDocument> referencedMap =
                new HashMap<>();

        Set<Long> resourceIds =
                new LinkedHashSet<>();

        for (CourseDocument doc : referencedDocuments) {

            referencedMap.put(
                    doc.getId(),
                    doc
            );

            if (doc.getResourceId() != null) {
                resourceIds.add(doc.getResourceId());
            }
        }

        // 3. 批量查询资源的当前生效版本
        Map<Long, CourseDocument> currentMap =
                new HashMap<>();

        if (!resourceIds.isEmpty()) {

            List<CourseDocument> currentDocuments =
                    documentMapper.selectList(
                            new LambdaQueryWrapper<CourseDocument>()
                                    .eq(
                                            CourseDocument::getCourseId,
                                            courseId
                                    )
                                    .eq(
                                            CourseDocument::getStatus,
                                            "READY"
                                    )
                                    .eq(
                                            CourseDocument::getVersionStatus,
                                            "CURRENT"
                                    )
                                    .in(
                                            CourseDocument::getResourceId,
                                            resourceIds
                                    )
                    );

            for (CourseDocument doc : currentDocuments) {
                currentMap.put(
                        doc.getResourceId(),
                        doc
                );
            }
        }

        // 4. 为每条历史消息组装版本信息
        List<ChatMessageVO> result =
                new ArrayList<>(messages.size());

        for (ChatMessage message : messages) {

            ChatMessageVO vo =
                    ChatMessageVO.from(message);

            Set<Long> documentIds =
                    messageDocumentIds.getOrDefault(
                            message.getId(),
                            Set.of()
                    );

            List<ChatMessageVO.CitationVersion> versions =
                    new ArrayList<>();

            for (Long documentId : documentIds) {

                CourseDocument original =
                        referencedMap.get(documentId);

                if (original == null) {
                    continue;
                }

                CourseDocument current =
                        currentMap.get(
                                original.getResourceId()
                        );

                versions.add(
                        buildCitationVersion(
                                original,
                                current
                        )
                );
            }

            vo.setCitationVersions(versions);

            result.add(vo);
        }

        return result;
    }

    /**
     * 构建单份引用文档的版本信息。
     */
    private ChatMessageVO.CitationVersion
    buildCitationVersion(
            CourseDocument original,
            CourseDocument current) {

        boolean outdated =
                current != null
                        && !Objects.equals(
                                original.getId(),
                                current.getId()
                        )
                        && original.getVersionNo() != null
                        && current.getVersionNo() != null
                        && current.getVersionNo()
                                > original.getVersionNo();

        return new ChatMessageVO.CitationVersion(

                original.getId().toString(),

                original.getOriginalName(),

                original.getResourceId() == null
                        ? null
                        : original.getResourceId().toString(),

                original.getVersionNo(),

                original.getVersionStatus(),

                current == null
                        ? null
                        : current.getId().toString(),

                current == null
                        ? null
                        : current.getOriginalName(),

                current == null
                        ? null
                        : current.getVersionNo(),

                outdated
        );
    }

    /**
     * 兼容数字类型和字符串类型的历史文档ID。
     */
    private Set<Long> parseDocumentIds(
            String sourcesJson) {

        Set<Long> ids = new LinkedHashSet<>();

        if (sourcesJson == null
                || sourcesJson.isBlank()) {
            return ids;
        }

        try {
            JsonNode sources =
                    objectMapper.readTree(sourcesJson);

            if (sources == null || !sources.isArray()) {
                return ids;
            }

            for (JsonNode source : sources) {

                if (!source.isObject()) {
                    continue;
                }

                JsonNode idNode =
                        source.get("documentId");

                if (idNode == null || idNode.isNull()) {
                    continue;
                }

                if (!idNode.isString()
                        && !idNode.isIntegralNumber()) {
                    continue;
                }

                String value;

                if (idNode.isString()) {
                    value = idNode.asString();
                } else {
                    value = idNode.toString();
                }

                if (value == null
                        || !value.matches("[1-9][0-9]*")) {
                    continue;
                }

                try {
                    ids.add(Long.parseLong(value));
                } catch (NumberFormatException ignored) {
                    // 忽略超出Long范围的异常ID。
                }
            }

        } catch (Exception e) {

            log.warn(
                    "历史消息引用JSON解析失败: {}",
                    e.getClass().getSimpleName()
            );
        }

        return ids;
    }
}
