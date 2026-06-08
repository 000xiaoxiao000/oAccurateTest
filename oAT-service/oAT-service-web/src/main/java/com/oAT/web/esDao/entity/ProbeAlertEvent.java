package com.oAT.web.esDao.entity;

import org.springframework.data.annotation.Id;

import java.io.Serializable;
import java.util.Date;

public class ProbeAlertEvent implements Serializable {
    @Id
    private String id;

    private String projectId;
    private String appId;
    private String appName;
    private String probeKey;
    private String sessionId;
    private String addressIp;
    private String pid;
    private String systemDir;
    private String agentVersion;
    private String eventType;
    private Date eventTime;
    private Long lastHeartbeatTime;
    private Long offlineDurationMillis;
    private String message;
    private Boolean notifyEnabled;
    private String notifyStatus;
    private String notifyChannel;
    private String notifyResponse;
    private String notifyError;
    private Date createTime;
    private Date updateTime;

    public enum EventType {
        ONLINE,
        OFFLINE,
        RECOVERED
    }

    public enum NotifyStatus {
        PENDING,
        SUCCESS,
        FAILED,
        SKIPPED
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getProjectId() {
        return projectId;
    }

    public void setProjectId(String projectId) {
        this.projectId = projectId;
    }

    public String getAppId() {
        return appId;
    }

    public void setAppId(String appId) {
        this.appId = appId;
    }

    public String getAppName() {
        return appName;
    }

    public void setAppName(String appName) {
        this.appName = appName;
    }

    public String getProbeKey() {
        return probeKey;
    }

    public void setProbeKey(String probeKey) {
        this.probeKey = probeKey;
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public String getAddressIp() {
        return addressIp;
    }

    public void setAddressIp(String addressIp) {
        this.addressIp = addressIp;
    }

    public String getPid() {
        return pid;
    }

    public void setPid(String pid) {
        this.pid = pid;
    }

    public String getSystemDir() {
        return systemDir;
    }

    public void setSystemDir(String systemDir) {
        this.systemDir = systemDir;
    }

    public String getAgentVersion() {
        return agentVersion;
    }

    public void setAgentVersion(String agentVersion) {
        this.agentVersion = agentVersion;
    }

    public String getEventType() {
        return eventType;
    }

    public void setEventType(String eventType) {
        this.eventType = eventType;
    }

    public Date getEventTime() {
        return eventTime;
    }

    public void setEventTime(Date eventTime) {
        this.eventTime = eventTime;
    }

    public Long getLastHeartbeatTime() {
        return lastHeartbeatTime;
    }

    public void setLastHeartbeatTime(Long lastHeartbeatTime) {
        this.lastHeartbeatTime = lastHeartbeatTime;
    }

    public Long getOfflineDurationMillis() {
        return offlineDurationMillis;
    }

    public void setOfflineDurationMillis(Long offlineDurationMillis) {
        this.offlineDurationMillis = offlineDurationMillis;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public Boolean getNotifyEnabled() {
        return notifyEnabled;
    }

    public void setNotifyEnabled(Boolean notifyEnabled) {
        this.notifyEnabled = notifyEnabled;
    }

    public String getNotifyStatus() {
        return notifyStatus;
    }

    public void setNotifyStatus(String notifyStatus) {
        this.notifyStatus = notifyStatus;
    }

    public String getNotifyChannel() {
        return notifyChannel;
    }

    public void setNotifyChannel(String notifyChannel) {
        this.notifyChannel = notifyChannel;
    }

    public String getNotifyResponse() {
        return notifyResponse;
    }

    public void setNotifyResponse(String notifyResponse) {
        this.notifyResponse = notifyResponse;
    }

    public String getNotifyError() {
        return notifyError;
    }

    public void setNotifyError(String notifyError) {
        this.notifyError = notifyError;
    }

    public Date getCreateTime() {
        return createTime;
    }

    public void setCreateTime(Date createTime) {
        this.createTime = createTime;
    }

    public Date getUpdateTime() {
        return updateTime;
    }

    public void setUpdateTime(Date updateTime) {
        this.updateTime = updateTime;
    }
}
