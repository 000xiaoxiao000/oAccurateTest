package com.oAT.agent.collect;

import com.oAT.agent.collect.rocketmq.InvocationAdapter;
import com.oAT.agent.common.StackTraceFormatter;
import com.oAT.agent.common.logger.Log;
import com.oAT.agent.common.logger.LogFactory;
import com.oAT.agent.context.AgentContext;
import com.oAT.agent.jacoco.CoverageCollector;
import com.oAT.agent.model.RocketMQConsumerTraceNode;
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
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicInteger;

public class RocketMqReceiveCollects extends AbstractByteTransformCollect implements ICollect {
    private final static Log logger = LogFactory.getLog(RocketMqReceiveCollects.class);
    public static RocketMqReceiveCollects INSTANCE;
    //    private static final String CONSUMER_CLASS = "org.apache.rocketmq.client.consumer.DefaultMQPushConsumer";
//    private static final String CONSUMER_METHOD = "getMessageListener";
    private static final String CONSUMER_CLASS = "org.apache.rocketmq.spring.support" +
            ".DefaultRocketMQListenerContainer$DefaultMessageListenerConcurrently";
    private static final String CONSUMER_METHOD = "consumeMessage";
    private static final String CONSUMER_METHOD_DESC = "(Ljava/util/List;Lorg/apache/rocketmq/client/consumer" +
            "/listener/ConsumeConcurrentlyContext;)" +
            "Lorg/apache/rocketmq/client/consumer/listener/ConsumeConcurrentlyStatus;";

    private final TraceContext traceContext;
    private static final String BEGIN_SRC;
    private static final String END_SRC;
    private static final String ERROR_SRC;

    public RocketMqReceiveCollects(TraceContext context, Instrumentation instrumentation) {
        super(instrumentation);
        this.traceContext = context;
    }

    public RocketMqReceiveCollects(TraceContext context) {
        super();
        this.traceContext = context;
    }

    //this.defaultMQPushConsumerImpl
    static {
        BEGIN_SRC =
                RocketMqReceiveCollects.class.getName() + " instance = " + RocketMqReceiveCollects.class.getName() +
                        ".INSTANCE;\r\n" +
                RocketMqReceiveTraceNodeWrapper.class.getName() + " node = instance.begin" +
                        "($args);\r\n";
        END_SRC = "instance.end(node, $args);";
        ERROR_SRC = "instance.error(node, e);";
    }

    //    Object defaultMQPushConsumerImpl
    public RocketMqReceiveTraceNodeWrapper begin(Object[] params) {
        try {
            if (params == null || params.length == 0) {
                logger.warn("[Agent-bytecode-safety]RocketMqReceiveCollects.begin 参数非法");
                return null;
            }
            List<?> paramList = (List<?>) params[0];
            if (paramList.isEmpty()) {
                logger.warn("[Agent-bytecode-safety]RocketMqReceiveCollects.begin paramList为空");
                return null;
            }
            for (Object param : paramList) {
                if (param == null) {
                    continue;
                }
                InvocationAdapter invocationAdapter;
                try {
                    invocationAdapter = new InvocationAdapter(param);
                } catch (Throwable t) {
                    logger.error("[Agent-EXCError]InvocationAdapter构造异常. " + StackTraceFormatter.formatExceptionWithAgentMark(t));
                    continue;
                }
                TraceRequest request = getTraceRequest(invocationAdapter);
                boolean localRoot = false;
                if (request == null) {
                    request = createLocalRootRequest();
                    localRoot = true;
                }
                final TraceSession session = traceContext.openTraceSession(CONSUMER_CLASS, CONSUMER_METHOD, request);
                if (session == null) {
                    logger.warn("[Agent-EXCError]TraceSession创建失败. ");
                    return null;
                }
                RocketMQConsumerTraceNode node = new RocketMQConsumerTraceNode();
                try {
                    node.setTraceId(traceContext.getTraceSession().getTraceId());
                    node.setTraceNodeId(localRoot ? "0" : traceContext.getTraceSession().getParentNodeId() + ".remote");
                } catch (Throwable t) {
                    logger.error("[Agent-EXCError]TraceSession属性获取异常. " + StackTraceFormatter.formatExceptionWithAgentMark(t));
                }
                node.setBeginTime(System.currentTimeMillis());
                String messageExtStr;
                try {
                    messageExtStr = convertMessageBodyToString(param.toString());
                } catch (Throwable t) {
                    logger.error("[Agent-EXCError]消息体转换异常. " + StackTraceFormatter.formatExceptionWithAgentMark(t));
                    messageExtStr = param.toString();
                }
                node.setConsumer(messageExtStr);
                node.setMessage(messageExtStr);
                RocketMqReceiveTraceNodeWrapper wrapper = new RocketMqReceiveTraceNodeWrapper(session, node);
                if (AsyncCoverageSupport.enabled(traceContext)) {
                    wrapper.coverageCollector = AsyncCoverageSupport.begin(wrapper);
                }
                return wrapper;
            }
        } catch (Throwable t) {
            logger.error("[Agent-EXCError]begin error. "
                    + StackTraceFormatter.formatExceptionWithAgentMark(t));
        }
        return null;
    }

