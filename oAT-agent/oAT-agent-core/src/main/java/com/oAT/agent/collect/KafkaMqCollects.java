package com.oAT.agent.collect;

import com.oAT.agent.collect.kafka.InvocationProducerAdapter;
import com.oAT.agent.common.StackTraceFormatter;
import com.oAT.agent.common.logger.Log;
import com.oAT.agent.common.logger.LogFactory;
import com.oAT.agent.model.KafkaMQTraceNode;
import com.oAT.agent.model.TraceNode;
import com.oAT.agent.trace.TraceContext;
import com.oAT.shaded.javassist.*;

import java.lang.instrument.Instrumentation;
import java.security.ProtectionDomain;


public class KafkaMqCollects extends AbstractByteTransformCollect implements ICollect {
    private final static Log logger = LogFactory.getLog(KafkaMqCollects.class);

    public static KafkaMqCollects INSTANCE;
    private static final String PRODUCER_CLASS = "org.apache.kafka.clients.producer.internals.ProducerInterceptors";
    private static final String PRODUCER_METHOD = "onSend";
    private static final String PRODUCER_METHOD_DESC = "(Lorg/apache/kafka/clients/producer/ProducerRecord;)" +
            "Lorg/apache/kafka/clients/producer/ProducerRecord;";

    private final TraceContext traceContext;
    private static final String BEGIN_SRC;
    private static final String END_SRC;
    private static final String ERROR_SRC;

    public KafkaMqCollects(TraceContext context, Instrumentation instrumentation) {
        super(instrumentation);
        this.traceContext = context;
    }

    static {
        BEGIN_SRC = KafkaMqCollects.class.getName() + " instance = " + KafkaMqCollects.class.getName() + ".INSTANCE;" +
                "\r\n" +
                KafkaMQTraceNode.class.getName() + " node = instance.begin($args);";
        END_SRC = "instance.end(node, $args);";
        ERROR_SRC = "instance.error(node, e);";
    }

    public KafkaMQTraceNode begin(Object[] params) {
        if (traceContext == null || traceContext.getTraceSession() == null) {
            return null;
        }
        if (params == null || params.length == 0 || params[0] == null) {
            logger.error("[Agent-warn]KafkaMqCollects.begin参数异常: params为空或首元素为null");
            return null;
        }
        KafkaMQTraceNode node = new KafkaMQTraceNode();
        try {
            InvocationProducerAdapter invocation = new InvocationProducerAdapter(params[0]);
            node.setTraceId(traceContext.getTraceSession().getTraceId());
            node.setTraceNodeId(traceContext.getTraceSession().getNextNodeId());
            node.setBeginTime(System.currentTimeMillis());
            node.setProducerRecord(params[0].toString());
            try {
                if (node.getTraceId() != null) {
                    invocation.addHeader("_traceId", node.getTraceId().getBytes());
                }
                if (traceContext.getTraceSession().getCurrentNodeId() != null) {
                    invocation.addHeader("_parentTraceNodeId",
                            traceContext.getTraceSession().getCurrentNodeId().getBytes());
                }
            } catch (Throwable t) {
                logger.error("[Agent-EXCError]KafkaMqCollects.begin header添加异常. " + StackTraceFormatter.formatExceptionWithAgentMark(t));
            }
        } catch (Throwable t) {
            logger.error("[Agent-EXCError]KafkaMqCollects.begin异常. " + StackTraceFormatter.formatExceptionWithAgentMark(t));
            return null;
        }
        return node;
    }

    public void end(KafkaMQTraceNode node, Object[] params) {
        if (node == null || traceContext == null || traceContext.getTraceSession() == null) {
            return;
        }
        try {
            node.setEndTime(System.currentTimeMillis());
            long userTime = node.getEndTime() - node.getBeginTime();
            node.setUseTime(userTime);
            if (node.getError() != null) {
                node.setStatus(TraceNode.Status.fail.toString());
            } else {
                node.setStatus(TraceNode.Status.succeed.toString());
            }
            traceContext.getTraceSession().saveNode(node);
        } catch (Throwable t) {
            logger.error("[Agent-EXCError]KafkaMqCollects.end异常. " + StackTraceFormatter.formatExceptionWithAgentMark(t));
        }
    }

    public void error(KafkaMQTraceNode node, Throwable e) {
        if (node == null || traceContext == null || traceContext.getTraceSession() == null) {
            return;
        }
        node.setError(buildError(e));
    }

    @Override
    public byte[] transform(ClassLoader loader, String className, ProtectionDomain protectionDomain, byte[] classfileBuffer) {
        if (!PRODUCER_CLASS.equals(className)) {
            return null;
        }
        getJarAndVersion(protectionDomain);
        CtClass ctClass = null;
        try {
            ctClass = AgentByteBuild.toCtClass(loader, className, classfileBuffer);
            AgentByteBuild byteLoade = new AgentByteBuild(className, loader, ctClass);
            CtMethod m = ctClass.getMethod(PRODUCER_METHOD, PRODUCER_METHOD_DESC);
            AgentByteBuild.MethodSrcBuild build = new AgentByteBuild.MethodSrcBuild();
            build.setBeginSrc(BEGIN_SRC);
            build.setErrorSrc(ERROR_SRC);
            build.setEndSrc(END_SRC);
            byteLoade.updateMethod(m, build);
            logger.info("[Agent-info]完成 KafkaMQ 采集器初始化.");
            return byteLoade.toByteCode();
        } catch (Throwable t) {
            logger.error("[Agent-error]KafkaMqCollects.transform异常. " + StackTraceFormatter.formatExceptionWithAgentMark(t));
            return null;
        } finally {
            if (ctClass != null) {
                try {
                    ctClass.detach(); // 释放CtClass资源
                } catch (Throwable ignore) {
                }
            }
        }
    }

}
