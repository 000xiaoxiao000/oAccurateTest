package com.oAT.agent.collect;

import com.oAT.agent.collect.dubbo.InvocationAdapter;
import com.oAT.agent.collect.dubbo.InvokerAdapter;
import com.oAT.agent.collect.dubbo.ResultAdapter;
import com.oAT.agent.common.JsonUtil;
import com.oAT.agent.common.StackTraceFormatter;
import com.oAT.agent.common.logger.Log;
import com.oAT.agent.common.logger.LogFactory;
import com.oAT.agent.model.DubboTraceNode;
import com.oAT.agent.model.TraceNode;
import com.oAT.agent.trace.TraceContext;
import com.oAT.agent.trace.TraceSession;
import com.oAT.shaded.apache.commons.codec.binary.Base64;
import com.oAT.shaded.javassist.CtClass;
import com.oAT.shaded.javassist.CtMethod;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.instrument.Instrumentation;
import java.security.ProtectionDomain;
import java.util.Properties;
import java.util.logging.Level;

/**
 * 拦截目标：DubboInvoker
 */
public class DubboInvokerCollect extends AbstractByteTransformCollect implements ICollect {
    private final static Log logger = LogFactory.getLog(DubboInvokerCollect.class);

    public static DubboInvokerCollect INSTANCE;
    // 建议重构成 com.alibaba.dubbo.rpc.protacol.dubbo.filter.futureFilter 与服务端统一
    static final String TARGET_CLASS = "org.apache.dubbo.rpc.protocol.dubbo.DubboInvoker";
    static final String TARGET_METHOD = "doInvoke";

    private final TraceContext traceContext;
    private static final String BEGIN_SRC;
    private static final String END_SRC;
    private static final String ERROR_SRC;

    public DubboInvokerCollect(TraceContext traceContext, Instrumentation instrumentation) {
        super(instrumentation);
        this.traceContext = traceContext;
    }

    static {
        BEGIN_SRC = DubboInvokerCollect.class.getName() + " instance = " +
                DubboInvokerCollect.class.getName() + ".INSTANCE;\r\n" +
                DubboTraceNode.class.getName() + " node = instance.begin($args);";
        END_SRC = "instance.end(node, $args, _result);";
        ERROR_SRC = "instance.error(node, e);";
    }

    public DubboTraceNode begin(Object[] params) {
        try {
            if (traceContext == null || traceContext.getTraceSession() == null) {
                return null;
            }
            if (params == null || params.length == 0 || params[0] == null) {
                return null;
            }
            DubboTraceNode node = new DubboTraceNode();
            InvocationAdapter invocation = new InvocationAdapter(params[0]);
            InvokerAdapter invoker = new InvokerAdapter(invocation.getInvoker());

            node.setServiceMethodName(invocation.getMethodName());
            node.setServiceInterface(invoker.getInterface() != null ? invoker.getInterface().getName() : null);
            node.setRemoteUrl(invoker.getUrl());
            if (invocation.getArguments() != null) {
                node.setInParam(JsonUtil.toJson(invocation.getArguments()));
            }
            if (traceContext.getTraceSession() != null) {
                node.setTraceId(traceContext.getTraceSession().getTraceId());
                node.setTraceNodeId(traceContext.getTraceSession().getNextNodeId());
                setAttachments(invocation, traceContext.getTraceSession());
            }
            node.setBeginTime(System.currentTimeMillis());
            return node;
        } catch (Throwable t) {
            logger.error("[Agent-EXCError] DubboInvokerCollect begin error"+ StackTraceFormatter.formatExceptionWithAgentMark(t));
            return null;
        }
    }

    //设置隐示参数，上下游传递RPC节点信息
    private void setAttachments(InvocationAdapter adapter, TraceSession session) {
        if (adapter == null || session == null || session.getTraceRequest() == null) {
            return;
        }
        try {
            adapter.setAttachment("_traceId", session.getTraceId());
            adapter.setAttachment("_parentTraceNodeId", session.getCurrentNodeId());
            adapter.setAttachment("_traceProperties", converProperties(session.getTraceRequest().getProperties()));
        } catch (Throwable t) {
            logger.error("[Agent-EXCError] DubboInvokerCollect setAttachments error"+ StackTraceFormatter.formatExceptionWithAgentMark(t));
        }
    }

    private String converProperties(Properties properties) {
        if (properties == null) {
            return null;
        }
        try (ByteArrayOutputStream stream = new ByteArrayOutputStream()) {
            try {
                properties.store(stream, "");
                return Base64.encodeBase64String(stream.toByteArray());
            } catch (IOException e) {
                logger.error("[Agent-EXCError]" + Level.SEVERE + "DubboInvokerCollect properties encode fail"
                        + StackTraceFormatter.formatExceptionWithAgentMark(e));
                return null;
            } catch (Throwable t) {
                logger.error("[Agent-EXCError] DubboInvokerCollect properties unknown error"+ StackTraceFormatter.formatExceptionWithAgentMark(t));
                return null;
            }
        } catch (IOException ignore) {
        }
        return null;
    }

    public void end(DubboTraceNode node, Object[] parames, Object result) {
        try {
            if (node == null || traceContext == null || traceContext.getTraceSession() == null) {
                return;
            }
            node.setEndTime(System.currentTimeMillis());
            long userTime = node.getEndTime() - node.getBeginTime();
            node.setUseTime(userTime);
            if (result != null) {
                try {
                    ResultAdapter adapter = new ResultAdapter(result);
                    if (adapter.getException() != null) {
                        error(node, adapter.getException());
                    }
                } catch (Throwable t) {
                    logger.error("[Agent-EXCError] DubboInvokerCollect result adapter error"
                            + StackTraceFormatter.formatExceptionWithAgentMark(t));
                }
            }
            if (node.getError() != null) {
                node.setStatus(TraceNode.Status.fail.toString());
            } else {
                node.setStatus(TraceNode.Status.succeed.toString());
            }
            traceContext.getTraceSession().saveNode(node);
        } catch (Throwable t) {
            logger.error("[Agent-EXCError] DubboInvokerCollect end error"+ StackTraceFormatter.formatExceptionWithAgentMark(t));
        }
    }

    public void error(DubboTraceNode node, Throwable e) {
        try {
            if (node == null || traceContext == null || traceContext.getTraceSession() == null) {
                return;
            }
            node.setError(buildError(e));
        } catch (Throwable t) {
            logger.error("[Agent-EXCError] DubboInvokerCollect error error"+ StackTraceFormatter.formatExceptionWithAgentMark(t));
        }
    }

    @Override
    public byte[] transform(ClassLoader loader, String className, ProtectionDomain protectionDomain, byte[] classfileBuffer) {
        if (!TARGET_CLASS.equals(className)) {
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
            logger.info("[Agent-info]完成 DubboInvoker 采集器初始化.");
            return byteLoade.toByteCode();
        } catch (Throwable t) {
            logger.error("[Agent-EXCError] DubboInvokerCollect transform error"+ StackTraceFormatter.formatExceptionWithAgentMark(t));
            return null;
        } finally {
            if (ctClass != null) {
                try {
                    ctClass.detach();
                } catch (Throwable ignore) {
                }
            }
        }
    }

}
