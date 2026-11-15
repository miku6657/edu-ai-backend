package com.edu.ai.notification.service;

import com.edu.ai.common.BusinessException;
import com.edu.ai.notification.dto.*;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Service
public class NotificationService {

    private static final ZoneOffset DISPLAY_OFFSET =
            ZoneOffset.ofHours(8);

    private final JdbcTemplate jdbc;

    public NotificationService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * 分页获取用户通知。
     */
    public NotificationListVO list(
            Long userId,
            String readStatus,
            int page,
            int size) {

        if (page < 1 || page > 1_000_000
                || size < 1 || size > 100) {
            throw new BusinessException(
                    400, "分页参数不合法"
            );
        }

        if (readStatus != null
                && !readStatus.equals("UNREAD")
                && !readStatus.equals("READ")) {
            throw new BusinessException(
                    400, "read_status必须为UNREAD或READ"
            );
        }

        String where = " WHERE user_id = ?"
                + (readStatus == null
                    ? ""
                    : " AND read_status = ?");

        List<Object> countArgs = new ArrayList<>();
        countArgs.add(userId);

        if (readStatus != null) {
            countArgs.add(readStatus);
        }

        Long count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM sys_notification" + where,
                Long.class,
                countArgs.toArray()
        );

        long total = count == null ? 0 : count;
        long unread = unreadCount(userId);

        long offset = (long) (page - 1) * size;

        List<Object> queryArgs =
                new ArrayList<>(countArgs);

        queryArgs.add(size);
        queryArgs.add(offset);

        List<NotificationItemVO> items = jdbc.query(
                """
                SELECT
                    id,
                    biz_type,
                    biz_id,
                    title,
                    content,
                    read_status,
                    outbound_status,
                    sent_im_at,
                    read_at,
                    created_at
                FROM sys_notification
                """
                + where
                + " ORDER BY created_at DESC, id DESC"
                + " LIMIT ? OFFSET ?",

                (rs, rowNum) -> toVO(rs),
                queryArgs.toArray()
        );

        long pages = (total + size - 1) / size;

        return new NotificationListVO(
                unread,
                page,
                size,
                total,
                pages,
                items
        );
    }

    /**
     * 标记通知为已读。
     * 仅允许修改当前登录用户自己的通知。
     */
    @Transactional(rollbackFor = Exception.class)
    public MarkReadResultVO markRead(
            Long userId,
            MarkReadRequest request) {

        if (request == null) {
            throw new BusinessException(
                    400, "请求体不能为空"
            );
        }

        int updated;

        if (request.markAll()) {

            updated = jdbc.update(
                    """
                    UPDATE sys_notification
                    SET read_status = 'READ',
                        read_at = NOW(3)
                    WHERE user_id = ?
                      AND read_status = 'UNREAD'
                    """,
                    userId
            );

        } else {

            List<String> ids =
                    request.notificationIds();

            if (ids == null || ids.isEmpty()
                    || ids.size() > 100) {
                throw new BusinessException(
                        400,
                        "一次必须指定1至100条通知"
                );
            }

            List<Long> parsed = new ArrayList<>();

            for (String value : ids) {

                if (value == null
                        || !value.matches(
                                "[1-9][0-9]{0,18}"
                        )) {
                    throw new BusinessException(
                            400, "通知ID格式错误"
                    );
                }

                try {
                    parsed.add(Long.parseLong(value));
                } catch (NumberFormatException e) {
                    throw new BusinessException(
                            400, "通知ID超出范围"
                    );
                }
            }

            String placeholders = String.join(
                    ",",
                    Collections.nCopies(
                            parsed.size(), "?"
                    )
            );

            List<Object> args = new ArrayList<>();
            args.add(userId);
            args.addAll(parsed);

            updated = jdbc.update(
                    """
                    UPDATE sys_notification
                    SET read_status = 'READ',
                        read_at = NOW(3)
                    WHERE user_id = ?
                      AND read_status = 'UNREAD'
                      AND id IN (
                    """
                    + placeholders + ")",
                    args.toArray()
            );
        }

        return new MarkReadResultVO(
                updated,
                unreadCount(userId)
        );
    }

    /**
     * 获取当前用户未读通知数量。
     */
    public long unreadCount(Long userId) {

        Long count = jdbc.queryForObject(
                """
                SELECT COUNT(*)
                FROM sys_notification
                WHERE user_id = ?
                  AND read_status = 'UNREAD'
                """,
                Long.class,
                userId
        );

        return count == null ? 0 : count;
    }

    /**
     * 同一业务事件只生成一次通知。
     *
     * MySQL唯一索引负责幂等去重。
     */
    public void createIfAbsent(
            Long userId,
            String bizType,
            Long bizId,
            String title,
            String content) {

        if (userId == null || bizId == null) {
            return;
        }

        jdbc.update(
                """
                INSERT IGNORE INTO sys_notification (
                    user_id,
                    biz_type,
                    biz_id,
                    title,
                    content,
                    read_status,
                    outbound_status
                )
                VALUES (
                    ?, ?, ?, ?, ?,
                    'UNREAD',
                    'FALLBACK_WEB_ONLY'
                )
                """,
                userId,
                bizType,
                bizId.toString(),
                trim(title, 200),
                trim(content, 2000)
        );
    }

    private String trim(String value, int max) {

        if (value == null) {
            return "";
        }

        return value.length() <= max
                ? value
                : value.substring(0, max);
    }

    private NotificationItemVO toVO(ResultSet rs)
            throws SQLException {

        return new NotificationItemVO(
                Long.toString(rs.getLong("id")),
                rs.getString("biz_type"),
                rs.getString("biz_id"),
                rs.getString("title"),
                rs.getString("content"),
                rs.getString("read_status"),
                rs.getString("outbound_status"),
                iso(rs, "sent_im_at"),
                iso(rs, "read_at"),
                iso(rs, "created_at")
        );
    }

    private String iso(
            ResultSet rs,
            String column) throws SQLException {

        LocalDateTime value = rs.getObject(
                column,
                LocalDateTime.class
        );

        return value == null
                ? null
                : value.atOffset(DISPLAY_OFFSET)
                        .format(
                                DateTimeFormatter
                                        .ISO_OFFSET_DATE_TIME
                        );
    }
}
