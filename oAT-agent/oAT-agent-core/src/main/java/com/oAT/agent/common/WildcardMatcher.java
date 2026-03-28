package com.oAT.agent.common;

import java.util.regex.Pattern;

/**
 * 通配符
 */
public class WildcardMatcher {
    private final Pattern pattern;
    private final boolean alwaysFalse;

    /**
     * Creates a new matcher with the given expression.
     *
     * @param expression wildcard expressions
     */
    public WildcardMatcher(final String expression) {
        this(expression, false);
    }

    /**
     * Creates a new matcher with the given expression.
     *
     * @param expression wildcard expressions
     * @param strict     if true, exact match; if false, contains match
     */
    public WildcardMatcher(final String expression, boolean strict) {
        if (expression == null || expression.trim().isEmpty()) {
            pattern = null;
            alwaysFalse = true;
            return;
        }
        alwaysFalse = false;
        final String[] splits = expression.split("&");
        final StringBuilder regex = new StringBuilder(expression.length() * 2);
        boolean next = false;
        for (final String split : splits) {
            if (next) {
                regex.append('|');
            }
            regex.append('(').append(toRegex(split, strict)).append(')');
            next = true;
        }
        pattern = Pattern.compile(regex.toString());
    }

    private static CharSequence toRegex(final String expression, boolean strict) {
        final StringBuilder regex = new StringBuilder(expression.length() * 2);
        if (!strict) {
            regex.append(".*"); // 前缀
        }
        for (final char ch : expression.toCharArray()) {
            switch (ch) {
                case '?':
                    regex.append(".?");
                    break;
                case '*':
                    regex.append(".*");
                    break;
                default:
                    regex.append(Pattern.quote(String.valueOf(ch)));
                    break;
            }
        }
        if (!strict) {
            regex.append(".*"); // 后缀
        }
        return regex;
    }

    /**
     * Matches the given string against the expressions of this matcher.
     *
     * @param s string to test
     * @return <code>true</code>, if the expression matches
     */
    public boolean matches(final String s) {
        if (alwaysFalse) {
            return false;
        }
        return pattern.matcher(s).matches();
    }

}
