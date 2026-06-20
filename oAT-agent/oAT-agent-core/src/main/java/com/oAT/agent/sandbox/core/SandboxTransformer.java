package com.oAT.agent.sandbox.core;

import com.oAT.agent.common.StackTraceFormatter;
import com.oAT.agent.common.logger.Log;
import com.oAT.agent.common.logger.LogFactory;
import com.oAT.agent.jacoco.instr.InstrSupport;
import com.oAT.agent.sandbox.api.EventType;
import com.oAT.agent.sandbox.spy.OatSpy;
import com.oAT.shaded.asm97.ClassReader;
import com.oAT.shaded.asm97.ClassVisitor;
import com.oAT.shaded.asm97.ClassWriter;
import com.oAT.shaded.asm97.Label;
import com.oAT.shaded.asm97.MethodVisitor;
import com.oAT.shaded.asm97.Opcodes;
import com.oAT.shaded.asm97.Type;
import com.oAT.shaded.asm97.commons.AdviceAdapter;

import java.lang.instrument.ClassFileTransformer;
import java.security.ProtectionDomain;
import java.util.ArrayList;
import java.util.List;

public class SandboxTransformer implements ClassFileTransformer {
    private static final Log logger = LogFactory.getLog(SandboxTransformer.class);
    private static final String SPY_INTERNAL_NAME = OatSpy.class.getName().replace('.', '/');

    private final DefaultEventWatcher eventWatcher;
    private final SandboxEnhancementRegistry enhancementRegistry;

    public SandboxTransformer(DefaultEventWatcher eventWatcher, SandboxEnhancementRegistry enhancementRegistry) {
        this.eventWatcher = eventWatcher;
        this.enhancementRegistry = enhancementRegistry;
    }

    @Override
    public byte[] transform(ClassLoader loader,
                            String className,
                            Class<?> classBeingRedefined,
                            ProtectionDomain protectionDomain,
                            byte[] classfileBuffer) {
        if (className == null || classfileBuffer == null || shouldSkip(className)) {
            return null;
        }
        String dottedClassName = className.replace('/', '.');
        List<WatchDefinition> classWatches = matchedClassWatches(loader, dottedClassName);
        if (classWatches.isEmpty()) {
            return null;
        }
        try {
            ClassReader reader = new ClassReader(classfileBuffer);
            ClassWriter writer = new SafeClassWriter(reader, ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES,
                    loader);
            List<PendingEnhancement> pendingEnhancements = new ArrayList<PendingEnhancement>();
            ClassVisitor visitor = new SandboxClassVisitor(writer, loader, dottedClassName, classWatches,
                    pendingEnhancements);
            reader.accept(visitor, ClassReader.EXPAND_FRAMES);
            byte[] enhanced = writer.toByteArray();
            for (PendingEnhancement pending : pendingEnhancements) {
                enhancementRegistry.record(pending.moduleId, pending.className, pending.methodName,
                        pending.descriptor);
            }
            return enhanced;
        } catch (Throwable t) {
            logger.error("[Sandbox] transform failed, class=" + dottedClassName + " "
                    + StackTraceFormatter.formatExceptionWithAgentMark(t));
            return null;
        }
    }

    private boolean shouldSkip(String className) {
        return className.startsWith("com/oAT/agent/")
                || className.startsWith("com/oAT/shaded/")
                || className.startsWith("java/")
                || className.startsWith("sun/")
                || className.startsWith("jdk/")
                || className.startsWith("com/sun/");
    }

    private List<WatchDefinition> matchedClassWatches(ClassLoader loader, String dottedClassName) {
        List<WatchDefinition> result = new ArrayList<WatchDefinition>();
        for (WatchDefinition watch : eventWatcher.snapshot()) {
            try {
                if (watch.classMatcher().matches(loader, dottedClassName)) {
                    result.add(watch);
                }
            } catch (Throwable t) {
                logger.warn("[Sandbox] class matcher failed, class=" + dottedClassName, t);
            }
        }
        return result;
    }

    private static class SandboxClassVisitor extends ClassVisitor {
        private final ClassLoader loader;
        private final String className;
        private final List<WatchDefinition> classWatches;
        private final List<PendingEnhancement> pendingEnhancements;
        private String internalClassName;

