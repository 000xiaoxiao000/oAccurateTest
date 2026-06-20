package com.oAT.agent.bootstrap;

public final class OatAsyncBridge {
    private static volatile Object bridge;
    private static volatile boolean enabled = true;

    private OatAsyncBridge() {
    }

    public static void init(Object contextBridge) {
        bridge = contextBridge;
        enabled = true;
    }

    public static void freeze() {
        enabled = false;
    }

    public static Runnable wrap(Runnable runnable) {
        if (!enabled || runnable == null) {
            return runnable;
        }
        Object current = bridge;
        if (current == null) {
            return runnable;
        }
        try {
            java.lang.reflect.Method method = current.getClass().getMethod("wrap", new Class[]{Runnable.class});
            Runnable wrapped = (Runnable) method.invoke(current, new Object[]{runnable});
            return wrapped == null ? runnable : wrapped;
        } catch (Throwable ignored) {
            return runnable;
        }
    }

    public static java.util.concurrent.Callable wrap(java.util.concurrent.Callable callable) {
        if (!enabled || callable == null) {
            return callable;
        }
        Object current = bridge;
        if (current == null) {
            return callable;
        }
        try {
            java.lang.reflect.Method method = current.getClass().getMethod("wrap",
                    new Class[]{java.util.concurrent.Callable.class});
            java.util.concurrent.Callable wrapped =
                    (java.util.concurrent.Callable) method.invoke(current, new Object[]{callable});
            return wrapped == null ? callable : wrapped;
        } catch (Throwable ignored) {
            return callable;
        }
    }

    public static java.util.Collection wrap(java.util.Collection tasks) {
        if (!enabled || tasks == null) {
            return tasks;
        }
        Object current = bridge;
        if (current == null) {
            return tasks;
        }
        try {
            java.lang.reflect.Method method = current.getClass().getMethod("wrap", new Class[]{java.util.Collection.class});
            java.util.Collection wrapped = (java.util.Collection) method.invoke(current, new Object[]{tasks});
            return wrapped == null ? tasks : wrapped;
        } catch (Throwable ignored) {
            return tasks;
        }
    }

    public static void release(Object task) {
        if (!enabled || task == null) {
            return;
        }
        Object current = bridge;
        if (current == null) {
            return;
        }
        try {
            java.lang.reflect.Method method = current.getClass().getMethod("release", new Class[]{Object.class});
            method.invoke(current, new Object[]{task});
        } catch (Throwable ignored) {
        }
    }
}
