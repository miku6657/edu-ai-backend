package com.edu.ai.document.service;

import com.edu.ai.common.BusinessException;

import com.edu.ai.document.dto.ChunkKnowledgeDetailVO;
import com.edu.ai.document.dto.ChunkKnowledgeProgressVO;
import com.edu.ai.document.dto.ChunkKnowledgeRecommendVO;
import com.edu.ai.document.dto.ChunkKnowledgeRelationVO;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

@Service
public class ChunkKnowledgeService {

    private static final int MAX_BATCH_SIZE = 200;

    private static final int MAX_MANUAL_POINTS = 20;

    private final JdbcTemplate jdbc;

    private final DocumentChunkService chunkService;

    private final ChunkKnowledgeMatcher matcher;

    public ChunkKnowledgeService(
            JdbcTemplate jdbc,
            DocumentChunkService chunkService,
            ChunkKnowledgeMatcher matcher) {

        this.jdbc = jdbc;
        this.chunkService = chunkService;
        this.matcher = matcher;
    }

    /**
     * 切片内部数据。
     */
    private record ChunkRow(

            Long id,

            Long chapterId,

            String sectionTitle,

            String content,

            String knowledgeStatus,

            Long checkedBy,

            String checkedTime

    ) {}

    /**
     * 知识点关联内部数据。
     */
    private record RelationRow(

            Long id,

            Long pointId,

            String status

    ) {}

    /**
     * 批量处理结果。
     */
    public record BatchResult(

            int processedCount

    ) {}

    /**
     * 统一业务错误。
     */
    private BusinessException badRequest(
            String message) {

        return new BusinessException(
                400,
                message
        );
    }

    private BusinessException conflict(
            String message) {

        return new BusinessException(
                409,
                message
        );
    }

    /**
     * 校验并去重ID列表。
     */
    private List<Long> normalizeIds(
            List<Long> ids,
            int maxSize,
            String label) {

        if (ids == null
                || ids.isEmpty()
                || ids.size() > maxSize) {

            throw badRequest(
                    label + "数量必须在1至"
                    + maxSize + "之间"
            );
        }

        if (ids.stream().anyMatch(
                id -> id == null || id <= 0
        )) {

            throw badRequest(
                    label + "ID不合法"
            );
        }

        return ids.stream()
                .distinct()
                .sorted()
                .toList();
    }

    /**
     * 修改切片关联之前统一锁定课程和文档。
     */
    private void lockDocument(
            Long courseId,
            Long documentId,
            Long userId) {

        // 复用原有课程管理权限检查。
        chunkService.requireManagedDocument(
                courseId,
                documentId,
                userId
        );

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

            throw conflict(
                    "课程当前不可用"
            );
        }

        List<String> versions =
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

        if (versions.isEmpty()) {

            throw conflict(
                    "文档状态已经变化"
            );
        }

        String versionStatus =
                versions.get(0);

