package com.edu.ai.document.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;

import com.edu.ai.common.BusinessException;

import com.edu.ai.course.service.CoursePermissionService;

import com.edu.ai.document.dto.ChunkKnowledgeProgressVO;
import com.edu.ai.document.dto.DocumentReviewDetailVO;
import com.edu.ai.document.dto.DocumentReviewRecordVO;

import com.edu.ai.document.entity.CourseDocument;
import com.edu.ai.document.entity.DocumentReviewRecord;

import com.edu.ai.document.mapper.CourseDocumentMapper;
import com.edu.ai.document.mapper.DocumentReviewRecordMapper;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

@Service
public class DocumentReviewService {

    private static final String DRAFT = "DRAFT";

    private static final String PENDING = "PENDING";

    private static final String APPROVED = "APPROVED";

    private static final String REJECTED = "REJECTED";

    private final JdbcTemplate jdbc;

    private final CourseDocumentMapper documentMapper;

    private final DocumentReviewRecordMapper recordMapper;

    private final CoursePermissionService permissions;

    private final ChunkKnowledgeService knowledgeService;

    private final DocumentQualityService qualityService;

    public DocumentReviewService(

            JdbcTemplate jdbc,

            CourseDocumentMapper documentMapper,

            DocumentReviewRecordMapper recordMapper,

            CoursePermissionService permissions,

            ChunkKnowledgeService knowledgeService,

            DocumentQualityService qualityService) {

        this.jdbc = jdbc;
        this.documentMapper = documentMapper;
        this.recordMapper = recordMapper;
        this.permissions = permissions;
        this.knowledgeService = knowledgeService;
        this.qualityService = qualityService;
    }

    /**
     * 校验课程管理权限及文档归属。
     */
    private CourseDocument requireDocument(

            Long courseId,

            Long documentId,

            Long userId) {

        permissions.requireManage(
                courseId,
                userId
        );

        permissions.requireActiveRead(
                courseId,
                userId
        );

        CourseDocument document =
                documentMapper.selectById(documentId);

        if (document == null
                || !Objects.equals(
                        document.getCourseId(),
                        courseId
                )) {

            throw new BusinessException(
                    404,
                    "当前课程中不存在该文档"
            );
        }

        if ("DELETING".equals(document.getStatus())
                || "DELETE_FAILED".equals(document.getStatus())
                || "DELETED".equals(document.getStatus())) {

            throw new BusinessException(
                    409,
                    "当前文档不可进行审核操作"
            );
        }

        return document;
    }

    /**
     * 加锁顺序：
     *
     * 课程 -> 资源根记录 -> 文档记录。
     *
     * 与课程知识点修改、文档版本管理协调。
     */
    private CourseDocument lockDocument(

            Long courseId,

            Long documentId,

            Long userId) {

        CourseDocument source =
                requireDocument(
                        courseId,
                        documentId,
                        userId
                );

        List<Integer> courseStatuses =
                jdbc.queryForList(
                        """
                        SELECT status
                        FROM course
                        WHERE id = ?
                        FOR UPDATE
                        """,
                        Integer.class,
                        courseId
                );

        if (courseStatuses.isEmpty()
                || !Integer.valueOf(1)
                           .equals(courseStatuses.get(0))) {

            throw new BusinessException(
                    409,
                    "课程当前不可用"
            );
        }

        if (source.getResourceId() == null) {

            throw new BusinessException(
                    409,
                    "资源尚未完成版本初始化"
            );
        }

        List<Long> roots =
                jdbc.queryForList(
                        """
                        SELECT id
                        FROM course_document
                        WHERE id = ?
                          AND course_id = ?
                        FOR UPDATE
                        """,
                        Long.class,
                        source.getResourceId(),
                        courseId
                );

        if (roots.isEmpty()) {

            throw new BusinessException(
                    409,
                    "资源根记录不存在"
            );
        }

        CourseDocument document =
                documentMapper.selectOne(
                        new LambdaQueryWrapper<CourseDocument>()
                                .eq(
                                        CourseDocument::getId,
                                        documentId
                                )
                                .eq(
                                        CourseDocument::getCourseId,
                                        courseId
                                )
                                .last("LIMIT 1 FOR UPDATE")
                );

        if (document == null) {

            throw new BusinessException(
                    404,
                    "文档不存在"
            );
        }

        if (!"READY".equals(document.getStatus())) {

            throw new BusinessException(
                    409,
                    "文档尚未完成解析或已经不可用"
            );
        }

        if (!PENDING.equals(
                document.getVersionStatus())) {

            throw new BusinessException(
                    409,
                    "仅待生效版本允许进行发布审核"
            );
        }

        return document;
    }

