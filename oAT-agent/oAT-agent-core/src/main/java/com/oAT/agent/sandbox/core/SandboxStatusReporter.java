package com.oAT.agent.sandbox.core;

import com.oAT.agent.common.HttpClient;
import com.oAT.agent.common.JsonUtil;
import com.oAT.agent.common.StackTraceFormatter;
import com.oAT.agent.common.logger.Log;
import com.oAT.agent.common.logger.LogFactory;
import com.oAT.agent.trace.TraceContext;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class SandboxStatusReporter {
    private static final Log logger = LogFactory.getLog(SandboxStatusReporter.class);
    private static volatile boolean scheduled;
    private static volatile boolean running;
    private static volatile Thread reporterThread;

    private SandboxStatusReporter() {
    }

    public static void report(SandboxRuntime runtime, StartMode startMode) {
        reportOnce(runtime, startMode);
        scheduleReport(runtime, startMode);
    }

    public static void reportNow(SandboxRuntime runtime, StartMode startMode) {
        reportOnce(runtime, startMode);
    }

    private static void scheduleReport(final SandboxRuntime runtime, final StartMode startMode) {
        if (scheduled) {
            return;
        }
        synchronized (SandboxStatusReporter.class) {
            if (scheduled) {
                return;
            }
            scheduled = true;
            running = true;
            Thread reporter = new Thread(new Runnable() {
                @Override
                public void run() {
                    while (running) {
                        try {
                            TimeUnit.SECONDS.sleep(20);
                            reportOnce(runtime, startMode);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            return;
                        } catch (Throwable t) {
                            logger.warn("[Sandbox] scheduled report status failed: "
                                    + StackTraceFormatter.formatExceptionWithAgentMark(t));
                        }
                    }
                }
            }, "oAT-sandbox-status");
            reporter.setDaemon(true);
            reporterThread = reporter;
            reporter.start();
        }
    }

    public static void stop() {
        running = false;
        scheduled = false;
        Thread thread = reporterThread;
        if (thread != null) {
            thread.interrupt();
        }
        reporterThread = null;
    }

    private static void reportOnce(SandboxRuntime runtime, StartMode startMode) {
        if (runtime == null || runtime.traceContext() == null) {
            return;
        }
        TraceContext traceContext = runtime.traceContext();
        String sessionId = traceContext.getClientSessionId();
        if (sessionId == null || sessionId.isEmpty() || traceContext.getRemoteServer().isEmpty()) {
            return;
        }
        try {
            Map<String, Object> payload = new LinkedHashMap<String, Object>();
            payload.put("sessionId", sessionId);
            payload.put("startMode", startMode == null ? "" : startMode.name());
            payload.put("sandboxVersion", runtime.properties().getProperty("agentVersion", "1.0-SNAPSHOT"));
            payload.put("modules", runtime.moduleManager().stateNames());

            Map<String, String> params = new HashMap<String, String>();
            params.put("sessionId", sessionId);
            params.put("status", JsonUtil.toJson(payload));
            if (Thread.currentThread().isInterrupted()) {
                return;
            }
            try {
                HttpClient.execHttp(traceContext.getRemoteServer() + "/client/sandbox/status", params)
                        .get(10, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                logger.info("[Sandbox] report status interrupted");
            }
        } catch (Throwable t) {
            logger.warn("[Sandbox] report status failed: " + StackTraceFormatter.formatExceptionWithAgentMark(t));
        }
    }
}
