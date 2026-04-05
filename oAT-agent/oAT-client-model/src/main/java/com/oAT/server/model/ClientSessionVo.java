package com.oAT.server.model;

import com.oAT.agent.model.Application;

import java.util.Date;
import java.util.Properties;

public class ClientSessionVo implements java.io.Serializable {
    private static final long serialVersionUID = -7156032079009497957L;

    /**
     * 会话Id
     */
    private String sessionId;
    /**
     * 节点上传地址
     */
    private String[] uploadUrls;
    /**
     * 属性配置
     */
    private Properties configs;
    /**
     * 客户端基本信息
     */
    private ClientInfoVo clientInfo;
    /**
     * 登录时间（毫秒）
     */
    private Date loginTime;
    /**
     * 应用基本信息
     */
    private Application application;
    /**
     * 会话状态Status.active，Status.disable
     */
    private String status;
    private Long lastHeartbeatTime;
    /**
     * 会话是否断开
     */
    private volatile boolean disable = false;
    /**
     * 在线时长
     */
    private String onlineTime;

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public String[] getUploadUrls() {
        return uploadUrls;
    }

    public void setUploadUrls(String[] uploadUrls) {
        this.uploadUrls = uploadUrls;
    }

    public Properties getConfigs() {
        return configs;
    }

    public void setConfigs(Properties configs) {
        this.configs = configs;
    }

    public ClientInfoVo getClientInfo() {
        return clientInfo;
    }

    public void setClientInfo(ClientInfoVo clientInfo) {
        this.clientInfo = clientInfo;
    }

    public Date getLoginTime() {
        return loginTime;
    }

    public void setLoginTime(Date loginTime) {
        this.loginTime = loginTime;
    }

    public Application getApplication() {
        return application;
    }

    public void setApplication(Application application) {
        this.application = application;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Long getLastHeartbeatTime() {
        return lastHeartbeatTime;
    }

    public void setLastHeartbeatTime(Long lastHeartbeatTime) {
        this.lastHeartbeatTime = lastHeartbeatTime;
    }

    public boolean isDisable() {
        return disable;
    }

    public void setDisable(boolean disable) {
        this.disable = disable;
    }

    public String getOnlineTime() {
        return onlineTime;
    }

    public void setOnlineTime(String onlineTime) {
        this.onlineTime = onlineTime;
    }
}
