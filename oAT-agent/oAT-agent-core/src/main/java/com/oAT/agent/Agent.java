package com.oAT.agent;

import com.oAT.agent.common.StackTraceFormatter;
import com.oAT.agent.common.logger.Log;
import com.oAT.agent.common.logger.LogFactory;
import com.oAT.agent.sandbox.core.SandboxLauncher;
import com.oAT.agent.sandbox.core.SandboxRuntime;
import com.oAT.agent.sandbox.core.StartMode;
import com.oAT.agent.trace.TraceContext;

import java.io.*;
import java.lang.instrument.Instrumentation;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.Properties;

public class Agent {
    private final static Log logger = LogFactory.getLog(Agent.class);

    public static TraceContext traceContext;

    public static Instrumentation instrumentation;
    private static volatile String lastStartupArg;

    public static void premain(String arg, Instrumentation instrumentation) {
        try {
            // 设置系统属性，确保编码正确
            System.setProperty("file.encoding", "UTF-8");
            System.setProperty("sun.jnu.encoding", "UTF-8");

            // 设置标准输入输出的编码
            if (System.console() == null) {
                try {
                    System.setOut(new PrintStream(System.out, true, "UTF-8"));
                    System.setErr(new PrintStream(System.err, true, "UTF-8"));
                } catch (UnsupportedEncodingException e) {
                    // 忽略，使用默认编码
                }
            }

            // 从 classpath 中移除 agent jar 的路径
            String classpath = System.getProperty("java.class.path");
            String agentJarPath = Agent.class.getProtectionDomain()
                    .getCodeSource()
                    .getLocation()
                    .getPath();

            // 兼容不同操作系统的路径分隔符
            String pathSeparator = File.pathSeparator;
            String[] paths = classpath.split(java.util.regex.Pattern.quote(pathSeparator));
            StringBuilder sb = new StringBuilder();
            boolean first = true;
            for (String path : paths) {
                if (!path.equals(agentJarPath)) {
                    if (!first) {
                        sb.append(pathSeparator);
                    }
                    sb.append(path);
                    first = false;
                }
            }
            String newClasspath = sb.toString();
            System.setProperty("java.class.path", newClasspath);

            // 调用实例方法进行启动
            start(arg, instrumentation, StartMode.PREMAIN);
        } catch (Throwable t) {
            logger.error("[Agent-EXCError] Agent初始化失败，被测系统将继续启动。错误原因: "
                    + StackTraceFormatter.formatExceptionWithAgentMark(t));
        }
    }

    private static void start(String arg, Instrumentation instrumentation, StartMode startMode) {
        try {
            Agent.instrumentation = instrumentation;
            lastStartupArg = arg;
            // 启动逻辑
            logger.info("[Agent-202601061956]Agent is starting...");
            SandboxRuntime runtime = SandboxLauncher.start(arg, instrumentation, startMode);
            traceContext = runtime.traceContext();
        } catch (Throwable t) {
            logger.error("[Agent-EXCError] Agent启动过程中发生错误，但不影响被测系统运行。错误原因: "
                    + StackTraceFormatter.formatExceptionWithAgentMark(t));
        }
    }

    public static Properties buildStartupProperties(String arg) {
        if (arg == null) {
            throw new IllegalArgumentException("[Agent-start]启动参数不能为空");
        }
        try {
            Properties startupProperties = new Properties();
            Properties properties = new Properties();

//        try {
//            //装载app（目标应用）注册信息
//            properties.putAll(getAppRegister());
//        } catch (IllegalArgumentException e) {
//            logger.error("[Agent-start]Invalid configuration parameter: " + e.getMessage(), e);
//            return;
//        }

            //装载启动参数信息，其可直接覆盖配置文件
            if (!arg.trim().isEmpty()) {
                try {
                    // 使用 StringReader 避免 ISO-8859-1 编码限制，确保非ASCII字符正确
                    startupProperties.load(new StringReader(arg.replaceAll(",", "\n")));
                } catch (IOException ioException) {
                    logger.error("[Agent-EXCError]Config format is error" + StackTraceFormatter.formatExceptionWithAgentMark(ioException));
                    throw new IllegalArgumentException("[Agent-EXCError]Config format is error", ioException);
                }
            }

            String appKey = startupProperties.getProperty("appKey", "");
            Properties agentConfigs = getAgentConfigs(appKey);
            if (agentConfigs.isEmpty()) {
                logger.warn("[Agent-start] 无法加载有效配置，Agent部分功能可能受限。appKey: " + appKey);
            } else {
                properties.putAll(agentConfigs);
            }
            properties.putAll(startupProperties);
            return properties;
        } catch (Throwable t) {
            throw t instanceof RuntimeException ? (RuntimeException) t : new RuntimeException(t);
        }
    }

