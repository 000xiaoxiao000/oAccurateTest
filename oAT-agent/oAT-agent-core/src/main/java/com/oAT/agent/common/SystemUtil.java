package com.oAT.agent.common;

import com.oAT.agent.Agent;

import java.io.File;
import java.lang.management.ManagementFactory;
import java.net.URL;

public class SystemUtil {
    private static final String DEFAULT_APP_KEY = "defaultAppKey";

    public static String getAppKeyFromArgs() {
        String javaAgentArg = "-javaagent:";
        String appKeyPrefix = "appKey=";

        // 获取 JVM 启动参数
        for (String arg : ManagementFactory.getRuntimeMXBean().getInputArguments()) {
            if (arg.startsWith(javaAgentArg) && arg.contains(appKeyPrefix)) {
                // 提取 appKey 的值
                int startIndex = arg.indexOf(appKeyPrefix) + appKeyPrefix.length();
                return arg.substring(startIndex);
            }
        }
        return DEFAULT_APP_KEY; // 如果未找到，返回默认值
    }
    //获取 进程ID pid
    //获取 Agent 部署目录
    public static String getPid(){
        String name = ManagementFactory.getRuntimeMXBean().getName();
        return name.split("@")[0];
    }

    /**
     * 获取agent 部署路径
     */
    public static String getAgentPath(){
        String agentHome = System.getProperty("oAT.agent.home");
        if (agentHome != null && !agentHome.isEmpty()) {
            return agentHome;
        }
        try {
            URL url = Agent.class.getProtectionDomain().getCodeSource().getLocation();
            return new File(url.getFile()).getParentFile().getPath();
        } catch (Throwable e) {
            // 在 Bootstrap ClassLoader 加载时可能发生异常
            return "";
        }
    }

}
