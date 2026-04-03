package com.oAT.agent.common;

import com.oAT.agent.common.logger.Log;
import com.oAT.agent.common.logger.LogFactory;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.*;

public class HttpClient {
    static Log logger = LogFactory.getLog(HttpClient.class);

    private static final int MAX_QUEUE_SIZE = Integer.getInteger("oAT.agent.http.max.queue.size", 8192);
    private static final int CORE_POOL_SIZE = Integer.getInteger("oAT.agent.http.core.pool.size", 20);
    private static final int MAX_POOL_SIZE = Integer.getInteger("oAT.agent.http.max.pool.size", 160);
    private static final long KEEP_ALIVE_TIME = Long.getLong("oAT.agent.http.keep.alive.time", 60L);
    private static final int BASE_TIMEOUT_MS = Integer.getInteger("oAT.agent.http.base.timeout.ms", 10000);
    private static final int PER_PARAM_TIMEOUT_MS = Integer.getInteger("oAT.agent.http.per.param.timeout.ms", 50);
    private static final int READ_TIMEOUT_EXTRA_MS = Integer.getInteger("oAT.agent.http.read.timeout.extra.ms", 5000);
    // 异步执行器，用于执行阻塞的 HTTP 网络操作，避免调用线程被长时间阻塞
    private static final ThreadPoolExecutor ASYNC_EXECUTOR;

    static {
        ASYNC_EXECUTOR = new ThreadPoolExecutor(
                CORE_POOL_SIZE,
                MAX_POOL_SIZE,
                KEEP_ALIVE_TIME, TimeUnit.SECONDS,
                new ArrayBlockingQueue<Runnable>(MAX_QUEUE_SIZE),
                new ThreadFactory() {
                    @Override
                    public Thread newThread(Runnable r) {
                        Thread t = new Thread(r, "oAT-http-async");
                        t.setDaemon(true);
                        try {
                            t.setPriority(Math.max(Thread.MIN_PRIORITY, Thread.NORM_PRIORITY - 1));
                        } catch (Throwable ignore) {
                        }
                        return t;
                    }
                },
                new DiscardWithLogPolicy()
        );

        // 允许核心线程超时销毁（保留4个，其他空闲回收）
        ASYNC_EXECUTOR.allowCoreThreadTimeOut(true);

        // 预启动核心线程（4个），减少首批请求延迟
        try {
            for (int i = 0; i < 3; i++) {
                ASYNC_EXECUTOR.prestartCoreThread();
            }
        } catch (Throwable ignore) {
        }

        // JVM 关闭时优雅关闭执行器，最多等待 5 秒
        Runtime.getRuntime().addShutdownHook(new Thread(new Runnable() {
            @Override
            public void run() {
                shutdownExecutor(ASYNC_EXECUTOR, "oAT-http-async");
            }
        }, "oAT-http-async-shutdown"));
    }

    /**
     * 获取执行器实例，供外部使用相同的线程池
     */
    public static ExecutorService getExecutor() {
        return ASYNC_EXECUTOR;
    }

    /**
     * 非阻塞的异步执行 HTTP POST 请求，返回 Future，调用方可选择等待或异步处理结果。
     * body 以 raw 字节方式发送，适用于大数据量（如 JSON），不受 form-urlencoded 编码限制。
     */
    public static Future<String> execHttpRawBody(final String url, final String contentType,
                                                 final byte[] body) {
        final FutureTask<String> task = new FutureTask<>(new Callable<String>() {
            @Override
            public String call() throws Exception {
                return executeHttpRequestRawBody(url, contentType, body);
            }
        });
        try {
            ASYNC_EXECUTOR.execute(task);
        } catch (final RejectedExecutionException rex) {
            logger.warn(String.format("[Agent-warn]异步HTTP任务被拒绝: url=%s, reason=%s", url, rex.getMessage()));
            return new FailedFuture(rex);
        } catch (final Throwable t) {
            return new FailedFuture(t);
        }
        return task;
    }

    /**
     * 非阻塞的异步执行 HTTP POST 请求，返回 Future，调用方可选择等待或异步处理结果。
     */
    public static Future<String> execHttp(final String url, final Map<String, String> params) {
        final FutureTask<String> task = new FutureTask<String>(new Callable<String>() {
            @Override
            public String call() throws Exception {
                return executeHttpRequestSync(url, params);
            }
        });
        try {
            ASYNC_EXECUTOR.execute(task);
        } catch (final RejectedExecutionException rex) {
            // 线程池队列满或已关闭
            logger.warn(String.format("[Agent-warn]异步HTTP任务被拒绝: url=%s, reason=%s", url, rex.getMessage()));
            return new FailedFuture(rex);
        } catch (final Throwable t) {
             return new FailedFuture(t);
        }
        return task;
    }

    public interface HttpCallback {
        void onComplete(String response, Throwable error);
    }

    public static void execHttp(final String url, final Map<String, String> params, final HttpCallback callback) {
        try {
            ASYNC_EXECUTOR.execute(new Runnable() {
                @Override
                public void run() {
                    try {
                        String resp = executeHttpRequestSync(url, params);
                        if (callback != null) {
                            callback.onComplete(resp, null);
                        }
                    } catch (Throwable t) {
                        if (callback != null) {
                            callback.onComplete(null, t);
                        }
                    }
                }
            });
        } catch (RejectedExecutionException rex) {
             logger.warn(String.format("[Agent-warn]异步HTTP任务被拒绝: url=%s, reason=%s", url, rex.getMessage()));
             if (callback != null) {
                 callback.onComplete(null, rex);
             }
        } catch (Throwable t) {
             if (callback != null) {
                 callback.onComplete(null, t);
             }
        }
    }

