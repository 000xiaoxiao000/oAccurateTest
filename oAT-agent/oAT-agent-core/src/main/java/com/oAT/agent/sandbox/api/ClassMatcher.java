package com.oAT.agent.sandbox.api;

public interface ClassMatcher {
    boolean matches(ClassLoader loader, String className);
}