        if (!"CURRENT".equals(versionStatus)
                && !"PENDING".equals(versionStatus)) {

            throw conflict(
                    "当前文档版本不允许修改知识点关联"
            );
        }
    }

    /**
     * 切片数据库映射。
     */
    private ChunkRow mapChunk(
            ResultSet rs,
            int rowNum) throws SQLException {

        return new ChunkRow(

                rs.getLong("id"),

                rs.getObject(
                        "chapter_id",
                        Long.class
                ),

                rs.getString("section_title"),

                rs.getString("content"),

                rs.getString("knowledge_status"),

                rs.getObject(
                        "knowledge_checked_by",
                        Long.class
                ),

                rs.getString(
                        "knowledge_checked_time"
                )
        );
    }

    /**
     * 查询单条切片。
     */
    private ChunkRow requireChunk(
            Long courseId,
            Long documentId,
            Long chunkId,
            boolean lock) {

        String sql = """
                SELECT
                    id,
                    chapter_id,
                    section_title,
                    content,
                    knowledge_status,
                    knowledge_checked_by,
                    knowledge_checked_time
                FROM course_document_chunk
                WHERE id = ?
                  AND course_id = ?
                  AND document_id = ?
                """;

        if (lock) {
            sql += " FOR UPDATE";
        }

        List<ChunkRow> rows = jdbc.query(
                sql,
                this::mapChunk,
                chunkId,
                courseId,
                documentId
        );

        if (rows.isEmpty()) {

            throw new BusinessException(
                    404,
                    "当前资料中不存在该知识切片"
            );
        }

        return rows.get(0);
    }

    /**
     * 查询课程全部知识点候选。
     *
     * 修改操作中调用，使用锁定读，
     * 避免与知识点章节调整并发冲突。
     */
    private List<
            ChunkKnowledgeMatcher.PointCandidate
            > loadCandidates(
            Long courseId) {

        return jdbc.query(
                """
                SELECT
                    id,
                    chapter_id,
                    title,
                    match_keywords
                FROM course_knowledge_point
                WHERE course_id = ?
                ORDER BY id
                FOR UPDATE
                """,
                (rs, rowNum) ->
                        new ChunkKnowledgeMatcher
                                .PointCandidate(

                                rs.getLong("id"),

                                rs.getObject(
                                        "chapter_id",
                                        Long.class
                                ),

                                rs.getString("title"),

                                rs.getString(
                                        "match_keywords"
                                )
                        ),
                courseId
        );
    }

    /**
     * 查询原始PDF已经挂接的知识点。
     */
    private Set<Long> loadDocumentPointIds(
            Long courseId,
            Long documentId) {

        List<Long> ids = jdbc.queryForList(
                """
                SELECT d.point_id
                FROM course_document_knowledge_point d
                JOIN course_knowledge_point p
                  ON p.id = d.point_id
                WHERE d.document_id = ?
                  AND p.course_id = ?
                """,
                Long.class,
                documentId,
                courseId
        );

        return new HashSet<>(ids);
    }

    /**
     * 锁定切片已有的关联记录。
     */
    private List<RelationRow> loadRelationsForUpdate(
            Long courseId,
            Long chunkId) {

        return jdbc.query(
                """
                SELECT
                    id,
                    point_id,
                    confirm_status
                FROM course_chunk_knowledge_point
                WHERE course_id = ?
                  AND chunk_id = ?
                ORDER BY id
                FOR UPDATE
                """,
                (rs, rowNum) ->
                        new RelationRow(
                                rs.getLong("id"),
                                rs.getLong("point_id"),
                                rs.getString(
                                        "confirm_status"
                                )
                        ),
                courseId,
                chunkId
        );
    }

    /**
     * 更新切片知识点处理状态。
     */
    private void setChunkStatus(
            Long courseId,
            Long documentId,
            Long chunkId,
            String status,
            Long checkedBy) {

        jdbc.update(
                """
                UPDATE course_document_chunk
                SET
                    knowledge_status = ?,
                    knowledge_checked_by = ?,
                    knowledge_checked_time =
                        CASE
                            WHEN ? IS NULL THEN NULL
                            ELSE CURRENT_TIMESTAMP
                        END,
                    update_time = CURRENT_TIMESTAMP
                WHERE id = ?
                  AND course_id = ?
                  AND document_id = ?
                """,
                status,
                checkedBy,
                checkedBy,
                chunkId,
                courseId,
                documentId
        );
    }

    /**
     * 根据真实关联结果重新计算切片状态。
     *
     * 有待确认关联：RECOMMENDED。
     * 没有待确认且存在已确认关联：CONFIRMED。
     * 全部拒绝：RECOMMENDED，仍需人工处理。
     */
    private void refreshChunkStatus(
            Long courseId,
            Long documentId,
            Long chunkId,
            Long userId) {

        List<RelationRow> relations =
                loadRelationsForUpdate(
                        courseId,
                        chunkId
                );

        boolean pending = relations.stream()
                .anyMatch(
                        row -> "PENDING".equals(
                                row.status()
                        )
                );

        boolean confirmed = relations.stream()
                .anyMatch(
                        row -> "CONFIRMED".equals(
                                row.status()
                        )
                );

        if (!pending && confirmed) {

            setChunkStatus(
                    courseId,
                    documentId,
                    chunkId,
                    "CONFIRMED",
                    userId
            );

        } else {

            setChunkStatus(
                    courseId,
                    documentId,
                    chunkId,
                    "RECOMMENDED",
                    null
            );
        }
    }

    /**
     * 自动推荐知识点。
     *
     * chunkIds为空：处理下一批未处理切片。
     * chunkIds不为空：处理指定切片。
     *
     * 自动推荐不会覆盖人工确认和拒绝结果。
     */
    @Transactional(rollbackFor = Exception.class)
    public ChunkKnowledgeRecommendVO recommend(
            Long courseId,
            Long documentId,
            Long userId,
            List<Long> chunkIds) {

        lockDocument(
                courseId,
                documentId,
                userId
        );

        List<Long> ids;

        if (chunkIds == null
                || chunkIds.isEmpty()) {

            ids = jdbc.queryForList(
                    """
                    SELECT id
                    FROM course_document_chunk
                    WHERE course_id = ?
                      AND document_id = ?
                      AND knowledge_status = 'UNPROCESSED'
                    ORDER BY
                        page_no,
                        chunk_index,
                        id
                    LIMIT ?
                    FOR UPDATE
                    """,
                    Long.class,
                    courseId,
                    documentId,
                    MAX_BATCH_SIZE
            );

        } else {

            ids = normalizeIds(
                    chunkIds,
                    MAX_BATCH_SIZE,
                    "知识切片"
            );
        }

        if (ids.isEmpty()) {

            return new ChunkKnowledgeRecommendVO(
                    0, 0, 0, 0, 0
            );
        }

        List<
                ChunkKnowledgeMatcher.PointCandidate
                > candidates =
                loadCandidates(courseId);

        if (candidates.isEmpty()) {

            throw conflict(
                    "当前课程尚未建立知识点体系"
            );
        }

        Set<Long> documentPointIds =
                loadDocumentPointIds(
                        courseId,
                        documentId
                );

        int processedChunks = 0;
        int recommendedChunks = 0;
        int unmatchedChunks = 0;
        int skippedChunks = 0;
        int generatedRelations = 0;

        for (Long chunkId : ids) {

            ChunkRow chunk = requireChunk(
                    courseId,
                    documentId,
                    chunkId,
                    true
            );

            // 已完成处理的切片不自动覆盖。
            if ("CONFIRMED".equals(
                    chunk.knowledgeStatus()
            )
                    || "NOT_APPLICABLE".equals(
                    chunk.knowledgeStatus()
            )) {

                skippedChunks++;
                continue;
            }

            List<RelationRow> relations =
                    loadRelationsForUpdate(
                            courseId,
                            chunkId
                    );

            // 即使切片仍有待确认候选，
            // 只要已有人工确认结果，也不自动覆盖。
            boolean hasConfirmed =
                    relations.stream().anyMatch(
                            relation ->
                                    "CONFIRMED".equals(
                                            relation.status()
                                    )
                    );

            if (hasConfirmed) {

                skippedChunks++;
                continue;
            }

            // 保留人工拒绝结果。
            Set<Long> rejectedPointIds =
                    new HashSet<>();

            for (RelationRow relation : relations) {

                if ("REJECTED".equals(
                        relation.status())) {

                    rejectedPointIds.add(
                            relation.pointId()
                    );
                }
            }

            List<
                    ChunkKnowledgeMatcher.PointCandidate
                    > availableCandidates =
                    candidates.stream()
                            .filter(
                                    candidate ->
                                            !rejectedPointIds
                                                    .contains(
                                                            candidate.id()
                                                    )
                            )
                            .toList();

            // 只清理旧的待确认自动推荐。
            jdbc.update(
                    """
                    DELETE FROM course_chunk_knowledge_point
                    WHERE course_id = ?
                      AND chunk_id = ?
                      AND relation_source = 'AUTO'
                      AND confirm_status = 'PENDING'
                    """,
                    courseId,
                    chunkId
            );

            ChunkKnowledgeMatcher.ChunkInput input =
                    new ChunkKnowledgeMatcher.ChunkInput(

                            chunk.id(),

                            chunk.chapterId(),

                            chunk.sectionTitle(),

                            chunk.content()
                    );

            List<ChunkKnowledgeMatcher.MatchResult>
                    matches =
                    matcher.recommend(
                            input,
                            availableCandidates,
                            documentPointIds
                    );

            if (matches.isEmpty()) {

                unmatchedChunks++;

            } else {

                recommendedChunks++;

                List<Object[]> args =
                        new ArrayList<>();

                for (
                        ChunkKnowledgeMatcher.MatchResult
                                match : matches
                ) {

                    args.add(
                            new Object[]{

                                    courseId,

                                    chunkId,

                                    match.pointId(),

                                    match.score(),

                                    match.reason()
                            }
                    );
                }

                jdbc.batchUpdate(
                        """
                        INSERT INTO course_chunk_knowledge_point (
                            course_id,
                            chunk_id,
                            point_id,
                            match_score,
                            relation_source,
                            confirm_status,
                            match_reason
                        )
                        VALUES (
                            ?, ?, ?, ?,
                            'AUTO',
                            'PENDING',
                            ?
                        )
                        """,
                        args
                );

                generatedRelations +=
                        matches.size();
            }

            setChunkStatus(
                    courseId,
                    documentId,
                    chunkId,
                    "RECOMMENDED",
                    null
            );

            processedChunks++;
        }

        return new ChunkKnowledgeRecommendVO(

                processedChunks,

                recommendedChunks,

                unmatchedChunks,

                skippedChunks,

                generatedRelations
        );
    }

    /**
     * 查询单个切片的知识点关联详情。
     */
    @Transactional(readOnly = true)
    public ChunkKnowledgeDetailVO getDetail(
            Long courseId,
            Long documentId,
            Long chunkId,
            Long userId) {

        chunkService.requireManagedDocument(
                courseId,
                documentId,
                userId
        );

        ChunkRow chunk = requireChunk(
                courseId,
                documentId,
                chunkId,
                false
        );

        List<ChunkKnowledgeRelationVO> relations =
                jdbc.query(
                        """
                        SELECT
                            r.id,
                            r.chunk_id,
                            r.point_id,
                            p.title AS point_title,
                            p.chapter_id,
                            ch.chapter_name,
                            p.difficulty,
                            r.match_score,
                            r.relation_source,
                            r.confirm_status,
                            r.match_reason,
                            r.confirmed_by,
                            r.confirmed_time
                        FROM course_chunk_knowledge_point r
                        JOIN course_knowledge_point p
                          ON p.id = r.point_id
                         AND p.course_id = r.course_id
                        LEFT JOIN course_chapter ch
                          ON ch.id = p.chapter_id
                         AND ch.course_id = p.course_id
                        WHERE r.course_id = ?
                          AND r.chunk_id = ?
                        ORDER BY
                            CASE r.confirm_status
                                WHEN 'CONFIRMED' THEN 1
                                WHEN 'PENDING' THEN 2
                                ELSE 3
                            END,
                            r.match_score DESC,
                            r.id ASC
                        """,
                        (rs, rowNum) -> {

                            Long chapterId =
                                    rs.getObject(
                                            "chapter_id",
                                            Long.class
                                    );

                            Long confirmedBy =
                                    rs.getObject(
                                            "confirmed_by",
                                            Long.class
                                    );

                            return new ChunkKnowledgeRelationVO(

                                    rs.getString("id"),

                                    rs.getString(
                                            "chunk_id"
                                    ),

                                    rs.getString(
                                            "point_id"
                                    ),

                                    rs.getString(
                                            "point_title"
                                    ),

                                    chapterId == null
                                            ? null
                                            : chapterId
                                                    .toString(),

                                    rs.getString(
                                            "chapter_name"
                                    ),

                                    rs.getInt(
                                            "difficulty"
                                    ),

                                    rs.getInt(
                                            "match_score"
                                    ),

                                    rs.getString(
                                            "relation_source"
                                    ),

                                    rs.getString(
                                            "confirm_status"
                                    ),

                                    rs.getString(
                                            "match_reason"
                                    ),

                                    confirmedBy == null
                                            ? null
                                            : confirmedBy
                                                    .toString(),

                                    rs.getString(
                                            "confirmed_time"
                                    )
                            );
                        },
                        courseId,
                        chunkId
                );

        return new ChunkKnowledgeDetailVO(

                chunkId.toString(),

                chunk.knowledgeStatus(),

                chunk.checkedBy() == null
                        ? null
                        : chunk.checkedBy().toString(),

                chunk.checkedTime(),

                relations
        );
    }

    /**
     * 确认或拒绝知识点推荐。
     *
     * 支持跨多个切片批量处理，
     * 但所有关联必须属于当前文档。
     */
    @Transactional(rollbackFor = Exception.class)
    public BatchResult decideRelations(
            Long courseId,
            Long documentId,
            Long userId,
            List<Long> relationIds,
            String decision) {

        lockDocument(
                courseId,
                documentId,
                userId
        );

        List<Long> ids = normalizeIds(
                relationIds,
                MAX_BATCH_SIZE,
                "知识点关联"
        );

        if (decision == null) {

            throw badRequest(
                    "处理决定不能为空"
            );
        }

        String targetStatus = switch (decision) {

            case "CONFIRM" -> "CONFIRMED";

            case "REJECT" -> "REJECTED";

            default -> throw badRequest(
                    "不支持的知识点处理决定"
            );
        };

        Set<Long> affectedChunks =
                new TreeSet<>();

        for (Long relationId : ids) {

            List<Long> chunkIds =
                    jdbc.queryForList(
                            """
                            SELECT r.chunk_id
                            FROM course_chunk_knowledge_point r
                            JOIN course_document_chunk c
                              ON c.id = r.chunk_id
                             AND c.course_id = r.course_id
                            WHERE r.id = ?
                              AND r.course_id = ?
                              AND c.document_id = ?
                            FOR UPDATE
                            """,
                            Long.class,
                            relationId,
                            courseId,
                            documentId
                    );

            if (chunkIds.isEmpty()) {

                throw badRequest(
                        "存在不属于当前资料的知识点关联"
                );
            }

            Long chunkId = chunkIds.get(0);

            ChunkRow chunk = requireChunk(
                    courseId,
                    documentId,
                    chunkId,
                    true
            );

            List<RelationRow> relations =
                    loadRelationsForUpdate(
                            courseId,
                            chunkId
                    );

            RelationRow relation =
                    relations.stream()
                            .filter(
                                    row -> Objects.equals(
                                            row.id(),
                                            relationId
                                    )
                            )
                            .findFirst()
                            .orElseThrow(
                                    () -> conflict(
                                            "知识点关联已经变化"
                                    )
                            );

            // 相同决定重复提交视为成功。
            if (targetStatus.equals(
                    relation.status()
            )) {

                continue;
            }

            if ("NOT_APPLICABLE".equals(
                    chunk.knowledgeStatus()
            )) {

                throw conflict(
                        "该切片已标记为无需关联"
                );
            }

            if (!"PENDING".equals(
                    relation.status()
            )) {

                throw conflict(
                        "该推荐已处理，请通过手动调整修改"
                );
            }

            int updated = jdbc.update(
                    """
                    UPDATE course_chunk_knowledge_point
                    SET
                        confirm_status = ?,
                        confirmed_by = ?,
                        confirmed_time = CURRENT_TIMESTAMP,
                        update_time = CURRENT_TIMESTAMP
                    WHERE id = ?
                      AND course_id = ?
                      AND chunk_id = ?
                      AND confirm_status = 'PENDING'
                    """,
                    targetStatus,
                    userId,
                    relationId,
                    courseId,
                    chunkId
            );

            if (updated != 1) {

                throw conflict(
                        "知识点推荐状态已经变化"
                );
            }

            affectedChunks.add(chunkId);
        }

        // 所有候选处理结束后，
        // 统一重新计算受影响切片的状态。
        for (Long chunkId : affectedChunks) {

            refreshChunkStatus(
                    courseId,
                    documentId,
                    chunkId,
                    userId
            );
        }

        return new BatchResult(
                ids.size()
        );
    }

    /**
     * 人工设置切片的最终知识点集合。
     *
     * 采用完整覆盖方式：
     * 清除该切片全部旧推荐及旧关联，
     * 使用人工选择的知识点建立正式关联。
     */
    @Transactional(rollbackFor = Exception.class)
    public BatchResult replaceKnowledgePoints(
            Long courseId,
            Long documentId,
            Long chunkId,
            Long userId,
            List<Long> pointIds) {

        lockDocument(
                courseId,
                documentId,
                userId
        );

        ChunkRow chunk = requireChunk(
                courseId,
                documentId,
                chunkId,
                true
        );

        List<Long> ids = normalizeIds(
                pointIds,
                MAX_MANUAL_POINTS,
                "知识点"
        );

        List<
                ChunkKnowledgeMatcher.PointCandidate
                > candidates =
                loadCandidates(courseId);

        Map<
                Long,
                ChunkKnowledgeMatcher.PointCandidate
                > candidateMap =
                new HashMap<>();

        for (
                ChunkKnowledgeMatcher.PointCandidate
                        candidate : candidates
        ) {

            candidateMap.put(
                    candidate.id(),
                    candidate
            );
        }

        for (Long pointId : ids) {

            ChunkKnowledgeMatcher.PointCandidate point =
                    candidateMap.get(pointId);

            if (point == null) {

                throw badRequest(
                        "存在不属于当前课程的知识点"
                );
            }

            if (chunk.chapterId() != null
                    && point.chapterId() != null
                    && !Objects.equals(
                            chunk.chapterId(),
                            point.chapterId()
                    )) {

                throw badRequest(
                        "知识点与切片所属章节不一致"
                );
            }
        }

        // 全部参数校验完成后再执行覆盖。
        jdbc.update(
                """
                DELETE FROM course_chunk_knowledge_point
                WHERE course_id = ?
                  AND chunk_id = ?
                """,
                courseId,
                chunkId
        );

        List<Object[]> args =
                new ArrayList<>();

        for (Long pointId : ids) {

            args.add(
                    new Object[]{

                            courseId,

                            chunkId,

                            pointId,

                            userId
                    }
            );
        }

        jdbc.batchUpdate(
                """
                INSERT INTO course_chunk_knowledge_point (
                    course_id,
                    chunk_id,
                    point_id,
                    match_score,
                    relation_source,
                    confirm_status,
                    match_reason,
                    confirmed_by,
                    confirmed_time
                )
                VALUES (
                    ?, ?, ?,
                    0,
                    'MANUAL',
                    'CONFIRMED',
                    '教师手动设置知识点',
                    ?,
                    CURRENT_TIMESTAMP
                )
                """,
                args
        );

        setChunkStatus(
                courseId,
                documentId,
                chunkId,
                "CONFIRMED",
                userId
        );

        return new BatchResult(
                ids.size()
        );
    }

    /**
     * 批量标记切片无需关联知识点。
     */
    @Transactional(rollbackFor = Exception.class)
    public BatchResult markNotApplicable(
            Long courseId,
            Long documentId,
            Long userId,
            List<Long> chunkIds) {

        lockDocument(
                courseId,
                documentId,
                userId
        );

        List<Long> ids = normalizeIds(
                chunkIds,
                MAX_BATCH_SIZE,
                "知识切片"
        );

        for (Long chunkId : ids) {

            ChunkRow chunk = requireChunk(
                    courseId,
                    documentId,
                    chunkId,
                    true
            );

            // 重复标记不改变原操作人及处理时间。
            if ("NOT_APPLICABLE".equals(
                    chunk.knowledgeStatus()
            )) {

                continue;
            }

            // 教师主动标记无需关联，
            // 清除原有候选及正式关联。
            jdbc.update(
                    """
                    DELETE FROM course_chunk_knowledge_point
                    WHERE course_id = ?
                      AND chunk_id = ?
                    """,
                    courseId,
                    chunkId
            );

            setChunkStatus(
                    courseId,
                    documentId,
                    chunkId,
                    "NOT_APPLICABLE",
                    userId
            );
        }

        return new BatchResult(
                ids.size()
        );
    }

    /**
     * 批量重置知识点处理状态。
     *
     * 用于明确放弃原有处理结果，
     * 重新执行推荐和人工确认。
     */
    @Transactional(rollbackFor = Exception.class)
    public BatchResult resetChunks(
            Long courseId,
            Long documentId,
            Long userId,
            List<Long> chunkIds) {

        lockDocument(
                courseId,
                documentId,
                userId
        );

        List<Long> ids = normalizeIds(
                chunkIds,
                MAX_BATCH_SIZE,
                "知识切片"
        );

        for (Long chunkId : ids) {

            requireChunk(
                    courseId,
                    documentId,
                    chunkId,
                    true
            );

            jdbc.update(
                    """
                    DELETE FROM course_chunk_knowledge_point
                    WHERE course_id = ?
                      AND chunk_id = ?
                    """,
                    courseId,
                    chunkId
            );

            setChunkStatus(
                    courseId,
                    documentId,
                    chunkId,
                    "UNPROCESSED",
                    null
            );
        }

        return new BatchResult(
                ids.size()
        );
    }

    /**
     * 统计数量辅助方法。
     */
    private long count(
            String sql,
            Object... args) {

        Long value = jdbc.queryForObject(
                sql,
                Long.class,
                args
        );

        return value == null
                ? 0L
                : value;
    }

    /**
     * 查询整份文档的知识点处理进度。
     */
    @Transactional(readOnly = true)
    public ChunkKnowledgeProgressVO getProgress(
            Long courseId,
            Long documentId,
            Long userId) {

        chunkService.requireManagedDocument(
                courseId,
                documentId,
                userId
        );

        Map<String, Long> counts =
                new HashMap<>();

        jdbc.query(
                """
                SELECT
                    knowledge_status,
                    COUNT(*) AS total
                FROM course_document_chunk
                WHERE course_id = ?
                  AND document_id = ?
                GROUP BY knowledge_status
                """,
                rs -> {

                    counts.put(
                            rs.getString(
                                    "knowledge_status"
                            ),
                            rs.getLong("total")
                    );
                },
                courseId,
                documentId
        );

        long unprocessed = counts.getOrDefault(
                "UNPROCESSED",
                0L
        );

        long recommended = counts.getOrDefault(
                "RECOMMENDED",
                0L
        );

        long confirmed = counts.getOrDefault(
                "CONFIRMED",
                0L
        );

        long notApplicable = counts.getOrDefault(
                "NOT_APPLICABLE",
                0L
        );

        long total = counts.values()
                .stream()
                .mapToLong(Long::longValue)
                .sum();

        long pendingRelations = count(
                """
                SELECT COUNT(*)
                FROM course_chunk_knowledge_point r
                JOIN course_document_chunk c
                  ON c.id = r.chunk_id
                 AND c.course_id = r.course_id
                WHERE c.course_id = ?
                  AND c.document_id = ?
                  AND r.confirm_status = 'PENDING'
                """,
                courseId,
                documentId
        );

        long confirmedRelations = count(
                """
                SELECT COUNT(*)
                FROM course_chunk_knowledge_point r
                JOIN course_document_chunk c
                  ON c.id = r.chunk_id
                 AND c.course_id = r.course_id
                WHERE c.course_id = ?
                  AND c.document_id = ?
                  AND r.confirm_status = 'CONFIRMED'
                """,
                courseId,
                documentId
        );

        long completedCount =
                confirmed + notApplicable;

        // 检查切片状态与真实关联结果是否一致。
        long inconsistentChunks = count(
                """
                SELECT COUNT(*)
                FROM course_document_chunk c
                WHERE c.course_id = ?
                  AND c.document_id = ?
                  AND (
                      (
                          c.knowledge_status = 'CONFIRMED'
                          AND NOT EXISTS (
                              SELECT 1
                              FROM course_chunk_knowledge_point r
                              WHERE r.chunk_id = c.id
                                AND r.course_id = c.course_id
                                AND r.confirm_status = 'CONFIRMED'
                          )
                      )
                      OR
                      (
                          c.knowledge_status = 'NOT_APPLICABLE'
                          AND EXISTS (
                              SELECT 1
                              FROM course_chunk_knowledge_point r
                              WHERE r.chunk_id = c.id
                                AND r.course_id = c.course_id
                          )
                      )
                  )
                """,
                courseId,
                documentId
        );

        int percent = total == 0
                ? 0
                : (int) (
                        completedCount * 100 / total
                );

        boolean completed =
                total > 0
                && completedCount == total
                && pendingRelations == 0
                && inconsistentChunks == 0;

        return new ChunkKnowledgeProgressVO(

                documentId.toString(),

                total,

                unprocessed,

                recommended,

                confirmed,

                notApplicable,

                pendingRelations,

                confirmedRelations,

                percent,

                completed
        );
    }
}
