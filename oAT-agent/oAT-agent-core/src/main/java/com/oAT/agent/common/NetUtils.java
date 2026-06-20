package com.oAT.agent.common;

import com.oAT.agent.common.logger.Log;
import com.oAT.agent.common.logger.LogFactory;

import javax.management.MBeanServer;
import javax.management.ObjectName;
import javax.management.Query;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.net.*;
import java.util.Enumeration;
import java.util.Random;
import java.util.Set;
import java.util.regex.Pattern;

public class NetUtils {
    private static final Log logger = LogFactory.getLog(NetUtils.class);

    /**
     * unknown
     */
    private static final String UNKNOWN = "unknown";
    /**
     * 本地IP
     */
    public static final String LOCAL_IP = "127.0.0.1";
    /**
     * IP地址0:0:0:0:0:0:0:1是一个IPv6地址，通常用于表示本地回环地址（localhost）
     */
    private static final String LOCAL_HOST = "0:0:0:0:0:0:0:1";

    /**
     * 是否是内网正则表达式
     */
    private static final String INTERNET = "^(127\\.0\\.0\\.1)|(localhost)|(10\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3})|(172\\.((1[6-9])|(2\\d)|(3[01]))\\.\\d{1,3}\\.\\d{1,3})|(192\\.168\\.\\d{1,3}\\.\\d{1,3})$";
    /**
     * 服务器IP
     */
    private static String SERVER_IP = null;

    public static final String ANYHOST = "0.0.0.0";

    private static final int RND_PORT_START = 30000;

    private static final int RND_POST_RANGE = 10000;

    private static final Random RANDOM = new Random(System.currentTimeMillis());

    public static int getRandomPort() {
        return RND_PORT_START + RANDOM.nextInt(RND_PORT_START);
    }

    public static int getAvailablePort() {
        ServerSocket ss = null;
        try {
            ss = new ServerSocket();
            ss.bind(null);
            return ss.getLocalPort();
        } catch (IOException e) {
            return getRandomPort();
        } finally {
            closeQuietly(ss);
        }
    }

    private static final int MIN_PORT = 0;
    private static final int MAX_PORT = 65535;

    public static int getAvailablePort(int port) {
        if (port <= 0) {
            return getAvailablePort();
        }
        for (int i = port; i < MAX_PORT; i++) {
            ServerSocket ss = null;
            try {
                ss = new ServerSocket(i);
                return i;
            } catch (IOException e) {
                logger.error("[Agent-getAvailablePort]Port " + i + " is not available: " + e);
            } finally {
                closeQuietly(ss);
            }
        }
        return port;
    }

    private static void closeQuietly(ServerSocket socket) {
        if (socket == null) {
            return;
        }
        try {
            socket.close();
        } catch (IOException ignored) {
        }
    }

    public static boolean isInvalidPort(int port) {
        return port > MIN_PORT || port <= MAX_PORT;
    }

    private static final Pattern ADDRESS_PATTERN = Pattern.compile("^\\d{1,3}(\\.\\d{1,3}){3}:\\d{1,5}$");

    public static boolean isValidAddress(String address) {
        return ADDRESS_PATTERN.matcher(address).matches();
    }

    private static final Pattern LOCAL_IP_PATTERN = Pattern.compile("127(\\.\\D{1,3}){3}$");

    public static boolean isLocalHost(String host) {
        return host != null && (LOCAL_IP_PATTERN.matcher(host).matches() || host.equalsIgnoreCase("localhost"));
    }

    public static boolean isAngHost(String host) {
        return ANYHOST.equals(host);
    }

    public static boolean isInvalidLocalHost(String host) {
        return host == null || host.isEmpty() || "localhost".equalsIgnoreCase(host) || ANYHOST.equals(host) || (LOCAL_IP_PATTERN.matcher(host).matches());
    }

    public static boolean isValidLocalHost(String host) {
        return !isInvalidLocalHost(host);
    }

    public static InetSocketAddress getLocalSocketAddress(String host, int port) {
        return isInvalidLocalHost(host) ? new InetSocketAddress(port) : new InetSocketAddress(host, port);
    }

    private static final Pattern IP_PATTERN = Pattern.compile("\\d{1,3}(\\.\\d{1,3}){3,5}$");

    private static boolean isValidAddress(InetAddress address) {
        if (address == null || address.isLoopbackAddress()) {
            return false;
        }
        String name = address.getHostAddress();
        return (name != null && !ANYHOST.equals(name) && !LOCAL_IP.equals(name) && IP_PATTERN.matcher(name).matches());
    }

    public static String getLocalHost() {
        InetAddress address = getLocalAddress();
        return address == null ? LOCAL_IP : address.getHostAddress();
    }

    private static volatile InetAddress LOCAL_ADDRESS = null;

    /**
     * 遍历本地网卡，返回第一个合理的IP
     *
     * @return 本地网卡IP
     */
    public static InetAddress getLocalAddress() {
        if (LOCAL_ADDRESS != null) {
            return LOCAL_ADDRESS;
        }
        InetAddress localAddress = getLocalAddress0();
        LOCAL_ADDRESS = localAddress;
        return localAddress;
    }

