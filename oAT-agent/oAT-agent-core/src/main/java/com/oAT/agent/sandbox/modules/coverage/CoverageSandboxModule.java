package com.oAT.agent.sandbox.modules.coverage;

import com.oAT.agent.collect.CodeStackCollect;
import com.oAT.agent.collect.CodeStaticStackCollect;
import com.oAT.agent.common.logger.Log;
import com.oAT.agent.common.logger.LogFactory;
import com.oAT.agent.jacoco.data.CompactDataOutput;
import com.oAT.agent.sandbox.api.ModuleContext;
import com.oAT.agent.sandbox.api.OatModule;
import com.oAT.agent.trace.TraceContext;

public class CoverageSandboxModule implements OatModule {
    private static final Log logger = LogFactory.getLog(CoverageSandboxModule.class);
    private CodeStackCollect codeStackCollect;
    private CodeStaticStackCollect codeStaticStackCollect;

    @Override
    public String id() {
        return "coverage";
    }

    @Override
    public void load(ModuleContext context) {
        TraceContext traceContext = context.traceContext() instanceof TraceContext ? (TraceContext) context.traceContext() : null;
        if (traceContext == null) {
            logger.warn("[Sandbox-Coverage] traceContext is null, module skipped");
            return;
        }
        unloadCollectors();
        codeStackCollect = new CodeStackCollect(traceContext, context.instrumentation());
        CodeStackCollect.INSTANCE = codeStackCollect;
        codeStaticStackCollect = new CodeStaticStackCollect(traceContext, context.instrumentation());
        CodeStaticStackCollect.INSTANCE = codeStaticStackCollect;
        retransformLoadedClasses(context, traceContext);
        CompactDataOutput.trySendStaticInfo();
    }

    @Override
    public void active() {
        logger.info("[Sandbox-Coverage] active");
    }

    @Override
    public void frozen() {
        logger.info("[Sandbox-Coverage] frozen");
    }

    @Override
    public void unload() {
        unloadCollectors();
        logger.info("[Sandbox-Coverage] unloaded");
    }

    private void unloadCollectors() {
        if (codeStackCollect != null) {
            codeStackCollect.close();
            if (CodeStackCollect.INSTANCE == codeStackCollect) {
                CodeStackCollect.INSTANCE = null;
            }
            codeStackCollect = null;
        }
        if (codeStaticStackCollect != null) {
            codeStaticStackCollect.close();
            if (CodeStaticStackCollect.INSTANCE == codeStaticStackCollect) {
                CodeStaticStackCollect.INSTANCE = null;
            }
            codeStaticStackCollect = null;
        }
    }

    private void retransformLoadedClasses(ModuleContext context, TraceContext traceContext) {
        if (!context.instrumentation().isRetransformClassesSupported()) {
            return;
        }
        try {
            String includeExpr = traceContext.getConfig("codeStack.include");
            if (includeExpr == null || includeExpr.trim().isEmpty()) {
                includeExpr = traceContext.getConfig("conf_codeStack.include");
            }
            if (includeExpr == null || includeExpr.trim().isEmpty()) {
                return;
            }
            com.oAT.agent.common.WildcardMatcher includes =
                    new com.oAT.agent.common.WildcardMatcher(includeExpr);
            for (Class<?> clazz : context.instrumentation().getAllLoadedClasses()) {
                if (clazz == null || !context.instrumentation().isModifiableClass(clazz)) {
                    continue;
                }
                if (isAlreadyInstrumented(clazz)) {
                    continue;
                }
                String className = clazz.getName();
                if (includes.matches(className)) {
                    try {
                        context.instrumentation().retransformClasses(clazz);
                    } catch (Throwable ignored) {
                    }
                }
            }
        } catch (Throwable t) {
            logger.warn("[Sandbox-Coverage] retransform loaded classes failed: " + t.getMessage());
        }
    }

    private boolean isAlreadyInstrumented(Class<?> clazz) {
        try {
            clazz.getDeclaredField(com.oAT.agent.jacoco.instr.InstrSupport.DATAFIELD_NAME);
            return true;
        } catch (NoSuchFieldException ignored) {
            return false;
        } catch (Throwable ignored) {
            return false;
        }
    }
}
