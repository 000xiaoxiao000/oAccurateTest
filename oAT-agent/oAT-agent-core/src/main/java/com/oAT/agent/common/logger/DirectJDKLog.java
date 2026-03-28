/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.oAT.agent.common.logger;

import com.oAT.agent.common.SystemUtil;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.util.Properties;
import java.util.logging.*;

/**
 * Hardcoded java.util.logging commons-logging implementation.
 * <p>
 * In addition, it curr
 */
class DirectJDKLog implements Log {
    private static FileHandler fileHandler;
    private static ConsoleHandler consoleHandler; // 仅debug级别下启用
    private static boolean enableConsole = false;

    private final Level currentLevel;
    private final String name;
    private final Handler[] handlers;

    public DirectJDKLog(String name) {
        this.name = name;
        if (fileHandler == null) {
            throw new IllegalStateException("未初始化日志处理器。调用 DirectJDKLog.init 进行初始化。name: " + name);
        }
        // 仅debug级别下启用控制台输出
        if (enableConsole && consoleHandler != null) {
            handlers = new Handler[]{fileHandler, consoleHandler};
        } else {
            handlers = new Handler[]{fileHandler};
        }
        currentLevel = fileHandler.getLevel();
    }

    static {
        File configFile = new File(SystemUtil.getAgentPath(), "conf/oAT.conf");
        File oATConfAPPKeyFile = new File(SystemUtil.getAgentPath(),
                "conf/oAT_" + SystemUtil.getAppKeyFromArgs() + ".conf");
        File selectedFile;
        if (oATConfAPPKeyFile.exists() && oATConfAPPKeyFile.isFile()) {
            selectedFile = oATConfAPPKeyFile;
        } else if (configFile.exists() && configFile.isFile()) {
            selectedFile = configFile;
        } else {
            selectedFile = null;
        }
        if (selectedFile == null) {
            Logger.getLogger(DirectJDKLog.class.getName()).log(Level.SEVERE, "Failed to load configuration file");
        }
        Properties prop = new Properties();
        if (selectedFile != null && selectedFile.exists() && selectedFile.isFile()) {
            try (FileInputStream input = new FileInputStream(selectedFile);
                 InputStreamReader reader = new InputStreamReader(input, Charset.defaultCharset())) { // 跟随系统默认编码
                prop.load(reader);
            } catch (IOException e) {
                Logger.getLogger(DirectJDKLog.class.getName()).log(Level.SEVERE, "Failed to load configuration file",
                        e);
            }
        }
        init(prop);
    }

    protected static void init(Properties properties) {
        // 获取日志路径，默认为 SystemUtil.getAgentPath() + "/logs/"
        String logPath = properties.getProperty("log.path", SystemUtil.getAgentPath() + "/logs/");

        // 创建日志目录（如果不存在）
        File logDir = new File(logPath);
        if (!logDir.exists() && !logDir.mkdirs()) {
            Logger.getLogger(DirectJDKLog.class.getName()).log(Level.SEVERE,
                    "Failed to create log directory: " + logPath);
        }

        // 单个日志文件大小限制，单位为字节，默认100M
        int limit = Integer.parseInt(properties.getProperty("log.limit", "100")) * 1048576;
        // 日志文件数量限制，默认10个
        int count = Integer.parseInt(properties.getProperty("log.count", "10"));
        // 日志级别，默认为info
        Level level = toLevel(properties.getProperty("log.level", "info"));
        // 控制台日志级别
        String consoleLevelName = properties.getProperty("log.console", "off");
        Level consoleLevel = toLevel(consoleLevelName);

        // 仅debug级别下启用控制台输出
        enableConsole = !Level.OFF.equals(consoleLevel);

        // 允许通过 log.encoding 指定日志文件编码，默认使用系统编码
        String logEncoding = properties.getProperty("log.encoding", Charset.defaultCharset().name());

        try {
            // 日志文件的命名模式
            String pattern = logPath + "/" + SystemUtil.getPid() + "-" + SystemUtil.getAppKeyFromArgs() + "-%g.log";
            // 创建文件处理器
            fileHandler = new FileHandler(pattern, limit, count, true) {
                @Override
                public synchronized void publish(LogRecord record) {
                    super.publish(record);
                    flush();
                }
            };
            fileHandler.setLevel(level);
            fileHandler.setEncoding(logEncoding);
            fileHandler.setFormatter(new Formatter() {
                @Override
                public String format(LogRecord record) {
                    String msg = new SimpleFormatter().format(record);
                    // 保证每条日志以换行结尾
                    if (!msg.endsWith(System.lineSeparator())) {
                        msg += System.lineSeparator();
                    }
                    return msg;
                }
            });

            // 仅debug级别下启用控制台输出
            if (enableConsole) {
                consoleHandler = new ConsoleHandler() {
                    @Override
                    public synchronized void publish(LogRecord record) {
                        super.publish(record);
                        flush();
                    }
                };
                consoleHandler.setLevel(consoleLevel);
                consoleHandler.setEncoding(logEncoding); // 控制台编码跟随
                consoleHandler.setFormatter(new Formatter() {
                    @Override
                    public String format(LogRecord record) {
                        String msg = new SimpleFormatter().format(record);
                        if (!msg.endsWith(System.lineSeparator())) {
                            msg += System.lineSeparator();
                        }
                        return msg;
                    }
                });
            } else {
                consoleHandler = null;
            }

            // 写入初始日志
            String initMsg = String.format("PID=%s TimesTamp=%s JVM_Path=%s APP_Path=%s",
                    SystemUtil.getPid(), System.currentTimeMillis(), System.getProperty("java.home"),
                    System.getProperty("user.dir"));
            LogRecord initLogRecord = new LogRecord(Level.INFO, initMsg);
            initLogRecord.setLoggerName("startLog");
            fileHandler.publish(initLogRecord);
            if (enableConsole && consoleHandler != null) {
                consoleHandler.publish(initLogRecord);
            }
        } catch (IOException e) {
            // 日志文件锁定失败
            Logger.getLogger(DirectJDKLog.class.getName()).log(Level.SEVERE, "日志文件锁定失败", e);
        }
    }

