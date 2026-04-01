package com.oAT.agent.collect;

import com.oAT.agent.collect.sofaRPC.ResultAdapter;
import com.oAT.agent.collect.sofaRPC.SofaRequestAdapter;
import com.oAT.agent.common.StackTraceFormatter;
import com.oAT.agent.common.logger.Log;
import com.oAT.agent.common.logger.LogFactory;
import com.oAT.agent.jacoco.CoverageCollector;
import com.oAT.agent.jacoco.data.StackNodeVoBuilder;
import com.oAT.agent.model.SofaRpcRemoteTraceNode;
import com.oAT.agent.model.StackNodeVo;
import com.oAT.agent.model.StackNodeVo;
import com.oAT.agent.model.TraceNode;
import com.oAT.agent.trace.ISessionDestroy;
import com.oAT.agent.trace.TraceContext;
import com.oAT.agent.trace.TraceRequest;
import com.oAT.agent.trace.TraceSession;
import com.oAT.shaded.apache.commons.codec.binary.Base64;
import com.oAT.shaded.javassist.CtClass;
import com.oAT.shaded.javassist.CtMethod;
import com.oAT.shaded.javassist.NotFoundException;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.lang.instrument.Instrumentation;
import java.security.ProtectionDomain;
import java.util.Properties;

/**
 * 拦截目标：Sofa RPC Server端 ProviderInvoker
 * 服务提供者（Provider） - Server 端
 * 提供服务实现
 * 监听端口等待调用
 */
public class SofaServerCollect extends AbstractByteTransformCollect implements ICollect {
    private final static Log logger = LogFactory.getLog(SofaServerCollect.class);
    public static SofaServerCollect INSTANCE;
    private static final String TARGET_CLASS = "com.alipay.sofa.rpc.filter.ProviderInvoker";
    private static final String TARGET_METHOD = "invoke";
    private static final String BEGIN_SRC;
    private static final String END_SRC;
    private static final String ERROR_SRC;
    private final TraceContext traceContext;

    static {
        BEGIN_SRC = SofaServerCollect.class.getName() + " instance = " +
                SofaServerCollect.class.getName() + ".INSTANCE;\r\n" +
                SofaRpcRemoteTraceNodeWrapper.class.getName() + " node = instance.begin($args);";
        END_SRC = "instance.end(node, $args, _result);";
        ERROR_SRC = "instance.error(node, e);";
    }

    public SofaServerCollect(TraceContext traceContext, Instrumentation instrumentation) {
        super(instrumentation);
        this.traceContext = traceContext;
    }

    public SofaRpcRemoteTraceNodeWrapper begin(Object[] args) {
        if (traceContext == null) {
            return null;
        }
        try {
            SofaRequestAdapter sofaRequestAdapter = new SofaRequestAdapter(args[0]);
            TraceRequest request = getTraceRequest(sofaRequestAdapter);
            TraceSession traceSession = traceContext.getTraceSession();
            String traceId;
            String traceNodeId = "0";
            if (request != null && traceSession != null) {
                traceSession = traceContext.openTraceSession(TARGET_CLASS, TARGET_METHOD, request);
                traceId = traceSession.getTraceId();
                traceNodeId = traceSession.getParentNodeId() + ".remote";
            } else {
                request = new TraceRequest();
                request.setParentNodeCallId("0");
                traceId = traceContext.createTraceId();
                request.setTraceId(traceId);
                String userHeader = sofaRequestAdapter.getRequestProp("userheader");
                request.setUserHeader(userHeader);
                request.setProperties(new Properties());

                traceSession = traceContext.openTraceSession(TARGET_CLASS, TARGET_METHOD, request);
            }
            SofaRpcRemoteTraceNode node = new SofaRpcRemoteTraceNode();
            node.setTraceId(traceId);
            node.setTraceNodeId(traceNodeId);
            node.setBeginTime(System.currentTimeMillis());
            try {
                node.setTargetServiceUniqueName(sofaRequestAdapter.getTargetServiceUniqueName());
                node.setInterfaceName(sofaRequestAdapter.getInterfaceName());
                node.setMethodName(sofaRequestAdapter.getMethodName());
                node.setMethodArgs(sofaRequestAdapter.getMethodArgs());
            } catch (Throwable t) {
                logger.error("[Agent-EXCError]SofaRequestAdapter error: " + StackTraceFormatter.formatExceptionWithAgentMark(t));
            }
            // 创建代码堆栈采集会话
            SofaRpcRemoteTraceNodeWrapper wrapper = new SofaRpcRemoteTraceNodeWrapper(traceSession, node);

            // 仅在配置开启时采集代码堆栈
            if (traceContext.getConfig("codeStack.include") != null
                    || traceContext.getConfig("conf_codeStack.include") != null) {
                wrapper.coverageCollector = CoverageCollector.begin();
            }
            return wrapper;
        } catch (Throwable t) {
            logger.error("[Agent-EXCError]stackSession init error: " + StackTraceFormatter.formatExceptionWithAgentMark(t));
        }
        return null;
    }

