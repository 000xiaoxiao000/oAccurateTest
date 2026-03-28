package com.oAT.agent.collect;

import com.oAT.agent.collect.sofaRPC.ResultAdapter;
import com.oAT.agent.collect.sofaRPC.SofaRequestAdapter;
import com.oAT.agent.common.JsonUtil;
import com.oAT.agent.common.StackTraceFormatter;
import com.oAT.agent.common.logger.Log;
import com.oAT.agent.common.logger.LogFactory;
import com.oAT.agent.model.SofaRpcTraceNode;
import com.oAT.agent.model.TraceNode;
import com.oAT.agent.trace.TraceContext;
import com.oAT.agent.trace.TraceSession;
import com.oAT.shaded.apache.commons.codec.binary.Base64;
import com.oAT.shaded.javassist.*;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.instrument.Instrumentation;
import java.security.ProtectionDomain;
import java.util.Properties;
import java.util.logging.Level;

/**
 * 拦截目标：Sofa RPC Client端 ConsumerInvoker
 * 服务消费者（Consumer） - Client 端
 * 调用远程服务
 * 发起 RPC 请求
 */
public class SofaClientCollect extends AbstractByteTransformCollect implements ICollect {
    private final static Log logger = LogFactory.getLog(SofaClientCollect.class);
    public static SofaClientCollect INSTANCE;
    private static final String TARGET_CLASS = "com.alipay.sofa.rpc.filter.ConsumerInvoker";
    private static final String TARGET_METHOD = "invoke";
    private static final String BEGIN_SRC;
    private static final String END_SRC;
    private static final String ERROR_SRC;
    private final TraceContext traceContext;

    static {
        BEGIN_SRC = SofaClientCollect.class.getName() + " instance = " +
                SofaClientCollect.class.getName() + ".INSTANCE;\r\n" +
                SofaRpcTraceNode.class.getName() + " node = instance.begin($args, $0);";
        END_SRC = "instance.end(node, $args, _result);";
        ERROR_SRC = "instance.error(node, e);";
    }

    public SofaClientCollect(TraceContext traceContext, Instrumentation instrumentation) {
        super(instrumentation);
        INSTANCE = this;
        this.traceContext = traceContext;
    }

    public SofaRpcTraceNode begin(Object[] args, Object targetObj) {
        if (traceContext == null || traceContext.getTraceSession() == null) {
            return null;
        }
        if (args == null || args.length == 0 || args[0] == null) {
            return null;
        }
        TraceSession traceSession = traceContext.getTraceSession();
        SofaRpcTraceNode node = new SofaRpcTraceNode();

        try {
            SofaRequestAdapter sofaRequestAdapter = new SofaRequestAdapter(args[0]);
            String traceNodeId = traceSession.getNextNodeId();

            //ConsumerConfigAdapter consumerConfigAdapter = new ConsumerConfigAdapter(targetObj);   // TODO待注释
            //node.setDirectUrl(consumerConfigAdapter.getDirectUrl());    // TODO待注释
            node.setInterfaceName(sofaRequestAdapter.getInterfaceName());
            node.setMethodName(sofaRequestAdapter.getMethodName());
            node.setInvokeType(sofaRequestAdapter.getInvokeType());
            node.setTargetServiceUniqueName(sofaRequestAdapter.getTargetServiceUniqueName());
            if (sofaRequestAdapter.getMethodArgs() != null) {
                node.setInParam(JsonUtil.toJson(sofaRequestAdapter.getMethodArgs()));
            }
            node.setTraceId(traceSession.getTraceId());
            node.setTraceNodeId(traceNodeId);
            setAttachments(sofaRequestAdapter, traceSession);
            node.setBeginTime(System.currentTimeMillis());

            return node;
        } catch (Throwable t) {
            logger.error("[Agent-EXCError]begin failed: " + StackTraceFormatter.formatExceptionWithAgentMark(t));
        }
        return null;
    }

