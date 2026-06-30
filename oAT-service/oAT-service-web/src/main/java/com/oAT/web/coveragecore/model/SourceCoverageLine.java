package com.oAT.web.coveragecore.model;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class SourceCoverageLine implements Serializable {
    private int line;
    private String text;
    private int hits;
    private List<String> cases = new ArrayList<>();
    private boolean changed;

    public int getLine() { return line; }
    public void setLine(int line) { this.line = line; }
    public String getText() { return text; }
    public void setText(String text) { this.text = text; }
    public int getHits() { return hits; }
    public void setHits(int hits) { this.hits = hits; }
    public List<String> getCases() { return cases; }
    public void setCases(List<String> cases) { this.cases = cases == null ? new ArrayList<>() : cases; }
    public boolean isChanged() { return changed; }
    public void setChanged(boolean changed) { this.changed = changed; }
}
