package com.oAT.agent.context;

import com.oAT.agent.jacoco.CoverageCollector;
import com.oAT.agent.common.logger.Log;
import com.oAT.agent.common.logger.LogFactory;
import com.oAT.agent.trace.TraceSession;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 统一上下文管理，解决跨线程透传问题
 */
public class AgentContext {
    private static final Log logger = LogFactory.getLog(AgentContext.class);

    private static final ThreadLocal<TraceSession> TRACE_SESSION = new InheritableThreadLocal<TraceSession>();
    private static final ThreadLocal<AtomicInteger> ACTIVE_ASYNC_TASK_COUNT = new InheritableThreadLocal<AtomicInteger>();
    private static final ThreadLocal<CoverageCollector> COVERAGE_COLLECTOR = new InheritableThreadLocal<CoverageCollector>();
    private static final ThreadLocal<AsyncCompletionListener> ASYNC_COMPLETION_LISTENER = new InheritableThreadLocal<AsyncCompletionListener>();

    public interface AsyncCompletionListener {
        void onAsyncComplete(TraceSession traceSession);
    }

    public static TraceSession getTraceSession() {
        return TRACE_SESSION.get();
    }

    public static void setTraceSession(TraceSession session) {
        TRACE_SESSION.set(session);
    }

    public static void removeTraceSession() {
        TRACE_SESSION.remove();
    }

    public static CoverageCollector getCoverageCollector() {
        return COVERAGE_COLLECTOR.get();
    }

    public static void setCoverageCollector(CoverageCollector coverageCollector) {
        if (coverageCollector == null) {
            COVERAGE_COLLECTOR.remove();
        } else {
            COVERAGE_COLLECTOR.set(coverageCollector);
        }
    }

    public static void removeCoverageCollector() {
        COVERAGE_COLLECTOR.remove();
    }

    public static AtomicInteger getActiveAsyncTaskCount() {
        return ACTIVE_ASYNC_TASK_COUNT.get();
    }

    public static void setActiveAsyncTaskCount(AtomicInteger count) {
        if (count == null) {
            ACTIVE_ASYNC_TASK_COUNT.remove();
        } else {
            ACTIVE_ASYNC_TASK_COUNT.set(count);
        }
    }

    public static void removeActiveAsyncTaskCount() {
        ACTIVE_ASYNC_TASK_COUNT.remove();
    }

    public static AsyncCompletionListener getAsyncCompletionListener() {
        return ASYNC_COMPLETION_LISTENER.get();
    }

    public static void setAsyncCompletionListener(AsyncCompletionListener listener) {
        if (listener == null) {
            ASYNC_COMPLETION_LISTENER.remove();
        } else {
            ASYNC_COMPLETION_LISTENER.set(listener);
        }
    }

    public static void removeAsyncCompletionListener() {
        ASYNC_COMPLETION_LISTENER.remove();
    }

    public static void registerAsyncTask() {
        AtomicInteger count = ACTIVE_ASYNC_TASK_COUNT.get();
        if (count != null) {
            int value = count.incrementAndGet();
            if (logger.isDebugEnabled()) {
                TraceSession traceSession = TRACE_SESSION.get();
                logger.debug("[AgentContext] register async task, pendingAsyncTasks=" + value
                        + ", traceId=" + (traceSession == null ? null : traceSession.getTraceId()));
            }
        }
    }

    public static int completeAsyncTask() {
        AtomicInteger count = ACTIVE_ASYNC_TASK_COUNT.get();
        if (count == null) {
            return -1;
        }
        int value = count.decrementAndGet();
        if (logger.isDebugEnabled()) {
            TraceSession traceSession = TRACE_SESSION.get();
            logger.debug("[AgentContext] complete async task, pendingAsyncTasks=" + value
                    + ", traceId=" + (traceSession == null ? null : traceSession.getTraceId()));
        }
        return value;
    }

    public static boolean hasPendingAsyncTasks() {
        AtomicInteger count = ACTIVE_ASYNC_TASK_COUNT.get();
        return count != null && count.get() > 0;
    }

    /**
     * 获取当前上下文快照
     */
    public static ContextSnapshot capture() {
        return new ContextSnapshot(TRACE_SESSION.get(), ACTIVE_ASYNC_TASK_COUNT.get(), COVERAGE_COLLECTOR.get(),
                ASYNC_COMPLETION_LISTENER.get());
    }

    public static class ContextSnapshot {
        private final TraceSession traceSession;
        private final AtomicInteger activeAsyncTaskCount;
        private final CoverageCollector coverageCollector;
        private final AsyncCompletionListener asyncCompletionListener;

