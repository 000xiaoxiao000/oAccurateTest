package com.oAT.agent.collect.thread;

import com.oAT.agent.collect.AbstractByteTransformCollect;
import com.oAT.agent.collect.AgentByteBuild;
import com.oAT.agent.common.StackTraceFormatter;
import com.oAT.agent.common.logger.Log;
import com.oAT.agent.common.logger.LogFactory;
import com.oAT.shaded.javassist.CtClass;
import com.oAT.shaded.javassist.CtMethod;

import java.lang.instrument.Instrumentation;
import java.security.ProtectionDomain;

public class ThreadPoolCollect extends AbstractByteTransformCollect {

    private static final Log logger = LogFactory.getLog(ThreadPoolCollect.class);
    public static ThreadPoolCollect INSTANCE;

    private static final String THREAD_POOL_EXECUTOR = "java.util.concurrent.ThreadPoolExecutor";
    private static final String SCHEDULED_THREAD_POOL_EXECUTOR = "java.util.concurrent.ScheduledThreadPoolExecutor";

    public ThreadPoolCollect(Instrumentation instrumentation) {
        super(instrumentation);
        // 尝试重转换，因为这些类可能已经被加载
        try {
            Class<?> tpe = Class.forName(THREAD_POOL_EXECUTOR);
            Class<?> stpe = Class.forName(SCHEDULED_THREAD_POOL_EXECUTOR);
            instrumentation.retransformClasses(tpe, stpe);
        } catch (Throwable e) {
            logger.warn("[Agent-warn]无法重转换线程池类（可能尚未加载或无权限）: " + e.getMessage());
        }
    }

    @Override
    public byte[] transform(ClassLoader loader, String className, ProtectionDomain protectionDomain, byte[] classfileBuffer) {
        if (!THREAD_POOL_EXECUTOR.equals(className) && !SCHEDULED_THREAD_POOL_EXECUTOR.equals(className)) {
            return null;
        }

        try {
            CtClass ctClass = AgentByteBuild.toCtClass(loader, className, classfileBuffer);
            if (ctClass.isFrozen()) {
                return null;
            }

            // 使用反射调用 AgentContext.wrap，避免将 AgentContext 放入 Bootstrap ClassLoader
            String runnableWrapCode = "try {" +
                    "Class c = ClassLoader.getSystemClassLoader().loadClass(\"com.oAT.agent.context.AgentContext\");" +
                    "java.lang.reflect.Method m = c.getMethod(\"wrap\", new Class[]{java.lang.Runnable.class});" +
                    "$1 = (java.lang.Runnable) m.invoke(null, new Object[]{$1});" +
                    "} catch (Throwable t) { t.printStackTrace(); }";

            String callableWrapCode = "try {" +
                    "Class c = ClassLoader.getSystemClassLoader().loadClass(\"com.oAT.agent.context.AgentContext\");" +
                    "java.lang.reflect.Method m = c.getMethod(\"wrap\", new Class[]{java.util.concurrent.Callable.class});" +
                    "$1 = (java.util.concurrent.Callable) m.invoke(null, new Object[]{$1});" +
                    "} catch (Throwable t) { t.printStackTrace(); }";

            // 针对 ThreadPoolExecutor 的 execute 方法
            if (THREAD_POOL_EXECUTOR.equals(className)) {
                try {
                    CtMethod executeMethod = ctClass.getDeclaredMethod("execute", new CtClass[]{
                            AgentByteBuild.toCtClass(loader, "java.lang.Runnable", null)
                    });
                    executeMethod.insertBefore(runnableWrapCode);
                } catch (Throwable t) {
                    logger.warn("[Agent-warn]ThreadPoolExecutor.execute method not found or error: " + t.getMessage());
                }
            }

            // 针对 ScheduledThreadPoolExecutor
            if (SCHEDULED_THREAD_POOL_EXECUTOR.equals(className)) {
                String[] methods = new String[]{"schedule", "scheduleAtFixedRate", "scheduleWithFixedDelay"};
                for (CtMethod m : ctClass.getDeclaredMethods()) {
                     boolean match = false;
                     for (String name : methods) {
                         if (m.getName().equals(name)) {
                             match = true;
                             break;
                         }
                     }
                     if (match && (m.getModifiers() & java.lang.invoke.MethodHandles.Lookup.PUBLIC) != 0) {
                         try {
                             CtClass[] params = m.getParameterTypes();
                             if (params.length > 0) {
                                 String firstParam = params[0].getName();
                                 if ("java.lang.Runnable".equals(firstParam)) {
                                     m.insertBefore(runnableWrapCode);
                                 } else if ("java.util.concurrent.Callable".equals(firstParam)) {
                                     m.insertBefore(callableWrapCode);
                                 }
                             }
                         } catch (Throwable ignore) {}
                     }
                }
            }

            logger.info("[Agent-info]完成 ThreadPool 采集器初始化: " + className);
            byte[] bytes = ctClass.toBytecode();
            ctClass.detach();
            return bytes;

        } catch (Throwable e) {
             logger.error("[Agent-EXCError]ThreadPoolCollect error: " + StackTraceFormatter.formatExceptionWithAgentMark(e));
             return null;
        }
    }
}