    /**
     * 校验知识点关联是否完整。
     */
    private ChunkKnowledgeProgressVO
            requireKnowledgeCompleted(

            Long courseId,

            Long documentId,

            Long userId) {

        ChunkKnowledgeProgressVO progress =
                knowledgeService.getProgress(

                        courseId,

                        documentId,

                        userId
                );

        if (!progress.completed()) {

            throw new BusinessException(
                    409,
                    "知识切片关联尚未全部完成，"
                    + "请先处理未完成的切片及待确认关联"
            );
        }

        return progress;
    }

    /**
     * 校验并整理审核意见。
     */
    private String normalizeComment(

            String comment,

            boolean required) {

        String value = comment == null
                ? ""
                : comment.trim();

        if (required && value.isBlank()) {

            throw new BusinessException(
                    400,
                    "请填写审核驳回原因"
            );
        }

        if (value.length() > 1000) {

            throw new BusinessException(
                    400,
                    "审核意见不能超过1000个字符"
            );
        }

        return value.isBlank()
                ? null
                : value;
    }

    /**
     * 保存审核历史。
     */
    private void saveRecord(

            Long courseId,

            Long documentId,

            Long userId,

            String action,

            String previousStatus,

            String nextStatus,

            String comment) {

        DocumentReviewRecord record =
                new DocumentReviewRecord();

        record.setCourseId(courseId);

        record.setDocumentId(documentId);

        record.setOperatorId(userId);

        record.setAction(action);

        record.setPreviousStatus(previousStatus);

        record.setNextStatus(nextStatus);

        record.setComment(comment);

        record.setCreateTime(LocalDateTime.now());

        if (recordMapper.insert(record) != 1) {

            throw new BusinessException(
                    500,
                    "保存审核历史失败"
            );
        }
    }

    /**
     * 检查是否存在更新的生效版本。
     */
    private void requireNoNewerVersion(
            CourseDocument document) {

        List<Integer> versions =
                jdbc.queryForList(
                        """
                        SELECT version_no
                        FROM course_document
                        WHERE course_id = ?
                          AND resource_id = ?
                          AND version_status = 'CURRENT'
                        FOR UPDATE
                        """,
                        Integer.class,
                        document.getCourseId(),
                        document.getResourceId()
                );

        if (versions.size() > 1) {

            throw new BusinessException(
                    409,
                    "资源存在多个生效版本，请检查数据"
            );
        }

        boolean newerExists =
                versions.stream()
                        .anyMatch(
                                version ->
                                        version >=
                                        document.getVersionNo()
                        );

        if (newerExists) {

            throw new BusinessException(
                    409,
                    "已有相同或更新版本生效，"
                    + "当前版本不能发布"
            );
        }
    }

    /**
     * 发布前禁止与活动Embedding迁移并行。
     */
    private void requireNoActiveMigration(
            Long courseId) {

        Long count = jdbc.queryForObject(
                """
                SELECT COUNT(*)
                FROM course_embedding_migration
                WHERE course_id = ?
                  AND status IN ('PENDING', 'RUNNING')
                """,
                Long.class,
                courseId
        );

        if (count != null && count > 0) {

            throw new BusinessException(
                    409,
                    "课程正在进行Embedding迁移，"
                    + "请等待迁移完成后再发布资料"
            );
        }
    }