        public ContextSnapshot(TraceSession traceSession, AtomicInteger activeAsyncTaskCount,
                               CoverageCollector coverageCollector,
                               AsyncCompletionListener asyncCompletionListener) {
            this.traceSession = traceSession;
            this.activeAsyncTaskCount = activeAsyncTaskCount;
            this.coverageCollector = coverageCollector;
            this.asyncCompletionListener = asyncCompletionListener;
        }

        public boolean isEmpty() {
            return traceSession == null && coverageCollector == null && asyncCompletionListener == null;
        }

        /**
         * 恢复上下文到当前线程
         * @return 用于还原现场的 Scope
         */
        public Scope restore() {
            TraceSession previousTrace = TRACE_SESSION.get();
            AtomicInteger previousAsyncTaskCount = ACTIVE_ASYNC_TASK_COUNT.get();
            CoverageCollector previousCoverageCollector = COVERAGE_COLLECTOR.get();
            AsyncCompletionListener previousAsyncCompletionListener = ASYNC_COMPLETION_LISTENER.get();

            if (this.traceSession != null) {
                TRACE_SESSION.set(this.traceSession);
            }
            if (this.activeAsyncTaskCount != null) {
                ACTIVE_ASYNC_TASK_COUNT.set(this.activeAsyncTaskCount);
            } else {
                ACTIVE_ASYNC_TASK_COUNT.remove();
            }
            if (this.coverageCollector != null) {
                COVERAGE_COLLECTOR.set(this.coverageCollector);
            } else {
                COVERAGE_COLLECTOR.remove();
            }
            if (this.asyncCompletionListener != null) {
                ASYNC_COMPLETION_LISTENER.set(this.asyncCompletionListener);
            } else {
                ASYNC_COMPLETION_LISTENER.remove();
            }

            return new Scope(previousTrace, previousAsyncTaskCount, previousCoverageCollector,
                    previousAsyncCompletionListener, true);
        }

        public Scope restoreForAsyncExecution() {
            TraceSession previousTrace = TRACE_SESSION.get();
            AtomicInteger previousAsyncTaskCount = ACTIVE_ASYNC_TASK_COUNT.get();
            CoverageCollector previousCoverageCollector = COVERAGE_COLLECTOR.get();
            AsyncCompletionListener previousAsyncCompletionListener = ASYNC_COMPLETION_LISTENER.get();

            if (this.traceSession != null) {
                TRACE_SESSION.set(this.traceSession);
            } else {
                TRACE_SESSION.remove();
            }
            if (this.activeAsyncTaskCount != null) {
                ACTIVE_ASYNC_TASK_COUNT.set(this.activeAsyncTaskCount);
            } else {
                ACTIVE_ASYNC_TASK_COUNT.remove();
            }
            if (this.coverageCollector != null) {
                COVERAGE_COLLECTOR.set(this.coverageCollector);
            } else {
                COVERAGE_COLLECTOR.remove();
            }
            if (this.asyncCompletionListener != null) {
                ASYNC_COMPLETION_LISTENER.set(this.asyncCompletionListener);
            } else {
                ASYNC_COMPLETION_LISTENER.remove();
            }

            return new Scope(previousTrace, previousAsyncTaskCount, previousCoverageCollector,
                    previousAsyncCompletionListener, true);
        }
    }

    public static class Scope {
        private final TraceSession previousTrace;
        private final AtomicInteger previousAsyncTaskCount;
        private final CoverageCollector previousCoverageCollector;
        private final AsyncCompletionListener previousAsyncCompletionListener;
        private final boolean restorePrevious;

        public Scope(TraceSession previousTrace, AtomicInteger previousAsyncTaskCount,
                     CoverageCollector previousCoverageCollector,
                     AsyncCompletionListener previousAsyncCompletionListener,
                     boolean restorePrevious) {
            this.previousTrace = previousTrace;
            this.previousAsyncTaskCount = previousAsyncTaskCount;
            this.previousCoverageCollector = previousCoverageCollector;
            this.previousAsyncCompletionListener = previousAsyncCompletionListener;
            this.restorePrevious = restorePrevious;
        }

