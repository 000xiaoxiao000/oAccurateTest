package com.oAT.agent.collect;

import com.oAT.agent.collect.kafka.InvocationConsumerAdapter;
import com.oAT.agent.common.StackTraceFormatter;
import com.oAT.agent.common.logger.Log;
import com.oAT.agent.common.logger.LogFactory;
import com.oAT.agent.context.AgentContext;
import com.oAT.agent.jacoco.CoverageCollector;
import com.oAT.agent.model.KafkaMQRemoteTraceNode;
import com.oAT.agent.model.TraceNode;
import com.oAT.agent.trace.ISessionDestroy;
import com.oAT.agent.trace.TraceContext;
import com.oAT.agent.trace.TraceRequest;
import com.oAT.agent.trace.TraceSession;
import com.oAT.shaded.javassist.CtClass;
import com.oAT.shaded.javassist.CtMethod;

import java.lang.instrument.Instrumentation;
import java.security.ProtectionDomain;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

public class KafkaMqReceiveCollects extends AbstractByteTransformCollect implements ICollect {
    private final static Log logger = LogFactory.getLog(KafkaMqReceiveCollects.class);
    public static KafkaMqReceiveCollects INSTANCE;
    private static final String CONSUMER_CLASS = "org.apache.kafka.clients.consumer.internals.ConsumerInterceptors";
    private static final String CONSUMER_METHOD = "onConsume";
    private static final String CONSUMER_METHOD_DESC = "(Lorg/apache/kafka/clients/consumer/ConsumerRecords;)" +
            "Lorg/apache/kafka/clients/consumer/ConsumerRecords;";

    private final TraceContext traceContext;
    private static final String BEGIN_SRC;
    private static final String END_SRC;
    private static final String ERROR_SRC;

    public KafkaMqReceiveCollects(TraceContext context, Instrumentation instrumentation) {
        super(instrumentation);
        this.traceContext = context;
    }

    public KafkaMqReceiveCollects(TraceContext context) {
        super();
        this.traceContext = context;
    }

    static {
        BEGIN_SRC =
                KafkaMqReceiveCollects.class.getName()
                        + " instance = " + KafkaMqReceiveCollects.class.getName() + ".INSTANCE;\r\n" +
                KafkaMqReceiveTraceNodeWrapper.class.getName()
                        + " node = instance.begin($args);";
        END_SRC = "instance.end(node, $args);";
        ERROR_SRC = "instance.error(node, e);";
    }

    public KafkaMqReceiveTraceNodeWrapper begin(Object[] params) {
        if (params == null || params.length == 0 || params[0] == null) {
            return null;
        }
        InvocationConsumerAdapter invocationAdapter;
        try {
            invocationAdapter = new InvocationConsumerAdapter(params[0]);
        } catch (Throwable t) {
            logger.error("[Agent-EXCError]InvocationConsumerAdapter init failed. " + StackTraceFormatter.formatExceptionWithAgentMark(t));
            return null;
        }
        TraceRequest request;
        try {
            request = getTraceRequest(invocationAdapter);
        } catch (Throwable t) {
            logger.error("[Agent-EXCError]getTraceRequest failed. " + StackTraceFormatter.formatExceptionWithAgentMark(t));
            return null;
        }
        boolean localRoot = false;
        if (request == null) {
            request = createLocalRootRequest();
            localRoot = true;
        }
        TraceSession session;
        try {
            session = traceContext.openTraceSession(CONSUMER_CLASS, CONSUMER_METHOD, request);
        } catch (Throwable t) {
            logger.error("[Agent-EXCError]openTraceSession failed. " + StackTraceFormatter.formatExceptionWithAgentMark(t));
            return null;
        }
        if (session == null) {
            return null;
        }
        KafkaMQRemoteTraceNode node = new KafkaMQRemoteTraceNode();
        try {
            node.setTraceId(traceContext.getTraceSession().getTraceId());
            node.setTraceNodeId(localRoot ? "0" : traceContext.getTraceSession().getParentNodeId() + ".remote");
        } catch (Throwable t) {
            logger.error("[Agent-EXCError]set traceId/traceNodeId failed. " + StackTraceFormatter.formatExceptionWithAgentMark(t));
        }
        node.setBeginTime(System.currentTimeMillis());
        KafkaMqReceiveTraceNodeWrapper wrapper = new KafkaMqReceiveTraceNodeWrapper(session, node);
        if (AsyncCoverageSupport.enabled(traceContext)) {
            wrapper.coverageCollector = AsyncCoverageSupport.begin(wrapper);
        }
        return wrapper;
    }

