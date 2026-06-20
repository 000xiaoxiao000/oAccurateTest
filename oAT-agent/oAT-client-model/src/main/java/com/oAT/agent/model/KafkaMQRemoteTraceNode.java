package com.oAT.agent.model;

import java.io.Serializable;

public class KafkaMQRemoteTraceNode extends TraceNode implements CodeNodeBean, Serializable, StatementError {
    private static final long serialVersionUID = -7156032079009497957L;

    private String ConsumerRecords;
    private Error error;
    private StackNodeVo[] codeNodes;

    public String getConsumerRecords() {
        return ConsumerRecords;
    }

    public void setConsumerRecords(String consumerRecords) {
        ConsumerRecords = consumerRecords;
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
    public Error getError() {
        return error;
    }

    @Override
    public String toType() {
        return "kafkaMQ-remote";
    }
}
