package com.oAT.agent.model;

import java.io.Serializable;

public class RedisTraceNode extends TraceNode implements StatementError, Serializable {
    private static final long serialVersionUID = -7156032079009497957L;

    private String host = "host";
    private String port = "6379";

    // set, get
    private String type = "";
    // key<> value<>
    private String cmd = "";

    private Error error;

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public String getPort() {
        return port;
    }

    public void setPort(String port) {
        this.port = port;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getCmd() {
        return cmd;
    }

    public void setCmd(String cmd) {
        this.cmd = cmd;
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
        return "redis";
    }
}
