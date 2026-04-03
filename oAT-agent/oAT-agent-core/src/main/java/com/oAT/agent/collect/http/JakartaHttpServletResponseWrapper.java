package com.oAT.agent.collect.http;

import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.WriteListener;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletResponseWrapper;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

public class JakartaHttpServletResponseWrapper extends HttpServletResponseWrapper {
    private static final int MAX_CAPTURE_SIZE = 8 * 1024 * 1024; // 8MB
    private final HttpServletResponse original;
    private final ByteArrayOutputStream outputCopy = new ByteArrayOutputStream();
    private ServletOutputStream teeOutputStream;
    private PrintWriter teeWriter;
    private final Map<String, String> headers = new HashMap<>();
    private int status = 200;
    private boolean usingWriter = false;
    private boolean usingOutputStream = false;
    private String specialResponseBody = null;
    private final Object bufferLock = new Object();

    public JakartaHttpServletResponseWrapper(HttpServletResponse response) {
        super(response);
        this.original = response;
    }

    @Override
    public ServletOutputStream getOutputStream() throws IOException {
        if (usingWriter) {
            throw new IllegalStateException("getWriter() has already been called on this response.");
        }
        usingOutputStream = true;
        if (teeOutputStream == null) {
            teeOutputStream = new TeeOutputStream(original.getOutputStream(), outputCopy);
        }
        return teeOutputStream;
    }

    @Override
    public PrintWriter getWriter() throws IOException {
        if (usingOutputStream) {
            throw new IllegalStateException("[Agent-EXCError]getOutputStream() has already beenCalled on this response.");
        }
        usingWriter = true;
        if (teeWriter == null) {
            teeWriter = new PrintWriter(new OutputStreamWriter(outputCopy, getCharacterEncoding()), true) {
                @Override
                public void write(char[] buf, int off, int len) {
                    super.write(buf, off, len);
                    try {
                        original.getWriter().write(buf, off, len);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                }
                @Override
                public void write(int c) {
                    super.write(c);
                    try {
                        original.getWriter().write(c);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                }
                @Override
                public void write(String s, int off, int len) {
                    super.write(s, off, len);
                    try {
                        original.getWriter().write(s, off, len);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                }
                @Override
                public void flush() {
                    super.flush();
                    try {
                        original.getWriter().flush();
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                }
                @Override
                public void close() {
                    super.close();
                    try {
                        original.getWriter().close();
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                }
            };
        }
        return teeWriter;
    }

    @Override
    public void setHeader(String name, String value) {
        headers.put(name, value);
        original.setHeader(name, value);
    }

    @Override
    public void addHeader(String name, String value) {
        headers.put(name, headers.containsKey(name) ? headers.get(name) + "," + value : value);
        original.addHeader(name, value);
    }

    @Override
    public void setStatus(int sc) {
        this.status = sc;
        original.setStatus(sc);
    }

    @Override
    public void sendError(int sc) throws IOException {
        this.status = sc;
        specialResponseBody = "sendError: " + sc;
        original.sendError(sc);
    }

    @Override
    public void sendError(int sc, String msg) throws IOException {
        this.status = sc;
        specialResponseBody = "sendError: " + sc + (msg != null ? (", " + msg) : "");
        original.sendError(sc, msg);
    }

    @Override
    public void sendRedirect(String location) throws IOException {
        this.status = HttpServletResponse.SC_FOUND;
        specialResponseBody = "sendRedirect: " + location;
        original.sendRedirect(location);
    }

    @Override
    public void flushBuffer() throws IOException {
        synchronized (bufferLock) {
            if (teeWriter != null) {
                teeWriter.flush();
            } else if (teeOutputStream != null) {
                teeOutputStream.flush();
            }
            original.flushBuffer();
        }
    }

    @Override
    public Collection<String> getHeaderNames() {
        return original.getHeaderNames();
    }

    // 获取完整响应体
    public byte[] getResponseData() {
        synchronized (bufferLock) {
            return outputCopy.toByteArray();
        }
    }

    // 获取完整响应报文（含状态、头、体）
    public FullResponse getFullResponse() {
        FullResponse data = new FullResponse();
        data.setStatus(status);
        data.setHeaders(new HashMap<>(headers));
        data.setBody(getResponseData());
        return data;
    }

    public String getBodyString() {
        synchronized (bufferLock) {
            if (outputCopy.size() > 0) {
                try {
                    String s = outputCopy.toString(getCharacterEncoding() != null ? getCharacterEncoding() : "UTF-8");
                    if (s.length() > 8192) {
                        return s.substring(0, 8192) + "...";
                    }
                    return s;
                } catch (Throwable e) {
                    String s = outputCopy.toString();
                    if (s.length() > 8192) {
                        return s.substring(0, 8192) + "...";
                    }
                    return s;
                }
            }
            if (specialResponseBody != null) {
                return specialResponseBody;
            }
            return "";
        }
    }

    // 支持大文件截断
    private class TeeOutputStream extends ServletOutputStream {
        private final ServletOutputStream out1;
        private final ByteArrayOutputStream out2;

        public TeeOutputStream(ServletOutputStream out1, ByteArrayOutputStream out2) {
            this.out1 = out1;
            this.out2 = out2;
        }

        @Override
        public void write(int b) throws IOException {
            if (out2.size() < MAX_CAPTURE_SIZE) {
                out2.write(b);
            }
            out1.write(b);
        }

        @Override
        public void write(byte[] b, int off, int len) throws IOException {
            if (out2.size() < MAX_CAPTURE_SIZE) {
                int remain = MAX_CAPTURE_SIZE - out2.size();
                out2.write(b, off, Math.min(len, remain));
            }
            out1.write(b, off, len);
        }

        @Override
        public boolean isReady() {
            return out1.isReady();
        }

        @Override
        public void setWriteListener(WriteListener writeListener) {
            out1.setWriteListener(writeListener);
        }
    }

    // 响应报文结构体
    public static class FullResponse {
        private int status;
        private Map<String, String> headers;
        private byte[] body;

        public int getStatus() { return status; }
        public void setStatus(int status) { this.status = status; }
        public Map<String, String> getHeaders() { return headers; }
        public void setHeaders(Map<String, String> headers) { this.headers = headers; }
        public byte[] getBody() { return body; }
        public void setBody(byte[] body) { this.body = body; }
    }
}