    private TraceRequest getTraceRequest(SofaRequestAdapter sofaRequestAdapter) {
        try {
            String traceId = sofaRequestAdapter.getRequestProp("_traceId");
            String parentNodeId = sofaRequestAdapter.getRequestProp("_parentTraceNodeId");
            String properties = sofaRequestAdapter.getRequestProp("_traceProperties");
            String userHeader = sofaRequestAdapter.getRequestProp("_userheader");

            if (traceId != null && parentNodeId != null) {
                TraceRequest request = new TraceRequest();
                request.setTraceId(traceId);
                request.setParentNodeCallId(parentNodeId);
                if (properties != null) {
                    try {
                        Properties p = new Properties();
                        p.load(new ByteArrayInputStream(Base64.decodeBase64(properties)));
                        request.setProperties(p);
                    } catch (IOException e) {
                        logger.error("[Agent-EXCError]error. " + StackTraceFormatter.formatExceptionWithAgentMark(e));
                    }
                }
                // 透传 header
                request.setUserHeader(userHeader);
                return request;
            }
            return null;
        } catch (Throwable t) {
            logger.error("[Agent-EXCError]getTraceRequest error: " + StackTraceFormatter.formatExceptionWithAgentMark(t));
            return null;
        }
    }

    public void end(SofaRpcRemoteTraceNodeWrapper nodeWrapper, Object[] parames, Object result) {
        try {
            TraceSession traceSession = traceContext.getTraceSession();
            if (nodeWrapper == null || traceSession == null) {
                return;
            }
            SofaRpcRemoteTraceNode node = nodeWrapper.node;

            node.setEndTime(System.currentTimeMillis());
            long userTime = node.getEndTime() - node.getBeginTime();
            node.setUseTime(userTime);
            if (result != null) {
                ResultAdapter adapter = new ResultAdapter(result);
                if (adapter.getErrorMsg() != null) {
                    error(nodeWrapper, adapter.getErrorMsg());
                }
            }
            if (node.getError() != null) {
                node.setStatus(TraceNode.Status.fail.toString());
            } else {
                node.setStatus(TraceNode.Status.succeed.toString());
            }
            // 采集代码堆栈
            try {
                if (nodeWrapper.coverageCollector != null) {
                    nodeWrapper.coverageCollector = CoverageCollector.end();
                    if (nodeWrapper.coverageCollector != null && !nodeWrapper.coverageCollector.getProbeSnapshots().isEmpty()) {
                        try {
                            StackNodeVo[] codeNodes = new StackNodeVoBuilder()
                                    .buildCodeNodes(nodeWrapper.coverageCollector);
                            node.setCodeNodes(codeNodes);
                        } catch (Throwable t) {
                            logger.error("[Agent-EXCError]buildCodeNodes 异常: " + StackTraceFormatter.formatExceptionWithAgentMark(t));
                        }
                    }
                }
            } catch (Throwable t) {
                logger.error("[Agent-EXCError]coverageCollector end error: " + StackTraceFormatter.formatExceptionWithAgentMark(t));
            }
            if (node.getCodeNodes() != null && node.getCodeNodes().length > 0) {
                traceSession.saveNode(node);
            }
        } catch (Throwable t) {
            logger.error("[Agent-EXCError]end failed: " + StackTraceFormatter.formatExceptionWithAgentMark(t));
        } finally {
            //关闭会话
            if (nodeWrapper != null && "0".equals(nodeWrapper.getSofaRpcRemoteTraceNode().getTraceNodeId())) {
                nodeWrapper.doDestroy();
            }
        }
    }

