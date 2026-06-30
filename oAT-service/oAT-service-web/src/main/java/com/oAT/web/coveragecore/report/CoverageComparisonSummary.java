package com.oAT.web.coveragecore.report;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class CoverageComparisonSummary implements Serializable {
    private int addedCount;
    private int stableCount;
    private int decreasedCount;
    private List<MethodDiffSummary> addedMethods = new ArrayList<>();
    private List<MethodDiffSummary> stableMethods = new ArrayList<>();
    private List<MethodDiffSummary> decreasedMethods = new ArrayList<>();

    public int getAddedCount() { return addedCount; }
    public void setAddedCount(int addedCount) { this.addedCount = addedCount; }
    public int getStableCount() { return stableCount; }
    public void setStableCount(int stableCount) { this.stableCount = stableCount; }
    public int getDecreasedCount() { return decreasedCount; }
    public void setDecreasedCount(int decreasedCount) { this.decreasedCount = decreasedCount; }
    public List<MethodDiffSummary> getAddedMethods() { return addedMethods; }
    public void setAddedMethods(List<MethodDiffSummary> addedMethods) { this.addedMethods = addedMethods == null ? new ArrayList<>() : addedMethods; }
    public List<MethodDiffSummary> getStableMethods() { return stableMethods; }
    public void setStableMethods(List<MethodDiffSummary> stableMethods) { this.stableMethods = stableMethods == null ? new ArrayList<>() : stableMethods; }
    public List<MethodDiffSummary> getDecreasedMethods() { return decreasedMethods; }
    public void setDecreasedMethods(List<MethodDiffSummary> decreasedMethods) { this.decreasedMethods = decreasedMethods == null ? new ArrayList<>() : decreasedMethods; }
}
