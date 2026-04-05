package com.oAT.agent.model;

import java.io.Serializable;

public class HttpClientTraceNode extends TraceNode implements StatementError, Serializable {
    private static final long serialVersionUID = -7156032079009497957L;
    private String serviceMethod;
    private String serviceURL;
    private String serviceHeaders;
    private String serviceBody;
    private Error error;

    public String getServiceBody() {
        return serviceBody;
    }

    public void setServiceBody(String serviceBody) {
        this.serviceBody = serviceBody;
    }

    public String getServiceHeaders() {
        return serviceHeaders;
    }

    public void setServiceHeaders(String serviceHeaders) {
        this.serviceHeaders = serviceHeaders;
    }

    public String getServiceURL() {
        return serviceURL;
    }

    public void setServiceURL(String serviceURL) {
        this.serviceURL = serviceURL;
    }

    public String getServiceMethod() {
        return serviceMethod;
    }

    public void setServiceMethod(String serviceMethod) {
        this.serviceMethod = serviceMethod;
    }

    @Override
    public Error getError() {
        return error;
    }

    public void setError(Error error) {
        this.error = error;
    }

    protected String nodeType = "httpClient";

    @Override
    public String toType() {
        return nodeType;
    }

}
