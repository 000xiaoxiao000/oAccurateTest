package com.oAT.agent.sandbox.modules.redis;

import com.oAT.agent.common.StackTraceFormatter;
import com.oAT.agent.common.logger.Log;
import com.oAT.agent.common.logger.LogFactory;
import com.oAT.agent.model.RedisTraceNode;
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

import java.lang.reflect.Method;
import java.util.EnumSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class RedisSandboxModule implements OatModule, EventListener {
    private static final Log logger = LogFactory.getLog(RedisSandboxModule.class);
    private static final String WRITE_DESC = "(Lio/lettuce/core/protocol/RedisCommand;)Lio/lettuce/core/protocol/RedisCommand;";
    private static final int MAX_CMD_LENGTH = 1024;

    private final ConcurrentMap<Long, RedisWrapper> wrappers = new ConcurrentHashMap<Long, RedisWrapper>();
    private ModuleContext context;
    private TraceContext traceContext;
    private WatchId watchId;

    @Override
    public String id() {
        return "redis";
    }

    @Override
    public void load(ModuleContext context) {
        this.context = context;
        this.traceContext = context.traceContext() instanceof TraceContext ? (TraceContext) context.traceContext() : null;
        this.watchId = context.eventWatcher().watch(
                new ExactClassMatcher("io.lettuce.core.protocol.DefaultEndpoint"),
                new ExactMethodMatcher("write", WRITE_DESC),
                EnumSet.of(EventType.BEFORE, EventType.RETURN, EventType.THROWS),
                this);
    }

    @Override
    public void active() {
        logger.info("[Sandbox-Redis] active");
    }

    @Override
    public void frozen() {
        logger.info("[Sandbox-Redis] frozen");
    }

    @Override
    public void unload() {
        if (context != null) {
            context.eventWatcher().delete(watchId);
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

    private RedisWrapper begin(BeforeEvent event) {
        if (traceContext == null || traceContext.getTraceSession() == null) {
            return null;
        }
        try {
            TraceSession session = traceContext.getTraceSession();
            RedisTraceNode node = new RedisTraceNode();
            node.setBeginTime(System.currentTimeMillis());
            node.setTraceId(session.getTraceId());
            node.setTraceNodeId(session.getNextNodeId());
            applyRemoteAddress(event.target(), node);
            applyCommand(event.args() != null && event.args().length > 0 ? event.args()[0] : null, node);
            return new RedisWrapper(session, node);
        } catch (Throwable t) {
            logger.error("[Sandbox-Redis] begin failed: " + StackTraceFormatter.formatExceptionWithAgentMark(t));
            return null;
        }
    }

    private void end(RedisWrapper wrapper, Throwable throwable) {
        if (wrapper == null) {
            return;
        }
        try {
            RedisTraceNode node = wrapper.node;
            node.setEndTime(System.currentTimeMillis());
            node.setUseTime(Math.max(0, node.getEndTime() - node.getBeginTime()));
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
            logger.error("[Sandbox-Redis] end failed: " + StackTraceFormatter.formatExceptionWithAgentMark(t));
        }
    }

    private void applyRemoteAddress(Object target, RedisTraceNode node) {
        try {
            Object channel = invoke(target, "channel");
            Object remote = invoke(channel, "remoteAddress");
            String value = remote == null ? null : String.valueOf(remote);
            if (value == null) {
                return;
            }
            String host = value;
            String port = null;
            int idx = value.lastIndexOf(':');
            if (idx > 0) {
                host = value.substring(0, idx);
                port = value.substring(idx + 1);
            }
            if (host.startsWith("/")) {
                host = host.substring(1);
            }
            node.setHost(host);
            if (port != null) {
                node.setPort(port);
            }
        } catch (Throwable ignored) {
        }
    }

    private void applyCommand(Object command, RedisTraceNode node) {
        try {
            Object type = invoke(command, "getType");
            if (type != null) {
                node.setType(String.valueOf(type));
            }
            Object args = invoke(command, "getArgs");
            Object cmd = invoke(args, "toCommandString");
            if (cmd != null) {
                node.setCmd(sanitize(String.valueOf(cmd)));
            }
        } catch (Throwable ignored) {
        }
    }

    private Object invoke(Object target, String methodName) throws Exception {
        if (target == null) {
            return null;
        }
        Method method = target.getClass().getMethod(methodName);
        return method.invoke(target);
    }

    private String sanitize(String value) {
        if (value == null) {
            return "";
        }
        return value.length() > MAX_CMD_LENGTH ? value.substring(0, MAX_CMD_LENGTH) + "...[truncated]" : value;
    }

    private static class RedisWrapper {
        private final TraceSession traceSession;
        private final RedisTraceNode node;

        private RedisWrapper(TraceSession traceSession, RedisTraceNode node) {
            this.traceSession = traceSession;
            this.node = node;
        }
    }
}
