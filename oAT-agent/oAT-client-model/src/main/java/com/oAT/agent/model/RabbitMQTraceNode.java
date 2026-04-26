package com.oAT.agent.model;

import java.io.Serializable;

public class RabbitMQTraceNode extends TraceNode implements Serializable, StatementError, RemoteInvokeNode {
    private static final long serialVersionUID = -7156032079009497957L;

    private String exchange;
    private String routingKey;
    private String body;
    private Error error;
    //远程应用
    private Application remoteApp;

    public String getExchange() {
        return exchange;
    }

    public void setExchange(String exchange) {
        this.exchange = exchange;
    }

    public String getRoutingKey() {
        return routingKey;
    }

    public void setRoutingKey(String routingKey) {
        this.routingKey = routingKey;
    }

    public String getBody() {
        return body;
    }

    public void setBody(String body) {
        this.body = body;
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
        return "rabbitMQ";
    }
}
