package com.edu.ai.document.service;

import com.edu.ai.common.BusinessException;
import com.edu.ai.course.service.CoursePermissionService;
import com.edu.ai.document.dto.DocumentSummaryVO;
import com.edu.ai.document.entity.CourseDocument;
import com.edu.ai.document.mapper.CourseDocumentMapper;
import com.edu.ai.system.service.UserModelResolver;
import com.edu.ai.system.service.UserModelResolver.UserModelConfig;

import org.springframework.stereotype.Service;

import tools.jackson.databind.JsonNode;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

@Service
public class DocumentSummaryService {

    private static final ZoneId APP_ZONE =
            ZoneId.of("Asia/Shanghai");

    private static final int MAX_SOURCE_CHARS = 1_000_000;

    private static final int MAX_SOURCE_CHUNKS = 2000;

    private final CoursePermissionService permissions;

    private final CourseDocumentMapper documentMapper;

    private final DocumentSummarySourceReader sourceReader;

    private final AiDocumentSummaryClient aiClient;

    private final UserModelResolver modelResolver;

    private final DocumentSummaryPersistenceService persistence;

    public DocumentSummaryService(
            CoursePermissionService permissions,
            CourseDocumentMapper documentMapper,
            DocumentSummarySourceReader sourceReader,
            AiDocumentSummaryClient aiClient,
            UserModelResolver modelResolver,
            DocumentSummaryPersistenceService persistence) {

        this.permissions = permissions;
        this.documentMapper = documentMapper;
        this.sourceReader = sourceReader;
        this.aiClient = aiClient;
        this.modelResolver = modelResolver;
        this.persistence = persistence;
    }

    /**
     * AI生成文档摘要和章节大纲。
     */
    public DocumentSummaryVO generate(
            Long courseId,
            Long documentId,
            Long userId) {

        permissions.requireManage(
                courseId,
                userId
        );

        CourseDocument document =
                requireCurrentDocument(
                        courseId,
                        documentId
                );

        List<DocumentSummarySourceReader.SourceChunk> chunks =
                sourceReader.read(
                        courseId,
                        documentId
                );

        if (chunks.isEmpty()) {
            throw new BusinessException(
                    409,
                    "当前资料没有知识切片，请先完成PDF解析"
            );
        }

        if (chunks.size() > MAX_SOURCE_CHUNKS) {
            throw new BusinessException(
                    413,
                    "资料切片数量超过2000条，请拆分资料或分册上传"
            );
        }

        int totalChars =
                sourceReader.totalCharacters(chunks);

        if (totalChars > MAX_SOURCE_CHARS) {
            throw new BusinessException(
                    413,
                    "资料正文超过1000000字符的单次处理上限，请拆分资料或分册上传"
            );
        }

        String fingerprint =
                sourceReader.fingerprint(chunks);

        UserModelConfig model =
                modelResolver.resolveChatModel(userId);

        AiDocumentSummaryClient.GeneratedSummary generated =
                aiClient.generate(
                        document.getOriginalName(),
                        chunks,
                        model.chatModel(),
                        model.chatApiKey(),
                        model.chatBaseUrl()
                );

        return persistence.saveDraft(
                courseId,
                documentId,
                userId,
                generated.summary(),
                generated.outline(),
                fingerprint,
                chunks.size()
        );
    }

    /**
     * 查询当前可见摘要。
     *
     * 管理人员可以看到待确认版本；
     * 学生只能看到已确认版本。
     */
    public DocumentSummaryVO get(
            Long courseId,
            Long documentId,
            Long userId) {

        permissions.requireActiveRead(
                courseId,
                userId
        );

        CourseDocument document =
                requireCurrentDocument(
                        courseId,
                        documentId
                );

        boolean canManage =
                permissions.canManage(
                        courseId,
                        userId
                );

        if (!canManage
                && document.getValidUntil() != null
                && document.getValidUntil().isBefore(
                        LocalDate.now(APP_ZONE)
                )) {

            throw new BusinessException(
                    403,
                    "资料已经过期，无法查看摘要"
            );
        }

        return persistence.findLatest(
                courseId,
                documentId,
                canManage
        );
    }

    /**
     * 人工编辑待确认摘要。
     */
    public DocumentSummaryVO updateDraft(
            Long courseId,
            Long documentId,
            Long summaryId,
            Long userId,
            String summary,
            JsonNode outline) {

        permissions.requireManage(
                courseId,
                userId
        );

        return persistence.updateDraft(
                courseId,
                documentId,
                summaryId,
                userId,
                summary,
                outline
        );
    }

    /**
     * 负责人确认摘要。
     */
    public DocumentSummaryVO confirm(
            Long courseId,
            Long documentId,
            Long summaryId,
            Long userId) {

        permissions.requireManage(
                courseId,
                userId
        );

        return persistence.confirm(
                courseId,
                documentId,
                summaryId,
                userId
        );
    }

    private CourseDocument requireCurrentDocument(
            Long courseId,
            Long documentId) {

        CourseDocument document =
                documentMapper.selectById(documentId);

        if (document == null
                || !courseId.equals(document.getCourseId())
                || !"READY".equals(document.getStatus())
                || !"CURRENT".equals(document.getVersionStatus())
                || !"APPROVED".equals(document.getReviewStatus())
                || !"NORMAL".equals(document.getQualityStatus())) {

            throw new BusinessException(
                    409,
                    "当前资料尚未发布或不可用"
            );
        }

        return document;
    }
}
