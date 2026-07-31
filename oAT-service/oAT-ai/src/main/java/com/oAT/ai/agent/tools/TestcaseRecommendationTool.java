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
 * 测试用例推荐工具
 * 基于代码覆盖率和调用链分析，智能推荐需要补充测试的用例
 */
public class TestcaseRecommendationTool {

    private static final Logger logger = LoggerFactory.getLogger(TestcaseRecommendationTool.class);

    private final AgentDataProvider dataProvider;

    public TestcaseRecommendationTool(AgentDataProvider dataProvider) {
        this.dataProvider = dataProvider;
    }

    @Tool("获取应用的测试用例推荐，基于覆盖率分析找出未充分测试的代码")
    public String recommendTestcases(@P("应用名称") String appName) {
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

            // 获取最新覆盖率报告
            List<Map<String, Object>> reports = dataProvider.getCoverageReports(appId);
            if (reports == null || reports.isEmpty()) {
                return "应用【" + appName + "】暂无覆盖率报告，无法生成测试推荐";
            }

            Map<String, Object> latestReport = reports.get(0);
            String reportId = (String) latestReport.get("id");

            StringBuilder sb = new StringBuilder();
            sb.append("## ").append(appName).append(" - 测试用例推荐\n\n");
            sb.append("基于最新覆盖率报告（").append(latestReport.get("createTime")).append("）分析\n\n");

            // 获取低覆盖率的类
            List<Map<String, Object>> lowCoverageClasses = dataProvider.getClassCoverageList(
                    reportId, 0.0, 0.3); // 覆盖率低于30%的类

            if (lowCoverageClasses == null || lowCoverageClasses.isEmpty()) {
                sb.append("未查询到覆盖率低于30%的类级明细。\n\n");
                sb.append("> 这不等于所有类都已达标；可能是类级覆盖率明细未入库、筛选条件未命中，或当前报告只包含汇总指标。请结合总体覆盖率判断风险。\n");
            } else {
                sb.append("### 🔴 需要补充测试的类（覆盖率<30%）\n\n");
                sb.append("| 类名 | 行覆盖率 | 分支覆盖率 | 建议优先级 |\n");
                sb.append("|------|----------|------------|------------|\n");

                int count = 0;
                for (Map<String, Object> cls : lowCoverageClasses) {
                    if (count >= 15) break;
                    count++;

                    String className = (String) cls.getOrDefault("className", "");
                    Double lineRate = parseDouble(cls.get("lineRate"));
                    Double branchRate = parseDouble(cls.get("branchRate"));

                    // 简单类名
                    if (className.contains(".")) {
                        className = className.substring(className.lastIndexOf(".") + 1);
                    }

                    // 优先级判断
                    String priority = "中";
                    String icon = "🟡";
                    if (lineRate != null && lineRate < 0.1) {
                        priority = "高";
                        icon = "🔴";
                    } else if (lineRate != null && lineRate < 0.5) {
                        priority = "中";
                        icon = "🟡";
                    } else {
                        priority = "低";
                        icon = "🟢";
                    }

                    sb.append("| ").append(icon).append(" ").append(className);
                    sb.append(" | ").append(formatRate(lineRate));
                    sb.append(" | ").append(formatRate(branchRate));
                    sb.append(" | ").append(priority);
                    sb.append(" |\n");
                }

                if (lowCoverageClasses.size() > 15) {
                    sb.append("| ... | ... | ... | ... |\n");
                }
            }

            // 获取中等覆盖率的类
            List<Map<String, Object>> mediumCoverageClasses = dataProvider.getClassCoverageList(
                    reportId, 0.3, 0.6); // 覆盖率30%-60%

            if (mediumCoverageClasses != null && !mediumCoverageClasses.isEmpty()) {
                sb.append("\n### 🟡 建议优化测试的类（覆盖率30%-60%）\n\n");
                sb.append("以下类已有一定测试覆盖，但还可以进一步完善：\n\n");

                int count = 0;
                for (Map<String, Object> cls : mediumCoverageClasses) {
                    if (count >= 10) break;
                    count++;

                    String className = (String) cls.getOrDefault("className", "");
                    Double lineRate = parseDouble(cls.get("lineRate"));

                    if (className.contains(".")) {
                        className = className.substring(className.lastIndexOf(".") + 1);
                    }

                    sb.append(count).append(". ").append(className).append(" (").append(formatRate(lineRate)).append(")\n");
                }
            }

            sb.append("\n---\n");
            sb.append("### 💡 测试建议\n\n");
            sb.append("1. **优先覆盖核心业务逻辑**：Service层和Controller层的类\n");
            sb.append("2. **关注分支覆盖**：不仅要看行覆盖率，还要覆盖各种条件分支\n");
            sb.append("3. **边界条件测试**：特别注意空值、异常值等边界情况\n");
            sb.append("4. **集成测试**：对于涉及外部调用的接口，补充集成测试\n");

            return sb.toString();
        } catch (Exception e) {
            logger.error("获取测试用例推荐失败", e);
            return "获取测试用例推荐失败：" + e.getMessage();
        }
    }

    @Tool("获取指定接口的测试建议，包括需要覆盖的场景和边界条件")
    public String getEndpointTestSuggestions(@P("应用名称") String appName, @P("接口路径或类名") String endpoint) {
        if (appName == null || appName.trim().isEmpty()) {
            return "错误：请提供应用名称";
        }
        if (endpoint == null || endpoint.trim().isEmpty()) {
            return "错误：请提供接口路径或类名";
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

            // 搜索代码关系
            Map<String, Object> codeRelation = dataProvider.searchCodeRelation(projectId, endpoint.trim());
            if (codeRelation == null || codeRelation.isEmpty()) {
                return "未找到与 \"" + endpoint + "\" 相关的代码信息";
            }

            StringBuilder sb = new StringBuilder();
            sb.append("## ").append(appName).append(" - ").append(endpoint).append(" 测试建议\n\n");

            // 接口信息
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> interfaces = (List<Map<String, Object>>) codeRelation.get("interfaces");
            if (interfaces != null && !interfaces.isEmpty()) {
                sb.append("### 📍 接口信息\n\n");
                for (Map<String, Object> iface : interfaces) {
                    String name = (String) iface.get("name");
                    String app = (String) iface.get("appName");
                    @SuppressWarnings("unchecked")
                    List<String> methods = (List<String>) iface.get("methods");

                    sb.append("- 类名: `").append(name).append("`\n");
                    sb.append("- 所属应用: ").append(app).append("\n");
                    if (methods != null && !methods.isEmpty()) {
                        sb.append("- 接口方法:\n");
                        for (String method : methods) {
                            sb.append("  - `").append(method).append("`\n");
                        }
                    }
                }

                sb.append("\n### 🧪 推荐测试场景\n\n");
                int scenarioNum = 1;

                for (Map<String, Object> iface : interfaces) {
                    @SuppressWarnings("unchecked")
                    List<String> methods = (List<String>) iface.get("methods");
                    if (methods != null) {
                        for (String method : methods) {
                            sb.append("**场景").append(scenarioNum++).append(": ").append(method).append("**\n");
                            sb.append("- [ ] 正常请求测试（有效参数）\n");
                            sb.append("- [ ] 边界值测试（最大/最小值）\n");
                            sb.append("- [ ] 异常参数测试（空值、非法格式）\n");
                            sb.append("- [ ] 权限测试（未登录、无权限）\n");
                            sb.append("- [ ] 并发测试（多个请求同时到达）\n\n");
                        }
                    }
                }
            }

            // 类信息
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> classes = (List<Map<String, Object>>) codeRelation.get("classes");
            if (classes != null && !classes.isEmpty()) {
                sb.append("### 📦 相关类\n\n");
                for (Map<String, Object> cls : classes) {
                    String name = (String) cls.get("name");
                    String simpleName = (String) cls.get("simpleName");
                    String pkg = (String) cls.get("package");

                    sb.append("- `").append(name).append("`\n");
                    sb.append("  - 类型: ").append(inferClassType(simpleName)).append("\n");
                }

                sb.append("\n### 💡 测试建议\n\n");
                sb.append("1. **单元测试**：为每个public方法编写单元测试\n");
                sb.append("2. **Mock外部依赖**：使用Mock框架隔离外部调用\n");
                sb.append("3. **覆盖异常分支**：确保异常处理逻辑也被测试到\n");
                sb.append("4. **验证业务逻辑**：重点测试核心业务计算和判断逻辑\n");
            }

            return sb.toString();
        } catch (Exception e) {
            logger.error("获取接口测试建议失败", e);
            return "获取接口测试建议失败：" + e.getMessage();
        }
    }

    @Tool("获取覆盖率增长建议，帮助提升整体覆盖率")
    public String getCoverageImprovementSuggestions(@P("应用名称") String appName) {
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
                return "应用【" + appName + "】暂无覆盖率报告";
            }

            Map<String, Object> latestReport = reports.get(0);
            Double lineRate = parseDouble(latestReport.get("lineRate"));
            Double branchRate = parseDouble(latestReport.get("branchRate"));
            Double methodRate = parseDouble(latestReport.get("methodRate"));

            StringBuilder sb = new StringBuilder();
            sb.append("## ").append(appName).append(" - 覆盖率提升建议\n\n");
            sb.append("### 📊 当前覆盖率\n");
            sb.append("- 行覆盖率: **").append(formatRate(lineRate)).append("**\n");
            sb.append("- 分支覆盖率: **").append(formatRate(branchRate)).append("**\n");
            sb.append("- 方法覆盖率: **").append(formatRate(methodRate)).append("**\n");

            sb.append("\n### 🎯 提升目标\n\n");

            // 根据当前覆盖率给出建议
            if (lineRate != null && lineRate < 0.3) {
                sb.append("#### 第一阶段：达到30%覆盖率\n");
                sb.append("1. 为核心Controller编写基础测试\n");
                sb.append("2. 为核心Service的主要方法编写测试\n");
                sb.append("3. 使用代码生成工具快速生成测试框架\n\n");
            }

            if (lineRate == null || lineRate < 0.6) {
                sb.append("#### 第二阶段：达到60%覆盖率\n");
                sb.append("1. 补充Service层的分支测试\n");
                sb.append("2. 为工具类和辅助类添加测试\n");
                sb.append("3. 增加异常场景的测试覆盖\n\n");
            }

            if (lineRate == null || lineRate < 0.8) {
                sb.append("#### 第三阶段：达到80%覆盖率\n");
                sb.append("1. 覆盖所有边界条件\n");
                sb.append("2. 补充集成测试\n");
                sb.append("3. 添加并发和性能测试\n\n");
            }

            sb.append("### 💡 通用建议\n\n");
            sb.append("1. **优先级排序**：优先测试业务价值高的代码\n");
            sb.append("2. **测试质量**：不要为了覆盖率而测试，要确保测试有意义\n");
            sb.append("3. **持续集成**：在CI流程中加入覆盖率检查\n");
            sb.append("4. **定期review**：定期审查覆盖率报告，找出遗漏\n");
            sb.append("5. **团队协作**：将覆盖率目标纳入团队KPI\n");

            return sb.toString();
        } catch (Exception e) {
            logger.error("获取覆盖率提升建议失败", e);
            return "获取覆盖率提升建议失败：" + e.getMessage();
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

    private String inferClassType(String simpleName) {
        if (simpleName == null) return "未知";
        String lower = simpleName.toLowerCase();
        if (lower.contains("controller") || lower.contains("action")) return "Controller层";
        if (lower.contains("service") || lower.contains("manager")) return "Service层";
        if (lower.contains("dao") || lower.contains("mapper") || lower.contains("repository")) return "DAO层";
        if (lower.contains("util") || lower.contains("helper")) return "工具类";
        if (lower.contains("model") || lower.contains("entity") || lower.contains("dto")) return "数据模型";
        return "普通类";
    }
}
