package com.oAT.agent.collect.feign;

import com.oAT.agent.common.ReflectUtil;
import com.oAT.agent.common.logger.Log;
import com.oAT.agent.common.logger.LogFactory;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

public class InvocationAdapter {
    private final static Log logger = LogFactory.getLog(InvocationAdapter.class);
    private final Object target;
//    private final Method _getRequestTemplate;
    private final Method _getMethod;
    private final Method _getURL;
    private final Method _getBody;
    private final Method _getHeaders;
    private final Field _headers;

    public InvocationAdapter(Object target) {
        this.target = target;
        try {
//            _getRequestTemplate = target.getClass().getMethod("requestTemplate");
            _getMethod = target.getClass().getMethod("method");
            _getURL = target.getClass().getMethod("url");
            _getBody = target.getClass().getMethod("body");
            _getHeaders = target.getClass().getMethod("headers");
            _headers = target.getClass().getDeclaredField("headers");
        } catch (NoSuchMethodException e) {
            throw new IllegalArgumentException("[Agent-EXCError]error: " + e.getMessage() + ". "
                    + "probable cause the target is not belong FeignHttpClient.FeignInvocation");
        } catch (NoSuchFieldException e) {
            throw new IllegalArgumentException("[Agent-EXCError]error: " + e.getMessage() + ". "
                    + "probable cause the target is not belong FeignHttpClient.FeignInvocation");
        }
    }

//    public Object getRequestTemplate() {
//        return ReflectUtil.invoker(_getRequestTemplate, target);
//    }

    public String getMethod() {
        Object result = ReflectUtil.invoker(_getMethod, target);
        return result != null ? result.toString() : "";
    }

    public String getURL() {
        Object result = ReflectUtil.invoker(_getURL, target);
        return result != null ? result.toString() : "";
    }

    public Map getHeaders() {
        return (Map) ReflectUtil.invoker(_getHeaders, target);
    }

    @SuppressWarnings("unchecked")
    public void setHeaders(Map map) {
        boolean old = _headers.isAccessible();
        try {
            _headers.setAccessible(true);
            Map<?,?> headerMap = new HashMap((Map) _headers.get(target));
            headerMap.putAll(map);
            _headers.set(target, headerMap);
        } catch (IllegalAccessException e) {
            logger.error("[Agent-setHeaders]error", e);
        } finally {
            _headers.setAccessible(old);
        }
    }

    public byte[] getBody() {
        return (byte[]) ReflectUtil.invoker(_getBody, target);
    }
}
