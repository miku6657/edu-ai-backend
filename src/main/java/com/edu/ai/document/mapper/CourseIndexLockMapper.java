package com.edu.ai.document.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

@Mapper
public interface CourseIndexLockMapper {

    @Update("""
        INSERT INTO course_index_lock (
            course_id,
            lock_token,
            lock_type,
            lease_until
        )
        VALUES (
            #{courseId},
            #{token},
            #{lockType},
            #{leaseUntil}
        )
        ON DUPLICATE KEY UPDATE
            lock_token = IF(
                lease_until IS NULL OR lease_until < NOW(),
                VALUES(lock_token),
                lock_token
            ),
            lock_type = IF(
                lease_until IS NULL OR lease_until < NOW(),
                VALUES(lock_type),
                lock_type
            ),
            lease_until = IF(
                lease_until IS NULL OR lease_until < NOW(),
                VALUES(lease_until),
                lease_until
            )
        """)
    int acquire(
            @Param("courseId") Long courseId,
            @Param("token") String token,
            @Param("lockType") String lockType,
            @Param("leaseUntil") LocalDateTime leaseUntil
    );

    @Update("""
        UPDATE course_index_lock
        SET lease_until = #{leaseUntil}
        WHERE course_id = #{courseId}
          AND lock_token = #{token}
          AND lease_until > NOW()
        """)
    int renew(
            @Param("courseId") Long courseId,
            @Param("token") String token,
            @Param("leaseUntil") LocalDateTime leaseUntil
    );

    @Update("""
        UPDATE course_index_lock
        SET lock_token = NULL,
            lock_type = NULL,
            lease_until = NULL
        WHERE course_id = #{courseId}
          AND lock_token = #{token}
        """)
    int release(
            @Param("courseId") Long courseId,
            @Param("token") String token
    );
}
