package com.oAT.agent.model;

import java.io.Serializable;

public class SofaRpcTraceNode extends TraceNode implements RemoteInvokeNode, StatementError, Serializable {
    private static final long serialVersionUID = -7156032079009497957L;

    private String directUrl;
    //远程应用
    private Application remoteApp;
    private String interfaceName;
    private String methodName;
    private String invokeType;
    private String targetServiceUniqueName;
    private String inParam;
    private String outParam;
    private Error error;

    public String getDirectUrl() {
        return directUrl;
    }

    public void setDirectUrl(String directUrl) {
        this.directUrl = directUrl;
    }

    public void setRemoteApp(Application remoteApp) {
        this.remoteApp = remoteApp;
    }

    public String getInterfaceName() {
        return interfaceName;
    }

    public void setInterfaceName(String interfaceName) {
        this.interfaceName = interfaceName;
    }

    public String getMethodName() {
        return methodName;
    }

    public void setMethodName(String methodName) {
        this.methodName = methodName;
    }

    public String getInvokeType() {
        return invokeType;
    }

    public void setInvokeType(String invokeType) {
        this.invokeType = invokeType;
    }

    public String getTargetServiceUniqueName() {
        return targetServiceUniqueName;
    }

    public void setTargetServiceUniqueName(String targetServiceUniqueName) {
        this.targetServiceUniqueName = targetServiceUniqueName;
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

    public void setError(Error error) {
        this.error = error;
    }

    @Override
    public Application getRemoteApp() {
        return remoteApp;
    }

    @Override
    public Error getError() {
        return error;
    }

    @Override
    public String toType() {
        return "sofaRPC";
    }
}
