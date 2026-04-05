package com.oAT.agent.model;

import java.io.Serializable;

public class KafkaMQTraceNode extends TraceNode implements Serializable, StatementError {
    private static final long serialVersionUID = -7156032079009497957L;

    private String producerRecord;
    private Error error;
    //远程应用
    private Application remoteApp;

    public String getProducerRecord() {
        return producerRecord;
    }

    public void setProducerRecord(String producerRecord) {
        this.producerRecord = producerRecord;
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
        return "kafkaMQ";
    }
}
