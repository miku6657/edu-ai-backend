package com.edu.ai.task.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.edu.ai.common.BusinessException;
import com.edu.ai.course.service.CoursePermissionService;
import com.edu.ai.task.entity.DdlTask;
import com.edu.ai.task.mapper.DdlTaskMapper;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class LearningPathTaskSyncService {

    private final JdbcTemplate jdbc;
    private final DdlTaskMapper ddlMapper;
    private final DdlDisplayCodeGenerator codeGenerator;
    private final CoursePermissionService permissions;

    public LearningPathTaskSyncService(
            JdbcTemplate jdbc,
            DdlTaskMapper ddlMapper,
            DdlDisplayCodeGenerator codeGenerator,
            CoursePermissionService permissions) {

        this.jdbc = jdbc;
        this.ddlMapper = ddlMapper;
        this.codeGenerator = codeGenerator;
        this.permissions = permissions;
    }

    private record StageRow(
            Long id,
            Long studentId,
            Long courseId,
            String courseName,
            String pointTitle,
            int weekNo,
            int segmentNo,
            int plannedMinutes,
            LocalDate dueDate
    ) {}

    /**
     * 将某条学习路径的阶段任务同步为个人 DDL。
     *
     * 必须由当前学生本人触发。
     * 重复调用不会重新生成已有任务。
     */
    @Transactional(rollbackFor = Exception.class)
    public int syncPath(Long pathId, Long studentId) {

        // 锁定路径，避免同一路径被并发同步，同时读取课程ID。
        List<Long> courseIds = jdbc.queryForList(
                """
                SELECT course_id
                FROM student_learning_path
                WHERE id = ? AND student_id = ?
                FOR UPDATE
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

        // 课程权限保护：学生退出课程后不能再同步该路径。
        permissions.requireActiveRead(courseId, studentId);

        List<StageRow> stages = jdbc.query(
                """
                SELECT
                    i.id,
                    p.student_id,
                    p.course_id,
                    c.name AS course_name,
                    kp.title AS point_title,
                    i.week_no,
                    i.segment_no,
                    i.planned_minutes,
                    i.planned_due_date
                FROM student_learning_path_item i
                JOIN student_learning_path p
                    ON p.id = i.path_id
                JOIN course c
                    ON c.id = p.course_id
                JOIN course_knowledge_point kp
                    ON kp.id = i.point_id
                WHERE i.path_id = ?
                  AND p.student_id = ?
                ORDER BY i.sequence_no, i.id
                """,
                (rs, rowNum) -> new StageRow(
                        rs.getLong("id"),
                        rs.getLong("student_id"),
                        rs.getLong("course_id"),
                        rs.getString("course_name"),
                        rs.getString("point_title"),
                        rs.getInt("week_no"),
                        rs.getInt("segment_no"),
                        rs.getInt("planned_minutes"),
                        rs.getDate("planned_due_date")
                                .toLocalDate()
                ),
                pathId,
                studentId
        );

        int created = 0;

        for (StageRow stage : stages) {

            Long exists = ddlMapper.selectCount(
                    new LambdaQueryWrapper<DdlTask>()
                            .eq(
                                DdlTask::getLearningPathItemId,
                                stage.id()
                            )
            );

            if (exists != null && exists > 0) {
                continue;
            }

            DdlTask task = new DdlTask();

            task.setUserId(studentId);
            task.setCourseId(stage.courseId());
            task.setCourseName(stage.courseName());

            task.setDisplayCode(
                    codeGenerator.generate()
            );

            task.setTaskTitle(
                    "第" + stage.weekNo() + "周：" +
                    stage.pointTitle() +
                    (stage.segmentNo() > 1
                            ? "（阶段" + stage.segmentNo() + "）"
                            : "")
            );

            task.setDescription(
                    "学习路径阶段任务，预计学习" +
                    stage.plannedMinutes() + "分钟"
            );

            task.setTimePrecision("DATE_ONLY");
            task.setDueDate(stage.dueDate());
            task.setDueTime(null);
            task.setDueAt(
                    stage.dueDate().atTime(23, 59, 59)
            );

            task.setRelativeTime(false);
            task.setTimeInferred(true);
            task.setUserTimezone("Asia/Shanghai");

            task.setSourcePlatform("LEARNING_PATH");
            task.setLearningPathItemId(stage.id());

            task.setFollowUpCount(0);
            task.setRemindedT24h(false);
            task.setRemindedT6h(false);
            task.setRemindedOverdue(false);

            task.setCreatedAt(LocalDateTime.now());
            task.setUpdatedAt(LocalDateTime.now());

            // 已经过期的阶段任务直接标记逾期。
            task.setStatus(
                    task.getDueAt().isBefore(
                            LocalDateTime.now()
                    )
                            ? "OVERDUE"
                            : "ACTIVE"
            );

            ddlMapper.insert(task);
            created++;
        }

        return created;
    }

    /**
     * DDL 完成状态同步到学习路径阶段任务。
     *
     * 由 DdlTaskService 在同一事务内调用，
     * 确保两个模块不会只更新其中一方。
     */
    public void syncCompletion(
            DdlTask task,
            boolean completed) {

        Long itemId = task.getLearningPathItemId();

        if (itemId == null) {
            return;
        }

        int updated = jdbc.update(
                """
                UPDATE student_learning_path_item i
                JOIN student_learning_path p
                    ON p.id = i.path_id
                SET i.status = ?,
                    i.completed_at = ?
                WHERE i.id = ?
                  AND p.student_id = ?
                """,
                completed ? "COMPLETED" : "PENDING",
                completed ? LocalDateTime.now() : null,
                itemId,
                task.getUserId()
        );

        if (updated == 0) {
            // MySQL 在更新前后值完全相同时可能返回 0，
            // 只有关系确实不存在时才视为失败。
            Long count = jdbc.queryForObject(
                    """
                    SELECT COUNT(*)
                    FROM student_learning_path_item i
                    JOIN student_learning_path p
                      ON p.id = i.path_id
                    WHERE i.id = ?
                      AND p.student_id = ?
                    """,
                    Long.class,
                    itemId,
                    task.getUserId()
            );

            if (count == null || count == 0) {
                throw new BusinessException(
                        409,
                        "学习路径阶段任务同步失败"
                );
            }
        }
    }

    /**
     * 修改学习路径任务前，统一锁定路径及阶段任务。
     * 必须在调用方 @Transactional 事务中使用。
     */
    public void lockForCompletion(
            Long itemId,
            Long studentId) {

        List<Long> pathIds = jdbc.queryForList(
                """
                SELECT p.id
                FROM student_learning_path_item i
                JOIN student_learning_path p
                  ON p.id = i.path_id
                WHERE i.id = ?
                  AND p.student_id = ?
                  AND p.status = 'ACTIVE'
                """,
                Long.class,
                itemId,
                studentId
        );

        if (pathIds.isEmpty()) {
            throw new BusinessException(
                    409, "学习路径不存在或已失效"
            );
        }

        Long pathId = pathIds.get(0);

        // 先锁学习路径
        List<Long> lockedPaths = jdbc.queryForList(
                """
                SELECT id
                FROM student_learning_path
                WHERE id = ?
                  AND student_id = ?
                  AND status = 'ACTIVE'
                FOR UPDATE
                """,
                Long.class,
                pathId,
                studentId
        );

        if (lockedPaths.isEmpty()) {
            throw new BusinessException(
                    409, "学习路径状态已变化"
            );
        }

        // 再锁阶段项
        List<Long> lockedItems = jdbc.queryForList(
                """
                SELECT id
                FROM student_learning_path_item
                WHERE id = ?
                  AND path_id = ?
                FOR UPDATE
                """,
                Long.class,
                itemId,
                pathId
        );

        if (lockedItems.isEmpty()) {
            throw new BusinessException(
                    409, "学习阶段任务不存在"
            );
        }
    }
}
