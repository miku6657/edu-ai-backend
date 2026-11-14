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
     * 尝试获取课程锁。
     *
     * 获取成功返回 token。
     * 获取失败返回 null。
     */
    public String tryAcquire(
            Long courseId,
            String lockType) {

        if (courseId == null || courseId <= 0
                || lockType == null
                || lockType.isBlank()) {
            throw new IllegalArgumentException(
                    "课程锁参数不合法"
            );
        }

        String token = UUID.randomUUID().toString();

        mapper.acquire(
                courseId,
                token,
                lockType,
                LocalDateTime.now()
                        .plusMinutes(LOCK_MINUTES)
        );

        // 不依赖 INSERT/UPDATE 受影响行数。
        // 直接读取数据库确认 token 所有权。
        if (!isOwned(courseId, token)) {
            return null;
        }

        return token;
    }

    /**
     * 续租当前课程锁。
     */
    public boolean renew(
            Long courseId,
            String token) {

        if (courseId == null
                || token == null
                || token.isBlank()) {
            return false;
        }

        return mapper.renew(
                courseId,
                token,
                LocalDateTime.now()
                        .plusMinutes(LOCK_MINUTES)
        ) == 1;
    }

    /**
     * 检查当前 token 是否仍然有效。
     */
    public boolean isOwned(
            Long courseId,
            String token) {

        if (courseId == null
                || token == null
                || token.isBlank()) {
            return false;
        }

        return mapper.isOwned(
                courseId,
                token
        ) == 1;
    }

    /**
     * 仅当前持有者才能释放课程锁。
     */
    public void release(
            Long courseId,
            String token) {

        if (courseId == null
                || token == null
                || token.isBlank()) {
            return;
        }

        mapper.release(courseId, token);
    }

    /**
     * 在当前事务中锁定课程锁记录并校验所有权。
     *
     * 用于 Embedding 迁移最终提交前。
     * 必须在事务中调用。
     */
    public void assertOwnedForUpdate(
            Long courseId,
            String token) {

        if (courseId == null
                || token == null
                || token.isBlank()) {

            throw new IllegalArgumentException(
                    "课程锁参数不合法"
            );
        }

        String currentToken =
                mapper.selectTokenForUpdate(courseId);

        if (!token.equals(currentToken)) {
            throw new IllegalStateException(
                    "课程索引锁已经失效，禁止提交迁移"
            );
        }
    }
}
