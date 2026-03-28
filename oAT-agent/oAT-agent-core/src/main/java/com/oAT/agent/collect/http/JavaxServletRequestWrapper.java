package com.oAT.agent.collect.http;

import com.oAT.agent.common.StackTraceFormatter;
import com.oAT.agent.common.logger.Log;
import com.oAT.agent.common.logger.LogFactory;

import javax.servlet.*;
import javax.servlet.ReadListener;
import javax.servlet.ServletInputStream;
import javax.servlet.ServletRequest;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;
import javax.servlet.http.Part;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class JavaxServletRequestWrapper extends HttpServletRequestWrapper {
    private final static Log logger = LogFactory.getLog(JavaxServletRequestWrapper.class);

    private byte[] cachedBody;
    private boolean bodyRead = false;
    private final Map<String, String> headers;
    private ServletResponse response;

    public JavaxServletRequestWrapper(ServletRequest request, ServletResponse response) {
        this(request);
        this.response = response;
    }

    public JavaxServletRequestWrapper(ServletRequest request) {
        super((HttpServletRequest) request);
        logger.debug("[Agent-JavaxServletRequestWrapper]" + request.getClass().getName());
        this.headers = new HashMap<>();
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        Collections.list(httpRequest.getHeaderNames())
                .forEach(name -> headers.put(name, httpRequest.getHeader(name)));
    }

    /**
     * 获取请求体内容
     */
    public String getRequestBody() {
        if (!bodyRead) {
            cacheRequestBody();
        }
        if (cachedBody == null) {
            return "";
        }
        try {
            String s = new String(cachedBody, getCharacterEncoding() != null ? getCharacterEncoding() :
                    StandardCharsets.UTF_8.name());
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
        return new BufferedReader(new InputStreamReader(new ByteArrayInputStream(cachedBody),
                getCharacterEncoding() != null ? getCharacterEncoding() : StandardCharsets.UTF_8.name()));
    }

    private synchronized void cacheRequestBody() {
        if (bodyRead) {
            return;
        }
        try {
            // 尝试触发 container 解析 parameters，防止后续读取流导致 parameters 丢失
            try {
                String contentType = getContentType();
                if (contentType != null && contentType.toLowerCase().startsWith("application/x-www-form-urlencoded")) {
                    super.getParameterMap();
                }
            } catch (Throwable ignore) {
            }

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
                                logger.error("[Agent-EXCError]Read part stream error, fallback to skip part. "
                                        + StackTraceFormatter.formatExceptionWithAgentMark(e));
                            }
                        }
                    }
                } catch (Throwable e) {
                    // 降级，multipart 解析异常
                    logger.error("[Agent-EXCError]Get parts error, fallback to empty body. " + StackTraceFormatter.formatExceptionWithAgentMark(e));
                    cachedBody = new byte[0];
                }
            } else {
                try {
                    InputStream inputStream = super.getInputStream();
                    byte[] buffer = new byte[8192];
                    int bytesRead;
                    while ((bytesRead = inputStream.read(buffer)) != -1) {
                        outputStream.write(buffer, 0, bytesRead);
                    }
                } catch (IOException e) {
                    // 处理 Stream closed 或其他 IO 异常，降级为空 body
                    logger.error("[Agent-EXCError]InputStream closed or IO error, fallback to empty body. " + e.getMessage());
                    cachedBody = new byte[0];
                } catch (Throwable e) {
                    // 其他异常也降级
                    logger.error("[Agent-EXCError]Unexpected error, fallback to empty body. " + StackTraceFormatter.formatExceptionWithAgentMark(e));
                    cachedBody = new byte[0];
                }
            }
            // 如果 cachedBody 还未赋值，正常赋值
            if (cachedBody == null) {
                cachedBody = outputStream.toByteArray();
            }
        } catch (Throwable e) {
            // 任何异常都不影响主流程
            logger.error("[Agent-EXCError]Error caching request body, fallback to empty body. "
                    + StackTraceFormatter.formatExceptionWithAgentMark(e));
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
    public AsyncContext startAsync() throws IllegalStateException {
        if (response != null) {
            return super.startAsync(this, response);
        }
        return super.startAsync();
    }

    @Override
    public String getContentType() {
        return super.getContentType();
    }

    @Override
    public int getContentLength() {
        return super.getContentLength();
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
