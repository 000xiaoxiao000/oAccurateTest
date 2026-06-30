package com.oAT.web.coveragecore.model;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class CoverageUnit implements Serializable {
    private CoverageLanguage language;
    private String unitKey;
    private String displayName;
    private String sourcePath;
    private List<CoverageFunction> functions = new ArrayList<>();
    private List<CoverageLine> lines = new ArrayList<>();
    private List<CoverageBranch> branches = new ArrayList<>();

    public CoverageLanguage getLanguage() { return language; }
    public void setLanguage(CoverageLanguage language) { this.language = language; }
    public String getUnitKey() { return unitKey; }
    public void setUnitKey(String unitKey) { this.unitKey = unitKey; }
    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }
    public String getSourcePath() { return sourcePath; }
    public void setSourcePath(String sourcePath) { this.sourcePath = sourcePath; }
    public List<CoverageFunction> getFunctions() { return functions; }
    public void setFunctions(List<CoverageFunction> functions) { this.functions = functions == null ? new ArrayList<>() : functions; }
    public List<CoverageLine> getLines() { return lines; }
    public void setLines(List<CoverageLine> lines) { this.lines = lines == null ? new ArrayList<>() : lines; }
    public List<CoverageBranch> getBranches() { return branches; }
    public void setBranches(List<CoverageBranch> branches) { this.branches = branches == null ? new ArrayList<>() : branches; }
}
