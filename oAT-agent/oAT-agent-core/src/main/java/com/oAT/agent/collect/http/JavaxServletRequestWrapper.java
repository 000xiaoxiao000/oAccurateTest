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
import java.io.*;
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
        this.headers = new HashMap<String, String>();
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        Enumeration headerNames = httpRequest.getHeaderNames();
        while (headerNames != null && headerNames.hasMoreElements()) {
            String name = String.valueOf(headerNames.nextElement());
            headers.put(name, httpRequest.getHeader(name));
        }
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
                    "UTF-8");
            if (s.length() > 8192) {
                return s.substring(0, 8192) + "...";
            }
            return s;
        } catch (Throwable e) {
            String s = com.oAT.agent.common.StringUtils.newStringUtf8(cachedBody);
            if (s.length() > 8192) {
                return s.substring(0, 8192) + "...";
            }
            return s;
        }
    }

    @Override
    public ServletInputStream getInputStream() throws IOException {
        String contentType = getContentType();
        if (contentType != null && contentType.toLowerCase().startsWith("multipart/")) {
            return super.getInputStream();
        }
        if (!bodyRead) {
            cacheRequestBody();
        }
        if (cachedBody == null) {
            cachedBody = new byte[0];
        }
        return new CachedBodyServletInputStream(cachedBody);
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
        if (cachedBody == null) {
            cachedBody = new byte[0];
        }
        return new BufferedReader(new InputStreamReader(new ByteArrayInputStream(cachedBody),
                getCharacterEncoding() != null ? getCharacterEncoding() : "UTF-8"));
    }

    private synchronized void cacheRequestBody() {
        if (bodyRead) {
            return;
        }
        try {
            String contentType = getContentType();
            if (contentType != null && contentType.toLowerCase().startsWith("multipart/")) {
                cachedBody = "[multipart omitted]".getBytes("UTF-8");
                return;
            }

            try {
                if (contentType != null && contentType.toLowerCase().startsWith("application/x-www-form-urlencoded")) {
                    super.getParameterMap();
                }
            } catch (Throwable ignore) {
            }

            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            InputStream inputStream = null;
            try {
                inputStream = super.getInputStream();
                byte[] buffer = new byte[8192];
                int bytesRead;
                while ((bytesRead = inputStream.read(buffer)) != -1) {
                    outputStream.write(buffer, 0, bytesRead);
                }
            } catch (IOException e) {
                logger.error("[Agent-EXCError]InputStream closed or IO error, fallback to empty body. " + e.getMessage());
                cachedBody = new byte[0];
            } catch (Throwable e) {
                logger.error("[Agent-EXCError]Unexpected error, fallback to empty body. " + StackTraceFormatter.formatExceptionWithAgentMark(e));
                cachedBody = new byte[0];
            } finally {
                closeQuietly(inputStream);
            }
            if (cachedBody == null) {
                cachedBody = outputStream.toByteArray();
            }
        } catch (Throwable e) {
            logger.error("[Agent-EXCError]Error caching request body, fallback to empty body. "
                    + StackTraceFormatter.formatExceptionWithAgentMark(e));
            cachedBody = new byte[0];
        } finally {
            bodyRead = true;
        }
    }

    @Override
    public Map<String, String[]> getParameterMap() {
        return super.getParameterMap();
    }

    @Override
    public String getHeader(String name) {
        String value = headers.get(name);
        return value == null ? super.getHeader(name) : value;
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

    private static void closeQuietly(Closeable closeable) {
        if (closeable == null) {
            return;
        }
        try {
            closeable.close();
        } catch (IOException ignored) {
        }
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