    private TraceRequest getTraceRequest(InvocationConsumerAdapter invocationAdapter) {
        if (invocationAdapter == null) {
            return null;
        }
        String partitions;
        try {
            partitions = invocationAdapter.getPartitions();
        } catch (Throwable t) {
            logger.error("[Agent-EXCError]getPartitions failed. " + StackTraceFormatter.formatExceptionWithAgentMark(t));
            return null;
        }
        Object records;
        try {
            records = invocationAdapter.getRecords(partitions);
        } catch (Throwable t) {
            logger.error("[Agent-EXCError]getRecords failed. " + StackTraceFormatter.formatExceptionWithAgentMark(t));
            return null;
        }
        Map<String, String> headers;
        try {
            headers = invocationAdapter.getHeaders(records);
        } catch (Throwable t) {
            logger.error("[Agent-EXCError]getHeaders failed. " + StackTraceFormatter.formatExceptionWithAgentMark(t));
            return null;
        }
        if(headers == null || headers.isEmpty()) {
            return null;
        }
        String traceId = headers.get("_traceId");
        String parentNodeId = headers.get("_parentTraceNodeId");
        if (traceId != null && parentNodeId != null) {
            TraceRequest request = new TraceRequest();
            request.setTraceId(traceId);
            request.setParentNodeCallId(parentNodeId);
            return request;
        }
        return null;
    }

    private TraceRequest createLocalRootRequest() {
        TraceRequest request = new TraceRequest();
        request.setTraceId(traceContext.createTraceId());
        request.setParentNodeCallId("0");
        request.setProperties(new java.util.Properties());
        return request;
    }

    public void end(KafkaMqReceiveTraceNodeWrapper nodeWrapper, Object[] params) {
        if (nodeWrapper == null || params == null || params.length == 0 || params[0] == null) {
            return;
        }
        TraceSession session;
        try {
            session = traceContext.getTraceSession();
        } catch (Throwable t) {
            logger.error("[Agent-EXCError]getTraceSession failed. " + StackTraceFormatter.formatExceptionWithAgentMark(t));
            return;
        }
        if (session == null) {
            return;
        }
        boolean deferred = false;
        KafkaMQRemoteTraceNode node = nodeWrapper.node;
        try {
            node.setEndTime(System.currentTimeMillis());
            long useTime = node.getEndTime() - node.getBeginTime();
            node.setUseTime(useTime);
            if (node.getError() != null) {
                node.setStatus(TraceNode.Status.fail.toString());
            } else {
                node.setStatus(TraceNode.Status.succeed.toString());
            }
            InvocationConsumerAdapter invocationAdapter;
            try {
                invocationAdapter = new InvocationConsumerAdapter(params[0]);
            } catch (Throwable t) {
                logger.error("[Agent-EXCError]InvocationConsumerAdapter init failed in end. " + StackTraceFormatter.formatExceptionWithAgentMark(t));
                return;
            }
            String partitions;
            try {
                partitions = invocationAdapter.getPartitions();
            } catch (Throwable t) {
                logger.error("[Agent-EXCError]getPartitions failed in end. " + StackTraceFormatter.formatExceptionWithAgentMark(t));
                return;
            }
            Object records;
            try {
                records = invocationAdapter.getRecords(partitions);
            } catch (Throwable t) {
                logger.error("[Agent-EXCError]getRecords failed in end. " + StackTraceFormatter.formatExceptionWithAgentMark(t));
                return;
            }
            try {
                if (records instanceof Iterable) {
                    Iterable<?> iterable = (Iterable<?>) records;
                    Object first = iterable.iterator().hasNext() ? iterable.iterator().next() : null;
                    if (first != null) {
                        node.setConsumerRecords(first.toString());
                    }
                }
            } catch (Throwable t) {
                logger.error("[Agent-EXCError]setConsumerRecords failed. " + StackTraceFormatter.formatExceptionWithAgentMark(t));
            }
            if (nodeWrapper.coverageCollector != null && AgentContext.hasPendingAsyncTasks()) {
                deferred = true;
                nodeWrapper.markDeferred();
                AtomicInteger activeAsyncTaskCount = AgentContext.getActiveAsyncTaskCount();
                int pendingAsyncTasks = activeAsyncTaskCount == null ? 0 : activeAsyncTaskCount.get();
                nodeWrapper.detachCurrentThreadContext();
                logger.info("[Agent-info]KafkaMqReceiveCollects 检测到异步任务仍在执行，延迟覆盖率汇总，pendingAsyncTasks="
                        + pendingAsyncTasks + ", traceId=" + node.getTraceId());
            } else {
                nodeWrapper.collectCoverage();
                try {
                    traceContext.getTraceSession().saveNode(node);
                } catch (Throwable t) {
                    logger.error("[Agent-EXCError]saveNode failed. " + StackTraceFormatter.formatExceptionWithAgentMark(t));
                }
            }
        } finally {
            try {
                if (!deferred) {
                    nodeWrapper.doDestroy();
                }
            } catch (Throwable t) {
                logger.error("[Agent-EXCError]doDestroy failed. " + StackTraceFormatter.formatExceptionWithAgentMark(t));
            }
        }
    }

