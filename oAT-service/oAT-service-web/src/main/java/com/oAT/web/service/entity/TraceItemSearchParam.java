package com.oAT.web.service.entity;

import java.io.Serializable;
import java.util.List;

public class TraceItemSearchParam implements Serializable {
    private List<String> appIds;
    private List<String> clientIps;
    private Integer maxSize;

    public List<String> getAppIds() {
        return appIds;
    }

    public void setAppIds(List<String> appIds) {
        this.appIds = appIds;
    }

    public List<String> getClientIps() {
        return clientIps;
    }

    public void setClientIps(List<String> clientIps) {
        this.clientIps = clientIps;
    }

    public Integer getMaxSize() {
        return maxSize;
    }

    public void setMaxSize(Integer maxSize) {
        this.maxSize = maxSize;
    }
}
