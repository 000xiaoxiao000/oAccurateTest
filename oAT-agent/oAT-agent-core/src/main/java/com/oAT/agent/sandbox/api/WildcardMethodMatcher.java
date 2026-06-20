package com.oAT.agent.sandbox.api;

import com.oAT.agent.common.WildcardMatcher;

public class WildcardMethodMatcher implements MethodMatcher {
    private final WildcardMatcher includes;
    private final WildcardMatcher excludes;

    public WildcardMethodMatcher(String includeExpression, String excludeExpression) {
        this.includes = new WildcardMatcher(includeExpression == null || includeExpression.trim().isEmpty()
                ? "*" : includeExpression, true);
        this.excludes = new WildcardMatcher(excludeExpression == null ? "" : excludeExpression, true);
    }

    @Override
    public boolean matches(String methodName, String descriptor) {
        if (methodName == null || methodName.startsWith("$")) {
            return false;
        }
        String signature = methodName + "(";
        return (includes.matches(methodName) || includes.matches(signature)) && !excludes.matches(methodName);
    }
}
