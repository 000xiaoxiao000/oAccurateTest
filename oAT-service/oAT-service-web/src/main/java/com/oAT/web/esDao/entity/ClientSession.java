package com.oAT.web.esDao.entity;


import java.io.Serializable;

public class ClientSession implements Serializable {
    private static final String DATE_FORMAT = "yyyy-MM-dd HH:mm:ss,SSS";

    /**
     * 节点上传地址
    */
    private String[] uploadUrls;

    /**
     * 属性配置
     */
    private String configs;

    /**
     * 登录时间 (格式化的字符串)
    */
    private String loginTime;

    /**
     * 客户端基本信息
     */
    private ClientInfo clientInfo;

    /**
     * 会话状态 Status.active, Status.disable
     */
    private String status;

    private Long lastHeartbeatTime;

    private String agentLogs;

    private String packageVerifyData;

    public String[] getUploadUrls() {
        return uploadUrls;
    }

    public void setUploadUrls(String[] uploadUrls) {
        this.uploadUrls = uploadUrls;
    }

    public String getConfigs() {
        return configs;
    }

    public void setConfigs(String configs) {
        this.configs = configs;
    }

    public static String getDateFormat() {
        return DATE_FORMAT;
    }

    public String getLoginTime() {
        return loginTime;
    }

    public void setLoginTime(String loginTime) {
        this.loginTime = loginTime;
    }

    public ClientInfo getClientInfo() {
        return clientInfo;
    }

    public void setClientInfo(ClientInfo clientInfo) {
        this.clientInfo = clientInfo;
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

    public String getAgentLogs() {
        return agentLogs;
    }

    public void setAgentLogs(String agentLogs) {
        this.agentLogs = agentLogs;
    }

    public String getPackageVerifyData() {
        return packageVerifyData;
    }

    public void setPackageVerifyData(String packageVerifyData) {
        this.packageVerifyData = packageVerifyData;
    }

    public enum Status {
        // 激活的
        active,
        // 作废的
        disable
    }
}
