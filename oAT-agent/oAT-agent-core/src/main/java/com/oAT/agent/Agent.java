package com.oAT.agent;

import com.oAT.agent.common.StackTraceFormatter;
import com.oAT.agent.common.logger.Log;
import com.oAT.agent.common.logger.LogFactory;
import com.oAT.agent.trace.TraceContext;

import java.io.*;
import java.lang.instrument.Instrumentation;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Properties;
import java.util.stream.Collectors;

public class Agent {
    private final static Log logger = LogFactory.getLog(Agent.class);

    public static TraceContext traceContext;

    public static Instrumentation instrumentation;

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
            start(arg, instrumentation);
        } catch (Throwable t) {
            logger.error("[Agent-EXCError] Agent初始化失败，被测系统将继续启动。错误原因: "
                    + StackTraceFormatter.formatExceptionWithAgentMark(t));
        }
    }

    private static void start(String arg, Instrumentation instrumentation) {
        try {
            if (arg == null) {
                logger.error("[Agent-start]启动参数不能为空");
                return;
            }
            Agent.instrumentation = instrumentation;
            // 启动逻辑
            logger.info("[Agent-202601061956]Agent is starting...");
            Properties properties = new Properties();

//        try {
//            //装载app（目标应用）注册信息
//            properties.putAll(getAppRegister());
//        } catch (IllegalArgumentException e) {
//            logger.error("[Agent-start]Invalid configuration parameter: " + e.getMessage(), e);
//            return;
//        }

            //装载Debug调试参数信息，其可直接覆盖上述配置
            String[] kv = null;
            if (!arg.trim().isEmpty()) {
                kv = arg.split("=");
                if (kv.length == 2) {
                    properties.setProperty(kv[0], kv[1]);
                } else {
                    try {
                        // 使用 StringReader 避免 ISO-8859-1 编码限制，确保非ASCII字符正确
                        properties.load(new StringReader(arg.replaceAll(",", "\n")));
                    } catch (IOException ioException) {
                        logger.error("[Agent-EXCError]Config format is error" + StackTraceFormatter.formatExceptionWithAgentMark(ioException));
                        return;
                    }
                }
            }

            String appKey = "";
            if (kv != null && kv.length > 1) {
                appKey = kv[1];
            }
            Properties agentConfigs = getAgentConfigs(appKey);
            if (agentConfigs.isEmpty()) {
                logger.warn("[Agent-start] 无法加载有效配置，Agent部分功能可能受限。appKey: " + appKey);
            } else {
                properties.putAll(agentConfigs);
            }
            //构建追踪上下文
            traceContext = new TraceContext(properties, instrumentation);
        } catch (Throwable t) {
            logger.error("[Agent-EXCError] Agent启动过程中发生错误，但不影响被测系统运行。错误原因: "
                    + StackTraceFormatter.formatExceptionWithAgentMark(t));
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
            "collect.threadPool",
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
            boolean containsKey = Arrays.stream(AGENT_CONFIG_KEYS).anyMatch(s::contains);
            if (!containsKey) {
                throw new IllegalArgumentException("[Agent-EXCError]配置文件中存在非法的参数: " + s);
            }
        }

        return properties;
    }

    private static boolean loadPropertiesFromFile(File file, Properties properties, String encodingName) {
        if (!file.exists() || file.isDirectory()) {
            return false;
        } else {
            try (InputStream inputStream = Files.newInputStream(file.toPath());
                 Reader reader = new InputStreamReader(inputStream, safeCharset(encodingName))) {
                properties.load(reader);
                logger.debug("[Agent-debug]已使用编码(" + safeCharset(encodingName).name() + ")加载配置文件: " + file.getPath());
                return true;
            } catch (IOException ioException) {
                logger.error("[Agent-EXCError]读取配置文件失败: " + file.getPath() + StackTraceFormatter.formatExceptionWithAgentMark(ioException));
                return false;
            }
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
        System.setProperty("file.encoding", "UTF-8");
    }
}
