package com.oAT.web.coveragecore.report;

public final class CoverageReportTypes {
    public static final int VERSION_FULL = 0;
    public static final int INCREMENTAL = 1;
    public static final int CURRENT_COMMIT = 2;

    private CoverageReportTypes() {
    }

    public static int normalize(Integer reportType) {
        return reportType == null ? VERSION_FULL : reportType;
    }

    public static boolean isIncremental(Integer reportType) {
        return normalize(reportType) == INCREMENTAL;
    }

    public static String displayName(Integer reportType) {
        int normalizedType = normalize(reportType);
        if (normalizedType == INCREMENTAL) {
            return "增量";
        }
        if (normalizedType == CURRENT_COMMIT) {
            return "本次 Commit";
        }
        return "版本全量";
    }
}
