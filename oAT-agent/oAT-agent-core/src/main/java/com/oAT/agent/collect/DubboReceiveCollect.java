package com.oAT.agent.collect;

import com.oAT.agent.collect.dubbo.InvocationAdapter;
import com.oAT.agent.collect.dubbo.ResultAdapter;
import com.oAT.agent.common.StackTraceFormatter;
import com.oAT.agent.common.logger.Log;
import com.oAT.agent.common.logger.LogFactory;
import com.oAT.agent.model.DubboRemoteTraceNode;
import com.oAT.agent.model.TraceNode;
import com.oAT.agent.trace.ISessionDestroy;
import com.oAT.agent.trace.TraceContext;
import com.oAT.agent.trace.TraceRequest;
import com.oAT.agent.trace.TraceSession;
import com.oAT.shaded.apache.commons.codec.binary.Base64;
import com.oAT.shaded.javassist.CtClass;
import com.oAT.shaded.javassist.CtMethod;

import java.io.ByteArrayInputStream;
import java.lang.instrument.Instrumentation;
import java.security.ProtectionDomain;
import java.util.Properties;

public class DubboReceiveCollect extends AbstractByteTransformCollect implements ICollect {
    private final static Log logger = LogFactory.getLog(DubboReceiveCollect.class);

    public static DubboReceiveCollect INSTANCE;
    public static final String TARGET_CLASS = "org.apache.dubbo.rpc.filter.GenericFilter";
    public static final String TARGET_METHOD = "invoke";
    private static final String BEGIN_SRC;
    private static final String END_SRC;
    private static final String ERROR_SRC;

    static {
        BEGIN_SRC = DubboReceiveCollect.class.getName() + " instance = " +
                DubboReceiveCollect.class.getName() + ".INSTANCE;\r\n" +
                DubboRemoteTraceNodeWrapper.class.getName() + " node = instance.begin($args);";
        END_SRC = "instance.end(node, $args, _result);";
        ERROR_SRC = "instance.error(node, e);";
    }

    private final TraceContext traceContext;

    public DubboReceiveCollect(TraceContext traceContext, Instrumentation instrumentation) {
        super(instrumentation);
        this.traceContext = traceContext;
    }

    public DubboReceiveCollect(TraceContext traceContext) {
        super();
        this.traceContext = traceContext;
    }

    // 统一异常捕捉的问题
    public DubboRemoteTraceNodeWrapper begin(Object[] params) {
        if (params == null || params.length < 2 || params[1] == null) {
            return null;
        }
        InvocationAdapter invocationAdapter;
        try {
            invocationAdapter = new InvocationAdapter(params[1]);
        } catch (Throwable t) {
            logger.error("[Agent-EXCError]InvocationAdapter error. " + StackTraceFormatter.formatExceptionWithAgentMark(t));
            return null;
        }
        TraceRequest request = getTraceRequest(invocationAdapter);
        if (request == null) {
            return null;
        }
        TraceSession session = traceContext.openTraceSession(TARGET_CLASS, TARGET_METHOD, request);
        if (session == null) {
            return null;
        }
        DubboRemoteTraceNode node = new DubboRemoteTraceNode();
        try {
            if (traceContext.getTraceSession() == null) {
                return null;
            }
            node.setTraceId(traceContext.getTraceSession().getTraceId());
            node.setTraceNodeId(traceContext.getTraceSession().getParentNodeId() + ".remote");
            node.setBeginTime(System.currentTimeMillis());
        } catch (Throwable t) {
            logger.error("[Agent-EXCError]set node info error. " + StackTraceFormatter.formatExceptionWithAgentMark(t));
            return null;
        }
        return new DubboRemoteTraceNodeWrapper(session, node);
    }

    public void end(DubboRemoteTraceNodeWrapper nodeWrapper, Object[] params, Object result) {
        TraceSession session = null;
        try {
            session = traceContext.getTraceSession();
        } catch (Throwable t) {
            logger.error("[Agent-EXCError]getTraceSession error. " + StackTraceFormatter.formatExceptionWithAgentMark(t));
        }
        if (nodeWrapper == null || session == null) {
            return;
        }
        DubboRemoteTraceNode node = nodeWrapper.node;
        try {
            node.setEndTime(System.currentTimeMillis());
            long userTime = node.getEndTime() - node.getBeginTime();
            node.setUseTime(userTime);
            if (result != null) {
                ResultAdapter adapter = null;
                try {
                    adapter = new ResultAdapter(result);
                } catch (Throwable t) {
                    logger.error("[Agent-EXCError]ResultAdapter error. " + StackTraceFormatter.formatExceptionWithAgentMark(t));
                }
                if (adapter != null && adapter.getException() != null) {
                    error(nodeWrapper, adapter.getException());
                }
            }
            if (node.getError() != null) {
                node.setStatus(TraceNode.Status.fail.toString());
            } else {
                node.setStatus(TraceNode.Status.succeed.toString());
            }
            session.saveNode(node);
        } catch (Throwable t) {
            logger.error("[Agent-EXCError]saveNode error. " + StackTraceFormatter.formatExceptionWithAgentMark(t));
        } finally {
            try {
                nodeWrapper.doDestroy();
            } catch (Throwable t) {
                logger.error("[Agent-EXCError]doDestroy error. " + StackTraceFormatter.formatExceptionWithAgentMark(t));
            }
        }
    }

