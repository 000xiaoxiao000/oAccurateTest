package com.oAT.agent.sandbox.api;

public class SandboxEvent {
    private final EventType type;
    private final long invokeId;
    private final long listenerId;
    private final ClassLoader loader;
    private final String className;
    private final String methodName;
    private final String descriptor;
    private final Object target;
    private final Object[] args;
    private final Object returnValue;
    private final Throwable throwable;

    public SandboxEvent(EventType type, long invokeId) {
        this(type, invokeId, 0L, null, null, null, null, null, null, null, null);
    }

    public SandboxEvent(EventType type,
                        long invokeId,
                        long listenerId,
                        ClassLoader loader,
                        String className,
                        String methodName,
                        String descriptor,
                        Object target,
                        Object[] args,
                        Object returnValue,
                        Throwable throwable) {
        this.type = type;
        this.invokeId = invokeId;
        this.listenerId = listenerId;
        this.loader = loader;
        this.className = className;
        this.methodName = methodName;
        this.descriptor = descriptor;
        this.target = target;
        this.args = args;
        this.returnValue = returnValue;
        this.throwable = throwable;
    }

    public EventType type() {
        return type;
    }

    public long invokeId() {
        return invokeId;
    }

    public long listenerId() {
        return listenerId;
    }

    public ClassLoader loader() {
        return loader;
    }

    public String className() {
        return className;
    }

    public String methodName() {
        return methodName;
    }

    public String descriptor() {
        return descriptor;
    }

    public Object target() {
        return target;
    }

    public Object[] args() {
        return args;
    }

    public Object returnValue() {
        return returnValue;
    }

    public Throwable throwable() {
        return throwable;
    }
}
