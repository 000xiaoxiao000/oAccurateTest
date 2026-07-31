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
 * 代码质量分析工具
 * 基于覆盖率报告中的类级覆盖率和复杂度数据提供质量风险分析。
 */
public class CodeQualityTool {

    private static final Logger logger = LoggerFactory.getLogger(CodeQualityTool.class);

    private final AgentDataProvider dataProvider;

    public CodeQualityTool(AgentDataProvider dataProvider) {
        this.dataProvider = dataProvider;
    }

    @Tool("获取应用的代码质量风险报告，基于覆盖率和类级复杂度指标")
    public String getCodeQualityReport(@P("应用名称") String appName) {
        if (appName == null || appName.trim().isEmpty()) {
            return "错误：请提供应用名称";
        }
        try {
            String projectId = AgentContext.getCurrentProjectId();
            if (projectId == null) {
                return "错误：未找到项目上下文";
            }

            List<Map<String, Object>> apps = dataProvider.getApps(projectId);
            String appId = findAppIdByName(apps, appName.trim());
            if (appId == null) {
                return "未找到应用：" + appName;
            }

            // 获取覆盖率报告（作为代码质量的参考）
            List<Map<String, Object>> reports = dataProvider.getCoverageReports(appId);
            if (reports == null || reports.isEmpty()) {
                return "应用【" + appName + "】暂无数据，无法生成质量报告";
            }

            Map<String, Object> latestReport = reports.get(0);
            Double lineRate = parseDouble(latestReport.get("lineRate"));
            Double branchRate = parseDouble(latestReport.get("branchRate"));

            StringBuilder sb = new StringBuilder();
            sb.append("## ").append(appName).append(" - 代码质量分析报告\n\n");

            // 覆盖率质量评估
            sb.append("### 📊 测试质量\n");
            sb.append("- 行覆盖率: ").append(formatRate(lineRate)).append(" - ").append(qualityRating(lineRate, 80, 60));
            sb.append("\n- 分支覆盖率: ").append(formatRate(branchRate)).append(" - ").append(qualityRating(branchRate, 70, 50));
            sb.append("\n");

            // 获取类覆盖率详情
            String reportId = (String) latestReport.get("id");
            List<Map<String, Object>> lowCoverageClasses = dataProvider.getClassCoverageList(
                    reportId, 0.0, 0.2);

            if (lowCoverageClasses != null && !lowCoverageClasses.isEmpty()) {
                sb.append("\n### 🔴 低覆盖率类（<20%）\n");
                sb.append("这些类可能存在测试盲区，建议优先补充测试：\n\n");
                sb.append("| 类名 | 行覆盖率 | 分支覆盖率 | 复杂度 |\n");
                sb.append("|------|----------|------------|--------|\n");

                int count = 0;
                for (Map<String, Object> cls : lowCoverageClasses) {
                    if (count >= 10) break;
                    count++;

                    String className = (String) cls.getOrDefault("className", "");
                    if (className.contains(".")) {
                        className = className.substring(className.lastIndexOf(".") + 1);
                    }

                    sb.append("| ").append(className);
                    sb.append(" | ").append(formatRate(cls.get("lineRate")));
                    sb.append(" | ").append(formatRate(cls.get("branchRate")));
                    sb.append(" | ").append(cls.getOrDefault("complexity", "-"));
                    sb.append(" |\n");
                }
            }

            // 代码质量建议
            sb.append("\n### 💡 代码质量建议\n\n");
            sb.append("1. **提高测试覆盖率**：目标是行覆盖率>80%，分支覆盖率>70%\n");
            sb.append("2. **降低代码复杂度**：单个方法圈复杂度应<10\n");
            sb.append("3. **补齐质量数据源**：当前报告不包含重复率、坏味道、漏洞等静态扫描指标\n");
            sb.append("4. **静态扫描补充**：如需这些指标，请接入 SonarQube 等静态扫描数据源\n");
            sb.append("5. **定期代码审查**：建立code review机制\n");

            return sb.toString();
        } catch (Exception e) {
            logger.error("获取代码质量报告失败", e);
            return "获取代码质量报告失败：" + e.getMessage();
        }
    }