    public void error(KafkaMqReceiveTraceNodeWrapper nodeWrapper, Throwable e) {
        if (nodeWrapper == null || traceContext == null) {
            return;
        }
        try {
            if (traceContext.getTraceSession() == null) {
                return;
            }
        } catch (Throwable t) {
            logger.error("[Agent-EXCError]getTraceSession failed in error. " + StackTraceFormatter.formatExceptionWithAgentMark(t));
            return;
        }
        try {
            nodeWrapper.node.setError(buildError(e));
        } catch (Throwable t) {
            logger.error("[Agent-EXCError]setError failed. " + StackTraceFormatter.formatExceptionWithAgentMark(t));
        }
    }

    public class KafkaMqReceiveTraceNodeWrapper implements ISessionDestroy, AgentContext.AsyncCompletionListener {
        private final TraceSession session;
        private final KafkaMQRemoteTraceNode node;
        private CoverageCollector coverageCollector;
        private boolean deferred;
        private boolean destroyed;

        public KafkaMqReceiveTraceNodeWrapper(TraceSession session, KafkaMQRemoteTraceNode node) {
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
                logger.error("[Agent-EXCError]KafkaMqReceiveCollects 异步覆盖率补报失败. " + StackTraceFormatter.formatExceptionWithAgentMark(t));
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
            if (traceContext == null || session == null) {
                return;
            }
            try {
                detachCurrentThreadContext();
                traceContext.closeTraceSession(session);
            } catch (Throwable t) {
                logger.error("[Agent-EXCError]closeTraceSession failed. " + StackTraceFormatter.formatExceptionWithAgentMark(t));
            }
        }

        private void detachCurrentThreadContext() {
            AsyncCoverageSupport.detach(coverageCollector);
        }
    }

    @Override
    public byte[] transform(ClassLoader loader, String className, ProtectionDomain protectionDomain, byte[] classfileBuffer) {
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
            logger.info("[Agent-info]完成 KafkaMQReceive 采集器初始化.");
            return byteLoade.toByteCode();
        } catch (Throwable t) {
            logger.error("transform failed. " + StackTraceFormatter.formatExceptionWithAgentMark(t));
            return null;
        } finally {
            if (ctClass != null) {
                try {
                    ctClass.detach();
                } catch (Throwable ignore) {}
            }
        }
    }
}
