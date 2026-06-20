package com.oAT.agent.sandbox.modules.context;

import com.oAT.agent.common.logger.Log;
import com.oAT.agent.common.logger.LogFactory;
import com.oAT.agent.jacoco.instr.InstrSupport;
import com.oAT.agent.sandbox.api.ModuleContext;
import com.oAT.agent.sandbox.api.OatModule;
import com.oAT.agent.sandbox.core.BootstrapBridgeInstaller;
import com.oAT.agent.sandbox.core.BootstrapEnhanceDefinition;
import com.oAT.agent.sandbox.core.BootstrapEnhanceManager;
import com.oAT.agent.sandbox.core.BootstrapMethodEnhancer;
import com.oAT.shaded.asm97.MethodVisitor;
import com.oAT.shaded.asm97.Type;
import com.oAT.shaded.asm97.commons.AdviceAdapter;
import com.oAT.shaded.asm97.commons.Method;

public class ContextPropagationSandboxModule implements OatModule {
    private static final Log logger = LogFactory.getLog(ContextPropagationSandboxModule.class);
    private static final String MODULE_ID = "context-propagation";
    private ModuleContext context;

    @Override
    public String id() {
        return MODULE_ID;
    }

    @Override
    public void load(ModuleContext context) {
        this.context = context;
        BootstrapBridgeInstaller.install(context.instrumentation(), new AgentContextBridge());
        BootstrapEnhanceManager manager = (BootstrapEnhanceManager) context.bootstrapEnhanceManager();
        if (Boolean.parseBoolean(context.properties().getProperty("sandbox.context-propagation.executor", "true"))) {
            registerExecutor(manager);
        }
        if (Boolean.parseBoolean(context.properties().getProperty("sandbox.context-propagation.scheduled", "true"))) {
            registerScheduled(manager);
        }
        if (Boolean.parseBoolean(context.properties().getProperty("sandbox.context-propagation.spring", "true"))) {
            registerSpring(manager);
        }
        manager.retransformRegisteredClasses();
    }

    @Override
    public void active() {
        logger.info("[Sandbox-ContextPropagation] active");
    }

    @Override
    public void frozen() {
        BootstrapBridgeInstaller.freeze();
        if (context != null) {
            ((BootstrapEnhanceManager) context.bootstrapEnhanceManager()).freezeModule(MODULE_ID);
        }
        logger.info("[Sandbox-ContextPropagation] frozen");
    }

    @Override
    public void unload() {
        frozen();
        logger.info("[Sandbox-ContextPropagation] unloaded; enhanced JDK classes stay fail-open until JVM restart");
    }

    private void registerExecutor(BootstrapEnhanceManager manager) {
        manager.register(new BootstrapEnhanceDefinition(MODULE_ID, "java.util.concurrent.ThreadPoolExecutor",
                "execute", "(Ljava/lang/Runnable;)V", new WrapFirstRunnableEnhancer()));
        manager.register(new BootstrapEnhanceDefinition(MODULE_ID, "java.util.concurrent.AbstractExecutorService",
                "submit", "(Ljava/lang/Runnable;)Ljava/util/concurrent/Future;", new WrapFirstRunnableEnhancer()));
        manager.register(new BootstrapEnhanceDefinition(MODULE_ID, "java.util.concurrent.AbstractExecutorService",
                "submit", "(Ljava/lang/Runnable;Ljava/lang/Object;)Ljava/util/concurrent/Future;",
                new WrapFirstRunnableEnhancer()));
        manager.register(new BootstrapEnhanceDefinition(MODULE_ID, "java.util.concurrent.AbstractExecutorService",
                "submit", "(Ljava/util/concurrent/Callable;)Ljava/util/concurrent/Future;",
                new WrapFirstCallableEnhancer()));
        manager.register(new BootstrapEnhanceDefinition(MODULE_ID, "java.util.concurrent.AbstractExecutorService",
                "invokeAll", "(Ljava/util/Collection;)Ljava/util/List;", new WrapFirstCollectionEnhancer()));
        manager.register(new BootstrapEnhanceDefinition(MODULE_ID, "java.util.concurrent.AbstractExecutorService",
                "invokeAll", "(Ljava/util/Collection;JLjava/util/concurrent/TimeUnit;)Ljava/util/List;",
                new WrapFirstCollectionEnhancer()));
        manager.register(new BootstrapEnhanceDefinition(MODULE_ID, "java.util.concurrent.AbstractExecutorService",
                "invokeAny", "(Ljava/util/Collection;)Ljava/lang/Object;", new WrapFirstCollectionEnhancer()));
        manager.register(new BootstrapEnhanceDefinition(MODULE_ID, "java.util.concurrent.AbstractExecutorService",
                "invokeAny", "(Ljava/util/Collection;JLjava/util/concurrent/TimeUnit;)Ljava/lang/Object;",
                new WrapFirstCollectionEnhancer()));
    }

