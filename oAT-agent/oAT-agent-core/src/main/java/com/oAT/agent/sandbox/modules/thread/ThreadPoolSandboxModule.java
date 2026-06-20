package com.oAT.agent.sandbox.modules.thread;

import com.oAT.agent.collect.thread.ThreadPoolCollect;
import com.oAT.agent.common.logger.Log;
import com.oAT.agent.common.logger.LogFactory;
import com.oAT.agent.sandbox.api.ModuleContext;
import com.oAT.agent.sandbox.api.OatModule;

public class ThreadPoolSandboxModule implements OatModule {
    private static final Log logger = LogFactory.getLog(ThreadPoolSandboxModule.class);

    @Override
    public String id() {
        return "thread-pool";
    }

    @Override
    public void load(ModuleContext context) {
        ThreadPoolCollect.INSTANCE = new ThreadPoolCollect(context.instrumentation());
    }

    @Override
    public void active() {
        logger.info("[Sandbox-ThreadPool] active");
    }

    @Override
    public void frozen() {
        logger.info("[Sandbox-ThreadPool] frozen");
    }

    @Override
    public void unload() {
        logger.info("[Sandbox-ThreadPool] unload requested; JDK class transformer is kept until JVM restart");
    }
}
