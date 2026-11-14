package com.edu.ai.chat.service;

import com.edu.ai.common.BusinessException;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicReference;

@Component
public class ChatStreamRegistry {

    private final Map<Long, RunningStream> streams =
            new ConcurrentHashMap<>();

    /**
     * 流式任务状态。
     */
    public enum StreamState {
        QUEUED,
        RUNNING,
        COMPLETING,
        SUCCEEDED,
        CANCELLED,
        FAILED
    }

    /**
     * 注册一个会话任务。
     */
    public synchronized RunningStream register(Long sessionId) {

        if (sessionId == null) {
            throw new BusinessException(
                    400, "会话ID不能为空");
        }

        RunningStream stream = new RunningStream();

        if (streams.putIfAbsent(sessionId, stream) != null) {
            throw new BusinessException(
                    409, "当前会话正在生成回答，请稍后再试");
        }

        return stream;
    }

    /**
     * 查询会话是否正在执行任务。
     */
    public boolean isRunning(Long sessionId) {

        RunningStream stream = streams.get(sessionId);

        if (stream == null) {
            return false;
        }

        StreamState state = stream.getState();

        return state == StreamState.QUEUED
                || state == StreamState.RUNNING
                || state == StreamState.COMPLETING;
    }

    /**
     * 查询指定会话当前任务状态。
     */
    public StreamState getState(Long sessionId) {

        RunningStream stream = streams.get(sessionId);

        return stream == null
                ? null
                : stream.getState();
    }

    /**
     * 删除会话前检查是否有正在执行的任务。
     */
    public synchronized void runIfIdle(
            Long sessionId,
            Runnable action) {

        if (isRunning(sessionId)) {
            throw new BusinessException(
                    409, "AI正在回答，请先停止生成");
        }

        action.run();
    }

    /**
     * 取消当前会话的流式任务。
     *
     * 只有 QUEUED 和 RUNNING 状态允许取消。
     * COMPLETING 之后不允许再取消。
     */
    public boolean cancel(Long sessionId) {

        RunningStream stream = streams.get(sessionId);

        if (stream == null) {
            return false;
        }

        boolean cancelled = stream.cancel();

        if (cancelled) {
            // 尚未启动的任务无需等待工作线程释放
            if (stream.getState() == StreamState.CANCELLED
                    && !stream.hasStarted()) {

                streams.remove(sessionId, stream);
            }
        }

        return cancelled;
    }

    /**
     * 只释放当前任务，避免旧任务误删新任务。
     */
    public void release(
            Long sessionId,
            RunningStream stream) {

        streams.remove(sessionId, stream);
    }

    public static class RunningStream {

        private final AtomicReference<StreamState> state =
                new AtomicReference<>(StreamState.QUEUED);

        private volatile boolean started = false;

        private volatile Future<?> future;

        private volatile InputStream upstream;

        public StreamState getState() {
            return state.get();
        }

        public boolean isCancelled() {
            return state.get() == StreamState.CANCELLED;
        }

        public boolean isSuccessful() {
            return state.get() == StreamState.SUCCEEDED;
        }

        public boolean hasStarted() {
            return started;
        }

        /**
         * 工作线程开始执行。
         */
        public boolean markStarted() {

            boolean changed = state.compareAndSet(
                    StreamState.QUEUED,
                    StreamState.RUNNING
            );

            if (changed) {
                started = true;
            }

            return changed;
        }

        /**
         * 绑定 Future。
         *
         * 处理提交任务后、绑定 Future 前发生取消的竞态。
         */
        public synchronized void setFuture(Future<?> future) {

            this.future = future;

            if (isCancelled()) {
                future.cancel(true);
            }
        }

        /**
         * 绑定 Python 上游响应流。
         *
         * 如果绑定时任务已经取消，立即关闭流。
         */
        public synchronized void setUpstream(InputStream upstream) {

            this.upstream = upstream;

            if (isCancelled()) {
                closeQuietly(upstream);
            }
        }

        /**
         * Python 已经正常完成。
         *
         * 通过 CAS 确保取消与完成只有一个能够获胜。
         */
        public boolean beginCompletion() {

            return state.compareAndSet(
                    StreamState.RUNNING,
                    StreamState.COMPLETING
            );
        }

        /**
         * 数据库持久化成功。
         */
        public boolean markSuccessful() {

            return state.compareAndSet(
                    StreamState.COMPLETING,
                    StreamState.SUCCEEDED
            );
        }

        /**
         * 标记任务失败。
         *
         * 已取消或已成功的任务不再改变状态。
         */
        public boolean markFailed() {

            while (true) {

                StreamState current = state.get();

                if (current == StreamState.CANCELLED
                        || current == StreamState.SUCCEEDED
                        || current == StreamState.FAILED) {

                    return false;
                }

                if (state.compareAndSet(
                        current,
                        StreamState.FAILED)) {

                    return true;
                }
            }
        }

        /**
         * 取消任务。
         */
        public boolean cancel() {

            while (true) {

                StreamState current = state.get();

                if (current != StreamState.QUEUED
                        && current != StreamState.RUNNING) {

                    return false;
                }

                if (state.compareAndSet(
                        current,
                        StreamState.CANCELLED)) {

                    break;
                }
            }

            InputStream input;
            Future<?> runningTask;

            synchronized (this) {
                input = upstream;
                runningTask = future;
            }

            // 关闭 Python 上游流
            closeQuietly(input);

            // 中断 Java 工作线程
            if (runningTask != null) {
                runningTask.cancel(true);
            }

            return true;
        }

        private static void closeQuietly(InputStream input) {

            if (input == null) {
                return;
            }

            try {
                input.close();
            } catch (IOException ignored) {
                // 取消过程中的关闭异常不影响状态
            }
        }
    }
}
