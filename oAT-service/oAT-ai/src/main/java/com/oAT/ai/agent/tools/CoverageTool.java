package com.oAT.ai.agent.tools;

import com.oAT.ai.agent.AgentContext;
import com.oAT.ai.agent.AgentDataProvider;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;

/**
 * 覆盖率查询工具
 * 提供覆盖率报告、类覆盖率、趋势等查询能力
 */
public class CoverageTool {

    private static final Logger logger = LoggerFactory.getLogger(CoverageTool.class);

    private final AgentDataProvider dataProvider;

    public CoverageTool(AgentDataProvider dataProvider) {
        this.dataProvider = dataProvider;
    }

    @Tool("获取整个项目的覆盖率概览。当用户问项目覆盖率、代码覆盖情况等总体问题时使用此工具。")
    public String getProjectCoverageOverview() {
        String projectId = AgentContext.getCurrentProjectId();
        if (projectId == null) {
            return "错误：未找到项目上下文";
        }
        try {
            // 获取项目下的所有应用
            List<Map<String, Object>> apps = dataProvider.getApps(projectId);
            if (apps == null || apps.isEmpty()) {
                return "当前项目下没有应用";
            }

            StringBuilder sb = new StringBuilder();
            sb.append("## 项目覆盖率概览\n\n");
            sb.append("项目共有 ").append(apps.size()).append(" 个应用：\n\n");

            int appWithReport = 0;
            int onlineApps = 0;

            for (Map<String, Object> app : apps) {
                String appName = (String) app.getOrDefault("name", "未知");
                String appId = (String) app.get("id");
                boolean isOnline = Boolean.TRUE.equals(app.get("online"));

                if (isOnline) {
                    onlineApps++;
                }

                sb.append("### ").append(appName);
                if (isOnline) {
                    sb.append(" [在线]");
                }
                sb.append("\n");

                // 获取该应用的覆盖率报告
                try {
                    List<Map<String, Object>> reports = dataProvider.getCoverageReports(appId);
                    if (reports != null && !reports.isEmpty()) {
                        appWithReport++;
                        Map<String, Object> latestReport = reports.get(0);
                        sb.append("- 最新报告时间: ").append(latestReport.getOrDefault("createTime", "-")).append("\n");
                        sb.append("- 行覆盖率: ").append(formatRate(latestReport.get("lineRate"))).append("\n");
                        sb.append("- 分支覆盖率: ").append(formatRate(latestReport.get("branchRate"))).append("\n");
                        sb.append("- 方法覆盖率: ").append(formatRate(latestReport.get("methodRate"))).append("\n");
                        sb.append("- 历史报告数: ").append(reports.size()).append(" 份\n");
                    } else {
                        sb.append("- 暂无覆盖率报告\n");
                    }
                } catch (Exception e) {
                    sb.append("- 获取覆盖率失败: ").append(e.getMessage()).append("\n");
                }
                sb.append("\n");
            }

            sb.append("---\n");
            sb.append("**统计摘要**：\n");
            sb.append("- 在线应用：").append(onlineApps).append("/").append(apps.size()).append("\n");
            sb.append("- 有覆盖率报告的应用：").append(appWithReport).append("/").append(apps.size()).append("\n");

            return sb.toString();
        } catch (Exception e) {
            logger.error("获取项目覆盖率概览失败", e);
            return "获取项目覆盖率概览失败：" + e.getMessage();
        }
    }

    @Tool("根据应用名称获取该应用的最新覆盖率报告。当用户问某个具体应用的覆盖率时使用。")
    public String getAppCoverageReport(@P("应用名称") String appName) {
        if (appName == null || appName.trim().isEmpty()) {
            return "错误：请提供应用名称";
        }
        try {
            String projectId = AgentContext.getCurrentProjectId();
            if (projectId == null) {
                return "错误：未找到项目上下文";
            }

            // 1. 根据应用名称找到应用ID
            List<Map<String, Object>> apps = dataProvider.getApps(projectId);
            String appId = null;
            String matchedName = null;
            if (apps != null) {
                for (Map<String, Object> app : apps) {
                    String name = (String) app.getOrDefault("name", "");
                    if (name.equalsIgnoreCase(appName.trim()) ||
                        name.toLowerCase().contains(appName.trim().toLowerCase())) {
                        appId = (String) app.get("id");
                        matchedName = name;
                        break;
                    }
                }
            }
            if (appId == null) {
                return "未找到应用：" + appName + "。可用的应用有：" + getAppNameList(apps);
            }

            // 2. 获取该应用的覆盖率报告列表
            List<Map<String, Object>> reports = dataProvider.getCoverageReports(appId);
            if (reports == null || reports.isEmpty()) {
                return "应用【" + matchedName + "】暂无覆盖率报告";
            }

            // 3. 获取最新报告（第一条）
            Map<String, Object> latestReport = reports.get(0);
            String reportId = (String) latestReport.get("id");

            // 4. 获取报告详情
            Map<String, Object> report = dataProvider.getCoverageReportDetail(reportId);
            if (report == null || report.isEmpty()) {
                return "获取报告详情失败";
            }

            StringBuilder sb = new StringBuilder();
            sb.append("## ").append(matchedName).append(" - 最新覆盖率报告\n\n");
            sb.append("- 报告时间: ").append(report.getOrDefault("createTime", "")).append("\n");
            sb.append("- 报告类型: ").append(formatReportType(report.get("reportType"))).append("\n");
            sb.append("\n### 覆盖率统计\n");
            sb.append("- 行覆盖率: **").append(formatRate(report.get("lineRate"))).append("**\n");
            sb.append("- 分支覆盖率: **").append(formatRate(report.get("branchRate"))).append("**\n");
            sb.append("- 方法覆盖率: **").append(formatRate(report.get("methodRate"))).append("**\n");
            sb.append("- 类数量: ").append(report.getOrDefault("classCount", 0)).append("\n");
            sb.append("- 方法数量: ").append(report.getOrDefault("methodCount", 0)).append("\n");

            if (reports.size() > 1) {
                sb.append("\n> 该应用共有 ").append(reports.size()).append(" 份历史报告\n");
            }

            return sb.toString();
        } catch (Exception e) {
            logger.error("获取应用覆盖率报告失败", e);
            return "获取应用覆盖率报告失败：" + e.getMessage();
        }
    }

