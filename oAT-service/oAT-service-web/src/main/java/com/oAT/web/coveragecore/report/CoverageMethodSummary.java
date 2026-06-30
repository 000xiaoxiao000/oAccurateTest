package com.oAT.web.coveragecore.report;

import java.io.Serializable;

public class CoverageMethodSummary implements Serializable {
    private String methodName;
    private String methodDesc;
    private int totalLines;
    private int coveredLines;
    private int totalBranches;
    private int coveredBranches;
    private int complexity;
    private boolean covered;
    private int totalBranchTargets;
    private int coveredBranchTargets;
    private Double branchRate;
    private boolean hasCodeChanges;

    public String getMethodName() { return methodName; }
    public void setMethodName(String methodName) { this.methodName = methodName; }
    public String getMethodDesc() { return methodDesc; }
    public void setMethodDesc(String methodDesc) { this.methodDesc = methodDesc; }
    public int getTotalLines() { return totalLines; }
    public void setTotalLines(int totalLines) { this.totalLines = totalLines; }
    public int getCoveredLines() { return coveredLines; }
    public void setCoveredLines(int coveredLines) { this.coveredLines = coveredLines; }
    public int getTotalBranches() { return totalBranches; }
    public void setTotalBranches(int totalBranches) { this.totalBranches = totalBranches; }
    public int getCoveredBranches() { return coveredBranches; }
    public void setCoveredBranches(int coveredBranches) { this.coveredBranches = coveredBranches; }
    public int getComplexity() { return complexity; }
    public void setComplexity(int complexity) { this.complexity = complexity; }
    public boolean isCovered() { return covered; }
    public void setCovered(boolean covered) { this.covered = covered; }
    public int getTotalBranchTargets() { return totalBranchTargets; }
    public void setTotalBranchTargets(int totalBranchTargets) { this.totalBranchTargets = totalBranchTargets; }
    public int getCoveredBranchTargets() { return coveredBranchTargets; }
    public void setCoveredBranchTargets(int coveredBranchTargets) { this.coveredBranchTargets = coveredBranchTargets; }
    public Double getBranchRate() { return branchRate; }
    public void setBranchRate(Double branchRate) { this.branchRate = branchRate; }
    public boolean isHasCodeChanges() { return hasCodeChanges; }
    public void setHasCodeChanges(boolean hasCodeChanges) { this.hasCodeChanges = hasCodeChanges; }
}
