package com.oAT.agent.collect;

import com.oAT.agent.common.StackTraceFormatter;
import com.oAT.agent.common.StringUtils;
import com.oAT.agent.common.WildcardMatcher;
import com.oAT.agent.common.logger.Log;
import com.oAT.agent.common.logger.LogFactory;
import com.oAT.agent.jacoco.data.CompactDataInput;
import com.oAT.agent.jacoco.data.CompactDataOutput;
import com.oAT.agent.jacoco.instr.ClassInfo;
import com.oAT.agent.jacoco.instr.InstrSupport;
import com.oAT.agent.trace.TraceContext;
import com.oAT.shaded.asm97.ClassReader;

import java.io.ByteArrayOutputStream;

import java.io.File;
import java.io.InputStream;
import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.Instrumentation;
import java.security.CodeSource;
import java.security.ProtectionDomain;
import java.util.Enumeration;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

public class CodeStaticStackCollect implements ClassFileTransformer {
    private final static Log logger = LogFactory.getLog(CodeStaticStackCollect.class);

    public static CodeStaticStackCollect INSTANCE;
    private final WildcardMatcher excludeInner;
    private final WildcardMatcher excludes;
    private final WildcardMatcher includes;
    private final WildcardMatcher excludeClassloader;
    private final Instrumentation instrumentation;

    public CodeStaticStackCollect(TraceContext context, Instrumentation instrumentation) {
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

            //添加类转换器
            instrumentation.addTransformer(this, true);

            // 启动全量类扫描，确保未加载的类也能被采集
            scanClassPath();
        } catch (Throwable t) {
            logger.error("[Agent-EXCError]CodeStaticStackCollect 初始化异常: " + StackTraceFormatter.formatExceptionWithAgentMark(t));
            throw t instanceof RuntimeException ? (RuntimeException) t : new RuntimeException(t);
        }
    }

    private void scanClassPath() {
        Thread scanner = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    String classPath = System.getProperty("java.class.path");
                    if (classPath == null) return;
                    String[] paths = classPath.split(File.pathSeparator);
                    for (String path : paths) {
                        File file = new File(path);
                        if (!file.exists()) continue;
                        if (file.isDirectory()) {
                            scanDirectory(file, "");
                        } else if (path.toLowerCase().endsWith(".jar") || path.toLowerCase().endsWith(".war")) {
                            scanJar(file);
                        }
                    }
                    logger.info("[Agent-StaticCode]Classpath scanning completed.");
                } catch (Throwable t) {
                    logger.warn("[Agent-StaticCode]Classpath scanning failed: " + t.getMessage());
                }
            }
        }, "oAT-static-code-scan");
        scanner.setDaemon(true);
        scanner.start();
    }

    private void scanDirectory(File dir, String pkg) {
        File[] files = dir.listFiles();
        if (files == null) return;
        for (File f : files) {
            if (f.isDirectory()) {
                scanDirectory(f, pkg + f.getName() + ".");
            } else if (f.getName().endsWith(".class")) {
                String className = pkg + f.getName().substring(0, f.getName().length() - 6);
                if (isTarget(className)) {
                    try {
                        byte[] bytes = readFile(f);
                        processClass(bytes, className);
                    } catch (Throwable t) {
                        // ignore
                    }
                }
            }
        }
    }

    private void scanJar(File jarFile) {
        JarFile jar = null;
        try {
            jar = new JarFile(jarFile);
            Enumeration<JarEntry> entries = jar.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                if (entry.getName().endsWith(".class")) {
                    String className = entry.getName().replace('/', '.');
                    className = className.substring(0, className.length() - 6);
                    if (isTarget(className)) {
                        InputStream is = null;
                        try {
                            is = jar.getInputStream(entry);
                            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
                            int nRead;
                            byte[] data = new byte[1024];
                            while ((nRead = is.read(data, 0, data.length)) != -1) {
                                buffer.write(data, 0, nRead);
                            }
                            processClass(buffer.toByteArray(), className);
                        } catch (Throwable t) {
                            // ignore
                        } finally {
                            if (is != null) {
                                try {
                                    is.close();
                                } catch (Throwable ignore) {
                                }
                            }
                        }
                    }
                }
            }
        } catch (Throwable t) {
            // ignore
        } finally {
            if (jar != null) {
                try {
                    jar.close();
                } catch (Throwable ignore) {
                }
            }
        }
    }

    private byte[] readFile(File file) throws java.io.IOException {
        InputStream input = null;
        try {
            input = new java.io.FileInputStream(file);
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int read;
            while ((read = input.read(buffer)) != -1) {
                output.write(buffer, 0, read);
            }
            return output.toByteArray();
        } finally {
            if (input != null) {
                try {
                    input.close();
                } catch (Throwable ignore) {
                }
            }
        }
    }

    private boolean isTarget(String className) {
        try {
            if (excludeInner.matches(className)) return false;
            if (excludes.matches(className)) return false;
            return includes.matches(className);
        } catch (Throwable t) {
            return false;
        }
    }

    private void processClass(byte[] bytes, String className) {
        try {
            ClassReader reader = new ClassReader(bytes);
            if (InstrSupport.isInterface(reader)) return;
            ClassInfo info = new ClassInfo(reader);
            CompactDataInput.collectClassStaticInfo(info);
        } catch (Throwable t) {
            logger.warn("[Agent-StaticCode]Failed to process scanned class: " + className);
        }
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
            return includes.matches(className);
        } catch (Throwable t) {
            logger.warn("[Agent-EXCError]doFilter 异常: " + className, t);
            return false;
        }
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
                logger.warn("[Agent-EXCError]ClassReader 异常: " + StackTraceFormatter.formatExceptionWithAgentMark(t));
                return null;
            }
            if (InstrSupport.isInterface(reader)) {
                return null;
            }

            ClassInfo info = new ClassInfo(reader);

            // 收集全量静态代码数据
            try {
                CompactDataInput.collectClassStaticInfo(info);
            } catch (Throwable collectErr) {
                logger.warn("[Agent-StaticCode]收集静态类信息失败: " + className + ", " + collectErr.getMessage());
            }

            return null;
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
            logger.warn("[Agent-StaticCode]remove transformer failed: " + t.getMessage());
        }
    }

}
