package com.oAT.agent.collect.http;

import com.oAT.agent.common.StackTraceFormatter;
import com.oAT.agent.common.logger.Log;
import com.oAT.agent.common.logger.LogFactory;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class JakartaHttpServletRequestWrapper extends HttpServletRequestWrapper {
    private final static Log logger = LogFactory.getLog(JakartaHttpServletRequestWrapper.class);

    private byte[] cachedBody;
    private boolean bodyRead = false;
    private final Map<String, String> headers;

    public JakartaHttpServletRequestWrapper(HttpServletRequest request) {
        super(request);
        this.headers = new HashMap<>();
        Collections.list(request.getHeaderNames())
                .forEach(name -> headers.put(name, request.getHeader(name)));
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
            String s = new String(cachedBody, getCharacterEncoding() != null ? getCharacterEncoding() : StandardCharsets.UTF_8.name());
            if (s.length() > 8192) {
                return s.substring(0, 8192) + "...";
            }
            return s;
        } catch (Throwable e) {
            String s = new String(cachedBody, StandardCharsets.UTF_8);
            if (s.length() > 8192) {
                return s.substring(0, 8192) + "...";
            }
            return s;
        }
    }

    @Override
    public jakarta.servlet.ServletInputStream getInputStream() throws IOException {
        String contentType = getContentType();
        if (contentType != null && contentType.toLowerCase().startsWith("multipart/")) {
            return super.getInputStream();
        }
        if (!bodyRead) {
            cacheRequestBody();
        }
        // 防御性处理，防止 cachedBody 为 null
        if (cachedBody == null) {
            cachedBody = new byte[0];
        }
        return new JakartaHttpServletRequestWrapper.CachedBodyServletInputStream(cachedBody);
    }

    @Override
    public BufferedReader getReader() throws IOException {
        String contentType = getContentType();
        if (contentType != null && contentType.toLowerCase().startsWith("multipart/")) {
            return super.getReader();
        }
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
            String contentType = getContentType();
            if (contentType != null && contentType.toLowerCase().startsWith("multipart/")) {
                cachedBody = "[multipart omitted]".getBytes(StandardCharsets.UTF_8);
                return;
            }

            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
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
        return headers.getOrDefault(name, super.getHeader(name));
    }

    @Override
    public String getContentType() {
        return headers.getOrDefault("Content-Type", super.getContentType());
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
