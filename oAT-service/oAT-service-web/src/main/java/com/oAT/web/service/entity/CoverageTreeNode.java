package com.oAT.web.service.entity;

import java.io.Serializable;

public class CoverageTreeNode implements Serializable {
    private String id;
    private String parentId;
    private String name;
    private String fullName;
    private String type;
    private boolean hasChildren;
    private String anchor;

    private int totalMethods;
    private int coveredMethods;
    private int totalBranches;
    private int coveredBranches;
    private int totalBranchTargets;
    private int coveredBranchTargets;
    private int totalLines;
    private int coveredLines;
    private int totalComplexity;

    private Double lineRate;
    private Double branchRate;
    private Double methodRate;
    private boolean hasCodeChanges;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getParentId() { return parentId; }
    public void setParentId(String parentId) { this.parentId = parentId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public boolean isHasChildren() { return hasChildren; }
    public void setHasChildren(boolean hasChildren) { this.hasChildren = hasChildren; }
    public String getAnchor() { return anchor; }
    public void setAnchor(String anchor) { this.anchor = anchor; }
    public int getTotalMethods() { return totalMethods; }
    public void setTotalMethods(int totalMethods) { this.totalMethods = totalMethods; }
    public int getCoveredMethods() { return coveredMethods; }
    public void setCoveredMethods(int coveredMethods) { this.coveredMethods = coveredMethods; }
    public int getTotalBranches() { return totalBranches; }
    public void setTotalBranches(int totalBranches) { this.totalBranches = totalBranches; }
    public int getCoveredBranches() { return coveredBranches; }
    public void setCoveredBranches(int coveredBranches) { this.coveredBranches = coveredBranches; }
    public int getTotalBranchTargets() { return totalBranchTargets; }
    public void setTotalBranchTargets(int totalBranchTargets) { this.totalBranchTargets = totalBranchTargets; }
    public int getCoveredBranchTargets() { return coveredBranchTargets; }
    public void setCoveredBranchTargets(int coveredBranchTargets) { this.coveredBranchTargets = coveredBranchTargets; }
    public int getTotalLines() { return totalLines; }
    public void setTotalLines(int totalLines) { this.totalLines = totalLines; }
    public int getCoveredLines() { return coveredLines; }
    public void setCoveredLines(int coveredLines) { this.coveredLines = coveredLines; }
    public int getTotalComplexity() { return totalComplexity; }
    public void setTotalComplexity(int totalComplexity) { this.totalComplexity = totalComplexity; }
    public Double getLineRate() { return lineRate; }
    public void setLineRate(Double lineRate) { this.lineRate = lineRate; }
    public Double getBranchRate() { return branchRate; }
    public void setBranchRate(Double branchRate) { this.branchRate = branchRate; }
    public Double getMethodRate() { return methodRate; }
    public void setMethodRate(Double methodRate) { this.methodRate = methodRate; }
    public boolean isHasCodeChanges() { return hasCodeChanges; }
    public void setHasCodeChanges(boolean hasCodeChanges) { this.hasCodeChanges = hasCodeChanges; }
}
