package com.edu.ai.document.service;

import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class DocumentAccessTrackingService {

    private static final Logger log =
            LoggerFactory.getLogger(
                    DocumentAccessTrackingService.class
            );

    private final JdbcTemplate jdbc;

    public DocumentAccessTrackingService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

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
                    WHERE d.id = ?
                      AND d.status = 'READY'
                      AND d.version_status = 'CURRENT'
                      AND d.review_status = 'APPROVED'
                    """,
                    accessType,
                    userId,
                    documentId
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
