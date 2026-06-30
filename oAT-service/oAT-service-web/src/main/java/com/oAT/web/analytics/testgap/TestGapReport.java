package com.oAT.web.analytics.testgap;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class TestGapReport implements Serializable {
    private String reportId;
    private String language;
    private int totalUnits;
    private int riskyUnits;
    private int totalLines;
    private int uncoveredLines;
    private double lineCoverageRate;
    private List<TestGapUnit> units = new ArrayList<>();

    public String getReportId() { return reportId; }
    public void setReportId(String reportId) { this.reportId = reportId; }
    public String getLanguage() { return language; }
    public void setLanguage(String language) { this.language = language; }
    public int getTotalUnits() { return totalUnits; }
    public void setTotalUnits(int totalUnits) { this.totalUnits = totalUnits; }
    public int getRiskyUnits() { return riskyUnits; }
    public void setRiskyUnits(int riskyUnits) { this.riskyUnits = riskyUnits; }
    public int getTotalLines() { return totalLines; }
    public void setTotalLines(int totalLines) { this.totalLines = totalLines; }
    public int getUncoveredLines() { return uncoveredLines; }
    public void setUncoveredLines(int uncoveredLines) { this.uncoveredLines = uncoveredLines; }
    public double getLineCoverageRate() { return lineCoverageRate; }
    public void setLineCoverageRate(double lineCoverageRate) { this.lineCoverageRate = lineCoverageRate; }
    public List<TestGapUnit> getUnits() { return units; }
    public void setUnits(List<TestGapUnit> units) { this.units = units == null ? new ArrayList<>() : units; }
}
