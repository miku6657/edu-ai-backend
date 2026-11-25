package com.edu.ai.document.service;

import com.edu.ai.common.BusinessException;
import com.edu.ai.course.service.CoursePermissionService;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
public class DocumentClassificationService {

    private final JdbcTemplate jdbc;
    private final CoursePermissionService permissions;

    public DocumentClassificationService(
            JdbcTemplate jdbc,
            CoursePermissionService permissions) {
        this.jdbc = jdbc;
        this.permissions = permissions;
    }

    public record Chapter(
            String id,
            String name,
            int sortOrder
    ) {}

    public record DocumentTags(
            String documentId,
            List<String> chapterIds,
            List<String> pointIds
    ) {}

    public record BatchResult(
            int updatedDocuments
    ) {}

    public record ClassificationTag(
            String id,
            String name
    ) {}

    public record DocumentClassification(
            String documentId,
            List<ClassificationTag> chapters,
            List<ClassificationTag> points
    ) {}

    /**
     * 创建课程章节。
     */
    @Transactional(rollbackFor = Exception.class)
    public void addChapter(
            Long courseId,
            Long userId,
            String name,
            Integer sortOrder) {

        permissions.requireManage(courseId, userId);
        permissions.requireActiveRead(courseId, userId);

        if (sortOrder != null && sortOrder < 0) {
            throw new BusinessException(
                    400, "章节排序不能小于0"
            );
        }

        if (name == null || name.isBlank()
                || name.trim().length() > 200) {
            throw new BusinessException(
                    400, "章节名称不合法"
            );
        }

        try {
            jdbc.update(
                    """
                    INSERT INTO course_chapter
                        (course_id, chapter_name, sort_order)
                    VALUES (?, ?, ?)
                    """,
                    courseId,
                    name.trim(),
                    sortOrder == null ? 0 : sortOrder
            );
        } catch (DuplicateKeyException e) {
            throw new BusinessException(
                    409, "该课程已存在同名章节"
            );
        }
    }

