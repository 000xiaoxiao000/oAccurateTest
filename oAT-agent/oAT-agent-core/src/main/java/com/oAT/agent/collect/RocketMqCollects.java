package com.oAT.agent.collect;

import com.oAT.agent.collect.rocketmq.InvocationAdapter;
import com.oAT.agent.common.StackTraceFormatter;
import com.oAT.agent.common.logger.Log;
import com.oAT.agent.common.logger.LogFactory;
import com.oAT.agent.model.RocketMQProducerTraceNode;
import com.oAT.agent.model.TraceNode;
import com.oAT.agent.trace.TraceContext;
import com.oAT.agent.trace.TraceSession;
import com.oAT.shaded.apache.commons.codec.binary.Base64;
import com.oAT.shaded.javassist.CtClass;
import com.oAT.shaded.javassist.CtMethod;

import java.io.ByteArrayOutputStream;
import java.lang.instrument.Instrumentation;
import java.security.ProtectionDomain;
import java.util.*;
import java.util.logging.Level;

public class RocketMqCollects extends AbstractByteTransformCollect implements ICollect {
    private final static Log logger = LogFactory.getLog(RocketMqCollects.class);
    public static RocketMqCollects INSTANCE;
    private static final String PRODUCER_CLASS = "org.apache.rocketmq.client.producer.DefaultMQProducer";
    private static final Map<String, List<String>> PRODUCER_METHOD_METHODDESCS = new HashMap<>();

    private final TraceContext traceContext;
    private static final String BEGIN_SRC;
    private static final String END_SRC;
    private static final String ERROR_SRC;

    public RocketMqCollects(TraceContext context, Instrumentation instrumentation) {
        super(instrumentation);
        this.traceContext = context;

        List<String> sendDescs = Arrays.asList(
                "(Lorg/apache/rocketmq/common/message/Message;)Lorg/apache/rocketmq/client/producer/SendResult;",
                "(Lorg/apache/rocketmq/common/message/Message;J)Lorg/apache/rocketmq/client/producer/SendResult;",
                "(Lorg/apache/rocketmq/common/message/Message;Lorg/apache/rocketmq/client/producer/SendCallback;)V",
                "(Lorg/apache/rocketmq/common/message/Message;Lorg/apache/rocketmq/client/producer/SendCallback;J)V",
                "(Lorg/apache/rocketmq/common/message/Message;Lorg/apache/rocketmq/common/message/MessageQueue;)" +
                        "Lorg/apache/rocketmq/client/producer/SendResult;",
                "(Lorg/apache/rocketmq/common/message/Message;Lorg/apache/rocketmq/common/message/MessageQueue;J)" +
                        "Lorg/apache/rocketmq/client/producer/SendResult;",
                "(Lorg/apache/rocketmq/common/message/Message;Lorg/apache/rocketmq/common/message/MessageQueue;" +
                        "Lorg/apache/rocketmq/client/producer/SendCallback;)V",
                "(Lorg/apache/rocketmq/common/message/Message;Lorg/apache/rocketmq/common/message/MessageQueue;" +
                        "Lorg/apache/rocketmq/client/producer/SendCallback;J)V",
                "(Lorg/apache/rocketmq/common/message/Message;" +
                        "Lorg/apache/rocketmq/client/producer/MessageQueueSelector;Ljava/lang/Object;)" +
                        "Lorg/apache/rocketmq/client/producer/SendResult;",
                "(Lorg/apache/rocketmq/common/message/Message;" +
                        "Lorg/apache/rocketmq/client/producer/MessageQueueSelector;Ljava/lang/Object;J)" +
                        "Lorg/apache/rocketmq/client/producer/SendResult;",
                "(Lorg/apache/rocketmq/common/message/Message;" +
                        "Lorg/apache/rocketmq/client/producer/MessageQueueSelector;Ljava/lang/Object;" +
                        "Lorg/apache/rocketmq/client/producer/SendCallback;)V",
                "(Lorg/apache/rocketmq/common/message/Message;" +
                        "Lorg/apache/rocketmq/client/producer/MessageQueueSelector;Ljava/lang/Object;" +
                        "Lorg/apache/rocketmq/client/producer/SendCallback;J)V",
                "(Ljava/util/Collection;)Lorg/apache/rocketmq/client/producer/SendResult;",
                "(Ljava/util/Collection;J)Lorg/apache/rocketmq/client/producer/SendResult;",
                "(Ljava/util/Collection;Lorg/apache/rocketmq/common/message/MessageQueue;)" +
                        "Lorg/apache/rocketmq/client/producer/SendResult;",
                "(Ljava/util/Collection;Lorg/apache/rocketmq/common/message/MessageQueue;J)" +
                        "Lorg/apache/rocketmq/client/producer/SendResult;"
                //"(Ljava/util/Collection;Lorg/apache/rocketmq/client/producer/SendCallback;)V",
                //"(Ljava/util/Collection;Lorg/apache/rocketmq/client/producer/SendCallback;J)V",
//                "(Ljava/util/Collection;Lorg/apache/rocketmq/common/message/MessageQueue;
//                Lorg/apache/rocketmq/client/producer/SendCallback;)V",
//                "(Ljava/util/Collection;Lorg/apache/rocketmq/common/message/MessageQueue;
//                Lorg/apache/rocketmq/client/producer/SendCallback;J)V"
        );
        PRODUCER_METHOD_METHODDESCS.put("send", sendDescs);

        List<String> sendOnewayDescs = Arrays.asList(
                "(Lorg/apache/rocketmq/common/message/Message;Lorg/apache/rocketmq/common/message/MessageQueue;)V",
                "(Lorg/apache/rocketmq/common/message/Message;" +
                        "Lorg/apache/rocketmq/client/producer/MessageQueueSelector;Ljava/lang/Object;)V"
        );
        PRODUCER_METHOD_METHODDESCS.put("sendOneway", sendOnewayDescs);
    }

