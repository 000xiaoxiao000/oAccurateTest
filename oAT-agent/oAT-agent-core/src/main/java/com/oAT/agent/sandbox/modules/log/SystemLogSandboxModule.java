package com.oAT.agent.sandbox.modules.log;

import com.oAT.agent.collect.SystemLogCollect;
import com.oAT.agent.common.logger.Log;
import com.oAT.agent.common.logger.LogFactory;
import com.oAT.agent.sandbox.api.ModuleContext;
import com.oAT.agent.sandbox.api.OatModule;
import com.oAT.agent.trace.TraceContext;

public class SystemLogSandboxModule implements OatModule {
    private static final Log logger = LogFactory.getLog(SystemLogSandboxModule.class);

    @Override
    public String id() {
        return "system-log";
    }

    @Override
    public void load(ModuleContext context) {
        TraceContext traceContext = context.traceContext() instanceof TraceContext ? (TraceContext) context.traceContext() : null;
        if (traceContext == null) {
            logger.warn("[Sandbox-SystemLog] traceContext is null, module skipped");
            return;
        }
        SystemLogCollect.INSTANCE = new SystemLogCollect(traceContext, context.instrumentation());
    }

    @Override
    public void active() {
        logger.info("[Sandbox-SystemLog] active");
    }

    @Override
    public void frozen() {
        logger.info("[Sandbox-SystemLog] frozen");
    }

    @Override
    public void unload() {
        logger.info("[Sandbox-SystemLog] unload requested; System.out/System.err proxy is kept until JVM restart");
    }
}
