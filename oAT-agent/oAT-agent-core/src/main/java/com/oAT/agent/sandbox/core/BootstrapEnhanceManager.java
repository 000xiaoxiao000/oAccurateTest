package com.oAT.agent.sandbox.core;

import com.oAT.agent.common.StackTraceFormatter;
import com.oAT.agent.common.logger.Log;
import com.oAT.agent.common.logger.LogFactory;
import com.oAT.agent.jacoco.instr.InstrSupport;
import com.oAT.shaded.asm97.ClassReader;
import com.oAT.shaded.asm97.ClassVisitor;
import com.oAT.shaded.asm97.ClassWriter;
import com.oAT.shaded.asm97.MethodVisitor;

import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.Instrumentation;
import java.security.ProtectionDomain;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class BootstrapEnhanceManager {
    private static final Log logger = LogFactory.getLog(BootstrapEnhanceManager.class);

    private final Instrumentation instrumentation;
    private final List definitions = Collections.synchronizedList(new ArrayList());
    private final Map statuses = Collections.synchronizedMap(new LinkedHashMap());
    private final ClassFileTransformer transformer;
    private final int javaVersion;
    private volatile boolean frozen;

    public BootstrapEnhanceManager(Instrumentation instrumentation) {
        this.instrumentation = instrumentation;
        this.javaVersion = detectJavaVersion();
        this.transformer = new BootstrapTransformer();
        this.instrumentation.addTransformer(transformer, true);
    }

    public void register(BootstrapEnhanceDefinition definition) {
        if (definition == null) {
            return;
        }
        BootstrapEnhanceStatus status = new BootstrapEnhanceStatus(definition.moduleId(), definition.className(),
                definition.methodName(), definition.descriptor());
        status.mark(definition.supportsJavaVersion(javaVersion) ? "REGISTERED" : "SKIPPED",
                definition.supportsJavaVersion(javaVersion) ? null : "unsupported java version: " + javaVersion);
        definitions.add(definition);
        statuses.put(statusKey(definition), status);
    }

    public void retransformRegisteredClasses() {
        if (!instrumentation.isRetransformClassesSupported()) {
            markAll("ERROR", "retransform unsupported");
            return;
        }
        Class[] loadedClasses = instrumentation.getAllLoadedClasses();
        for (int i = 0; i < loadedClasses.length; i++) {
            Class clazz = loadedClasses[i];
            if (clazz == null || !instrumentation.isModifiableClass(clazz)) {
                continue;
            }
            if (!hasDefinition(clazz.getName())) {
                continue;
            }
            try {
                instrumentation.retransformClasses(new Class[]{clazz});
            } catch (Throwable t) {
                markClass(clazz.getName(), "ERROR", t.getMessage());
                logger.warn("[Sandbox-Bootstrap] retransform failed, class=" + clazz.getName() + ", " + t.getMessage());
            }
        }
    }

    public void freezeModule(String moduleId) {
        frozen = true;
        synchronized (statuses) {
            java.util.Iterator iterator = statuses.values().iterator();
            while (iterator.hasNext()) {
                BootstrapEnhanceStatus status = (BootstrapEnhanceStatus) iterator.next();
                if (moduleId == null || moduleId.equals(status.moduleId())) {
                    status.mark("FROZEN", null);
                }
            }
        }
    }

    public List statuses() {
        synchronized (statuses) {
            return new ArrayList(statuses.values());
        }
    }

    public void stop() {
        try {
            instrumentation.removeTransformer(transformer);
        } catch (Throwable ignored) {
        }
    }

    private boolean hasDefinition(String dottedClassName) {
        synchronized (definitions) {
            for (int i = 0; i < definitions.size(); i++) {
                BootstrapEnhanceDefinition definition = (BootstrapEnhanceDefinition) definitions.get(i);
                if (definition.className().equals(dottedClassName)
                        && definition.supportsJavaVersion(javaVersion)) {
                    return true;
                }
            }
        }
        return false;
    }

    private List matchingDefinitions(String internalClassName) {
        List result = new ArrayList();
        synchronized (definitions) {
            for (int i = 0; i < definitions.size(); i++) {
                BootstrapEnhanceDefinition definition = (BootstrapEnhanceDefinition) definitions.get(i);
                if (definition.internalClassName().equals(internalClassName)
                        && definition.supportsJavaVersion(javaVersion)) {
                    result.add(definition);
                }
            }
        }
        return result;
    }

    private void markClass(String className, String state, String error) {
        synchronized (statuses) {
            java.util.Iterator iterator = statuses.values().iterator();
            while (iterator.hasNext()) {
                BootstrapEnhanceStatus status = (BootstrapEnhanceStatus) iterator.next();
                if (className.equals(status.className())) {
                    status.mark(state, error);
                }
            }
        }
    }

    private void markAll(String state, String error) {
        synchronized (statuses) {
            java.util.Iterator iterator = statuses.values().iterator();
            while (iterator.hasNext()) {
                ((BootstrapEnhanceStatus) iterator.next()).mark(state, error);
            }
        }
    }

    private String statusKey(BootstrapEnhanceDefinition definition) {
        return definition.moduleId() + "|" + definition.className() + "|" + definition.methodName() + "|"
                + definition.descriptor();
    }

    private int detectJavaVersion() {
        String version = System.getProperty("java.specification.version", "6");
        if (version.startsWith("1.")) {
            version = version.substring(2);
        }
        int dot = version.indexOf('.');
        if (dot > 0) {
            version = version.substring(0, dot);
        }
        try {
            return Integer.parseInt(version);
        } catch (Throwable ignored) {
            return 6;
        }
    }

    private class BootstrapTransformer implements ClassFileTransformer {
        @Override
        public byte[] transform(ClassLoader loader, String className, Class classBeingRedefined,
                                ProtectionDomain protectionDomain, byte[] classfileBuffer) {
            if (frozen || className == null || classfileBuffer == null) {
                return null;
            }
            List matched = matchingDefinitions(className);
            if (matched.isEmpty()) {
                return null;
            }
            final List methodDefinitions = matched;
            try {
                ClassReader reader = new ClassReader(classfileBuffer);
                ClassWriter writer = new SafeClassWriter(reader, ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES,
                        loader);
                ClassVisitor visitor = new ClassVisitor(InstrSupport.ASM_API_VERSION, writer) {
                    private String dottedClassName;

                    @Override
                    public void visit(int version, int access, String name, String signature, String superName,
                                      String[] interfaces) {
                        dottedClassName = name.replace('/', '.');
                        super.visit(version, access, name, signature, superName, interfaces);
                    }

                    @Override
                    public MethodVisitor visitMethod(int access, String name, String descriptor, String signature,
                                                     String[] exceptions) {
                        MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);
                        if (mv == null) {
                            return null;
                        }
                        for (int i = 0; i < methodDefinitions.size(); i++) {
                            BootstrapEnhanceDefinition definition =
                                    (BootstrapEnhanceDefinition) methodDefinitions.get(i);
                            if (definition.methodName().equals(name) && definition.descriptor().equals(descriptor)) {
                                mv = definition.enhancer().create(mv, access, name, descriptor);
                                BootstrapEnhanceStatus status = (BootstrapEnhanceStatus) statuses.get(statusKey(definition));
                                if (status != null) {
                                    status.mark("ACTIVE", null);
                                }
                            }
                        }
                        return mv;
                    }
                };
                reader.accept(visitor, ClassReader.EXPAND_FRAMES);
                return writer.toByteArray();
            } catch (Throwable t) {
                String dottedClassName = className.replace('/', '.');
                markClass(dottedClassName, "ERROR", t.getMessage());
                logger.error("[Sandbox-Bootstrap] transform failed, class=" + dottedClassName + " "
                        + StackTraceFormatter.formatExceptionWithAgentMark(t));
                return null;
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
                Class c1 = load(type1);
                Class c2 = load(type2);
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

        private Class load(String internalName) throws ClassNotFoundException {
            String className = internalName.replace('/', '.');
            ClassLoader classLoader = loader == null ? ClassLoader.getSystemClassLoader() : loader;
            return Class.forName(className, false, classLoader);
        }
    }
}