    //设置隐示参数，上下游传递RPC节点信息
    private void setAttachments(SofaRequestAdapter adapter, TraceSession session) {
        if (adapter == null || session == null || session.getTraceRequest() == null) {
            logger.warn("[Agent-warn]SofaClientCollect setAttachments: adapter or session is null, skip.");
            return;
        }
        try {
            adapter.addRequestProp("_traceId", session.getTraceId());
            adapter.addRequestProp("_parentTraceNodeId", session.getCurrentNodeId());
            adapter.addRequestProp("_traceProperties", converProperties(session.getTraceRequest().getProperties()));
            adapter.addRequestProp("_userheader", session.getTraceRequest().getUserHeader());
        } catch (Throwable t) {
            logger.error("[Agent-EXCError]setAttachments failed: " + StackTraceFormatter.formatExceptionWithAgentMark(t));
        }
    }

    private String converProperties(Properties properties) {
        if (properties == null) {
            return null;
        }
        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        try {
            properties.store(stream, "");
        } catch (IOException e) {
            logger.error("[Agent-EXCError]" + Level.SEVERE + "properties " +
                    "encode fail" + StackTraceFormatter.formatExceptionWithAgentMark(e));
        } catch (Throwable t) {
            logger.error("[Agent-EXCError]converProperties failed: "
                    + StackTraceFormatter.formatExceptionWithAgentMark(t));
        }
        return Base64.encodeBase64String(stream.toByteArray());
    }

    public void end(SofaRpcTraceNode node, Object[] params, Object result) {
        if (node == null || traceContext == null || traceContext.getTraceSession() == null) {
            return;
        }
        try {
            TraceSession session = traceContext.getTraceSession();
            node.setEndTime(System.currentTimeMillis());
            long userTime = node.getEndTime() - node.getBeginTime();
            node.setUseTime(userTime);
            if (result != null) {
                ResultAdapter adapter = new ResultAdapter(result);
                if (adapter.getErrorMsg() != null) {
                    error(node, adapter.getErrorMsg());
                }
            }
            if (node.getError() != null) {
                node.setStatus(TraceNode.Status.fail.toString());
            } else {
                node.setStatus(TraceNode.Status.succeed.toString());
            }
            session.saveNode(node);
        } catch (Throwable t) {
            logger.error("[Agent-EXCError]end failed: "
                    + StackTraceFormatter.formatExceptionWithAgentMark(t));
        }
    }

    public void error(SofaRpcTraceNode node, Throwable e) {
        if (node == null || traceContext == null || traceContext.getTraceSession() == null) {
            return;
        }
        try {
            node.setError(buildError(e));
        } catch (Throwable t) {
            logger.error("[Agent-EXCError]setError failed: "
                    + StackTraceFormatter.formatExceptionWithAgentMark(t));
        }
    }

    @Override
    public byte[] transform(ClassLoader loader, String className, ProtectionDomain protectionDomain, byte[] classfileBuffer) {
        if (className == null || !className.equals(TARGET_CLASS)) {
            return null;
        }
        getJarAndVersion(protectionDomain);
        CtClass ctClass = null;
        try {
            ctClass = AgentByteBuild.toCtClass(loader, className, classfileBuffer);
            if (ctClass == null) {
                logger.warn("[Agent-warn]SofaClientCollect transform: CtClass is null, skip transform.");
                return null;
            }
            CtMethod invokeMethod;
            try {
                invokeMethod = ctClass.getDeclaredMethod(TARGET_METHOD);
            } catch (NotFoundException e) {
                logger.error("[Agent-EXCError]transform: method '" + TARGET_METHOD + "' not " +
                        "found, skip transform.");
                return null;
            }
            AgentByteBuild byteLoade = new AgentByteBuild(className, loader, ctClass);

            AgentByteBuild.MethodSrcBuild invokeBuild = new AgentByteBuild.MethodSrcBuild();
            invokeBuild.setBeginSrc(BEGIN_SRC);
            invokeBuild.setErrorSrc(ERROR_SRC);
            invokeBuild.setEndSrc(END_SRC);
            byteLoade.updateMethod(invokeMethod, invokeBuild);

            logger.info("[Agent-info]完成 SofaClient 采集器初始化.");
            return byteLoade.toByteCode();
        } catch (Throwable t) {
            logger.error("[Agent-EXCError]transform failed: "
                    + StackTraceFormatter.formatExceptionWithAgentMark(t));
            return null;
        } finally {
            if (ctClass != null) {
                try {
                    ctClass.detach();
                } catch (Throwable e) {
                    logger.error("[Agent-EXCError]transform: CtClass detach failed. "
                            + StackTraceFormatter.formatExceptionWithAgentMark(e));
                }
            }
        }
    }
}
