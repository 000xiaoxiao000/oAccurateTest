package com.oAT.agent.sandbox.modules.jdbc;

import com.oAT.agent.collect.jdbc.JdbcCommonCollects;
import com.oAT.agent.common.logger.Log;
import com.oAT.agent.common.logger.LogFactory;
import com.oAT.agent.sandbox.api.EventListener;
import com.oAT.agent.sandbox.api.EventType;
import com.oAT.agent.sandbox.api.ExactClassMatcher;
import com.oAT.agent.sandbox.api.ExactMethodMatcher;
import com.oAT.agent.sandbox.api.ModuleContext;
import com.oAT.agent.sandbox.api.OatModule;
import com.oAT.agent.sandbox.api.ReturnEvent;
import com.oAT.agent.sandbox.api.SandboxEvent;
import com.oAT.agent.sandbox.api.WatchId;
import com.oAT.agent.trace.TraceContext;

import java.sql.Connection;
import java.util.EnumSet;

public class JdbcSandboxModule implements OatModule, EventListener {
    private static final Log logger = LogFactory.getLog(JdbcSandboxModule.class);
    private static final String CONNECT_DESC = "(Ljava/lang/String;Ljava/util/Properties;)Ljava/sql/Connection;";

    private ModuleContext context;
    private WatchId watchId;
    private JdbcCommonCollects delegate;

    @Override
    public String id() {
        return "jdbc";
    }

    @Override
    public void load(ModuleContext context) {
        this.context = context;
        TraceContext traceContext = context.traceContext() instanceof TraceContext
                ? (TraceContext) context.traceContext() : null;
        this.delegate = new JdbcCommonCollects(traceContext,
                "com.mysql.cj.jdbc.NonRegisteringDriver", "com.mysql.jdbc.NonRegisteringDriver");
        this.watchId = context.eventWatcher().watch(
                new ExactClassMatcher("com.mysql.cj.jdbc.NonRegisteringDriver", "com.mysql.jdbc.NonRegisteringDriver"),
                new ExactMethodMatcher("connect", CONNECT_DESC),
                EnumSet.of(EventType.BEFORE, EventType.RETURN),
                this);
    }

    @Override
    public void active() {
        logger.info("[Sandbox-JDBC] active");
    }

    @Override
    public void frozen() {
        logger.info("[Sandbox-JDBC] frozen");
    }

    @Override
    public void unload() {
        if (context != null && watchId != null) {
            context.eventWatcher().delete(watchId);
        }
    }

    @Override
    public Object onEvent(SandboxEvent event) {
        if (!(event instanceof ReturnEvent) || delegate == null) {
            return null;
        }
        Object value = event.returnValue();
        if (value instanceof Connection) {
            return delegate.proxyConnection((Connection) value);
        }
        return null;
    }
}
