package com.oAT.agent.model;

import java.io.Serializable;

public class KafkaMQRemoteTraceNode extends TraceNode implements Serializable, StatementError {
    private static final long serialVersionUID = -7156032079009497957L;

    private String ConsumerRecords;
    private Error error;

    public String getConsumerRecords() {
        return ConsumerRecords;
    }

    public void setConsumerRecords(String consumerRecords) {
        ConsumerRecords = consumerRecords;
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
        return "kafkaMQ-remote";
    }
}
