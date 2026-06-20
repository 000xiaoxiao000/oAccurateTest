package com.oAT.agent.common;

import com.oAT.agent.common.logger.Log;
import com.oAT.agent.common.logger.LogFactory;

import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class PackageVerifier {
    private final static Log logger = LogFactory.getLog(PackageVerifier.class);

    private static final String[] BUILD_INFO_PATHS = {
            "META-INF/git.properties",
            "META-INF/build-info.properties",
            "WEB-INF/classes/META-INF/git.properties",
            "WEB-INF/classes/META-INF/build-info.properties",
            "WEB-INF/classes/git.properties",
            "WEB-INF/classes/build-info.properties"
    };

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

    /**
     * 从 Jar/War 包中读取 Git Commit ID
     */
    public static String getGitCommitIdFromPackage(String packagePath) {
        ZipFile zipFile = null;
        InputStream is = null;
        try {
            zipFile = new ZipFile(packagePath);
            ZipEntry entry = findBuildInfoEntry(zipFile);
            if (entry == null) {
                return null;
            }

            Properties props = new Properties();
            is = zipFile.getInputStream(entry);
            props.load(is);

            // 优先使用完整 Commit ID
            String commitId = props.getProperty("git.commit.id");
            if (commitId == null || commitId.isEmpty()) {
                commitId = props.getProperty("git.commit.id.abbrev");
            }
            return commitId;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        } finally {
            closeQuietly(is);
            closeQuietly(zipFile);
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

    private static ZipEntry findBuildInfoEntry(ZipFile zipFile) {
        for (String buildInfoPath : BUILD_INFO_PATHS) {
            ZipEntry entry = zipFile.getEntry(buildInfoPath);
            if (entry != null) {
                return entry;
            }
        }
        return null;
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
