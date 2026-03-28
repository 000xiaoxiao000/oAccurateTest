package com.oAT.agent.trace;

import com.oAT.agent.common.NetUtils;
import com.oAT.agent.common.SystemUtil;
import com.oAT.agent.common.logger.Log;
import com.oAT.agent.common.logger.LogFactory;
import com.oAT.server.model.ClientInfoVo;

import java.lang.management.ManagementFactory;
import java.util.Arrays;
import java.util.List;

public class ClientInfoHelp {
    private final static Log logger = LogFactory.getLog(ClientInfoHelp.class);
    private final TraceContext traceContext;

    public ClientInfoHelp(TraceContext tcontext) {
        this.traceContext = tcontext;
    }

    public ClientInfoVo buildClientInfo(Long timestamp) {
        ClientInfoVo client = new ClientInfoVo();
        client.setSystemDir(System.getProperty("user.dir"));
        client.setAgentVersion(traceContext.getConfig("agentVersion"));
        //设置应用key，即appId
        if (traceContext.getConfig("appKey") == null || traceContext.getConfig("appKey").isEmpty()) {
            logger.warn("appKey有误，检查conf文件");
        }
        client.setAppKey(traceContext.getConfig("appKey"));
        client.setPid(SystemUtil.getPid());
        // 使用传入的时间戳，否则用当前时间戳
        client.setTimesTamp(timestamp != null ? timestamp : System.currentTimeMillis());
        client.setJvmVersion(System.getProperty("java.version"));
        client.setJvmOption(Arrays.toString(getJavaOption().toArray()));
        client.setOsVersion(System.getProperty("os.version"));
        client.setOsName(System.getProperty("os.name"));
        client.setAddressIp(NetUtils.getLocalHost());
        client.setAddressPort(NetUtils.getPort());
        String localMac = NetUtils.getLocalMac();
        if (localMac != null) {
            client.setAddressMac(localMac);
        } else {
            client.setAddressMac("unknown");
        }
        return client;
    }

    // 保持原有无参方法兼容
    public ClientInfoVo buildClientInfo() {
        return buildClientInfo(null);
    }

    public static List<String> getJavaOption() {
        return ManagementFactory.getRuntimeMXBean().getInputArguments();
    }

//    public static String getPort() {
//        // 环境变量
//        String port = System.getProperty("server.port");
//        if (port == null) {
//            RuntimeMXBean runtimeMXBean = ManagementFactory.getRuntimeMXBean();
//            // 启动参数
//            for (String arg : runtimeMXBean.getInputArguments()) {
//                if (arg.startsWith("-Dserver.port")) {
//                    port = arg.split("=")[1];
//                    break;
//                }
//            }
//            if (port == null) {
//                // springboot tomcat
//                String catalinaHome = runtimeMXBean.getSystemProperties().get("catalina.home");
//                if (catalinaHome != null) {
//                    port = catalinaHome.split("\\.")[1];
//                }
//            }
//        }
//        return port;
//    }
}
