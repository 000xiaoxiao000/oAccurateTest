package com.oAT.agent.model;

import java.io.Serializable;

public class ServiceTraceNode extends TraceNode implements CodeNodeBean, StatementError, Serializable {
    private static final long serialVersionUID = -7156032079009497957L;

    //服务名称
    private String serviceName;
    //服务简称
    private String simpleName;
    //方法名称
    private String methodName;

    private Error error;

    // 代码堆栈
    private StackNodeVo[] codeNodes;

    public String getServiceName() {
        return serviceName;
    }

    public void setServiceName(String serviceName) {
        this.serviceName = serviceName;
    }

    public String getSimpleName() {
        return simpleName;
    }

    public void setSimpleName(String simpleName) {
        this.simpleName = simpleName;
    }

    public String getMethodName() {
        return methodName;
    }

    public void setMethodName(String methodName) {
        this.methodName = methodName;
    }

    @Override
    public StackNodeVo[] getCodeNodes() {
        return codeNodes;
    }

    public void setCodeNodes(StackNodeVo[] codeNodes) {
        this.codeNodes = codeNodes;
    }

    public void setError(Error error) {
        this.error = error;
    }

    @Override
    public Error getError() {
        return error;
    }

    @Override
    public String toType() {
        return "service";
    }
}