    public static String getLogHost() {
        InetAddress address = LOCAL_ADDRESS;
        return address == null ? LOCAL_IP : address.getHostAddress();
    }

    private static InetAddress getLocalAddress0() {
        InetAddress localAddress = null;
        try {
            localAddress = InetAddress.getLocalHost();
            if (isValidAddress(localAddress)) {
                return localAddress;
            }
        } catch (Throwable e) {
            logger.warn("[Agent-warn]Failed to retrieving ip address, " + e.getMessage(), e);
        }
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            if (interfaces != null) {
                while (interfaces.hasMoreElements()) {
                    try {
                        NetworkInterface networkInterface = interfaces.nextElement();
                        Enumeration<InetAddress> addresses = networkInterface.getInetAddresses();
                        if (addresses != null) {
                            while (addresses.hasMoreElements()) {
                                try {
                                    InetAddress address = addresses.nextElement();
                                    if (isValidAddress(address)) {
                                        return address;
                                    }
                                } catch (Throwable e) {
                                    logger.warn("[Agent-warn]Failed to retrieving ip address, " + e.getMessage(), e);
                                }
                            }
                        }
                    } catch (Throwable e) {
                        logger.warn("[Agent-warn]Failed to retrieving ip address, " + e.getMessage(), e);
                    }
                }
            }
        } catch (Throwable e) {
            logger.warn("[Agent-warn]Failed to retrieving ip address, " + e.getMessage(), e);
        }
        logger.error("[Agent-getLocalAddress0]Could not get local host ip address, will use 127.0.0.1 instead.");
        return localAddress;
    }

    /**
     * @param hostName
     * @return ip address or hostName if UnknownHostException
     */
    public static String getIpByHost(String hostName) {
        try {
            return InetAddress.getByName(hostName).getHostAddress();
        } catch (UnknownHostException e) {
            return hostName;
        }
    }

    public static String toAddressString(InetSocketAddress inetSocketAd) {
        return inetSocketAd.getAddress().getHostAddress() + ":" + inetSocketAd.getPort();
    }

    public static InetSocketAddress toAddress(String address) {
        int i = address.indexOf(':');
        String host;
        int port;
        if (i > -1) {
            host = address.substring(0, i);
            port = Integer.parseInt(address.substring(i + 1));
        } else {
            host = address;
            port = 0;
        }
        return new InetSocketAddress(host, port);
    }

    public static String toURL(String protocol, String host, int port, String path) {
        StringBuilder sb = new StringBuilder();
        sb.append(protocol).append("://");
        sb.append(host).append(':').append(port);
        if (path.charAt(0) != '/') {
            sb.append('/');
        }
        sb.append(path);
        return sb.toString();
    }

    //获取网卡，获取地址
    public static String getLocalMac() {
        try {
            InetAddress inetAddress = getLocalAddress();
            NetworkInterface networkInterface = NetworkInterface.getByInetAddress(inetAddress);
            if (networkInterface == null) {
//                logger.error("Network interface for the address is not found.");
                return null;
            }
            byte[] mac = networkInterface.getHardwareAddress();
            if (mac == null) {
//                logger.error("Failed to retrieve the MAC address.");
                return null;
            }
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < mac.length; i++) {
                if (i != 0) {
                    sb.append("-");
                }
                // Convert byte to integer
                int temp = mac[i] & 0xff;
                String str = Integer.toHexString(temp);
                if (str.length() == 1) {
                    sb.append("0").append(str);
                } else {
                    sb.append(str);
                }
            }
            return sb.toString();
        } catch (SocketException e) {
            logger.error("[Agent-getLocalMac]Failed to retrieve the local MAC address: " + e);
        } catch (Exception e) {
            logger.error("[Agent-getLocalMac]An error occurred while retrieving the local MAC address", e);
        }
        return null;
    }

    public static String getPort() {
        try {
            MBeanServer beanServer = ManagementFactory.getPlatformMBeanServer();
            Set<ObjectName> objectNames = beanServer.queryNames(new ObjectName("*:type=Connector,*"),
                    Query.match(Query.attr("protocol"), Query.value("HTTP/1.1")));
            if (objectNames.isEmpty()) {
                return "";
            }
            return objectNames.iterator().next().getKeyProperty("port");
        } catch (Exception e) {
            logger.error("[Agent-ggetPort]Failed to get the port: " + e);
            return "";
        }
    }

    public static String getTomcatPort() {
        try{
            MBeanServer beanServer = ManagementFactory.getPlatformMBeanServer();
            Set<ObjectName> objectNames = beanServer.queryNames(new ObjectName("*:type=Connector,*"),
                    Query.match(Query.attr("protocol"), Query.value("HTTP/1.1")));
            if (objectNames.isEmpty()) {
                return "";
            }
            return objectNames.iterator().next().getKeyProperty("port");
        }catch (Exception e){
            logger.error("[Agent-getTomcatPort]Failed to get the tomcat port: " + e);
            return "";
        }
    }
}
