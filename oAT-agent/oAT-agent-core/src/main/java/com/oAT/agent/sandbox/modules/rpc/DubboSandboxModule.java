package com.oAT.agent.sandbox.modules.rpc;

import com.oAT.agent.collect.DubboInvokerCollect;
import com.oAT.agent.collect.DubboReceiveCollect;
import com.oAT.agent.common.StackTraceFormatter;
import com.oAT.agent.common.logger.Log;
import com.oAT.agent.common.logger.LogFactory;
import com.oAT.agent.model.DubboTraceNode;
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

public class DubboSandboxModule implements OatModule, EventListener {
    private static final Log logger = LogFactory.getLog(DubboSandboxModule.class);

    private final ConcurrentMap<Long, Wrapper> wrappers = new ConcurrentHashMap<Long, Wrapper>();
    private ModuleContext context;
    private DubboInvokerCollect invokerCollect;
    private DubboReceiveCollect receiveCollect;
    private WatchId invokerWatchId;
    private WatchId receiveWatchId;

    @Override
    public String id() {
        return "dubbo";
    }

    @Override
    public void load(ModuleContext context) {
        this.context = context;
        TraceContext traceContext = (TraceContext) context.traceContext();
        this.invokerCollect = new DubboInvokerCollect(traceContext);
        this.receiveCollect = new DubboReceiveCollect(traceContext);
        this.invokerWatchId = context.eventWatcher().watch(
                new ExactClassMatcher("org.apache.dubbo.rpc.protocol.dubbo.DubboInvoker"),
                new ExactMethodMatcher("doInvoke", null),
                EnumSet.of(EventType.BEFORE, EventType.RETURN, EventType.THROWS),
                this);
        this.receiveWatchId = context.eventWatcher().watch(
                new ExactClassMatcher("org.apache.dubbo.rpc.filter.GenericFilter"),
                new ExactMethodMatcher("invoke", null),
                EnumSet.of(EventType.BEFORE, EventType.RETURN, EventType.THROWS),
                this);
    }

    @Override
    public void active() {
        logger.info("[Sandbox-Dubbo] active");
    }

    @Override
    public void frozen() {
        logger.info("[Sandbox-Dubbo] frozen");
    }

    @Override
    public void unload() {
        if (context != null) {
            context.eventWatcher().delete(invokerWatchId);
            context.eventWatcher().delete(receiveWatchId);
        }
        wrappers.clear();
    }

    @Override
    public Object onEvent(SandboxEvent event) {
        try {
            if (event instanceof BeforeEvent) {
                BeforeEvent before = (BeforeEvent) event;
                if ("org.apache.dubbo.rpc.protocol.dubbo.DubboInvoker".equals(before.className())) {
                    wrappers.put(event.invokeId(), new Wrapper("invoker", invokerCollect.begin(before.args()), before.args()));
                } else {
                    wrappers.put(event.invokeId(), new Wrapper("receive", receiveCollect.begin(before.args()), before.args()));
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
            logger.error("[Sandbox-Dubbo] event failed: " + StackTraceFormatter.formatExceptionWithAgentMark(t));
        }
        return null;
    }

    private void end(Wrapper wrapper, Object result, Throwable throwable) {
        if ("invoker".equals(wrapper.kind)) {
            DubboTraceNode node = (DubboTraceNode) wrapper.node;
            if (throwable != null) {
                invokerCollect.error(node, throwable);
            }
            invokerCollect.end(node, wrapper.args, result);
        } else {
            DubboReceiveCollect.DubboRemoteTraceNodeWrapper node =
                    (DubboReceiveCollect.DubboRemoteTraceNodeWrapper) wrapper.node;
            if (throwable != null) {
                receiveCollect.error(node, throwable);
            }
            receiveCollect.end(node, wrapper.args, result);
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