    private void registerScheduled(BootstrapEnhanceManager manager) {
        manager.register(new BootstrapEnhanceDefinition(MODULE_ID,
                "java.util.concurrent.ScheduledThreadPoolExecutor", "schedule",
                "(Ljava/lang/Runnable;JLjava/util/concurrent/TimeUnit;)Ljava/util/concurrent/ScheduledFuture;",
                new WrapFirstRunnableEnhancer()));
        manager.register(new BootstrapEnhanceDefinition(MODULE_ID,
                "java.util.concurrent.ScheduledThreadPoolExecutor", "schedule",
                "(Ljava/util/concurrent/Callable;JLjava/util/concurrent/TimeUnit;)Ljava/util/concurrent/ScheduledFuture;",
                new WrapFirstCallableEnhancer()));
        manager.register(new BootstrapEnhanceDefinition(MODULE_ID,
                "java.util.concurrent.ScheduledThreadPoolExecutor", "scheduleAtFixedRate",
                "(Ljava/lang/Runnable;JJLjava/util/concurrent/TimeUnit;)Ljava/util/concurrent/ScheduledFuture;",
                new WrapFirstRunnableEnhancer()));
        manager.register(new BootstrapEnhanceDefinition(MODULE_ID,
                "java.util.concurrent.ScheduledThreadPoolExecutor", "scheduleWithFixedDelay",
                "(Ljava/lang/Runnable;JJLjava/util/concurrent/TimeUnit;)Ljava/util/concurrent/ScheduledFuture;",
                new WrapFirstRunnableEnhancer()));
    }

    private void registerSpring(BootstrapEnhanceManager manager) {
        manager.register(new BootstrapEnhanceDefinition(MODULE_ID,
                "org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor", "execute",
                "(Ljava/lang/Runnable;)V", new WrapFirstRunnableEnhancer()));
        manager.register(new BootstrapEnhanceDefinition(MODULE_ID,
                "org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor", "execute",
                "(Ljava/lang/Runnable;J)V", new WrapFirstRunnableEnhancer()));
        manager.register(new BootstrapEnhanceDefinition(MODULE_ID,
                "org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor", "submit",
                "(Ljava/lang/Runnable;)Ljava/util/concurrent/Future;", new WrapFirstRunnableEnhancer()));
        manager.register(new BootstrapEnhanceDefinition(MODULE_ID,
                "org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor", "submit",
                "(Ljava/util/concurrent/Callable;)Ljava/util/concurrent/Future;", new WrapFirstCallableEnhancer()));
        manager.register(new BootstrapEnhanceDefinition(MODULE_ID,
                "org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor", "submitListenable",
                "(Ljava/lang/Runnable;)Lorg/springframework/util/concurrent/ListenableFuture;",
                new WrapFirstRunnableEnhancer()));
        manager.register(new BootstrapEnhanceDefinition(MODULE_ID,
                "org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor", "submitListenable",
                "(Ljava/util/concurrent/Callable;)Lorg/springframework/util/concurrent/ListenableFuture;",
                new WrapFirstCallableEnhancer()));
        manager.register(new BootstrapEnhanceDefinition(MODULE_ID,
                "org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler", "schedule",
                "(Ljava/lang/Runnable;Ljava/util/Date;)Ljava/util/concurrent/ScheduledFuture;",
                new WrapFirstRunnableEnhancer()));
        manager.register(new BootstrapEnhanceDefinition(MODULE_ID,
                "org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler", "schedule",
                "(Ljava/lang/Runnable;Lorg/springframework/scheduling/Trigger;)Ljava/util/concurrent/ScheduledFuture;",
                new WrapFirstRunnableEnhancer()));
    }

    private static class WrapFirstRunnableEnhancer implements BootstrapMethodEnhancer {
        @Override
        public MethodVisitor create(MethodVisitor methodVisitor, int access, String name, String descriptor) {
            return new WrapFirstArgumentAdapter(methodVisitor, access, name, descriptor,
                    "wrap", "(Ljava/lang/Runnable;)Ljava/lang/Runnable;",
                    Type.getType(Runnable.class));
        }
    }

    private static class WrapFirstCallableEnhancer implements BootstrapMethodEnhancer {
        @Override
        public MethodVisitor create(MethodVisitor methodVisitor, int access, String name, String descriptor) {
            return new WrapFirstArgumentAdapter(methodVisitor, access, name, descriptor,
                    "wrap", "(Ljava/util/concurrent/Callable;)Ljava/util/concurrent/Callable;",
                    Type.getType(java.util.concurrent.Callable.class));
        }
    }

    private static class WrapFirstCollectionEnhancer implements BootstrapMethodEnhancer {
        @Override
        public MethodVisitor create(MethodVisitor methodVisitor, int access, String name, String descriptor) {
            return new WrapFirstArgumentAdapter(methodVisitor, access, name, descriptor,
                    "wrap", "(Ljava/util/Collection;)Ljava/util/Collection;",
                    Type.getType(java.util.Collection.class));
        }
    }

    private static class WrapFirstArgumentAdapter extends AdviceAdapter {
        private final String bridgeMethodName;
        private final String bridgeMethodDescriptor;
        private final Type argumentType;

        protected WrapFirstArgumentAdapter(MethodVisitor mv, int access, String name, String descriptor,
                                           String bridgeMethodName, String bridgeMethodDescriptor, Type argumentType) {
            super(InstrSupport.ASM_API_VERSION, mv, access, name, descriptor);
            this.bridgeMethodName = bridgeMethodName;
            this.bridgeMethodDescriptor = bridgeMethodDescriptor;
            this.argumentType = argumentType;
        }

        @Override
        protected void onMethodEnter() {
            loadArg(0);
            invokeStatic(Type.getObjectType("com/oAT/agent/bootstrap/OatAsyncBridge"),
                    new Method(bridgeMethodName, bridgeMethodDescriptor));
            checkCast(argumentType);
            storeArg(0);
        }

        @Override
        protected void onMethodExit(int opcode) {
            if (opcode != ATHROW) {
                return;
            }
            loadArg(0);
            invokeStatic(Type.getObjectType("com/oAT/agent/bootstrap/OatAsyncBridge"),
                    new Method("release", "(Ljava/lang/Object;)V"));
        }
    }
}
