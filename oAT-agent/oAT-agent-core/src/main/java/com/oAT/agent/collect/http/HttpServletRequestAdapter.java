package com.oAT.agent.collect.http;

import com.oAT.agent.common.ReflectUtil;

import java.io.BufferedReader;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.Map;

/**
 * 请求适配器
 */
public class HttpServletRequestAdapter {
    private final Object request;

    public HttpServletRequestAdapter(Object request) {
        this.request = request;
    }

    public String getRequestURL() {
        try {
            Method m = request.getClass().getMethod("getRequestURL");
            Object urlObj = ReflectUtil.invoker(m, request);
            return urlObj != null ? urlObj.toString() : "";
        } catch (Throwable e) {
            return "";
        }
    }

    public String getRequestURI() {
        try {
            Method m = request.getClass().getMethod("getRequestURI");
            Object uri = ReflectUtil.invoker(m, request);
            return uri != null ? (String) uri : "";
        } catch (Throwable e) {
            return "";
        }
    }

    public Map<String, String[]> getParameterMap() {
        // 兼容Tomcat/Jetty/JBoss等标准Servlet
        try {
            // 标准Servlet
            Method m = request.getClass().getMethod("getParameterMap");
            Object map = ReflectUtil.invoker(m, request);
            if (map instanceof Map) {
                // 类型安全检查
                Map<?, ?> rawMap = (Map<?, ?>) map;
                boolean safe = true;
                for (Map.Entry<?, ?> entry : rawMap.entrySet()) {
                    if (!(entry.getKey() instanceof String) || !(entry.getValue() instanceof String[])) {
                        safe = false;
                        break;
                    }
                }
                if (safe) {
                    @SuppressWarnings("unchecked")
                    Map<String, String[]> casted = (Map<String, String[]>) map;
                    return casted;
                }
            }
        } catch (Throwable ignore) {}
        return Collections.emptyMap();
    }

    public String getInputStream() {
        // 优先自定义Wrapper增强
        try {
            Method m = request.getClass().getMethod("getRequestBody");
            Object body = ReflectUtil.invoker(m, request);
            if (body instanceof String) {
                String s = (String) body;
                if (s.length() > 8192) {
                    return s.substring(0, 8192) + "...";
                }
                return s;
            }
        } catch (Throwable ignore) {}
        // 常规ServletRequest读取，防止流多次读取导致异常
        try {
            Method m = request.getClass().getMethod("getInputStream");
            Object is = ReflectUtil.invoker(m, request);
            if (is instanceof InputStream) {
                InputStream inputStream = (InputStream) is;
                if (inputStream.markSupported()) {
                    inputStream.mark(4096);
                }
                byte[] buf = new byte[4096];
                int len = inputStream.read(buf);
                if (inputStream.markSupported()) {
                    inputStream.reset();
                }
                if (len > 0) {
                    String s = new String(buf, 0, len);
                    if (s.length() > 4096) {
                        return s.substring(0, 4096) + "...";
                    }
                    return s;
                }
            }
        } catch (Throwable ignore) {}
        try {
            Method m = request.getClass().getMethod("getReader");
            Object reader = ReflectUtil.invoker(m, request);
            if (reader instanceof BufferedReader) {
                BufferedReader br = (BufferedReader) reader;
                if (br.markSupported()) {
                    br.mark(4096);
                }
                StringBuilder sb = new StringBuilder();
                String line;
                int total = 0;
                while ((line = br.readLine()) != null && total < 4096) {
                    sb.append(line);
                    total += line.length();
                }
                if (br.markSupported()) {
                    br.reset();
                }
                String s = sb.toString();
                if (s.length() > 4096) {
                    return s.substring(0, 4096) + "...";
                }
                return s;
            }
        } catch (Throwable ignore) {}
        return "";
    }

    public String getMethod() {
        try {
            Method m = request.getClass().getMethod("getMethod");
            Object method = ReflectUtil.invoker(m, request);
            return method != null ? (String) method : "";
        } catch (Throwable e) {
            return "";
        }
    }

    public String getClientIp() {
        try {
            Method m = request.getClass().getMethod("getRemoteAddr");
            Object ip = ReflectUtil.invoker(m, request);
            return ip != null ? (String) ip : "";
        } catch (Throwable e) {
            return "";
        }
    }

    public int getServerPort() {
        try {
            Method m = request.getClass().getMethod("getServerPort");
            Object port = ReflectUtil.invoker(m, request);
            return port != null ? (int) port : -1;
        } catch (Throwable e) {
            return -1;
        }
    }

    public String getHeader(String name) {
        try {
            Method m = request.getClass().getMethod("getHeader", String.class);
            Object header = ReflectUtil.invoker(m, request, name);
            return header != null ? (String) header : null;
        } catch (Throwable e) {
            return null;
        }
    }

    public void setAttribute(String str, Object obj) {
        try {
            Method m = request.getClass().getMethod("setAttribute", String.class, Object.class);
            m.invoke(request, str, obj);
        } catch (Throwable ignored) {}
    }

    public String getAttribute(String str) {
        try {
            Method m = request.getClass().getMethod("getAttribute", String.class);
            Object obj = m.invoke(request, str);
            return obj != null ? (String) obj : null;
        } catch (Throwable e) {
            return null;
        }
    }

    public Object[] getCookies() {
        try {
            Method m = request.getClass().getMethod("getCookies");
            Object cookies = ReflectUtil.invoker(m, request);
            if (cookies != null && cookies.getClass().isArray()) {
                return (Object[]) cookies;
            }
        } catch (Throwable ignore) {}
        return null;
    }
}
