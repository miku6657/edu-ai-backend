package com.edu.ai.document.service;

import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Component;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

@Component
public class CourseIndexLease {

    private final CourseIndexLockService lockService;

    private final ScheduledExecutorService scheduler =
            Executors.newScheduledThreadPool(
                    2,
                    task -> {
                        Thread thread = new Thread(
                                task,
                                "course-index-heartbeat"
                        );
                        thread.setDaemon(true);
                        return thread;
                    }
            );

    public CourseIndexLease(
            CourseIndexLockService lockService) {
        this.lockService = lockService;
    }

    /**
     * 尝试获取课程锁，并启动心跳。
     *
     * 获取失败返回 null。
     */
    public Handle tryOpen(
            Long courseId,
            String lockType) {

        String token = lockService.tryAcquire(
                courseId,
                lockType
        );

        if (token == null) {
            return null;
        }

        Handle handle = new Handle(
                courseId,
                token
        );

        handle.startHeartbeat();

        return handle;
    }

    public final class Handle implements AutoCloseable {

        private final Long courseId;
        private final String token;

        private final AtomicBoolean lost =
                new AtomicBoolean(false);

        private final AtomicBoolean closed =
                new AtomicBoolean(false);

        private ScheduledFuture<?> heartbeat;

        private Handle(
                Long courseId,
                String token) {

            this.courseId = courseId;
            this.token = token;
        }

        private void startHeartbeat() {

            heartbeat = scheduler.scheduleAtFixedRate(
                    () -> {
                        if (closed.get() || lost.get()) {
                            return;
                        }

                        try {
                            boolean renewed =
                                    lockService.renew(
                                            courseId,
                                            token
                                    );

                            if (!renewed) {
                                lost.set(true);
                            }

                        } catch (Exception e) {
                            lost.set(true);
                        }
                    },
                    30,
                    30,
                    TimeUnit.SECONDS
            );
        }

        public Long getCourseId() {
            return courseId;
        }

        public String getToken() {
            return token;
        }

        public boolean isValid() {

            if (closed.get() || lost.get()) {
                return false;
            }

            try {
                boolean owned = lockService.isOwned(
                        courseId,
                        token
                );

                if (!owned) {
                    lost.set(true);
                }

                return owned;

            } catch (Exception e) {
                lost.set(true);
                return false;
            }
        }

        /**
         * 关键操作前调用。
         * 锁失效则立即阻止继续操作。
         */
        public void assertValid() {

            if (!isValid()) {
                throw new IllegalStateException(
                        "课程索引锁已失效，禁止继续操作"
                );
            }
        }

        @Override
        public void close() {

            if (!closed.compareAndSet(
                    false, true)) {
                return;
            }

            if (heartbeat != null) {
                heartbeat.cancel(false);
            }

            // 根据 token 释放。
            // 不会释放其他 Worker 后来取得的锁。
            lockService.release(
                    courseId,
                    token
            );
        }
    }

    @PreDestroy
    public void shutdown() {
        scheduler.shutdownNow();
    }
}
