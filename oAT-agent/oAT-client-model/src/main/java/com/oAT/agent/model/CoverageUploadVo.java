package com.oAT.agent.model;

import java.io.Serializable;

public class CoverageUploadVo implements Serializable {
    private static final long serialVersionUID = 1L;

    private String sessionId;
    private String caseId;
    private String traceId;
    private String traceNodeId;
    private String entryType;
    private String entryName;
    private String appId;
    private StackNodeVo[] codeNodes;

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public String getCaseId() {
        return caseId;
    }

    public void setCaseId(String caseId) {
        this.caseId = caseId;
    }

    public String getTraceId() {
        return traceId;
    }

    public void setTraceId(String traceId) {
        this.traceId = traceId;
    }

    public String getTraceNodeId() {
        return traceNodeId;
    }

    public void setTraceNodeId(String traceNodeId) {
        this.traceNodeId = traceNodeId;
    }

    public String getEntryType() {
        return entryType;
    }

    public void setEntryType(String entryType) {
        this.entryType = entryType;
    }

    public String getEntryName() {
        return entryName;
    }

    public void setEntryName(String entryName) {
        this.entryName = entryName;
    }

    public String getAppId() {
        return appId;
    }

    public void setAppId(String appId) {
        this.appId = appId;
    }

    public StackNodeVo[] getCodeNodes() {
        return codeNodes;
    }

    public void setCodeNodes(StackNodeVo[] codeNodes) {
        this.codeNodes = codeNodes;
    }
}