    @Tool("获取高复杂度方法列表，这些方法通常需要重构")
    public String getHighComplexityMethods(@P("应用名称") String appName) {
        if (appName == null || appName.trim().isEmpty()) {
            return "错误：请提供应用名称";
        }
        try {
            String projectId = AgentContext.getCurrentProjectId();
            if (projectId == null) {
                return "错误：未找到项目上下文";
            }

            List<Map<String, Object>> apps = dataProvider.getApps(projectId);
            String appId = findAppIdByName(apps, appName.trim());
            if (appId == null) {
                return "未找到应用：" + appName;
            }

            List<Map<String, Object>> reports = dataProvider.getCoverageReports(appId);
            if (reports == null || reports.isEmpty()) {
                return "应用【" + appName + "】暂无数据";
            }

            String reportId = (String) reports.get(0).get("id");
            List<Map<String, Object>> classes = dataProvider.getClassCoverageList(reportId, null, null);

            if (classes == null || classes.isEmpty()) {
                return "暂无类覆盖率数据";
            }

            // 筛选高复杂度的类
            StringBuilder sb = new StringBuilder();
            sb.append("## ").append(appName).append(" - 高复杂度类分析\n\n");
            sb.append("| 类名 | 复杂度 | 行覆盖率 | 建议 |\n");
            sb.append("|------|--------|----------|------|\n");

            int count = 0;
            for (Map<String, Object> cls : classes) {
                Object complexityObj = cls.get("complexity");
                if (complexityObj == null) continue;

                int complexity = ((Number) complexityObj).intValue();
                if (complexity >= 10) { // 复杂度>=10的类
                    if (count >= 15) break;
                    count++;

                    String className = (String) cls.getOrDefault("className", "");
                    if (className.contains(".")) {
                        className = className.substring(className.lastIndexOf(".") + 1);
                    }

                    Double lineRate = parseDouble(cls.get("lineRate"));
                    String suggestion = getSuggestion(complexity, lineRate);

                    sb.append("| ").append(className);
                    sb.append(" | ").append(complexity);
                    sb.append(" | ").append(formatRate(lineRate));
                    sb.append(" | ").append(suggestion);
                    sb.append(" |\n");
                }
            }

            if (count == 0) {
                return "✅ 未发现高复杂度类，代码质量良好！";
            }

            sb.append("\n> 复杂度≥10的类建议进行重构，拆分为更小的方法\n");
            return sb.toString();
        } catch (Exception e) {
            logger.error("获取高复杂度方法失败", e);
            return "获取高复杂度方法失败：" + e.getMessage();
        }
    }

    // ========== 辅助方法 ==========

    private String findAppIdByName(List<Map<String, Object>> apps, String appName) {
        if (apps == null) return null;
        for (Map<String, Object> app : apps) {
            String name = (String) app.getOrDefault("name", "");
            if (name.equalsIgnoreCase(appName) || name.toLowerCase().contains(appName.toLowerCase())) {
                return (String) app.get("id");
            }
        }
        return null;
    }

    private String formatRate(Object rate) {
        if (rate == null) return "-";
        if (rate instanceof Number) {
            return String.format("%.1f%%", ((Number) rate).doubleValue() * 100);
        }
        return rate.toString();
    }

    private Double parseDouble(Object obj) {
        if (obj == null) return null;
        if (obj instanceof Number) return ((Number) obj).doubleValue();
        try {
            return Double.parseDouble(obj.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String qualityRating(Double rate, double excellent, double good) {
        if (rate == null) return "⚪ 未知";
        double percentage = rate * 100;
        if (percentage >= excellent) return "✅ 优秀";
        if (percentage >= good) return "🟡 良好";
        return "🔴 需要改进";
    }

    private String getSuggestion(int complexity, Double lineRate) {
        if (complexity >= 20) return "🔴 强烈建议重构";
        if (complexity >= 15) {
            if (lineRate != null && lineRate < 0.5) return "🔴 重构+补充测试";
            return "🟡 建议重构";
        }
        return "🟢 可接受";
    }
}
