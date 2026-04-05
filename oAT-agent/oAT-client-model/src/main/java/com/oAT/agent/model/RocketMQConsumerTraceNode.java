package com.oAT.agent.model;

import java.io.Serializable;

public class RocketMQConsumerTraceNode extends TraceNode implements Serializable, StatementError{
    private static final long serialVersionUID = -7156032079009497957L;

    String consumer;
    String message;

    private Error error;

    public String getConsumer() {
        return consumer;
    }

    public void setConsumer(String consumer) {
        this.consumer = consumer;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
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
        return "rocketMQ-consumer";
    }
}
