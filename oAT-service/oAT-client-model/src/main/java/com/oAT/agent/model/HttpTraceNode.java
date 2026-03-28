package com.oAT.agent.model;

import java.io.Serializable;

public class HttpTraceNode extends TraceNode implements CodeNodeBean, StatementError, Serializable {
    private static final long serialVersionUID = -7156032079009497957L;

    private String clientIp;
    private String serverIp;
    private String serverPort;
    private String parentTraceId;
    //请求参数名称
    private String[] requestParamNames;
    //请求参数值
    private String[] requestParamValues;
    //请求体
    private String requestBody;
    //请求方法
    private String requestMethod;
    //请求路径
    private String requestUrl;
    //是否为异步请求
    private Boolean ajax;
    //请求头
    private RequestHeader requestHeader;
    //返回状态码
    private String responseCode;
    //返回类型html，json取值HttpTraceNode.ResponseType
    private String responseType;

    private String responseContent;

    private Error error;
    //完成的代码堆栈
    private StackNodeVo[] codeNodes;
    //完成的日志信息
    private String log;

    public String getRequestBody() {
        return requestBody;
    }

    public void setRequestBody(String requestBody) {
        this.requestBody = requestBody;
    }

    public String getClientIp() {
        return clientIp;
    }

    public void setClientIp(String clientIp) {
        this.clientIp = clientIp;
    }

    public String getServerPort() {
        return serverPort;
    }

    public void setServerPort(String serverPort) {
        this.serverPort = serverPort;
    }

    public String getServerIp() {
        return serverIp;
    }

    public void setServerIp(String serverIp) {
        this.serverIp = serverIp;
    }

    public String getParentTraceId() {
        return parentTraceId;
    }

    public void setParentTraceId(String parentTraceId) {
        this.parentTraceId = parentTraceId;
    }

    public String[] getRequestParamNames() {
        return requestParamNames;
    }

    public void setRequestParamNames(String[] requestParamNames) {
        this.requestParamNames = requestParamNames;
    }

    public String[] getRequestParamValues() {
        return requestParamValues;
    }

    public void setRequestParamValues(String[] requestParamValues) {
        this.requestParamValues = requestParamValues;
    }

    public String getRequestMethod() {
        return requestMethod;
    }

    public void setRequestMethod(String requestMethod) {
        this.requestMethod = requestMethod;
    }

    public String getRequestUrl() {
        return requestUrl;
    }

    public void setRequestUrl(String requestUrl) {
        this.requestUrl = requestUrl;
    }

    public Boolean getAjax() {
        return ajax;
    }

    public void setAjax(Boolean ajax) {
        this.ajax = ajax;
    }

    public RequestHeader getRequestHeader() {
        return requestHeader;
    }

    public void setRequestHeader(RequestHeader requestHeader) {
        this.requestHeader = requestHeader;
    }

    public String getResponseCode() {
        return responseCode;
    }

    public void setResponseCode(String responseCode) {
        this.responseCode = responseCode;
    }

    public String getResponseType() {
        return responseType;
    }

    public void setResponseType(String responseType) {
        this.responseType = responseType;
    }

    public String getResponseContent() {
        return responseContent;
    }

    public void setResponseContent(String responseContent) {
        this.responseContent = responseContent;
    }

    @Override
    public String toType(){
        return "http";
    }

    @Override
    public Error getError() {
        return error;
    }

    public void setError(Error error) {
        this.error = error;
    }

    @Override
    public StackNodeVo[] getCodeNodes() {
        return codeNodes;
    }

    public void setCodeNodes(StackNodeVo[] codeNodes) {
        this.codeNodes = codeNodes;
    }

    public String getLog() {
        return log;
    }

    public void setLog(String log) {
        this.log = log;
    }

    public static class RequestHeader implements Serializable {
        private static final long serialVersionUID = -7156032079009497957L;

        //用户浏览器信息
        private String userAgent;
        //关联页
        private String referer;
        private String cookie;
        private String Authorization;
        //客户端标识
        private String userHeader;

        public String getUserAgent() {
            return userAgent;
        }

        public void setUserAgent(String userAgent) {
            this.userAgent = userAgent;
        }

        public String getReferer() {
            return referer;
        }

        public void setReferer(String referer) {
            this.referer = referer;
        }

        public String getCookie() {
            return cookie;
        }

        public void setCookie(String cookie) {
            this.cookie = cookie;
        }

        public String getAuthorization() {
            return Authorization;
        }

        public void setAuthorization(String authorization) {
            Authorization = authorization;
        }

        public String getUserHeader() {
            return userHeader;
        }

        public void setUserHeader(String userHeader) {
            this.userHeader = userHeader;
        }
    }

    public enum ResponseType {
        json, html
    }
}
