package com.oAT.agent.model;

import java.io.Serializable;

public class FeignTraceNode extends TraceNode implements RemoteInvokeNode, StatementError, Serializable {
    private static final long serialVersionUID = -7156032079009497957L;

    private String serviceMethod;
    private String serviceURL;
    private String serviceHeaders;
    private String serviceBody;

    //远程应用
    private Application remoteApp;
    private String remoteFeignTargetName;
    private String remoteMethod;
    private String remoteUrl;
    private String remoteBody;
    private String remoteResponse;

    private Error error;

    public String getServiceMethod() {
        return serviceMethod;
    }

    public void setServiceMethod(String serviceMethod) {
        this.serviceMethod = serviceMethod;
    }

    public String getServiceURL() {
        return serviceURL;
    }

    public void setServiceURL(String serviceURL) {
        this.serviceURL = serviceURL;
    }

    public String getServiceHeaders() {
        return serviceHeaders;
    }

    public void setServiceHeaders(String serviceHeaders) {
        this.serviceHeaders = serviceHeaders;
    }

    public String getServiceBody() {
        return serviceBody;
    }

    public void setServiceBody(String serviceBody) {
        this.serviceBody = serviceBody;
    }

    public String getRemoteFeignTargetName() {
        return remoteFeignTargetName;
    }

    public void setRemoteFeignTargetName(String remoteFeignTargetName) {
        this.remoteFeignTargetName = remoteFeignTargetName;
    }

    public String getRemoteMethod() {
        return remoteMethod;
    }

    public void setRemoteMethod(String remoteMethod) {
        this.remoteMethod = remoteMethod;
    }

    public String getRemoteUrl() {
        return remoteUrl;
    }

    public void setRemoteUrl(String remoteUrl) {
        this.remoteUrl = remoteUrl;
    }

    public String getRemoteBody() {
        return remoteBody;
    }

    public void setRemoteBody(String remoteBody) {
        this.remoteBody = remoteBody;
    }

    public String getRemoteResponse() {
        return remoteResponse;
    }

    public void setRemoteResponse(String remoteResponse) {
        this.remoteResponse = remoteResponse;
    }

    @Override
    public Error getError() {
        return error;
    }

    public void setError(Error error) {
        this.error = error;
    }

    protected String nodeType = "feign";

    @Override
    public String toType() {
        return nodeType;
    }

    @Override
    public Application getRemoteApp() {
        return remoteApp;
    }

    public void setRemoteApp(Application remoteApp) {
        this.remoteApp = remoteApp;
    }
}
