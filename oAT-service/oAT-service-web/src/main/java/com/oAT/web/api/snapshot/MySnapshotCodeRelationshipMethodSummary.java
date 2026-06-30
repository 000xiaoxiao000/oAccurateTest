package com.oAT.web.api.snapshot;

import java.util.List;

public class MySnapshotCodeRelationshipMethodSummary {
    private String className;
    private String methodName;
    private String methodDescriptor;
    private List<Integer> doLines;
    private int lineTotalCount;
    private int coveredLineCount;
    private int branchTotalCount;
    private int branchCoveredCount;
    private int branchTargetTotalCount;
    private int branchTargetCoveredCount;

    public MySnapshotCodeRelationshipMethodSummary() {
    }

    public MySnapshotCodeRelationshipMethodSummary(String className,
                                                   String methodName,
                                                   String methodDescriptor,
                                                   List<Integer> doLines) {
        this.className = className;
        this.methodName = methodName;
        this.methodDescriptor = methodDescriptor;
        this.doLines = doLines;
    }

    public String getClassName() { return className; }
    public void setClassName(String className) { this.className = className; }
    public String getMethodName() { return methodName; }
    public void setMethodName(String methodName) { this.methodName = methodName; }
    public String getMethodDescriptor() { return methodDescriptor; }
    public void setMethodDescriptor(String methodDescriptor) { this.methodDescriptor = methodDescriptor; }
    public List<Integer> getDoLines() { return doLines; }
    public void setDoLines(List<Integer> doLines) { this.doLines = doLines; }
    public int getLineTotalCount() { return lineTotalCount; }
    public void setLineTotalCount(int lineTotalCount) { this.lineTotalCount = lineTotalCount; }
    public int getCoveredLineCount() { return coveredLineCount; }
    public void setCoveredLineCount(int coveredLineCount) { this.coveredLineCount = coveredLineCount; }
    public int getBranchTotalCount() { return branchTotalCount; }
    public void setBranchTotalCount(int branchTotalCount) { this.branchTotalCount = branchTotalCount; }
    public int getBranchCoveredCount() { return branchCoveredCount; }
    public void setBranchCoveredCount(int branchCoveredCount) { this.branchCoveredCount = branchCoveredCount; }
    public int getBranchTargetTotalCount() { return branchTargetTotalCount; }
    public void setBranchTargetTotalCount(int branchTargetTotalCount) { this.branchTargetTotalCount = branchTargetTotalCount; }
    public int getBranchTargetCoveredCount() { return branchTargetCoveredCount; }
    public void setBranchTargetCoveredCount(int branchTargetCoveredCount) { this.branchTargetCoveredCount = branchTargetCoveredCount; }
}
