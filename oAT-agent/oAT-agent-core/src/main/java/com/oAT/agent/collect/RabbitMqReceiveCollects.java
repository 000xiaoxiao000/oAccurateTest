package com.oAT.agent.collect;

import com.oAT.agent.collect.rabbitmq.InvocationAdapter;
import com.oAT.agent.common.StackTraceFormatter;
import com.oAT.agent.common.logger.Log;
import com.oAT.agent.common.logger.LogFactory;
import com.oAT.agent.model.RabbitMQRemoteTraceNode;
import com.oAT.agent.model.RabbitMQTraceNode;
import com.oAT.agent.trace.ISessionDestroy;
import com.oAT.agent.trace.TraceContext;
import com.oAT.agent.trace.TraceRequest;
import com.oAT.agent.trace.TraceSession;
import com.oAT.shaded.javassist.*;

import java.lang.instrument.Instrumentation;
import java.security.ProtectionDomain;
import java.util.Map;

public class RabbitMqReceiveCollects extends AbstractByteTransformCollect implements ICollect {
    private final static Log logger = LogFactory.getLog(RabbitMqReceiveCollects.class);
    public static RabbitMqReceiveCollects INSTANCE;
    private static final String CONSUMER_DISPATCHER_CLASS = "com.rabbitmq.client.impl.ConsumerDispatcher";
    private static final String HANDLE_DELIVERY_METHOD = "handleDelivery";
    private static final String HANDLE_DELIVERY_METHOD_DESC = "(Lcom/rabbitmq/client/Consumer;Ljava/lang/String;" +
            "Lcom/rabbitmq/client/Envelope;Lcom/rabbitmq/client/AMQP$BasicProperties;[B)V";

    private final TraceContext traceContext;
    private static final String BEGIN_SRC;
    private static final String END_SRC;
    private static final String ERROR_SRC;

    public RabbitMqReceiveCollects(TraceContext context, Instrumentation instrumentation) {
        super(instrumentation);
        this.traceContext = context;
    }

    public RabbitMqReceiveCollects(TraceContext context) {
        super();
        this.traceContext = context;
    }

    static {
        BEGIN_SRC =
                RabbitMqReceiveCollects.class.getName() + " instance = " + RabbitMqReceiveCollects.class.getName() +
                        ".INSTANCE;\r\n" +
                        RabbitMqReceiveTraceNodeWrapper.class.getName() + " node = instance" +
                        ".begin($args);";
        END_SRC = "instance.end(node, $args);";
        ERROR_SRC = "instance.error(node, e);";
    }

    public RabbitMqReceiveTraceNodeWrapper begin(Object[] params) {
        try {
            if (params == null || params.length < 5 || params[3] == null || params[1] == null || params[4] == null) {
                logger.warn("[Agent-begin]RabbitMqReceiveCollects begin params invalid");
                return null;
            }
            InvocationAdapter invocation;
            try {
                invocation = new InvocationAdapter(params[3]);
            } catch (Throwable t) {
                logger.error("[Agent-EXCError]InvocationAdapter error. " + StackTraceFormatter.formatExceptionWithAgentMark(t));
                return null;
            }
            TraceRequest request = getTraceRequest(invocation);
            if (request == null) {
                return null;
            }
            final TraceSession session;
            try {
                session = traceContext.openTraceSession(CONSUMER_DISPATCHER_CLASS, HANDLE_DELIVERY_METHOD, request);
            } catch (Throwable t) {
                logger.error("[Agent-EXCError]openTraceSession error. " + StackTraceFormatter.formatExceptionWithAgentMark(t));
                return null;
            }
            if (session == null) {
                return null;
            }
            RabbitMQRemoteTraceNode node = new RabbitMQRemoteTraceNode();
            TraceSession currentSession = null;
            try {
                currentSession = traceContext.getTraceSession();
            } catch (Throwable t) {
                logger.error("[Agent-EXCError]getTraceSession error" + StackTraceFormatter.formatExceptionWithAgentMark(t));
            }
            if (currentSession == null) {
                logger.warn("[Agent-EXCError]traceContext.getTraceSession() is null");
                return null;
            }
            node.setTraceId(currentSession.getTraceId());
            node.setTraceNodeId(currentSession.getParentNodeId() + ".remote");
            node.setBeginTime(System.currentTimeMillis());
            node.setConsumerTag(params[1] instanceof String ? (String) params[1] : String.valueOf(params[1]));
            node.setBody(params[4] instanceof byte[] ? new String((byte[]) params[4]) : String.valueOf(params[4]));
            try {
                if (params[2] != null) {
                    Object exchange = null;
                    Object routingKey = null;
                    try {
                        exchange = params[2].getClass().getMethod("getExchange").invoke(params[2]);
                    } catch (Throwable t) {
                        logger.error("[Agent-EXCError]getExchange reflect error. " + StackTraceFormatter.formatExceptionWithAgentMark(t));
                    }
                    try {
                        routingKey = params[2].getClass().getMethod("getRoutingKey").invoke(params[2]);
                    } catch (Throwable t) {
                        logger.error("[Agent-EXCError]getRoutingKey reflect error. " + StackTraceFormatter.formatExceptionWithAgentMark(t));
                    }
                    node.setExchange(exchange != null ? exchange.toString() : "");
                    node.setRoutingKey(routingKey != null ? routingKey.toString() : "");
                }
            } catch (Throwable e) {
                logger.error("[Agent-EXCError]RabbitMqReceiveCollects begin error. " + StackTraceFormatter.formatExceptionWithAgentMark(e));
            }
            return new RabbitMqReceiveTraceNodeWrapper(session, node);
        } catch (Throwable t) {
            logger.error("[Agent-EXCError]RabbitMqReceiveCollects begin outer error. " + StackTraceFormatter.formatExceptionWithAgentMark(t));
            return null;
        }
    }

