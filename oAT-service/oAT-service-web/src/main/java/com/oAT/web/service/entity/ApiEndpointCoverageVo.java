package com.oAT.web.service.entity;

import java.io.Serializable;

public class ApiEndpointCoverageVo implements Serializable {
    private int coveredCount;
    private int totalCount;

    public ApiEndpointCoverageVo() {
    }

    public ApiEndpointCoverageVo(int coveredCount, int totalCount) {
        this.coveredCount = coveredCount;
        this.totalCount = totalCount;
    }

    public int getCoveredCount() {
        return coveredCount;
    }

    public void setCoveredCount(int coveredCount) {
        this.coveredCount = coveredCount;
    }

    public int getTotalCount() {
        return totalCount;
    }

    public void setTotalCount(int totalCount) {
        this.totalCount = totalCount;
    }

    public String getDisplayText() {
        return coveredCount + " / " + totalCount;
    }
}
