package com.oAT.agent.collect;

import com.oAT.agent.collect.http.HttpClientRequestAdapterV4;
import com.oAT.agent.common.StackTraceFormatter;
import com.oAT.agent.common.StringUtils;
import com.oAT.agent.common.logger.Log;
import com.oAT.agent.common.logger.LogFactory;
import com.oAT.agent.model.HttpClientTraceNode;
import com.oAT.agent.model.TraceNode;
import com.oAT.agent.trace.TraceContext;
import com.oAT.agent.trace.TraceRequest;
import com.oAT.agent.trace.TraceSession;
import com.oAT.shaded.javassist.CtClass;
import com.oAT.shaded.javassist.CtMethod;

import java.lang.instrument.Instrumentation;
import java.security.ProtectionDomain;
import java.util.*;

public class HttpClientCollectV4 extends AbstractByteTransformCollect implements ICollect {
    private final static Log logger = LogFactory.getLog(HttpClientCollectV4.class);

    public static HttpClientCollectV4 INSTANCE;
    private static String TARGET_CLASS = "";
    private static final String TARGET_METHOD = "doExecute";
    private final List<String> httpClientDrivers;

    private final TraceContext traceContext;
    private static final String BEGIN_SRC;
    private static final String END_SRC;
    private static final String ERROR_SRC;


    public HttpClientCollectV4(TraceContext context, Instrumentation instrumentation) {
        super(instrumentation);
        this.traceContext = context;
        this.httpClientDrivers = Arrays.asList(
                "org.apache.http.impl.client.InternalHttpClient",
                "org.apache.http.impl.client.CloseableHttpClient"
        );
    }

    static {
        BEGIN_SRC = HttpClientCollectV4.class.getName() + " instance = " + HttpClientCollectV4.class.getName() + ".INSTANCE;\r\n" + HttpClientTraceNode.class.getName() + " node = instance.begin($args);";
        END_SRC = "instance.end(node, $args, _result);";
        ERROR_SRC = "instance.error(node, e);";
    }

    public HttpClientTraceNode begin(Object[] params) {
        if (traceContext == null) {
            logger.debug("[Agent-debug]HttpClientCollectV4 begin traceContext 为空.");
            return null;
        }
        logger.info("[Agent-info]httpclientV4 params: " + (params == null ? -1 : params.length));
        try {
            HttpClientRequestAdapterV4 invocation = new HttpClientRequestAdapterV4(params);
            TraceSession traceSession = traceContext.getTraceSession();
            TraceRequest request;
            String traceId;
            String traceNodeId = "0";
            String userHeader = invocation.getRequestHeaders().get("userheader");
            if (traceSession != null) {
                request = traceSession.getTraceRequest();
                traceSession = traceContext.openTraceSession(TARGET_CLASS, TARGET_METHOD, request);
                traceId = request.getTraceId();
                traceNodeId = traceSession.getNextNodeId();
                userHeader = request.getUserHeader();
            } else {
                request = new TraceRequest();
                request.setParentNodeCallId("0");
                traceId = traceContext.createTraceId();
                request.setTraceId(traceId);
                logger.info("[Agent-userHeader]探针通过服务 httpClient 协议获取到的 userheader: " + userHeader);

                request.setUserHeader(userHeader);
                request.setProperties(new Properties());

                traceSession = traceContext.openTraceSession(TARGET_CLASS, TARGET_METHOD, request);
            }

            HttpClientTraceNode node = new HttpClientTraceNode();
            node.setTraceId(traceSession.getTraceId());
            node.setTraceNodeId(traceNodeId);
            node.setBeginTime(System.currentTimeMillis());
            node.setServiceMethod(invocation.getMethod());
            node.setServiceURL(invocation.getURL());
            node.setServiceHeaders(invocation.getRequestHeaders().toString());
            node.setServiceBody(invocation.getRequestBody());

            Map<String, String> map = new HashMap();
            map.put("userheader", userHeader);
            map.put("parentTraceId", traceId);
            map.put("parentTraceNodeId", traceSession.getCurrentNodeId());
            invocation.setHeaders(map);

            return node;
        } catch (Throwable t) {
            logger.error("[Agent-EXCError]begin error. "+ StackTraceFormatter.formatExceptionWithAgentMark(t));
            return null;
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
                    logger.debug("[Agent-debug]HttpClient4Collect 保存 TraceNode 成功");
                } catch (Throwable t) {
                    logger.error("[Agent-EXCError]保存 TraceNode 失败. " + StackTraceFormatter.formatExceptionWithAgentMark(t));
                }
            }
        } catch (Throwable t) {
            logger.error("[Agent-EXCError]end error. "+ StackTraceFormatter.formatExceptionWithAgentMark(t));
        }
    }

    public void error(HttpClientTraceNode node, Throwable e) {
        try {
            if (node == null || traceContext.getTraceSession() == null) {
                return;
            }
            node.setError(buildError(e));
        } catch (Throwable t) {
            logger.error("[Agent-EXCError]setError error. "+ StackTraceFormatter.formatExceptionWithAgentMark(t));
        }
    }

    @Override
    public byte[] transform(ClassLoader loader, String className, ProtectionDomain protectionDomain, byte[] classfileBuffer) {
        if (!this.httpClientDrivers.contains(className)) {
            return null;
        }
        getJarAndVersion(protectionDomain);
        CtClass ctClass = null;
        TARGET_CLASS = className;
        try {
            ctClass = AgentByteBuild.toCtClass(loader, className, classfileBuffer);
            AgentByteBuild byteLoade = new AgentByteBuild(className, loader, ctClass);
            CtMethod[] methods = ctClass.getDeclaredMethods(TARGET_METHOD);
            AgentByteBuild.MethodSrcBuild build = new AgentByteBuild.MethodSrcBuild();
            build.setBeginSrc(BEGIN_SRC);
            build.setErrorSrc(ERROR_SRC);
            build.setEndSrc(END_SRC);
            for(CtMethod method : methods){
                byteLoade.updateMethod(method, build);
            }

            logger.info("[Agent-info]完成 HttpClient4 采集器初始化.");
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
