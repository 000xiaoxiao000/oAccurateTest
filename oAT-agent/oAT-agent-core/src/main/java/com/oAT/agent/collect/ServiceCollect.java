package com.oAT.agent.collect;

import com.oAT.agent.common.StackTraceFormatter;
import com.oAT.agent.common.StringUtils;
import com.oAT.agent.common.WildcardMatcher;
import com.oAT.agent.common.logger.Log;
import com.oAT.agent.common.logger.LogFactory;
import com.oAT.agent.jacoco.CoverageCollector;
import com.oAT.agent.jacoco.data.StackNodeVoBuilder;
import com.oAT.agent.model.ServiceTraceNode;
import com.oAT.agent.model.StackNodeVo;
import com.oAT.agent.model.TraceNode;
import com.oAT.agent.trace.ISessionDestroy;
import com.oAT.agent.trace.TraceContext;
import com.oAT.agent.trace.TraceRequest;
import com.oAT.agent.trace.TraceSession;
import com.oAT.shaded.javassist.*;
import com.oAT.shaded.javassist.Modifier;

import java.io.OutputStream;
import java.lang.instrument.Instrumentation;
import java.security.CodeSource;
import java.security.ProtectionDomain;
import java.util.Properties;

/**
 * 采集服务中自定义的类与方法
 */
public class ServiceCollect extends AbstractByteTransformCollect implements ICollect {
    private static final Log logger = LogFactory.getLog(ServiceCollect.class);

    private final TraceContext traceContext;
    //TraceContext.initCollects()方法进行实例化
    public static ServiceCollect INSTANCE;

    //包含类
    private final WildcardMatcher includes;
    //排除类
    private final WildcardMatcher excludes;
    //包含方法
    private final WildcardMatcher methodIncludes;
    //排除方法
    private final WildcardMatcher methodExcludes;
    private final WildcardMatcher excludeInner;
    private final WildcardMatcher excludeClassloader;

    // 兼容所有 javassist 版本，定义 BRIDGE 和 SYNTHETIC 修饰符，避免编译器生成的桥接方法和合成方法被插桩
    private static final int BRIDGE = 0x00000040;
    private static final int SYNTHETIC = 0x00001000;

    private static final String BEGIN_SRC;
    private static final String END_SRC;
    private static final String ERROR_SRC;

    static {
        StringBuilder sbuilder = new StringBuilder();
        sbuilder.append(ServiceCollect.class.getName()).append(" instance = ");
        sbuilder.append(ServiceCollect.class.getName()).append(".INSTANCE;\r\n");
        sbuilder.append(ServiceTraceNodeWrapper.class.getName()).append(" statistic =instance.begin(\"%s\",\"%s\"," +
                "$args);");
        BEGIN_SRC = sbuilder.toString();
        sbuilder = new StringBuilder();
        sbuilder.append("instance.end(statistic);");
        END_SRC = sbuilder.toString();
        sbuilder = new StringBuilder();
        sbuilder.append("instance.error(statistic,e);");
        ERROR_SRC = sbuilder.toString();
    }

