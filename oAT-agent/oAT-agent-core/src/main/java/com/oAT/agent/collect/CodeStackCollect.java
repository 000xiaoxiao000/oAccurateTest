package com.oAT.agent.collect;

import com.oAT.agent.common.StackTraceFormatter;
import com.oAT.agent.common.StringUtils;
import com.oAT.agent.common.WildcardMatcher;
import com.oAT.agent.common.logger.Log;
import com.oAT.agent.common.logger.LogFactory;
import com.oAT.agent.jacoco.flow.ClassProbesAdapter;
import com.oAT.agent.jacoco.instr.ClassInfo;
import com.oAT.agent.jacoco.instr.ClassInstrumenter;
import com.oAT.agent.jacoco.instr.InstrSupport;
import com.oAT.agent.sandbox.core.SandboxEnhancementRegistry;
import com.oAT.agent.trace.TraceContext;
import com.oAT.shaded.asm97.*;

import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.Instrumentation;
import java.security.CodeSource;
import java.security.ProtectionDomain;

/**
 * 代码堆栈采集
 */
public class CodeStackCollect implements ClassFileTransformer {
    private final static Log logger = LogFactory.getLog(CodeStackCollect.class);
    public static CodeStackCollect INSTANCE;
    private final WildcardMatcher excludeInner;
    private final WildcardMatcher excludes;
    private final WildcardMatcher includes;
    private final WildcardMatcher excludeClassloader;
    private final Instrumentation instrumentation;
    private final SandboxEnhancementRegistry enhancementRegistry;
    private final String enhancementModuleId;

    public CodeStackCollect(TraceContext context, Instrumentation instrumentation) {
        this(context, instrumentation, null, null);
    }

    public CodeStackCollect(TraceContext context,
                            Instrumentation instrumentation,
                            SandboxEnhancementRegistry enhancementRegistry,
                            String enhancementModuleId) {
        try {
            //包含的代码堆栈表达式
            String includeExpr = context.getConfig("codeStack.include");
            if (StringUtils.isBlank(includeExpr)) {
                logger.warn("[Agent-使用本地（.conf）配置]目标应用配置为空");
                includeExpr = context.getConfig("conf_codeStack.include");
            }
            includes = new WildcardMatcher(StringUtils.isBlank(includeExpr) ? "" : includeExpr);
            //排除的代码堆栈表达式，排掉Spring的内部类，如$FastClassBySpringCGLIB$*和$EnhancerBySpringCGLIB$*
            String excludeExpr = context.getConfig("codeStack.exclude");
            if (!StringUtils.isBlank(excludeExpr)) {
                excludeExpr += "&$*$FastClassBySpringCGLIB$*&$*$EnhancerBySpringCGLIB$*&$*$EnhancerByCGLIB$*";
            } else {
                excludeExpr = "*$FastClassBySpringCGLIB$*&$*$EnhancerBySpringCGLIB$*&$*$EnhancerByCGLIB$*";
            }
            //默认排除 *$FastClassBySpringCGLIB$*&$*$EnhancerBySpringCGLIB$*
            excludes = new WildcardMatcher(excludeExpr);
            //排除的ClassLoader表达式
            String excludeClassloaderExpr = context.getConfig("codeStack.excludeClassloader");
            excludeClassloader = new WildcardMatcher(StringUtils.isBlank(excludeClassloaderExpr) ?
                    "sun.reflect.DelegatingClassLoader" : excludeClassloaderExpr);
            //排除监听器跟踪内部代码堆栈
            excludeInner = new WildcardMatcher("com.oAT.agent.*");
            this.instrumentation = instrumentation;
            this.enhancementRegistry = enhancementRegistry;
            this.enhancementModuleId = enhancementModuleId;


            //添加类转换器
            instrumentation.addTransformer(this, true);
        } catch (Throwable t) {
            logger.error("[Agent-EXCError]CodeStackCollect初始化异常: " + StackTraceFormatter.formatExceptionWithAgentMark(t));
            throw t instanceof RuntimeException ? (RuntimeException) t : new RuntimeException(t);
        }
    }

    /**
     * 自定义ClassWriter，避免找不到类时报错
     */
    private static class SafeClassWriter extends ClassWriter {
        private final ClassLoader loader;