    static {
        BEGIN_SRC = RocketMqCollects.class.getName() + " instance = " + RocketMqCollects.class.getName() + ".INSTANCE;" +
                "\r\n" +
                RocketMQProducerTraceNode.class.getName() + " node = instance.begin($args, this" +
                ".defaultMQProducerImpl);\r\n";
        END_SRC = "instance.end(node, $args);";
        ERROR_SRC = "instance.error(node, e);";
    }

    public RocketMQProducerTraceNode begin(Object[] params, Object defaultMQProducerImpl) {
        if (traceContext == null || traceContext.getTraceSession() == null) {
            return null;
        }
        if (params == null || params.length == 0 || params[0] == null || defaultMQProducerImpl == null) {
            return null;
        }
        String defaultMQProducerStr = "";
        try {
            Object producerObj =
                    defaultMQProducerImpl.getClass().getMethod("getDefaultMQProducer").invoke(defaultMQProducerImpl);
            if (producerObj != null) {
                defaultMQProducerStr = producerObj.toString();
            }
        } catch (Throwable e) {
            logger.error("[Agent-EXCError]" + Level.SEVERE + "RocketMqCollects begin error. " + StackTraceFormatter.formatExceptionWithAgentMark(e));
        }
        InvocationAdapter invocation;
        try {
            invocation = new InvocationAdapter(params[0]);
        } catch (Throwable t) {
            logger.error("[Agent-EXCError] InvocationAdapter error. " + StackTraceFormatter.formatExceptionWithAgentMark(t));
            return null;
        }
        RocketMQProducerTraceNode node = new RocketMQProducerTraceNode();
        node.setTraceId(traceContext.getTraceSession().getTraceId());
        node.setTraceNodeId(traceContext.getTraceSession().getNextNodeId());
        node.setBeginTime(System.currentTimeMillis());
        node.setProducer(defaultMQProducerStr);
        String messageStr;
        try {
            messageStr = convertMessageBodyToString(params[0].toString());
        } catch (Throwable t) {
            logger.error("[Agent-EXCError] convertMessageBodyToString error. " + StackTraceFormatter.formatExceptionWithAgentMark(t));
            messageStr = params[0].toString();
        }
        node.setMessage(messageStr);
        try {
            setUserProperty(invocation, traceContext.getTraceSession());
        } catch (Throwable t) {
            logger.error("[Agent-EXCError] setUserProperty error. " + StackTraceFormatter.formatExceptionWithAgentMark(t));
        }
        return node;
    }

