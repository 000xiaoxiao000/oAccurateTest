package com.oAT.agent.sandbox.api;

public interface MethodMatcher {
    boolean matches(String methodName, String descriptor);
}