    /**
     * 修改课程章节名称及排序。
     */
    @Transactional(rollbackFor = Exception.class)
    public void updateChapter(
            Long courseId,
            Long chapterId,
            Long userId,
            String name,
            Integer sortOrder) {

        permissions.requireManage(courseId, userId);
        permissions.requireActiveRead(courseId, userId);

        if (name == null
                || name.isBlank()
                || name.trim().length() > 200) {

            throw new BusinessException(
                    400, "章节名称不能为空且不能超过200个字符"
            );
        }

        if (sortOrder == null || sortOrder < 0) {

            throw new BusinessException(
                    400, "章节排序必须为非负整数"
            );
        }

        // 限制章节必须属于当前课程。
        List<Long> rows = jdbc.queryForList(
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

        if (rows.isEmpty()) {
            throw new BusinessException(
                    404, "当前课程中不存在该章节"
            );
        }

        try {
            jdbc.update(
                    """
                    UPDATE course_chapter
                    SET chapter_name = ?,
                        sort_order = ?
                    WHERE id = ?
                      AND course_id = ?
                    """,
                    name.trim(),
                    sortOrder,
                    chapterId,
                    courseId
            );

        } catch (DuplicateKeyException e) {

            throw new BusinessException(
                    409, "该课程已存在同名章节"
            );
        }
    }

    /**
     * 删除指定课程章节。
     *
     * 仅删除章节及文档与章节的关联，
     * 不删除PDF、知识点或向量索引。
     */
    @Transactional(rollbackFor = Exception.class)
    public void deleteChapter(
            Long courseId,
            Long chapterId,
            Long userId) {

        permissions.requireManage(courseId, userId);
        permissions.requireActiveRead(courseId, userId);

        int deleted = jdbc.update(
                """
                DELETE FROM course_chapter
                WHERE id = ?
                  AND course_id = ?
                """,
                chapterId,
                courseId
        );

        if (deleted != 1) {

            throw new BusinessException(
                    404, "当前课程中不存在该章节"
            );
        }
    }

    /**
     * 课程成员可查看章节。
     */
    public List<Chapter> listChapters(
            Long courseId,
            Long userId) {

        permissions.requireActiveRead(courseId, userId);

        return jdbc.query(
                """
                SELECT id, chapter_name, sort_order
                FROM course_chapter
                WHERE course_id = ?
                ORDER BY sort_order, id
                """,
                (rs, n) -> new Chapter(
                        Long.toString(rs.getLong("id")),
                        rs.getString("chapter_name"),
                        rs.getInt("sort_order")
                ),
                courseId
        );
    }

    /**
     * 查询当前课程中单份文档的分类。
     */
    public DocumentTags getDocumentTags(
            Long courseId,
            Long documentId,
            Long userId) {

        permissions.requireActiveRead(courseId, userId);
        requireDocument(
                courseId, documentId, userId, false
        );

        List<String> chapterIds = jdbc.queryForList(
                """
                SELECT CAST(dc.chapter_id AS CHAR)
                FROM course_document_chapter dc
                WHERE dc.document_id = ?
                ORDER BY dc.chapter_id
                """,
                String.class,
                documentId
        );

        List<String> pointIds = jdbc.queryForList(
                """
                SELECT CAST(dp.point_id AS CHAR)
                FROM course_document_knowledge_point dp
                WHERE dp.document_id = ?
                ORDER BY dp.point_id
                """,
                String.class,
                documentId
        );

        return new DocumentTags(
                documentId.toString(),
                chapterIds,
                pointIds
        );
    }

    /**
     * 批量替换文档的章节和知识点关联。
     *
     * 参数语义：
     * documentIds：本次要修改的文档
     * chapterIds：更新后的完整章节集合
     * pointIds：更新后的完整知识点集合
     *
     * 允许传空标签列表以取消挂接，
     * 但不允许传空文档列表。
     */
    @Transactional(rollbackFor = Exception.class)
    public BatchResult batchAssign(
            Long courseId,
            Long userId,
            List<Long> documentIds,
            List<Long> chapterIds,
            List<Long> pointIds) {

        permissions.requireManage(courseId, userId);

        List<Long> documents = normalize(
                documentIds, "文档"
        );
        List<Long> chapters = normalize(
                chapterIds, "章节"
        );
        List<Long> points = normalize(
                pointIds, "知识点"
        );

        if (documents.isEmpty()) {
            throw new BusinessException(
                    400, "请至少选择一份文档"
            );
        }

        if (documents.size() > 100
                || chapters.size() > 100
                || points.size() > 100) {
            throw new BusinessException(
                    400, "单次批量挂接数量不能超过100"
            );
        }

        // 全部先验证，防止部分写入。
        // 统一按 ID 顺序处理，减少并发死锁风险。
        for (Long docId : documents) {
            requireDocument(
                    courseId, docId, userId, true
            );
        }

        for (Long chapterId : chapters) {
            requireCourseRecord(
                    "course_chapter",
                    chapterId,
                    courseId
            );
        }

        for (Long pointId : points) {
            requireCourseRecord(
                    "course_knowledge_point",
                    pointId,
                    courseId
            );
        }

        for (Long docId : documents) {

            // 替换原有章节关联
            jdbc.update(
                    """
                    DELETE FROM course_document_chapter
                    WHERE document_id = ?
                    """,
                    docId
            );

            // 替换原有知识点关联
            jdbc.update(
                    """
                    DELETE FROM
                        course_document_knowledge_point
                    WHERE document_id = ?
                    """,
                    docId
            );

            for (Long chapterId : chapters) {
                jdbc.update(
                        """
                        INSERT INTO course_document_chapter
                            (document_id, chapter_id)
                        VALUES (?, ?)
                        """,
                        docId, chapterId
                );
            }

            for (Long pointId : points) {
                jdbc.update(
                        """
                        INSERT INTO
                            course_document_knowledge_point
                            (document_id, point_id)
                        VALUES (?, ?)
                        """,
                        docId, pointId
                );
            }
        }

        return new BatchResult(documents.size());
    }

    /**
     * 批量查询当前课程可见文档的章节及知识点。
     *
     * 避免前端为每一份文档单独发送请求。
     */
    public List<DocumentClassification> listDocumentClassifications(
            Long courseId,
            Long userId) {

        permissions.requireActiveRead(courseId, userId);

        boolean canManage =
                permissions.canManage(courseId, userId);

        List<String> documentIds = jdbc.queryForList(
                """
                SELECT CAST(id AS CHAR)
                FROM course_document
                WHERE course_id = ?
                  AND (
                      (
                          version_status = 'CURRENT'
                          AND status = 'READY'
                      )
                      OR (
                          version_status = 'PENDING'
                          AND ? = 1
                      )
                  )
                  AND status NOT IN (
                      'DELETING',
                      'DELETE_FAILED',
                      'DELETED'
                  )
                ORDER BY id
                """,
                String.class,
                courseId,
                canManage ? 1 : 0
        );

        Map<String, List<ClassificationTag>> chapterMap =
                new HashMap<>();

        Map<String, List<ClassificationTag>> pointMap =
                new HashMap<>();

        Set<String> visibleIds =
                new HashSet<>(documentIds);

        // 批量查询文档章节
        jdbc.query(
                """
                SELECT
                    dc.document_id,
                    c.id,
                    c.chapter_name
                FROM course_document_chapter dc
                JOIN course_chapter c
                  ON c.id = dc.chapter_id
                JOIN course_document d
                  ON d.id = dc.document_id
                WHERE d.course_id = ?
                  AND c.course_id = ?
                ORDER BY c.sort_order, c.id
                """,
                rs -> {
                    String documentId =
                            Long.toString(
                                    rs.getLong("document_id")
                            );

                    if (!visibleIds.contains(documentId)) {
                        return;
                    }

                    chapterMap
                            .computeIfAbsent(
                                    documentId,
                                    key -> new ArrayList<>()
                            )
                            .add(new ClassificationTag(
                                    Long.toString(
                                            rs.getLong("id")
                                    ),
                                    rs.getString("chapter_name")
                            ));
                },
                courseId,
                courseId
        );

        // 批量查询文档知识点
        jdbc.query(
                """
                SELECT
                    dp.document_id,
                    p.id,
                    p.title
                FROM course_document_knowledge_point dp
                JOIN course_knowledge_point p
                  ON p.id = dp.point_id
                JOIN course_document d
                  ON d.id = dp.document_id
                WHERE d.course_id = ?
                  AND p.course_id = ?
                ORDER BY p.sort_order, p.id
                """,
                rs -> {
                    String documentId =
                            Long.toString(
                                    rs.getLong("document_id")
                            );

                    if (!visibleIds.contains(documentId)) {
                        return;
                    }

                    pointMap
                            .computeIfAbsent(
                                    documentId,
                                    key -> new ArrayList<>()
                            )
                            .add(new ClassificationTag(
                                    Long.toString(
                                            rs.getLong("id")
                                    ),
                                    rs.getString("title")
                            ));
                },
                courseId,
                courseId
        );

        List<DocumentClassification> result =
                new ArrayList<>();

        for (String documentId : documentIds) {
            result.add(
                    new DocumentClassification(
                            documentId,
                            chapterMap.getOrDefault(
                                    documentId,
                                    List.of()
                            ),
                            pointMap.getOrDefault(
                                    documentId,
                                    List.of()
                            )
                    )
            );
        }

        return result;
    }

    private List<Long> normalize(
            List<Long> input,
            String fieldName) {

        if (input == null) {
            throw new BusinessException(
                    400, fieldName + "列表不能为空"
            );
        }

        TreeSet<Long> ids = new TreeSet<>();

        for (Long id : input) {
            if (id == null || id <= 0) {
                throw new BusinessException(
                        400, fieldName + "ID不合法"
                );
            }
            ids.add(id);
        }

        return new ArrayList<>(ids);
    }

    private void requireCourseRecord(
            String table,
            Long id,
            Long courseId) {

        // 表名只能来自本类写死的两个常量值，
        // 不接收外部任意字符串。
        if (!table.equals("course_chapter")
                && !table.equals("course_knowledge_point")) {
            throw new IllegalArgumentException(
                    "非法分类表"
            );
        }

        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM " + table
                        + " WHERE id = ? AND course_id = ?",
                Integer.class,
                id, courseId
        );

        if (count == null || count != 1) {
            throw new BusinessException(
                    400, "章节或知识点不属于当前课程"
            );
        }
    }

