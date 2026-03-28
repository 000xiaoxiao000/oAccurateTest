package com.oAT.agent.model;

import java.io.Serializable;

/**
 * 跟踪节点
 */
public abstract class TraceNode implements Serializable {
    private static final long serialVersionUID = -7156032079009497957L;

    /**
     * 全局唯一标识
     */
    private String traceId;
    /**
     * 节点调用标识，表明执行顺序，与嵌套层次
     */
    private String traceNodeId;
    /**
     * 会话Id
     */
    private String sessionId;
    /**
     * 当前应用信息
     */
    private Application app;
    /**
     * 状态：取值于TraceNode.Status
     */
    private String status;
    /**
     * 应用节点IP
     */
    private String addressIp;
    /**
     * 开始时间，单位：毫秒
     */
    private Long beginTime = 0L;
    /**
     * 结束时间，单位：毫秒
     */
    private Long endTime = 0L;
    /**
     * 使用时间，单位：毫秒
     */
    private Long useTime = 0L;

    public static long getSerialVersionUID() {
        return serialVersionUID;
    }

    public String getTraceId() {
        return traceId;
    }

    public void setTraceId(String traceId) {
        this.traceId = traceId;
    }

    public String getTraceNodeId() {
        return traceNodeId;
    }

    public void setTraceNodeId(String traceNodeId) {
        this.traceNodeId = traceNodeId;
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public Application getApp() {
        return app;
    }

    public void setApp(Application app) {
        this.app = app;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getAddressIp() {
        return addressIp;
    }

    public void setAddressIp(String addressIp) {
        this.addressIp = addressIp;
    }

    public Long getBeginTime() {
        return beginTime;
    }

    public void setBeginTime(Long beginTime) {
        this.beginTime = beginTime;
    }

    public Long getEndTime() {
        return endTime;
    }

    public void setEndTime(Long endTime) {
        this.endTime = endTime;
    }

    public Long getUseTime() {
        return useTime;
    }

    public void setUseTime(Long useTime) {
        this.useTime = useTime;
    }

    public abstract String toType();

    public enum Status {
        succeed,    //成功
        succeed_warn,   //成功但存在警告
        fail    //失败
    }
}
