package com.oAT.agent.sandbox.api;

import com.oAT.agent.common.WildcardMatcher;

public class WildcardClassMatcher implements ClassMatcher {
    private final WildcardMatcher includes;
    private final WildcardMatcher excludes;

    public WildcardClassMatcher(String includeExpression, String excludeExpression) {
        this.includes = new WildcardMatcher(includeExpression == null ? "" : includeExpression);
        this.excludes = new WildcardMatcher(excludeExpression == null ? "" : excludeExpression);
    }

    @Override
    public boolean matches(ClassLoader loader, String className) {
        if (className == null || className.startsWith("com.oAT.agent.")) {
            return false;
        }
        return includes.matches(className) && !excludes.matches(className);
    }
}
