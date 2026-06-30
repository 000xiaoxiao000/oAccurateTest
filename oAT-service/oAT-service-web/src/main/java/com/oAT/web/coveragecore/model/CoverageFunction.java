package com.oAT.web.coveragecore.model;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class CoverageFunction implements Serializable {
    private String signature;
    private int startLine;
    private int endLine;
    private int complexity;
    private List<CoverageLine> lines = new ArrayList<>();
    private List<CoverageBranch> branches = new ArrayList<>();

    public String getSignature() { return signature; }
    public void setSignature(String signature) { this.signature = signature; }
    public int getStartLine() { return startLine; }
    public void setStartLine(int startLine) { this.startLine = startLine; }
    public int getEndLine() { return endLine; }
    public void setEndLine(int endLine) { this.endLine = endLine; }
    public int getComplexity() { return complexity; }
    public void setComplexity(int complexity) { this.complexity = complexity; }
    public List<CoverageLine> getLines() { return lines; }
    public void setLines(List<CoverageLine> lines) { this.lines = lines == null ? new ArrayList<>() : lines; }
    public List<CoverageBranch> getBranches() { return branches; }
    public void setBranches(List<CoverageBranch> branches) { this.branches = branches == null ? new ArrayList<>() : branches; }
}