    //include,exclude
    public ServiceCollect(TraceContext context, Instrumentation instrumentation) {
        super(instrumentation);
        this.traceContext = context;

        //包含的代码堆栈表达式
        String includeExpr = context.getConfig("service.include");
        if (StringUtils.isBlank(includeExpr)) {
            logger.warn("[Agent-使用本地（.conf）配置]目标应用配置 service.include 为空");
            includeExpr = context.getConfig("conf_service.include");
        }
        if (StringUtils.isBlank(includeExpr)) {
            logger.warn("[Agent-warn]未配置 'service.include' 参数无法监控service服务方法");
        }
        includes = new WildcardMatcher(StringUtils.isBlank(includeExpr) ? "" : includeExpr);
        //排除的应用堆栈表达式，排掉Spring的内部类，如$FastClassBySpringCGLIB$*和$EnhancerBySpringCGLIB$*
        String excludeExpr = context.getConfig("service.exclude");
        if (StringUtils.isBlank(excludeExpr)) {
            excludeExpr = context.getConfig("conf_service.exclude");
        }

        if (!StringUtils.isBlank(excludeExpr)) {
            excludeExpr += "&$*$FastClassBySpringCGLIB$*&$*$EnhancerBySpringCGLIB$*&$*$EnhancerByCGLIB$*";
        } else {
            excludeExpr = "*$FastClassBySpringCGLIB$*&$*$EnhancerBySpringCGLIB$*&$*$EnhancerByCGLIB$*";
        }
        excludes = new WildcardMatcher(excludeExpr);
        //排除的ClassLoader表达式
        String excludeClassloaderExpr = context.getConfig("codeStack.excludeClassloader");
        excludeClassloader = new WildcardMatcher(StringUtils.isBlank(excludeClassloaderExpr) ?
                "sun.reflect.DelegatingClassLoader" : excludeClassloaderExpr);
        //排除监听器跟踪内部代码堆栈
        excludeInner = new WildcardMatcher("com.oAT.agent.*");

        //包含的方法表达式
        String methodIncludeExpr = context.getConfig("service.includeMethod");
        if (StringUtils.isBlank(methodIncludeExpr)) {
            methodIncludeExpr = context.getConfig("conf_service.includeMethod");
        }
        methodIncludes = new WildcardMatcher(StringUtils.isBlank(methodIncludeExpr) ? "" : methodIncludeExpr, true);
        //排除的方法表达式
        String methodExcludeExpr = context.getConfig("service.excludeMethod");
        if (StringUtils.isBlank(methodExcludeExpr)) {
            methodExcludeExpr = context.getConfig("conf_service.excludeMethod");
        }
        methodExcludes = new WildcardMatcher(StringUtils.isBlank(methodExcludeExpr) ? "" : methodExcludeExpr, true);
    }

    private boolean doFilter(ClassLoader loader, String className, ProtectionDomain protectionDomain) {
        try {
            if (loader == null) {
                return false;
            }
            if (!hasSourceLocation(protectionDomain)) {
                return false;
            }
            if (excludeClassloader != null && excludeClassloader.matches(loader.getClass().getName())) {
                return false;
            }
            if (excludeInner != null && excludeInner.matches(className)) {
                return false;
            }
            if (excludes != null && excludes.matches(className)) {
                return false;
            }
            return includes != null && includes.matches(className);
        } catch (Throwable t) {
            logger.warn("[Agent-EXCError]doFilter异常: " + className, t);
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
            logger.warn("[Agent-EXCError]hasSourceLocation异常", t);
            return false;
        }
    }

    public ServiceTraceNodeWrapper begin(String className, String methodName, Object[] params) {
        if (traceContext == null) {
            logger.warn("[Agent-warn]ServiceCollect begin traceContext 为空");
            return null;
        }

        TraceSession traceSession = traceContext.getTraceSession();
        String traceNodeId = "0";
        try {
            if (traceSession == null) {
                TraceRequest traceRequest = new TraceRequest();
                traceRequest.setParentNodeCallId(traceNodeId);
                traceRequest.setTraceId(traceContext.createTraceId());
                traceRequest.setProperties(new Properties());
                traceSession = traceContext.openTraceSession(className, methodName, traceRequest);
            } else {
                traceNodeId = traceSession.getNextNodeId();
            }

            ServiceTraceNode node = new ServiceTraceNode();
            node.setTraceId(traceSession.getTraceId());
            node.setTraceNodeId(traceNodeId);
            node.setBeginTime(System.currentTimeMillis());
            node.setServiceName(className);
            node.setMethodName(methodName);
            node.setSimpleName(StringUtils.unqualify(className, '.'));

            ServiceTraceNodeWrapper nodeWrapper = new ServiceTraceNodeWrapper(traceSession, node);
            if (StringUtils.hasText(this.traceContext.getConfig("codeStack.include"))
                    || StringUtils.hasText(this.traceContext.getConfig("conf_codeStack.include"))) {
                nodeWrapper.coverageCollector = CoverageCollector.begin();
            }

            return nodeWrapper;
        } catch (Throwable t) {
            logger.error("[Agent-EXCError]begin error. " + StackTraceFormatter.formatExceptionWithAgentMark(t));
            return null;
        }
    }

