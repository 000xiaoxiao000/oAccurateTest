package com.oAT.agent.sandbox.core;

public class BootstrapEnhanceDefinition {
    private final String moduleId;
    private final String className;
    private final String methodName;
    private final String descriptor;
    private final BootstrapMethodEnhancer enhancer;
    private final int minJavaVersion;
    private final int maxJavaVersion;

    public BootstrapEnhanceDefinition(String moduleId,
                                      String className,
                                      String methodName,
                                      String descriptor,
                                      BootstrapMethodEnhancer enhancer) {
        this(moduleId, className, methodName, descriptor, enhancer, 6, 999);
    }

    public BootstrapEnhanceDefinition(String moduleId,
                                      String className,
                                      String methodName,
                                      String descriptor,
                                      BootstrapMethodEnhancer enhancer,
                                      int minJavaVersion,
                                      int maxJavaVersion) {
        this.moduleId = moduleId;
        this.className = className;
        this.methodName = methodName;
        this.descriptor = descriptor;
        this.enhancer = enhancer;
        this.minJavaVersion = minJavaVersion;
        this.maxJavaVersion = maxJavaVersion;
    }

    public String moduleId() {
        return moduleId;
    }

    public String className() {
        return className;
    }

    public String internalClassName() {
        return className.replace('.', '/');
    }

    public String methodName() {
        return methodName;
    }

    public String descriptor() {
        return descriptor;
    }

    public BootstrapMethodEnhancer enhancer() {
        return enhancer;
    }

    public boolean supportsJavaVersion(int javaVersion) {
        return javaVersion >= minJavaVersion && javaVersion <= maxJavaVersion;
    }
}
