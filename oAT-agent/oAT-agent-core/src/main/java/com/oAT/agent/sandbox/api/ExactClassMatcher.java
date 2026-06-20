package com.oAT.agent.sandbox.api;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class ExactClassMatcher implements ClassMatcher {
    private final Set<String> classNames;

    public ExactClassMatcher(String... classNames) {
        this.classNames = classNames == null ? new HashSet<String>() : new HashSet<String>(Arrays.asList(classNames));
    }

    @Override
    public boolean matches(ClassLoader loader, String className) {
        return classNames.contains(className);
    }
}
