package com.oAT.agent.sandbox.modules.http;

import com.oAT.agent.collect.HttpClientCollectV3;
import com.oAT.agent.common.StackTraceFormatter;
import com.oAT.agent.common.logger.Log;
import com.oAT.agent.common.logger.LogFactory;
import com.oAT.agent.model.HttpClientTraceNode;
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

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class HttpClientV3SandboxModule implements OatModule, EventListener {
    private static final Log logger = LogFactory.getLog(HttpClientV3SandboxModule.class);

    private final ConcurrentMap<Long, Wrapper> wrappers = new ConcurrentHashMap<Long, Wrapper>();
    private final List<WatchId> watchIds = new ArrayList<WatchId>();
    private ModuleContext context;
    private HttpClientCollectV3 collect;

    @Override
    public String id() {
        return "http-client-v3";
    }

    @Override
    public void load(ModuleContext context) {
        this.context = context;
        this.collect = new HttpClientCollectV3((TraceContext) context.traceContext());
        watch("org.apache.commons.httpclient.HttpClient");
        watch("org.apache.commons.httpclient.HttpMethod");
        watch("org.apache.commons.httpclient.HttpMethodBase");
    }

    private void watch(String className) {
        watchIds.add(context.eventWatcher().watch(
                new ExactClassMatcher(className),
                new ExactMethodMatcher("executeMethod", null),
                EnumSet.of(EventType.BEFORE, EventType.RETURN, EventType.THROWS),
                this));
    }

    @Override
    public void active() {
        logger.info("[Sandbox-HttpClientV3] active");
    }

    @Override
    public void frozen() {
        logger.info("[Sandbox-HttpClientV3] frozen");
    }

    @Override
    public void unload() {
        if (context != null) {
            for (WatchId watchId : watchIds) {
                context.eventWatcher().delete(watchId);
            }
        }
        wrappers.clear();
    }

    @Override
    public Object onEvent(SandboxEvent event) {
        try {
            if (event instanceof BeforeEvent) {
                BeforeEvent before = (BeforeEvent) event;
                wrappers.put(event.invokeId(), new Wrapper(collect.begin(before.args()), before.args()));
            } else if (event instanceof ReturnEvent) {
                Wrapper wrapper = wrappers.remove(event.invokeId());
                if (wrapper != null) {
                    collect.end(wrapper.node, wrapper.args, event.returnValue());
                }
            } else if (event instanceof ThrowsEvent) {
                Wrapper wrapper = wrappers.remove(event.invokeId());
                if (wrapper != null) {
                    collect.error(wrapper.node, ((ThrowsEvent) event).throwable());
                    collect.end(wrapper.node, wrapper.args, null);
                }
            }
        } catch (Throwable t) {
            logger.error("[Sandbox-HttpClientV3] event failed: " + StackTraceFormatter.formatExceptionWithAgentMark(t));
        }
        return null;
    }

    private static class Wrapper {
        private final HttpClientTraceNode node;
        private final Object[] args;

        private Wrapper(HttpClientTraceNode node, Object[] args) {
            this.node = node;
            this.args = args;
        }
    }
}
