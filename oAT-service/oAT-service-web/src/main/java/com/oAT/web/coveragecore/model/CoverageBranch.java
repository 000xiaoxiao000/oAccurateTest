package com.oAT.web.coveragecore.model;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class CoverageBranch implements Serializable {
    private int line;
    private int branchIndex;
    private String groupId;
    private int hits;
    private List<CoverageFootprint> footprints = new ArrayList<>();

    public int getLine() { return line; }
    public void setLine(int line) { this.line = line; }
    public int getBranchIndex() { return branchIndex; }
    public void setBranchIndex(int branchIndex) { this.branchIndex = branchIndex; }
    public String getGroupId() { return groupId; }
    public void setGroupId(String groupId) { this.groupId = groupId; }
    public int getHits() { return hits; }
    public void setHits(int hits) { this.hits = hits; }
    public List<CoverageFootprint> getFootprints() { return footprints; }
    public void setFootprints(List<CoverageFootprint> footprints) { this.footprints = footprints == null ? new ArrayList<>() : footprints; }
}
