package com.oAT.web.coveragecore.ingest;

public class CoverageRawIngestResult {
    private String rawReportId;

    public static CoverageRawIngestResult fromRawId(String rawReportId) {
        CoverageRawIngestResult result = new CoverageRawIngestResult();
        result.setRawReportId(rawReportId);
        return result;
    }

    public String getRawReportId() { return rawReportId; }
    public void setRawReportId(String rawReportId) { this.rawReportId = rawReportId; }
}
