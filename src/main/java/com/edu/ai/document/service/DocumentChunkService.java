package com.edu.ai.document.service;

import com.edu.ai.common.BusinessException;
import com.edu.ai.course.service.CoursePermissionService;
import com.edu.ai.document.dto.DocumentChunkVO;
import com.edu.ai.document.dto.DocumentChunkPageVO;
import com.edu.ai.document.entity.CourseDocument;
import com.edu.ai.document.mapper.CourseDocumentMapper;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import tools.jackson.databind.JsonNode;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.ResultSet;
import java.sql.SQLException;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.Set;

@Service
public class DocumentChunkService {

    private final JdbcTemplate jdbc;

    private final CoursePermissionService permissions;

    private final CourseDocumentMapper documentMapper;

    public DocumentChunkService(
            JdbcTemplate jdbc,
            CoursePermissionService permissions,
            CourseDocumentMapper documentMapper) {

        this.jdbc = jdbc;
        this.permissions = permissions;
        this.documentMapper = documentMapper;
    }

    /**
     * 单条PDF知识切片。
     */
    public record ChunkData(

            int page,

            int chunkIndex,

            String sectionTitle,

            String content

    ) {}

    /**
     * 解析Python索引接口返回的切片数据。
     */
    public List<ChunkData> parseIndexResult(
            Long documentId,
            int chunkingVersion,
            JsonNode result) {

        if (result == null || !result.isObject()) {

            throw new IllegalStateException(
                    "Python索引响应格式不合法"
            );
        }

        JsonNode idNode = result.get("document_id");

        if (idNode == null
                || (!idNode.isIntegralNumber()
                    && !idNode.isString())) {

            throw new IllegalStateException(
                    "索引响应缺少有效文档ID"
            );
        }

        String responseDocumentId = idNode.isString()
                ? idNode.asString()
                : idNode.toString();

        if (!documentId.toString().equals(
                responseDocumentId)) {

            throw new IllegalStateException(
                    "索引响应文档ID不一致"
            );
        }

        int responseVersion = result
                .path("chunking_version")
                .asInt();

        if (responseVersion != chunkingVersion) {

            throw new IllegalStateException(
                    "索引响应切片算法版本不一致"
            );
        }

        JsonNode chunks = result.get("chunks");

        if (chunks == null || !chunks.isArray()) {

            throw new IllegalStateException(
                    "Python未返回有效切片数据"
            );
        }

        int indexedCount = result
                .path("indexed_chunks")
                .asInt();

        if (indexedCount <= 0
                || indexedCount != chunks.size()) {

            throw new IllegalStateException(
                    "切片数量与向量索引数量不一致"
            );
        }

        List<ChunkData> data =
                new ArrayList<>(chunks.size());

        Set<String> keys = new HashSet<>();

        for (JsonNode item : chunks) {

            if (!item.isObject()) {

                throw new IllegalStateException(
                        "切片数据格式不合法"
                );
            }

            JsonNode pageNode =
                    item.get("page");

            JsonNode indexNode =
                    item.get("chunk_index");

            JsonNode textNode =
                    item.get("text");

            if (pageNode == null
                    || !pageNode.isIntegralNumber()
                    || indexNode == null
                    || !indexNode.isIntegralNumber()
                    || textNode == null
                    || !textNode.isString()) {

                throw new IllegalStateException(
                        "切片缺少必要字段"
                );
            }

            int page = pageNode.asInt();

            int chunkIndex = indexNode.asInt();

            String content = textNode.asString();

            if (page <= 0
                    || chunkIndex < 0
                    || content == null
                    || content.isBlank()) {

                throw new IllegalStateException(
                        "存在无效知识切片"
                );
            }

            // Python按Unicode字符计数，
            // Java需要使用codePointCount保持一致。
            int charCount = content.codePointCount(
                    0,
                    content.length()
            );

            if (charCount > 800) {

                throw new IllegalStateException(
                        "切片长度超过800字符"
                );
            }

            String key =
                    documentId + ":"
                    + page + ":"
                    + chunkIndex;

            if (!keys.add(key)) {

                throw new IllegalStateException(
                        "Python返回重复切片ID"
                );
            }

            String sectionTitle = null;

            JsonNode sectionNode =
                    item.get("section_title");

            if (sectionNode != null
                    && sectionNode.isString()) {

                sectionTitle = sectionNode.asString();

                if (sectionTitle != null) {

                    sectionTitle = sectionTitle.trim();

                    if (sectionTitle.isBlank()) {

                        sectionTitle = null;

                    } else if (
                            sectionTitle.codePointCount(
                                    0,
                                    sectionTitle.length()
                            ) > 500
                    ) {

                        int end =
                                sectionTitle.offsetByCodePoints(
                                        0,
                                        500
                                );

                        sectionTitle =
                                sectionTitle.substring(0, end);
                    }
                }
            }

            data.add(
                    new ChunkData(
                            page,
                            chunkIndex,
                            sectionTitle,
                            content
                    )
            );
        }

        return List.copyOf(data);
    }

