package com.edu.ai.chat.service;

import com.edu.ai.common.BusinessException;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;

@Component
public class ChatStreamRegistry {

    private final Map<Long, RunningStream> streams =
            new ConcurrentHashMap<>();

    // 注册正在生成的会话
    public synchronized RunningStream register(Long sessionId) {

        RunningStream stream = new RunningStream();

        if (streams.putIfAbsent(sessionId, stream) != null) {
            throw new BusinessException(
                    409, "当前会话正在生成回答，请稍后再试");
        }

        return stream;
    }

    // 查询会话是否正在生成
    public boolean isRunning(Long sessionId) {
        return streams.containsKey(sessionId);
    }

    // 删除会话时，避免检查和删除之间被新任务抢占
    public synchronized void runIfIdle(
            Long sessionId,
            Runnable action) {

        if (isRunning(sessionId)) {
            throw new BusinessException(
                    409, "AI正在回答，请先停止生成");
        }

        action.run();
    }

    // 停止正在生成的任务
    public boolean cancel(Long sessionId) {

        RunningStream stream = streams.get(sessionId);

        if (stream == null) {
            return false;
        }

        stream.cancel();

        // 尚未开始的任务已取消，无需等待线程执行
        if (!stream.hasStarted()) {
            streams.remove(sessionId, stream);
        }

        return true;
    }

    // 只移除当前任务，避免误删后续任务
    public void release(
            Long sessionId,
            RunningStream stream) {

        streams.remove(sessionId, stream);
    }

    public static class RunningStream {

        private final AtomicBoolean cancelled =
                new AtomicBoolean(false);

        private volatile boolean successful = false;

        private volatile boolean started = false;

        private volatile Future<?> future;
        private volatile InputStream upstream;

        public boolean isCancelled() {
            return cancelled.get();
        }

        public boolean isSuccessful() {
            return successful;
        }

        public void markSuccessful() {
            successful = true;
        }

        public void markStarted() {
            started = true;
        }

        public boolean hasStarted() {
            return started;
        }

        // 绑定任务线程
        public synchronized void setFuture(Future<?> future) {
            this.future = future;

            if (cancelled.get()) {
                future.cancel(true);
            }
        }

        // 绑定 Python 返回的响应流
        public synchronized void setUpstream(InputStream upstream) {
            this.upstream = upstream;

            if (cancelled.get()) {
                closeQuietly(upstream);
            }
        }

        public void cancel() {

            if (!cancelled.compareAndSet(false, true)) {
                return;
            }

            InputStream input;
            Future<?> runningTask;

            synchronized (this) {
                input = upstream;
                runningTask = future;
            }

            // 关闭 Python 上游流
            closeQuietly(input);

            // 中断等待网络响应的 Java 线程
            if (runningTask != null) {
                runningTask.cancel(true);
            }
        }

        private static void closeQuietly(InputStream input) {
            if (input == null) {
                return;
            }

            try {
                input.close();
            } catch (IOException ignored) {
            }
        }
    }
}
