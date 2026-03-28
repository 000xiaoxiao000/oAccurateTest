package com.oAT.agent.collect;

import com.oAT.agent.collect.http.HttpClientRequestAdapterV3;
import com.oAT.agent.common.StackTraceFormatter;
import com.oAT.agent.common.StringUtils;
import com.oAT.agent.common.logger.Log;
import com.oAT.agent.common.logger.LogFactory;
import com.oAT.agent.model.HttpClientTraceNode;
import com.oAT.agent.model.TraceNode;
import com.oAT.agent.trace.TraceContext;
import com.oAT.agent.trace.TraceSession;
import com.oAT.shaded.javassist.CtClass;
import com.oAT.shaded.javassist.CtMethod;

import java.lang.instrument.Instrumentation;
import java.security.ProtectionDomain;
import java.util.*;

public class HttpClientCollectV3 extends AbstractByteTransformCollect implements ICollect {
    private final static Log logger = LogFactory.getLog(HttpClientCollectV3.class);

    public static HttpClientCollectV3 INSTANCE;
    private static final String TARGET_METHOD = "executeMethod";

    private final TraceContext traceContext;
    private final List<String> httpClientDrivers;
    private static final String BEGIN_SRC;
    private static final String END_SRC;
    private static final String ERROR_SRC;

    public HttpClientCollectV3(TraceContext context, Instrumentation instrumentation, String... httpClientDrivers) {
        super(instrumentation);
        this.traceContext = context;
        this.httpClientDrivers = Arrays.asList(httpClientDrivers);
    }

    static {
        BEGIN_SRC = HttpClientCollectV3.class.getName() + " instance = " + HttpClientCollectV3.class.getName() +
                ".INSTANCE;\r\n" + HttpClientTraceNode.class.getName() + " node = instance.begin($args);";
        END_SRC = "instance.end(node, $args, _result);";
        ERROR_SRC = "instance.error(node, e);";
    }

    public HttpClientTraceNode begin(Object[] params) {
        if (traceContext.getTraceSession() == null) {
            return null;
        }
        if (params == null || params.length < 3) {
            return null;
        }
        logger.info("ap httpclient3 params: " + params.length);
        try {
            HttpClientTraceNode node = new HttpClientTraceNode();
            HttpClientRequestAdapterV3 invocation = new HttpClientRequestAdapterV3(params);
            node.setTraceId(traceContext.getTraceSession().getTraceId());
            node.setTraceNodeId(traceContext.getTraceSession().getNextNodeId());
            node.setBeginTime(System.currentTimeMillis());
            node.setServiceMethod(invocation.getMethod());
            node.setServiceURL(invocation.getURL());
//            node.setRemoteMethod(invocation.getMethod());
//            node.setRemoteUrl(invocation.getURL());
            // if (invocation.getBody() != null) {
            //     node.setRemoteBody(JsonUtil.toJson(invocation.getBody()));
            // }
            invocation.setHeaders(traceHeader(node));
            node.setServiceHeaders(invocation.getRequestHeaders().toString());
            return node;
        } catch (Throwable t) {
            logger.error("[Agent-EXCError]begin error. " + StackTraceFormatter.formatExceptionWithAgentMark(t));
            return null;
        }
    }

    private Map<String, String> traceHeader(HttpClientTraceNode node) {
        try {
            Map<String, String> map = new HashMap<>();
//            map.put("parentTraceId", node.getTraceId() + "_" + node.getTraceNodeId());
            String userHeader = traceContext.getTraceSession().getTraceRequest().getUserHeader();
            if (StringUtils.hasText(userHeader)) {
                map.put("userheader", userHeader);
            }
            return map;
        } catch (Throwable t) {
            logger.error("[Agent-EXCError]traceHeader error. " + StackTraceFormatter.formatExceptionWithAgentMark(t));
            return Collections.emptyMap();
        }
    }

    public void end(HttpClientTraceNode node, Object[] params, Object result) {
        if (node == null) {
            return;
        }
        try {
            node.setEndTime(System.currentTimeMillis());
            long userTime = node.getEndTime() - node.getBeginTime();
            node.setUseTime(userTime);
            // if (result != null) {
            //     node.setRemoteResponse("");
            // }
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
                    logger.debug("[Agent-debug]HttpClient3Collect 保存 TraceNode 成功");
                } catch (Throwable t) {
                    logger.error("[Agent-EXCError]保存TraceNode失败. " + StackTraceFormatter.formatExceptionWithAgentMark(t));
                }
            }
        } catch (Throwable t) {
            logger.error("[Agent-EXCError]end error. " + StackTraceFormatter.formatExceptionWithAgentMark(t));
        }
    }

    public void error(HttpClientTraceNode node, Throwable e) {
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
    public byte[] transform(ClassLoader loader, String className, ProtectionDomain protectionDomain, byte[] classfileBuffer) {
        if (!this.httpClientDrivers.contains(className)) {
            return null;
        }
        getJarAndVersion(protectionDomain);
        CtClass ctClass = null;
        try {
            ctClass = AgentByteBuild.toCtClass(loader, className, classfileBuffer);
            AgentByteBuild byteLoade = new AgentByteBuild(className, loader, ctClass);
            CtMethod[] methods = ctClass.getDeclaredMethods(TARGET_METHOD);
            AgentByteBuild.MethodSrcBuild build = new AgentByteBuild.MethodSrcBuild();
            build.setBeginSrc(BEGIN_SRC);
            build.setErrorSrc(ERROR_SRC);
            build.setEndSrc(END_SRC);
            for (CtMethod method : methods) {
                byteLoade.updateMethod(method, build);
            }

            logger.info("[Agent-info]完成 HttpClient3 采集器初始化.");
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