    /**
     * 保存索引生成的全部切片。
     *
     * 必须与文档索引完成、版本生效
     * 处于同一个数据库事务中。
     */
    @Transactional(
            propagation = Propagation.MANDATORY,
            rollbackFor = Exception.class
    )
    public void saveIndexedChunks(
            Long courseId,
            Long documentId,
            List<ChunkData> chunks) {

        if (chunks == null || chunks.isEmpty()) {

            throw new BusinessException(
                    409,
                    "文档没有有效知识切片"
            );
        }

        // 首次建立切片记录时才允许写入。
        // 防止后续重复索引覆盖已经审核、
        // 已关联知识点的业务切片。
        Long existingCount = jdbc.queryForObject(
                """
                SELECT COUNT(*)
                FROM course_document_chunk
                WHERE document_id = ?
                """,
                Long.class,
                documentId
        );

        if (existingCount != null
                && existingCount > 0) {

            throw new BusinessException(
                    409,
                    "文档已存在知识切片，不允许重复初始化"
            );
        }

        List<Object[]> args =
                new ArrayList<>(chunks.size());

        for (ChunkData chunk : chunks) {

            String chunkKey =
                    documentId + ":"
                    + chunk.page() + ":"
                    + chunk.chunkIndex();

            int charCount =
                    chunk.content().codePointCount(
                            0,
                            chunk.content().length()
                    );

            args.add(
                    new Object[]{

                            courseId,

                            documentId,

                            chunkKey,

                            chunk.page(),

                            chunk.chunkIndex(),

                            chunk.sectionTitle(),

                            chunk.content(),

                            contentHash(
                                    chunk.content()
                            ),

                            charCount
                    }
            );
        }

        jdbc.batchUpdate(
                """
                INSERT INTO course_document_chunk (
                    course_id,
                    document_id,
                    chunk_key,
                    page_no,
                    chunk_index,
                    section_title,
                    content,
                    content_hash,
                    char_count
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                args
        );
    }

    /**
     * 生成正文SHA-256指纹。
     * 后续质量抽检可用来检测完全重复的内容。
     */
    private String contentHash(
            String content) {

        try {

            byte[] digest =
                    MessageDigest
                            .getInstance("SHA-256")
                            .digest(
                                    content.getBytes(
                                            StandardCharsets.UTF_8
                                    )
                            );

            return HexFormat.of()
                    .formatHex(digest);

        } catch (NoSuchAlgorithmException e) {

            throw new IllegalStateException(
                    "无法计算切片内容指纹",
                    e
            );
        }
    }

    /**
     * 获取具备切片管理权限的课程文档。
     */
    public CourseDocument requireManagedDocument(
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

        if (!"READY".equals(document.getStatus())) {

            throw new BusinessException(
                    409,
                    "当前文档尚未完成解析或已经不可用"
            );
        }

        return document;
    }

    /**
     * 查询文档已经持久化的切片数量。
     */
    public long countChunks(
            Long courseId,
            Long documentId) {

        Long count = jdbc.queryForObject(
                """
                SELECT COUNT(*)
                FROM course_document_chunk
                WHERE course_id = ?
                  AND document_id = ?
                """,
                Long.class,
                courseId,
                documentId
        );

        return count == null ? 0L : count;
    }

    /**
     * 将数据库记录映射为前端切片对象。
     */
    private DocumentChunkVO mapChunk(
            ResultSet rs,
            int rowNum) throws SQLException {

        Long chapterId = rs.getObject(
                "chapter_id",
                Long.class
        );

        return new DocumentChunkVO(

                Long.toString(
                        rs.getLong("id")
                ),

                Long.toString(
                        rs.getLong("course_id")
                ),

                Long.toString(
                        rs.getLong("document_id")
                ),

                rs.getString("chunk_key"),

                rs.getInt("page_no"),

                rs.getInt("chunk_index"),

                chapterId == null
                        ? null
                        : chapterId.toString(),

                rs.getString("chapter_name"),

                rs.getString("section_title"),

                rs.getString("content"),

                rs.getString("content_hash"),

                rs.getInt("char_count"),

                rs.getString("knowledge_status"),

                rs.getInt("confirmed_point_count"),

                rs.getInt("pending_point_count")
        );
    }

    /**
     * 分页查询课程资料的知识切片。
     */
    @Transactional(readOnly = true)
    public DocumentChunkPageVO listChunks(
            Long courseId,
            Long documentId,
            Long userId,
            String keyword,
            Integer pageNo,
            Long chapterId,
            Boolean unclassified,
            String knowledgeStatus,
            Integer page,
            Integer pageSize) {

        CourseDocument document =
                requireManagedDocument(
                        courseId,
                        documentId,
                        userId
                );

        int currentPage = page == null
                ? 1
                : page;

        int size = pageSize == null
                ? 20
                : pageSize;

        if (currentPage < 1
                || size < 1
                || size > 100) {

            throw new BusinessException(
                    400,
                    "分页参数不合法"
            );
        }

        if (pageNo != null && pageNo <= 0) {

            throw new BusinessException(
                    400,
                    "PDF页码必须大于0"
            );
        }

        if (chapterId != null
                && chapterId <= 0) {

            throw new BusinessException(
                    400,
                    "章节ID不合法"
            );
        }

        if (chapterId != null
                && Boolean.TRUE.equals(unclassified)) {

            throw new BusinessException(
                    400,
                    "章节筛选与未分类筛选不能同时使用"
            );
        }

        long offset =
                ((long) currentPage - 1) * size;

        StringBuilder where =
                new StringBuilder(
                        """
                        WHERE c.course_id = ?
                          AND c.document_id = ?
                        """
                );

        List<Object> params = new ArrayList<>();

        params.add(courseId);
        params.add(documentId);

        // 正文及原文目录标题检索。
        if (keyword != null
                && !keyword.isBlank()) {

            String normalized = keyword.trim();

            if (normalized.length() > 200) {

                throw new BusinessException(
                        400,
                        "检索关键词不能超过200字符"
                );
            }

            where.append(
                    """
                     AND (
                         LOCATE(?, c.content) > 0
                         OR LOCATE(
                             ?,
                             COALESCE(c.section_title, '')
                         ) > 0
                     )
                    """
            );

            params.add(normalized);
            params.add(normalized);
        }

        // PDF原始页码筛选。
        if (pageNo != null) {

            where.append(
                    " AND c.page_no = ?"
            );

            params.add(pageNo);
        }

        // 系统课程章节筛选。
        if (chapterId != null) {

            where.append(
                    " AND c.chapter_id = ?"
            );

            params.add(chapterId);
        }

        // 未分类切片筛选。
        if (Boolean.TRUE.equals(unclassified)) {

            where.append(
                    " AND c.chapter_id IS NULL"
            );
        }

        // F2-03：知识点处理状态筛选。
        if (knowledgeStatus != null
                && !knowledgeStatus.isBlank()) {

            String normalizedStatus =
                    knowledgeStatus.trim();

            if (!Set.of(
                    "UNPROCESSED",
                    "RECOMMENDED",
                    "CONFIRMED",
                    "NOT_APPLICABLE"
            ).contains(normalizedStatus)) {

                throw new BusinessException(
                        400,
                        "知识点处理状态不合法"
                );
            }

            where.append(
                    " AND c.knowledge_status = ?"
            );

            params.add(normalizedStatus);
        }

        Long filteredCount = jdbc.queryForObject(
                """
                SELECT COUNT(*)
                FROM course_document_chunk c
                """
                + where,
                Long.class,
                params.toArray()
        );

        List<Object> queryParams =
                new ArrayList<>(params);

        queryParams.add(size);
        queryParams.add(offset);

        List<DocumentChunkVO> records =
                jdbc.query(
                        """
                        SELECT
                            c.id,
                            c.course_id,
                            c.document_id,
                            c.chunk_key,
                            c.page_no,
                            c.chunk_index,
                            c.chapter_id,
                            ch.chapter_name,
                            c.section_title,
                            c.content,
                            c.content_hash,
                            c.char_count,
                            c.knowledge_status,

                            (
                                SELECT COUNT(*)
                                FROM course_chunk_knowledge_point r
                                WHERE r.chunk_id = c.id
                                  AND r.course_id = c.course_id
                                  AND r.confirm_status = 'CONFIRMED'
                            ) AS confirmed_point_count,

                            (
                                SELECT COUNT(*)
                                FROM course_chunk_knowledge_point r
                                WHERE r.chunk_id = c.id
                                  AND r.course_id = c.course_id
                                  AND r.confirm_status = 'PENDING'
                            ) AS pending_point_count
                        FROM course_document_chunk c
                        LEFT JOIN course_chapter ch
                          ON ch.id = c.chapter_id
                         AND ch.course_id = c.course_id
                        """
                        + where
                        + """
                         ORDER BY
                             c.page_no ASC,
                             c.chunk_index ASC,
                             c.id ASC
                         LIMIT ? OFFSET ?
                        """,
                        this::mapChunk,
                        queryParams.toArray()
                );

        long totalChunks = countChunks(
                courseId,
                documentId
        );

        Long classifiedChunks =
                jdbc.queryForObject(
                        """
                        SELECT COUNT(*)
                        FROM course_document_chunk
                        WHERE course_id = ?
                          AND document_id = ?
                          AND chapter_id IS NOT NULL
                        """,
                        Long.class,
                        courseId,
                        documentId
                );

        return new DocumentChunkPageVO(

                documentId.toString(),

                document.getOriginalName(),

                document.getVersionStatus(),

                document.getChunkingVersion() == null
                        ? 1
                        : document.getChunkingVersion(),

                totalChunks > 0,

                totalChunks,

                classifiedChunks == null
                        ? 0L
                        : classifiedChunks,

                filteredCount == null
                        ? 0L
                        : filteredCount,

                currentPage,

                size,

                records
        );
    }

    /**
     * 查询指定切片的完整内容。
     */
    @Transactional(readOnly = true)
    public DocumentChunkVO getChunk(
            Long courseId,
            Long documentId,
            Long chunkId,
            Long userId) {

        requireManagedDocument(
                courseId,
                documentId,
                userId
        );

        List<DocumentChunkVO> rows =
                jdbc.query(
                        """
                        SELECT
                            c.id,
                            c.course_id,
                            c.document_id,
                            c.chunk_key,
                            c.page_no,
                            c.chunk_index,
                            c.chapter_id,
                            ch.chapter_name,
                            c.section_title,
                            c.content,
                            c.content_hash,
                            c.char_count,
                            c.knowledge_status,

                            (
                                SELECT COUNT(*)
                                FROM course_chunk_knowledge_point r
                                WHERE r.chunk_id = c.id
                                  AND r.course_id = c.course_id
                                  AND r.confirm_status = 'CONFIRMED'
                            ) AS confirmed_point_count,

                            (
                                SELECT COUNT(*)
                                FROM course_chunk_knowledge_point r
                                WHERE r.chunk_id = c.id
                                  AND r.course_id = c.course_id
                                  AND r.confirm_status = 'PENDING'
                            ) AS pending_point_count
                        FROM course_document_chunk c
                        LEFT JOIN course_chapter ch
                          ON ch.id = c.chapter_id
                         AND ch.course_id = c.course_id
                        WHERE c.id = ?
                          AND c.course_id = ?
                          AND c.document_id = ?
                        """,
                        this::mapChunk,
                        chunkId,
                        courseId,
                        documentId
                );

        if (rows.isEmpty()) {

            throw new BusinessException(
                    404,
                    "当前文档中不存在该知识切片"
            );
        }

        return rows.get(0);
    }

    /**
     * 批量调整知识切片所属章节。
     *
     * chapterId为null时解除章节关联。
     */
    @Transactional(rollbackFor = Exception.class)
    public int assignChapter(
            Long courseId,
            Long documentId,
            Long userId,
            List<Long> chunkIds,
            Long chapterId) {

        requireManagedDocument(
                courseId,
                documentId,
                userId
        );

        if (chunkIds == null
                || chunkIds.isEmpty()
                || chunkIds.size() > 200) {

            throw new BusinessException(
                    400,
                    "每次请选择1至200个知识切片"
            );
        }

        if (chunkIds.stream().anyMatch(
                id -> id == null || id <= 0
        )) {

            throw new BusinessException(
                    400,
                    "切片ID不合法"
            );
        }

        List<Long> ids = chunkIds.stream()
                .distinct()
                .sorted()
                .toList();

        // 统一加锁顺序：课程 -> 章节 -> 文档 -> 切片。
        List<Integer> statuses =
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

        if (statuses.isEmpty()
                || !Integer.valueOf(1)
                           .equals(statuses.get(0))) {

            throw new BusinessException(
                    409,
                    "课程当前不可用"
            );
        }

        if (chapterId != null) {

            if (chapterId <= 0) {

                throw new BusinessException(
                        400,
                        "章节ID不合法"
                );
            }

            List<Long> chapters =
                    jdbc.queryForList(
                            """
                            SELECT id
                            FROM course_chapter
                            WHERE id = ?
                              AND course_id = ?
                            FOR UPDATE
                            """,
                            Long.class,
                            chapterId,
                            courseId
                    );

            if (chapters.isEmpty()) {

                throw new BusinessException(
                        400,
                        "目标章节不属于当前课程"
                );
            }
        }

        // 锁定文档，防止调整过程中被删除。
        List<String> versionStatuses =
                jdbc.queryForList(
                        """
                        SELECT version_status
                        FROM course_document
                        WHERE id = ?
                          AND course_id = ?
                          AND status = 'READY'
                        FOR UPDATE
                        """,
                        String.class,
                        documentId,
                        courseId
                );

        if (versionStatuses.isEmpty()) {

            throw new BusinessException(
                    409,
                    "文档状态已经变化"
            );
        }

        if ("HISTORY".equals(
                versionStatuses.get(0))) {

            throw new BusinessException(
                    409,
                    "历史版本的知识切片不允许修改"
            );
        }

        String placeholders = String.join(
                ",",
                java.util.Collections.nCopies(
                        ids.size(),
                        "?"
                )
        );

        List<Object> params =
                new ArrayList<>();

        params.add(courseId);
        params.add(documentId);
        params.addAll(ids);

        // 校验全部切片属于当前文档。
        List<Long> lockedIds =
                jdbc.queryForList(
                        """
                        SELECT id
                        FROM course_document_chunk
                        WHERE course_id = ?
                          AND document_id = ?
                          AND id IN (
                        """
                        + placeholders
                        + """
                        )
                        ORDER BY id
                        FOR UPDATE
                        """,
                        Long.class,
                        params.toArray()
                );

        if (lockedIds.size() != ids.size()) {

            throw new BusinessException(
                    400,
                    "存在不属于当前文档的知识切片"
            );
        }

        /*
         * F2-03章节调整保护。
         *
         * 已经建立知识点推荐或人工处理结果的切片，
         * 必须先重置知识点关联，再修改所属章节。
         */

        List<Object> guardParams =
                new ArrayList<>();

        guardParams.addAll(params);

        guardParams.add(chapterId);

        List<Long> protectedChunks =
                jdbc.queryForList(
                        """
                        SELECT c.id
                        FROM course_document_chunk c
                        WHERE c.course_id = ?
                          AND c.document_id = ?
                          AND c.id IN (
                        """
                        + placeholders
                        + """
                        )
                          AND NOT (
                              c.chapter_id <=> ?
                          )
                          AND (
                              c.knowledge_status <> 'UNPROCESSED'
                              OR EXISTS (
                                  SELECT 1
                                  FROM course_chunk_knowledge_point r
                                  WHERE r.chunk_id = c.id
                                    AND r.course_id = c.course_id
                              )
                          )
                        ORDER BY c.id
                        FOR UPDATE
                        """,
                        Long.class,
                        guardParams.toArray()
                );

        if (!protectedChunks.isEmpty()) {

            throw new BusinessException(
                    409,
                    "部分切片已经完成知识点推荐或人工处理，"
                    + "请先重置知识点关联，再修改所属章节"
            );
        }

        List<Object> updateParams =
                new ArrayList<>();

        updateParams.add(chapterId);
        updateParams.addAll(params);

        jdbc.update(
                """
                UPDATE course_document_chunk
                SET chapter_id = ?,
                    update_time = CURRENT_TIMESTAMP
                WHERE course_id = ?
                  AND document_id = ?
                  AND id IN (
                """
                + placeholders
                + ")",
                updateParams.toArray()
        );

        return ids.size();
    }
}