    public void end(ServiceTraceNodeWrapper nodeWrapper) {
        if (nodeWrapper == null || traceContext == null || traceContext.getTraceSession() == null) {
            logger.warn("[Agent-warn]ServiceCollect end nodeWrapper 或 traceContext 为空");
            return;
        }
        ServiceTraceNode node = nodeWrapper.node;
        TraceSession traceSession = traceContext.getTraceSession();
        try {
            node.setEndTime(System.currentTimeMillis());
            long userTime = node.getEndTime() - node.getBeginTime();
            node.setUseTime(userTime);
            if (node.getError() != null) {
                node.setStatus(TraceNode.Status.fail.toString());
            } else {
                node.setStatus(TraceNode.Status.succeed.toString());
            }
            if (nodeWrapper.coverageCollector != null) {
                nodeWrapper.coverageCollector = CoverageCollector.end();
                if (nodeWrapper.coverageCollector != null && !nodeWrapper.coverageCollector.getProbeSnapshots().isEmpty()) {
                    try {
                        StackNodeVo[] codeNodes = new StackNodeVoBuilder()
                                .buildCodeNodes(nodeWrapper.coverageCollector);
                        node.setCodeNodes(codeNodes);
                    } catch (Throwable t) {
                        logger.error("[Agent-EXCError]buildCodeNodes 异常: " + StackTraceFormatter.formatExceptionWithAgentMark(t));
                    }
                }
            }
            if (node.getCodeNodes() != null && node.getCodeNodes().length > 0) {
                traceSession.saveNode(node);
            }
        } catch (Throwable t) {
            logger.error("[Agent-EXCError]end error. " + StackTraceFormatter.formatExceptionWithAgentMark(t));
        } finally {
            try {
                //关闭会话
                if ("0".equals(nodeWrapper.getServiceTraceNode().getTraceNodeId())) {
                    nodeWrapper.doDestroy();
                }

            } catch (Throwable t) {
                logger.error("[Agent-EXCError]doDestroy error: " + StackTraceFormatter.formatExceptionWithAgentMark(t));
            }
        }
    }

    public void error(ServiceTraceNodeWrapper nodeWrapper, Throwable e) {
        if (nodeWrapper == null || this.traceContext.getTraceSession() == null) {
            return;
        }
        try {
            ServiceTraceNode node = nodeWrapper.node;
            node.setError(buildError(e));
        } catch (Throwable t) {
            logger.error("[Agent-EXCError]setError error. " + StackTraceFormatter.formatExceptionWithAgentMark(t));
        }
    }

    public class ServiceTraceNodeWrapper implements ISessionDestroy {
        private final TraceSession traceSession;
        private final ServiceTraceNode node;
        private CoverageCollector coverageCollector;
        private OutputStream logOut;

        public ServiceTraceNodeWrapper(TraceSession traceSession, ServiceTraceNode node) {
            this.traceSession = traceSession;
            this.node = node;
        }

        @Override
        public void doDestroy() {
            try {
                if (coverageCollector != null) {
                    CoverageCollector.remove();
                }
                if (logOut != null) {
                    SystemLogCollect.INSTANCE.removeOutput(logOut);
                }
                traceContext.closeTraceSession(traceSession);
            } catch (Throwable t) {
                logger.error("[Agent-EXCError]doDestroy error: " + StackTraceFormatter.formatExceptionWithAgentMark(t));
            }
        }

        public ServiceTraceNode getServiceTraceNode() {
            return node;
        }
    }