    /**
     * 提交审核。
     */
    @Transactional(
            rollbackFor = Exception.class,
            isolation = Isolation.READ_COMMITTED
    )
    public DocumentReviewDetailVO submit(

            Long courseId,

            Long documentId,

            Long userId,

            String comment) {

        String normalizedComment =
                normalizeComment(
                        comment,
                        false
                );

        CourseDocument document =
                lockDocument(
                        courseId,
                        documentId,
                        userId
                );

        String previousStatus =
                document.getReviewStatus();

        if (!DRAFT.equals(previousStatus)
                && !REJECTED.equals(previousStatus)) {

            throw new BusinessException(
                    409,
                    "当前审核状态不允许提交审核"
            );
        }

        requireKnowledgeCompleted(
                courseId,
                documentId,
                userId
        );

        // F2-05：质量抽检必须处理完成。
        qualityService.requirePublishable(
                courseId,
                documentId
        );

        requireNoNewerVersion(document);

        int updated = jdbc.update(
                """
                UPDATE course_document
                SET review_status = 'PENDING',
                    review_submitter_id = ?,
                    review_submit_time = NOW(),
                    reviewer_id = NULL,
                    review_time = NULL,
                    review_comment = NULL,
                    update_time = NOW()
                WHERE id = ?
                  AND course_id = ?
                  AND status = 'READY'
                  AND version_status = 'PENDING'
                  AND review_status = ?
                """,
                userId,
                documentId,
                courseId,
                previousStatus
        );

        if (updated != 1) {

            throw new BusinessException(
                    409,
                    "审核状态已经发生变化"
            );
        }

        saveRecord(

                courseId,

                documentId,

                userId,

                "SUBMIT",

                previousStatus,

                PENDING,

                normalizedComment
        );

        return getDetail(
                courseId,
                documentId,
                userId
        );
    }

    /**
     * 审核决定。
     */
    @Transactional(
            rollbackFor = Exception.class,
            isolation = Isolation.READ_COMMITTED
    )
    public DocumentReviewDetailVO decide(

            Long courseId,

            Long documentId,

            Long userId,

            String decision,

            String comment) {

        String action = decision == null
                ? ""
                : decision.trim()
                          .toUpperCase(Locale.ROOT);

        if (!"APPROVE".equals(action)
                && !"REJECT".equals(action)) {

            throw new BusinessException(
                    400,
                    "审核决定必须为APPROVE或REJECT"
            );
        }

        String normalizedComment =
                normalizeComment(
                        comment,
                        "REJECT".equals(action)
                );

        CourseDocument document =
                lockDocument(

                        courseId,

                        documentId,

                        userId
                );

        if (!PENDING.equals(
                document.getReviewStatus())) {

            throw new BusinessException(
                    409,
                    "当前文档不处于待审核状态"
            );
        }

        if ("APPROVE".equals(action)) {

            approve(
                    document,
                    userId,
                    normalizedComment
            );

        } else {

            reject(
                    document,
                    userId,
                    normalizedComment
            );
        }

        return getDetail(
                courseId,
                documentId,
                userId
        );
    }

    /**
     * 审核通过并激活版本。
     */
    private void approve(

            CourseDocument document,

            Long userId,

            String comment) {

        Long courseId =
                document.getCourseId();

        Long documentId =
                document.getId();

        // 审核时再次验证，不能只依赖提交审核时的结果。
        requireKnowledgeCompleted(

                courseId,

                documentId,

                userId
        );

        // 发布前再次检查质量问题。
        qualityService.requirePublishable(

                courseId,

                documentId
        );

        requireNoActiveMigration(courseId);

        requireNoNewerVersion(document);

        // 原生效版本转为历史版本。
        jdbc.update(
                """
                UPDATE course_document
                SET version_status = 'HISTORY',
                    update_time = NOW()
                WHERE course_id = ?
                  AND resource_id = ?
                  AND version_status = 'CURRENT'
                  AND id <> ?
                """,
                courseId,
                document.getResourceId(),
                documentId
        );

        // 当前审核版本正式生效。
        int updated = jdbc.update(
                """
                UPDATE course_document
                SET review_status = 'APPROVED',
                    version_status = 'CURRENT',
                    reviewer_id = ?,
                    review_time = NOW(),
                    review_comment = ?,
                    update_time = NOW()
                WHERE id = ?
                  AND course_id = ?
                  AND status = 'READY'
                  AND version_status = 'PENDING'
                  AND review_status = 'PENDING'
                """,
                userId,
                comment,
                documentId,
                courseId
        );

        if (updated != 1) {

            throw new BusinessException(
                    409,
                    "审核发布失败，文档状态已经变化"
            );
        }

        saveRecord(

                courseId,

                documentId,

                userId,

                "APPROVE",

                PENDING,

                APPROVED,

                comment
        );
    }

    /**
     * 审核驳回。
     */
    private void reject(

            CourseDocument document,

            Long userId,

            String comment) {

        int updated = jdbc.update(
                """
                UPDATE course_document
                SET review_status = 'REJECTED',
                    reviewer_id = ?,
                    review_time = NOW(),
                    review_comment = ?,
                    update_time = NOW()
                WHERE id = ?
                  AND course_id = ?
                  AND status = 'READY'
                  AND version_status = 'PENDING'
                  AND review_status = 'PENDING'
                """,
                userId,
                comment,
                document.getId(),
                document.getCourseId()
        );

        if (updated != 1) {

            throw new BusinessException(
                    409,
                    "审核驳回失败，审核状态已经变化"
            );
        }

        saveRecord(

                document.getCourseId(),

                document.getId(),

                userId,

                "REJECT",

                PENDING,

                REJECTED,

                comment
        );
    }

