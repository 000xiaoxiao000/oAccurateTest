package com.oAT.agent.collect.http;

import javax.servlet.ServletOutputStream;
import javax.servlet.ServletResponse;
import javax.servlet.WriteListener;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpServletResponseWrapper;
import java.io.*;
import java.util.*;

public class JavaxServletResponseWrapper extends HttpServletResponseWrapper {
    private static final int MAX_CAPTURE_SIZE = 8 * 1024 * 1024; // 8MB
    private final HttpServletResponse original;
    private final ByteArrayOutputStream outputCopy = new ByteArrayOutputStream();
    private ServletOutputStream teeOutputStream;
    private PrintWriter teeWriter;
    private final Map<String, String> headers = new HashMap();
    private int status = 200;
    private boolean usingWriter = false;
    private boolean usingOutputStream = false;
    private String specialResponseBody = null;
    private final Object bufferLock = new Object();

    public JavaxServletResponseWrapper(ServletResponse response) {
        super((HttpServletResponse) response);
        this.original = (HttpServletResponse) response;
    }

    @Override
    public ServletOutputStream getOutputStream() throws IOException {
        if (usingWriter) {
            throw new IllegalStateException("getWriter() has already been called on this response.");
        }
        usingOutputStream = true;
        if (teeOutputStream == null) {
            teeOutputStream = new TeeOutputStream(original.getOutputStream(), outputCopy, MAX_CAPTURE_SIZE);
        }
        return teeOutputStream;
    }

    @Override
    public PrintWriter getWriter() throws IOException {
        if (usingOutputStream) {
            throw new IllegalStateException("getOutputStream() has already been called on this response.");
        }
        usingWriter = true;
        if (teeWriter == null) {
            final Writer originalWriter = original.getWriter();
            final OutputStreamWriter outputCopyWriter = new OutputStreamWriter(outputCopy, getCharacterEncoding());
            teeWriter = new PrintWriter(new Writer() {
                @Override
                public void write(char[] cbuf, int off, int len) throws IOException {
                    if (outputCopy.size() < MAX_CAPTURE_SIZE) {
                        outputCopyWriter.write(cbuf, off, len);
                        outputCopyWriter.flush();
                    }
                    originalWriter.write(cbuf, off, len);
                    originalWriter.flush();
                }
                @Override
                public void flush() throws IOException {
                    outputCopyWriter.flush();
                    originalWriter.flush();
                }
                @Override
                public void close() throws IOException {
                    outputCopyWriter.close();
                    originalWriter.close();
                }
            }, true);
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

    public Map<String, String> getHeaders() {
        return headers;
    }

    @Override
    public int getStatus() {
        return status;
    }

    public String getBodyString() {
        if (specialResponseBody != null) {
            return specialResponseBody;
        }
        try {
            if (outputCopy.size() > 0) {
                String s = outputCopy.toString(getCharacterEncoding() != null ? getCharacterEncoding() : "UTF-8");
                if (s.length() > 8192) {
                    return s.substring(0, 8192) + "...";
                }
                return s;
            }
        } catch (UnsupportedEncodingException e) {
            return outputCopy.toString();
        }
        return "";
    }

    private static class TeeOutputStream extends ServletOutputStream {
        private final ServletOutputStream original;
        private final OutputStream branch;
        private int branchSize = 0;
        private final int maxBranchSize;

        public TeeOutputStream(ServletOutputStream original, OutputStream branch, int maxBranchSize) {
            this.original = original;
            this.branch = branch;
            this.maxBranchSize = maxBranchSize;
        }

        @Override
        public void write(int b) throws IOException {
            original.write(b);
            if (branchSize < maxBranchSize) {
                branch.write(b);
                branchSize++;
            }
        }

        @Override
        public void write(byte[] b, int off, int len) throws IOException {
            original.write(b, off, len);
            if (branchSize < maxBranchSize) {
                int toWrite = Math.min(len, maxBranchSize - branchSize);
                if (toWrite > 0) {
                    branch.write(b, off, toWrite);
                    branchSize += toWrite;
                }
            }
        }

        @Override
        public void flush() throws IOException {
            original.flush();
            branch.flush();
        }

        @Override
        public void close() throws IOException {
            original.close();
            branch.close();
        }

        @Override
        public boolean isReady() {
            return original.isReady();
        }

        @Override
        public void setWriteListener(WriteListener writeListener) {
            original.setWriteListener(writeListener);
        }
    }
}
