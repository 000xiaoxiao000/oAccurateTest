package com.oAT.web.esDao.entity;

import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

import java.io.Serializable;
import java.util.Date;

@Document(indexName = "probe-instance-status", shards = 2)
public class ProbeInstanceStatus implements Serializable {
    @Id
    private String id;

    @Field(type = FieldType.Keyword)
    private String probeKey;
    @Field(type = FieldType.Keyword)
    private String projectId;
    @Field(type = FieldType.Keyword)
    private String appId;
    @Field(type = FieldType.Keyword)
    private String appName;
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
    private String status;
    @Field(type = FieldType.Date, format = {}, pattern = "yyyy-MM-dd HH:mm:ss,SSS")
    private Date loginTime;
    @Field(type = FieldType.Long)
    private Long lastHeartbeatTime;
    @Field(type = FieldType.Date, format = {}, pattern = "yyyy-MM-dd HH:mm:ss,SSS")
    private Date lastStatusChangeTime;
    @Field(type = FieldType.Date, format = {}, pattern = "yyyy-MM-dd HH:mm:ss,SSS")
    private Date offlineSince;
    @Field(type = FieldType.Date, format = {}, pattern = "yyyy-MM-dd HH:mm:ss,SSS")
    private Date onlineSince;
    @Field(type = FieldType.Keyword)
    private String lastAlertEventType;
    @Field(type = FieldType.Date, format = {}, pattern = "yyyy-MM-dd HH:mm:ss,SSS")
    private Date lastAlertTime;
    @Field(type = FieldType.Date, format = {}, pattern = "yyyy-MM-dd HH:mm:ss,SSS")
    private Date createTime;
    @Field(type = FieldType.Date, format = {}, pattern = "yyyy-MM-dd HH:mm:ss,SSS")
    private Date updateTime;

    public enum Status {
        ONLINE,
        OFFLINE
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getProbeKey() {
        return probeKey;
    }

    public void setProbeKey(String probeKey) {
        this.probeKey = probeKey;
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

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Date getLoginTime() {
        return loginTime;
    }

    public void setLoginTime(Date loginTime) {
        this.loginTime = loginTime;
    }

    public Long getLastHeartbeatTime() {
        return lastHeartbeatTime;
    }

    public void setLastHeartbeatTime(Long lastHeartbeatTime) {
        this.lastHeartbeatTime = lastHeartbeatTime;
    }

    public Date getLastStatusChangeTime() {
        return lastStatusChangeTime;
    }

    public void setLastStatusChangeTime(Date lastStatusChangeTime) {
        this.lastStatusChangeTime = lastStatusChangeTime;
    }

    public Date getOfflineSince() {
        return offlineSince;
    }

    public void setOfflineSince(Date offlineSince) {
        this.offlineSince = offlineSince;
    }

    public Date getOnlineSince() {
        return onlineSince;
    }

    public void setOnlineSince(Date onlineSince) {
        this.onlineSince = onlineSince;
    }

    public String getLastAlertEventType() {
        return lastAlertEventType;
    }

    public void setLastAlertEventType(String lastAlertEventType) {
        this.lastAlertEventType = lastAlertEventType;
    }

    public Date getLastAlertTime() {
        return lastAlertTime;
    }

    public void setLastAlertTime(Date lastAlertTime) {
        this.lastAlertTime = lastAlertTime;
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