    private TraceRequest getTraceRequest(InvocationAdapter invocationAdapter) {
        try {
            if (invocationAdapter == null) {
                return null;
            }
            Map<String, Object> headers;
            try {
                headers = invocationAdapter.getHeaders();
            } catch (Throwable t) {
                logger.error("[Agent-EXCError]getHeaders error. " + StackTraceFormatter.formatExceptionWithAgentMark(t));
                return null;
            }
            if (headers == null) {
                return null;
            }
            Object traceIdObj = headers.get("_traceId");
            Object parentNodeIdObj = headers.get("_parentTraceNodeId");
            if (traceIdObj == null || parentNodeIdObj == null) {
                return null;
            }
            String traceId = traceIdObj instanceof String ? (String) traceIdObj : traceIdObj.toString();
            String parentNodeId = parentNodeIdObj instanceof String ? (String) parentNodeIdObj : parentNodeIdObj.toString();
            if (traceId != null && parentNodeId != null) {
                TraceRequest request = new TraceRequest();
                request.setTraceId(traceId);
                request.setParentNodeCallId(parentNodeId);
                return request;
            }
            return null;
        } catch (Throwable t) {
            logger.error("[Agent-EXCError]error. " + StackTraceFormatter.formatExceptionWithAgentMark(t));
            return null;
        }
    }

    public void end(RabbitMqReceiveTraceNodeWrapper nodeWrapper, Object[] params) {
        TraceSession session = null;
        try {
            try {
                session = traceContext.getTraceSession();
            } catch (Throwable t) {
                logger.error("[Agent-EXCError]getTraceSession error. " + StackTraceFormatter.formatExceptionWithAgentMark(t));
            }
            if (nodeWrapper == null || session == null) {
                return;
            }
            RabbitMQRemoteTraceNode node = nodeWrapper.node;
            node.setEndTime(System.currentTimeMillis());
            long useTime = node.getEndTime() - node.getBeginTime();
            node.setUseTime(useTime);
            if (node.getError() != null) {
                node.setStatus(RabbitMQTraceNode.Status.fail.toString());
            } else {
                node.setStatus(RabbitMQTraceNode.Status.succeed.toString());
            }
            try {
                session.saveNode(node);
            } catch (Throwable t) {
                logger.error("[Agent-EXCError]saveNode error. " + StackTraceFormatter.formatExceptionWithAgentMark(t));
            }
        } catch (Throwable t) {
            logger.error("[Agent-EXCError]RabbitMqReceiveCollects end error. " + StackTraceFormatter.formatExceptionWithAgentMark(t));
        } finally {
            if (nodeWrapper != null) {
                try {
                    nodeWrapper.doDestroy();
                } catch (Throwable t) {
                    logger.error("[Agent-EXCError]RabbitMqReceiveCollects doDestroy error. " + StackTraceFormatter.formatExceptionWithAgentMark(t));
                }
            }
        }
    }

