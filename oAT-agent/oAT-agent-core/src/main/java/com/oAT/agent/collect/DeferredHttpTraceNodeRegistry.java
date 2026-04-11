package com.oAT.agent.collect;

import com.oAT.agent.common.StackTraceFormatter;
import com.oAT.agent.common.logger.Log;
import com.oAT.agent.common.logger.LogFactory;
import com.oAT.agent.trace.TraceSession;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 管理延迟补报的 HTTP 节点。
 */
public class DeferredHttpTraceNodeRegistry {
    private static final Log logger = LogFactory.getLog(DeferredHttpTraceNodeRegistry.class);

    private final Map<String, HttpServletCollect.HttpServletTraceNodeWrapper> deferredNodes = new ConcurrentHashMap<>();

    public void register(TraceSession traceSession, HttpServletCollect.HttpServletTraceNodeWrapper nodeWrapper) {
        if (traceSession == null || nodeWrapper == null) {
            return;
        }
        deferredNodes.put(traceSession.getTraceId(), nodeWrapper);
    }

    public HttpServletCollect.HttpServletTraceNodeWrapper remove(TraceSession traceSession) {
        if (traceSession == null) {
            return null;
        }
        return deferredNodes.remove(traceSession.getTraceId());
    }

    public void finalizeIfReady(TraceSession traceSession, HttpServletCollect collector) {
        if (traceSession == null || collector == null) {
            return;
        }
        HttpServletCollect.HttpServletTraceNodeWrapper nodeWrapper = remove(traceSession);
        if (nodeWrapper == null) {
            return;
        }
        try {
            collector.finalizeDeferredNode(nodeWrapper, traceSession);
        } catch (Throwable t) {
            logger.error("[Agent-EXCError]异步覆盖率补报失败: " + StackTraceFormatter.formatExceptionWithAgentMark(t));
        }
    }
}