    //读取应用注册信息
    //{app home}/oAT.key
//    private static final String[] appRegisterKeys = {"appKey", "projectKey"};

    //读取oAT.key或oAT.key，优先级由高到低
//    private static Properties getAppRegister() {
//        String path = System.getProperty("user.dir") + "oAT.key";
//        File keyFile = new File(path);
//        if (!keyFile.exists() || keyFile.isDirectory()) {
//            path = System.getProperty("user.dir") + "/oAT.key";
//            keyFile = new File(path);
//            if (!keyFile.exists() || keyFile.isDirectory()) {
//                logger.warn("找不到oAT.key配置文件: " + keyFile.getPath());
//                return new Properties();
//            }
//        }
//        Properties properties = new Properties();
//        try {
//            FileInputStream inputStream = new FileInputStream(keyFile);
//            properties.load(inputStream);
//            inputStream.close();
//        } catch (IOException ioException) {
//            logger.error("[Agent-EXCError]读取oAT.key配置文件失败"+ StackTraceFormatter
//            .formatExceptionWithAgentMark(ioException));
//        }
//        //验证参数是否合法
//        for (String s : properties.stringPropertyNames()) {
//            if (!Arrays.asList(appRegisterKeys).contains(s)) {
//                throw new IllegalArgumentException(keyFile.getPath() + "中存在非法的参数: " + s);
//            }
//        }
//        return properties;
//    }

    private static final String[] AGENT_CONFIG_KEYS = {
            "server", "heartbeatTime", "sessionTimeout", "agentVersion",
            "sandbox.jdbc.enabled", "sandbox.service.enabled", "sandbox.http-servlet.enabled",
            "sandbox.clickhouse-jdbc.enabled",
            "sandbox.http-client-v3.enabled", "sandbox.http-client-v4.enabled", "sandbox.feign.enabled",
            "sandbox.dubbo.enabled", "sandbox.sofa-rpc.enabled", "sandbox.mq-producer.enabled",
            "sandbox.mq-consumer.enabled", "sandbox.redis.enabled", "sandbox.redisson.enabled",
            "sandbox.coverage.enabled", "sandbox.system-log.enabled",
            "sandbox.context-propagation.enabled", "sandbox.context-propagation.executor",
            "sandbox.context-propagation.scheduled", "sandbox.context-propagation.spring",
            "sandbox.context-propagation.forkjoin",
            "sandbox.context-propagation.completable-future",
            //HTTP 请求与响应采集
            "collect.HttpServlet",
            "collect.httpRequestParams", "collect.httpRequestBody", "collect.httpResponseBody",
            //中间件采集开关
            "collect.systemLog", "collect.feignInvoker", "collect.dubboInvoker",
            "collect.dubboReceive", "collect.sofaProviderInvoker", "collect.sofaConsumerInvoker",
            "collect.jdbc", "collect.clickHouseJdbc", "collect.redis",
            "collect.redisson", "collect.rabbitMq", "collect.rabbitMqReceive",
            "collect.rocketMq", "collect.rocketMqReceive", "collect.kafkaMq",
            "collect.kafkaMqReceive","collect.httpClientV3","collect.httpClientV4",
            //日志开关
            "log.level", "log.console", "log.path",
            "log.encoding", "conf.encoding",
            //URL 过滤配置
            "exclude.urls",
            //服务过滤配置
            "conf_service.include", "conf_service.exclude",
            //代码采集
            "conf_codeStack.include",
            "conf_codeStack.includeMethod", "conf_codeStack.excludeMethod"
    };

    //读取agent配置
    private static Properties getAgentConfigs(String appKey) {
        URL url = Agent.class.getProtectionDomain().getCodeSource().getLocation();
        File confDir = new File(new File(url.getFile()).getParentFile(), "conf");
        File oatConfFile = new File(confDir, "oAT.conf");
        File oatConfAppKeyFile = new File(confDir, "oAT_" + appKey + ".conf");

        Properties properties = new Properties();
        boolean configFileFound = false;

        // 读取编码优先级：系统默认 -> 通过启动参数 conf.encoding 指定
        String encoding = properties.getProperty("conf.encoding", "UTF-8");

        configFileFound |= loadPropertiesFromFile(oatConfFile, properties, encoding);
        configFileFound |= loadPropertiesFromFile(oatConfAppKeyFile, properties, encoding);

        if (!configFileFound) {
            logger.warn("[Agent-warn]找不到任何配置文件: oAT.conf 或 oAT_" + appKey + ".conf");
            return properties;
        }

        for (String s : properties.stringPropertyNames()) {
            boolean containsKey = containsConfigKey(s);
            if (!containsKey) {
                throw new IllegalArgumentException("[Agent-EXCError]配置文件中存在非法的参数: " + s);
            }
        }

        return properties;
    }

