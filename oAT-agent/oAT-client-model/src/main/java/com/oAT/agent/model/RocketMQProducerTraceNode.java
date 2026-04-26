package com.oAT.agent.model;

import java.io.Serializable;

public class RocketMQProducerTraceNode extends TraceNode implements Serializable, StatementError, RemoteInvokeNode {
    private static final long serialVersionUID = -7156032079009497957L;

    String producer;
    String message;
    //远程应用
    private Application remoteApp;

    private Error error;

    public String getProducer() {
        return producer;
    }

    public void setProducer(String producer) {
        this.producer = producer;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    @Override
    public Application getRemoteApp() {
        return remoteApp;
    }

    public void setRemoteApp(Application remoteApp) {
        this.remoteApp = remoteApp;
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
        return "rocketMQ-producer";
    }
}
