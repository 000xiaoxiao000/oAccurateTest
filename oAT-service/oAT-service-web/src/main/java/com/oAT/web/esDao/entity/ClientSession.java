package com.oAT.web.esDao.entity;

import org.springframework.data.elasticsearch.annotations.DateFormat;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

import java.io.Serializable;

public class ClientSession implements Serializable {
    private static final String DATE_FORMAT = "yyyy-MM-dd HH:mm:ss,SSS";

    /**
     * 节点上传地址
    */
    @Field(type = FieldType.Text)
    private String[] uploadUrls;

    /**
     * 属性配置
     */
    @Field(type = FieldType.Text)
    private String configs;

    /**
     * 登录时间 (毫秒)
    */
    @Field(type = FieldType.Date, pattern = DATE_FORMAT, format = DateFormat.custom)
    private String loginTime;

    /**
     * 客户端基本信息
     */
    @Field(type = FieldType.Object)
    private ClientInfo clientInfo;

    /**
     * 会话状态 Status.active, Status.disable
     */
    @Field(type = FieldType.Keyword)
    private String status;
    @Field(type = FieldType.Long)
    private Long lastHeartbeatTime;

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

    public enum Status {
        // 激活的
        active,
        // 作废的
        disable
    }
}
