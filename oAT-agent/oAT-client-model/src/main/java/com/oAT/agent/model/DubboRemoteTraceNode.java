package com.oAT.agent.model;

import java.io.Serializable;

public class DubboRemoteTraceNode extends TraceNode implements CodeNodeBean, Serializable, StatementError {
    private static final long serialVersionUID = -7156032079009497957L;

    private Error error;
    private StackNodeVo[] codeNodes;

    @Override
    public Error getError() {
        return error;
    }

    public void setError(Error error) {
        this.error = error;
    }

    public StackNodeVo[] getCodeNodes() {
        return codeNodes;
    }

    public void setCodeNodes(StackNodeVo[] codeNodes) {
        this.codeNodes = codeNodes;
    }

    @Override
    public String toType() {
        return "dubbo-remote";
    }
}
