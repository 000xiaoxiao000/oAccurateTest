package com.oAT.agent.sandbox.core;

import com.oAT.agent.sandbox.api.EventWatcher;
import com.oAT.agent.sandbox.api.ModuleContext;
import com.oAT.agent.trace.TraceContext;

import java.lang.instrument.Instrumentation;
import java.util.Properties;

public class SandboxContext implements ModuleContext {
    private final Instrumentation instrumentation;
    private final Properties properties;
    private final TraceContext traceContext;
    private final EventWatcher eventWatcher;

    public SandboxContext(Instrumentation instrumentation,
                          Properties properties,
                          TraceContext traceContext,
                          EventWatcher eventWatcher) {
        this.instrumentation = instrumentation;
        this.properties = properties;
        this.traceContext = traceContext;
        this.eventWatcher = eventWatcher;
    }

    @Override
    public Instrumentation instrumentation() {
        return instrumentation;
    }

    @Override
    public Properties properties() {
        return properties;
    }

    @Override
    public TraceContext traceContext() {
        return traceContext;
    }

    @Override
    public EventWatcher eventWatcher() {
        return eventWatcher;
    }
}
