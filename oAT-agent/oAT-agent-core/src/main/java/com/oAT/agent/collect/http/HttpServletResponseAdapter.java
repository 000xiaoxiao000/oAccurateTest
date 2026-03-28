package com.oAT.agent.collect.http;

import java.lang.reflect.Method;

/**
 * 响应适配器
 */
public class HttpServletResponseAdapter {
    private final Object response;

    public HttpServletResponseAdapter(Object response) {
        this.response = response;
    }

    public int getStatus() {
        try {
            Method m = response.getClass().getMethod("getStatus");
            Object status = m.invoke(response);
            if (status instanceof Integer) {
                return (Integer) status;
            }
        } catch (Throwable e) {
            // ignore
        }
        return 200;
    }

    public String getResponseBody() {
        // 优先自定义Wrapper增强
        try {
            Method m = response.getClass().getMethod("getBodyString");
            Object body = m.invoke(response);
            if (body instanceof String) {
                return (String) body;
            }
        } catch (Throwable ignore) {}
        return null;
    }
}
