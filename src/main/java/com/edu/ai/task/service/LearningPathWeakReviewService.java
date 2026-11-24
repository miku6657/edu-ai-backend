package com.edu.ai.task.service;

import com.edu.ai.common.BusinessException;
import com.edu.ai.course.service.CoursePermissionService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class LearningPathWeakReviewService {

    private final JdbcTemplate jdbc;
    private final CoursePermissionService permissions;

    public LearningPathWeakReviewService(
            JdbcTemplate jdbc,
            CoursePermissionService permissions) {
        this.jdbc = jdbc;
        this.permissions = permissions;
    }

    public record WeakReviewItem(
            String pointId,
            String title,
            int effectiveCount,
            int sessionCount,
            int manualLevel,
            int inferredLevel,
            int currentMinutes,
            int recommendedMinutes,
            int additionalMinutes
    ) {}

    public record WeakReviewPreview(
            String pathId,
            int additionalTotalMinutes,
            String message,
            List<WeakReviewItem> items
    ) {}

    /**
     * 预览疑似薄弱点所需增加的复习时间。
     *
     * 只读操作：
     * 不修改路径、DDL或学生自评。
     */
    public WeakReviewPreview preview(
            Long pathId,
            Long studentId) {

        List<Long> courseIds = jdbc.queryForList(
                """
                SELECT course_id
                FROM student_learning_path
                WHERE id = ?
                  AND student_id = ?
                  AND status = 'ACTIVE'
                """,
                Long.class,
                pathId,
                studentId
        );

        if (courseIds.isEmpty()) {
            throw new BusinessException(
                    404, "学习路径不存在或无权访问"
            );
        }

        Long courseId = courseIds.get(0);

        permissions.requireActiveRead(courseId, studentId);

        List<WeakReviewItem> items = jdbc.query(
                """
                SELECT
                    p.id AS point_id,
                    p.title,
                    p.estimated_minutes,
                    COALESCE(m.mastery_level, 2)
                        AS manual_level,
                    COALESCE(q.effective_count, 0)
                        AS effective_count,
                    COALESCE(q.session_count, 0)
                        AS session_count,
                    COALESCE(s.planned_minutes, 0)
                        AS planned_minutes
                FROM course_knowledge_point p
                LEFT JOIN student_knowledge_mastery m
                  ON m.point_id = p.id
                 AND m.student_id = ?
                LEFT JOIN (
                    SELECT point_id,
                           SUM(LEAST(question_count, 2))
                               AS effective_count,
                           COUNT(*) AS session_count
                    FROM (
                        SELECT point_id,
                               session_id,
                               COUNT(*) AS question_count
                        FROM student_question_knowledge
                        WHERE student_id = ?
                          AND course_id = ?
                          AND asked_at >= NOW()
                              - INTERVAL 30 DAY
                        GROUP BY point_id, session_id
                    ) t
                    GROUP BY point_id
                ) q ON q.point_id = p.id
                LEFT JOIN (
                    SELECT point_id,
                           SUM(planned_minutes)
                               AS planned_minutes
                    FROM student_learning_path_item
                    WHERE path_id = ?
                    GROUP BY point_id
                ) s ON s.point_id = p.id
                WHERE p.course_id = ?
                ORDER BY effective_count DESC, p.sort_order, p.id
                """,
                (rs, rowNum) -> {
                    int count = rs.getInt("effective_count");
                    int sessions = rs.getInt("session_count");
                    int manual = rs.getInt("manual_level");
                    int baseMinutes = rs.getInt("estimated_minutes");
                    int currentMinutes = rs.getInt("planned_minutes");

                    int inferred = 2;
                    if (count >= 6 && sessions >= 3) {
                        inferred = 0;
                    } else if (count >= 3 && sessions >= 2) {
                        inferred = 1;
                    }

                    // 取更需要复习的等级，不覆盖学生自评。
                    int effective = Math.min(manual, inferred);

                    double multiplier = switch (effective) {
                        case 0 -> 1.5;
                        case 1 -> 1.25;
                        default -> 1.0;
                    };

                    int recommended = (int) Math.ceil(
                            baseMinutes * multiplier
                    );

                    int additional = Math.max(
                            0,
                            recommended - currentMinutes
                    );

                    return new WeakReviewItem(
                            Long.toString(rs.getLong("point_id")),
                            rs.getString("title"),
                            count,
                            sessions,
                            manual,
                            inferred,
                            currentMinutes,
                            recommended,
                            additional
                    );
                },
                studentId,
                studentId,
                courseId,
                pathId,
                courseId
        );

        // 只把有有效提问且确实需要追加时间的
        // 知识点作为自动复习建议。
        List<WeakReviewItem> suggested = new ArrayList<>();
        int totalAdditional = 0;

        for (WeakReviewItem item : items) {
            if (item.inferredLevel() >= 2
                    || item.additionalMinutes() <= 0) {
                continue;
            }

            suggested.add(item);
            totalAdditional += item.additionalMinutes();
        }

        String message = suggested.isEmpty()
                ? "当前提问记录尚未产生额外复习建议"
                : "识别到 " + suggested.size()
                    + " 个疑似薄弱知识点，建议追加 "
                    + totalAdditional + " 分钟复习";

        return new WeakReviewPreview(
                pathId.toString(),
                totalAdditional,
                message,
                suggested
        );
    }
}
