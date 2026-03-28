package com.oAT.server.model;

public class ClientInfoVo implements java.io.Serializable {
    private static final long serialVersionUID = -7156032079009497957L;

    private String appKey;
    private String agentVersion;
    /**
     * 应用所在系统目录
     */
    private String systemDir;
    /**
     * 进程id
     */
    private String pid;

    /**
     * 时间戳
     */
    private Long timesTamp;
    /**
     * jvm版本
     */
    private String jvmVersion;
    /**
     * jvm启动参数配置
     */
    private String jvmOption;
    /**
     * 操作系统名称
     */
    private String osName;
    /**
     * 操作系统版本
     */
    private String osVersion;
    /**
     * 客户端ip地址
     */
    private String addressIp;
    /**
     * 客户端mac地址
     */
    private String addressMac;
    /**
     * 客户端端口号
     */
    private String addressPort;

    public String getAppKey() {
        return appKey;
    }

    // ============ getter/setter ============

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

    public Long getTimesTamp() {
        return timesTamp;
    }

    public void setTimesTamp(Long timesTamp) {
        this.timesTamp = timesTamp;
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

    public String getAddressPort() {
        return addressPort;
    }

    public void setAddressPort(String addressPort) {
        this.addressPort = addressPort;
    }
}
