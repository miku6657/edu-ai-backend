package com.edu.ai.document.service;

import com.edu.ai.notification.service.NotificationService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Component
public class DocumentExpiryNotificationJob {

    private static final Logger log =
            LoggerFactory.getLogger(
                    DocumentExpiryNotificationJob.class
            );

    private static final ZoneId APP_ZONE =
            ZoneId.of("Asia/Shanghai");

    private static final int BATCH_SIZE = 500;

    private static final String BIZ_EXPIRING =
            "DOC_EXPIRING";

    private static final String BIZ_EXPIRED =
            "DOC_EXPIRED";

    private final JdbcTemplate jdbc;

    private final NotificationService notifications;

    public DocumentExpiryNotificationJob(
            JdbcTemplate jdbc,
            NotificationService notifications) {

        this.jdbc = jdbc;
        this.notifications = notifications;
    }

    /**
     * 应用启动30秒后执行。
     *
     * 每次执行完成后间隔5分钟。
     */
    @Scheduled(
            initialDelay = 30000,
            fixedDelay = 300000
    )
    public void generateExpiryNotifications() {

        LocalDate today =
                LocalDate.now(APP_ZONE);

        long lastId = 0L;

        long scanned = 0L;

        try {

            while (true) {

                List<ExpiryCandidate> candidates =
                        queryCandidates(today, lastId);

                if (candidates.isEmpty()) {
                    break;
                }

                for (ExpiryCandidate candidate : candidates) {

                    try {

                        emitNotification(
                                candidate,
                                today
                        );

                    } catch (Exception e) {

                        log.warn(
                                "资料有效期通知生成失败，documentId={}",
                                candidate.documentId(),
                                e
                        );
                    }

                    lastId = candidate.documentId();

                    scanned++;
                }

                if (candidates.size() < BATCH_SIZE) {
                    break;
                }
            }

            log.debug(
                    "资料有效期扫描完成，date={}, scanned={}",
                    today,
                    scanned
            );

        } catch (Exception e) {

            log.error(
                    "资料有效期扫描任务执行失败",
                    e
            );
        }
    }

    /**
     * 分批查询即将到期或已过期的当前发布资料。
     */
    private List<ExpiryCandidate> queryCandidates(
            LocalDate today,
            long lastId) {

        return jdbc.query(
                """
                SELECT
                    d.id AS document_id,

                    d.course_id,

                    d.original_name AS document_name,

                    d.valid_until,

                    COALESCE(
                        d.expiry_remind_days,
                        7
                    ) AS remind_days,

                    c.name AS course_name,

                    COALESCE(
                        responsible.id,
                        owner.id
                    ) AS recipient_id

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

                WHERE d.id > ?

                  AND d.status = 'READY'

                  AND d.version_status = 'CURRENT'

                  AND d.review_status = 'APPROVED'

                  AND d.valid_until IS NOT NULL

                  AND DATEDIFF(
                      d.valid_until,
                      ?
                  ) <= COALESCE(
                      d.expiry_remind_days,
                      7
                  )

                ORDER BY d.id ASC

                LIMIT ?
                """,

                (rs, rowNum) -> new ExpiryCandidate(

                        rs.getLong("document_id"),

                        rs.getLong("course_id"),

                        rs.getString("document_name"),

                        rs.getString("course_name"),

                        rs.getObject(
                                "valid_until",
                                LocalDate.class
                        ),

                        rs.getInt("remind_days"),

                        rs.getObject(
                                "recipient_id",
                                Long.class
                        )
                ),

                lastId,
                today,
                BATCH_SIZE
        );
    }

    /**
     * 生成单份资料的到期通知。
     */
    private void emitNotification(
            ExpiryCandidate candidate,
            LocalDate today) {

        if (candidate.recipientId() == null) {

            log.warn(
                    "资料缺少有效更新责任人，courseId={}, documentId={}",
                    candidate.courseId(),
                    candidate.documentId()
            );

            return;
        }

        long remainingDays =
                ChronoUnit.DAYS.between(
                        today,
                        candidate.validUntil()
                );

        /*
         * 将截止日期作为业务事件的一部分。
         *
         * 同一截止日期不会重复通知；
         * 续期至新的截止日期后可以再次通知。
         */
        String bizKey =
                candidate.documentId()
                        + ":"
                        + candidate.validUntil();

        if (remainingDays < 0) {

            emitExpiredNotification(
                    candidate,
                    bizKey,
                    -remainingDays
            );

            return;
        }

        if (remainingDays <= candidate.remindDays()) {

            emitExpiringNotification(
                    candidate,
                    bizKey,
                    remainingDays
            );
        }
    }

    /**
     * 即将到期提醒。
     */
    private void emitExpiringNotification(
            ExpiryCandidate candidate,
            String bizKey,
            long remainingDays) {

        String title;

        if (remainingDays == 0) {

            title = "课程资料今日到期";

        } else {

            title = "课程资料即将到期";
        }

        String content =
                "课程：《"
                        + candidate.courseName()
                        + "》\n"

                        + "资料：《"
                        + candidate.documentName()
                        + "》\n"

                        + "有效截止日期："
                        + candidate.validUntil()
                        + "\n"

                        + "剩余有效天数："
                        + remainingDays
                        + "天\n"

                        + "请及时检查资料内容，"
                        + "更新资料版本或延长有效期。"
                        + "截止日期次日起，学生将无法访问该资料，"
                        + "该资料也将停止参与知识库问答检索。";

        notifications.createIfAbsentWithKey(
                candidate.recipientId(),

                BIZ_EXPIRING,

                bizKey,

                title,

                content
        );
    }

    /**
     * 已过期提醒。
     */
    private void emitExpiredNotification(
            ExpiryCandidate candidate,
            String bizKey,
            long expiredDays) {

        String title =
                "课程资料已过期并停止学生访问";

        String content =
                "课程：《"
                        + candidate.courseName()
                        + "》\n"

                        + "资料：《"
                        + candidate.documentName()
                        + "》\n"

                        + "有效截止日期："
                        + candidate.validUntil()
                        + "\n"

                        + "已过期："
                        + expiredDays
                        + "天\n"

                        + "系统已限制学生访问及知识库问答检索。"
                        + "原始文件、历史版本及向量数据仍然保留。"
                        + "请及时更新资料或重新设置有效期。";

        notifications.createIfAbsentWithKey(
                candidate.recipientId(),

                BIZ_EXPIRED,

                bizKey,

                title,

                content
        );
    }

    /**
     * 等待检查的资料信息。
     */
    private record ExpiryCandidate(

            Long documentId,

            Long courseId,

            String documentName,

            String courseName,

            LocalDate validUntil,

            int remindDays,

            Long recipientId

    ) {}
}
