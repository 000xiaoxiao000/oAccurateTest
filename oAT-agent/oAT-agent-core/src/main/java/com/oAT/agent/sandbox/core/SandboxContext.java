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
    private final BootstrapEnhanceManager bootstrapEnhanceManager;
    private final SandboxEnhancementRegistry enhancementRegistry;
    private final String moduleId;

    public SandboxContext(Instrumentation instrumentation,
                          Properties properties,
                          TraceContext traceContext,
                          EventWatcher eventWatcher,
                          BootstrapEnhanceManager bootstrapEnhanceManager) {
        this(instrumentation, properties, traceContext, eventWatcher, bootstrapEnhanceManager, null, "");
    }

    public SandboxContext(Instrumentation instrumentation,
                          Properties properties,
                          TraceContext traceContext,
                          EventWatcher eventWatcher,
                          BootstrapEnhanceManager bootstrapEnhanceManager,
                          SandboxEnhancementRegistry enhancementRegistry) {
        this(instrumentation, properties, traceContext, eventWatcher, bootstrapEnhanceManager,
                enhancementRegistry, "");
    }

    private SandboxContext(Instrumentation instrumentation,
                           Properties properties,
                           TraceContext traceContext,
                           EventWatcher eventWatcher,
                           BootstrapEnhanceManager bootstrapEnhanceManager,
                           SandboxEnhancementRegistry enhancementRegistry,
                           String moduleId) {
        this.instrumentation = instrumentation;
        this.properties = properties;
        this.traceContext = traceContext;
        this.eventWatcher = eventWatcher;
        this.bootstrapEnhanceManager = bootstrapEnhanceManager;
        this.enhancementRegistry = enhancementRegistry;
        this.moduleId = moduleId;
    }

    public SandboxContext withModuleId(String moduleId) {
        EventWatcher scopedWatcher = eventWatcher;
        if (eventWatcher instanceof DefaultEventWatcher) {
            scopedWatcher = new ModuleScopedEventWatcher((DefaultEventWatcher) eventWatcher, moduleId);
        }
        return new SandboxContext(instrumentation, properties, traceContext, scopedWatcher,
                bootstrapEnhanceManager, enhancementRegistry, moduleId);
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

    @Override
    public Object bootstrapEnhanceManager() {
        return bootstrapEnhanceManager;
    }

    public String moduleId() {
        return moduleId;
    }

    public SandboxEnhancementRegistry enhancementRegistry() {
        return enhancementRegistry;
    }
}
