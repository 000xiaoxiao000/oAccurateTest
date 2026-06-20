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
    private final SandboxTransformer transformer;
    private volatile boolean stopped;

    public SandboxRuntime(Instrumentation instrumentation, Properties properties, TraceContext traceContext) {
        this.instrumentation = instrumentation;
        this.properties = properties;
        this.traceContext = traceContext;
        this.moduleManager = new ModuleManager();
        this.listenerRegistry = new ListenerRegistry();
        this.eventWatcher = new DefaultEventWatcher(listenerRegistry);
        this.transformer = new SandboxTransformer(eventWatcher);
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
        return new SandboxContext(instrumentation, properties, traceContext, eventWatcher);
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