        public void close() {
            if (!restorePrevious) {
                return;
            }
            if (previousTrace != null) {
                TRACE_SESSION.set(previousTrace);
            } else {
                TRACE_SESSION.remove();
            }

            if (previousAsyncTaskCount != null) {
                ACTIVE_ASYNC_TASK_COUNT.set(previousAsyncTaskCount);
            } else {
                ACTIVE_ASYNC_TASK_COUNT.remove();
            }

            if (previousCoverageCollector != null) {
                COVERAGE_COLLECTOR.set(previousCoverageCollector);
            } else {
                COVERAGE_COLLECTOR.remove();
            }

            if (previousAsyncCompletionListener != null) {
                ASYNC_COMPLETION_LISTENER.set(previousAsyncCompletionListener);
            } else {
                ASYNC_COMPLETION_LISTENER.remove();
            }
        }
    }

    /**
     * 包装 Runnable，实现上下文透传
     */
    public static Runnable wrap(Runnable runnable) {
        if (runnable == null) return null;
        if (runnable instanceof ContextAwareRunnable) return runnable;
        ContextSnapshot snapshot = capture();
        if (snapshot.isEmpty()) return runnable;
        registerAsyncTask();
        return new ContextAwareRunnable(runnable, snapshot);
    }

    /**
     * 包装 Callable，实现上下文透传
     */
    public static <V> java.util.concurrent.Callable<V> wrap(java.util.concurrent.Callable<V> callable) {
        if (callable == null) return null;
        if (callable instanceof ContextAwareCallable) return callable;
        ContextSnapshot snapshot = capture();
        if (snapshot.isEmpty()) return callable;
        registerAsyncTask();
        return new ContextAwareCallable<V>(callable, snapshot);
    }

    public static void cancelAsyncTask(Object task) {
        if (task instanceof AsyncTaskHandle) {
            ((AsyncTaskHandle) task).finishAsyncTask();
        }
    }

    private interface AsyncTaskHandle {
        void finishAsyncTask();
    }

    private static void completeAsyncTaskAndFinalizeIfNeeded() {
        int remaining = completeAsyncTask();
        if (remaining == 0) {
            AsyncCompletionListener listener = ASYNC_COMPLETION_LISTENER.get();
            if (logger.isDebugEnabled()) {
                TraceSession traceSession = TRACE_SESSION.get();
                logger.debug("[AgentContext] last async task completed, finalize deferred coverage, traceId="
                        + (traceSession == null ? null : traceSession.getTraceId()));
            }
            if (listener != null) {
                listener.onAsyncComplete(TRACE_SESSION.get());
            }
            com.oAT.agent.collect.HttpServletCollect.tryFinalizeDeferredNode();
            com.oAT.agent.sandbox.modules.http.HttpServletSandboxModule.tryFinalizeDeferredNode();
        }
    }

    private static class ContextAwareRunnable implements Runnable, AsyncTaskHandle {
        private final Runnable delegate;
        private final ContextSnapshot snapshot;
        private final AtomicBoolean finished = new AtomicBoolean(false);

        public ContextAwareRunnable(Runnable delegate, ContextSnapshot snapshot) {
            this.delegate = delegate;
            this.snapshot = snapshot;
        }

        @Override
        public void run() {
            Scope scope = snapshot.restoreForAsyncExecution();
            try {
                delegate.run();
            } finally {
                try {
                    finishAsyncTask();
                } finally {
                    scope.close();
                }
            }
        }

        public void finishAsyncTask() {
            if (!finished.compareAndSet(false, true)) {
                return;
            }
            Scope scope = snapshot.restoreForAsyncExecution();
            try {
                completeAsyncTaskAndFinalizeIfNeeded();
            } finally {
                scope.close();
            }
        }
    }

    private static class ContextAwareCallable<V> implements java.util.concurrent.Callable<V>, AsyncTaskHandle {
        private final java.util.concurrent.Callable<V> delegate;
        private final ContextSnapshot snapshot;
        private final AtomicBoolean finished = new AtomicBoolean(false);

        public ContextAwareCallable(java.util.concurrent.Callable<V> delegate, ContextSnapshot snapshot) {
            this.delegate = delegate;
            this.snapshot = snapshot;
        }

        @Override
        public V call() throws Exception {
            Scope scope = snapshot.restoreForAsyncExecution();
            try {
                return delegate.call();
            } finally {
                try {
                    finishAsyncTask();
                } finally {
                    scope.close();
                }
            }
        }

        public void finishAsyncTask() {
            if (!finished.compareAndSet(false, true)) {
                return;
            }
            Scope scope = snapshot.restoreForAsyncExecution();
            try {
                completeAsyncTaskAndFinalizeIfNeeded();
            } finally {
                scope.close();
            }
        }
    }
}