    /*
     * 字节码转换入口
     * 线程池 调用链 com.example.consumer.controller#invokeRun()
     * ---> asyncPoolTaskSubmit.execute(clientRunner)
     * ---> com.example.consumer#un()
     *
     * 对 invokeRun$agent 插桩，开启线程，获取到 com.example.consumer#un() 中代码覆盖率数据
     *
     * package com.example.consumer.controller;
     * @RestController
     * @RequestMapping("/client")
     * public class HttpController {
     * @GetMapping("/run")
     *     public String invokeRun() {
     *         try {
     *             asyncPoolTaskSubmit.execute(clientRunner);
     *             return "run invoked";
     *         } catch (Exception e) {
     *             return "run failed: " + e.getMessage();
     *         }
     *     }
     * }
     * package com.example.consumer;
     * @Component
     * public class ClientRunner implements Runnable {
     * @Autowired
     *     private HelloService helloService;
     *
     *     @Override
     *     public void run() {
     *         System.out.println("[ClientRunner] Calling HelloService.sayHello...");
     *         try {
     *             String resp = helloService.sayHello("XiaoXiao");
     *             System.out.println("[ClientRunner] sayHello response: " + resp);
     *         } catch (Exception e) {
     *             System.err.println("[ClientRunner] sayHello failed: " + e.getMessage());
     *             e.printStackTrace(System.err);
     *         }
     *     }
     * }
     */
    @Override
    public byte[] transform(ClassLoader loader, String orgClassName, ProtectionDomain protectionDomain,
                            byte[] classfileBuffer) {
        if (orgClassName == null || loader == null) {
            return null;
        }
        String className = orgClassName.replace('/', '.');
        if (!doFilter(loader, className, protectionDomain)) {
            return null;
        }
        getJarAndVersion(protectionDomain);
        CtClass ctClass = null;
        try {
            ctClass = AgentByteBuild.toCtClass(loader, className, classfileBuffer);
            AgentByteBuild byteLoade = new AgentByteBuild(className, loader, ctClass);
            CtMethod[] methods = ctClass.getDeclaredMethods();
            String methodName;
            for (CtMethod m : methods) {
                if (m.isEmpty() || m.getName().isEmpty()) {
                    continue;
                }
                int mod = m.getModifiers();
                methodName = m.getName();
                String longName = m.getLongName();

                // 逻辑：找到 '(' 之前的最后一个 '.'，截取其后的部分
                int bracketIndex = longName.indexOf('(');
                String shortSignature = longName;
                if (bracketIndex != -1) {
                    int lastDotIndex = longName.substring(0, bracketIndex).lastIndexOf('.');
                    if (lastDotIndex != -1) {
                        shortSignature = longName.substring(lastDotIndex + 1);
                    }
                }
                // 跳过 bridge 和 synthetic 方法，避免泛型擦除导致的插桩异常
                if ((mod & BRIDGE) != 0 || (mod & SYNTHETIC) != 0) {
                    continue;
                }
//                !Modifier.isPublic(mod) // 屏蔽非公共方法
                if (
                        Modifier.isStatic(mod)   // 屏蔽静态方法
                                || Modifier.isNative(mod)   // 屏蔽本地方法
                                || !methodIncludes.matches(shortSignature)
                                || (methodExcludes.matches(longName) || methodExcludes.matches(shortSignature))) {
                    continue;
                }
                AgentByteBuild.MethodSrcBuild build = new AgentByteBuild.MethodSrcBuild();
                build.setBeginSrc(String.format(BEGIN_SRC, className, m.getName()));
                build.setEndSrc(END_SRC);
                build.setErrorSrc(ERROR_SRC);
                byteLoade.updateMethod(m, build);
                logger.info("[Agent-info]完成 Service 采集器初始化, " + className + " " + methodName);
            }
            return byteLoade.toByteCode();
        } catch (Throwable t) {
            logger.error("[Agent-EXCError]异常, className: " + className
                    + " " + StackTraceFormatter.formatExceptionWithAgentMark(t));
            return null;
        } finally {
            // 资源释放
            if (ctClass != null) {
                try {
                    // 释放CtClass资源
                    ctClass.detach();
                } catch (Throwable ignore) {
                    // ignore
                }
            }
        }
    }

}
