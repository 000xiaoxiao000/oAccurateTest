package com.oAT.web.coveragecore.model;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class SourceCoveragePayload implements Serializable {
    private CoverageLanguage language;
    private String sourcePath;
    private List<SourceCoverageLine> lines = new ArrayList<>();
    private List<SourceCoverageBranch> branches = new ArrayList<>();

    public CoverageLanguage getLanguage() { return language; }
    public void setLanguage(CoverageLanguage language) { this.language = language; }
    public String getSourcePath() { return sourcePath; }
    public void setSourcePath(String sourcePath) { this.sourcePath = sourcePath; }
    public List<SourceCoverageLine> getLines() { return lines; }
    public void setLines(List<SourceCoverageLine> lines) { this.lines = lines == null ? new ArrayList<>() : lines; }
    public List<SourceCoverageBranch> getBranches() { return branches; }
    public void setBranches(List<SourceCoverageBranch> branches) { this.branches = branches == null ? new ArrayList<>() : branches; }
}