    private void requireDocument(
            Long courseId,
            Long documentId,
            Long userId,
            boolean forUpdate) {

        String sql = """
                SELECT course_id, uploader_id, status, version_status
                FROM course_document
                WHERE id = ?
                """
                + (forUpdate ? " FOR UPDATE" : "");

        List<Map<String, Object>> rows =
                jdbc.queryForList(sql, documentId);

        if (rows.isEmpty()) {
            throw new BusinessException(
                    404, "文档不存在"
            );
        }

        Map<String, Object> row = rows.get(0);

        Long actualCourse = ((Number)
                row.get("course_id")).longValue();

        if (!courseId.equals(actualCourse)) {
            throw new BusinessException(
                    403, "文档不属于当前课程"
            );
        }

        String status = (String) row.get("status");

        if (Set.of(
                "DELETING", "DELETE_FAILED", "DELETED"
        ).contains(status)) {
            throw new BusinessException(
                    409, "已删除或删除中的文档不能分类"
            );
        }

        if (forUpdate
                && "HISTORY".equals(
                        row.get("version_status"))) {

            throw new BusinessException(
                    409,
                    "历史版本的分类不能直接修改"
            );
        }

        if (!forUpdate && !"READY".equals(status)) {

            Long uploaderId = ((Number)
                    row.get("uploader_id")).longValue();

            // 未完成索引的文档仅课程管理者可查看
            if (!permissions.canManage(
                    courseId, userId)) {
                throw new BusinessException(
                        403, "文档尚未完成处理"
                );
            }
        }
    }
}
