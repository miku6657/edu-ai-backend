package com.edu.ai.document.service;

import com.edu.ai.audit.service.ContentOperationLogService;
import com.edu.ai.common.BusinessException;
import com.edu.ai.document.dto.DocumentSummaryVO;
import com.edu.ai.document.entity.CourseDocument;
import com.edu.ai.document.mapper.CourseDocumentMapper;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

@Service
public class DocumentSummaryPersistenceService {

    private static final ZoneId APP_ZONE =
            ZoneId.of("Asia/Shanghai");

    private final JdbcTemplate jdbc;

    private final CourseDocumentMapper documentMapper;

    private final DocumentSummarySourceReader sourceReader;

    private final ObjectMapper objectMapper;

    private final ContentOperationLogService operationLogService;

    public DocumentSummaryPersistenceService(
            JdbcTemplate jdbc,
            CourseDocumentMapper documentMapper,
            DocumentSummarySourceReader sourceReader,
            ObjectMapper objectMapper,
            ContentOperationLogService operationLogService) {

        this.jdbc = jdbc;
        this.documentMapper = documentMapper;
        this.sourceReader = sourceReader;
        this.objectMapper = objectMapper;
        this.operationLogService = operationLogService;
    }

    /**
     * 保存AI生成的待确认摘要。
     */
    @Transactional(rollbackFor = Exception.class)
    public DocumentSummaryVO saveDraft(
            Long courseId,
            Long documentId,
            Long userId,
            String summary,
            JsonNode outline,
            String fingerprint,
            int chunkCount) {

        lockDocument(courseId, documentId);

        CourseDocument document =
                requireCurrentDocument(courseId, documentId);

        List<DocumentSummarySourceReader.SourceChunk> currentChunks =
                sourceReader.read(courseId, documentId);

        String currentFingerprint =
                sourceReader.fingerprint(currentChunks);

        if (!currentFingerprint.equals(fingerprint)) {

            throw new BusinessException(
                    409,
                    "资料内容已经发生变化，请重新生成摘要"
            );
        }

        if (summary == null
                || summary.isBlank()
                || outline == null
                || !outline.isArray()
                || outline.isEmpty()) {

            throw new BusinessException(
                    400,
                    "摘要和章节大纲不能为空"
            );
        }

        jdbc.update(
                """
                UPDATE course_document_summary

                SET status = 'SUPERSEDED',
                    update_time = NOW()

                WHERE course_id = ?
                  AND document_id = ?
                  AND status = 'DRAFT'
                """,
                courseId,
                documentId
        );

        Integer maxRevision = jdbc.queryForObject(
                """
                SELECT COALESCE(MAX(revision), 0)

                FROM course_document_summary

                WHERE course_id = ?
                  AND document_id = ?
                """,
                Integer.class,
                courseId,
                documentId
        );

        int revision =
                (maxRevision == null ? 0 : maxRevision) + 1;

        jdbc.update(
                """
                INSERT INTO course_document_summary (
                    course_id,
                    document_id,
                    revision,
                    summary,
                    outline_json,
                    source_fingerprint,
                    source_chunk_count,
                    status,
                    generated_by,
                    generated_at,
                    create_time,
                    update_time
                )
                VALUES (
                    ?, ?, ?, ?, ?, ?, ?,
                    'DRAFT',
                    ?,
                    NOW(),
                    NOW(),
                    NOW()
                )
                """,
                courseId,
                documentId,
                revision,
                summary,
                outline.toString(),
                fingerprint,
                chunkCount,
                userId
        );

        Long summaryId = jdbc.queryForObject(
                "SELECT LAST_INSERT_ID()",
                Long.class
        );

        operationLogService.write(
                courseId,
                documentId,
                userId,
                "DOCUMENT_SUMMARY_GENERATE",
                "生成课程资料摘要",
                "为资料《" + document.getOriginalName()
                        + "》生成第" + revision + "版摘要和章节大纲",
                null,
                "summaryId=" + summaryId
                        + ", revision=" + revision
                        + ", status=DRAFT"
        );

        return findById(summaryId);
    }

