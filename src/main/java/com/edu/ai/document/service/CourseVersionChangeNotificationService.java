package com.edu.ai.document.service;

import com.edu.ai.document.entity.CourseDocument;
import com.edu.ai.notification.service.NotificationService;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class CourseVersionChangeNotificationService {

    private static final String BIZ_TYPE =
            "DOC_VERSION_UPDATED";

    private final JdbcTemplate jdbc;

    private final NotificationService notifications;

    public CourseVersionChangeNotificationService(
            JdbcTemplate jdbc,
            NotificationService notifications) {

        this.jdbc = jdbc;
        this.notifications = notifications;
    }

    /**
     * 课程资料新版本发布后，
     * 通知同课程其他资料的更新责任人。
     */
    public void notifyOtherDocumentResponsibles(
            CourseDocument publishedDocument) {

        if (publishedDocument == null
                || publishedDocument.getCourseId() == null
                || publishedDocument.getResourceId() == null
                || publishedDocument.getId() == null
                || publishedDocument.getVersionNo() == null) {
            return;
        }

        Long courseId = publishedDocument.getCourseId();
        Long resourceId = publishedDocument.getResourceId();

        String courseName = jdbc.query(
                """
                SELECT name
                FROM course
                WHERE id = ?
                """,
                rs -> rs.next()
                        ? rs.getString("name")
                        : null,
                courseId
        );

        if (courseName == null || courseName.isBlank()) {
            courseName = "课程";
        }

        /*
         * 查询其他有效资料的责任人。
         *
         * 规则：
         * 1. 优先使用资料指定责任人。
         * 2. 责任人无效时回退到课程负责人。
         * 3. 同一责任人负责多份资料时合并成一条通知。
         * 4. 排除本次刚刚更新的资源。
         */
        List<RecipientSummary> recipients = jdbc.query(
                """
                SELECT
                    COALESCE(
                        responsible.id,
                        owner.id
                    ) AS recipient_id,

                    COUNT(DISTINCT d.id) AS document_count

                FROM course_document d

                JOIN course c
                  ON c.id = d.course_id
                 AND c.status = 1

                LEFT JOIN sys_user responsible
                  ON responsible.id = d.update_responsible_id
                 AND responsible.status = 1
                 AND (
                     responsible.role = 'ADMIN'
                     OR (
                         responsible.role = 'TEACHER'
                         AND responsible.id = c.user_id
                     )
                 )

                LEFT JOIN sys_user owner
                  ON owner.id = c.user_id
                 AND owner.status = 1
                 AND owner.role IN ('TEACHER', 'ADMIN')

                WHERE d.course_id = ?

                  AND d.resource_id <> ?

                  AND d.status = 'READY'

                  AND d.version_status = 'CURRENT'

                  AND d.review_status = 'APPROVED'

                  AND d.quality_status = 'NORMAL'

                  AND (
                      d.valid_until IS NULL
                      OR d.valid_until >= CURDATE()
                  )

                  AND COALESCE(
                      responsible.id,
                      owner.id
                  ) IS NOT NULL

                GROUP BY COALESCE(
                    responsible.id,
                    owner.id
                )

                ORDER BY recipient_id ASC
                """,

                (rs, rowNum) -> new RecipientSummary(
                        rs.getLong("recipient_id"),
                        rs.getLong("document_count")
                ),

                courseId,
                resourceId
        );

        if (recipients.isEmpty()) {
            return;
        }

        /*
         * 同一次资源版本发布使用固定业务标识。
         * 数据库唯一索引负责最终幂等。
         */
        String bizKey =
                courseId
                        + ":"
                        + resourceId
                        + ":"
                        + publishedDocument.getVersionNo();

        String title =
                "课程资料版本已更新，请检查负责资料";

        for (RecipientSummary recipient : recipients) {

            String content =
                    "课程：《"
                            + courseName
                            + "》发布了新的资料版本。\n"

                            + "本次更新资料：《"
                            + publishedDocument.getOriginalName()
                            + "》，新版本为 V"
                            + publishedDocument.getVersionNo()
                            + "。\n"

                            + "你目前负责该课程的 "
                            + recipient.documentCount()
                            + " 份其他有效资料。"
                            + "请检查这些资料是否需要同步更新，"
                            + "避免课件内容与当前课程教学内容不一致。";

            notifications.createIfAbsentWithKey(
                    recipient.userId(),
                    BIZ_TYPE,
                    bizKey,
                    title,
                    content
            );
        }
    }

    private record RecipientSummary(
            Long userId,
            long documentCount
    ) {}
}
