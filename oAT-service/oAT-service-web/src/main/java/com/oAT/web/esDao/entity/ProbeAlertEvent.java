package com.oAT.web.esDao.entity;

import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

import java.io.Serializable;
import java.util.Date;

@Document(indexName = "probe-alert-event", shards = 2)
public class ProbeAlertEvent implements Serializable {
    @Id
    private String id;

    @Field(type = FieldType.Keyword)
    private String projectId;
    @Field(type = FieldType.Keyword)
    private String appId;
    @Field(type = FieldType.Keyword)
    private String appName;
    @Field(type = FieldType.Keyword)
    private String probeKey;
    @Field(type = FieldType.Keyword)
    private String sessionId;
    @Field(type = FieldType.Keyword)
    private String addressIp;
    @Field(type = FieldType.Keyword)
    private String pid;
    @Field(type = FieldType.Keyword)
    private String systemDir;
    @Field(type = FieldType.Keyword)
    private String agentVersion;
    @Field(type = FieldType.Keyword)
    private String eventType;
    @Field(type = FieldType.Date, format = {}, pattern = "yyyy-MM-dd HH:mm:ss,SSS")
    private Date eventTime;
    @Field(type = FieldType.Long)
    private Long lastHeartbeatTime;
    @Field(type = FieldType.Long)
    private Long offlineDurationMillis;
    @Field(type = FieldType.Text)
    private String message;
    @Field(type = FieldType.Boolean)
    private Boolean notifyEnabled;
    @Field(type = FieldType.Keyword)
    private String notifyStatus;
    @Field(type = FieldType.Keyword)
    private String notifyChannel;
    @Field(type = FieldType.Text)
    private String notifyResponse;
    @Field(type = FieldType.Text)
    private String notifyError;
    @Field(type = FieldType.Date, format = {}, pattern = "yyyy-MM-dd HH:mm:ss,SSS")
    private Date createTime;
    @Field(type = FieldType.Date, format = {}, pattern = "yyyy-MM-dd HH:mm:ss,SSS")
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
