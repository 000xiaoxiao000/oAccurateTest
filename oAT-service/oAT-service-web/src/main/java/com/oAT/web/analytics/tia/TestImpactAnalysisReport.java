package com.oAT.web.analytics.tia;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class TestImpactAnalysisReport implements Serializable {
    private String reportId;
    private String language;
    private int changedLineCount;
    private int impactedCaseCount;
    private int impactedTraceCount;
    private List<TestImpactCase> impactedCases = new ArrayList<>();
    private List<String> reasons = new ArrayList<>();

    public String getReportId() { return reportId; }
    public void setReportId(String reportId) { this.reportId = reportId; }
    public String getLanguage() { return language; }
    public void setLanguage(String language) { this.language = language; }
    public int getChangedLineCount() { return changedLineCount; }
    public void setChangedLineCount(int changedLineCount) { this.changedLineCount = changedLineCount; }
    public int getImpactedCaseCount() { return impactedCaseCount; }
    public void setImpactedCaseCount(int impactedCaseCount) { this.impactedCaseCount = impactedCaseCount; }
    public int getImpactedTraceCount() { return impactedTraceCount; }
    public void setImpactedTraceCount(int impactedTraceCount) { this.impactedTraceCount = impactedTraceCount; }
    public List<TestImpactCase> getImpactedCases() { return impactedCases; }
    public void setImpactedCases(List<TestImpactCase> impactedCases) { this.impactedCases = impactedCases == null ? new ArrayList<>() : impactedCases; }
    public List<String> getReasons() { return reasons; }
    public void setReasons(List<String> reasons) { this.reasons = reasons == null ? new ArrayList<>() : reasons; }
}
