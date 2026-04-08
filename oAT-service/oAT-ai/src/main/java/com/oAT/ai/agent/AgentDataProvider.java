package com.oAT.ai.agent;

import java.util.List;
import java.util.Map;

/**
 * Agent 数据提供者接口
 * 定义 AI Agent 获取项目数据的接口，由 web 模块实现
 */
public interface AgentDataProvider {

    /**
     * 获取项目信息
     *
     * @param projectId 项目ID
     * @return 项目信息
     */
    Map<String, Object> getProjectInfo(String projectId);

    /**
     * 获取应用列表
     *
     * @param projectId 项目ID
     * @return 应用列表
     */
    List<Map<String, Object>> getApps(String projectId);

    /**
     * 获取在线应用列表
     *
     * @param projectId 项目ID
     * @return 在线应用列表
     */
    List<Map<String, Object>> getOnlineApps(String projectId);

    /**
     * 获取应用详情
     *
     * @param appId 应用ID
     * @return 应用详情
     */
    Map<String, Object> getAppDetail(String appId);

    /**
     * 获取覆盖率报告列表
     *
     * @param appId 应用ID
     * @return 覆盖率报告列表
     */
    List<Map<String, Object>> getCoverageReports(String appId);

    /**
     * 获取覆盖率报告详情
     *
     * @param reportId 报告ID
     * @return 报告详情
     */
    Map<String, Object> getCoverageReportDetail(String reportId);

    /**
     * 获取覆盖率趋势
     *
     * @param appId 应用ID
     * @param limit 限制数量
     * @return 趋势数据
     */
    List<Map<String, Object>> getCoverageTrend(String appId, int limit);

    /**
     * 获取类覆盖率列表
     *
     * @param reportId 报告ID
     * @param minRate  最小覆盖率（可选）
     * @param maxRate  最大覆盖率（可选）
     * @return 类覆盖率列表
     */
    List<Map<String, Object>> getClassCoverageList(String reportId, Double minRate, Double maxRate);

    /**
     * 获取调用链列表
     *
     * @param projectId 项目ID
     * @param appId     应用ID（可选）
     * @param limit     限制数量
     * @return 调用链列表
     */
    List<Map<String, Object>> getTraceList(String projectId, String appId, int limit);

    /**
     * 获取调用链详情
     *
     * @param traceId 调用链ID
     * @return 调用链详情
     */
    Map<String, Object> getTraceDetail(String traceId);

    /**
     * 获取快照列表
     *
     * @param projectId 项目ID
     * @param userId    用户ID（可选）
     * @return 快照列表
     */
    List<Map<String, Object>> getSnapshots(String projectId, String userId);

    /**
     * 获取快照详情
     *
     * @param snapshotId 快照ID
     * @return 快照详情
     */
    Map<String, Object> getSnapshotDetail(String snapshotId);

    /**
     * 搜索代码关系
     *
     * @param projectId 项目ID
     * @param keyword   关键词
     * @return 代码关系结果
     */
    Map<String, Object> searchCodeRelation(String projectId, String keyword);

    /**
     * 获取接口调用关系图
     *
     * @param className 类名
     * @param methodName 方法名（可选）
     * @return 调用关系图数据
     */
    Map<String, Object> getCallGraph(String className, String methodName);

    /**
     * 获取项目统计数据
     *
     * @param projectId 项目ID
     * @return 统计数据
     */
    Map<String, Object> getProjectStatistics(String projectId);

    /**
     * 获取指定类的源码（用于AI代码分析）
     *
     * @param className 类全限定名（如 com.example.UserService）
     * @return 源码文本；如果未找到返回 null
     */
    String getSourceCode(String className);

    /**
     * 获取多个类的源码（批量）
     *
     * @param classNames 类全限定名列表
     * @return 类名 -> 源码的映射
     */
    Map<String, String> getSourceCodes(List<String> classNames);
}
