package com.oAT.web.coveragecore.report;

import java.io.Serializable;

public class AppSummary implements Serializable {
    private String id;
    private String name;
    private String currentVersion;
    private String currentBranch;
    private String currentCommitId;
    private String sourceType;
    private String language;
    private String languageConfig;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getCurrentVersion() { return currentVersion; }
    public void setCurrentVersion(String currentVersion) { this.currentVersion = currentVersion; }
    public String getCurrentBranch() { return currentBranch; }
    public void setCurrentBranch(String currentBranch) { this.currentBranch = currentBranch; }
    public String getCurrentCommitId() { return currentCommitId; }
    public void setCurrentCommitId(String currentCommitId) { this.currentCommitId = currentCommitId; }
    public String getSourceType() { return sourceType; }
    public void setSourceType(String sourceType) { this.sourceType = sourceType; }
    public String getLanguage() { return language; }
    public void setLanguage(String language) { this.language = language; }
    public String getLanguageConfig() { return languageConfig; }
    public void setLanguageConfig(String languageConfig) { this.languageConfig = languageConfig; }
}
