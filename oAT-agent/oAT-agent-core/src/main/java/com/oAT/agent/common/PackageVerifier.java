package com.oAT.agent.common;

import com.oAT.agent.common.logger.Log;
import com.oAT.agent.common.logger.LogFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

public class PackageVerifier {
    private final static Log logger = LogFactory.getLog(PackageVerifier.class);

    // 文件级验证：整体文件大小和SHA256校验和
    public static Map<String, Object> verifyFileLevel(File jarFile) throws Exception {
        Map<String, Object> result = new HashMap<String, Object>();
        long size = jarFile.length();
        String sha256 = calcSHA256(jarFile);
        result.put("size", bytesToMB(size));
        result.put("sha256", sha256);
        return result;
    }

    private static String bytesToMB(long bytes) {
        double mb = (double) bytes / 1024 / 1024;
        return String.format("%.4f MB", mb);
    }

    private static String calcSHA256(File file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        FileInputStream is = null;
        try {
            is = new FileInputStream(file);
            byte[] buf = new byte[8192];
            int n;
            while ((n = is.read(buf)) > 0) {
                digest.update(buf, 0, n);
            }
        } finally {
            if (is != null) {
                try {
                    is.close();
                } catch (IOException ignore) {}
            }
        }
        byte[] hash = digest.digest();
        StringBuilder sb = new StringBuilder();
        for (byte b : hash) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    // 元数据验证：MANIFEST.MF中的关键属性
    public static Map<String, String> verifyManifest(File jarFile, List<String> keyAttrs) throws Exception {
        Map<String, String> attrs = new HashMap<String, String>();
        JarFile jar = null;
        try {
            jar = new JarFile(jarFile);
            Manifest manifest = jar.getManifest();
            if (manifest == null) {
                return attrs;
            }
            Attributes mainAttrs = manifest.getMainAttributes();
            for (String key : keyAttrs) {
                String val = mainAttrs.getValue(key);
                attrs.put(key, val);
            }
        } finally {
            if (jar != null) {
                try {
                    jar.close();
                } catch (IOException ignore) {}
            }
        }
        return attrs;
    }

    // 结构验证：类文件和资源文件的数量和路径
    public static Map<String, Object> verifyStructure(File jarFile) throws Exception {
        int classCount = 0, jarCount = 0, resourceCount = 0;
        JarFile jar = null;
        try {
            jar = new JarFile(jarFile);
            Enumeration<JarEntry> entries = jar.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                String name = entry.getName();

                if (entry.isDirectory()) {
                    continue;
                }
                // 排除MANIFEST.MF文件
                if ("META-INF/MANIFEST.MF".equals(name)) {
                    continue;
                }

                // 如果指定了根路径，只统计该路径下的文件
                if (name.startsWith("BOOT-INF/classes") && name.endsWith(".class")) {
                    classCount++;
                } else if (name.startsWith("BOOT-INF/lib") && name.endsWith(".jar")) {
                    // 统计lib目录中的JAR包
                    jarCount++;
                } else {
                    resourceCount++;
                }
            }
        } finally {
            if (jar != null) {
                try {
                    jar.close();
                } catch (IOException ignore) {}
            }
        }
        Map<String, Object> result = new HashMap<String, Object>();
        result.put("classCount", classCount);
        result.put("jarCount", jarCount);
        result.put("resourceCount", resourceCount);
        return result;
    }

    // 内容验证：类文件的简化签名（基于哈希值），资源文件的校验和
    public static Map<String, String> verifyContent(File jarFile) throws Exception {
        Map<String, String> fileHashes = new HashMap<String, String>();
        JarFile jar = null;
        try {
            jar = new JarFile(jarFile);
            Enumeration<JarEntry> entries = jar.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                String name = entry.getName();
                if (entry.isDirectory() || "META-INF/MANIFEST.MF".equals(name)) {
                    continue;
                }
                InputStream is = null;
                try {
                    is = jar.getInputStream(entry);
                    String hash = calcStreamSHA256(is);
                    fileHashes.put(name, hash);
                } finally {
                    if (is != null) {
                        try {
                            is.close();
                        } catch (IOException ignore) {}
                    }
                }
            }
        } finally {
            if (jar != null) {
                try {
                    jar.close();
                } catch (IOException ignore) {}
            }
        }
        return fileHashes;
    }

    private static String calcStreamSHA256(InputStream is) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] buf = new byte[4096];
        int n;
        while ((n = is.read(buf)) > 0) {
            digest.update(buf, 0, n);
        }
        byte[] hash = digest.digest();
        StringBuilder sb = new StringBuilder();
        for (byte b : hash) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    /**
     * 获取当前运行 jar 包的绝对路径（优先使用 java.class.path 和 sun.java.command）
     */
    public static String getRunningJarPath() {
        try {
            // 优先使用 sun.java.command
            String command = System.getProperty("sun.java.command");
            if (command != null && command.endsWith(".jar")) {
                File jarFile = new File(command.split(" ")[0]);
                if (jarFile.exists()) {
                    return jarFile.getAbsolutePath();
                }
            }
            // 其次使用 java.class.path
            String classPath = System.getProperty("java.class.path");
            if (classPath != null) {
                String[] paths = classPath.split(File.pathSeparator);
                for (String path : paths) {
                    if (path.endsWith(".jar")) {
                        File jarFile = new File(path);
                        if (jarFile.exists()) {
                            return jarFile.getAbsolutePath();
                        }
                    }
                }
            }
        } catch (Throwable e) {
            logger.warn("[Agent-warn]Failed to get running jar path", e);
        }
        return null;
    }

    /**
     * 获取当前运行 jar 包的包路径（不带文件名和扩展名）
     * 例如：/home/user/app/yourApplication
     */
    public static String getRunningJarPackagePath() {
        String jarPath = getRunningJarPath();
        if (jarPath == null) {
            return null;
        }
        File jarFile = new File(jarPath);
        String parent = jarFile.getParent();
        String jarName = jarFile.getName();
        if (parent != null) {
            return parent + File.separator + jarName;
        } else {
            return jarName;
        }
    }

    // 发送验证数据到指定URL
    public static Boolean sendVerificationData(String targetUrl, Map<String, String> data) {
        try {
            if (data == null) {
                logger.warn("[Agent-sendVerificationData]Verification data is null, nothing will be sent to " + targetUrl);
                return false;
            }
            // 等待异步HTTP调用返回，设置超时以避免无限阻塞
            String resp = HttpClient.execHttp(targetUrl, data).get(10, TimeUnit.SECONDS);
            if (resp != null) {
                return true;
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("[Agent-sendVerificationData]Interrupted while sending verification data to "
                    + targetUrl + " : " + e);
            return false;
        } catch (ExecutionException e) {
            logger.error("[Agent-sendVerificationData]Failed to send verification data to " + targetUrl + " : " + e);
            return false;
        } catch (TimeoutException e) {
            logger.error("[Agent-sendVerificationData]Timeout while sending verification data to " + targetUrl + " : "
                    + e);
            return false;
        }
        return false;
    }
}