    @Tool("根据应用名称获取该应用的覆盖率趋势变化")
    public String getAppCoverageTrend(@P("应用名称") String appName, @P("返回数量限制，默认10") int limit) {
        if (appName == null || appName.trim().isEmpty()) {
            return "错误：请提供应用名称";
        }
        if (limit <= 0) {
            limit = 10;
        }
        try {
            String projectId = AgentContext.getCurrentProjectId();
            if (projectId == null) {
                return "错误：未找到项目上下文";
            }

            // 根据应用名称找到应用ID
            List<Map<String, Object>> apps = dataProvider.getApps(projectId);
            String appId = null;
            String matchedName = null;
            if (apps != null) {
                for (Map<String, Object> app : apps) {
                    String name = (String) app.getOrDefault("name", "");
                    if (name.equalsIgnoreCase(appName.trim()) ||
                        name.toLowerCase().contains(appName.trim().toLowerCase())) {
                        appId = (String) app.get("id");
                        matchedName = name;
                        break;
                    }
                }
            }
            if (appId == null) {
                return "未找到应用：" + appName;
            }

            List<Map<String, Object>> trends = dataProvider.getCoverageTrend(appId, limit);
            if (trends == null || trends.isEmpty()) {
                return "应用【" + matchedName + "】暂无覆盖率趋势数据";
            }

            StringBuilder sb = new StringBuilder();
            sb.append("## ").append(matchedName).append(" - 覆盖率趋势\n\n");
            sb.append("| 时间 | 行覆盖率 | 分支覆盖率 | 方法覆盖率 |\n");
            sb.append("|------|----------|------------|------------|\n");
            for (Map<String, Object> trend : trends) {
                sb.append("| ").append(trend.getOrDefault("createTime", "-"));
                sb.append(" | ").append(formatRate(trend.get("lineRate")));
                sb.append(" | ").append(formatRate(trend.get("branchRate")));
                sb.append(" | ").append(formatRate(trend.get("methodRate")));
                sb.append(" |\n");
            }
            return sb.toString();
        } catch (Exception e) {
            logger.error("获取覆盖率趋势失败", e);
            return "获取覆盖率趋势失败：" + e.getMessage();
        }
    }

    @Tool("获取指定应用的覆盖率报告列表")
    public String getCoverageReports(@P("应用ID") String appId) {
        if (appId == null || appId.trim().isEmpty()) {
            return "错误：请提供应用ID";
        }
        try {
            List<Map<String, Object>> reports = dataProvider.getCoverageReports(appId);
            if (reports == null || reports.isEmpty()) {
                return "该应用暂无覆盖率报告";
            }
            StringBuilder sb = new StringBuilder();
            sb.append("共找到 ").append(reports.size()).append(" 份覆盖率报告：\n\n");
            int count = 0;
            for (Map<String, Object> report : reports) {
                count++;
                sb.append(count).append(". 报告ID: ").append(report.getOrDefault("id", ""));
                sb.append("\n   - 创建时间: ").append(report.getOrDefault("createTime", ""));
                sb.append("\n   - 行覆盖率: ").append(formatRate(report.get("lineRate")));
                sb.append("\n   - 分支覆盖率: ").append(formatRate(report.get("branchRate")));
                sb.append("\n   - 方法覆盖率: ").append(formatRate(report.get("methodRate")));
                sb.append("\n");
                if (count >= 10) {
                    sb.append("... 仅显示最近10份报告\n");
                    break;
                }
            }
            return sb.toString();
        } catch (Exception e) {
            logger.error("获取覆盖率报告列表失败", e);
            return "获取覆盖率报告列表失败：" + e.getMessage();
        }
    }

