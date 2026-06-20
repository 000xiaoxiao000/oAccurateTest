package com.oAT.agent.collect.http;

import com.oAT.agent.common.StackTraceFormatter;
import com.oAT.agent.common.logger.Log;
import com.oAT.agent.common.logger.LogFactory;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

public class HttpClientRequestAdapterV3 {
    private final static Log logger = LogFactory.getLog(HttpClientRequestAdapterV3.class);
    private Object hostconfig = null;
    private Object HttpMethod = null;
    private Object state = null;//unused right now

    private Method _getMethod = null;
    private Method _getURL = null;
    private Method _getRequestHeaders = null;
    private Method _getResponseHeaders = null;
    private Method _setRequestHeaders = null;

    public HttpClientRequestAdapterV3(Object[] target) {
        if(target == null || target.length < 3){
            logger.error("ap httpclient request adapter params: " + (target == null ? -1 : target.length));
            return;
        }
        this.hostconfig = target[0];
        this.HttpMethod = target[1];
        this.state = target[2];

        try {
            _getMethod = HttpMethod.getClass().getMethod("getName");
            _getURL = HttpMethod.getClass().getMethod("getURI");
            _getRequestHeaders = HttpMethod.getClass().getMethod("getRequestHeaders");
            _getResponseHeaders = HttpMethod.getClass().getMethod("getResponseHeaders");
            _getResponseHeaders = HttpMethod.getClass().getMethod("getResponseHeaders");
            _setRequestHeaders = HttpMethod.getClass().getMethod("setRequestHeader", String.class, String.class);
        } catch (NoSuchMethodException e) {
            logger.error("[Agent-EXCError]HttpClientRequestAdapter HttpClientRequestAdapter error: "+ StackTraceFormatter.formatExceptionWithAgentMark(e));
        } catch (SecurityException e) {
            logger.error("[Agent-EXCError]HttpClientRequestAdapter HttpClientRequestAdapter error: "+ StackTraceFormatter.formatExceptionWithAgentMark(e));
        }
    }

    public void setHeaders(Map<String, String> headers){
        if(headers == null || headers.size() == 0 || _setRequestHeaders == null || HttpMethod == null){
            return;
        }
        try {
            for(Map.Entry<String, String> entry : headers.entrySet()){
                _setRequestHeaders.invoke(HttpMethod, entry.getKey(), entry.getValue());
            }
        } catch (Exception e) {
            logger.error("[Agent-EXCError]HttpClientRequestAdapter setHeaders error: "+ StackTraceFormatter.formatExceptionWithAgentMark(e));
        }
    }
    public String getMethod() {
        if(_getMethod == null || HttpMethod == null){
            return "";
        }
        String result = null;
        try {
            result = (String)_getMethod.invoke(HttpMethod);
        } catch (Exception e) {
            logger.error("[Agent-EXCError]HttpClientRequestAdapter getMethod error: "+ StackTraceFormatter.formatExceptionWithAgentMark(e));
        }
        return result != null ? result : "";
    }

    public String getURL() {
         if(_getURL == null || HttpMethod == null){
            return "";
        }
        String result = "";
        try {
            result = _getURL.invoke(HttpMethod).toString();
        } catch (Exception e) {
            logger.error("[Agent-EXCError]HttpClientRequestAdapter getURL error: "+ StackTraceFormatter.formatExceptionWithAgentMark(e));
        }

        return result != null ? result : "";
    }

    public Map<String, String> getRequestHeaders() {
        return getHeaders(_getRequestHeaders);
    }
    public Map<String, String> getResponseHeaders() {
        return getHeaders(_getResponseHeaders);
    }

    protected Map<String, String> getHeaders(Method targetHeader){
        Map<String, String> headers = new HashMap();
        if(targetHeader == null || HttpMethod == null){
            return headers;
        }
        Object[] result = null;
        try {
            result = (Object[])targetHeader.invoke(HttpMethod);
            if(result == null){
                return headers;
            }

            for(Object header : result){
                Method _getName = header.getClass().getMethod("getName");
                Method _getValue = header.getClass().getMethod("getValue");
                String name = (String)_getName.invoke(header);
                String value = (String)_getValue.invoke(header);
                if(name != null && value != null){
                    headers.put(name, value);
                }
            }
        } catch (Exception e) {
            logger.error("[Agent-EXCError]HttpClientRequestAdapter getHeaders error: "+ StackTraceFormatter.formatExceptionWithAgentMark(e));
        }
        return headers;
    }
}
