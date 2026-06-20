package com.oAT.agent.model;

import java.io.Serializable;

public class RabbitMQRemoteTraceNode extends TraceNode implements CodeNodeBean, Serializable, StatementError {
    private static final long serialVersionUID = -7156032079009497957L;

    private String consumerTag;
    private String body;
    private String exchange;
    private String routingKey;
    private Error error;
    private StackNodeVo[] codeNodes;

    public String getConsumerTag() {
        return consumerTag;
    }

    public void setConsumerTag(String consumerTag) {
        this.consumerTag = consumerTag;
    }

    public String getBody() {
        return body;
    }

    public void setBody(String body) {
        this.body = body;
    }

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
        return "rabbitMQ-remote";
    }
}
