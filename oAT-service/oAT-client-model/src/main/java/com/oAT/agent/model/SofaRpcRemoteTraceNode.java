package com.oAT.agent.model;

import java.io.Serializable;

public class SofaRpcRemoteTraceNode extends TraceNode implements CodeNodeBean, Serializable, StatementError {
    private static final long serialVersionUID = -7156032079009497957L;

    private String targetServiceUniqueName;
    private String interfaceName;
    private String methodName;
    private Object[] methodArgs;

    private Error error;
    // 代码堆栈
    private StackNodeVo[] codeNodes;

    public String getTargetServiceUniqueName() {
        return targetServiceUniqueName;
    }

    public void setTargetServiceUniqueName(String targetServiceUniqueName) {
        this.targetServiceUniqueName = targetServiceUniqueName;
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

    public Object[] getMethodArgs() {
        return methodArgs;
    }

    public void setMethodArgs(Object[] methodArgs) {
        this.methodArgs = methodArgs;
    }

    @Override
    public Error getError() {
        return error;
    }

    public void setError(Error error) {
        this.error = error;
    }

    @Override
    public StackNodeVo[] getCodeNodes() {
        return codeNodes;
    }

    public void setCodeNodes(StackNodeVo[] codeNodes) {
        this.codeNodes = codeNodes;
    }

    @Override
    public String toType() {
        return "sofaRPC-remote";
    }
}
