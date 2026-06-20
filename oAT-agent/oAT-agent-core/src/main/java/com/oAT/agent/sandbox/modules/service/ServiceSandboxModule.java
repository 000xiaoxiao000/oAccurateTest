package com.oAT.agent.sandbox.modules.service;

import com.oAT.agent.common.StackTraceFormatter;
import com.oAT.agent.common.StringUtils;
import com.oAT.agent.common.logger.Log;
import com.oAT.agent.common.logger.LogFactory;
import com.oAT.agent.model.ServiceTraceNode;
import com.oAT.agent.model.TraceNode;
import com.oAT.agent.sandbox.api.BeforeEvent;
import com.oAT.agent.sandbox.api.EventListener;
import com.oAT.agent.sandbox.api.EventType;
import com.oAT.agent.sandbox.api.ModuleContext;
import com.oAT.agent.sandbox.api.OatModule;
import com.oAT.agent.sandbox.api.ReturnEvent;
import com.oAT.agent.sandbox.api.SandboxEvent;
import com.oAT.agent.sandbox.api.ThrowsEvent;
import com.oAT.agent.sandbox.api.WatchId;
import com.oAT.agent.sandbox.api.WildcardClassMatcher;
import com.oAT.agent.sandbox.api.WildcardMethodMatcher;
import com.oAT.agent.trace.TraceContext;
import com.oAT.agent.trace.TraceRequest;
import com.oAT.agent.trace.TraceSession;

import java.util.EnumSet;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class ServiceSandboxModule implements OatModule, EventListener {
    private static final Log logger = LogFactory.getLog(ServiceSandboxModule.class);

    private final ConcurrentMap<Long, ServiceWrapper> wrappers = new ConcurrentHashMap<Long, ServiceWrapper>();
    private ModuleContext context;
    private TraceContext traceContext;
    private WatchId watchId;

    @Override
    public String id() {
        return "service";
    }

    @Override
    public void load(ModuleContext context) {
        this.context = context;
        this.traceContext = context.traceContext() instanceof TraceContext ? (TraceContext) context.traceContext() : null;
        String include = property("service.include", property("conf_service.include", ""));
        String exclude = property("service.exclude", property("conf_service.exclude", ""));
        String includeMethod = property("service.includeMethod", property("conf_service.includeMethod", "*"));
        String excludeMethod = property("service.excludeMethod", property("conf_service.excludeMethod",
                "get&set&add&hashCode&toString&equals"));
        if (!StringUtils.hasText(include)) {
            logger.warn("[Sandbox-Service] service include is blank, module skipped");
            return;
        }
        this.watchId = context.eventWatcher().watch(
                new WildcardClassMatcher(include, exclude),
                new WildcardMethodMatcher(includeMethod, excludeMethod),
                EnumSet.of(EventType.BEFORE, EventType.RETURN, EventType.THROWS),
                this);
    }

    @Override
    public void active() {
        logger.info("[Sandbox-Service] active");
    }

    @Override
    public void frozen() {
        logger.info("[Sandbox-Service] frozen");
    }

    @Override
    public void unload() {
        if (context != null && watchId != null) {
            context.eventWatcher().delete(watchId);
        }
    }

    @Override
    public Object onEvent(SandboxEvent event) {
        if (event instanceof BeforeEvent) {
            wrappers.put(event.invokeId(), begin((BeforeEvent) event));
        } else if (event instanceof ReturnEvent) {
            end(wrappers.remove(event.invokeId()));
        } else if (event instanceof ThrowsEvent) {
            error(wrappers.remove(event.invokeId()), ((ThrowsEvent) event).throwable());
        }
        return null;
    }

    private ServiceWrapper begin(BeforeEvent event) {
        if (traceContext == null) {
            return null;
        }
        try {
            TraceSession traceSession = traceContext.getTraceSession();
            String traceNodeId = "0";
            boolean root = false;
            if (traceSession == null) {
                TraceRequest traceRequest = new TraceRequest();
                traceRequest.setParentNodeCallId(traceNodeId);
                traceRequest.setTraceId(traceContext.createTraceId());
                traceRequest.setProperties(new Properties());
                traceSession = traceContext.openTraceSession(event.className(), event.methodName(), traceRequest);
                root = true;
            } else {
                traceNodeId = traceSession.getNextNodeId();
            }
            if (traceSession == null) {
                return null;
            }
            ServiceTraceNode node = new ServiceTraceNode();
            node.setTraceId(traceSession.getTraceId());
            node.setTraceNodeId(traceNodeId);
            node.setBeginTime(System.currentTimeMillis());
            node.setServiceName(event.className());
            node.setMethodName(event.methodName());
            node.setSimpleName(StringUtils.unqualify(event.className(), '.'));
            return new ServiceWrapper(traceSession, node, root);
        } catch (Throwable t) {
            logger.error("[Sandbox-Service] begin failed: " + StackTraceFormatter.formatExceptionWithAgentMark(t));
            return null;
        }
    }

    private void end(ServiceWrapper wrapper) {
        if (wrapper == null || traceContext == null) {
            return;
        }
        try {
            ServiceTraceNode node = wrapper.node;
            node.setEndTime(System.currentTimeMillis());
            node.setUseTime(node.getEndTime() - node.getBeginTime());
            node.setStatus(TraceNode.Status.succeed.toString());
            wrapper.traceSession.saveNode(node);
        } catch (Throwable t) {
            logger.error("[Sandbox-Service] end failed: " + StackTraceFormatter.formatExceptionWithAgentMark(t));
        } finally {
            if (wrapper.root) {
                try {
                    traceContext.closeTraceSession(wrapper.traceSession);
                } catch (Throwable t) {
                    logger.error("[Sandbox-Service] close root session failed: "
                            + StackTraceFormatter.formatExceptionWithAgentMark(t));
                }
            }
        }
    }

    private void error(ServiceWrapper wrapper, Throwable throwable) {
        if (wrapper == null || traceContext == null) {
            return;
        }
        try {
            ServiceTraceNode node = wrapper.node;
            node.setEndTime(System.currentTimeMillis());
            node.setUseTime(node.getEndTime() - node.getBeginTime());
            node.setStatus(TraceNode.Status.fail.toString());
            com.oAT.agent.model.Error error = new com.oAT.agent.model.Error();
            error.setType(throwable == null ? null : throwable.getClass().getName());
            error.setMessage(throwable == null ? null : throwable.getMessage());
            error.setErrorStack(throwable == null ? null : StackTraceFormatter.formatExceptionWithAgentMark(throwable));
            node.setError(error);
            wrapper.traceSession.saveNode(node);
        } catch (Throwable t) {
            logger.error("[Sandbox-Service] error failed: " + StackTraceFormatter.formatExceptionWithAgentMark(t));
        } finally {
            if (wrapper.root) {
                try {
                    traceContext.closeTraceSession(wrapper.traceSession);
                } catch (Throwable t) {
                    logger.error("[Sandbox-Service] close root session failed: "
                            + StackTraceFormatter.formatExceptionWithAgentMark(t));
                }
            }
        }
    }

    private String property(String key, String defaultValue) {
        return context.properties().getProperty(key, defaultValue);
    }

    private static class ServiceWrapper {
        private final TraceSession traceSession;
        private final ServiceTraceNode node;
        private final boolean root;

        private ServiceWrapper(TraceSession traceSession, ServiceTraceNode node, boolean root) {
            this.traceSession = traceSession;
            this.node = node;
            this.root = root;
        }
    }
}
