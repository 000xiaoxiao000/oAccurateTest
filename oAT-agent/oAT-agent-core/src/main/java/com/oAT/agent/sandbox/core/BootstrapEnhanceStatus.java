package com.oAT.agent.sandbox.core;

public class BootstrapEnhanceStatus {
    private final String moduleId;
    private final String className;
    private final String methodName;
    private final String descriptor;
    private volatile String state;
    private volatile String errorMessage;
    private volatile long updatedAt;

    public BootstrapEnhanceStatus(String moduleId, String className, String methodName, String descriptor) {
        this.moduleId = moduleId;
        this.className = className;
        this.methodName = methodName;
        this.descriptor = descriptor;
        this.state = "REGISTERED";
        this.updatedAt = System.currentTimeMillis();
    }

    public void mark(String state, String errorMessage) {
        this.state = state;
        this.errorMessage = errorMessage;
        this.updatedAt = System.currentTimeMillis();
    }

    public String moduleId() {
        return moduleId;
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

    public String state() {
        return state;
    }

    public String errorMessage() {
        return errorMessage;
    }

    public long updatedAt() {
        return updatedAt;
    }
}
