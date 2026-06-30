package com.oAT.web.coveragecore.report;

import java.io.Serializable;

public class VersionSummary implements Serializable {
    private String id;
    private String versionNumber;
    private String describe;
    private String repoBranch;
    private String repoCommitId;
    private String programFile;
    private String programName;
    private String createTimeText;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getVersionNumber() { return versionNumber; }
    public void setVersionNumber(String versionNumber) { this.versionNumber = versionNumber; }
    public String getDescribe() { return describe; }
    public void setDescribe(String describe) { this.describe = describe; }
    public String getRepoBranch() { return repoBranch; }
    public void setRepoBranch(String repoBranch) { this.repoBranch = repoBranch; }
    public String getRepoCommitId() { return repoCommitId; }
    public void setRepoCommitId(String repoCommitId) { this.repoCommitId = repoCommitId; }
    public String getProgramFile() { return programFile; }
    public void setProgramFile(String programFile) { this.programFile = programFile; }
    public String getProgramName() { return programName; }
    public void setProgramName(String programName) { this.programName = programName; }
    public String getCreateTimeText() { return createTimeText; }
    public void setCreateTimeText(String createTimeText) { this.createTimeText = createTimeText; }
}