    public void error(DubboRemoteTraceNodeWrapper nodeWrapper, Throwable e) {
        if (nodeWrapper == null || traceContext == null) {
            return;
        }
        TraceSession session = null;
        try {
            session = traceContext.getTraceSession();
        } catch (Throwable t) {
            logger.error("[Agent-EXCError]getTraceSession error. " + StackTraceFormatter.formatExceptionWithAgentMark(t));
        }
        if (session == null) {
            return;
        }
        try {
            nodeWrapper.node.setError(buildError(e));
        } catch (Throwable t) {
            logger.error("[Agent-EXCError]setError error" + StackTraceFormatter.formatExceptionWithAgentMark(t));
        }
    }

    private TraceRequest getTraceRequest(InvocationAdapter invocationAdapter) {
        if (invocationAdapter == null) {
            return null;
        }
        String traceId;
        String parentNodeId;
        String properties;
        try {
            traceId = invocationAdapter.getAttachment("_traceId");
            parentNodeId = invocationAdapter.getAttachment("_parentTraceNodeId");
            properties = invocationAdapter.getAttachment("_traceProperties");
        } catch (Throwable t) {
            logger.error("[Agent-EXCError]getAttachment error. " + StackTraceFormatter.formatExceptionWithAgentMark(t));
            return null;
        }
        if (traceId != null && parentNodeId != null) {
            TraceRequest request = new TraceRequest();
            request.setTraceId(traceId);
            request.setParentNodeCallId(parentNodeId);
            if (properties != null) {
                try {
                    Properties p = new Properties();
                    p.load(new ByteArrayInputStream(Base64.decodeBase64(properties)));
                    request.setProperties(p);
                } catch (Throwable e) {
                    logger.error("[Agent-EXCError]getTraceRequest error. " + StackTraceFormatter.formatExceptionWithAgentMark(e));
                }
            }
            return request;
        }
        return null;
    }

    public class DubboRemoteTraceNodeWrapper implements ISessionDestroy {
        private final TraceSession session;
        private final DubboRemoteTraceNode node;

        public DubboRemoteTraceNodeWrapper(TraceSession session, DubboRemoteTraceNode node) {
            this.session = session;
            this.node = node;
        }

        @Override
        public void doDestroy() {
            if (traceContext != null && session != null) {
                try {
                    traceContext.closeTraceSession(session);
                } catch (Throwable t) {
                    logger.error("[Agent-doDestroy] closeTraceSession error" + StackTraceFormatter.formatExceptionWithAgentMark(t));
                }
            }
        }
    }

    @Override
    public byte[] transform(ClassLoader loader, String className, ProtectionDomain protectionDomain, byte[] classfileBuffer) {
        if (!TARGET_CLASS.equals(className)) {
            return null;
        }
        getJarAndVersion(protectionDomain);
        CtClass ctClass = null;
        AgentByteBuild byteLoade;
        try {
            ctClass = AgentByteBuild.toCtClass(loader, className, classfileBuffer);
            byteLoade = new AgentByteBuild(className, loader, ctClass);
            CtMethod m = ctClass.getDeclaredMethod(TARGET_METHOD);

            AgentByteBuild.MethodSrcBuild build = new AgentByteBuild.MethodSrcBuild();
            build.setBeginSrc(BEGIN_SRC);
            build.setErrorSrc(ERROR_SRC);
            build.setEndSrc(END_SRC);
            byteLoade.updateMethod(m, build);
            logger.info("[Agent-info]完成 DubboReceive 采集器初始化.");
            return byteLoade.toByteCode();
        } catch (Throwable t) {
            logger.error("[Agent-EXCError]transform error. " + StackTraceFormatter.formatExceptionWithAgentMark(t));
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
