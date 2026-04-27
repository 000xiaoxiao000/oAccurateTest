package com.oAT.agent.trace;

import com.oAT.agent.collect.*;
import com.oAT.agent.collect.ckjdbc.ClickHouseJdbcCollects;
import com.oAT.agent.collect.jdbc.JdbcCommonCollects;
import com.oAT.agent.collect.redis.RedisCollects;
import com.oAT.agent.collect.redis.RedissonCollects;
import com.oAT.agent.collect.thread.ThreadPoolCollect;
import com.oAT.agent.common.*;
import com.oAT.agent.common.logger.Log;
import com.oAT.agent.common.logger.LogFactory;
import com.oAT.agent.context.AgentContext;
import com.oAT.agent.jacoco.data.CompactDataOutput;
import com.oAT.agent.jacoco.instr.InstrSupport;
import com.oAT.agent.transfer.HttpTransferServiceImpl;
import com.oAT.agent.transfer.TransferService;
import com.oAT.server.model.ClientInfoVo;
import com.oAT.server.model.ClientSessionVo;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.lang.instrument.Instrumentation;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 跟踪上下文
 */
public class TraceContext {
    private final static Log logger = LogFactory.getLog(TraceContext.class);

    private final Instrumentation instrumentation;
    private ThreadPoolExecutor scheduledPool;
    // 保存日志清理的调度器引用，便于在关闭时释放
    private ScheduledExecutorService logCleanupScheduler;

    private final Properties config;
    private TransferService transferService;
    private ClientSessionVo clientSession;

    // 记录 agent 启动时间
    private final long agentStartTime = System.currentTimeMillis();
    // 标记日志是否已发送
    private volatile boolean agentLogsSent = false;

    // 定义一个枚举来表示不同的配置状态
    enum ConfigStatus {
        HAS_COLLECTION, NO_COLLECTION
    }

    /**
     * doLogin时生成的时间戳
     */
    private Long loginTimestamp;

