package com.edu.ai.document.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.ZoneId;

@Service
public class DocumentAccessTrackingService {

    private static final Logger log =
            LoggerFactory.getLogger(
                    DocumentAccessTrackingService.class
            );

    private static final ZoneId APP_ZONE =
            ZoneId.of("Asia/Shanghai");

    private final JdbcTemplate jdbc;

    public DocumentAccessTrackingService(
            JdbcTemplate jdbc) {

        this.jdbc = jdbc;
    }

    /**
     * 记录学生资料访问。
     *
     * 仅记录正常课程中：
     * 1. 已正式发布的当前资料。
     * 2. 尚未过期的资料。
     * 3. 正常状态的学生访问。
     */
    public void recordStudentAccess(
            Long documentId,
            Long userId,
            String accessType) {

        if (documentId == null || userId == null) {
            return;
        }

        if (!"PREVIEW".equals(accessType)
                && !"DOWNLOAD".equals(accessType)) {

            throw new IllegalArgumentException(
                    "不支持的资料访问类型"
            );
        }

        try {

            LocalDate today = LocalDate.now(APP_ZONE);

            jdbc.update(
                    """
                    INSERT INTO course_document_access_log (
                        course_id,
                        document_id,
                        student_id,
                        access_type,
                        accessed_at
                    )

                    SELECT
                        d.course_id,
                        d.id,
                        u.id,
                        ?,
                        CURRENT_TIMESTAMP

                    FROM course_document d

                    JOIN sys_user u
                      ON u.id = ?
                     AND u.role = 'STUDENT'
                     AND u.status = 1

                    JOIN course_student cs
                      ON cs.course_id = d.course_id
                     AND cs.student_id = u.id

                    JOIN course c
                      ON c.id = d.course_id
                     AND c.status = 1

                    WHERE d.id = ?

                      AND d.status = 'READY'

                      AND d.version_status = 'CURRENT'

                      AND d.review_status = 'APPROVED'

                      AND (
                          d.valid_until IS NULL
                          OR d.valid_until >= ?
                      )
                    """,
                    accessType,
                    userId,
                    documentId,
                    today
            );

        } catch (DataAccessException e) {

            log.warn(
                    "记录课程资料访问失败，documentId={}, userId={}",
                    documentId,
                    userId,
                    e
            );
        }
    }
}
