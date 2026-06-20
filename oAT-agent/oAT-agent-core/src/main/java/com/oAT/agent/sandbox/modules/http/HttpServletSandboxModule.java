package com.oAT.agent.sandbox.modules.http;

import com.oAT.agent.common.NetUtils;
import com.oAT.agent.common.StackTraceFormatter;
import com.oAT.agent.common.StringUtils;
import com.oAT.agent.common.logger.Log;
import com.oAT.agent.common.logger.LogFactory;
import com.oAT.agent.collect.HttpServletCollect;
import com.oAT.agent.context.AgentContext;
import com.oAT.agent.jacoco.CoverageCollector;
import com.oAT.agent.jacoco.data.CompactDataOutput;
import com.oAT.agent.jacoco.data.StackNodeVoBuilder;
import com.oAT.agent.model.HttpTraceNode;
import com.oAT.agent.model.StackNodeVo;
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
import com.oAT.agent.sandbox.core.SandboxContext;
import com.oAT.agent.sandbox.core.SandboxEnhancementRegistry;
import com.oAT.agent.trace.TraceContext;
import com.oAT.agent.trace.TraceRequest;
import com.oAT.agent.trace.TraceSession;

import java.lang.reflect.Method;
import java.util.EnumSet;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class HttpServletSandboxModule implements OatModule, EventListener {
    private static final Log logger = LogFactory.getLog(HttpServletSandboxModule.class);
    private static volatile HttpServletSandboxModule INSTANCE;

    private final ConcurrentMap<Long, HttpWrapper> wrappers = new ConcurrentHashMap<Long, HttpWrapper>();
    private final ConcurrentMap<String, HttpWrapper> deferredWrappers = new ConcurrentHashMap<String, HttpWrapper>();
    private ModuleContext context;
    private TraceContext traceContext;
    private WatchId javaxWatchId;
    private WatchId jakartaWatchId;

    @Override
    public String id() {
        return "http-servlet";
    }

    @Override
    public void load(ModuleContext context) {
        INSTANCE = this;
        this.context = context;
        this.traceContext = context.traceContext() instanceof TraceContext ? (TraceContext) context.traceContext() : null;
        if (HttpServletCollect.INSTANCE != null) {
            SandboxEnhancementRegistry registry = context instanceof SandboxContext
                    ? ((SandboxContext) context).enhancementRegistry() : null;
            HttpServletCollect.INSTANCE.configureEnhancementRegistry(registry, id());
            logger.info("[Sandbox-HTTP] reuse HttpServletCollect as request entry");
            return;
        }
        this.javaxWatchId = context.eventWatcher().watch(
                new ExactClassMatcher("javax.servlet.http.HttpServlet"),
                new ExactMethodMatcher("service", null),
                EnumSet.of(EventType.BEFORE, EventType.RETURN, EventType.THROWS),
                this);
        this.jakartaWatchId = context.eventWatcher().watch(
                new ExactClassMatcher("jakarta.servlet.http.HttpServlet"),
                new ExactMethodMatcher("service", null),
                EnumSet.of(EventType.BEFORE, EventType.RETURN, EventType.THROWS),
                this);
    }

    @Override
    public void active() {
        logger.info("[Sandbox-HTTP] active");
    }

    @Override
    public void frozen() {
        logger.info("[Sandbox-HTTP] frozen");
    }

    @Override
    public void unload() {
        if (INSTANCE == this) {
            INSTANCE = null;
        }
        if (context != null) {
            context.eventWatcher().delete(javaxWatchId);
            context.eventWatcher().delete(jakartaWatchId);
        }
    }

    public static void tryFinalizeDeferredNode() {
        HttpServletSandboxModule module = INSTANCE;
        if (module == null) {
            return;
        }
        TraceSession traceSession = AgentContext.getTraceSession();
        if (traceSession == null || AgentContext.hasPendingAsyncTasks()) {
            return;
        }
        module.finalizeDeferredNode(traceSession);
    }

    @Override
    public Object onEvent(SandboxEvent event) {
        if (event instanceof BeforeEvent) {
            HttpWrapper wrapper = begin((BeforeEvent) event);
            if (wrapper != null) {
                wrappers.put(event.invokeId(), wrapper);
            }
        } else if (event instanceof ReturnEvent) {
            end(wrappers.remove(event.invokeId()), null);
        } else if (event instanceof ThrowsEvent) {
            end(wrappers.remove(event.invokeId()), ((ThrowsEvent) event).throwable());
        }
        return null;
    }

    private HttpWrapper begin(BeforeEvent event) {
        if (traceContext == null || event.args() == null || event.args().length == 0) {
            return null;
        }
        Object request = event.args()[0];
        try {
            String traceId = stringValue(invoke(request, "getAttribute", new Class[]{String.class},
                    new Object[]{"parentTraceId"}));
            if (traceId == null || traceId.isEmpty()) {
                traceId = stringValue(invoke(request, "getHeader", new Class[]{String.class},
                        new Object[]{"parentTraceId"}));
            }
            TraceRequest traceRequest = new TraceRequest();
            traceRequest.setParentNodeCallId("0");
            traceRequest.setTraceId(traceId == null || traceId.isEmpty() ? traceContext.createTraceId() : traceId);
            traceRequest.setProperties(new Properties());
            TraceSession traceSession = traceContext.openTraceSession(event.className(), event.methodName(), traceRequest);
            if (traceSession == null) {
                return null;
            }
            HttpTraceNode node = new HttpTraceNode();
            node.setTraceId(traceSession.getTraceId());
            node.setTraceNodeId("0");
            node.setBeginTime(System.currentTimeMillis());
            node.setRequestMethod(stringValue(invoke(request, "getMethod", null, null)));
            node.setRequestUrl(requestUrl(request));
            node.setClientIp(remoteAddress(request));
            node.setServerIp(NetUtils.getLocalHost());
            Object port = invoke(request, "getServerPort", null, null);
            node.setServerPort(port == null ? null : String.valueOf(port));
            HttpWrapper wrapper = new HttpWrapper(traceSession, node);
            if (coverageEnabled()) {
                wrapper.coverageCollector = CoverageCollector.begin();
                AgentContext.setCoverageCollector(wrapper.coverageCollector);
                AgentContext.setActiveAsyncTaskCount(new AtomicInteger(0));
                AgentContext.setAsyncCompletionListener(wrapper);
            } else if (logger.isDebugEnabled()) {
                logger.debug("[Sandbox-HTTP] coverage collector skipped, codeStack.include/conf_codeStack.include is blank");
            }
            return wrapper;
        } catch (Throwable t) {
            logger.error("[Sandbox-HTTP] begin failed: " + StackTraceFormatter.formatExceptionWithAgentMark(t));
            return null;
        }
    }

    private void end(HttpWrapper wrapper, Throwable throwable) {
        if (wrapper == null) {
            return;
        }
        boolean deferred = false;
        try {
            HttpTraceNode node = wrapper.node;
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
            if (wrapper.coverageCollector != null && AgentContext.hasPendingAsyncTasks()) {
                deferred = true;
                wrapper.markDeferred();
                deferredWrappers.put(wrapper.traceSession.getTraceId(), wrapper);
                AtomicInteger activeAsyncTaskCount = AgentContext.getActiveAsyncTaskCount();
                int pendingAsyncTasks = activeAsyncTaskCount == null ? 0 : activeAsyncTaskCount.get();
                wrapper.detachCurrentThreadContext();
                logger.info("[Sandbox-HTTP] detected pending async tasks, defer coverage flush, pendingAsyncTasks="
                        + pendingAsyncTasks + ", traceId=" + wrapper.traceSession.getTraceId());
            } else {
                collectCoverage(wrapper);
                wrapper.traceSession.saveNode(node);
            }
        } catch (Throwable t) {
            logger.error("[Sandbox-HTTP] end failed: " + StackTraceFormatter.formatExceptionWithAgentMark(t));
        } finally {
            if (!deferred) {
                wrapper.destroy(traceContext);
            }
        }
    }

    private void finalizeDeferredNode(TraceSession traceSession) {
        HttpWrapper wrapper = traceSession == null ? null : deferredWrappers.remove(traceSession.getTraceId());
        if (wrapper == null) {
            return;
        }
        try {
            wrapper.clearDeferred();
            collectCoverage(wrapper);
            wrapper.traceSession.saveNode(wrapper.node);
            if (logger.isDebugEnabled()) {
                logger.debug("[Sandbox-HTTP] finalized deferred coverage, traceId=" + wrapper.traceSession.getTraceId());
            }
        } catch (Throwable t) {
            logger.error("[Sandbox-HTTP] finalize deferred coverage failed: " + StackTraceFormatter.formatExceptionWithAgentMark(t));
        } finally {
            wrapper.destroy(traceContext);
        }
    }

    private boolean coverageEnabled() {
        return traceContext != null
                && (StringUtils.hasText(traceContext.getConfig("codeStack.include"))
                || StringUtils.hasText(traceContext.getConfig("conf_codeStack.include")));
    }

    private void collectCoverage(HttpWrapper wrapper) {
        if (wrapper == null || wrapper.coverageCollector == null) {
            return;
        }
        try {
            CoverageCollector collector = AgentContext.getCoverageCollector();
            if (collector != null) {
                wrapper.coverageCollector = collector;
            }
            wrapper.coverageCollector.collectSnapshots();
            if (!wrapper.coverageCollector.getProbeSnapshots().isEmpty()) {
                StackNodeVo[] codeNodes = new StackNodeVoBuilder().buildCodeNodes(wrapper.coverageCollector);
                wrapper.node.setCodeNodes(codeNodes);
                CompactDataOutput.trySendStaticInfo();
                if (logger.isDebugEnabled()) {
                    logger.debug("[Sandbox-HTTP] collected coverage codeNodes=" + codeNodes.length
                            + ", traceId=" + wrapper.node.getTraceId());
                }
            } else if (logger.isDebugEnabled()) {
                logger.debug("[Sandbox-HTTP] coverage probe snapshot empty, traceId=" + wrapper.node.getTraceId());
            }
        } catch (Throwable t) {
            logger.error("[Sandbox-HTTP] collect coverage failed: " + StackTraceFormatter.formatExceptionWithAgentMark(t));
        }
    }

    private String requestUrl(Object request) {
        Object url = invoke(request, "getRequestURL", null, null);
        return url == null ? null : String.valueOf(url);
    }

    private String remoteAddress(Object request) {
        Object value = invoke(request, "getHeader", new Class[]{String.class}, new Object[]{"X-Forwarded-For"});
        if (value == null) {
            value = invoke(request, "getRemoteAddr", null, null);
        }
        return value == null ? null : String.valueOf(value);
    }

    private Object invoke(Object target, String methodName, Class[] types, Object[] args) {
        if (target == null) {
            return null;
        }
        try {
            Method method = types == null ? target.getClass().getMethod(methodName) : target.getClass().getMethod(methodName, types);
            return method.invoke(target, args == null ? new Object[0] : args);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static class HttpWrapper implements AgentContext.AsyncCompletionListener {
        private final TraceSession traceSession;
        private final HttpTraceNode node;
        private CoverageCollector coverageCollector;
        private boolean deferred;
        private boolean destroyed;

        private HttpWrapper(TraceSession traceSession, HttpTraceNode node) {
            this.traceSession = traceSession;
            this.node = node;
        }

        @Override
        public void onAsyncComplete(TraceSession traceSession) {
            if (!deferred) {
                return;
            }
            HttpServletSandboxModule module = INSTANCE;
            if (module != null) {
                module.finalizeDeferredNode(traceSession);
            }
        }

        private void markDeferred() {
            this.deferred = true;
        }

        private void clearDeferred() {
            this.deferred = false;
        }

        private synchronized void destroy(TraceContext traceContext) {
            if (destroyed || deferred) {
                return;
            }
            destroyed = true;
            try {
                if (traceContext != null) {
                    traceContext.closeTraceSession(traceSession);
                }
            } catch (Throwable t) {
                logger.error("[Sandbox-HTTP] close session failed: " + StackTraceFormatter.formatExceptionWithAgentMark(t));
            } finally {
                detachCurrentThreadContext();
            }
        }

        private void detachCurrentThreadContext() {
            if (coverageCollector != null) {
                CoverageCollector.remove();
            }
            AgentContext.removeCoverageCollector();
            AgentContext.removeActiveAsyncTaskCount();
            AgentContext.removeAsyncCompletionListener();
        }
    }
}