    public TraceContext(final Properties config, Instrumentation ins) {
        // 设置全局未捕获异常处理器
        Thread.setDefaultUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
            @Override
            public void uncaughtException(Thread t, Throwable e) {
                logger.error("[Agent-UncaughtException]线程未捕获异常, 线程: " + t.getName(), e);
            }
        });

        this.config = config;
        this.instrumentation = ins;

        // 包校验和上报逻辑
        verifyAndReportPackage();

        // 注册JVM关闭钩子，优雅关闭线程池
        Runtime.getRuntime().addShutdownHook(new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    if (scheduledPool != null) {
                        scheduledPool.shutdown();
                    }
                    if (logCleanupScheduler != null) {
                        logCleanupScheduler.shutdown();
                    }
                } catch (Throwable ignore) {
                }
            }
        }));

        AtomicReference<ConfigStatus> codeStackIncludeStatus = new AtomicReference<>();
        AtomicReference<ConfigStatus> confCodeStackIncludeStatus = new AtomicReference<>();

        // 客户端未登录或客户端未启动时，代码采集的配置处理
        if (doLogin()) {
            if (logger.isDebugEnabled()) {
                logger.debug("[Agent-debug]登录 server 成功，使用目标应用配置进行初始化采集器. " + getConfigStatus("codeStack.include"));
            }
            codeStackIncludeStatus.set(getConfigStatus("codeStack.include"));
            confCodeStackIncludeStatus.set(ConfigStatus.NO_COLLECTION);
        } else {
            logger.warn("[Agent-warn]登录 server 失败，使用本地（.conf）配置文件进行初始化采集器. " + getConfigStatus("conf_codeStack" +
                    ".include"));
            codeStackIncludeStatus.set(ConfigStatus.NO_COLLECTION);
            confCodeStackIncludeStatus.set(getConfigStatus("conf_codeStack.include"));
        }
        // server(conf中的server)初始化
        initializeServices();
        // 初始化采集器
        initCollects(codeStackIncludeStatus.get(), confCodeStackIncludeStatus.get());

    }

    /**
     * 包校验和上报逻辑
     * 为高性能高可用的异步处理，避免阻塞主线程
     */
    private void verifyAndReportPackage() {
        if (getRemoteServer().isEmpty()) {
            logger.warn("[Agent-warn]<UNK>'server'<UNK>");
            return;
        }
        // 为包校验创建独立的一次性单线程执行器，任务完成后关闭，避免线程池长期占用资源
        final ExecutorService verifierExecutor = Executors.newSingleThreadExecutor(new ThreadFactory() {
            @Override
            public Thread newThread(Runnable r) {
                Thread t = new Thread(r, "PackageVerifier");
                t.setDaemon(true);
                return t;
            }
        });
        verifierExecutor.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    String packagePath = PackageVerifier.getRunningJarPackagePath();
                    if (packagePath != null) {
                        logger.info("[Agent-校验包]开始进行读取包的 GitCommitId，包路径: " + packagePath);

                        String gitCommitIdFromJar = PackageVerifier.getGitCommitIdFromPackage(packagePath);

                        Map<String, String> data = new HashMap<String, String>();
                        if (clientSession != null) {
                            data.put("sessionId", clientSession.getSessionId());
                        } else {
                            data.put("sessionId", "");
                        }
                        data.put("packagePath", packagePath);
                        data.put("GitCommitIdFromJar", gitCommitIdFromJar);
//                    data.put("classFiles", String.valueOf(structureInfo.get("classFiles")));
//                    data.put("resourceFiles", String.valueOf(structureInfo.get("resourceFiles")));
//                    data.put("fileHashes", String.valueOf(contentHashes));

                        String targetUrl = getRemoteServer() + "/client/packageVerify";
                        logger.info("[Agent-校验包]准备上送包的 GitCommitId 数据... GitCommitId: " + gitCommitIdFromJar);
                        Boolean b = PackageVerifier.sendVerificationData(targetUrl, data);
                        if (b) {
                            logger.info("[Agent-校验包]包的 GitCommitId 数据上送完成。");
                        }
                    } else {
                        logger.warn("[Agent-校验包]未能获取到运行包路径，跳过获取包的 GitCommitId 和上送。");
                    }
                } catch (Throwable e) {
                    logger.error("[Agent-EXCError]校验包流程失败: " + StackTraceFormatter.formatExceptionWithAgentMark(e));
                } finally {
                    // 保证在任务完成后关闭执行器，释放线程
                    try {
                        verifierExecutor.shutdown();
                    } catch (Throwable ignore) {
                    }
                }
            }
        });
    }

    // 抽象出一个方法来获取配置的状态
    private ConfigStatus getConfigStatus(String configKey) {
        if (StringUtils.hasText(getConfig(configKey))) {
            return ConfigStatus.HAS_COLLECTION;
        }
        return ConfigStatus.NO_COLLECTION;
    }

    private void initializeServices() {
        transferService = new HttpTransferServiceImpl(this);
        // 减少 corePoolSize，避免为大量短任务创建过多线程导致 GC 压力
        scheduledPool = new ThreadPoolExecutor(4, 20, 1L, TimeUnit.MILLISECONDS,
                new LinkedBlockingQueue<Runnable>(1000));
        initHeartbeat();    // 初始化心跳任务
        initLogCleanup(); // 初始化日志清理任务
    }

    private void initHeartbeat() {
        if (getRemoteServer().isEmpty()) {
            logger.warn("[Agent-warn]<UNK>'server'<UNK>");
            return;
        }
        if (ConfigStatus.NO_COLLECTION.equals(getConfigStatus("codeStack.include"))) {
            logger.warn("[Agent-warn]未配置 codeStack.include，不能采集覆盖率数据");
            return;
        }

        final int initIntervalTime = Integer.parseInt(getConfig("heartbeat.time", "20").trim()) * 1000;
        final int sessionTimeout = Integer.parseInt(getConfig("session.timeout", "60").trim()) * 1000;

        //初始化心跳线程
        Thread t = new Thread(new Runnable() {
            //心跳间隔时间
            volatile int intervalTime = initIntervalTime;
            //心跳连接失败的时间
            long turnOffTime = -1;

            @Override
            public void run() {
                while (true) {
                    try {
                        TimeUnit.MILLISECONDS.sleep(initIntervalTime);
                    } catch (InterruptedException e) {
                        logger.error("[Agent-EXCError]心跳线程被中断. " + StackTraceFormatter.formatExceptionWithAgentMark(e));
                    }
                    if (doHeartbeat()) {
                        intervalTime = initIntervalTime;
                        if (clientSession.isDisable()) {
                            clientSession.setDisable(false);
                            logger.info(String.format("[Agent-info]server 会话已恢复，断开总时长：%s 分钟",
                                    ((System.currentTimeMillis() - turnOffTime) / 1000 / 60)));
                        }
                        turnOffTime = -1;
                    } else {
                        //心跳失败则逐步延长心跳间隔，直至1分钟
                        synchronized (this) {
                            intervalTime = Math.min(intervalTime * 11 / 10, 60 * 1000);
                        }
                        if (logger.isDebugEnabled()) {
                            logger.debug(String.format("向 server 发送心跳失败，%s 秒后重新发送", (intervalTime / 1000)));
                        }
                        long turnOffTimeL = System.currentTimeMillis() - turnOffTime;
                        //记录断开时间
                        if (turnOffTime == -1) {
                            turnOffTime = System.currentTimeMillis();
                        } else if (clientSession != null && turnOffTimeL > sessionTimeout && !clientSession.isDisable()) {
                            clientSession.setDisable(true);
                            //计算断开总时长
                            logger.error(String.format("[Agent-initHeartbeat]server 连接已超时 %s 秒，会话已断开",
                                    (sessionTimeout / 1000)));
                        }
                    }
                }
            }
        });
        t.setDaemon(true);
        t.setName("oAT-heartbeat");
        t.start();
    }

    private void initLogCleanup() {
        logCleanupScheduler = Executors.newScheduledThreadPool(1);
        logCleanupScheduler.scheduleAtFixedRate(new Runnable() {
            @Override
            public void run() {
                cleanOldLogs();
            }
        }, 0, 1, TimeUnit.DAYS); // 每天执行一次
    }

    //初始化采集器
    private void initCollects(ConfigStatus codeStackIncludeStatus, ConfigStatus confCodeStackIncludeStatus) {
        //TODO 基础远程开关，控制采集器的打开与关闭
        // 根据配置决定是否开启 System.out/err 代理，默认关闭以避免性能影响
        if (Boolean.parseBoolean(getConfig("collect.systemLog", "true"))) {
            SystemLogCollect.INSTANCE = new SystemLogCollect(this, instrumentation);
        }
        if (Boolean.parseBoolean(getConfig("collect.HttpServlet", "true"))) {
            HttpServletCollect.INSTANCE = new HttpServletCollect(this, instrumentation,
                    "javax.servlet.http.HttpServlet", "jakarta.servlet.http.HttpServlet"
            );
        }
        if (Boolean.parseBoolean(getConfig("collect.feignInvoker", "true"))) {
            FeignClientCollect.INSTANCE = new FeignClientCollect(this, instrumentation, "feign.Client$Default",
                    "feign.httpclient.ApacheHttpClient");
        }
        if (Boolean.parseBoolean(getConfig("collect.dubboInvoker", "true"))) {
            DubboInvokerCollect.INSTANCE = new DubboInvokerCollect(this, instrumentation);
        }
        if (Boolean.parseBoolean(getConfig("collect.dubboReceive", "true"))) {
            DubboReceiveCollect.INSTANCE = new DubboReceiveCollect(this, instrumentation);
        }
        if (Boolean.parseBoolean(getConfig("collect.sofaProviderInvoker", "true"))) {
            SofaServerCollect.INSTANCE = new SofaServerCollect(this, instrumentation);
        }
        if (Boolean.parseBoolean(getConfig("collect.sofaConsumerInvoker", "true"))) {
            SofaClientCollect.INSTANCE = new SofaClientCollect(this, instrumentation);
        }
        if (Boolean.parseBoolean(getConfig("collect.jdbc", "true"))) {
            JdbcCommonCollects.INSTANCE = new JdbcCommonCollects(this, instrumentation,
                    "com.mysql.cj.jdbc.NonRegisteringDriver", "com.mysql.jdbc.NonRegisteringDriver");
        }
        if (Boolean.parseBoolean(getConfig("collect.clickHouseJdbc", "true"))) {
            ClickHouseJdbcCollects.INSTANCE = new ClickHouseJdbcCollects(this, instrumentation, "ru.yandex.clickhouse" +
                    ".ClickHouseStatementImpl");
        }
        if (Boolean.parseBoolean(getConfig("collect.redis", "true"))) {
            RedisCollects.INSTANCE = new RedisCollects(this, instrumentation, "io.lettuce.core.protocol" +
                    ".DefaultEndpoint");
        }
        if (Boolean.parseBoolean(getConfig("collect.redisson", "true"))) {
            RedissonCollects.INSTANCE = new RedissonCollects(this, instrumentation, "org.redisson.command" +
                    ".RedisExecutor");
        }
        if (Boolean.parseBoolean(getConfig("collect.rabbitMq", "true"))) {
            RabbitMqCollects.INSTANCE = new RabbitMqCollects(this, instrumentation);
        }
        if (Boolean.parseBoolean(getConfig("collect.rabbitMqReceive", "true"))) {
            RabbitMqReceiveCollects.INSTANCE = new RabbitMqReceiveCollects(this, instrumentation);
        }
        if (Boolean.parseBoolean(getConfig("collect.rocketMq", "true"))) {
            RocketMqCollects.INSTANCE = new RocketMqCollects(this, instrumentation);
        }
        if (Boolean.parseBoolean(getConfig("collect.rocketMqReceive", "true"))) {
            RocketMqReceiveCollects.INSTANCE = new RocketMqReceiveCollects(this, instrumentation);
        }
        if (Boolean.parseBoolean(getConfig("collect.kafkaMq", "true"))) {
            KafkaMqCollects.INSTANCE = new KafkaMqCollects(this, instrumentation);
        }
        if (Boolean.parseBoolean(getConfig("collect.kafkaMqReceive", "true"))) {
            KafkaMqReceiveCollects.INSTANCE = new KafkaMqReceiveCollects(this, instrumentation);
        }
        if (Boolean.parseBoolean(getConfig("collect.httpClientV3", "true"))) {
            HttpClientCollectV3.INSTANCE = new HttpClientCollectV3(this, instrumentation,
                    "org.apache.commons.httpclient.HttpClient",
                    "org.apache.commons.httpclient.HttpMethod",
                    "org.apache.commons.httpclient.HttpMethodBase");
        }
        if (Boolean.parseBoolean(getConfig("collect.httpClientV4", "true"))) {
            HttpClientCollectV4.INSTANCE = new HttpClientCollectV4(this, instrumentation);
        }
        if (Boolean.parseBoolean(getConfig("collect.threadPool", "true"))) {
            ThreadPoolCollect.INSTANCE = new ThreadPoolCollect(instrumentation);
        }
        //设置了 service.include 才开启服务类采集
        if (StringUtils.hasText(getConfig("service.include"))) {
            ServiceCollect.INSTANCE = new ServiceCollect(this, instrumentation);
        }
        logJvmAndAsmInfo(); // 打印JVM和ASM信息
        //仅设置了 codeStack.include 采集范围，才开启采集器
        if (logger.isDebugEnabled()) {
            logger.debug("[Agent-debug]" + codeStackIncludeStatus);
        }
        switch (codeStackIncludeStatus) {
            case HAS_COLLECTION:
                logger.info("[Agent-使用目标应用配置]正在开启 codeStack 初始化采集器.");
                CodeStackCollect.INSTANCE = new CodeStackCollect(this, instrumentation);
                CodeStaticStackCollect.INSTANCE = new CodeStaticStackCollect(this, instrumentation);
                break;
            case NO_COLLECTION:
                // 检查另一个配置
                switch (confCodeStackIncludeStatus) {
                    case HAS_COLLECTION:
                        logger.info("[Agent-使用本地（.conf）配置]正在开启 codeStack 初始化采集器.");
                        CodeStackCollect.INSTANCE = new CodeStackCollect(this, instrumentation);
                        CodeStaticStackCollect.INSTANCE = new CodeStaticStackCollect(this, instrumentation);
                        break;
                    case NO_COLLECTION:
                        // 如果两个配置都没有，则不执行任何操作
                        break;
                }
                break;
        }
    }

    private void logJvmAndAsmInfo() {
        if (logger.isDebugEnabled()) {
            logger.debug("===================================================\n" +
                    "Java 版本与 class file version 对照表：\n" +
                    "Java 1.1: 45.3 " + "Java 1.2: 46.0 " + "Java 1.3: 47.0 " + "Java 1.4: 48.0 " + "Java 5 : 49.0\n" +
                    "Java 6  : 50.0 " + "Java 7  : 51.0 " + "Java 8  : 52.0 " + "Java 9  : 53.0 " + "Java 10: 54.0\n" +
                    "Java 11 : 55.0 " + "Java 12 : 56.0 " + "Java 13 : 57.0 " + "Java 14 : 58.0 " + "Java 15: 59.0\n" +
                    "Java 16 : 60.0 " + "Java 17 : 61.0 " + "Java 18 : 62.0 " + "Java 19 : 63.0 " + "Java 20: 64.0\n" +
                    "Java 21 : 65.0 " + "Java 22 : 66.0 " + "Java 23 : 67.0\n" +
                    "===================================================");
        }

        String javaVersion = System.getProperty("java.version");
        String classVersion = System.getProperty("java.class.version");
        String asmVersion = InstrSupport.getAsmApiVersionString(Integer.parseInt(classVersion.split("\\.")[0]) + 44
                // class 52 -> V1_8
        );
        logger.info("[Agent-info]当前运行该 Java 程序的 JVM（Java 虚拟机）版本: " + javaVersion
                + ", class 版本: " + classVersion
                + ", agent需使用的 ASM 版本: " + asmVersion);
    }

    /**
     * 登录客户端
     */
    private boolean doLogin() {
        if (getRemoteServer().isEmpty()) {
            logger.warn("[Agent-warn]<UNK>'server'<UNK>");
            return false;
        }

        String loginUrl = getRemoteServer() + "/client/login";
        // doLogin时生成时间戳
        loginTimestamp = System.currentTimeMillis();
        ClientInfoHelp clientInfoHelp = new ClientInfoHelp(this);
        ClientInfoVo clientInfoVo = clientInfoHelp.buildClientInfo(loginTimestamp);
        String clientInfoStr = JsonUtil.toJson(clientInfoVo);
        Map<String, String> params = new HashMap<>();
        params.put("clientInfo", clientInfoStr);
        //获取本机信息
        String clientSessionStr;
        try {
            clientSessionStr = HttpClient.execHttp(loginUrl, params).get(10, TimeUnit.SECONDS);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("[Agent-EXCError]登录 server 被中断. " + ie);
            return false;
        } catch (ExecutionException | TimeoutException ee) {
            logger.error("[Agent-EXCError]登录 server 失败. " + ee); // 完整堆栈
            return false;
        }
        clientSession = JsonUtil.toObject(clientSessionStr, ClientSessionVo.class);
        //装载应用程序配配置文件，不能覆盖本地配置
        Properties appConfig = clientSession.getConfigs();
        if (logger.isDebugEnabled()) {
            logger.debug("[Agent-debug]当前配置项如下：");
        }
        if (appConfig != null) {
            for (String s : appConfig.stringPropertyNames()) {
                if (logger.isDebugEnabled()) {
                    logger.debug("[Agent-debug]" + s + " = " + appConfig.getProperty(s));
                }
                if (!this.config.containsKey(s)) {
                    this.config.put(s, appConfig.getProperty(s));
                }
            }
        } else {
            logger.warn("[Agent-warn]appConfig is null, 未获取到应用配置");
        }
        if (clientSession.getApplication() == null) {
            logger.warn("===================================================\n" +
                    "[Agent-warn]登录server失败，未获取到应用信息"+ "\n" +
                    "===================================================");
            return false;
        }
        logger.info(String.format("===================================================\n" +
                        "[Agent-doLogin]登录 server 成功。目标服务应用名称: %s, appKey: %s, sessionId: %s\n",
                clientSession.getApplication().getAppName(), clientSession.getClientInfo().getAppKey(),
                clientSession.getSessionId()) +
                "===================================================");
        return true;
    }

    private boolean doHeartbeat() {
        int maxRetries = 5; // 最大重试次数
        int retryInterval = 5111; // 重试间隔时间，单位为毫秒
        int attempt = 0;    //尝试连接次数，初始从0开始计数

        // 服务可用且只发送启动前5分钟的日志，只发一次
        if (isServerAvailable()) {
            // 只在启动5-6分钟内且未发送过日志时发送
            if (!agentLogsSent
                    && (1 * 60 * 1000 < System.currentTimeMillis() - agentStartTime
                    && System.currentTimeMillis() - agentStartTime < 6 * 60 * 1000)) {
                sendAgentLogs();
                agentLogsSent = true;

                CompactDataOutput.trySendStaticInfo();
            }
        }

        while (attempt < maxRetries) {
            if (isServerAvailable()) {
                return true;
            }
            attempt++;
            logger.warn("[Agent-warn]server 不可用，尝试重新连接... 尝试次数: " + attempt);
            try {
                Thread.sleep(retryInterval); // 等待一段时间后重试
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                logger.error("[Agent-EXCError]心跳重试过程中线程被中断. " + StackTraceFormatter.formatExceptionWithAgentMark(e));
                return false;
            }
        }
        return false;
    }

    private boolean isServerAvailable() {
        if (getRemoteServer().isEmpty()) {
            logger.warn("[Agent-warn]<UNK>'server'<UNK>");
            return false;
        }
        if (clientSession == null) {
            String loginUrl = getRemoteServer() + "/client/login";
            ClientInfoHelp clientInfoHelp = new ClientInfoHelp(this);
            // 复用doLogin时的时间戳
            ClientInfoVo clientInfoVo = clientInfoHelp.buildClientInfo(loginTimestamp);
            Map<String, String> params = new HashMap<>();
            params.put("clientInfo", JsonUtil.toJson(clientInfoVo));
            try {
                String clientSessionStr = HttpClient.execHttp(loginUrl, params).get(10, TimeUnit.SECONDS);
                clientSession = JsonUtil.toObject(clientSessionStr, ClientSessionVo.class);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                logger.error("[Agent-EXCError]server 可用性失败，重新登录被中断. " + ie);
                return false;
            } catch (ExecutionException | TimeoutException e) {
                logger.error("[Agent-EXCError]server 可用性失败，重新登录失败. " + e);
                return false;
            }
        }
        String heartbeatUrl = getRemoteServer() + "/client/heartbeat/"
                + clientSession.getSessionId() + "/"
                + clientSession.getClientInfo().getAppKey() + "/"
                + clientSession.getClientInfo().getTimesTamp();
        try {
            String result = HttpClient.execHttp(heartbeatUrl, new HashMap<String, String>()).get(10, TimeUnit.SECONDS);
            if ("heartbeatOK".equalsIgnoreCase(result)) {
                return true;
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("[Agent-EXCError]server 可用性检查被中断. " + ie);
        } catch (ExecutionException e) {
            logger.error("[Agent-EXCError]server 可用性检查失败. " + e);
        } catch (TimeoutException e) {
            logger.error("[Agent-EXCError]server 可用性检查失败. " + e);
        }
        return false;
    }

    /**
     * 创建会话
     */
    public TraceSession openTraceSession(String originClass, String originMethod, TraceRequest traceRequest) {
        TraceSession session;
        try {
            if (AgentContext.getTraceSession() != null) {
//                throw new IllegalStateException("[Agent-EXCError]TraceSession 已存在，不再创建。");
                session = AgentContext.getTraceSession();

                boolean traceIdNotBlank = StringUtils.isBlank(session.getTraceId());
                logger.warn("[Agent-warn]当前 TraceSession 已存在，不再创建。traceId 不为空: " + traceIdNotBlank);
                return session;
            }
            if (logger.isDebugEnabled()) {
                logger.debug("[Agent-debug]开启 TraceSession " + originClass + "#" + originMethod);
            }
            session = new TraceSession(this, traceRequest, transferService);
            AgentContext.setTraceSession(session);
        } catch (Throwable t) {
            logger.error("[Agent-EXCError]开启 TraceSession 异常: " + StackTraceFormatter.formatExceptionWithAgentMark(t));
            return null;
        }
        return session;
    }

    public TraceSession getTraceSession() {
        return AgentContext.getTraceSession();
    }

    /**
     * 关闭会话
     */
    public void closeTraceSession(TraceSession traceSession) {
        Assert.isTrue(traceSession == AgentContext.getTraceSession(), "当前 TraceSession 与本实例不一致，无法关闭会话。");
        AgentContext.removeTraceSession();
    }

    public ClientSessionVo getClientSession() {
        return clientSession;
    }

    public String getClientSessionId() {
        if (clientSession != null) {
            return clientSession.getSessionId();
        }
        return null;
    }

    public String getConfig(String key) {
        return this.config.getProperty(key);
    }

    public String getConfig(String key, String defaultValue) {
        return this.config.getProperty(key, defaultValue);
    }

    public String getRemoteServer() {
        if (this.config.getProperty("server") == null || this.config.getProperty("server").isEmpty()) {
            return "";
        }
        return "http://" + this.config.getProperty("server");
    }

    /**
     * agent 生成 TraceId
     */
    public String createTraceId() {
        // 使用 ThreadLocalRandom 直接生成 32 位十六进制字符串，避免 UUID 对象与正则替换的开销
        long hi = java.util.concurrent.ThreadLocalRandom.current().nextLong();
        long lo = java.util.concurrent.ThreadLocalRandom.current().nextLong();
        return toFixedLengthHex(hi) + toFixedLengthHex(lo);
    }

    // 把 long 转为固定 16 位长度的十六进制字符串（高性能实现，无临时对象分配过多）
    private static String toFixedLengthHex(long v) {
        final char[] hex = "0123456789abcdef".toCharArray();
        char[] buf = new char[16];
        long value = v;
        for (int i = 15; i >= 0; i--) {
            int nibble = (int) (value & 0xF);
            buf[i] = hex[nibble];
            value = value >>> 4;
        }
        return new String(buf);
    }

    private void cleanOldLogs() {
        String logPath = config.getProperty("log.path", SystemUtil.getAgentPath() + "/logs/");
        File logDir = new File(logPath);
        File[] files = logDir.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isFile() && isOldLogFile(file)) {
                    deleteLogFile(file);
                }
            }
        }
    }

    private boolean isOldLogFile(File file) {
        long lastModified = file.lastModified();
        long sevenDaysAgo = System.currentTimeMillis() - 7L * 24 * 60 * 60 * 1000;
        return lastModified < sevenDaysAgo;
    }

    // 删除日志文件
    private void deleteLogFile(File file) {
        if (!file.delete()) {
            if (file.exists()) {
                logger.error("[Agent-EXCError]删除日志文件失败: " + file.getAbsolutePath());
            } else {
                if (logger.isDebugEnabled()) {
                    logger.debug("[Agent-debug]尝试删除日志文件时文件不存在: " + file.getAbsolutePath());
                }
            }
        } else {
            logger.info("[Agent-info]删除日志文件: " + file.getAbsolutePath());
        }
    }

    // 发送 agent 日志文件大小限制
    private static final int MAX_UPLOAD_SIZE = 1024 * 1024; // 1MB

    // 发送 agent 日志文件
    private void sendAgentLogs() {
        if (getRemoteServer().isEmpty()) {
            logger.warn("[Agent-warn]<UNK>'server'<UNK>");
            return;
        }
        if (clientSession == null) {
            logger.warn("[Agent-sendAgentLogs]clientSession is null, 无法发送日志");
            return;
        }
        String logPath = config.getProperty("log.path", SystemUtil.getAgentPath() + "/logs/");
        File logDir = new File(logPath);
        if (!logDir.exists() || !logDir.isDirectory()) {
            if (logger.isDebugEnabled()) {
                logger.debug("[Agent-debug]日志目录不存在或不是目录: " + logDir);
            }
            return;
        }
        final String pidPrefix = SystemUtil.getPid() + "-" + SystemUtil.getAppKeyFromArgs();
        StringBuilder readLog = new StringBuilder();
        int totalRead = 0;

        File[] files = logDir.listFiles(new FilenameFilter() {
            @Override
            public boolean accept(File dir, String name) {
                return name.startsWith(pidPrefix) && name.endsWith(".log") && !name.endsWith(".log.lck");
            }
        });

        if (files != null) {
            List<File> logFiles = new ArrayList<File>();
            for (File f : files) {
                if (f.isFile() && f.length() > 0 && f.canRead()) {
                    logFiles.add(f);
                }
            }

            Collections.sort(logFiles, new Comparator<File>() {
                @Override
                public int compare(File o1, File o2) {
                    long t1 = o1.lastModified();
                    long t2 = o2.lastModified();
                    return Long.compare(t1, t2);
                }
            });

            for (File path : logFiles) {
                if (totalRead >= MAX_UPLOAD_SIZE) break;
                String fileName = path.getAbsolutePath();
                try (RandomAccessFile raf = new RandomAccessFile(fileName, "r")) {
                    byte[] buffer = new byte[8192];
                    int len;
                    while ((len = raf.read(buffer)) != -1) {
                        if (totalRead + len > MAX_UPLOAD_SIZE) {
                            len = MAX_UPLOAD_SIZE - totalRead;
                        }
                        readLog.append(new String(buffer, 0, len, StandardCharsets.UTF_8));
                        totalRead += len;
                        if (totalRead >= MAX_UPLOAD_SIZE) break;
                    }
                } catch (IOException e) {
                    if (logger.isDebugEnabled()) {
                        logger.debug("[Agent-debug]跳过无法读取的日志文件(可能被锁): " + fileName + ", error: " + e.getMessage());
                    }
                }
            }
        }

        if (readLog.length() == 0) return;
        String uploadUrl = getRemoteServer() + "/client/agentLogs";
        Map<String, String> params = new HashMap<String, String>();
        params.put("sessionId", clientSession.getSessionId());
        params.put("logs", readLog.toString());
        try {
            HttpClient.execHttp(uploadUrl, params).get(10, TimeUnit.SECONDS);
            double sizeMb = totalRead / 1024.0 / 1024.0;
            logger.info("[Agent-info]发送日志文件成功，大小: " + String.format("%.2f MB", sizeMb));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("[Agent-EXCError]发送日志被中断. error: " + e.getMessage());
        } catch (ExecutionException | TimeoutException e) {
            logger.error("[Agent-EXCError]发送日志失败. error: " + e.getMessage());
        } catch (NoClassDefFoundError | Exception e) {
            logger.error("[Agent-EXCError]发送日志过程中发生异常: " + e.getMessage());
        }
    }

    public TransferService getTransferService() {
        return transferService;
    }
}
