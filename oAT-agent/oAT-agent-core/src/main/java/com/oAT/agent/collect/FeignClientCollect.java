package com.oAT.agent.collect;

import com.oAT.agent.collect.feign.InvocationAdapter;
import com.oAT.agent.collect.feign.ResultAdapter;
import com.oAT.agent.common.JsonUtil;
import com.oAT.agent.common.StackTraceFormatter;
import com.oAT.agent.common.StringUtils;
import com.oAT.agent.common.logger.Log;
import com.oAT.agent.common.logger.LogFactory;
import com.oAT.agent.model.FeignTraceNode;
import com.oAT.agent.model.TraceNode;
import com.oAT.agent.trace.TraceContext;
import com.oAT.agent.trace.TraceSession;
import com.oAT.shaded.javassist.CtClass;
import com.oAT.shaded.javassist.CtMethod;

import java.lang.instrument.Instrumentation;
import java.security.ProtectionDomain;
import java.util.*;

/**
 * Feign HTTP 客户端采集器
 * <p>
 * 继承自 HttpCollectBase，属于 HTTP 协议层
 * 用于追踪 Feign 声明式 HTTP 客户端调用
 */
public class FeignClientCollect extends AbstractByteTransformCollect implements ICollect {
    private final static Log logger = LogFactory.getLog(FeignClientCollect.class);

    public static FeignClientCollect INSTANCE;
    private static final String TARGET_METHOD = "execute";

    private final TraceContext traceContext;
    private final List<String> feignDrivers;
    private static final String BEGIN_SRC;
    private static final String END_SRC;
    private static final String ERROR_SRC;

    public FeignClientCollect(TraceContext context, Instrumentation instrumentation, String... feignDrivers) {
        super(instrumentation);
        this.traceContext = context;
        this.feignDrivers = Arrays.asList(feignDrivers);
    }

    static {
        BEGIN_SRC = FeignClientCollect.class.getName() + " instance = " + FeignClientCollect.class.getName() +
                ".INSTANCE;\r\n" + FeignTraceNode.class.getName() + " node = instance.begin($args);";
        END_SRC = "instance.end(node, $args, _result);";
        ERROR_SRC = "instance.error(node, e);";
    }

    public FeignTraceNode begin(Object[] params) {
        if (traceContext.getTraceSession() == null) {
            return null;
        }
        try {
            FeignTraceNode node = new FeignTraceNode();
            InvocationAdapter invocation = new InvocationAdapter(params[0]);
//        InvokerAdapter invoker = new InvokerAdapter(invocation.getRequestTemplate());
            node.setTraceId(traceContext.getTraceSession().getTraceId());
            node.setTraceNodeId(traceContext.getTraceSession().getNextNodeId());
            node.setBeginTime(System.currentTimeMillis());
            node.setServiceMethod(invocation.getMethod());
            node.setServiceURL(invocation.getURL());
            node.setServiceHeaders(invocation.getHeaders().toString());
            if (invocation.getBody() != null) {
                node.setServiceBody(JsonUtil.toJson(invocation.getBody()));
            }

            node.setRemoteFeignTargetName("");
            node.setRemoteMethod(invocation.getMethod());
            node.setRemoteUrl(invocation.getURL());
            if (invocation.getBody() != null) {
                node.setRemoteBody(JsonUtil.toJson(invocation.getBody()));
            }
            invocation.setHeaders(traceHeader(node));
            return node;
        } catch (Throwable t) {
            logger.error("[Agent-EXCError]begin error. " + StackTraceFormatter.formatExceptionWithAgentMark(t));
            return null;
        }
    }

    private Map<String, Collection<String>> traceHeader(FeignTraceNode node) {
        try {
            Map<String, Collection<String>> map = new HashMap();
            map.put("parentTraceId", Collections.singletonList(node.getTraceId() + "_" + node.getTraceNodeId()));
            map.put("parentTraceNodeId", Collections.singletonList(node.getTraceNodeId()));
            String userHeader = traceContext.getTraceSession().getTraceRequest().getUserHeader();
            if (StringUtils.hasText(userHeader)) {
                map.put("userheader", Collections.singletonList(userHeader));
            }
            return map;
        } catch (Throwable t) {
            logger.error("[Agent-EXCError]traceHeader error. " + StackTraceFormatter.formatExceptionWithAgentMark(t));
            return Collections.emptyMap();
        }
    }

    public void end(FeignTraceNode node, Object[] params, Object result) {
        if (node == null) {
            return;
        }
        try {
            node.setEndTime(System.currentTimeMillis());
            long userTime = node.getEndTime() - node.getBeginTime();
            node.setUseTime(userTime);
            if (result != null) {
                ResultAdapter adapter = new ResultAdapter(result);
                node.setRemoteResponse("");
            }
            node.setStatus(node.getError() != null
                    ? TraceNode.Status.fail.toString()
                    : TraceNode.Status.succeed.toString());
            TraceSession session = traceContext != null ? traceContext.getTraceSession() : null;
            if (session == null) {
                return;
            }

            if (node.getTraceNodeId() != null
                    && node.getTraceId() != null
                    && !node.getTraceId().isEmpty()) {
                try {
                    session.saveNode(node);
                } catch (Throwable t) {
                    logger.error("[Agent-EXCError]保存TraceNode失败" + StackTraceFormatter.formatExceptionWithAgentMark(t));
                }
            }
        } catch (Throwable t) {
            logger.error("[Agent-EXCError]end error. " + StackTraceFormatter.formatExceptionWithAgentMark(t));
        }
    }

    public void error(FeignTraceNode node, Throwable e) {
        try {
            if (node == null || traceContext.getTraceSession() == null) {
                return;
            }
            node.setError(buildError(e));
        } catch (Throwable t) {
            logger.error("[Agent-EXCError]setError error. " + StackTraceFormatter.formatExceptionWithAgentMark(t));
        }
    }

    @Override
    public byte[] transform(ClassLoader loader, String className, ProtectionDomain protectionDomain,
                            byte[] classfileBuffer) {
        if (!this.feignDrivers.contains(className)) {
            return null;
        }
        getJarAndVersion(protectionDomain);
        CtClass ctClass = null;
        try {
            ctClass = AgentByteBuild.toCtClass(loader, className, classfileBuffer);
            AgentByteBuild byteLoade = new AgentByteBuild(className, loader, ctClass);
            CtMethod m = ctClass.getDeclaredMethod(TARGET_METHOD);
            AgentByteBuild.MethodSrcBuild build = new AgentByteBuild.MethodSrcBuild();
            build.setBeginSrc(BEGIN_SRC);
            build.setErrorSrc(ERROR_SRC);
            build.setEndSrc(END_SRC);
            byteLoade.updateMethod(m, build);
            logger.info("[Agent-info]完成 FeignClient 采集器初始化.");
            return byteLoade.toByteCode();
        } catch (Throwable t) {
            logger.error("[Agent-EXCError]transform error. " + StackTraceFormatter.formatExceptionWithAgentMark(t));
            return null;
        } finally {
            if (ctClass != null) {
                try {
                    ctClass.detach(); // 释放CtClass资源
                } catch (Throwable ignore) {
                    // ignore
                }
            }
        }
    }
}
