package com.oAT.agent.context;

import com.oAT.agent.jacoco.StackSession;
import com.oAT.agent.trace.TraceSession;

/**
 * 统一上下文管理，解决跨线程透传问题
 */
public class AgentContext {
    private static final ThreadLocal<StackSession> STACK_SESSION = new InheritableThreadLocal<>();
    private static final ThreadLocal<TraceSession> TRACE_SESSION = new InheritableThreadLocal<>();

    public static StackSession getStackSession() {
        return STACK_SESSION.get();
    }

    public static void setStackSession(StackSession session) {
        STACK_SESSION.set(session);
    }

    public static void removeStackSession() {
        STACK_SESSION.remove();
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

    /**
     * 获取当前上下文快照
     */
    public static ContextSnapshot capture() {
        return new ContextSnapshot(STACK_SESSION.get(), TRACE_SESSION.get());
    }

    public static class ContextSnapshot {
        private final StackSession stackSession;
        private final TraceSession traceSession;

        public ContextSnapshot(StackSession stackSession, TraceSession traceSession) {
            this.stackSession = stackSession;
            this.traceSession = traceSession;
        }

        /**
         * 恢复上下文到当前线程
         * @return 用于还原现场的 Scope
         */
        public Scope restore() {
            StackSession previousStack = STACK_SESSION.get();
            TraceSession previousTrace = TRACE_SESSION.get();

            if (this.stackSession != null) {
                STACK_SESSION.set(this.stackSession);
            }
            if (this.traceSession != null) {
                TRACE_SESSION.set(this.traceSession);
            }

            return new Scope(previousStack, previousTrace);
        }
    }

    public static class Scope implements AutoCloseable {
        private final StackSession previousStack;
        private final TraceSession previousTrace;

        public Scope(StackSession previousStack, TraceSession previousTrace) {
            this.previousStack = previousStack;
            this.previousTrace = previousTrace;
        }

        @Override
        public void close() {
            if (previousStack != null) {
                STACK_SESSION.set(previousStack);
            } else {
                STACK_SESSION.remove();
            }

            if (previousTrace != null) {
                TRACE_SESSION.set(previousTrace);
            } else {
                TRACE_SESSION.remove();
            }
        }
    }

    /**
     * 包装 Runnable，实现上下文透传
     */
    public static Runnable wrap(Runnable runnable) {
        if (runnable == null) return null;
        // 避免多重包装
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

    private static class ContextAwareRunnable implements Runnable {
        private final Runnable delegate;
        private final ContextSnapshot snapshot;

        public ContextAwareRunnable(Runnable delegate, ContextSnapshot snapshot) {
            this.delegate = delegate;
            this.snapshot = snapshot;
        }

        @Override
        public void run() {
            try (Scope ignored = snapshot.restore()) {
                delegate.run();
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
            try (Scope ignored = snapshot.restore()) {
                return delegate.call();
            }
        }
    }
}