    private static String convertMessageBodyToString(String message) {
        if (message == null) {
            return null;
        }
        try {
            int bodyStart = message.indexOf("body=[");
            int bodyEnd = message.indexOf("], transactionId");
            if (bodyStart < 0 || bodyEnd < 0 || bodyEnd <= bodyStart + 6) {
                return message;
            }
            String bodyPart = message.substring(bodyStart + 6, bodyEnd);
            String[] byteValues = bodyPart.split(", ");
            byte[] byteArray = new byte[byteValues.length];
            for (int i = 0; i < byteValues.length; i++) {
                try {
                    byteArray[i] = Byte.parseByte(byteValues[i]);
                } catch (Throwable e) {
                    byteArray[i] = 0;
                }
            }
            String bodyString = new String(byteArray);
            return message.replace(bodyPart, "\"" + bodyString + "\"");
        } catch (Throwable t) {
            // 安全兜底，异常时返回原始字符串
            return message;
        }
    }

    public void end(RocketMqReceiveTraceNodeWrapper nodeWrapper, Object[] params) {
        boolean deferred = false;
        try {
            if (nodeWrapper == null) {
                logger.warn("[Agent-warn]end nodeWrapper为空");
                return;
            }
            TraceSession session = traceContext.getTraceSession();
            if (session == null) {
                logger.warn("[Agent-warn]end session为空");
                return;
            }
            RocketMQConsumerTraceNode node = nodeWrapper.node;
            try {
                node.setEndTime(System.currentTimeMillis());
                long userTime = node.getEndTime() - node.getBeginTime();
                node.setUseTime(userTime);
                if (node.getError() != null) {
                    node.setStatus(TraceNode.Status.fail.toString());
                } else {
                    node.setStatus(TraceNode.Status.succeed.toString());
                }
                if (nodeWrapper.coverageCollector != null && AgentContext.hasPendingAsyncTasks()) {
                    deferred = true;
                    nodeWrapper.markDeferred();
                    AtomicInteger activeAsyncTaskCount = AgentContext.getActiveAsyncTaskCount();
                    int pendingAsyncTasks = activeAsyncTaskCount == null ? 0 : activeAsyncTaskCount.get();
                    nodeWrapper.detachCurrentThreadContext();
                    logger.info("[Agent-info]RocketMqReceiveCollects 检测到异步任务仍在执行，延迟覆盖率汇总，pendingAsyncTasks="
                            + pendingAsyncTasks + ", traceId=" + node.getTraceId());
                } else {
                    nodeWrapper.collectCoverage();
                    session.saveNode(node);
                }
            } catch (Throwable t) {
                logger.error("[Agent-EXCError]end 节点处理异常. "
                        + StackTraceFormatter.formatExceptionWithAgentMark(t));
            } finally {
                try {
                    if (!deferred) {
                        nodeWrapper.doDestroy();
                    }
                } catch (Throwable t) {
                    logger.error("[Agent-EXCError]end doDestroy异常. "
                            + StackTraceFormatter.formatExceptionWithAgentMark(t));
                }
            }
        } catch (Throwable t) {
            logger.error("[Agent-EXCError]end error. "
                    + StackTraceFormatter.formatExceptionWithAgentMark(t));
        }
    }

    public void error(RocketMqReceiveTraceNodeWrapper nodeWrapper, Throwable e) {
        if (nodeWrapper == null) {
            logger.warn("[Agent-warn]error nodeWrapper为空");
            return;
        }
        if (traceContext.getTraceSession() == null) {
            logger.warn("[Agent-warn]error session为空");
            return;
        }
        try {
            nodeWrapper.node.setError(buildError(e));
        } catch (Throwable t) {
            logger.error("[Agent-EXCError]setError 节点设置异常. "
                    + StackTraceFormatter.formatExceptionWithAgentMark(t));
        }
    }

