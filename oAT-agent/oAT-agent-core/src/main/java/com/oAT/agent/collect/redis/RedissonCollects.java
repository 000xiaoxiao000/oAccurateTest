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
import com.oAT.shaded.javassist.*;

import java.lang.instrument.Instrumentation;
import java.security.ProtectionDomain;
import java.util.Arrays;
import java.util.List;

public class RedissonCollects extends AbstractByteTransformCollect implements ICollect {
    private static final Log logger = LogFactory.getLog(RedissonCollects.class);

    public static RedissonCollects INSTANCE;

    private static final String TARGET_CLASS = "org.redisson.command.RedisExecutor";
    private static final String TARGET_METHOD_DESC = "(ZLorg/redisson/connection/NodeSource;" +
            "Lorg/redisson/client/codec/Codec;Lorg/redisson/client/protocol/RedisCommand;[Ljava/lang/Object;" +
            "Ljava/util/concurrent/CompletableFuture;ZLorg/redisson/connection/ConnectionManager;" +
            "Lorg/redisson/liveobject/core/RedissonObjectBuilder;" +
            "Lorg/redisson/liveobject/core/RedissonObjectBuilder$ReferenceType;Z)V";

    private final TraceContext traceContext;
    private final List<String> redisClient;

    private RedisTraceNode currentRedisInfo;

    public RedissonCollects(TraceContext tcontext, Instrumentation instrumentation, String... redisClient) {
        super(instrumentation);
        INSTANCE = this;
        this.traceContext = tcontext;
        this.redisClient = Arrays.asList(redisClient);
    }

    public TraceContext getTraceContext() {
        return traceContext;
    }

    public RedisTraceNode getCurrentRedisInfo() {
        return this.currentRedisInfo;
    }

    // 采集开始
    public void begin(Object nodeSource, Object codec, Object redisCommand, Object[] args, Object connectionManager) {
        try {
            RedisTraceNode info = new RedisTraceNode();
            info.setBeginTime(System.currentTimeMillis());
            // 命令类型
            if (redisCommand != null) {
                RedissonCommandAdapter redissonCommandAdapter = new RedissonCommandAdapter(redisCommand);
                info.setType(redissonCommandAdapter.getName());
            }
            // 命令内容
            if (args != null && args.length > 0 && args[0] != null) {
                info.setCmd(args[0].toString());
            }
            // host/port
            if (connectionManager != null) {
                RedissonAdapter redissonAdapter = new RedissonAdapter(connectionManager);
                String[] arr = redissonAdapter.getAddress();
                if (arr != null && arr.length > 0 && arr[0] != null) {
                    String[] addressArr = arr[0].replace("redis://", "").split(":");
                    if (addressArr.length == 2) {
                        info.setHost(addressArr[0]);
                        info.setPort(addressArr[1]);
                    }
                }
            }
            this.currentRedisInfo = info;
        } catch (Throwable t) {
            logger.error("[Agent-EXCError]begin异常: " + StackTraceFormatter.formatExceptionWithAgentMark(t));
            this.currentRedisInfo = null;
        }
    }

    // 采集结束
    public void end(Object redisCommand, Object[] args) {
        try {
            RedisTraceNode info = this.currentRedisInfo;
            if (info == null) {
                return;
            }
            // 命令类型
            if (redisCommand != null) {
                RedissonCommandAdapter redissonCommandAdapter = new RedissonCommandAdapter(redisCommand);
                info.setType(redissonCommandAdapter.getName());
            }
            // 命令内容
            if (args != null && args.length > 0 && args[0] != null) {
                info.setCmd(args[0].toString());
            }
            info.setEndTime(System.currentTimeMillis());
            long useTime = info.getEndTime() - info.getBeginTime();
            useTime = Math.max(useTime, 0);
            info.setUseTime(useTime);

            TraceSession session = traceContext != null ? traceContext.getTraceSession() : null;
            if (session == null) {
                return;
            }
            if (info.getTraceNodeId() != null
                    && info.getTraceId() != null
                    && !info.getTraceId().isEmpty()) {
                info.setTraceId(session.getTraceId());
                info.setTraceNodeId(session.getNextNodeId());
                session.saveNode(info);
            }
        } catch (Throwable t) {
            logger.error("[Agent-EXCError]end异常: " + StackTraceFormatter.formatExceptionWithAgentMark(t));
        } finally {
            this.currentRedisInfo = null;
        }
    }

    // 采集异常
    public void error(Throwable e) {
        try {
            RedisTraceNode info = this.currentRedisInfo;
            if (info != null) {
                info.setError(buildError(e));
                info.setEndTime(System.currentTimeMillis());
                TraceSession session = traceContext != null ? traceContext.getTraceSession() : null;
                if (session != null) {
                    session.saveNode(info);
                }
            }
        } catch (Throwable t) {
            logger.error("[Agent-EXCError]error异常: " + StackTraceFormatter.formatExceptionWithAgentMark(t));
        } finally {
            this.currentRedisInfo = null;
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
                        logger.warn("[Agent-warn]未找到目标类: " + className);
                        return null;
                    }
                    CtConstructor[] constructors = ctClass.getDeclaredConstructors();
                    for (CtConstructor constructor : constructors) {
                        if (constructor.getMethodInfo().getDescriptor().equals(TARGET_METHOD_DESC)) {
                            // 构造插桩代码
                            String beginSrc = RedissonCollects.class.getName() + " instance = " +
                                    RedissonCollects.class.getName() + ".INSTANCE;\r\n" +
                                    "instance.begin($2, $3, $4, $5, $8);\r\n";
                            String endSrc = RedissonCollects.class.getName() + " instance = " +
                                    RedissonCollects.class.getName() + ".INSTANCE;\r\n" +
                                    "if (instance.getCurrentRedisInfo() != null && instance.getTraceContext().getTraceSession() != null) {\r\n" +
                                    "    instance.end($4, $5);\r\n" +
                                    "}";
                            String errorSrc = RedissonCollects.class.getName() + " instance = " +
                                    RedissonCollects.class.getName() + ".INSTANCE;\r\n" +
                                    "if (instance.getCurrentRedisInfo() != null && instance.getTraceContext().getTraceSession() != null) {\r\n" +
                                    "    instance.error($e);\r\n" +
                                    "}";
                            constructor.insertBefore(beginSrc);
                            constructor.insertAfter(endSrc, true);
                            CtClass throwableType = ClassPool.getDefault().get("java.lang.Throwable");
                            constructor.addCatch(
                                    "{ " + errorSrc + "; throw $e; }",
                                    throwableType
                            );
                        }
                    }
                    logger.info("[Agent-info]完成 Redisson 采集器初始化.");
                    return ctClass.toBytecode();
                } finally {
                    if (ctClass != null) {
                        try {
                            ctClass.detach();
                        } catch (Throwable ignore) {}
                    }
                }
            }
        } catch (Throwable t) {
            logger.error("[Agent-EXCError]transform异常: " + StackTraceFormatter.formatExceptionWithAgentMark(t));
            return null;
        }
        return null;
    }
}