    public void error(SofaRpcRemoteTraceNodeWrapper nodeWrapper, Throwable e) {
        try {
            if (nodeWrapper == null || traceContext.getTraceSession() == null) {
                return;
            }
            nodeWrapper.node.setError(buildError(e));
        } catch (Throwable t) {
            logger.error("[Agent-EXCError]setError failed: " + StackTraceFormatter.formatExceptionWithAgentMark(t));
        }
    }

    public class SofaRpcRemoteTraceNodeWrapper implements ISessionDestroy {
        private final TraceSession session;
        private final SofaRpcRemoteTraceNode node;
        // 覆盖率数据聚合器
        private CoverageCollector coverageCollector;

        public SofaRpcRemoteTraceNodeWrapper(TraceSession session, SofaRpcRemoteTraceNode node) {
            this.session = session;
            this.node = node;
        }

        @Override
        public void doDestroy() {
            try {
                if (coverageCollector != null) {
                    CoverageCollector.remove();
                }
                traceContext.closeTraceSession(session);
            } catch (Throwable t) {
                logger.error("[Agent-EXCError]doDestroy error: " + StackTraceFormatter.formatExceptionWithAgentMark(t));
            }
        }

        public SofaRpcRemoteTraceNode getSofaRpcRemoteTraceNode() {
            return node;
        }
    }

    @Override
    public byte[] transform(ClassLoader loader, String className, ProtectionDomain protectionDomain, byte[] classfileBuffer) {
        if (!className.equals(TARGET_CLASS)) {
            return null;
        }
        getJarAndVersion(protectionDomain);
        CtClass ctClass = null;
        try {
            // 字节码转换过程异常捕获
            ctClass = AgentByteBuild.toCtClass(loader, className, classfileBuffer);
            if (ctClass == null) {
                logger.warn("[Agent-warn]CtClass is null for: " + className);
                return null;
            }
            AgentByteBuild byteLoade = new AgentByteBuild(className, loader, ctClass);
            // 方法存在性校验
            CtMethod m;
            try {
                m = ctClass.getDeclaredMethod(TARGET_METHOD);
            } catch (NotFoundException e) {
                logger.error("[Agent-EXCError]Method not found: " + TARGET_METHOD + " in " + className + StackTraceFormatter.formatExceptionWithAgentMark(e));
                return null;
            }
            AgentByteBuild.MethodSrcBuild build = new AgentByteBuild.MethodSrcBuild();
            build.setBeginSrc(BEGIN_SRC);
            build.setErrorSrc(ERROR_SRC);
            build.setEndSrc(END_SRC);
            byteLoade.updateMethod(m, build);
            logger.info("[Agent-info]完成 SofaServer 采集器初始化.");
            return byteLoade.toByteCode();
        } catch (Throwable t) {
            // 全局异常捕获，防止影响主流程
            logger.error("[Agent-EXCError]字节码增强失败: " + className + " "
                    + StackTraceFormatter.formatExceptionWithAgentMark(t));
            return null;
        } finally {
            // 资源释放
            if (ctClass != null) {
                try {
                    ctClass.detach();
                } catch (Throwable ignore) {
                    // ignore
                }
            }
        }
    }
}