    private static Level toLevel(String levelName) {
        if ("off".equals(levelName)) {
            return Level.OFF;
        } else if ("info".equals(levelName)) {
            return Level.INFO;
        } else if ("warn".equals(levelName)) {
            return Level.WARNING;
        } else if ("error".equals(levelName)) {
            return Level.SEVERE;
        } else if ("debug".equals(levelName)) {
            return Level.FINE;
        } else if ("trace".equals(levelName)) {
            return Level.FINEST;
        } else {
            throw new IllegalArgumentException("log level '" + levelName + "' is Illegality");
        }
    }

    @Override
    public final boolean isErrorEnabled() {
        return isLoggable(Level.SEVERE);
    }

    @Override
    public final boolean isWarnEnabled() {
        return isLoggable(Level.WARNING);
    }

    @Override
    public final boolean isInfoEnabled() {
        return isLoggable(Level.INFO);
    }

    @Override
    public final boolean isDebugEnabled() {
        return isLoggable(Level.FINE);
    }

    @Override
    public final boolean isFatalEnabled() {
        return isLoggable(Level.SEVERE);
    }

    @Override
    public final boolean isTraceEnabled() {
        return isLoggable(Level.FINEST);
    }

    public boolean isLoggable(Level level) {
        return currentLevel.intValue() <= level.intValue();
    }

    @Override
    public final void debug(Object message) {
        log(Level.FINE, String.valueOf(message), null);
    }

    @Override
    public final void debug(Object message, Throwable t) {
        log(Level.FINE, String.valueOf(message), t);
    }

    @Override
    public final void trace(Object message) {
        log(Level.FINEST, String.valueOf(message), null);
    }

    @Override
    public final void trace(Object message, Throwable t) {
        log(Level.FINEST, String.valueOf(message), t);
    }

    @Override
    public final void info(Object message) {
        log(Level.INFO, String.valueOf(message), null);
    }

    @Override
    public final void info(Object message, Throwable t) {
        log(Level.INFO, String.valueOf(message), t);
    }

    @Override
    public final void warn(Object message) {
        log(Level.WARNING, String.valueOf(message), null);
    }

    @Override
    public final void warn(Object message, Throwable t) {
        log(Level.WARNING, String.valueOf(message), t);
    }

    @Override
    public final void error(Object message) {
        log(Level.SEVERE, String.valueOf(message), null);
    }

    @Override
    public final void error(Object message, Throwable t) {
        log(Level.SEVERE, String.valueOf(message), t);
    }

    @Override
    public final void fatal(Object message) {
        log(Level.SEVERE, String.valueOf(message), null);
    }

    @Override
    public final void fatal(Object message, Throwable t) {
        log(Level.SEVERE, String.valueOf(message), t);
    }

    // from commons logging. This would be my number one reason why java.util.logging
    // is bad - design by committee can be really bad ! The impact on performance of
    // using java.util.logging - and the ugliness if you need to wrap it - is far
    // worse than the unfriendly and uncommon default format for logs.

    private void log(Level level, String msg, Throwable ex) {
        if (!isLoggable(level)) {
            return;
        }

        // 获取调用信息
        StackTraceElement[] locations = new Throwable().getStackTrace();
        String cname = "unknown";
        String method = "unknown";
        if (locations != null && locations.length > 2) {
            StackTraceElement caller = locations[2];
            cname = caller.getClassName();
            method = caller.getMethodName();
        }

        // 过滤并确保换行
        msg = ensureLineEnding(filterLogMsg(String.valueOf(msg)));

        LogRecord lr = new LogRecord(level, msg);
        lr.setSourceClassName(cname);
        lr.setSourceMethodName(method);
        lr.setThrown(ex);
        lr.setLoggerName(name);

        for (Handler handler : handlers) {
            handler.publish(lr);
        }
    }

