package com.oAT.agent.sandbox.modules.jdbc;

import com.oAT.agent.collect.ckjdbc.ClickHouseJdbcCollects;
import com.oAT.agent.common.StackTraceFormatter;
import com.oAT.agent.common.logger.Log;
import com.oAT.agent.common.logger.LogFactory;
import com.oAT.agent.model.CKSqlTraceNode;
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

import java.lang.reflect.Field;
import java.sql.Connection;
import java.util.EnumSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class ClickHouseJdbcSandboxModule implements OatModule, EventListener {
    private static final Log logger = LogFactory.getLog(ClickHouseJdbcSandboxModule.class);

    private final ConcurrentMap<Long, CKSqlTraceNode> nodes = new ConcurrentHashMap<Long, CKSqlTraceNode>();
    private ModuleContext context;
    private ClickHouseJdbcCollects delegate;
    private WatchId watchId;

    @Override
    public String id() {
        return "clickhouse-jdbc";
    }

    @Override
    public void load(ModuleContext context) {
        this.context = context;
        this.delegate = new ClickHouseJdbcCollects((TraceContext) context.traceContext());
        this.watchId = context.eventWatcher().watch(
                new ExactClassMatcher("ru.yandex.clickhouse.ClickHouseStatementImpl"),
                new ExactMethodMatcher("executeQuery", "(Ljava/lang/String;)Ljava/sql/ResultSet;"),
                EnumSet.of(EventType.BEFORE, EventType.RETURN, EventType.THROWS),
                this);
    }

    @Override
    public void active() {
        logger.info("[Sandbox-ClickHouseJdbc] active");
    }

    @Override
    public void frozen() {
        logger.info("[Sandbox-ClickHouseJdbc] frozen");
    }

    @Override
    public void unload() {
        if (context != null) {
            context.eventWatcher().delete(watchId);
        }
        nodes.clear();
    }

    @Override
    public Object onEvent(SandboxEvent event) {
        try {
            if (event instanceof BeforeEvent) {
                BeforeEvent before = (BeforeEvent) event;
                nodes.put(event.invokeId(), delegate.begin(findConnection(before.target()), before.args()));
            } else if (event instanceof ReturnEvent) {
                delegate.end(nodes.remove(event.invokeId()));
            } else if (event instanceof ThrowsEvent) {
                CKSqlTraceNode node = nodes.remove(event.invokeId());
                delegate.error(node, ((ThrowsEvent) event).throwable());
                delegate.end(node);
            }
        } catch (Throwable t) {
            logger.error("[Sandbox-ClickHouseJdbc] event failed: " + StackTraceFormatter.formatExceptionWithAgentMark(t));
        }
        return null;
    }

    private Connection findConnection(Object target) {
        Object value = readField(target, "connection");
        return value instanceof Connection ? (Connection) value : null;
    }

    private Object readField(Object target, String fieldName) {
        if (target == null) {
            return null;
        }
        Class<?> type = target.getClass();
        while (type != null) {
            try {
                Field field = type.getDeclaredField(fieldName);
                field.setAccessible(true);
                return field.get(target);
            } catch (NoSuchFieldException e) {
                type = type.getSuperclass();
            } catch (Throwable t) {
                logger.warn("[Sandbox-ClickHouseJdbc] read field failed: " + fieldName, t);
                return null;
            }
        }
        return null;
    }
}
