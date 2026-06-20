package com.oAT.agent.sandbox.api;

import java.lang.instrument.Instrumentation;
import java.util.Properties;

public interface ModuleContext {
    Instrumentation instrumentation();

    Properties properties();

    Object traceContext();

    EventWatcher eventWatcher();

    Object bootstrapEnhanceManager();
}
