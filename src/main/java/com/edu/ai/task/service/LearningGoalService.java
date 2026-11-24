package com.edu.ai.task.service;

import com.edu.ai.common.BusinessException;
import com.edu.ai.course.service.CoursePermissionService;
import com.edu.ai.system.entity.User;
import com.edu.ai.system.enums.UserRole;
import com.edu.ai.system.mapper.UserMapper;
import com.edu.ai.task.dto.LearningGoalRequest;
import com.edu.ai.task.dto.LearningGoalVO;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class LearningGoalService {

    private final JdbcTemplate jdbc;
    private final CoursePermissionService permissions;
    private final UserMapper userMapper;

    public LearningGoalService(
            JdbcTemplate jdbc,
            CoursePermissionService permissions,
            UserMapper userMapper) {
        this.jdbc = jdbc;
        this.permissions = permissions;
        this.userMapper = userMapper;
    }

    private void requireStudent(Long userId) {
        User user = userMapper.selectById(userId);

        if (user == null
                || !Integer.valueOf(1).equals(user.getStatus())
                || !UserRole.STUDENT.name().equals(user.getRole())) {
            throw new BusinessException(
                    403, "只有学生可以设置个人学习目标"
            );
        }
    }

    private void requireMembership(
            Long courseId, Long userId) {

        requireStudent(userId);
        permissions.requireActiveRead(courseId, userId);
    }

    private LearningGoalVO mapGoal(
            ResultSet rs, int rowNum) throws SQLException {

        var confirmed = rs.getTimestamp("confirmed_at");

        return new LearningGoalVO(
                String.valueOf(rs.getLong("id")),
                String.valueOf(rs.getLong("course_id")),
                String.valueOf(rs.getLong("student_id")),
                rs.getString("goal_text"),
                rs.getInt("weekly_hours"),
                rs.getDate("target_date").toLocalDate(),
                rs.getString("status"),
                confirmed == null
                        ? null
                        : confirmed.toLocalDateTime(),
                rs.getTimestamp("create_time").toLocalDateTime(),
                rs.getTimestamp("update_time").toLocalDateTime()
        );
    }

    /**
     * 查询当前学生在某课程的学习目标。
     */
    public LearningGoalVO getByCourse(
            Long courseId, Long userId) {

        requireMembership(courseId, userId);

        List<LearningGoalVO> rows = jdbc.query(
                """
                SELECT *
                FROM student_learning_goal
                WHERE course_id = ? AND student_id = ?
                """,
                this::mapGoal,
                courseId,
                userId
        );

        return rows.isEmpty() ? null : rows.get(0);
    }

    /**
     * 查询当前学生的全部学习目标。
     * 只返回当前仍然有权限访问的课程目标。
     */
    public List<LearningGoalVO> listMine(Long userId) {
        requireStudent(userId);

        return jdbc.query(
                """
                SELECT g.*
                FROM student_learning_goal g
                JOIN course c
                    ON c.id = g.course_id
                JOIN course_student cs
                    ON cs.course_id = g.course_id
                   AND cs.student_id = g.student_id
                WHERE g.student_id = ?
                  AND c.status = 1
                ORDER BY g.update_time DESC, g.id DESC
                """,
                this::mapGoal,
                userId
        );
    }

    /**
     * 保存学习目标草稿。
     *
     * 同一个学生、同一门课程只能有一份目标单。
     * 已确认的目标不能被覆盖。
     */
    @Transactional(rollbackFor = Exception.class)
    public LearningGoalVO saveDraft(
            Long courseId,
            Long userId,
            LearningGoalRequest request) {

        requireMembership(courseId, userId);

        List<LearningGoalVO> existing = jdbc.query(
                """
                SELECT *
                FROM student_learning_goal
                WHERE course_id = ? AND student_id = ?
                FOR UPDATE
                """,
                this::mapGoal,
                courseId,
                userId
        );

        if (existing.isEmpty()) {

            jdbc.update(
                    """
                    INSERT INTO student_learning_goal (
                        student_id,
                        course_id,
                        goal_text,
                        weekly_hours,
                        target_date,
                        status,
                        create_time,
                        update_time
                    )
                    VALUES (?, ?, ?, ?, ?, 'DRAFT', NOW(), NOW())
                    """,
                    userId,
                    courseId,
                    request.goalText().trim(),
                    request.weeklyHours(),
                    request.targetDate()
            );

        } else {

            LearningGoalVO goal = existing.get(0);

            if (!"DRAFT".equals(goal.status())) {
                throw new BusinessException(
                        409, "学习目标已确认，不能直接修改"
                );
            }

            jdbc.update(
                    """
                    UPDATE student_learning_goal
                    SET goal_text = ?,
                        weekly_hours = ?,
                        target_date = ?,
                        update_time = NOW()
                    WHERE id = ? AND student_id = ?
                      AND status = 'DRAFT'
                    """,
                    request.goalText().trim(),
                    request.weeklyHours(),
                    request.targetDate(),
                    Long.valueOf(goal.id()),
                    userId
            );
        }

        return getByCourse(courseId, userId);
    }

    /**
     * 确认学习目标。
     * 重复确认时返回已确认结果。
     */
    @Transactional(rollbackFor = Exception.class)
    public LearningGoalVO confirm(
            Long goalId, Long userId) {

        requireStudent(userId);

        List<LearningGoalVO> rows = jdbc.query(
                """
                SELECT *
                FROM student_learning_goal
                WHERE id = ? AND student_id = ?
                FOR UPDATE
                """,
                this::mapGoal,
                goalId,
                userId
        );

        if (rows.isEmpty()) {
            throw new BusinessException(
                    404, "学习目标不存在"
            );
        }

        LearningGoalVO goal = rows.get(0);

        requireMembership(
                Long.valueOf(goal.courseId()),
                userId
        );

        if ("CONFIRMED".equals(goal.status())) {
            return goal;
        }

        if (!"DRAFT".equals(goal.status())) {
            throw new BusinessException(
                    409, "当前状态无法确认"
            );
        }

        jdbc.update(
                """
                UPDATE student_learning_goal
                SET status = 'CONFIRMED',
                    confirmed_at = ?,
                    update_time = NOW()
                WHERE id = ? AND student_id = ?
                  AND status = 'DRAFT'
                """,
                LocalDateTime.now(),
                goalId,
                userId
        );

        return getByCourse(
                Long.valueOf(goal.courseId()),
                userId
        );
    }
}
