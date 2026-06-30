package com.oAT.web.analytics.testgap;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class TestGapUnit implements Serializable {
    private String unitKey;
    private String displayName;
    private String sourcePath;
    private int totalLines;
    private int uncoveredLines;
    private double lineCoverageRate;
    private List<Integer> uncoveredLineNumbers = new ArrayList<>();

    public String getUnitKey() { return unitKey; }
    public void setUnitKey(String unitKey) { this.unitKey = unitKey; }
    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }
    public String getSourcePath() { return sourcePath; }
    public void setSourcePath(String sourcePath) { this.sourcePath = sourcePath; }
    public int getTotalLines() { return totalLines; }
    public void setTotalLines(int totalLines) { this.totalLines = totalLines; }
    public int getUncoveredLines() { return uncoveredLines; }
    public void setUncoveredLines(int uncoveredLines) { this.uncoveredLines = uncoveredLines; }
    public double getLineCoverageRate() { return lineCoverageRate; }
    public void setLineCoverageRate(double lineCoverageRate) { this.lineCoverageRate = lineCoverageRate; }
    public List<Integer> getUncoveredLineNumbers() { return uncoveredLineNumbers; }
    public void setUncoveredLineNumbers(List<Integer> uncoveredLineNumbers) { this.uncoveredLineNumbers = uncoveredLineNumbers == null ? new ArrayList<>() : uncoveredLineNumbers; }
}