    private static String executeHttpRequestRawBody(String url, String contentType,
                                                     byte[] body) throws IOException {
        HttpURLConnection conn = null;
        try {
            URL realUrl = new URL(url);
            conn = (HttpURLConnection) realUrl.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", contentType);
            conn.setRequestProperty("accept", "*/*");
            conn.setRequestProperty("connection", "close");
            conn.setUseCaches(false);
            conn.setDoOutput(true);
            conn.setDoInput(true);
            int timeout = BASE_TIMEOUT_MS + READ_TIMEOUT_EXTRA_MS + 30000;
            conn.setConnectTimeout(timeout);
            conn.setReadTimeout(timeout);

            try (OutputStream out = new BufferedOutputStream(conn.getOutputStream())) {
                out.write(body);
                out.flush();
            }

            int responseCode = conn.getResponseCode();
            String resp = readResponse(conn, responseCode);
            if (responseCode >= 200 && responseCode < 400) {
                return resp;
            } else {
                throw new IOException("HTTP " + responseCode + ": " + resp);
            }
        } finally {
            if (conn != null) {
                try {
                    conn.disconnect();
                } catch (Throwable ignore) {
                }
            }
        }
    }

    private static String executeHttpRequestSync(String url, Map<String, String> params) throws IOException {
        HttpURLConnection conn = null;
        try {
            conn = getHttpURLConnection(url, params);
            String postData = buildPostData(params);

            try (OutputStream out = new BufferedOutputStream(conn.getOutputStream())) {
                out.write(postData.getBytes(StandardCharsets.UTF_8));
                out.flush();
            }

            int responseCode = conn.getResponseCode();
            String resp = readResponse(conn, responseCode);
            if (responseCode >= 200 && responseCode < 400) {
                return resp;
            } else {
                throw new IOException("HTTP " + responseCode + ": " + resp);
            }
        } finally {
            if (conn != null) {
                try {
                    conn.disconnect();
                } catch (Throwable ignore) {
                }
            }
        }
    }

    private static HttpURLConnection getHttpURLConnection(String url, Map<String, String> params) throws IOException {
        URL realUrl = new URL(url);
        HttpURLConnection conn = (HttpURLConnection) realUrl.openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8");
        conn.setRequestProperty("accept", "*/*");
        conn.setRequestProperty("connection", "close");
        conn.setUseCaches(false);
        conn.setDoOutput(true);
        conn.setDoInput(true);

        int timeout = BASE_TIMEOUT_MS + (params == null ? 0 : params.size() * PER_PARAM_TIMEOUT_MS);
        conn.setConnectTimeout(timeout);
        conn.setReadTimeout(timeout + READ_TIMEOUT_EXTRA_MS);

        return conn;
    }

    private static String buildPostData(Map<String, String> params) {
        if (params == null || params.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        boolean first = true;
        for (Map.Entry<String, String> e : params.entrySet()) {
            if (!first) {
                sb.append("&");
            }
            first = false;
            try {
                String k = URLEncoder.encode(e.getKey(), "UTF-8");
                String v = URLEncoder.encode(e.getValue() == null ? "" : e.getValue(), "UTF-8");
                sb.append(k).append("=").append(v);
            } catch (UnsupportedEncodingException ue) {
                // UTF-8 应该总是可用
                sb.append(e.getKey()).append("=").append(e.getValue() == null ? "" : e.getValue());
            }
        }
        return sb.toString();
    }

    private static String readResponse(HttpURLConnection conn, int responseCode) throws IOException {
        try (InputStream in = responseCode < 400 ? conn.getInputStream() : conn.getErrorStream()) {
            if (in == null) {
                return "";
            }
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            byte[] buf = new byte[1024];
            int r;
            while ((r = in.read(buf)) != -1) {
                baos.write(buf, 0, r);
            }
            return baos.toString("UTF-8");
        }
    }

    private static void shutdownExecutor(ExecutorService executor, String executorName) {
        try {
            executor.shutdown();
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                executor.shutdownNow();
                logger.info(String.format("[Agent-info] %s executor shutdown forcefully", executorName));
            } else {
                logger.info(String.format("[Agent-info] %s executor shutdown gracefully", executorName));
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            try {
                executor.shutdownNow();
            } catch (Throwable ignore) {
            }
        }
    }

    /**
     *
     */
    private static class FailedFuture implements Future<String> {
        private final Throwable cause;

        FailedFuture(Throwable cause) {
            this.cause = cause;
        }

        @Override
        public boolean cancel(boolean mayInterruptIfRunning) {
            return false;
        }

        @Override
        public boolean isCancelled() {
            return false;
        }

        @Override
        public boolean isDone() {
            return true;
        }

        @Override
        public String get() throws ExecutionException {
            throw new ExecutionException(cause);
        }

        @Override
        public String get(long timeout, TimeUnit unit) throws ExecutionException {
            throw new ExecutionException(cause);
        }
    }

    /**
     * 自定义拒绝策略：记录日志但不抛出异常
     */
    private static class DiscardWithLogPolicy implements RejectedExecutionHandler {
        @Override
        public void rejectedExecution(Runnable r, ThreadPoolExecutor executor) {
            if (!executor.isShutdown()) {
                try {
                    logger.warn(String.format("[Agent-warn]HTTP 任务被拒绝，当前队列大小: %d", executor.getQueue().size()));
                } catch (Throwable ignore) {
                }
            }
        }
    }
}
