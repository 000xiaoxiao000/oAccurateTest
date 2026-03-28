package com.oAT.agent.attach;

import com.oAT.agent.AttachStart;
import com.oAT.shaded.zeroturnaround.zip.ZipUtil;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.List;

public class AttachAgent {
    public AttachAgent(String[] args) throws Exception{
        File folder = getFolder();

        long pid = selectProcessByNumber(args, folder);

        attachToSelectedProcess(pid, folder);
    }

    /**
     * 获取 arthas-bin.zip 文件夹
     */
    private File getFolder() throws IOException {
        File folder = new File(System.getProperty("user.home"), ".arthas");
        if (!folder.exists() && !folder.mkdirs()) {
            throw new IOException("Failed to create directory: " + folder.getAbsolutePath());
        }
        System.out.println("Arthas target folder: " + folder.getAbsolutePath());

        String zipResourcePath = "/lib/arthas-bin.zip";
        try (InputStream inputStream = AttachStart.class.getResourceAsStream(zipResourcePath)) {
            if (inputStream == null) {
                throw new RuntimeException("Could not find " + zipResourcePath + " in classpath.");
            }
            unzip(inputStream, folder);
        }

        System.out.println("Arthas extracted successfully.");
        return folder;
    }

    /**
     * 解压 arthas-bin.zip
     */
    private void unzip(InputStream inputStream, File targetDir) {
        ZipUtil.unpack(inputStream, targetDir);
    }

    /**
     * 创建 Arthas ClassLoader
     */
    private URLClassLoader createArthasClassLoader(File folder) throws Exception {
        File bootJar = new File(folder, "arthas-boot.jar");
        if (!bootJar.exists()) {
            throw new FileNotFoundException("arthas-boot.jar not found at " + bootJar.getAbsolutePath());
        }
        return new URLClassLoader(new URL[]{bootJar.toURI().toURL()}, ClassLoader.getSystemClassLoader());
    }

    /**
     * 选择PID
     */
    private long selectProcessByNumber(String[] args, File folder) throws Exception {
        if (args.length > 0) {
            // 如果命令行指定了PID，直接使用
            return Long.parseLong(args[0]);
        }

        // 交互式选择：显示进程列表
        System.out.println("已发现现有 Java 进程，请选择一个并输入该进程的序列号，例如：1。然后按回车键。");

        // 使用ProcessUtils.select()获取用户选择的PID
        try (URLClassLoader classLoader = createArthasClassLoader(folder)) {
            Class<?> processUtilsClass = classLoader.loadClass("com.taobao.arthas.boot.ProcessUtils");
            Method selectMethod = processUtilsClass.getMethod("select", boolean.class, long.class, String.class);
            long pid = (long) selectMethod.invoke(null, false, -1, null);

            if (pid < 0) {
                System.err.println("未选择有效的进程");
                System.exit(1);
            }

            return pid;
        }
    }

    /**
     * 根据选择的PID，附着进程
     */
    private void attachToSelectedProcess(long pid, File folder) {
        try {
            // 构建启动参数
            List<String> attachArgs = new ArrayList<>();
            attachArgs.add("-jar");
            attachArgs.add(new File(folder, "arthas-core.jar").getAbsolutePath());
            attachArgs.add("-pid");
            attachArgs.add(String.valueOf(pid));
            attachArgs.add("-telnet-port");
            attachArgs.add("3658");
            attachArgs.add("-http-port");
            attachArgs.add("8563");
            attachArgs.add("-core");
            attachArgs.add(new File(folder, "arthas-core.jar").getAbsolutePath());
            attachArgs.add("-agent");
            attachArgs.add(new File(folder, "arthas-agent.jar").getAbsolutePath());

            // 启动arthas-core.jar进行附加
            try (URLClassLoader classLoader = createArthasClassLoader(folder)) {
                Class<?> processUtilsClass = classLoader.loadClass("com.taobao.arthas.boot.ProcessUtils");
                Method startArthasCoreMethod = processUtilsClass.getMethod("startArthasCore", long.class, List.class);
                startArthasCoreMethod.invoke(null, pid, attachArgs);
            }

            System.out.println("正在附加到进程 " + pid + "...");

            // 连接到 Arthas 控制台
            connectToArthasConsole(folder);

        } catch (Exception e) {
            System.err.println("附加到进程 " + pid + " 失败: " + e.getMessage());
        }
    }

    /**
     * 连接控制台
     */
    private void connectToArthasConsole(File folder) {
        try {
            File clientJar = new File(folder, "arthas-client.jar");
            ProcessBuilder pb = new ProcessBuilder("java", "-jar", clientJar.getAbsolutePath(), "127.0.0.1", "3658");
            pb.inheritIO();
            pb.start().waitFor();
        } catch (Exception e) {
            System.err.println("Failed to start arthas client: " + e.getMessage());
        }
    }
}
