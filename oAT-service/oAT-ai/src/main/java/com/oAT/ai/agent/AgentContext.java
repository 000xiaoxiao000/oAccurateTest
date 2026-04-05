package com.oAT.ai.agent;

import java.io.Serializable;

/**
 * AI Agent 会话上下文
 * 保存当前会话的关键信息，用于工具查询时获取上下文
 */
public class AgentContext implements Serializable {

    private static final ThreadLocal<AgentContext> CONTEXT_HOLDER = new ThreadLocal<>();

    /**
     * 项目ID
     */
    private String projectId;

    /**
     * 用户ID
     */
    private String userId;

    /**
     * 用户名称
     */
    private String userName;

    /**
     * 页面上下文（用户当前所在页面的信息）
     */
    private String pageContext;

    public AgentContext() {
    }

    public AgentContext(String projectId, String userId, String userName) {
        this.projectId = projectId;
        this.userId = userId;
        this.userName = userName;
    }

    /**
     * 设置当前线程的上下文
     */
    public static void setContext(AgentContext context) {
        CONTEXT_HOLDER.set(context);
    }

    /**
     * 获取当前线程的上下文
     */
    public static AgentContext getContext() {
        return CONTEXT_HOLDER.get();
    }

    /**
     * 清除当前线程的上下文
     */
    public static void clearContext() {
        CONTEXT_HOLDER.remove();
    }

    /**
     * 获取当前项目ID
     */
    public static String getCurrentProjectId() {
        AgentContext context = getContext();
        return context != null ? context.getProjectId() : null;
    }

    /**
     * 获取当前用户ID
     */
    public static String getCurrentUserId() {
        AgentContext context = getContext();
        return context != null ? context.getUserId() : null;
    }

    public String getProjectId() {
        return projectId;
    }

    public void setProjectId(String projectId) {
        this.projectId = projectId;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getUserName() {
        return userName;
    }

    public void setUserName(String userName) {
        this.userName = userName;
    }

    public String getPageContext() {
        return pageContext;
    }

    public void setPageContext(String pageContext) {
        this.pageContext = pageContext;
    }
}
