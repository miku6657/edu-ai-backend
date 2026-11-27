package com.edu.ai.exercise.service;

import com.edu.ai.chat.entity.ChatSession;
import com.edu.ai.chat.mapper.ChatSessionMapper;
import com.edu.ai.common.BusinessException;
import com.edu.ai.course.service.CoursePermissionService;
import com.edu.ai.exercise.dto.StudentExerciseExplainSessionVO;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class StudentExerciseExplainSessionService {

    private final JdbcTemplate jdbc;
    private final ChatSessionMapper chatSessionMapper;
    private final CoursePermissionService permissions;

    public StudentExerciseExplainSessionService(
            JdbcTemplate jdbc,
            ChatSessionMapper chatSessionMapper,
            CoursePermissionService permissions) {

        this.jdbc = jdbc;
        this.chatSessionMapper = chatSessionMapper;
        this.permissions = permissions;
    }

    /**
     * 获取或创建当前学生针对指定题目的 AI 讲解会话。
     */
    @Transactional
    public StudentExerciseExplainSessionVO getOrCreate(
            Long questionId,
            Long courseId,
            Long studentId) {

        if (questionId == null || questionId <= 0
                || courseId == null || courseId <= 0) {

            throw new BusinessException(400, "题目或课程参数无效");
        }

        // 校验学生是否有权访问课程
        permissions.requireActiveRead(courseId, studentId);

        // 确认题目确实属于当前课程。
        // FOR UPDATE 用于串行化同一道题的会话初始化。
        List<Long> questionIds = jdbc.query(
                """
                SELECT q.id
                FROM course_exercise_question q
                JOIN course_exercise e
                  ON e.id = q.exercise_id
                WHERE q.id = ?
                  AND e.course_id = ?
                FOR UPDATE
                """,
                (rs, rowNum) -> rs.getLong(1),
                questionId,
                courseId
        );

        if (questionIds.isEmpty()) {
            throw new BusinessException(404, "题目不存在或不属于当前课程");
        }

        // 查询已有会话关联
        List<Long> existingIds = jdbc.query(
                """
                SELECT chat_session_id
                FROM course_exercise_explain_session
                WHERE student_id = ?
                  AND course_id = ?
                  AND question_id = ?
                """,
                (rs, rowNum) -> rs.getLong(1),
                studentId,
                courseId,
                questionId
        );

        if (!existingIds.isEmpty()) {

            Long sessionId = existingIds.get(0);

            ChatSession existing =
                    chatSessionMapper.selectById(sessionId);

            if (existing != null) {

                if (!studentId.equals(existing.getUserId())
                        || !courseId.equals(existing.getCourseId())) {

                    throw new BusinessException(403, "无权访问该讲解会话");
                }

                return toVO(sessionId, courseId, questionId);
            }

            // 原聊天会话已经被删除，清理失效关联
            jdbc.update(
                    """
                    DELETE FROM course_exercise_explain_session
                    WHERE student_id = ?
                      AND course_id = ?
                      AND question_id = ?
                    """,
                    studentId,
                    courseId,
                    questionId
            );
        }

        // 创建独立的 RAG 聊天会话
        ChatSession session = new ChatSession();

        session.setCourseId(courseId);
        session.setUserId(studentId);
        session.setTitle("错题讲解：" + questionId);
        session.setCreateTime(LocalDateTime.now());
        session.setUpdateTime(LocalDateTime.now());

        chatSessionMapper.insert(session);

        if (session.getId() == null) {
            throw new IllegalStateException("讲解会话创建失败");
        }

        // 保存题目与聊天会话的关联
        jdbc.update(
                """
                INSERT INTO course_exercise_explain_session (
                    student_id,
                    course_id,
                    question_id,
                    chat_session_id
                )
                VALUES (?, ?, ?, ?)
                """,
                studentId,
                courseId,
                questionId,
                session.getId()
        );

        return toVO(
                session.getId(),
                courseId,
                questionId
        );
    }

    private StudentExerciseExplainSessionVO toVO(
            Long sessionId,
            Long courseId,
            Long questionId) {

        return new StudentExerciseExplainSessionVO(
                sessionId.toString(),
                courseId.toString(),
                questionId.toString()
        );
    }
}
