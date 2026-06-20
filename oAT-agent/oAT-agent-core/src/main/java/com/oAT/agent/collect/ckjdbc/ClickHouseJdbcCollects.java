package com.oAT.agent.collect.ckjdbc;

import com.oAT.agent.collect.AbstractByteTransformCollect;
import com.oAT.agent.collect.AgentByteBuild;
import com.oAT.agent.collect.ICollect;
import com.oAT.agent.common.StackTraceFormatter;
import com.oAT.agent.common.logger.Log;
import com.oAT.agent.common.logger.LogFactory;
import com.oAT.agent.model.CKSqlTraceNode;
import com.oAT.agent.model.TraceNode;
import com.oAT.agent.trace.TraceContext;
import com.oAT.agent.trace.TraceSession;
import com.oAT.shaded.javassist.CtClass;
import com.oAT.shaded.javassist.CtMethod;

import java.lang.instrument.Instrumentation;
import java.security.ProtectionDomain;
import java.sql.Connection;
import java.util.Arrays;
import java.util.List;

public class ClickHouseJdbcCollects extends AbstractByteTransformCollect implements ICollect {
    private final static Log logger = LogFactory.getLog(ClickHouseJdbcCollects.class);
    public static ClickHouseJdbcCollects INSTANCE;
    private TraceContext traceContext;
    private List<String> ckjdbcDrivers;

    public ClickHouseJdbcCollects(TraceContext tcontext, Instrumentation instrumentation, String... ckjdbcDrivers) {
        super(instrumentation);
        INSTANCE = this;
        this.traceContext = tcontext;
        if (ckjdbcDrivers != null) {
            this.ckjdbcDrivers = Arrays.asList(ckjdbcDrivers);
        } else {
            this.ckjdbcDrivers = java.util.Collections.emptyList();
        }
    }

    public ClickHouseJdbcCollects(TraceContext tcontext) {
        super();
        INSTANCE = this;
        this.traceContext = tcontext;
        this.ckjdbcDrivers = java.util.Collections.emptyList();
    }

    private static final String beginSrc;
    private static final String endSrc;
    private static final String errorSrc;

    static {
        beginSrc = ClickHouseJdbcCollects.class.getName() + " instance = " +
                ClickHouseJdbcCollects.class.getName() + ".INSTANCE;\r\n" +
                CKSqlTraceNode.class.getName() + " node = instance.begin((java.sql.Connection)connection, $args);";
        endSrc = "instance.end(node);";
        errorSrc = "instance.error(node, e);";
    }

    public CKSqlTraceNode begin(Connection connection, Object[] args) {
        CKSqlTraceNode node = new CKSqlTraceNode();

        TraceSession session = traceContext != null ? traceContext.getTraceSession() : null;
        if (session == null || connection == null || args == null || args.length == 0 || args[0] == null) {
            return null;
        }

        try {
            String url = null;
            try {
                if (connection != null && connection.createStatement() != null &&
                    connection.createStatement().getConnection() != null &&
                    connection.createStatement().getConnection().getMetaData() != null) {
                    url = connection.createStatement().getConnection().getMetaData().getURL();
                }
            } catch (Throwable t) {
                // swallow, url remains null
            }
            node.setJdbcUrl(url);
            node.setSql(args[0].toString());
        } catch (Throwable e) {
            node.setError(buildError(e));
        }
        node.setBeginTime(System.currentTimeMillis());
        node.setTraceId(session.getTraceId());
        node.setTraceNodeId(session.getNextNodeId());
        return node;
    }

    public void error(CKSqlTraceNode stat, Throwable throwable) {
        TraceSession session = traceContext != null ? traceContext.getTraceSession() : null;
        if (stat == null || session == null || throwable == null) {
            return;
        }
        try {
            stat.setError(buildError(throwable));
        } catch (Throwable t) {
            // swallow
        }
    }

    public void end(CKSqlTraceNode node) {
        TraceSession session = traceContext != null ? traceContext.getTraceSession() : null;
        if (node == null || session == null) {
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
            session.saveNode(node);
        } catch (Throwable t) {
            // swallow
        }
    }

    @Override
    public byte[] transform(ClassLoader loader, String className, ProtectionDomain protectionDomain, byte[] classfileBuffer) {
        if (className == null || ckjdbcDrivers == null || !ckjdbcDrivers.contains(className)) {
            return null;
        }
        getJarAndVersion(protectionDomain);
        CtClass ctClass = null;
        try {
            ctClass = AgentByteBuild.toCtClass(loader, className, classfileBuffer);
            if (ctClass == null) {
                return null;
            }
            AgentByteBuild byteLoade = new AgentByteBuild(className, loader, ctClass);
            CtMethod ctMethod;
            try {
                ctMethod = ctClass.getMethod("executeQuery", "(Ljava/lang/String;)Ljava/sql/ResultSet;");
            } catch (Throwable t) {
                logger.error("[Agent-EXCError]未找到executeQuery方法: " + t.getMessage());
                return null;
            }
            AgentByteBuild.MethodSrcBuild build = new AgentByteBuild.MethodSrcBuild();
            build.setBeginSrc(beginSrc);
            build.setErrorSrc(errorSrc);
            build.setEndSrc(endSrc);
            byteLoade.updateMethod(ctMethod, build);
            logger.info("[Agent-info]完成ClickHouse初始化采集器.");
            return byteLoade.toByteCode();
        } catch (Throwable t) {
            logger.error("[Agent-EXCError]ClickHouse字节码增强失败: " + StackTraceFormatter.formatExceptionWithAgentMark(t));
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