    // 简单过滤日志内容，防止注入
    // 简单过滤日志内容，防止注入
    private String filterLogMsg(String msg) {
        if (msg == null) return "";

        int length = msg.length();
        if (length == 0) return msg;

        // 快速堆栈检测
        if (isLikelyStackTrace(msg, length)) {
            return ensureLineEnding(msg);
        }

        // 扫描确定是否需要过滤
        boolean needsFiltering = false;
        boolean endsWithNewline = false;

        for (int i = 0; i < length; i++) {
            char c = msg.charAt(i);
            if (c == '\r' || c == '\n') {
                needsFiltering = true;
                // 检查是否以换行结束
                if ((c == '\n' && i == length - 1) ||
                        (c == '\r' && i == length - 1) ||
                        (c == '\r' && i == length - 2 && msg.charAt(i + 1) == '\n')) {
                    endsWithNewline = true;
                }
                break; // 找到第一个即可
            }
        }

        // 无需过滤的情况
        if (!needsFiltering) {
            return msg;
        }

        // 执行过滤（手动遍历，避免正则）
        StringBuilder sb = new StringBuilder(length);
        boolean lastWasNewline = false;

        for (int i = 0; i < length; i++) {
            char c = msg.charAt(i);

            if (c == '\r') {
                if (!lastWasNewline) {
                    sb.append(' ');
                    lastWasNewline = true;
                }
                // 如果是 \r\n，跳过 \n
                if (i + 1 < length && msg.charAt(i + 1) == '\n') {
                    i++;
                }
            } else if (c == '\n') {
                if (!lastWasNewline) {
                    sb.append(' ');
                    lastWasNewline = true;
                }
            } else {
                sb.append(c);
                lastWasNewline = false;
            }
        }

        String result = sb.toString();

        // 处理结尾换行
        if (endsWithNewline) {
            // 移除结尾空格
            int end = result.length();
            while (end > 0 && result.charAt(end - 1) == ' ') {
                end--;
            }
            return (end < result.length() ? result.substring(0, end) : result)
                    + System.lineSeparator();
        }

        return result;
    }

    // 堆栈检测
    private boolean isLikelyStackTrace(String msg, int length) {
        // 快速否定：太短不可能是堆栈
        if (length < 50) return false;

        char[] chars = msg.toCharArray();

        // 检查 "\n\tat " 模式（最常见的堆栈格式）
        // 从第10个字符开始检查，跳过开头可能的普通日志
        for (int i = 10; i < length - 5; i++) {
            if (chars[i] == '\n' &&
                    i + 4 < length &&
                    chars[i + 1] == '\t' &&
                    chars[i + 2] == 'a' &&
                    chars[i + 3] == 't' &&
                    chars[i + 4] == ' ') {
                return true;
            }

            // 检查 "\r\n\tat " 模式
            if (chars[i] == '\r' &&
                    i + 5 < length &&
                    chars[i + 1] == '\n' &&
                    chars[i + 2] == '\t' &&
                    chars[i + 3] == 'a' &&
                    chars[i + 4] == 't' &&
                    chars[i + 5] == ' ') {
                return true;
            }
        }

        // 快速检查 "Caused by:" 和 "Suppressed:"
        // 只检查可能的位置，避免全量遍历
        int checkLimit = Math.min(200, length); // 堆栈通常在开头部分
        for (int i = 0; i < checkLimit; i++) {
            // 检查 "Caused by:"
            if (i + 9 < length &&
                    chars[i] == 'C' &&
                    chars[i + 1] == 'a' &&
                    chars[i + 2] == 'u' &&
                    chars[i + 3] == 's' &&
                    chars[i + 4] == 'e' &&
                    chars[i + 5] == 'd' &&
                    chars[i + 6] == ' ' &&
                    chars[i + 7] == 'b' &&
                    chars[i + 8] == 'y' &&
                    chars[i + 9] == ':') {
                return true;
            }

            // 检查 "Suppressed:"
            if (i + 10 < length &&
                    chars[i] == 'S' &&
                    chars[i + 1] == 'u' &&
                    chars[i + 2] == 'p' &&
                    chars[i + 3] == 'p' &&
                    chars[i + 4] == 'r' &&
                    chars[i + 5] == 'e' &&
                    chars[i + 6] == 's' &&
                    chars[i + 7] == 's' &&
                    chars[i + 8] == 'e' &&
                    chars[i + 9] == 'd' &&
                    chars[i + 10] == ':') {
                return true;
            }
        }

        return false;
    }

    private String ensureLineEnding(String msg) {
        if (msg == null) {
            return System.lineSeparator();
        }
        if (msg.endsWith("\n")) {
            return msg;
        }
        return msg + System.lineSeparator();
    }

    /**
     * 释放日志相关资源（如文件句柄等）。
     * 兼容 commons-logging 的 LogFactory 设计。
     */
    static void release() {
        // 释放 fileHandler
        if (fileHandler != null) {
            try {
                fileHandler.close();
            } catch (Exception ignored) {
            }
            fileHandler = null;
        }
        // 释放 consoleHandler
        if (consoleHandler != null) {
            try {
                consoleHandler.close();
            } catch (Exception ignored) {
            }
            consoleHandler = null;
        }
    }

    static Log getInstance(String name) {
        return new DirectJDKLog(name);
    }
}