    @Tool("获取覆盖率报告的详细信息")
    public String getCoverageReportDetail(@P("报告ID") String reportId) {
        if (reportId == null || reportId.trim().isEmpty()) {
            return "错误：请提供报告ID";
        }
        try {
            Map<String, Object> report = dataProvider.getCoverageReportDetail(reportId);
            if (report == null || report.isEmpty()) {
                return "未找到报告信息，ID: " + reportId;
            }
            StringBuilder sb = new StringBuilder();
            sb.append("## 覆盖率报告详情\n\n");
            sb.append("- 报告ID: ").append(report.getOrDefault("id", "")).append("\n");
            sb.append("- 应用名称: ").append(report.getOrDefault("appName", "")).append("\n");
            sb.append("- 创建时间: ").append(report.getOrDefault("createTime", "")).append("\n");
            sb.append("- 报告类型: ").append(formatReportType(report.get("reportType"))).append("\n");
            sb.append("\n### 覆盖率统计\n");
            sb.append("- 行覆盖率: ").append(formatRate(report.get("lineRate"))).append("\n");
            sb.append("- 分支覆盖率: ").append(formatRate(report.get("branchRate"))).append("\n");
            sb.append("- 方法覆盖率: ").append(formatRate(report.get("methodRate"))).append("\n");
            sb.append("- 类数量: ").append(report.getOrDefault("classCount", 0)).append("\n");
            sb.append("- 方法数量: ").append(report.getOrDefault("methodCount", 0)).append("\n");
            return sb.toString();
        } catch (Exception e) {
            logger.error("获取覆盖率报告详情失败", e);
            return "获取覆盖率报告详情失败：" + e.getMessage();
        }
    }

    @Tool("获取类覆盖率列表，可按覆盖率范围筛选")
    public String getClassCoverageList(
            @P("报告ID") String reportId,
            @P("最小覆盖率(0-1之间的小数，如0.5表示50%)") Double minRate,
            @P("最大覆盖率(0-1之间的小数)") Double maxRate) {
        if (reportId == null || reportId.trim().isEmpty()) {
            return "错误：请提供报告ID";
        }
        try {
            List<Map<String, Object>> classes = dataProvider.getClassCoverageList(reportId, minRate, maxRate);
            if (classes == null || classes.isEmpty()) {
                String filter = "";
                if (minRate != null || maxRate != null) {
                    filter = "（筛选条件：";
                    if (minRate != null) filter += "最小" + (minRate * 100) + "%";
                    if (maxRate != null) filter += " 最大" + (maxRate * 100) + "%";
                    filter += "）";
                }
                return "未找到符合条件的类覆盖率数据" + filter + "。这只能说明当前类级明细查询没有返回结果，不能据此推断不存在低覆盖类，也不能推断所有类都低覆盖；请结合报告总体覆盖率或检查类级覆盖率数据是否已入库。";
            }
            StringBuilder sb = new StringBuilder();
            sb.append("找到 ").append(classes.size()).append(" 个类：\n\n");
            sb.append("| 类名 | 行覆盖率 | 分支覆盖率 | 复杂度 |\n");
            sb.append("|------|----------|------------|--------|\n");
            int count = 0;
            for (Map<String, Object> cls : classes) {
                count++;
                String className = (String) cls.getOrDefault("className", "");
                // 只显示简单类名
                if (className.contains(".")) {
                    className = className.substring(className.lastIndexOf(".") + 1);
                }
                sb.append("| ").append(className);
                sb.append(" | ").append(formatRate(cls.get("lineRate")));
                sb.append(" | ").append(formatRate(cls.get("branchRate")));
                sb.append(" | ").append(cls.getOrDefault("complexity", "-"));
                sb.append(" |\n");
                if (count >= 20) {
                    sb.append("| ... | ... | ... | ... |\n");
                    break;
                }
            }
            return sb.toString();
        } catch (Exception e) {
            logger.error("获取类覆盖率列表失败", e);
            return "获取类覆盖率列表失败：" + e.getMessage();
        }
    }

    @Tool("查询覆盖率低于指定阈值的类")
    public String getLowCoverageClasses(
            @P("报告ID") String reportId,
            @P("覆盖率阈值(0-1之间的小数，默认0.5表示50%)") Double threshold) {
        if (threshold == null) {
            threshold = 0.5;
        }
        return getClassCoverageList(reportId, 0.0, threshold);
    }

    private String formatRate(Object rate) {
        if (rate == null) {
            return "-";
        }
        if (rate instanceof Number) {
            return String.format("%.2f%%", ((Number) rate).doubleValue() * 100);
        }
        return rate.toString();
    }

    private String formatReportType(Object type) {
        if (type == null) return "未知";
        int typeInt = ((Number) type).intValue();
        switch (typeInt) {
            case 0: return "全量覆盖率";
            case 1: return "增量覆盖率";
            default: return "其他";
        }
    }

    private String getAppNameList(List<Map<String, Object>> apps) {
        if (apps == null || apps.isEmpty()) {
            return "无";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < apps.size(); i++) {
            if (i > 0) sb.append(", ");
            sb.append(apps.get(i).getOrDefault("name", ""));
        }
        return sb.toString();
    }
}
