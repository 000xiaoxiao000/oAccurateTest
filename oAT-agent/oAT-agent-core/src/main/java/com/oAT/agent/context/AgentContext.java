package com.oAT.agent.context;

import com.oAT.agent.jacoco.CoverageCollector;
import com.oAT.agent.trace.TraceSession;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * 统一上下文管理，解决跨线程透传问题
 */
public class AgentContext {
    private static final ThreadLocal<TraceSession> TRACE_SESSION = new InheritableThreadLocal<>();
    private static final ThreadLocal<AtomicInteger> ACTIVE_ASYNC_TASK_COUNT = new InheritableThreadLocal<>();
    private static final ThreadLocal<CoverageCollector> COVERAGE_COLLECTOR = new InheritableThreadLocal<>();

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

    public static void registerAsyncTask() {
        AtomicInteger count = ACTIVE_ASYNC_TASK_COUNT.get();
        if (count != null) {
            count.incrementAndGet();
        }
    }

    public static int completeAsyncTask() {
        AtomicInteger count = ACTIVE_ASYNC_TASK_COUNT.get();
        if (count == null) {
            return -1;
        }
        return count.decrementAndGet();
    }

    public static boolean hasPendingAsyncTasks() {
        AtomicInteger count = ACTIVE_ASYNC_TASK_COUNT.get();
        return count != null && count.get() > 0;
    }

    /**
     * 获取当前上下文快照
     */
    public static ContextSnapshot capture() {
        return new ContextSnapshot(TRACE_SESSION.get(), ACTIVE_ASYNC_TASK_COUNT.get(), COVERAGE_COLLECTOR.get());
    }

    public static class ContextSnapshot {
        private final TraceSession traceSession;
        private final AtomicInteger activeAsyncTaskCount;
        private final CoverageCollector coverageCollector;

        public ContextSnapshot(TraceSession traceSession, AtomicInteger activeAsyncTaskCount,
                               CoverageCollector coverageCollector) {
            this.traceSession = traceSession;
            this.activeAsyncTaskCount = activeAsyncTaskCount;
            this.coverageCollector = coverageCollector;
        }

        /**
         * 恢复上下文到当前线程
         * @return 用于还原现场的 Scope
         */
        public Scope restore() {
            TraceSession previousTrace = TRACE_SESSION.get();
            AtomicInteger previousAsyncTaskCount = ACTIVE_ASYNC_TASK_COUNT.get();
            CoverageCollector previousCoverageCollector = COVERAGE_COLLECTOR.get();

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

            return new Scope(previousTrace, previousAsyncTaskCount, previousCoverageCollector, true);
        }

        public Scope restoreForAsyncExecution() {
            TraceSession previousTrace = TRACE_SESSION.get();
            AtomicInteger previousAsyncTaskCount = ACTIVE_ASYNC_TASK_COUNT.get();
            CoverageCollector previousCoverageCollector = COVERAGE_COLLECTOR.get();
            boolean shouldRestorePrevious = previousTrace == null || previousTrace == this.traceSession;

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

            return new Scope(previousTrace, previousAsyncTaskCount, previousCoverageCollector, shouldRestorePrevious);
        }
    }

    public static class Scope implements AutoCloseable {
        private final TraceSession previousTrace;
        private final AtomicInteger previousAsyncTaskCount;
        private final CoverageCollector previousCoverageCollector;
        private final boolean restorePrevious;

        public Scope(TraceSession previousTrace, AtomicInteger previousAsyncTaskCount,
                     CoverageCollector previousCoverageCollector, boolean restorePrevious) {
            this.previousTrace = previousTrace;
            this.previousAsyncTaskCount = previousAsyncTaskCount;
            this.previousCoverageCollector = previousCoverageCollector;
            this.restorePrevious = restorePrevious;
        }

        @Override
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
        }
    }

    /**
     * 包装 Runnable，实现上下文透传
     */
    public static Runnable wrap(Runnable runnable) {
        if (runnable == null) return null;
        if (runnable instanceof ContextAwareRunnable) return runnable;
        ContextSnapshot snapshot = capture();
        return new ContextAwareRunnable(runnable, snapshot);
    }

    /**
     * 包装 Callable，实现上下文透传
     */
    public static <V> java.util.concurrent.Callable<V> wrap(java.util.concurrent.Callable<V> callable) {
        if (callable == null) return null;
        if (callable instanceof ContextAwareCallable) return callable;
        ContextSnapshot snapshot = capture();
        return new ContextAwareCallable<>(callable, snapshot);
    }

    private static void completeAsyncTaskAndFinalizeIfNeeded() {
        if (completeAsyncTask() == 0) {
            com.oAT.agent.collect.HttpServletCollect.tryFinalizeDeferredNode();
        }
    }

    private static class ContextAwareRunnable implements Runnable {
        private final Runnable delegate;
        private final ContextSnapshot snapshot;

        public ContextAwareRunnable(Runnable delegate, ContextSnapshot snapshot) {
            this.delegate = delegate;
            this.snapshot = snapshot;
        }

        @Override
        public void run() {
            try (Scope ignored = snapshot.restoreForAsyncExecution()) {
                try {
                    delegate.run();
                } finally {
                    completeAsyncTaskAndFinalizeIfNeeded();
                }
            }
        }
    }

    private static class ContextAwareCallable<V> implements java.util.concurrent.Callable<V> {
        private final java.util.concurrent.Callable<V> delegate;
        private final ContextSnapshot snapshot;

        public ContextAwareCallable(java.util.concurrent.Callable<V> delegate, ContextSnapshot snapshot) {
            this.delegate = delegate;
            this.snapshot = snapshot;
        }

        @Override
        public V call() throws Exception {
            try (Scope ignored = snapshot.restoreForAsyncExecution()) {
                try {
                    return delegate.call();
                } finally {
                    completeAsyncTaskAndFinalizeIfNeeded();
                }
            }
        }
    }
}