        public SafeClassWriter(int flags, ClassLoader loader) {
            super(flags);
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
                } else {
                    do {
                        c1 = c1.getSuperclass();
                    } while (!c1.isAssignableFrom(c2));
                    return c1.getName().replace('.', '/');
                }
            } catch (Throwable e) {
                // 找不到类时，直接返回Object
                return "java/lang/Object";
            }
        }
    }

    @Override
    public byte[] transform(ClassLoader loader, String orgClassName, Class<?> classBeingRedefined,
                            ProtectionDomain protectionDomain, byte[] classfileBuffer) {
        try {
            if (orgClassName == null || loader == null
                    || orgClassName.startsWith("sun/")
                    || orgClassName.startsWith("jdk/")
                    || orgClassName.startsWith("com/sun/")
                    || orgClassName.startsWith("org/javassist/")
                    || orgClassName.startsWith("org/objectweb/asm/")
                    || orgClassName.startsWith("com/oAT/agent/") // 避免增强自身
                    || orgClassName.startsWith("com/oAT/shaded/")
                    || orgClassName.startsWith("com/intellij/")
                    || orgClassName.startsWith("org/slf4j/")
                    || orgClassName.startsWith("org/apache/logging/")
                    || orgClassName.startsWith("org/apache/commons/logging/")
                    || orgClassName.startsWith("org/springframework/")
                    || orgClassName.startsWith("org/yaml/snakeyaml/external/")) {
                return null;
            }

            String className = orgClassName.replace('/', '.');
            if (!doFilter(loader, className, protectionDomain)) {
                return null;
            }
            ClassReader reader;
            try {
                reader = new ClassReader(classfileBuffer);
            } catch (Throwable t) {
                logger.error("[Agent-EXCError]ClassReader 异常: " + StackTraceFormatter.formatExceptionWithAgentMark(t));
                return null;
            }
            if (InstrSupport.isInterface(reader)) {
                return null;
            }
            if (isAlreadyInstrumented(reader)) {
                return null;
            }
            // 使用自定义SafeClassWriter
            final ClassWriter writer = new SafeClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES,
                    loader);

            ClassInfo info = new ClassInfo(reader);
            final ClassVisitor visitor = new ClassProbesAdapter(new ClassInstrumenter(info, writer), true);

            try {
                reader.accept(visitor, ClassReader.EXPAND_FRAMES);
            } catch (Throwable t) {
                logger.error("[Agent-EXCError]ASM 字节码增强异常: " + className, t);
                return null;
            }

            logger.info("[Agent-info]完成 CodeStack 采集器初始化, " + className);
            if (enhancementRegistry != null) {
                enhancementRegistry.recordClass(enhancementModuleId, className);
            }
            return writer.toByteArray();
        } catch (Throwable t) {
            logger.error("[Agent-EXCError]transform 方法异常: " + StackTraceFormatter.formatExceptionWithAgentMark(t));
            // transform方法不能抛出异常，否则会影响JVM类加载，必须返回null
            return null;
        }
    }

    public void close() {
        try {
            instrumentation.removeTransformer(this);
        } catch (Throwable t) {
            logger.warn("[Agent-CodeStack]remove transformer failed: " + t.getMessage());
        }
    }

    private boolean isAlreadyInstrumented(ClassReader reader) {
        final boolean[] instrumented = new boolean[]{false};
        try {
            reader.accept(new ClassVisitor(InstrSupport.ASM_API_VERSION) {
                @Override
                public FieldVisitor visitField(int access, String name, String desc, String signature, Object value) {
                    if (InstrSupport.DATAFIELD_NAME.equals(name)) {
                        instrumented[0] = true;
                    }
                    return null;
                }

                @Override
                public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
                    if (InstrSupport.INITMETHOD_NAME.equals(name)) {
                        instrumented[0] = true;
                    }
                    return null;
                }
            }, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        } catch (Throwable ignored) {
            return false;
        }
        return instrumented[0];
    }

    private boolean doFilter(ClassLoader loader, String className, ProtectionDomain protectionDomain) {
        try {
            if (loader == null) {
                return false;
            }
            if (!hasSourceLocation(protectionDomain)) {
                return false;
            }
            if (excludeClassloader.matches(loader.getClass().getName())) {
                return false;
            }
            if (excludeInner.matches(className)) {
                return false;
            }
            if (excludes.matches(className)) {
                return false;
            }
            return matchesIncludedClass(className);
        } catch (Throwable t) {
            logger.warn("[Agent-EXCError]doFilter 异常: " + className, t);
            return false;
        }
    }

    private boolean matchesIncludedClass(String className) {
        if (includes.matches(className)) {
            return true;
        }
        if (className == null || className.indexOf('$') < 0) {
            return false;
        }

        String candidate = className;
        int dollarIndex = candidate.lastIndexOf('$');
        while (dollarIndex > 0) {
            candidate = candidate.substring(0, dollarIndex);
            if (includes.matches(candidate)) {
                return true;
            }
            dollarIndex = candidate.lastIndexOf('$');
        }
        return false;
    }

    /**
     * Checks whether this protection domain is associated with a source
     * location.
     *
     * @param protectionDomain protection domain to check (or <code>null</code>)
     * @return <code>true</code> if a source location is defined
     */
    private boolean hasSourceLocation(final ProtectionDomain protectionDomain) {
        try {
            if (protectionDomain == null) {
                return false;
            }
            final CodeSource codeSource = protectionDomain.getCodeSource();
            if (codeSource == null) {
                return false;
            }
            return codeSource.getLocation() != null;
        } catch (Throwable t) {
            logger.warn("[Agent-EXCError]hasSourceLocation 异常", t);
            return false;
        }
    }

}