    private TraceRequest getTraceRequest(InvocationAdapter invocationAdapter) {
        try {
            Map<String, String> properties = invocationAdapter.getProperties();
            if (properties == null) {
                return null;
            }
            String traceId = properties.get("_traceId");
            String parentNodeId = properties.get("_parentTraceNodeId");
            String traceProperties = properties.get("_traceProperties");
            if (traceId != null && parentNodeId != null) {
                TraceRequest request = new TraceRequest();
                request.setTraceId(traceId);
                request.setParentNodeCallId(parentNodeId);
                if (traceProperties != null) {
                    try {
                        Properties p = new Properties();
                        p.load(new ByteArrayInputStream(Base64.decodeBase64(traceProperties)));
                        request.setProperties(p);
                    } catch (Throwable e) {
                        logger.error("[Agent-EXCError]error. "
                                + StackTraceFormatter.formatExceptionWithAgentMark(e));
                    }
                }
                return request;
            }
        } catch (Throwable t) {
            logger.error("[Agent-EXCError]getTraceRequest异常. "
                    + StackTraceFormatter.formatExceptionWithAgentMark(t));
        }
        return null;
    }

    private TraceRequest createLocalRootRequest() {
        TraceRequest request = new TraceRequest();
        request.setTraceId(traceContext.createTraceId());
        request.setParentNodeCallId("0");
        request.setProperties(new Properties());
        return request;
    }

    public class RocketMqReceiveTraceNodeWrapper implements ISessionDestroy, AgentContext.AsyncCompletionListener {
        private final TraceSession session;
        private final RocketMQConsumerTraceNode node;
        private CoverageCollector coverageCollector;
        private boolean deferred;
        private boolean destroyed;

        public RocketMqReceiveTraceNodeWrapper(TraceSession session, RocketMQConsumerTraceNode node) {
            this.session = session;
            this.node = node;
        }

        public void onAsyncComplete(TraceSession traceSession) {
            if (traceSession == null || !traceSession.getTraceId().equals(this.session.getTraceId())) {
                return;
            }
            if (!deferred) {
                return;
            }
            try {
                clearDeferred();
                collectCoverage();
                session.saveNode(node);
            } catch (Throwable t) {
                logger.error("[Agent-EXCError]RocketMqReceiveCollects 异步覆盖率补报失败. "
                        + StackTraceFormatter.formatExceptionWithAgentMark(t));
            } finally {
                doDestroy();
            }
        }

        private void collectCoverage() {
            AsyncCoverageSupport.collect(node, coverageCollector);
        }

        private void markDeferred() {
            this.deferred = true;
        }

        private void clearDeferred() {
            this.deferred = false;
        }

        @Override
        public void doDestroy() {
            if (destroyed || deferred) {
                return;
            }
            destroyed = true;
            try {
                detachCurrentThreadContext();
                traceContext.closeTraceSession(session);
            } catch (Throwable t) {
                logger.error("[Agent-EXCError]doDestroy异常. "
                        + StackTraceFormatter.formatExceptionWithAgentMark(t));
            }
        }

        private void detachCurrentThreadContext() {
            AsyncCoverageSupport.detach(coverageCollector);
        }
    }

    @Override
    public byte[] transform(ClassLoader loader, String className, ProtectionDomain protectionDomain, byte[] classfileBuffer) {
        try {
            if (!CONSUMER_CLASS.equals(className)) {
                return null;
            }
            getJarAndVersion(protectionDomain);
            CtClass ctClass = null;
            try {
                ctClass = AgentByteBuild.toCtClass(loader, className, classfileBuffer);
                AgentByteBuild byteLoade = new AgentByteBuild(className, loader, ctClass);
                CtMethod m = ctClass.getMethod(CONSUMER_METHOD, CONSUMER_METHOD_DESC);
                AgentByteBuild.MethodSrcBuild build = new AgentByteBuild.MethodSrcBuild();
                build.setBeginSrc(BEGIN_SRC);
                build.setErrorSrc(ERROR_SRC);
                build.setEndSrc(END_SRC);
                byteLoade.updateMethod(m, build);
                logger.info("[Agent-info]完成 RocketMQReceive 采集器初始化.");
                return byteLoade.toByteCode();
            } finally {
                if (ctClass != null) {
                    try {
                        ctClass.detach();
                    } catch (Throwable t) {
                        logger.warn("[Agent-EXCError]ctClass.detach异常. "
                                + StackTraceFormatter.formatExceptionWithAgentMark(t));
                    }
                }
            }
        } catch (Throwable t) {
            logger.error("[Agent-EXCError]transform error. "
                    + StackTraceFormatter.formatExceptionWithAgentMark(t));
            return null;
        }
    }
}
