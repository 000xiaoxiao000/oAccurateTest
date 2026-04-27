package com.oAT.web.service.entity;

import java.io.Serializable;

public class PackageCommitVerifyVo implements Serializable {
    private String runtimeCommitId;
    private String targetCommitId;
    private Boolean matched;

    public PackageCommitVerifyVo() {
    }

    public PackageCommitVerifyVo(String runtimeCommitId, String targetCommitId, Boolean matched) {
        this.runtimeCommitId = runtimeCommitId;
        this.targetCommitId = targetCommitId;
        this.matched = matched;
    }

    public String getRuntimeCommitId() {
        return runtimeCommitId;
    }

    public void setRuntimeCommitId(String runtimeCommitId) {
        this.runtimeCommitId = runtimeCommitId;
    }

    public String getTargetCommitId() {
        return targetCommitId;
    }

    public void setTargetCommitId(String targetCommitId) {
        this.targetCommitId = targetCommitId;
    }

    public Boolean getMatched() {
        return matched;
    }

    public void setMatched(Boolean matched) {
        this.matched = matched;
    }
}
