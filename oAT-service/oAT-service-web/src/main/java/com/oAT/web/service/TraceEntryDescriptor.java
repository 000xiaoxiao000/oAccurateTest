package com.oAT.web.service;

public class TraceEntryDescriptor {
    private String entryType;
    private String entryName;
    private String entryProtocol;
    private String entryAppId;
    private String entryAppName;
    private String entryClientIp;
    private String entryTopic;
    private String entryInterface;
    private String entryMethod;
    private String displayName;

    public String getEntryType() { return entryType; }
    public void setEntryType(String entryType) { this.entryType = entryType; }

    public String getEntryName() { return entryName; }
    public void setEntryName(String entryName) { this.entryName = entryName; }

    public String getEntryProtocol() { return entryProtocol; }
    public void setEntryProtocol(String entryProtocol) { this.entryProtocol = entryProtocol; }

    public String getEntryAppId() { return entryAppId; }
    public void setEntryAppId(String entryAppId) { this.entryAppId = entryAppId; }

    public String getEntryAppName() { return entryAppName; }
    public void setEntryAppName(String entryAppName) { this.entryAppName = entryAppName; }

    public String getEntryClientIp() { return entryClientIp; }
    public void setEntryClientIp(String entryClientIp) { this.entryClientIp = entryClientIp; }

    public String getEntryTopic() { return entryTopic; }
    public void setEntryTopic(String entryTopic) { this.entryTopic = entryTopic; }

    public String getEntryInterface() { return entryInterface; }
    public void setEntryInterface(String entryInterface) { this.entryInterface = entryInterface; }

    public String getEntryMethod() { return entryMethod; }
    public void setEntryMethod(String entryMethod) { this.entryMethod = entryMethod; }

    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }
}
