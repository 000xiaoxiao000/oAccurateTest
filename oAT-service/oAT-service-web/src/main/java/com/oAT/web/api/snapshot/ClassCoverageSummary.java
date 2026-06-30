package com.oAT.web.api.snapshot;

public class ClassCoverageSummary {
    private String className;
    private long totalMethods;
    private long coveredMethods;
    private long totalLines;
    private long coveredLines;
    private long totalBranches;
    private long coveredBranches;
    private long totalBranchTargets;
    private long coveredBranchTargets;
    private double branchRate;
    private int totalComplexity;

    public String getClassName() { return className; }
    public void setClassName(String className) { this.className = className; }
    public long getTotalMethods() { return totalMethods; }
    public void setTotalMethods(long totalMethods) { this.totalMethods = totalMethods; }
    public long getCoveredMethods() { return coveredMethods; }
    public void setCoveredMethods(long coveredMethods) { this.coveredMethods = coveredMethods; }
    public long getTotalLines() { return totalLines; }
    public void setTotalLines(long totalLines) { this.totalLines = totalLines; }
    public long getCoveredLines() { return coveredLines; }
    public void setCoveredLines(long coveredLines) { this.coveredLines = coveredLines; }
    public long getTotalBranches() { return totalBranches; }
    public void setTotalBranches(long totalBranches) { this.totalBranches = totalBranches; }
    public long getCoveredBranches() { return coveredBranches; }
    public void setCoveredBranches(long coveredBranches) { this.coveredBranches = coveredBranches; }
    public long getTotalBranchTargets() { return totalBranchTargets; }
    public void setTotalBranchTargets(long totalBranchTargets) { this.totalBranchTargets = totalBranchTargets; }
    public long getCoveredBranchTargets() { return coveredBranchTargets; }
    public void setCoveredBranchTargets(long coveredBranchTargets) { this.coveredBranchTargets = coveredBranchTargets; }
    public double getBranchRate() { return branchRate; }
    public void setBranchRate(double branchRate) { this.branchRate = branchRate; }
    public int getTotalComplexity() { return totalComplexity; }
    public void setTotalComplexity(int totalComplexity) { this.totalComplexity = totalComplexity; }
}
