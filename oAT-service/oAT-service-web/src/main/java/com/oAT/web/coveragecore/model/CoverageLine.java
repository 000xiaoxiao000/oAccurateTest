package com.oAT.web.coveragecore.model;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class CoverageLine implements Serializable {
    private int line;
    private int hits;
    private List<CoverageFootprint> footprints = new ArrayList<>();

    public int getLine() { return line; }
    public void setLine(int line) { this.line = line; }
    public int getHits() { return hits; }
    public void setHits(int hits) { this.hits = hits; }
    public List<CoverageFootprint> getFootprints() { return footprints; }
    public void setFootprints(List<CoverageFootprint> footprints) { this.footprints = footprints == null ? new ArrayList<>() : footprints; }
}
