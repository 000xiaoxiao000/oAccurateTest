package com.oAT.agent.sandbox.core;

import com.oAT.agent.trace.TraceContext;

import java.lang.instrument.Instrumentation;
import java.util.Properties;

public class SandboxRuntime {
    private final Instrumentation instrumentation;
    private final Properties properties;
    private final TraceContext traceContext;
    private final ModuleManager moduleManager;
    private final ListenerRegistry listenerRegistry;
    private final DefaultEventWatcher eventWatcher;
    private final SandboxEnhancementRegistry enhancementRegistry;
    private final SandboxTransformer transformer;
    private final BootstrapEnhanceManager bootstrapEnhanceManager;
    private volatile boolean stopped;

    public SandboxRuntime(Instrumentation instrumentation, Properties properties, TraceContext traceContext) {
        this.instrumentation = instrumentation;
        this.properties = properties;
        this.traceContext = traceContext;
        this.moduleManager = new ModuleManager();
        this.listenerRegistry = new ListenerRegistry();
        this.eventWatcher = new DefaultEventWatcher(listenerRegistry);
        this.enhancementRegistry = new SandboxEnhancementRegistry();
        this.transformer = new SandboxTransformer(eventWatcher, enhancementRegistry);
        this.bootstrapEnhanceManager = new BootstrapEnhanceManager(instrumentation);
        this.instrumentation.addTransformer(transformer, true);
    }

    public Instrumentation instrumentation() {
        return instrumentation;
    }

    public Properties properties() {
        return properties;
    }

    public TraceContext traceContext() {
        return traceContext;
    }

    public ModuleManager moduleManager() {
        return moduleManager;
    }

    public SandboxContext moduleContext() {
        return new SandboxContext(instrumentation, properties, traceContext, eventWatcher, bootstrapEnhanceManager,
                enhancementRegistry);
    }

    public ListenerRegistry listenerRegistry() {
        return listenerRegistry;
    }

    public DefaultEventWatcher eventWatcher() {
        return eventWatcher;
    }

    public SandboxTransformer transformer() {
        return transformer;
    }

    public SandboxEnhancementRegistry enhancementRegistry() {
        return enhancementRegistry;
    }

    public BootstrapEnhanceManager bootstrapEnhanceManager() {
        return bootstrapEnhanceManager;
    }

    public void retransformMatchedLoadedClasses() {
        if (!instrumentation.isRetransformClassesSupported()) {
            return;
        }
        Class<?>[] loadedClasses = instrumentation.getAllLoadedClasses();
        for (Class<?> clazz : loadedClasses) {
            if (clazz == null || !instrumentation.isModifiableClass(clazz)) {
                continue;
            }
            try {
                String className = clazz.getName();
                for (WatchDefinition watch : eventWatcher.snapshot()) {
                    if (watch.classMatcher().matches(clazz.getClassLoader(), className)) {
                        instrumentation.retransformClasses(clazz);
                        break;
                    }
                }
            } catch (Throwable ignored) {
            }
        }
    }

    public synchronized void stop() {
        if (stopped) {
            return;
        }
        moduleManager.unloadAll();
        try {
            instrumentation.removeTransformer(transformer);
        } catch (Throwable ignored) {
        }
        bootstrapEnhanceManager.stop();
        stopped = true;
    }

    public boolean stopped() {
        return stopped;
    }

    public void closeTraceContext() {
        try {
            traceContext.close();
        } catch (Throwable ignored) {
        }
    }
}
