package com.oAT.agent.sandbox.modules.redis;

import com.oAT.agent.collect.redis.RedissonAdapter;
import com.oAT.agent.collect.redis.RedissonCommandAdapter;
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

import java.util.EnumSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class RedissonSandboxModule implements OatModule, EventListener {
    private static final Log logger = LogFactory.getLog(RedissonSandboxModule.class);
    private static final String CONSTRUCTOR_DESC = "(ZLorg/redisson/connection/NodeSource;"
            + "Lorg/redisson/client/codec/Codec;Lorg/redisson/client/protocol/RedisCommand;[Ljava/lang/Object;"
            + "Ljava/util/concurrent/CompletableFuture;ZLorg/redisson/connection/ConnectionManager;"
            + "Lorg/redisson/liveobject/core/RedissonObjectBuilder;"
            + "Lorg/redisson/liveobject/core/RedissonObjectBuilder$ReferenceType;Z)V";

    private final ConcurrentMap<Long, Wrapper> wrappers = new ConcurrentHashMap<Long, Wrapper>();
    private ModuleContext context;
    private TraceContext traceContext;
    private WatchId watchId;

    @Override
    public String id() {
        return "redisson";
    }

    @Override
    public void load(ModuleContext context) {
        this.context = context;
        this.traceContext = (TraceContext) context.traceContext();
        this.watchId = context.eventWatcher().watch(
                new ExactClassMatcher("org.redisson.command.RedisExecutor"),
                new ExactMethodMatcher("<init>", CONSTRUCTOR_DESC),
                EnumSet.of(EventType.BEFORE, EventType.RETURN, EventType.THROWS),
                this);
    }

    @Override
    public void active() {
        logger.info("[Sandbox-Redisson] active");
    }

    @Override
    public void frozen() {
        logger.info("[Sandbox-Redisson] frozen");
    }

    @Override
    public void unload() {
        if (context != null) {
            context.eventWatcher().delete(watchId);
        }
        wrappers.clear();
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

    private Wrapper begin(BeforeEvent event) {
        try {
            TraceSession session = traceContext != null ? traceContext.getTraceSession() : null;
            if (session == null || event.args() == null || event.args().length < 8) {
                return null;
            }
            RedisTraceNode node = new RedisTraceNode();
            node.setTraceId(session.getTraceId());
            node.setTraceNodeId(session.getNextNodeId());
            node.setBeginTime(System.currentTimeMillis());
            applyCommand(event.args()[3], event.args()[4], node);
            applyAddress(event.args()[7], node);
            return new Wrapper(session, node);
        } catch (Throwable t) {
            logger.error("[Sandbox-Redisson] begin failed: " + StackTraceFormatter.formatExceptionWithAgentMark(t));
            return null;
        }
    }

    private void end(Wrapper wrapper, Throwable throwable) {
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
            wrapper.session.saveNode(node);
        } catch (Throwable t) {
            logger.error("[Sandbox-Redisson] end failed: " + StackTraceFormatter.formatExceptionWithAgentMark(t));
        }
    }

    private void applyCommand(Object redisCommand, Object args, RedisTraceNode node) {
        try {
            if (redisCommand != null) {
                node.setType(new RedissonCommandAdapter(redisCommand).getName());
            }
            if (args instanceof Object[] && ((Object[]) args).length > 0 && ((Object[]) args)[0] != null) {
                node.setCmd(String.valueOf(((Object[]) args)[0]));
            }
        } catch (Throwable t) {
            logger.warn("[Sandbox-Redisson] command parse failed", t);
        }
    }

    private void applyAddress(Object connectionManager, RedisTraceNode node) {
        try {
            if (connectionManager == null) {
                return;
            }
            String[] addresses = new RedissonAdapter(connectionManager).getAddress();
            if (addresses == null || addresses.length == 0 || addresses[0] == null) {
                return;
            }
            String address = addresses[0].replace("redis://", "").replace("rediss://", "");
            String[] parts = address.split(":");
            if (parts.length >= 1) {
                node.setHost(parts[0]);
            }
            if (parts.length >= 2) {
                node.setPort(parts[1]);
            }
        } catch (Throwable t) {
            logger.warn("[Sandbox-Redisson] address parse failed", t);
        }
    }

    private static class Wrapper {
        private final TraceSession session;
        private final RedisTraceNode node;

        private Wrapper(TraceSession session, RedisTraceNode node) {
            this.session = session;
            this.node = node;
        }
    }
}