    private static boolean containsConfigKey(String propertyName) {
        if (propertyName == null) {
            return false;
        }
        for (int i = 0; i < AGENT_CONFIG_KEYS.length; i++) {
            if (propertyName.indexOf(AGENT_CONFIG_KEYS[i]) >= 0) {
                return true;
            }
        }
        return false;
    }

    private static boolean loadPropertiesFromFile(File file, Properties properties, String encodingName) {
        if (!file.exists() || file.isDirectory()) {
            return false;
        } else {
            InputStream inputStream = null;
            Reader reader = null;
            try {
                inputStream = new FileInputStream(file);
                reader = new InputStreamReader(inputStream, safeCharset(encodingName));
                properties.load(reader);
                logger.debug("[Agent-debug]已使用编码(" + safeCharset(encodingName).name() + ")加载配置文件: " + file.getPath());
                return true;
            } catch (IOException ioException) {
                logger.error("[Agent-EXCError]读取配置文件失败: " + file.getPath() + StackTraceFormatter.formatExceptionWithAgentMark(ioException));
                return false;
            } finally {
                closeQuietly(reader);
                closeQuietly(inputStream);
            }
        }
    }

    private static void closeQuietly(Closeable closeable) {
        if (closeable == null) {
            return;
        }
        try {
            closeable.close();
        } catch (IOException ignored) {
        }
    }

    private static Charset safeCharset(String name) {
        if (name == null || name.trim().isEmpty()) {
            return Charset.defaultCharset();
        }
        try {
            return Charset.forName(name);
        } catch (Exception e) {
            logger.warn("[Agent-EXCError]指定编码无效: " + name + ", 回退使用系统默认编码: " + Charset.defaultCharset().name());
            return Charset.defaultCharset();
        }
    }

    /**
     * JVM attach 入口
     */
    public static void agentmain(String arg, Instrumentation instrumentation) {
        try {
            System.setProperty("file.encoding", "UTF-8");
            System.setProperty("sun.jnu.encoding", "UTF-8");
            String action = parseAttachAction(arg);
            if ("stop".equalsIgnoreCase(action)) {
                SandboxLauncher.stop();
                return;
            }
            if ("restart".equalsIgnoreCase(action)) {
                Agent.instrumentation = instrumentation;
                SandboxRuntime runtime = SandboxLauncher.restart(removeAttachAction(arg), instrumentation, StartMode.ATTACH);
                traceContext = runtime.traceContext();
                return;
            }
            if ("status".equalsIgnoreCase(action)) {
                SandboxRuntime runtime = SandboxLauncher.getRuntime();
                logger.info("[Sandbox] status=" + (runtime == null ? "NOT_STARTED" : runtime.moduleManager().stateNames()));
                return;
            }
            start(removeAttachAction(arg), instrumentation, StartMode.ATTACH);
        } catch (Throwable t) {
            logger.error("[Agent-EXCError] Agent attach 初始化失败，被测系统将继续运行。错误原因: "
                    + StackTraceFormatter.formatExceptionWithAgentMark(t));
        }
    }

    public static void handleSandboxCommand(String command) {
        if (command == null || command.trim().isEmpty()) {
            return;
        }
        String action = command.trim();
        if ("stop".equalsIgnoreCase(action)) {
            SandboxLauncher.stop();
            return;
        }
        if ("restart".equalsIgnoreCase(action)) {
            SandboxRuntime runtime = SandboxLauncher.restart(lastStartupArg, instrumentation, StartMode.ATTACH);
            traceContext = runtime.traceContext();
            return;
        }
        if ("start".equalsIgnoreCase(action)) {
            SandboxRuntime runtime = SandboxLauncher.start(lastStartupArg, instrumentation, StartMode.ATTACH);
            traceContext = runtime.traceContext();
            return;
        }
        if ("status".equalsIgnoreCase(action)) {
            SandboxRuntime runtime = SandboxLauncher.getRuntime();
            logger.info("[Sandbox] status=" + (runtime == null ? "NOT_STARTED" : runtime.moduleManager().stateNames()));
        }
    }

    private static String parseAttachAction(String arg) {
        Properties properties = parseArgProperties(arg);
        return properties.getProperty("action", "start");
    }

    private static String removeAttachAction(String arg) {
        if (arg == null || arg.trim().isEmpty()) {
            return arg;
        }
        String[] parts = arg.split(",");
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < parts.length; i++) {
            String item = parts[i];
            if (item == null || item.trim().startsWith("action=")) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append(',');
            }
            builder.append(item);
        }
        return builder.toString();
    }

    private static Properties parseArgProperties(String arg) {
        Properties properties = new Properties();
        if (arg == null || arg.trim().isEmpty()) {
            return properties;
        }
        try {
            properties.load(new StringReader(arg.replaceAll(",", "\n")));
        } catch (IOException e) {
            logger.warn("[Agent-EXCError]attach 参数解析失败: " + arg);
        }
        return properties;
    }
}