        SandboxClassVisitor(ClassVisitor cv,
                            ClassLoader loader,
                            String className,
                            List<WatchDefinition> classWatches,
                            List<PendingEnhancement> pendingEnhancements) {
            super(InstrSupport.ASM_API_VERSION, cv);
            this.loader = loader;
            this.className = className;
            this.classWatches = classWatches;
            this.pendingEnhancements = pendingEnhancements;
        }

        @Override
        public void visit(int version,
                          int access,
                          String name,
                          String signature,
                          String superName,
                          String[] interfaces) {
            this.internalClassName = name;
            super.visit(version, access, name, signature, superName, interfaces);
        }

        @Override
        public MethodVisitor visitMethod(int access,
                                         String name,
                                         String descriptor,
                                         String signature,
                                         String[] exceptions) {
            MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);
            if (mv == null || "<clinit>".equals(name)
                    || (access & Opcodes.ACC_NATIVE) != 0 || (access & Opcodes.ACC_ABSTRACT) != 0) {
                return mv;
            }
            List<WatchDefinition> methodWatches = new ArrayList<WatchDefinition>();
            for (WatchDefinition watch : classWatches) {
                if (watch.methodMatcher().matches(name, descriptor)) {
                    methodWatches.add(watch);
                }
            }
            if (methodWatches.isEmpty()) {
                return mv;
            }
            for (WatchDefinition watch : methodWatches) {
                pendingEnhancements.add(new PendingEnhancement(watch.moduleId(), className, name, descriptor));
            }
            return new SandboxAdviceAdapter(mv, access, name, descriptor, loader, className, internalClassName,
                    methodWatches);
        }
    }

    private static class PendingEnhancement {
        private final String moduleId;
        private final String className;
        private final String methodName;
        private final String descriptor;

        private PendingEnhancement(String moduleId, String className, String methodName, String descriptor) {
            this.moduleId = moduleId;
            this.className = className;
            this.methodName = methodName;
            this.descriptor = descriptor;
        }
    }

    private static class SandboxAdviceAdapter extends AdviceAdapter {
        private final ClassLoader loader;
        private final String className;
        private final String internalClassName;
        private final String methodName;
        private final String descriptor;
        private final int access;
        private final List<WatchDefinition> methodWatches;
        private final List<Integer> invokeIdLocals = new ArrayList<Integer>();
        private final Label startLabel = new Label();
        private final Label endLabel = new Label();

        SandboxAdviceAdapter(MethodVisitor mv,
                             int access,
                             String methodName,
                             String descriptor,
                             ClassLoader loader,
                             String className,
                             String internalClassName,
                             List<WatchDefinition> methodWatches) {
            super(InstrSupport.ASM_API_VERSION, mv, access, methodName, descriptor);
            this.loader = loader;
            this.className = className;
            this.internalClassName = internalClassName;
            this.methodName = methodName;
            this.descriptor = descriptor;
            this.access = access;
            this.methodWatches = methodWatches;
        }

        @Override
        protected void onMethodEnter() {
            for (WatchDefinition watch : methodWatches) {
                int invokeIdLocal = newLocal(Type.LONG_TYPE);
                invokeIdLocals.add(invokeIdLocal);
                push(0L);
                storeLocal(invokeIdLocal, Type.LONG_TYPE);
            }
            mark(startLabel);
            for (int i = 0; i < methodWatches.size(); i++) {
                WatchDefinition watch = methodWatches.get(i);
                push("oAT");
                push(watch.listenerId());
                loadClassLoader();
                push(className);
                push(methodName);
                push(descriptor);
                loadThisOrNull();
                loadArgArray();
                invokeStatic(Type.getType(OatSpy.class), new com.oAT.shaded.asm97.commons.Method(
                        "onBefore",
                        "(Ljava/lang/String;JLjava/lang/ClassLoader;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/Object;[Ljava/lang/Object;)J"));
                storeLocal(invokeIdLocals.get(i), Type.LONG_TYPE);
            }
        }

        @Override
        public void visitMaxs(int maxStack, int maxLocals) {
            mark(endLabel);
            Label handlerLabel = new Label();
            visitTryCatchBlock(startLabel, endLabel, handlerLabel, "java/lang/Throwable");
            mark(handlerLabel);
            int throwableLocal = newLocal(Type.getType(Throwable.class));
            storeLocal(throwableLocal);
            for (int i = methodWatches.size() - 1; i >= 0; i--) {
                WatchDefinition watch = methodWatches.get(i);
                if (!watch.eventTypes().contains(EventType.THROWS)) {
                    continue;
                }
                push(watch.listenerId());
                loadLocal(invokeIdLocals.get(i), Type.LONG_TYPE);
                loadLocal(throwableLocal);
                invokeStatic(Type.getType(OatSpy.class), new com.oAT.shaded.asm97.commons.Method(
                        "onThrows", "(JJLjava/lang/Throwable;)V"));
            }
            loadLocal(throwableLocal);
            throwException();
            super.visitMaxs(maxStack, maxLocals);
        }

        @Override
        protected void onMethodExit(int opcode) {
            if (opcode == ATHROW) {
                return;
            }
            Type returnType = Type.getReturnType(descriptor);
            int returnLocal = storeReturnValue(opcode, returnType);
            dispatchReturnEvents(opcode, returnType, returnLocal);
            loadReturnValueForReturn(opcode, returnType, returnLocal);
        }

        private void loadClassLoader() {
            visitLdcInsn(Type.getObjectType(internalClassName));
            invokeVirtual(Type.getType(Class.class), new com.oAT.shaded.asm97.commons.Method(
                    "getClassLoader", "()Ljava/lang/ClassLoader;"));
        }

        private void loadThisOrNull() {
            if ((access & Opcodes.ACC_STATIC) != 0) {
                visitInsn(ACONST_NULL);
            } else {
                loadThis();
            }
        }

        private int storeReturnValue(int opcode, Type returnType) {
            if (opcode == RETURN) {
                return -1;
            }
            int local = newLocal(returnType);
            storeLocal(local, returnType);
            return local;
        }

        private void dispatchReturnEvents(int opcode, Type returnType, int returnLocal) {
            for (int i = methodWatches.size() - 1; i >= 0; i--) {
                WatchDefinition watch = methodWatches.get(i);
                if (!watch.eventTypes().contains(EventType.RETURN)) {
                    continue;
                }
                push(watch.listenerId());
                loadLocal(invokeIdLocals.get(i), Type.LONG_TYPE);
                loadReturnEventValue(opcode, returnType, returnLocal);
                invokeStatic(Type.getType(OatSpy.class), new com.oAT.shaded.asm97.commons.Method(
                        "onReturn", "(JJLjava/lang/Object;)Ljava/lang/Object;"));
                if (opcode == ARETURN) {
                    checkCast(returnType);
                    storeLocal(returnLocal, returnType);
                } else {
                    pop();
                }
            }
        }

        private void loadReturnEventValue(int opcode, Type returnType, int returnLocal) {
            if (opcode == RETURN) {
                visitInsn(ACONST_NULL);
                return;
            }
            loadLocal(returnLocal, returnType);
            if (opcode != ARETURN) {
                box(returnType);
            }
        }

        private void loadReturnValueForReturn(int opcode, Type returnType, int returnLocal) {
            if (opcode != RETURN) {
                loadLocal(returnLocal, returnType);
            }
        }
    }

    private static class SafeClassWriter extends ClassWriter {
        private final ClassLoader loader;

        SafeClassWriter(ClassReader reader, int flags, ClassLoader loader) {
            super(reader, flags);
            this.loader = loader;
        }

        @Override
        protected String getCommonSuperClass(String type1, String type2) {
            try {
                Class<?> c1 = Class.forName(type1.replace('/', '.'), false, loader);
                Class<?> c2 = Class.forName(type2.replace('/', '.'), false, loader);
                if (c1.isAssignableFrom(c2)) {
                    return type1;
                }
                if (c2.isAssignableFrom(c1)) {
                    return type2;
                }
                if (c1.isInterface() || c2.isInterface()) {
                    return "java/lang/Object";
                }
                do {
                    c1 = c1.getSuperclass();
                } while (c1 != null && !c1.isAssignableFrom(c2));
                return c1 == null ? "java/lang/Object" : c1.getName().replace('.', '/');
            } catch (Throwable ignored) {
                return "java/lang/Object";
            }
        }
    }
}
