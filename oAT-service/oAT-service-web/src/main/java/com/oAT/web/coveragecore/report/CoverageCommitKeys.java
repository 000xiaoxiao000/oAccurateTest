package com.oAT.web.coveragecore.report;

import org.springframework.util.StringUtils;

import java.util.Locale;

public final class CoverageCommitKeys {
    private CoverageCommitKeys() {
    }

    public static String normalize(String commitId) {
        return StringUtils.hasText(commitId) ? commitId.trim().toLowerCase(Locale.ROOT) : null;
    }

    public static String shortCommit(String commitId) {
        if (!StringUtils.hasText(commitId)) {
            return "-";
        }
        String trimmed = commitId.trim();
        return trimmed.length() <= 8 ? trimmed : trimmed.substring(0, 8);
    }
}