    private static String convertMessageBodyToString(String message) {
        if (message == null) {
            return "";
        }
        int bodyStart = message.indexOf("body=[");
        int bodyEnd = message.indexOf("], transactionId");
        if (bodyStart == -1 || bodyEnd == -1 || bodyEnd <= bodyStart + 6) {
            return message;
        }
        String bodyPart = message.substring(bodyStart + 6, bodyEnd);
        String[] byteValues = bodyPart.split(", ");
        byte[] byteArray = new byte[byteValues.length];
        try {
            for (int i = 0; i < byteValues.length; i++) {
                byteArray[i] = Byte.parseByte(byteValues[i]);
            }
            String bodyString = new String(byteArray);
            return message.replace(bodyPart, "\"" + bodyString + "\"");
        } catch (Throwable t) {
            // 若解析失败，返回原始message
            return message;
        }
    }

    //设置参数，上下游传递mq节点信息
    private void setUserProperty(InvocationAdapter adapter, TraceSession session) {
        if (adapter == null || session == null) {
            return;
        }
        try {
            adapter.putUserProperty("_traceId", session.getTraceId());
            adapter.putUserProperty("_parentTraceNodeId", session.getCurrentNodeId());
            adapter.putUserProperty("_traceProperties", converProperties(session.getTraceRequest() != null ?
                    session.getTraceRequest().getProperties() : null));
        } catch (Throwable t) {
            logger.error("[Agent-EXCError]setUserProperty error. " + StackTraceFormatter.formatExceptionWithAgentMark(t));
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
            } catch (Throwable e) {
                logger.error("[Agent-EXCError]" + Level.SEVERE + "DubboInvokerCollect properties encode fail. "
                        + StackTraceFormatter.formatExceptionWithAgentMark(e));
                return null;
            }
        } catch (Throwable ignore) {
        }
        return "";
    }

    public void end(RocketMQProducerTraceNode node, Object[] parames) {
        if (node == null || traceContext == null || traceContext.getTraceSession() == null) {
            return;
        }
        node.setEndTime(System.currentTimeMillis());
        long userTime = node.getEndTime() - node.getBeginTime();
        node.setUseTime(userTime);
        if (node.getError() != null) {
            node.setStatus(TraceNode.Status.fail.toString());
        } else {
            node.setStatus(TraceNode.Status.succeed.toString());
        }
        try {
            traceContext.getTraceSession().saveNode(node);
        } catch (Throwable t) {
            logger.error("[Agent-EXCError]saveNode error. " + StackTraceFormatter.formatExceptionWithAgentMark(t));
        }
    }

    public void error(RocketMQProducerTraceNode node, Throwable e) {
        if (node == null || traceContext == null || traceContext.getTraceSession() == null) {
            return;
        }
        try {
            node.setError(buildError(e));
        } catch (Throwable t) {
            logger.error("[Agent-EXCError]setError error. " + StackTraceFormatter.formatExceptionWithAgentMark(t));
        }
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
            for (Map.Entry<String, List<String>> entry : PRODUCER_METHOD_METHODDESCS.entrySet()) {
                String methodName = entry.getKey();
                List<String> methodDescs = entry.getValue();
                for (String methodDesc : methodDescs) {
                    CtMethod m;
                    try {
                        m = ctClass.getMethod(methodName, methodDesc);
                    } catch (Throwable ex) {
                        logger.error("[Agent-EXCError]Method not found: " + methodName + methodDesc
                                + " " + StackTraceFormatter.formatExceptionWithAgentMark(ex));
                        continue;
                    }
                    AgentByteBuild.MethodSrcBuild build = new AgentByteBuild.MethodSrcBuild();
                    build.setBeginSrc(BEGIN_SRC);
                    build.setErrorSrc(ERROR_SRC);
                    build.setEndSrc(END_SRC);
                    try {
                        byteLoade.updateMethod(m, build);
                    } catch (Throwable t) {
                        logger.error("[Agent-EXCError]updateMethod error: " + methodName + methodDesc
                                + " " + StackTraceFormatter.formatExceptionWithAgentMark(t));
                    }
                }
            }
            logger.info("[Agent-info]完成 RocketMQ 采集器初始化.");
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