    /**
     * 确认AI生成的摘要。
     */
    @Transactional(rollbackFor = Exception.class)
    public DocumentSummaryVO confirm(
            Long courseId,
            Long documentId,
            Long summaryId,
            Long userId) {

        lockDocument(courseId, documentId);

        CourseDocument document =
                requireCurrentDocument(courseId, documentId);

        SummaryRow row = findRow(
                courseId,
                documentId,
                summaryId
        );

        if (row == null) {

            throw new BusinessException(
                    404,
                    "待确认摘要不存在"
            );
        }

        if ("CONFIRMED".equals(row.status())) {
            return findById(summaryId);
        }

        if (!"DRAFT".equals(row.status())) {

            throw new BusinessException(
                    409,
                    "当前摘要不是待确认状态"
            );
        }

        List<DocumentSummarySourceReader.SourceChunk> chunks =
                sourceReader.read(courseId, documentId);

        String currentFingerprint =
                sourceReader.fingerprint(chunks);

        if (!currentFingerprint.equals(row.fingerprint())) {

            throw new BusinessException(
                    409,
                    "资料内容已发生变化，该摘要不能发布，请重新生成"
            );
        }

        // 原有已发布摘要归档。
        jdbc.update(
                """
                UPDATE course_document_summary

                SET status = 'ARCHIVED',
                    update_time = NOW()

                WHERE course_id = ?
                  AND document_id = ?
                  AND status = 'CONFIRMED'
                """,
                courseId,
                documentId
        );

        int updated = jdbc.update(
                """
                UPDATE course_document_summary

                SET status = 'CONFIRMED',
                    confirmed_by = ?,
                    confirmed_at = NOW(),
                    update_time = NOW()

                WHERE id = ?
                  AND course_id = ?
                  AND document_id = ?
                  AND status = 'DRAFT'
                """,
                userId,
                summaryId,
                courseId,
                documentId
        );

        if (updated != 1) {

            throw new BusinessException(
                    409,
                    "摘要状态已经变化，请刷新后重试"
            );
        }

        operationLogService.write(
                courseId,
                documentId,
                userId,
                "DOCUMENT_SUMMARY_CONFIRM",
                "确认课程资料摘要",
                "确认资料《" + document.getOriginalName()
                        + "》的第" + row.revision() + "版摘要",
                "status=DRAFT",
                "status=CONFIRMED"
        );

        return findById(summaryId);
    }

    /**
     * 查询当前可展示的摘要。
     *
     * 管理人员优先查看待确认版本；
     * 学生只能查看已确认版本。
     */
    @Transactional(readOnly = true)
    public DocumentSummaryVO findLatest(
            Long courseId,
            Long documentId,
            boolean canManage) {

        String sql = canManage
                ? """
                    SELECT id
                    FROM course_document_summary
                    WHERE course_id = ?
                      AND document_id = ?
                      AND status IN ('DRAFT', 'CONFIRMED')
                    ORDER BY
                      CASE status
                        WHEN 'DRAFT' THEN 0
                        ELSE 1
                      END,
                      revision DESC
                    LIMIT 1
                  """
                : """
                    SELECT id
                    FROM course_document_summary
                    WHERE course_id = ?
                      AND document_id = ?
                      AND status = 'CONFIRMED'
                    ORDER BY revision DESC
                    LIMIT 1
                  """;

        List<Long> ids = jdbc.queryForList(
                sql,
                Long.class,
                courseId,
                documentId
        );

        return ids.isEmpty()
                ? null
                : findById(ids.get(0));
    }

    private void lockDocument(
            Long courseId,
            Long documentId) {

        List<Long> ids = jdbc.queryForList(
                """
                SELECT id

                FROM course_document

                WHERE id = ?
                  AND course_id = ?

                FOR UPDATE
                """,
                Long.class,
                documentId,
                courseId
        );

        if (ids.isEmpty()) {

            throw new BusinessException(
                    404,
                    "课程资料不存在"
            );
        }
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
                    "只有正常发布的当前资料版本才能生成或确认摘要"
            );
        }

        if (document.getValidUntil() != null
                && document.getValidUntil().isBefore(
                        LocalDate.now(APP_ZONE)
                )) {

            throw new BusinessException(
                    409,
                    "资料已经过期，不能发布摘要"
            );
        }

        return document;
    }

    private record SummaryRow(
            Integer revision,
            String status,
            String fingerprint
    ) {}

    private SummaryRow findRow(
            Long courseId,
            Long documentId,
            Long summaryId) {

        List<SummaryRow> rows = jdbc.query(
                """
                SELECT
                    revision,
                    status,
                    source_fingerprint

                FROM course_document_summary

                WHERE id = ?
                  AND course_id = ?
                  AND document_id = ?
                """,

                (rs, rowNum) -> new SummaryRow(
                        rs.getInt("revision"),
                        rs.getString("status"),
                        rs.getString("source_fingerprint")
                ),

                summaryId,
                courseId,
                documentId
        );

        return rows.isEmpty() ? null : rows.get(0);
    }

    private DocumentSummaryVO findById(Long id) {

        return jdbc.query(
                """
                SELECT *

                FROM course_document_summary

                WHERE id = ?
                """,

                rs -> {

                    if (!rs.next()) {
                        throw new BusinessException(
                                404,
                                "摘要记录不存在"
                        );
                    }

                    try {

                        return new DocumentSummaryVO(
                                rs.getString("id"),
                                rs.getString("course_id"),
                                rs.getString("document_id"),
                                rs.getInt("revision"),
                                rs.getString("summary"),
                                objectMapper.readTree(
                                        rs.getString("outline_json")
                                ),
                                rs.getString("status"),
                                rs.getInt("source_chunk_count"),
                                rs.getString("generated_by"),
                                rs.getTimestamp("generated_at")
                                        .toLocalDateTime(),
                                rs.getObject("confirmed_by") == null
                                        ? null
                                        : rs.getString("confirmed_by"),
                                rs.getTimestamp("confirmed_at") == null
                                        ? null
                                        : rs.getTimestamp("confirmed_at")
                                                .toLocalDateTime()
                        );

                    } catch (Exception e) {

                        throw new IllegalStateException(
                                "摘要记录解析失败",
                                e
                        );
                    }
                },

                id
        );
    }
}
