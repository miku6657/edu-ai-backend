package com.edu.ai.document.service;

import com.edu.ai.document.mapper.CourseIndexLockMapper;

import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class CourseIndexLockService {

    private static final int LOCK_MINUTES = 35;

    private final CourseIndexLockMapper mapper;

    public CourseIndexLockService(
            CourseIndexLockMapper mapper) {
        this.mapper = mapper;
    }

    /**
     * 尝试获取课程索引锁。
     *
     * 返回 token；获取失败返回 null。
     */
    public String tryAcquire(
            Long courseId,
            String lockType) {

        String token = UUID.randomUUID().toString();

        int result = mapper.acquire(
                courseId,
                token,
                lockType,
                LocalDateTime.now().plusMinutes(LOCK_MINUTES)
        );

        // INSERT 返回 1，抢占空闲/过期锁返回 2，均视为获取成功；
        // 返回 0 表示锁仍被他人持有，获取失败。
        if (result < 1) {
            return null;
        }

        return token;
    }

    /**
     * 续租。
     */
    public boolean renew(
            Long courseId,
            String token) {

        return mapper.renew(
                courseId,
                token,
                LocalDateTime.now().plusMinutes(LOCK_MINUTES)
        ) == 1;
    }

    /**
     * 释放锁。
     */
    public void release(
            Long courseId,
            String token) {

        if (courseId == null || token == null) {
            return;
        }

        mapper.release(courseId, token);
    }

    /**
     * 检查当前操作是否仍持有有效锁。
     */
    public boolean isOwned(
            Long courseId,
            String token) {

        if (courseId == null || token == null) {
            return false;
        }

        return mapper.isOwned(courseId, token) == 1;
    }
}
