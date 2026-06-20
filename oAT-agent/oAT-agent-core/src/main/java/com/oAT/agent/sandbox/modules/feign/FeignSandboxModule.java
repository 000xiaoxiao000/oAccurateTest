package com.oAT.agent.sandbox.modules.feign;

import com.oAT.agent.collect.feign.InvocationAdapter;
import com.oAT.agent.common.JsonUtil;
import com.oAT.agent.common.StackTraceFormatter;
import com.oAT.agent.common.StringUtils;
import com.oAT.agent.common.logger.Log;
import com.oAT.agent.common.logger.LogFactory;
import com.oAT.agent.model.FeignTraceNode;
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
import com.oAT.agent.trace.TraceSession;

import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class FeignSandboxModule implements OatModule, EventListener {
    private static final Log logger = LogFactory.getLog(FeignSandboxModule.class);

    private final ConcurrentMap<Long, FeignWrapper> wrappers = new ConcurrentHashMap<Long, FeignWrapper>();
    private ModuleContext context;
    private TraceContext traceContext;
    private WatchId defaultWatchId;
    private WatchId apacheWatchId;

    @Override
    public String id() {
        return "feign";
    }

    @Override
    public void load(ModuleContext context) {
        this.context = context;
        this.traceContext = context.traceContext() instanceof TraceContext ? (TraceContext) context.traceContext() : null;
        ExactMethodMatcher matcher = new ExactMethodMatcher("execute", null);
        this.defaultWatchId = context.eventWatcher().watch(
                new ExactClassMatcher("feign.Client$Default"),
                matcher,
                EnumSet.of(EventType.BEFORE, EventType.RETURN, EventType.THROWS),
                this);
        this.apacheWatchId = context.eventWatcher().watch(
                new ExactClassMatcher("feign.httpclient.ApacheHttpClient"),
                matcher,
                EnumSet.of(EventType.BEFORE, EventType.RETURN, EventType.THROWS),
                this);
    }

    @Override
    public void active() {
        logger.info("[Sandbox-Feign] active");
    }

    @Override
    public void frozen() {
        logger.info("[Sandbox-Feign] frozen");
    }

    @Override
    public void unload() {
        if (context != null) {
            context.eventWatcher().delete(defaultWatchId);
            context.eventWatcher().delete(apacheWatchId);
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

    private FeignWrapper begin(BeforeEvent event) {
        if (traceContext == null || traceContext.getTraceSession() == null
                || event.args() == null || event.args().length == 0) {
            return null;
        }
        try {
            TraceSession session = traceContext.getTraceSession();
            InvocationAdapter invocation = new InvocationAdapter(event.args()[0]);
            FeignTraceNode node = new FeignTraceNode();
            node.setTraceId(session.getTraceId());
            node.setTraceNodeId(session.getNextNodeId());
            node.setBeginTime(System.currentTimeMillis());
            node.setServiceMethod(invocation.getMethod());
            node.setServiceURL(invocation.getURL());
            node.setServiceHeaders(String.valueOf(invocation.getHeaders()));
            if (invocation.getBody() != null) {
                node.setServiceBody(JsonUtil.toJson(invocation.getBody()));
            }
            node.setRemoteFeignTargetName("");
            node.setRemoteMethod(invocation.getMethod());
            node.setRemoteUrl(invocation.getURL());
            if (invocation.getBody() != null) {
                node.setRemoteBody(JsonUtil.toJson(invocation.getBody()));
            }
            invocation.setHeaders(traceHeader(session, node));
            return new FeignWrapper(session, node);
        } catch (Throwable t) {
            logger.error("[Sandbox-Feign] begin failed: " + StackTraceFormatter.formatExceptionWithAgentMark(t));
            return null;
        }
    }

    private Map<String, Collection<String>> traceHeader(TraceSession session, FeignTraceNode node) {
        Map<String, Collection<String>> map = new HashMap<String, Collection<String>>();
        map.put("parentTraceId", Collections.singletonList(node.getTraceId() + "_" + node.getTraceNodeId()));
        map.put("parentTraceNodeId", Collections.singletonList(node.getTraceNodeId()));
        String userHeader = session.getTraceRequest().getUserHeader();
        if (StringUtils.hasText(userHeader)) {
            map.put("userheader", Collections.singletonList(userHeader));
        }
        return map;
    }

    private void end(FeignWrapper wrapper, Throwable throwable) {
        if (wrapper == null) {
            return;
        }
        try {
            FeignTraceNode node = wrapper.node;
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
            logger.error("[Sandbox-Feign] end failed: " + StackTraceFormatter.formatExceptionWithAgentMark(t));
        }
    }

    private static class FeignWrapper {
        private final TraceSession traceSession;
        private final FeignTraceNode node;

        private FeignWrapper(TraceSession traceSession, FeignTraceNode node) {
            this.traceSession = traceSession;
            this.node = node;
        }
    }
}
