package com.oAT.agent.model;

import java.io.Serializable;

public class DubboRemoteTraceNode extends TraceNode implements Serializable, StatementError {
    private static final long serialVersionUID = -7156032079009497957L;

    private Error error;

    @Override
    public Error getError() {
        return error;
    }

    public void setError(Error error) {
        this.error = error;
    }

    @Override
    public String toType() {
        return "dubbo-remote";
    }
}
