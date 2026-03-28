package com.oAT.web.esDao.entity;

import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

import java.io.Serializable;

public class ClientInfo implements Serializable {
    @Field(type = FieldType.Keyword)
    private String appKey;           // 应用ID
    @Field(type = FieldType.Keyword)
    private String agentVersion;    // 客户端版本
    @Field(type = FieldType.Keyword )
    private String systemDir;   // 应用所在系统目录
    @Field(type = FieldType.Keyword )
    private String pid;         // 进程id
    @Field(type = FieldType.Keyword )
    private String jvmVersion;  // jvm 版本
    @Field(type = FieldType.Text )
    private String jvmOption;   // jvm启动参数配置
    @Field(type = FieldType.Keyword )
    private String osName;      // 操作系统名称
    @Field(type = FieldType.Keyword )
    private String osVersion;   // 操作系统版本
    @Field(type = FieldType.Ip )
    private String addressIp;   // 客户端ip地址
    @Field(type = FieldType.Keyword)
    private String addressMac;  // 客户端mac地址

    // ============ getter/setter ============

    public String getAppKey() {
        return appKey;
    }

    public void setAppKey(String appKey) {
        this.appKey = appKey;
    }

    public String getAgentVersion() {
        return agentVersion;
    }

    public void setAgentVersion(String agentVersion) {
        this.agentVersion = agentVersion;
    }

    public String getSystemDir() {
        return systemDir;
    }

    public void setSystemDir(String systemDir) {
        this.systemDir = systemDir;
    }

    public String getPid() {
        return pid;
    }

    public void setPid(String pid) {
        this.pid = pid;
    }

    public String getJvmVersion() {
        return jvmVersion;
    }

    public void setJvmVersion(String jvmVersion) {
        this.jvmVersion = jvmVersion;
    }

    public String getJvmOption() {
        return jvmOption;
    }

    public void setJvmOption(String jvmOption) {
        this.jvmOption = jvmOption;
    }

    public String getOsName() {
        return osName;
    }

    public void setOsName(String osName) {
        this.osName = osName;
    }

    public String getOsVersion() {
        return osVersion;
    }

    public void setOsVersion(String osVersion) {
        this.osVersion = osVersion;
    }

    public String getAddressIp() {
        return addressIp;
    }

    public void setAddressIp(String addressIp) {
        this.addressIp = addressIp;
    }

    public String getAddressMac() {
        return addressMac;
    }

    public void setAddressMac(String addressMac) {
        this.addressMac = addressMac;
    }
}
