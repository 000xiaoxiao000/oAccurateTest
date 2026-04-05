package com.oAT.server.model;

import java.io.Serializable;

public class TraceItemVo implements Serializable {
    private static final long serialVersionUID = -7156032079009497957L;

    private String traceId;
    private String title;
    private Long cacheTime; // 缓存时间
    private int validity;//有效期 单位：秒
    private int index;
    private String appId;
    private String addressIp;
    private String clientIp;// 客户端IP，即终端IP
    private String userHeader;// 客户端唯一标识

    /**
     * @param traceId
     * @param httpUrl
     * @param validity
     */
    public TraceItemVo(String traceId, String httpUrl, int validity) {
        this.traceId = traceId;
        this.title = httpUrl;
        this.cacheTime = System.currentTimeMillis();
        this.validity = validity;
    }

    public TraceItemVo() {
    }

    public String getTraceId() {
        return traceId;
    }

    public void setTraceId(String traceId) {
        this.traceId = traceId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public Long getCacheTime() {
        return cacheTime;
    }

    public boolean isValidity(long currentTime) {
        return (cacheTime + (validity * 1000L) > currentTime);
    }

    public void setCacheTime(Long cacheTime) {
        this.cacheTime = cacheTime;
    }

    public int getIndex() {
        return index;
    }

    public void setIndex(int index) {
        this.index = index;
    }

    public int getValidity() {
        return validity;
    }

    public void setValidity(int validity) {
        this.validity = validity;
    }

    public String getAppId() {
        return appId;
    }

    public void setAppId(String appId) {
        this.appId = appId;
    }

    public String getAddressIp() {
        return addressIp;
    }

    public void setAddressIp(String addressIp) {
        this.addressIp = addressIp;
    }

    public String getClientIp() {
        return clientIp;
    }

    public void setClientIp(String clientIp) {
        this.clientIp = clientIp;
    }

    public String getUserHeader() {
        return userHeader;
    }

    public void setUserHeader(String userHeader) {
        this.userHeader = userHeader;
    }
}
