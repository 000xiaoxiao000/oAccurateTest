package com.oAT.web.coveragecore.model;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class CoverageModule implements Serializable {
    private CoverageLanguage language;
    private String moduleKey;
    private List<CoverageUnit> units = new ArrayList<>();

    public CoverageLanguage getLanguage() { return language; }
    public void setLanguage(CoverageLanguage language) { this.language = language; }
    public String getModuleKey() { return moduleKey; }
    public void setModuleKey(String moduleKey) { this.moduleKey = moduleKey; }
    public List<CoverageUnit> getUnits() { return units; }
    public void setUnits(List<CoverageUnit> units) { this.units = units == null ? new ArrayList<>() : units; }
}
