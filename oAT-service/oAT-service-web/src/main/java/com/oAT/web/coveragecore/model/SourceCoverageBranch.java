package com.oAT.web.coveragecore.model;

import java.io.Serializable;

public class SourceCoverageBranch implements Serializable {
    private int line;
    private String groupId;
    private int branchIndex;
    private int hits;

    public int getLine() { return line; }
    public void setLine(int line) { this.line = line; }
    public String getGroupId() { return groupId; }
    public void setGroupId(String groupId) { this.groupId = groupId; }
    public int getBranchIndex() { return branchIndex; }
    public void setBranchIndex(int branchIndex) { this.branchIndex = branchIndex; }
    public int getHits() { return hits; }
    public void setHits(int hits) { this.hits = hits; }
}
