package com.edu.ai.audit.service;

import com.edu.ai.audit.dto.ContentOperationLogPageVO;
import com.edu.ai.common.BusinessException;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

@Service
public class ContentOperationLogService {

    private static final Pattern TYPE_PATTERN =
            Pattern.compile("[A-Z0-9_]{1,50}");

    private final JdbcTemplate jdbc;

    public ContentOperationLogService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * 写入课程内容运营日志。
     *
     * 调用方处于事务中时，日志与业务操作共用事务。
     */
    @Transactional(rollbackFor = Exception.class)
    public void write(
            Long courseId,
            Long documentId,
            Long operatorId,
            String operationType,
            String operationName,
            String description,
            String beforeValue,
            String afterValue) {

        if (courseId == null || courseId <= 0
                || operatorId == null || operatorId <= 0
                || (documentId != null && documentId <= 0)) {

            throw new BusinessException(
                    400,
                    "内容运营日志关联信息不完整"
            );
        }

        String type = operationType == null
                ? ""
                : operationType.trim()
                        .toUpperCase(Locale.ROOT);

        if (!TYPE_PATTERN.matcher(type).matches()) {
            throw new BusinessException(
                    400,
                    "内容运营日志类型不合法"
            );
        }

        String name = requiredText(
                operationName,
                100,
                "操作名称"
        );

        String detail = optionalText(description, 1000);
        String before = optionalText(beforeValue, 4000);
        String after = optionalText(afterValue, 4000);

        String courseName = firstString(
                "SELECT name FROM course WHERE id = ?",
                "课程#" + courseId,
                courseId
        );

        String documentName = documentId == null
                ? null
                : firstString(
                        """
                        SELECT original_name
                        FROM course_document
                        WHERE id = ?
                          AND course_id = ?
                        """,
                        "资料#" + documentId,
                        documentId,
                        courseId
                );

        String operatorName = firstString(
                """
                SELECT COALESCE(
                    NULLIF(nickname, ''),
                    username
                )
                FROM sys_user
                WHERE id = ?
                """,
                "用户#" + operatorId,
                operatorId
        );

        jdbc.update(
                """
                INSERT INTO course_content_operation_log (
                    course_id,
                    course_name,
                    document_id,
                    document_name,
                    operator_id,
                    operator_name,
                    operation_type,
                    operation_name,
                    description,
                    before_value,
                    after_value,
                    create_time
                )
                VALUES (
                    ?, ?, ?, ?, ?, ?,
                    ?, ?, ?, ?, ?,
                    CURRENT_TIMESTAMP(3)
                )
                """,
                courseId,
                courseName,
                documentId,
                documentName,
                operatorId,
                operatorName,
                type,
                name,
                detail,
                before,
                after
        );
    }

