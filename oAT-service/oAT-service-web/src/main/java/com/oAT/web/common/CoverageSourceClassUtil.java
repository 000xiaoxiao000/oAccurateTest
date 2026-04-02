package com.oAT.web.common;

import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class CoverageSourceClassUtil {

    private CoverageSourceClassUtil() {
    }

    public static List<String> buildSourceClassCandidates(String className) {
        if (!StringUtils.hasText(className)) {
            return Collections.emptyList();
        }

        String normalizedClassName = className.replace('$', '.');
        String[] segments = normalizedClassName.split("\\.");
        if (segments.length == 0) {
            return Collections.emptyList();
        }

        int firstTypeSegment = findFirstTypeSegmentIndex(segments);
        List<String> candidates = new ArrayList<>();
        int endIndex = firstTypeSegment >= 0 ? firstTypeSegment : 0;
        for (int idx = segments.length - 1; idx >= endIndex; idx--) {
            String candidate = String.join(".", java.util.Arrays.copyOfRange(segments, 0, idx + 1));
            if (StringUtils.hasText(candidate) && !candidates.contains(candidate)) {
                candidates.add(candidate);
            }
        }
        return candidates;
    }

    public static List<String> buildSourcePathCandidates(String className) {
        List<String> candidates = new ArrayList<>();
        for (String candidateClassName : buildSourceClassCandidates(className)) {
            candidates.add(candidateClassName.replace('.', '/') + ".java");
        }
        return candidates;
    }

    public static String resolveSourceOwnerClassName(String className) {
        List<String> candidates = buildSourceClassCandidates(className);
        if (candidates.isEmpty()) {
            return className;
        }
        return candidates.get(candidates.size() - 1);
    }

    public static String toTreeDisplayName(String segment, String nodeType) {
        if (!StringUtils.hasText(segment)) {
            return segment;
        }
        if (!"class".equals(nodeType)) {
            return segment;
        }
        if (isAllDigits(segment)) {
            return "匿名类#" + segment;
        }

        int splitIndex = 0;
        while (splitIndex < segment.length() && Character.isDigit(segment.charAt(splitIndex))) {
            splitIndex++;
        }
        if (splitIndex > 0 && splitIndex < segment.length()) {
            return "局部类 " + segment.substring(splitIndex) + " (#" + segment.substring(0, splitIndex) + ")";
        }
        return segment;
    }

    public static int findFirstTypeSegmentIndex(String[] segments) {
        for (int i = 0; i < segments.length; i++) {
            if (isLikelyTypeSegment(segments[i])) {
                return i;
            }
        }
        return -1;
    }

    public static boolean isLikelyTypeSegment(String segment) {
        if (!StringUtils.hasText(segment)) {
            return false;
        }
        char firstChar = segment.charAt(0);
        return Character.isUpperCase(firstChar) || Character.isDigit(firstChar);
    }

    private static boolean isAllDigits(String segment) {
        for (int i = 0; i < segment.length(); i++) {
            if (!Character.isDigit(segment.charAt(i))) {
                return false;
            }
        }
        return segment.length() > 0;
    }
}
