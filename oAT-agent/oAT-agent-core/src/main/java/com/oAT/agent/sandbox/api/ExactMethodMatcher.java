package com.oAT.agent.sandbox.api;

public class ExactMethodMatcher implements MethodMatcher {
    private final String methodName;
    private final String descriptor;

    public ExactMethodMatcher(String methodName, String descriptor) {
        this.methodName = methodName;
        this.descriptor = descriptor;
    }

    @Override
    public boolean matches(String methodName, String descriptor) {
        if (!this.methodName.equals(methodName)) {
            return false;
        }
        return this.descriptor == null || this.descriptor.equals(descriptor);
    }
}
