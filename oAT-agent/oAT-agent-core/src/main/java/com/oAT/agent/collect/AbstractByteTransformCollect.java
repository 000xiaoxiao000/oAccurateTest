package com.oAT.agent.collect;

import com.oAT.agent.common.StackTraceFormatter;
import com.oAT.agent.common.logger.Log;
import com.oAT.agent.common.logger.LogFactory;
import com.oAT.agent.model.Error;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.io.UnsupportedEncodingException;
import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.Instrumentation;
import java.security.ProtectionDomain;
import java.util.concurrent.Callable;

public abstract class AbstractByteTransformCollect {
    private final static Log logger = LogFactory.getLog(AbstractByteTransformCollect.class);

    protected AbstractByteTransformCollect() {
    }

    public AbstractByteTransformCollect(Instrumentation instrumentation) {
        instrumentation.addTransformer(new ClassFileTransformer() {
            @Override
            public byte[] transform(ClassLoader loader, String className, Class<?> classBeingRedefined, ProtectionDomain protectionDomain, byte[] classfileBuffer) {
                if (className == null
                        || (loader == null && !isBootstrapTarget(className))
                        || className.startsWith("sun/")
                        || className.startsWith("jdk/")
                        || className.startsWith("com/sun/")
                        || className.startsWith("org/javassist/")
                        || className.startsWith("org/objectweb/asm/")
                        || className.startsWith("com/jettech/jettofocus/agent/") // 避免增强自身
                        || className.startsWith("com/jettech/shaded/")
                        || className.startsWith("com/intellij/")
                        || className.startsWith("org/slf4j/")
                        || className.startsWith("org/apache/logging/")
                        || className.startsWith("org/apache/commons/logging/")
                        || className.startsWith("org/springframework/")
                        || className.startsWith("org/yaml/snakeyaml/external/")
                ) {
                    return null;
                }
                className = className.trim().replace("/", ".");
                try {
                    return AbstractByteTransformCollect.this.transform(loader, className, protectionDomain,
                            classfileBuffer);
                } catch (Throwable e) {
                    logger.error("[Agent-EXCError]类插桩转换失败:" + StackTraceFormatter.formatExceptionWithAgentMark(e));
                }
                return null;
            }
        }, true);
    }

    private boolean isBootstrapTarget(String className) {
        if (className == null) {
            return false;
        }
        return "java/util/concurrent/ThreadPoolExecutor".equals(className)
                || "java/util/concurrent/ScheduledThreadPoolExecutor".equals(className);
    }

    protected Error buildError(Throwable e) {
        Error error = new Error();
        error.setMessage(e.getMessage());
        error.setType(e.getClass().getName());
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        e.printStackTrace(new PrintStream(out));
        try {
            error.setErrorStack(out.toString("UTF-8"));
        } catch (UnsupportedEncodingException u) {
            logger.error("[Agent-EXCError]编码转换异常" + StackTraceFormatter.formatExceptionWithAgentMark(u));
        }
        return error;
    }

    //插桩
    public abstract byte[] transform(ClassLoader loader, String className, ProtectionDomain protectionDomain, byte[] classfileBuffer);

    //TODO 所有监控目标直接执行的方法，都必须经过此代理
    public Object proxyInvoker(Callable callable) {
        try {
            return callable.call();
        } catch (Throwable t) {
            logger.error("[Agent-EXCError]插桩逻辑执行异常" + StackTraceFormatter.formatExceptionWithAgentMark(t));
        }
        return null;
    }

    private static final java.util.Set<String> PRINTED_JARS =
            java.util.Collections.synchronizedSet(new java.util.HashSet<String>());

    protected void getJarAndVersion(ProtectionDomain protectionDomain) {
        try {
            if (protectionDomain != null && protectionDomain.getCodeSource() != null) {
                String location = protectionDomain.getCodeSource().getLocation().getFile();
                java.util.regex.Matcher m = java.util.regex.Pattern.compile(".*/([^/]+\\.jar)!/").matcher(location);
                if (m.find()) {
                    String jarName = m.group(1);
                    if (PRINTED_JARS.add(jarName)) {
                        logger.debug("[Agent-来自Jar包]" + jarName);
                    }
                }
            }
        } catch (Throwable t) {
            logger.error("[Agent-EXCError]获取Jar包信息异常: " + StackTraceFormatter.formatExceptionWithAgentMark(t));
        }
    }
}