    public void error(RabbitMqReceiveTraceNodeWrapper nodeWrapper, Throwable e) {
        try {
            if (nodeWrapper == null) {
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
                logger.error("[Agent-EXCError]setError error. " + StackTraceFormatter.formatExceptionWithAgentMark(t));
            }
        } catch (Throwable t) {
            logger.error("[Agent-EXCError]RabbitMqReceiveCollects error error. " + StackTraceFormatter.formatExceptionWithAgentMark(t));
        }
    }

    public class RabbitMqReceiveTraceNodeWrapper implements ISessionDestroy {
        private final TraceSession session;
        private final RabbitMQRemoteTraceNode node;

        public RabbitMqReceiveTraceNodeWrapper(TraceSession session, RabbitMQRemoteTraceNode node) {
            this.session = session;
            this.node = node;
        }

        @Override
        public void doDestroy() {
            try {
                if (session != null) {
                    try {
                        traceContext.closeTraceSession(session);
                    } catch (Throwable t) {
                        logger.error("[Agent-EXCError]closeTraceSession error. " + StackTraceFormatter.formatExceptionWithAgentMark(t));
                    }
                }
            } catch (Throwable t) {
                logger.error("[Agent-EXCError]doDestroy error. "
                        + StackTraceFormatter.formatExceptionWithAgentMark(t));
            }
        }
    }

    @Override
    public byte[] transform(ClassLoader loader, String className, ProtectionDomain protectionDomain, byte[] classfileBuffer) {
        try {
            if (!CONSUMER_DISPATCHER_CLASS.equals(className)) {
                return null;
            }
            getJarAndVersion(protectionDomain);
            CtClass ctClass;
            try {
                ctClass = AgentByteBuild.toCtClass(loader, className, classfileBuffer);
            } catch (Throwable t) {
                logger.error("[Agent-EXCError]toCtClass error. " + StackTraceFormatter.formatExceptionWithAgentMark(t));
                return null;
            }
            AgentByteBuild byteLoade;
            try {
                byteLoade = new AgentByteBuild(className, loader, ctClass);
            } catch (Throwable t) {
                logger.error("[Agent-EXCError]AgentByteBuild error. " + StackTraceFormatter.formatExceptionWithAgentMark(t));
                if (ctClass != null) {
                    try { ctClass.detach(); } catch (Throwable ignore) {}
                }
                return null;
            }
            CtMethod m;
            try {
                m = ctClass.getMethod(HANDLE_DELIVERY_METHOD, HANDLE_DELIVERY_METHOD_DESC);
            } catch (Throwable t) {
                logger.error("[Agent-EXCError]getMethod error" + StackTraceFormatter.formatExceptionWithAgentMark(t));
                if (ctClass != null) {
                    try { ctClass.detach(); } catch (Throwable ignore) {}
                }
                return null;
            }
            AgentByteBuild.MethodSrcBuild build = new AgentByteBuild.MethodSrcBuild();
            build.setBeginSrc(BEGIN_SRC);
            build.setErrorSrc(ERROR_SRC);
            build.setEndSrc(END_SRC);
            try {
                byteLoade.updateMethod(m, build);
            } catch (Throwable t) {
                logger.error("[Agent-EXCError]updateMethod error. " + StackTraceFormatter.formatExceptionWithAgentMark(t));
                try {
                    ctClass.detach();
                } catch (Throwable ignore) {
                }
                return null;
            }
            logger.info("[Agent-info]完成 RabbitMQRemote 采集器初始化.");
            byte[] byteCode = null;
            try {
                byteCode = byteLoade.toByteCode();
            } catch (Throwable t) {
                logger.error("[Agent-EXCError]toByteCode error. " + StackTraceFormatter.formatExceptionWithAgentMark(t));
            }
            try {
                ctClass.detach();
            } catch (Throwable ignore) {
            }
            return byteCode;
        } catch (Throwable t) {
            logger.error("[Agent-EXCError]transform error. "
                    + StackTraceFormatter.formatExceptionWithAgentMark(t));
            return null;
        }
    }
}
