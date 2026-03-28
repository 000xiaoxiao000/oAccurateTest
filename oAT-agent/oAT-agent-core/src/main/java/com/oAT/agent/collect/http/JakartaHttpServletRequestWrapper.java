package com.oAT.agent.collect.http;

import com.oAT.agent.common.StackTraceFormatter;
import com.oAT.agent.common.logger.Log;
import com.oAT.agent.common.logger.LogFactory;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.Part;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

public class JakartaHttpServletRequestWrapper extends HttpServletRequestWrapper {
    private final static Log logger = LogFactory.getLog(JakartaHttpServletRequestWrapper.class);

    private byte[] cachedBody;
    private boolean bodyRead = false;
    private final Map<String, String> headers;

    public JakartaHttpServletRequestWrapper(HttpServletRequest request) {
        super(request);
        this.headers = new HashMap<String, String>();
        java.util.Enumeration<String> headerNames = request.getHeaderNames();
        while (headerNames.hasMoreElements()) {
            String name = headerNames.nextElement();
            headers.put(name, request.getHeader(name));
        }
        // 不在构造时直接cache，延迟到真正需要时
    }

    public String getRequestBody() {
        if (!bodyRead) {
            cacheRequestBody();
        }
        if (cachedBody == null) {
            return "";
        }
        try {
            String s = new String(cachedBody, getCharacterEncoding() != null ? getCharacterEncoding() : "UTF-8");
            if (s.length() > 8192) {
                return s.substring(0, 8192) + "...";
            }
            return s;
        } catch (Throwable e) {
            String s = "";
            try {
                s = new String(cachedBody, "UTF-8");
            } catch (UnsupportedEncodingException ignore) {}
            if (s.length() > 8192) {
                return s.substring(0, 8192) + "...";
            }
            return s;
        }
    }

    @Override
    public ServletInputStream getInputStream() {
        if (!bodyRead) {
            cacheRequestBody();
        }
        // 防御性处理，防止 cachedBody 为 null
        if (cachedBody == null) {
            cachedBody = new byte[0];
        }
        return new CachedBodyServletInputStream(cachedBody);
    }

    @Override
    public BufferedReader getReader() throws IOException {
        if (!bodyRead) {
            cacheRequestBody();
        }
        // 防御性处理，防止 cachedBody 为 null
        if (cachedBody == null) {
            cachedBody = new byte[0];
        }
        return new BufferedReader(new InputStreamReader(
                new ByteArrayInputStream(cachedBody),
                getCharacterEncoding() != null ? getCharacterEncoding() : StandardCharsets.UTF_8.name()
        ));
    }

    private synchronized void cacheRequestBody() {
        if (bodyRead) {
            return;
        }
        try {
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            String contentType = getContentType();
            if (contentType != null && contentType.startsWith("multipart/")) {
                try {
                    for (Part part : getParts()) {
                        if (part.getSubmittedFileName() != null) {
                            try (InputStream partStream = part.getInputStream()) {
                                byte[] buffer = new byte[8192];
                                int bytesRead;
                                while ((bytesRead = partStream.read(buffer)) != -1) {
                                    outputStream.write(buffer, 0, bytesRead);
                                }
                            } catch (Throwable e) {
                                // 降级，单个 part 读取异常不影响整体
                                logger.warn("[Agent-cacheRequestBody]Read part stream error, fallback to skip part" + StackTraceFormatter.formatExceptionWithAgentMark(e));
                            }
                        }
                    }
                } catch (Throwable e) {
                    // 降级，multipart 解析异常
                    logger.warn("[Agent-cacheRequestBody]Get parts error, fallback to empty body" + StackTraceFormatter.formatExceptionWithAgentMark(e));
                    cachedBody = new byte[0];
                }
            } else {
                try (InputStream inputStream = super.getInputStream()) {
                    byte[] buffer = new byte[8192];
                    int bytesRead;
                    while ((bytesRead = inputStream.read(buffer)) != -1) {
                        outputStream.write(buffer, 0, bytesRead);
                    }
                } catch (IOException e) {
                    // 处理 Stream closed 或其他 IO 异常，降级为空 body
                    logger.warn("[Agent-cacheRequestBody]InputStream closed or IO error, fallback to empty body. " + e.getMessage());
                    cachedBody = new byte[0];
                } catch (Throwable e) {
                    // 其他异常也降级
                    logger.warn("[Agent-cacheRequestBody]Unexpected error, fallback to empty body" + StackTraceFormatter.formatExceptionWithAgentMark(e));
                    cachedBody = new byte[0];
                }
            }
            // 如果 cachedBody 还未赋值，正常赋值
            if (cachedBody == null) {
                cachedBody = outputStream.toByteArray();
            }
        } catch (Throwable e) {
            // 任何异常都不影响主流程
            logger.warn("[Agent-cacheRequestBody]Error caching request body, fallback to empty body" + StackTraceFormatter.formatExceptionWithAgentMark(e));
            cachedBody = new byte[0];
        } finally {
            bodyRead = true;
        }
    }

    @Override
    public String getHeader(String name) {
        String value = headers.get(name);
        return value != null ? value : super.getHeader(name);
    }

    @Override
    public String getContentType() {
        String value = headers.get("Content-Type");
        return value != null ? value : super.getContentType();
    }

    private static class CachedBodyServletInputStream extends ServletInputStream {
        private final ByteArrayInputStream inputStream;

        public CachedBodyServletInputStream(byte[] cachedBody) {
            this.inputStream = new ByteArrayInputStream(cachedBody);
        }

        @Override
        public int read() {
            return inputStream.read();
        }

        @Override
        public boolean isFinished() {
            return inputStream.available() == 0;
        }

        @Override
        public boolean isReady() {
            return true;
        }

        @Override
        public void setReadListener(ReadListener listener) {
            try {
                if (!isFinished()) {
                    listener.onDataAvailable();
                }
                listener.onAllDataRead();
            } catch (IOException e) {
                listener.onError(e);
            }
        }

        @Override
        public void close() throws IOException {
            inputStream.close();
            super.close();
        }
    }
}
