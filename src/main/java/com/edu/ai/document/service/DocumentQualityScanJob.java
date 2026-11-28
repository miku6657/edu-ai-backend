package com.edu.ai.document.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class DocumentQualityScanJob {

    private static final Logger log =
            LoggerFactory.getLogger(DocumentQualityScanJob.class);

    private static final int BATCH_SIZE = 100;

    private final JdbcTemplate jdbc;

    private final DocumentQualityScanService scanService;

    public DocumentQualityScanJob(
            JdbcTemplate jdbc,
            DocumentQualityScanService scanService) {

        this.jdbc = jdbc;
        this.scanService = scanService;
    }

    /**
     * 启动60秒后首次扫描，之后每24小时执行一次。
     */
    @Scheduled(
            initialDelay = 60_000,
            fixedDelay = 86_400_000
    )
    public void scanUninspectedCurrentDocuments() {

        long lastId = 0L;

        long scanned = 0L;

        long skippedOrFailed = 0L;

        while (true) {

            List<Candidate> candidates =
                    queryCandidates(lastId);

            if (candidates.isEmpty()) {
                break;
            }

            for (Candidate candidate : candidates) {

                lastId = candidate.documentId();

                try {

                    scanService.scan(
                            candidate.courseId(),
                            candidate.documentId(),
                            candidate.ownerId()
                    );

                    scanned++;

                } catch (Exception e) {

                    skippedOrFailed++;

                    log.warn(
                            "定期质量检查未完成，courseId={}, documentId={}",
                            candidate.courseId(),
                            candidate.documentId(),
                            e
                    );
                }
            }

            if (candidates.size() < BATCH_SIZE) {
                break;
            }
        }

        log.info(
                "定期资料质量检查结束，scanned={}, skippedOrFailed={}",
                scanned,
                skippedOrFailed
        );
    }

    private List<Candidate> queryCandidates(long lastId) {

        return jdbc.query(
                """
                SELECT
                    d.id AS document_id,
                    d.course_id,
                    c.user_id AS owner_id

                FROM course_document d

                JOIN course c
                  ON c.id = d.course_id
                 AND c.status = 1

                JOIN sys_user owner
                  ON owner.id = c.user_id
                 AND owner.status = 1
                 AND owner.role = 'TEACHER'

                WHERE d.id > ?

                  AND d.status = 'READY'

                  AND d.version_status = 'CURRENT'

                  AND d.review_status = 'APPROVED'

                  AND d.quality_status = 'NORMAL'

                  AND d.storage_name IS NOT NULL

                  AND d.storage_name <> ''

                  AND NOT EXISTS (
                      SELECT 1
                      FROM course_document_quality_task t
                      WHERE t.course_id = d.course_id
                        AND t.document_id = d.id
                        AND t.version_no = COALESCE(d.version_no, 1)
                  )

                ORDER BY d.id ASC

                LIMIT ?
                """,
                (rs, rowNum) -> new Candidate(
                        rs.getLong("course_id"),
                        rs.getLong("document_id"),
                        rs.getLong("owner_id")
                ),
                lastId,
                BATCH_SIZE
        );
    }

    private record Candidate(
            Long courseId,
            Long documentId,
            Long ownerId
    ) {}
}
