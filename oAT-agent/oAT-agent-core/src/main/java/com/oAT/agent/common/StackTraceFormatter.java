package com.oAT.agent.common;


public class StackTraceFormatter {

    /**
     * 格式化堆栈跟踪，在Agent相关行前添加标记，并实现每行自动换行
     *
     * @param stackTrace   原始堆栈数组
     * @param agentPackage 标识Agent的包名前缀（可选）
     * @return 格式化后的堆栈字符串（每行自动换行）
     */
    public static String formatWithAgentMark(StackTraceElement[] stackTrace, String agentPackage) {
        // 支持多个包前缀
        String[] agentPrefixes;
        if (agentPackage == null || agentPackage.isEmpty()) {
            agentPrefixes = new String[] { "com.oAT.", "com.oAT.shaded." };
        } else {
            agentPrefixes = new String[] { agentPackage };
        }
        String lineSeparator = System.lineSeparator();
        StringBuilder sb = new StringBuilder();
        for (StackTraceElement element : stackTrace) {
            String line = "\tat " + element.toString();
            // 如果类名包含任一Agent包路径，则添加标记
            for (String prefix : agentPrefixes) {
                if (element.getClassName().startsWith(prefix)) {
                    line = "[Agent-特别关注] " + line; // 在Agent相关行前添加标记
                    break;
                }
            }
            sb.append(line).append(lineSeparator);
        }
        return sb.toString();
    }

    /**
     * 格式化堆栈跟踪，在Agent相关行前添加标记（agentPackage为默认值）
     *
     * @param stackTrace 原始堆栈数组
     * @return 格式化后的堆栈字符串
     */
    public static String formatWithAgentMark(StackTraceElement[] stackTrace) {
        return formatWithAgentMark(stackTrace, null);
    }

    /**
     * 格式化异常堆栈，在Agent相关行前添加标记
     *
     * @param throwable    异常对象
     * @param agentPackage 标识Agent的包名前缀（可选）
     * @return 格式化后的异常信息
     */
    public static String formatExceptionWithAgentMark(Throwable throwable, String agentPackage) {
        StringBuilder sb = new StringBuilder();
        String lineSeparator = System.lineSeparator();
        sb.append(throwable.toString()).append(lineSeparator);
        sb.append(formatWithAgentMark(throwable.getStackTrace(), agentPackage));

        // 处理cause异常
        Throwable cause = throwable.getCause();
        while (cause != null) {
            sb.append("Caused by: ").append(cause).append(lineSeparator);
            sb.append(formatWithAgentMark(cause.getStackTrace(), agentPackage));
            cause = cause.getCause();
        }

        return sb.toString();
    }

    /**
     * 格式化异常堆栈，在Agent相关行前添加标记（agentPackage为默认值）
     *
     * @param throwable 异常对象
     * @return 格式化后的异常信息
     */
    public static String formatExceptionWithAgentMark(Throwable throwable) {
        return formatExceptionWithAgentMark(throwable, null);
    }
}
