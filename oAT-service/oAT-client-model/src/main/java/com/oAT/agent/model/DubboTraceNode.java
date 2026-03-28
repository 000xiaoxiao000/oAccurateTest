package com.oAT.agent.model;

import java.io.Serializable;

public class DubboTraceNode extends TraceNode implements RemoteInvokeNode, StatementError, Serializable {
    private static final long serialVersionUID = -7156032079009497957L;

    private String remoteIp;
    private String remoteUrl;
    //远程应用
    private Application remoteApp;
    private String serviceInterface;
    private String serviceMethodName;
    private String inParam;
    private String outParam;
    private Error error;

    public String getRemoteIp() {
        return remoteIp;
    }

    public void setRemoteIp(String remoteIp) {
        this.remoteIp = remoteIp;
    }

    public String getRemoteUrl() {
        return remoteUrl;
    }

    public void setRemoteUrl(String remoteUrl) {
        this.remoteUrl = remoteUrl;
    }

    @Override
    public Application getRemoteApp() {
        return remoteApp;
    }

    public void setRemoteApp(Application remoteApp) {
        this.remoteApp = remoteApp;
    }

    public String getServiceInterface() {
        return serviceInterface;
    }

    public void setServiceInterface(String serviceInterface) {
        this.serviceInterface = serviceInterface;
    }

    public String getServiceMethodName() {
        return serviceMethodName;
    }

    public void setServiceMethodName(String serviceMethodName) {
        this.serviceMethodName = serviceMethodName;
    }

    public String getInParam() {
        return inParam;
    }

    public void setInParam(String inParam) {
        this.inParam = inParam;
    }

    public String getOutParam() {
        return outParam;
    }

    public void setOutParam(String outParam) {
        this.outParam = outParam;
    }

    @Override
    public Error getError() {
        return error;
    }

    public void setError(Error error) {
        this.error = error;
    }

    @Override
    public String toType() {
        return "dubbo";
    }

}
