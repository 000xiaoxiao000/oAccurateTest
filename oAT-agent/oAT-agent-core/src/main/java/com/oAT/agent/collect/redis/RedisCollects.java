package com.oAT.agent.collect.redis;

import com.oAT.agent.collect.AbstractByteTransformCollect;
import com.oAT.agent.collect.AgentByteBuild;
import com.oAT.agent.collect.ICollect;
import com.oAT.agent.common.StackTraceFormatter;
import com.oAT.agent.common.logger.Log;
import com.oAT.agent.common.logger.LogFactory;
import com.oAT.agent.model.RedisTraceNode;
import com.oAT.agent.trace.TraceContext;
import com.oAT.agent.trace.TraceSession;
import com.oAT.shaded.javassist.CtClass;
import com.oAT.shaded.javassist.CtMethod;

import java.lang.instrument.Instrumentation;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.security.ProtectionDomain;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public class RedisCollects extends AbstractByteTransformCollect implements ICollect {
    private static final Log logger = LogFactory.getLog(RedisCollects.class);
    private static final int MAX_CMD_LENGTH = 1024; // 限制命令最大长度

    // 性能统计
    private static final ConcurrentHashMap<String, AtomicLong> COMMAND_STATS = new ConcurrentHashMap<>();
    private static final AtomicLong TOTAL_COMMANDS = new AtomicLong(0);

    public static RedisCollects INSTANCE;

    private static final String TARGET_CLASS = "io.lettuce.core.protocol.DefaultEndpoint";
    private static final String TARGET_METHOD = "write";
    private static final String TARGET_METHOD_DESC = "(Lio/lettuce/core/protocol/RedisCommand;)Lio/lettuce/core/protocol/RedisCommand;";

    private static final String BEGIN_SRC;
    private static final String END_SRC;
    private static final String ERROR_SRC;

    private final TraceContext traceContext;
    private final List<String> redisClient;

    private RedisTraceNode currentRedisInfo;

    static {
        BEGIN_SRC = RedisCollects.class.getName() + " instance = " +
                RedisCollects.class.getName() + ".INSTANCE;\r\n" +
                "String remote = $0.channel.remoteAddress().toString();\r\n" +
                "String host = remote;\r\n" +
                "String port = null;\r\n" +
                "int idx1 = remote.lastIndexOf(\":\");\r\n" +
                "if (idx1 > 0) {\r\n" +
                "    host = remote.substring(0, idx1);\r\n" +
                "    port = remote.substring(idx1 + 1);\r\n" +
                "    if (host.startsWith(\"/\")) host = host.substring(1);\r\n" +
                "}\r\n" +
                "instance.begin(host, port);\r\n";

        END_SRC = RedisCollects.class.getName() + " instance = " +
                RedisCollects.class.getName() + ".INSTANCE;\r\n" +
                "if (instance.getCurrentRedisInfo() != null && instance.getTraceContext().getTraceSession() != null) {\r\n" +
                "    instance.end($1.getType().toString(), $1.getArgs().toCommandString());\r\n" +
                "}";

        ERROR_SRC = RedisCollects.class.getName() + " instance = " +
                RedisCollects.class.getName() + ".INSTANCE;\r\n" +
                "if (instance.getCurrentRedisInfo() != null && instance.getTraceContext().getTraceSession() != null) {\r\n" +
                "    instance.error(e);\r\n" +
                "}";
    }

    public RedisCollects(TraceContext tcontext, Instrumentation instrumentation, String... redisClient) {
        super(instrumentation);
        INSTANCE = this;
        this.traceContext = tcontext;
        this.redisClient = Arrays.asList(redisClient);
    }

    public TraceContext getTraceContext() {
        return traceContext;
    }

    // 供字节码插入调用
    public RedisTraceNode getCurrentRedisInfo() {
        return this.currentRedisInfo;
    }

    public void begin(String host, String port) {
        if (host == null || port == null) {
            logger.warn("[Agent-begin] host or port is null");
            return;
        }
        RedisTraceNode info = new RedisTraceNode();
        info.setHost(host);
        info.setPort(port);
        info.setBeginTime(System.currentTimeMillis());
        this.currentRedisInfo = info;
    }

    public void end(String type, String cmd) {
        try {
            RedisTraceNode currentInfo = this.currentRedisInfo;
            if (currentInfo == null) {
                logger.debug("[Agent-debug] currentInfo is null, skip end.");
                return;
            }
            if (type == null) {
                type = "UNKNOWN";
            }
            currentInfo.setType(type);

            String safeCmd = sanitizeCommand(cmd);
            currentInfo.setCmd(safeCmd);

            currentInfo.setEndTime(System.currentTimeMillis());
            long useTime = currentInfo.getEndTime() - currentInfo.getBeginTime();
            useTime = Math.max(useTime, 0);
            currentInfo.setUseTime(useTime);

            TraceSession session = traceContext != null ? traceContext.getTraceSession() : null;
            if (session == null) {
                return;
            }
            if (currentInfo.getTraceNodeId() != null
                    && currentInfo.getTraceId() != null
                    && !currentInfo.getTraceId().isEmpty()) {
                currentInfo.setTraceId(session.getTraceId());
                currentInfo.setTraceNodeId(session.getNextNodeId());
                session.saveNode(currentInfo);

                updateCommandStats(type, useTime);
            }
        } catch (Throwable t) {
            logger.error("[Agent-EXCError]end failed. " + StackTraceFormatter.formatExceptionWithAgentMark(t));
        } finally {
            this.currentRedisInfo = null;
        }
    }

    public void error(Throwable e) {
        try {
            RedisTraceNode currentInfo = this.currentRedisInfo;
            if (currentInfo != null) {
                currentInfo.setError(buildError(e));
                currentInfo.setEndTime(System.currentTimeMillis());

                TraceSession session = traceContext.getTraceSession();
                if (session != null) {
                    session.saveNode(currentInfo);
                }
            }
        } catch (Throwable t) {
            logger.error("[Agent-EXCError]error failed. " + StackTraceFormatter.formatExceptionWithAgentMark(t));
        } finally {
            this.currentRedisInfo = null;
        }
    }

    private String sanitizeCommand(String cmd) {
        if (cmd == null) {
            return "";
        }
        try {
            if (cmd.length() > MAX_CMD_LENGTH) {
                cmd = cmd.substring(0, MAX_CMD_LENGTH) + "...[truncated]";
            }
            return new String(cmd.getBytes(Charset.defaultCharset()), StandardCharsets.UTF_8);
        } catch (Throwable e) {
            logger.warn("[Agent-EXCError]Failed to convert command string encoding" + StackTraceFormatter.formatExceptionWithAgentMark(e));
            return "[unreadable-cmd]";
        }
    }

    private void updateCommandStats(String type, long useTime) {
        if (type == null) {
            type = "UNKNOWN";
        }
        try {
            TOTAL_COMMANDS.incrementAndGet();
            AtomicLong v = COMMAND_STATS.get(type);
            if (v == null) {
                v = new AtomicLong(0);
                AtomicLong old = COMMAND_STATS.putIfAbsent(type, v);
                if (old != null) {
                    v = old;
                }
            }
            v.addAndGet(useTime);
        } catch (Throwable t) {
            logger.warn("[Agent-EXCError]updating stats failed. " + StackTraceFormatter.formatExceptionWithAgentMark(t));
        }
    }

    @Override
    public byte[] transform(ClassLoader loader, String className, ProtectionDomain protectionDomain, byte[] classfileBuffer) {
        if (!redisClient.contains(className)) {
            return null;
        }
        getJarAndVersion(protectionDomain);
        try {
            if (TARGET_CLASS.equals(className)) {
                CtClass ctClass = null;
                try {
                    ctClass = AgentByteBuild.toCtClass(loader, className, classfileBuffer);
                    if (ctClass == null) {
                        logger.warn("[Agent-transform] CtClass not found for " + className);
                        return null;
                    }
                    AgentByteBuild byteLoade = new AgentByteBuild(className, loader, ctClass);
                    CtMethod writeMethod = null;
                    try {
                        writeMethod = ctClass.getMethod(TARGET_METHOD, TARGET_METHOD_DESC);
                    } catch (Throwable ex) {
                        logger.error("[Agent-EXCError]Method not found: " + TARGET_METHOD, ex);
                    }
                    if (writeMethod == null) {
                        return null;
                    }
                    AgentByteBuild.MethodSrcBuild build = new AgentByteBuild.MethodSrcBuild();
                    build.setBeginSrc(BEGIN_SRC);
                    build.setErrorSrc(ERROR_SRC);
                    build.setEndSrc(END_SRC);
                    byteLoade.updateMethod(writeMethod, build);
                    logger.info("[Agent-info]完成 Redis 采集器初始化.");
                    return ctClass.toBytecode();
                } finally {
                    if (ctClass != null) {
                        try {
                            ctClass.detach();
                        } catch (Throwable ignore) {}
                    }
                }
            }
        } catch (Throwable e) {
            logger.error("[Agent-EXCError]Failed to transform class: " + className + StackTraceFormatter.formatExceptionWithAgentMark(e));
            return null;
        }
        return null;
    }
}
