package com.oAT.agent.sandbox.core;

import com.oAT.agent.Agent;
import com.oAT.agent.common.StackTraceFormatter;
import com.oAT.agent.common.logger.Log;
import com.oAT.agent.common.logger.LogFactory;
import com.oAT.agent.sandbox.modules.BuiltinModuleLoader;
import com.oAT.agent.trace.TraceContext;

import java.lang.instrument.Instrumentation;
import java.util.Properties;

public class SandboxLauncher {
    private static final Log logger = LogFactory.getLog(SandboxLauncher.class);

    private static volatile SandboxRuntime runtime;

    private SandboxLauncher() {
    }

    public static synchronized SandboxRuntime start(String arg,
                                                    Instrumentation instrumentation,
                                                    StartMode startMode) {
        if (runtime != null && !runtime.stopped()) {
            logger.warn("[Sandbox] already started, mode=" + startMode);
            return runtime;
        }
        try {
            Properties properties = Agent.buildStartupProperties(arg);
            TraceContext traceContext = Agent.traceContext != null && Agent.traceContext.isRunning()
                    ? Agent.traceContext
                    : new TraceContext(properties, instrumentation);
            Agent.traceContext = traceContext;
            runtime = new SandboxRuntime(instrumentation, properties, traceContext);
            SpyInstaller.install(instrumentation, new SpyDispatcher(runtime.listenerRegistry()));
            BuiltinModuleLoader.load(runtime);
            SandboxStatusReporter.report(runtime, startMode);
            logger.info("[Sandbox] started, mode=" + startMode);
            return runtime;
        } catch (Throwable t) {
            logger.error("[Sandbox] start failed: " + StackTraceFormatter.formatExceptionWithAgentMark(t));
            throw t instanceof RuntimeException ? (RuntimeException) t : new RuntimeException(t);
        }
    }

    public static SandboxRuntime getRuntime() {
        return runtime;
    }

    public static synchronized void stop() {
        if (runtime == null || runtime.stopped()) {
            logger.warn("[Sandbox] already stopped");
            return;
        }
        try {
            runtime.stop();
            SandboxStatusReporter.reportNow(runtime, StartMode.ATTACH);
            SandboxStatusReporter.stop();
            logger.info("[Sandbox] stopped");
        } catch (Throwable t) {
            logger.error("[Sandbox] stop failed: " + StackTraceFormatter.formatExceptionWithAgentMark(t));
            throw t instanceof RuntimeException ? (RuntimeException) t : new RuntimeException(t);
        }
    }

    public static synchronized SandboxRuntime restart(String arg,
                                                      Instrumentation instrumentation,
                                                      StartMode startMode) {
        SandboxRuntime oldRuntime = runtime;
        stop();
        if (oldRuntime != null) {
            oldRuntime.closeTraceContext();
        }
        runtime = null;
        return start(arg, instrumentation, startMode);
    }
}
