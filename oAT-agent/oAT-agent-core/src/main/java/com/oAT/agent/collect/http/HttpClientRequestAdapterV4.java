package com.oAT.agent.collect.http;

import com.oAT.agent.common.StackTraceFormatter;
import com.oAT.agent.common.logger.Log;
import com.oAT.agent.common.logger.LogFactory;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;


public class HttpClientRequestAdapterV4 {
    private final static Log logger = LogFactory.getLog(HttpClientRequestAdapterV4.class);
    private final Object httpHost;
    private final Object httpRequest;
    private final Object httpContext;
    private Object requestLine;

    public HttpClientRequestAdapterV4(Object[] target) {
        if (target == null || target.length < 3) {
            logger.info("------> target is null or length < 3: " + (target == null ? -1 : target.length));
            httpHost = null;
            httpRequest = null;
            httpContext = null;
            return;
        }
        httpHost = target[0];
        httpRequest = target[1];
        httpContext = target[2];

        if (httpRequest == null) {
            requestLine = null;
            return;
        }
        try {
            Method _getRequestLine = httpRequest.getClass().getMethod("getRequestLine");
            requestLine = _getRequestLine.invoke(httpRequest);
        } catch (Exception e) {
            logger.error("[Agent-EXCError]HttpClientRequestAdapter getRequestLine error: " + StackTraceFormatter.formatExceptionWithAgentMark(e));
            requestLine = null;
        }
    }

    public String getMethod() {
        if (requestLine == null) {
            return "";
        }
        try {
            Method _getMethod = requestLine.getClass().getMethod("getMethod");
            return (String) _getMethod.invoke(requestLine);
        } catch (Exception e) {
            logger.error("[Agent-EXCError]HttpClientRequestAdapter getMethod error: " + StackTraceFormatter.formatExceptionWithAgentMark(e));
            return "";
        }
    }

    public String getURL() {
        String result = "";
        try {
            if (httpRequest != null) {
                // Try getURI (HttpUriRequest)
                try {
                    Method _getUri = httpRequest.getClass().getMethod("getURI");
                    Object uriObj = _getUri.invoke(httpRequest);
                    if (uriObj != null) {
                        result = uriObj.toString();
                        if (!result.isEmpty()) {
                            return result;
                        }
                    }
                } catch (NoSuchMethodException nsme) {
                    // Not HttpUriRequest, try getRequestLine.getUri
                    if (requestLine != null) {
                        try {
                            Method _getUri = requestLine.getClass().getMethod("getUri");
                            Object uriObj = _getUri.invoke(requestLine);
                            if (uriObj != null) {
                                result = uriObj.toString();
                                if (!result.isEmpty()) {
                                    return result;
                                }
                            }
                        } catch (Exception ignore) {}
                    }
                }
                // Fallback: try httpHost.toURI
                if (httpHost != null) {
                    try {
                        Method _toURI = httpHost.getClass().getMethod("toURI");
                        Object hostUriObj = _toURI.invoke(httpHost);
                        if (hostUriObj != null) {
                            result = hostUriObj.toString();
                        }
                    } catch (Exception ignore) {}
                }
                return result;
            }
        } catch (Exception e) {
            logger.error("[Agent-EXCError]HttpClientRequestAdapter getURL error: " + StackTraceFormatter.formatExceptionWithAgentMark(e));
        }
        return "";
    }

    public String getRequestBody() {
        if (httpRequest == null) {
            return null;
        }
        try {
            Method _getEntity = httpRequest.getClass().getMethod("getEntity");
            Object entity = _getEntity.invoke(httpRequest);
            if (entity == null) {
                return null;
            }
            Method _isRepeatable = entity.getClass().getMethod("isRepeatable");
            Object repeatable = _isRepeatable.invoke(entity);
            if (!Boolean.TRUE.equals(repeatable)) {
                return "[non-repeatable entity omitted]";
            }
            Method _getContent = entity.getClass().getMethod("getContent");
            Object content = _getContent.invoke(entity);
            if (!(content instanceof InputStream)) {
                return null;
            }
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            try (InputStream inputStream = (InputStream) content) {
                byte[] buffer = new byte[8192];
                int bytesRead;
                while ((bytesRead = inputStream.read(buffer)) != -1 && outputStream.size() < 8192) {
                    int writable = Math.min(bytesRead, 8192 - outputStream.size());
                    outputStream.write(buffer, 0, writable);
                }
            }
            String body = new String(outputStream.toByteArray(), StandardCharsets.UTF_8);
            if (body.length() >= 8192) {
                return body + "...";
            }
            return body;
        } catch (NoSuchMethodException e) {
            return null;
        } catch (Exception e) {
            logger.error("[Agent-EXCError]HttpClientRequestAdapter getRequestBody error: " + StackTraceFormatter.formatExceptionWithAgentMark(e));
            return null;
        }
    }

    public Map<String, String> getRequestHeaders() {
        Map<String, String> headers = new HashMap<>();
        if (httpRequest == null) {
            return headers;
        }
        try {
            Method _getAllHeaders = httpRequest.getClass().getMethod("getAllHeaders");
            Object[] result = (Object[]) _getAllHeaders.invoke(httpRequest);
            if (result == null) {
                return headers;
            }
            for (Object header : result) {
                Method _getName = header.getClass().getMethod("getName");
                Method _getValue = header.getClass().getMethod("getValue");
                String name = (String) _getName.invoke(header);
                String value = (String) _getValue.invoke(header);
                if (name != null && value != null) {
                    headers.put(name, value);
                }
            }
        } catch (Exception e) {
            logger.error("[Agent-EXCError]HttpClientRequestAdapter getHeaders error: " + StackTraceFormatter.formatExceptionWithAgentMark(e));
        }
        return headers;
    }

    public void setHeaders(Map<String, String> headers) {
        if (headers == null || headers.isEmpty() || httpRequest == null) {
            return;
        }
        try {
            Method httpMethod = httpRequest.getClass().getMethod("setHeader", String.class, String.class);
            for (Map.Entry<String, String> entry : headers.entrySet()) {
                httpMethod.invoke(httpRequest, entry.getKey(), entry.getValue());
            }
        } catch (Exception e) {
            logger.error("[Agent-EXCError]HttpClientRequestAdapter setHeaders error: " + StackTraceFormatter.formatExceptionWithAgentMark(e));
        }
    }

    public Object getHttpHost() {
        return httpHost;
    }
    public Object getHttpRequest() {
        return httpRequest;
    }
    public Object getHttpContext() {
        return httpContext;
    }
}
