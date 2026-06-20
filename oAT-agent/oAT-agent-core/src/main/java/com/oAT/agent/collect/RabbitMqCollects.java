package com.oAT.agent.collect;

import com.oAT.agent.collect.rabbitmq.InvocationAdapter;
import com.oAT.agent.common.StackTraceFormatter;
import com.oAT.agent.common.logger.Log;
import com.oAT.agent.common.logger.LogFactory;
import com.oAT.agent.model.RabbitMQTraceNode;
import com.oAT.agent.trace.TraceContext;
import com.oAT.shaded.javassist.*;

import java.lang.instrument.Instrumentation;
import java.security.ProtectionDomain;

public class RabbitMqCollects extends AbstractByteTransformCollect implements ICollect {
    private final static Log logger = LogFactory.getLog(RabbitMqCollects.class);
    public static RabbitMqCollects INSTANCE;
    private static final String CHANNELN_CLASS = "com.rabbitmq.client.impl.ChannelN";
    private static final String PUBLISH_METHOD = "basicPublish";
    private static final String PUBLISH_METHOD_DESC = "(Ljava/lang/String;Ljava/lang/String;ZLcom/rabbitmq/client/AMQP$BasicProperties;[B)V";

    private final TraceContext traceContext;
    private static final String BEGIN_SRC;
    private static final String END_SRC;
    private static final String ERROR_SRC;

    public RabbitMqCollects(TraceContext context, Instrumentation instrumentation) {
        super(instrumentation);
        this.traceContext = context;
    }

    public RabbitMqCollects(TraceContext context) {
        super();
        this.traceContext = context;
    }

    static {
        BEGIN_SRC = RabbitMqCollects.class.getName() + " instance = " + RabbitMqCollects.class.getName() + ".INSTANCE;\r\n" +
                RabbitMQTraceNode.class.getName() + " node = instance.begin($args);\r\n";
        END_SRC = "instance.end(node, $args);";
        ERROR_SRC = "instance.error(node, e);";
    }

    public RabbitMQTraceNode begin(Object[] params) {
        try {
            if (traceContext == null || traceContext.getTraceSession() == null) {
                return null;
            }
            if (params == null || params.length < 5) {
                logger.warn("RabbitMqCollects.begin: 参数长度不足，无法采集。");
                return null;
            }
            if (!(params[0] instanceof String) || !(params[1] instanceof String) || params[3] == null || !(params[4] instanceof byte[])) {
                logger.warn("RabbitMqCollects.begin: 参数类型不匹配，无法采集。");
                return null;
            }
            RabbitMQTraceNode node = new RabbitMQTraceNode();
            InvocationAdapter invocation = new InvocationAdapter(params[3]);
            node.setTraceId(traceContext.getTraceSession().getTraceId());
            node.setTraceNodeId(traceContext.getTraceSession().getNextNodeId());
            node.setBeginTime(System.currentTimeMillis());
            node.setExchange((String) params[0]);
            node.setRoutingKey((String) params[1]);
            node.setBody(new String((byte[]) params[4]));
            invocation.addHeader("_traceId", node.getTraceId());
            invocation.addHeader("_parentTraceNodeId", traceContext.getTraceSession().getCurrentNodeId());
            return node;
        } catch (Throwable t) {
            logger.error("[Agent-EXCError]begin: 采集异常. " + StackTraceFormatter.formatExceptionWithAgentMark(t));
            return null;
        }
    }

    public void end(RabbitMQTraceNode node, Object[] params) {
        try {
            if (node == null || traceContext == null || traceContext.getTraceSession() == null) {
                return;
            }
            node.setEndTime(System.currentTimeMillis());
            long useTime = node.getEndTime() - node.getBeginTime();
            node.setUseTime(useTime);
            if (node.getError() != null) {
                node.setStatus(RabbitMQTraceNode.Status.fail.toString());
            } else {
                node.setStatus(RabbitMQTraceNode.Status.succeed.toString());
            }
            traceContext.getTraceSession().saveNode(node);
        } catch (Throwable t) {
            logger.error("[Agent-EXCError]end: 采集异常. " + StackTraceFormatter.formatExceptionWithAgentMark(t));
        }
    }

    public void error(RabbitMQTraceNode node, Throwable e) {
        try {
            if (node == null || traceContext == null || traceContext.getTraceSession() == null) {
                return;
            }
            node.setError(buildError(e));
        } catch (Throwable t) {
            logger.error("[Agent-EXCError]error: 采集异常. " + StackTraceFormatter.formatExceptionWithAgentMark(t));
        }
    }

    @Override
    public byte[] transform(ClassLoader loader, String className, ProtectionDomain protectionDomain, byte[] classfileBuffer) {
        if (!CHANNELN_CLASS.equals(className)) {
            return null;
        }
        getJarAndVersion(protectionDomain);
        CtClass ctClass = null;
        try {
            ctClass = AgentByteBuild.toCtClass(loader, className, classfileBuffer);
            AgentByteBuild byteLoade = new AgentByteBuild(className, loader, ctClass);
            CtMethod m = ctClass.getMethod(PUBLISH_METHOD, PUBLISH_METHOD_DESC);
            AgentByteBuild.MethodSrcBuild build = new AgentByteBuild.MethodSrcBuild();
            build.setBeginSrc(BEGIN_SRC);
            build.setErrorSrc(ERROR_SRC);
            build.setEndSrc(END_SRC);
            byteLoade.updateMethod(m, build);
            logger.info("[Agent-info]完成 RabbitMQ 采集器初始化.");
            return byteLoade.toByteCode();
        } catch (Throwable t) {
            logger.error("[Agent-EXCError]transform: 字节码转换异常. " + StackTraceFormatter.formatExceptionWithAgentMark(t));
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
