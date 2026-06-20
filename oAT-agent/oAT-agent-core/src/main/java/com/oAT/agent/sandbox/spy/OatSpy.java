package com.oAT.agent.sandbox.spy;

public final class OatSpy {
    private static volatile Object handler;
    private static volatile java.lang.reflect.Method beforeMethod;
    private static volatile java.lang.reflect.Method returnMethod;
    private static volatile java.lang.reflect.Method throwsMethod;

    private OatSpy() {
    }

    public static void init(SpyHandler spyHandler) {
        handler = spyHandler;
        try {
            beforeMethod = spyHandler.getClass().getMethod("onBefore", String.class, long.class, ClassLoader.class,
                    String.class, String.class, String.class, Object.class, Object[].class);
            returnMethod = spyHandler.getClass().getMethod("onReturn", long.class, long.class, Object.class);
            throwsMethod = spyHandler.getClass().getMethod("onThrows", long.class, long.class, Throwable.class);
        } catch (Throwable t) {
            handler = null;
        }
    }

    public static void init(Object spyHandler) {
        handler = spyHandler;
        try {
            beforeMethod = spyHandler.getClass().getMethod("onBefore", String.class, long.class, ClassLoader.class,
                    String.class, String.class, String.class, Object.class, Object[].class);
            returnMethod = spyHandler.getClass().getMethod("onReturn", long.class, long.class, Object.class);
            throwsMethod = spyHandler.getClass().getMethod("onThrows", long.class, long.class, Throwable.class);
        } catch (Throwable t) {
            handler = null;
        }
    }

    public static long onBefore(String namespace,
                                long listenerId,
                                ClassLoader loader,
                                String className,
                                String methodName,
                                String descriptor,
                                Object target,
                                Object[] args) {
        Object current = handler;
        if (current == null) {
            return 0L;
        }
        try {
            Object result = beforeMethod.invoke(current, namespace, listenerId, loader, className, methodName,
                    descriptor, target, args);
            return result instanceof Long ? ((Long) result).longValue() : 0L;
        } catch (Throwable ignored) {
            return 0L;
        }
    }

    public static Object onReturn(long listenerId, long invokeId, Object returnValue) {
        Object current = handler;
        if (current != null) {
            try {
                return returnMethod.invoke(current, listenerId, invokeId, returnValue);
            } catch (Throwable ignored) {
                return returnValue;
            }
        }
        return returnValue;
    }

    public static void onThrows(long listenerId, long invokeId, Throwable throwable) {
        Object current = handler;
        if (current != null) {
            try {
                throwsMethod.invoke(current, listenerId, invokeId, throwable);
            } catch (Throwable ignored) {
            }
        }
    }

    public interface SpyHandler {
        long onBefore(String namespace,
                      long listenerId,
                      ClassLoader loader,
                      String className,
                      String methodName,
                      String descriptor,
                      Object target,
                      Object[] args);

        Object onReturn(long listenerId, long invokeId, Object returnValue);

        void onThrows(long listenerId, long invokeId, Throwable throwable);
    }
}