    /**
     * 撤回审核申请。
     */
    @Transactional(
            rollbackFor = Exception.class,
            isolation = Isolation.READ_COMMITTED
    )
    public DocumentReviewDetailVO withdraw(

            Long courseId,

            Long documentId,

            Long userId,

            String comment) {

        String normalizedComment =
                normalizeComment(
                        comment,
                        false
                );

        CourseDocument document =
                lockDocument(

                        courseId,

                        documentId,

                        userId
                );

        if (!PENDING.equals(
                document.getReviewStatus())) {

            throw new BusinessException(
                    409,
                    "仅待审核文档允许撤回"
            );
        }

        int updated = jdbc.update(
                """
                UPDATE course_document
                SET review_status = 'DRAFT',
                    review_submitter_id = NULL,
                    review_submit_time = NULL,
                    reviewer_id = NULL,
                    review_time = NULL,
                    review_comment = NULL,
                    update_time = NOW()
                WHERE id = ?
                  AND course_id = ?
                  AND status = 'READY'
                  AND version_status = 'PENDING'
                  AND review_status = 'PENDING'
                """,
                documentId,
                courseId
        );

        if (updated != 1) {

            throw new BusinessException(
                    409,
                    "撤回失败，审核状态已经变化"
            );
        }

        saveRecord(

                courseId,

                documentId,

                userId,

                "WITHDRAW",

                PENDING,

                DRAFT,

                normalizedComment
        );

        return getDetail(

                courseId,

                documentId,

                userId
        );
    }

    /**
     * 查询审核详情及最近100条历史记录。
     */
    @Transactional(readOnly = true)
    public DocumentReviewDetailVO getDetail(

            Long courseId,

            Long documentId,

            Long userId) {

        CourseDocument document =
                requireDocument(

                        courseId,

                        documentId,

                        userId
                );

        boolean ready =
                "READY".equals(
                        document.getStatus()
                );

        boolean pendingVersion =
                PENDING.equals(
                        document.getVersionStatus()
                );

        String reviewStatus =
                document.getReviewStatus();

        ChunkKnowledgeProgressVO progress =
                ready
                        ? knowledgeService.getProgress(
                                courseId,
                                documentId,
                                userId
                          )
                        : null;

        boolean canSubmit =
                ready
                && pendingVersion
                && (
                        DRAFT.equals(reviewStatus)
                        || REJECTED.equals(reviewStatus)
                )
                && progress != null
                && progress.completed();

        boolean canReview =
                ready
                && pendingVersion
                && PENDING.equals(reviewStatus);

        boolean canWithdraw =
                canReview;

        boolean published =
                ready
                && "CURRENT".equals(
                        document.getVersionStatus()
                )
                && APPROVED.equals(reviewStatus);

        List<DocumentReviewRecordVO> records =
                recordMapper.selectList(

                        new LambdaQueryWrapper<DocumentReviewRecord>()

                                .eq(
                                        DocumentReviewRecord::getCourseId,
                                        courseId
                                )

                                .eq(
                                        DocumentReviewRecord::getDocumentId,
                                        documentId
                                )

                                .orderByDesc(
                                        DocumentReviewRecord::getId
                                )

                                .last("LIMIT 100")
                )
                .stream()
                .map(DocumentReviewRecordVO::from)
                .toList();

        return new DocumentReviewDetailVO(

                documentId.toString(),

                courseId.toString(),

                stringId(document.getResourceId()),

                document.getOriginalName(),

                document.getVersionNo(),

                document.getStatus(),

                document.getVersionStatus(),

                reviewStatus,

                stringId(
                        document.getReviewSubmitterId()
                ),

                document.getReviewSubmitTime(),

                stringId(
                        document.getReviewerId()
                ),

                document.getReviewTime(),

                document.getReviewComment(),

                progress,

                canSubmit,

                canReview,

                canWithdraw,

                published,

                records
        );
    }

    private String stringId(Long id) {

        return id == null
                ? null
                : id.toString();
    }
}
