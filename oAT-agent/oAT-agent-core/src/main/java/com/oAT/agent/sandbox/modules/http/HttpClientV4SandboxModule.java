package com.oAT.agent.sandbox.modules.http;

import com.oAT.agent.collect.http.HttpClientRequestAdapterV4;
import com.oAT.agent.common.StackTraceFormatter;
import com.oAT.agent.common.logger.Log;
import com.oAT.agent.common.logger.LogFactory;
import com.oAT.agent.model.HttpClientTraceNode;
import com.oAT.agent.model.TraceNode;
import com.oAT.agent.sandbox.api.BeforeEvent;
import com.oAT.agent.sandbox.api.EventListener;
import com.oAT.agent.sandbox.api.EventType;
import com.oAT.agent.sandbox.api.ExactClassMatcher;
import com.oAT.agent.sandbox.api.ExactMethodMatcher;
import com.oAT.agent.sandbox.api.ModuleContext;
import com.oAT.agent.sandbox.api.OatModule;
import com.oAT.agent.sandbox.api.ReturnEvent;
import com.oAT.agent.sandbox.api.SandboxEvent;
import com.oAT.agent.sandbox.api.ThrowsEvent;
import com.oAT.agent.sandbox.api.WatchId;
import com.oAT.agent.trace.TraceContext;
import com.oAT.agent.trace.TraceRequest;
import com.oAT.agent.trace.TraceSession;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class HttpClientV4SandboxModule implements OatModule, EventListener {
    private static final Log logger = LogFactory.getLog(HttpClientV4SandboxModule.class);

    private final ConcurrentMap<Long, NodeWrapper> wrappers = new ConcurrentHashMap<Long, NodeWrapper>();
    private ModuleContext context;
    private TraceContext traceContext;
    private WatchId internalClientWatchId;
    private WatchId closeableClientWatchId;

    @Override
    public String id() {
        return "http-client-v4";
    }

    @Override
    public void load(ModuleContext context) {
        this.context = context;
        this.traceContext = context.traceContext() instanceof TraceContext ? (TraceContext) context.traceContext() : null;
        ExactMethodMatcher matcher = new ExactMethodMatcher("doExecute", null);
        this.internalClientWatchId = context.eventWatcher().watch(
                new ExactClassMatcher("org.apache.http.impl.client.InternalHttpClient"),
                matcher,
                EnumSet.of(EventType.BEFORE, EventType.RETURN, EventType.THROWS),
                this);
        this.closeableClientWatchId = context.eventWatcher().watch(
                new ExactClassMatcher("org.apache.http.impl.client.CloseableHttpClient"),
                matcher,
                EnumSet.of(EventType.BEFORE, EventType.RETURN, EventType.THROWS),
                this);
    }

    @Override
    public void active() {
        logger.info("[Sandbox-HttpClientV4] active");
    }

    @Override
    public void frozen() {
        logger.info("[Sandbox-HttpClientV4] frozen");
    }

    @Override
    public void unload() {
        if (context != null) {
            context.eventWatcher().delete(internalClientWatchId);
            context.eventWatcher().delete(closeableClientWatchId);
        }
    }

    @Override
    public Object onEvent(SandboxEvent event) {
        if (event instanceof BeforeEvent) {
            wrappers.put(event.invokeId(), begin((BeforeEvent) event));
        } else if (event instanceof ReturnEvent) {
            end(wrappers.remove(event.invokeId()), null);
        } else if (event instanceof ThrowsEvent) {
            end(wrappers.remove(event.invokeId()), ((ThrowsEvent) event).throwable());
        }
        return null;
    }

    private NodeWrapper begin(BeforeEvent event) {
        if (traceContext == null || event.args() == null) {
            return null;
        }
        try {
            HttpClientRequestAdapterV4 adapter = new HttpClientRequestAdapterV4(event.args());
            TraceSession traceSession = traceContext.getTraceSession();
            TraceRequest request;
            String traceNodeId = "0";
            if (traceSession == null) {
                request = new TraceRequest();
                request.setParentNodeCallId("0");
                request.setTraceId(traceContext.createTraceId());
                request.setProperties(new Properties());
                traceSession = traceContext.openTraceSession(event.className(), event.methodName(), request);
            } else {
                request = traceSession.getTraceRequest();
                traceNodeId = traceSession.getNextNodeId();
            }
            if (traceSession == null) {
                return null;
            }
            HttpClientTraceNode node = new HttpClientTraceNode();
            node.setTraceId(traceSession.getTraceId());
            node.setTraceNodeId(traceNodeId);
            node.setBeginTime(System.currentTimeMillis());
            node.setServiceMethod(adapter.getMethod());
            node.setServiceURL(adapter.getURL());
            node.setServiceHeaders(adapter.getRequestHeaders().toString());
            node.setServiceBody(adapter.getRequestBody());
            Map<String, String> headers = new HashMap<String, String>();
            headers.put("parentTraceId", node.getTraceId());
            headers.put("parentTraceNodeId", node.getTraceNodeId());
            headers.put("userheader", request.getUserHeader());
            adapter.setHeaders(headers);
            return new NodeWrapper(traceSession, node, traceNodeId.equals("0"));
        } catch (Throwable t) {
            logger.error("[Sandbox-HttpClientV4] begin failed: " + StackTraceFormatter.formatExceptionWithAgentMark(t));
            return null;
        }
    }

    private void end(NodeWrapper wrapper, Throwable throwable) {
        if (wrapper == null) {
            return;
        }
        try {
            HttpClientTraceNode node = wrapper.node;
            node.setEndTime(System.currentTimeMillis());
            node.setUseTime(node.getEndTime() - node.getBeginTime());
            node.setStatus(throwable == null ? TraceNode.Status.succeed.toString() : TraceNode.Status.fail.toString());
            if (throwable != null) {
                com.oAT.agent.model.Error error = new com.oAT.agent.model.Error();
                error.setType(throwable.getClass().getName());
                error.setMessage(throwable.getMessage());
                error.setErrorStack(StackTraceFormatter.formatExceptionWithAgentMark(throwable));
                node.setError(error);
            }
            wrapper.traceSession.saveNode(node);
        } catch (Throwable t) {
            logger.error("[Sandbox-HttpClientV4] end failed: " + StackTraceFormatter.formatExceptionWithAgentMark(t));
        } finally {
            if (wrapper.root) {
                try {
                    traceContext.closeTraceSession(wrapper.traceSession);
                } catch (Throwable ignored) {
                }
            }
        }
    }

    private static class NodeWrapper {
        private final TraceSession traceSession;
        private final HttpClientTraceNode node;
        private final boolean root;

        private NodeWrapper(TraceSession traceSession, HttpClientTraceNode node, boolean root) {
            this.traceSession = traceSession;
            this.node = node;
            this.root = root;
        }
    }
}