    /**
     * 管理员分页查询内容运营日志。
     */
    @Transactional(readOnly = true)
    public ContentOperationLogPageVO list(
            long current,
            long size,
            Long courseId,
            Long operatorId,
            String operationType,
            LocalDateTime startTime,
            LocalDateTime endTime) {

        if (current < 1 || size < 1 || size > 100) {
            throw new BusinessException(
                    400,
                    "分页参数不合法"
            );
        }

        if (courseId != null && courseId <= 0) {
            throw new BusinessException(
                    400,
                    "课程ID不合法"
            );
        }

        if (operatorId != null && operatorId <= 0) {
            throw new BusinessException(
                    400,
                    "操作人ID不合法"
            );
        }

        if (startTime != null
                && endTime != null
                && startTime.isAfter(endTime)) {

            throw new BusinessException(
                    400,
                    "开始时间不能晚于结束时间"
            );
        }

        String type = null;

        if (operationType != null
                && !operationType.isBlank()) {

            type = operationType.trim()
                    .toUpperCase(Locale.ROOT);

            if (!TYPE_PATTERN.matcher(type).matches()) {
                throw new BusinessException(
                        400,
                        "操作类型不合法"
                );
            }
        }

        StringBuilder where =
                new StringBuilder(" WHERE 1 = 1 ");

        List<Object> args = new ArrayList<>();

        if (courseId != null) {
            where.append(" AND l.course_id = ? ");
            args.add(courseId);
        }

        if (operatorId != null) {
            where.append(" AND l.operator_id = ? ");
            args.add(operatorId);
        }

        if (type != null) {
            where.append(" AND l.operation_type = ? ");
            args.add(type);
        }

        if (startTime != null) {
            where.append(" AND l.create_time >= ? ");
            args.add(Timestamp.valueOf(startTime));
        }

        if (endTime != null) {
            where.append(" AND l.create_time <= ? ");
            args.add(Timestamp.valueOf(endTime));
        }

        Long count = jdbc.queryForObject(
                """
                SELECT COUNT(*)
                FROM course_content_operation_log l
                """ + where,
                Long.class,
                args.toArray()
        );

        long total = count == null ? 0L : count;

        if (current - 1 > Long.MAX_VALUE / size) {
            throw new BusinessException(
                    400,
                    "分页偏移量超出范围"
            );
        }

        long offset = (current - 1) * size;

        List<Object> pageArgs =
                new ArrayList<>(args);

        pageArgs.add(size);
        pageArgs.add(offset);

        List<ContentOperationLogPageVO.Item> records =
                jdbc.query(
                        """
                        SELECT
                            l.id,
                            l.course_id,
                            l.course_name,
                            l.document_id,
                            l.document_name,
                            l.operator_id,
                            l.operator_name,
                            l.operation_type,
                            l.operation_name,
                            l.description,
                            l.before_value,
                            l.after_value,
                            l.create_time

                        FROM course_content_operation_log l
                        """
                        + where
                        + """
                         ORDER BY
                            l.create_time DESC,
                            l.id DESC
                         LIMIT ? OFFSET ?
                        """,

                        (rs, rowNum) ->
                                new ContentOperationLogPageVO.Item(
                                        stringValue(
                                                rs.getObject("id")
                                        ),
                                        stringValue(
                                                rs.getObject("course_id")
                                        ),
                                        rs.getString("course_name"),
                                        stringValue(
                                                rs.getObject("document_id")
                                        ),
                                        rs.getString("document_name"),
                                        stringValue(
                                                rs.getObject("operator_id")
                                        ),
                                        rs.getString("operator_name"),
                                        rs.getString("operation_type"),
                                        rs.getString("operation_name"),
                                        rs.getString("description"),
                                        rs.getString("before_value"),
                                        rs.getString("after_value"),
                                        rs.getTimestamp("create_time")
                                                .toLocalDateTime()
                                ),

                        pageArgs.toArray()
                );

        long pages = total == 0
                ? 0
                : (total + size - 1) / size;

        return new ContentOperationLogPageVO(
                current,
                size,
                total,
                pages,
                records
        );
    }

    private String firstString(
            String sql,
            String fallback,
            Object... args) {

        List<String> values = jdbc.query(
                sql,
                (rs, rowNum) -> rs.getString(1),
                args
        );

        if (values.isEmpty()
                || values.get(0) == null
                || values.get(0).isBlank()) {

            return fallback;
        }

        return values.get(0);
    }

    private String requiredText(
            String value,
            int max,
            String field) {

        String result = value == null
                ? ""
                : value.trim();

        if (result.isBlank()) {
            throw new BusinessException(
                    400,
                    field + "不能为空"
            );
        }

        if (result.length() > max) {
            throw new BusinessException(
                    400,
                    field + "不能超过" + max + "个字符"
            );
        }

        return result;
    }

    private String optionalText(
            String value,
            int max) {

        if (value == null || value.isBlank()) {
            return null;
        }

        String result = value.trim();

        return result.length() <= max
                ? result
                : result.substring(0, max);
    }

    private String stringValue(Object value) {
        return value == null
                ? null
                : value.toString();
    }
}
