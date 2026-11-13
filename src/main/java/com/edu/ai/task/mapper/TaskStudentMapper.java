package com.edu.ai.task.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.edu.ai.task.entity.TaskStudent;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;

@Mapper
public interface TaskStudentMapper
        extends BaseMapper<TaskStudent> {

    @Insert("""
        INSERT INTO task_student
            (task_id, student_id, completed, completed_time)
        VALUES
            (#{taskId}, #{studentId}, #{completed}, #{completedTime})
        ON DUPLICATE KEY UPDATE
            completed = #{completed},
            completed_time = #{completedTime}
        """)
    int upsertCompletion(
            @Param("taskId") Long taskId,
            @Param("studentId") Long studentId,
            @Param("completed") Integer completed,
            @Param("completedTime") LocalDateTime completedTime
    );
}
