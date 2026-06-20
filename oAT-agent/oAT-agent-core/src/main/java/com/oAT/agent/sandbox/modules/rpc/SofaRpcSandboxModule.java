package com.oAT.agent.sandbox.modules.rpc;

import com.oAT.agent.collect.SofaClientCollect;
import com.oAT.agent.collect.SofaServerCollect;
import com.oAT.agent.common.StackTraceFormatter;
import com.oAT.agent.common.logger.Log;
import com.oAT.agent.common.logger.LogFactory;
import com.oAT.agent.model.SofaRpcTraceNode;
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

import java.util.EnumSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class SofaRpcSandboxModule implements OatModule, EventListener {
    private static final Log logger = LogFactory.getLog(SofaRpcSandboxModule.class);

    private final ConcurrentMap<Long, Wrapper> wrappers = new ConcurrentHashMap<Long, Wrapper>();
    private ModuleContext context;
    private SofaClientCollect clientCollect;
    private SofaServerCollect serverCollect;
    private WatchId clientWatchId;
    private WatchId serverWatchId;

    @Override
    public String id() {
        return "sofa-rpc";
    }

    @Override
    public void load(ModuleContext context) {
        this.context = context;
        TraceContext traceContext = (TraceContext) context.traceContext();
        this.clientCollect = new SofaClientCollect(traceContext);
        this.serverCollect = new SofaServerCollect(traceContext);
        this.clientWatchId = context.eventWatcher().watch(
                new ExactClassMatcher("com.alipay.sofa.rpc.filter.ConsumerInvoker"),
                new ExactMethodMatcher("invoke", null),
                EnumSet.of(EventType.BEFORE, EventType.RETURN, EventType.THROWS),
                this);
        this.serverWatchId = context.eventWatcher().watch(
                new ExactClassMatcher("com.alipay.sofa.rpc.filter.ProviderInvoker"),
                new ExactMethodMatcher("invoke", null),
                EnumSet.of(EventType.BEFORE, EventType.RETURN, EventType.THROWS),
                this);
    }

    @Override
    public void active() {
        logger.info("[Sandbox-SofaRpc] active");
    }

    @Override
    public void frozen() {
        logger.info("[Sandbox-SofaRpc] frozen");
    }

    @Override
    public void unload() {
        if (context != null) {
            context.eventWatcher().delete(clientWatchId);
            context.eventWatcher().delete(serverWatchId);
        }
        wrappers.clear();
    }

    @Override
    public Object onEvent(SandboxEvent event) {
        try {
            if (event instanceof BeforeEvent) {
                BeforeEvent before = (BeforeEvent) event;
                if ("com.alipay.sofa.rpc.filter.ConsumerInvoker".equals(before.className())) {
                    wrappers.put(event.invokeId(), new Wrapper("client",
                            clientCollect.begin(before.args(), before.target()), before.args()));
                } else {
                    wrappers.put(event.invokeId(), new Wrapper("server",
                            serverCollect.begin(before.args()), before.args()));
                }
            } else if (event instanceof ReturnEvent) {
                Wrapper wrapper = wrappers.remove(event.invokeId());
                if (wrapper != null) {
                    end(wrapper, event.returnValue(), null);
                }
            } else if (event instanceof ThrowsEvent) {
                Wrapper wrapper = wrappers.remove(event.invokeId());
                if (wrapper != null) {
                    end(wrapper, null, ((ThrowsEvent) event).throwable());
                }
            }
        } catch (Throwable t) {
            logger.error("[Sandbox-SofaRpc] event failed: " + StackTraceFormatter.formatExceptionWithAgentMark(t));
        }
        return null;
    }

    private void end(Wrapper wrapper, Object result, Throwable throwable) {
        if ("client".equals(wrapper.kind)) {
            SofaRpcTraceNode node = (SofaRpcTraceNode) wrapper.node;
            if (throwable != null) {
                clientCollect.error(node, throwable);
            }
            clientCollect.end(node, wrapper.args, result);
        } else {
            SofaServerCollect.SofaRpcRemoteTraceNodeWrapper node =
                    (SofaServerCollect.SofaRpcRemoteTraceNodeWrapper) wrapper.node;
            if (throwable != null) {
                serverCollect.error(node, throwable);
            }
            serverCollect.end(node, wrapper.args, result);
        }
    }

    private static class Wrapper {
        private final String kind;
        private final Object node;
        private final Object[] args;

        private Wrapper(String kind, Object node, Object[] args) {
            this.kind = kind;
            this.node = node;
            this.args = args;
        }
    }
}
