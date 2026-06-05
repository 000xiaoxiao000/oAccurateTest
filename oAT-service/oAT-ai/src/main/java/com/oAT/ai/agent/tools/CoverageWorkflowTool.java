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
 * 覆盖率工作流工具
 * 提供覆盖率生成、任务查询、报告下载等操作能力
 */
public class CoverageWorkflowTool {

    private static final Logger logger = LoggerFactory.getLogger(CoverageWorkflowTool.class);

    private final AgentDataProvider dataProvider;

    public CoverageWorkflowTool(AgentDataProvider dataProvider) {
        this.dataProvider = dataProvider;
    }

    @Tool("自动拉取代码并生成覆盖率报告。当用户要求'生成覆盖率'、'拉取代码生成报告'、'自动生成覆盖率'时使用此工具。")
    public String generateCoverageReport(
            @P("应用名称") String appName,
            @P("版本号，可选") String versionNumber,
            @P("分支名，默认master") String branch,
            @P("Commit ID，可选，默认最新") String commitId) {

        String projectId = AgentContext.getCurrentProjectId();
        if (projectId == null) {
            return "错误：未找到项目上下文";
        }

        try {
            List<Map<String, Object>> apps = dataProvider.getApps(projectId);
            String appId = null;
            String matchedName = null;
            Map<String, Object> targetApp = null;

            if (apps != null) {
                for (Map<String, Object> app : apps) {
                    String name = (String) app.getOrDefault("name", "");
                    if (name.equalsIgnoreCase(appName.trim()) ||
                            name.toLowerCase().contains(appName.trim().toLowerCase())) {
                        appId = (String) app.get("id");
                        matchedName = name;
                        targetApp = app;
                        break;
                    }
                }
            }

            if (appId == null) {
                return "未找到应用：" + appName + "。可用的应用有：" + getAppNameList(apps);
            }

            Map<String, Object> appDetail = dataProvider.getAppDetail(appId);
            if (appDetail == null || appDetail.isEmpty()) {
                return "获取应用详情失败，无法启动覆盖率生成";
            }

            String repoUrl = (String) appDetail.get("repoUrl");
            if (repoUrl == null || repoUrl.trim().isEmpty()) {
                return "应用【" + matchedName + "】未配置 Git 仓库地址，无法拉取代码。请先在应用设置中配置仓库信息。";
            }

            if (versionNumber == null || versionNumber.trim().isEmpty()) {
                versionNumber = (String) targetApp.getOrDefault("currentVersion", "v1.0.0");
            }
            if (branch == null || branch.trim().isEmpty()) {
                branch = "master";
            }

            String jobId = dataProvider.startCoverageGenerationJob(appId, versionNumber, branch, commitId);

            if (jobId == null || jobId.trim().isEmpty()) {
                return "启动覆盖率生成任务失败，请检查应用配置和 Git 权限。";
            }

            StringBuilder sb = new StringBuilder();
            sb.append("## 覆盖率生成任务已启动\n\n");
            sb.append("- 应用：**").append(matchedName).append("**\n");
            sb.append("- 版本：").append(versionNumber).append("\n");
            sb.append("- 分支：").append(branch).append("\n");
            if (commitId != null && !commitId.trim().isEmpty()) {
                sb.append("- Commit：").append(commitId).append("\n");
            }
            sb.append("- 任务ID：`").append(jobId).append("`\n");
            sb.append("\n### 执行流程\n");
            sb.append("1. 正在拉取代码...\n");
            sb.append("2. 解析源码包...\n");
            sb.append("3. 分析覆盖率数据...\n");
            sb.append("4. 生成报告...\n");
            sb.append("\n> 任务通常需要 1-3 分钟完成，请稍等。\n");
            sb.append("> 你可以说「查询任务进度」或「任务 ").append(jobId).append(" 进度如何」来查看实时状态。");

            return sb.toString();
        } catch (Exception e) {
            logger.error("生成覆盖率报告失败", e);
            return "生成覆盖率报告失败：" + e.getMessage();
        }
    }

    @Tool("查询覆盖率生成任务的进度。当用户问'任务进度'、'生成好了吗'、'任务状态'时使用此工具。")
    public String queryJobStatus(@P("任务ID") String jobId) {
        if (jobId == null || jobId.trim().isEmpty()) {
            return "错误：请提供任务ID";
        }

        try {
            Map<String, Object> job = dataProvider.getJobStatus(jobId);
            if (job == null || job.isEmpty()) {
                return "未找到任务：" + jobId;
            }

            String status = (String) job.getOrDefault("status", "UNKNOWN");
            Object progressObj = job.get("progress");
            int progress = progressObj instanceof Number ? ((Number) progressObj).intValue() : 0;
            String message = (String) job.get("message");
            String reportId = (String) job.get("reportId");

            StringBuilder sb = new StringBuilder();
            sb.append("## 任务进度查询\n\n");
            sb.append("- 任务ID：`").append(jobId).append("`\n");
            sb.append("- 状态：**").append(formatJobStatus(status)).append("**\n");
            sb.append("- 进度：").append(progress).append("%\n");

            if (message != null && !message.trim().isEmpty()) {
                sb.append("- 详情：").append(message).append("\n");
            }

            switch (status.toUpperCase()) {
                case "COMPLETED":
                case "SUCCESS":
                    sb.append("\n### 任务已完成\n");
                    if (reportId != null && !reportId.trim().isEmpty()) {
                        sb.append("- 报告ID：`").append(reportId).append("`\n");
                        sb.append("\n你可以说「下载报告」或「导出覆盖率报告 ").append(reportId).append("」来获取完整报告。");
                    } else {
                        sb.append("\n报告生成成功，你可以说「查看最新覆盖率」来查看详情。");
                    }
                    break;
                case "RUNNING":
                case "IN_PROGRESS":
                    sb.append("\n> 任务正在执行中，请稍等...");
                    break;
                case "FAILED":
                case "ERROR":
                    sb.append("\n### 任务失败\n");
                    sb.append("可能原因：\n");
                    sb.append("- Git 仓库访问失败（检查账号密码/Token）\n");
                    sb.append("- 分支或 Commit 不存在\n");
                    sb.append("- 源码包解析失败\n");
                    sb.append("\n建议：检查应用 Git 配置后重新生成。");
                    break;
                case "PENDING":
                case "QUEUED":
                    sb.append("\n> 任务排队中，即将开始执行...");
                    break;
                default:
                    sb.append("\n状态未知，请稍后再试或联系管理员。");
            }

            return sb.toString();
        } catch (Exception e) {
            logger.error("查询任务状态失败", e);
            return "查询任务状态失败：" + e.getMessage();
        }
    }

    @Tool("下载覆盖率报告（导出为 Excel）。当用户要求'下载报告'、'导出报告'、'获取报告文件'时使用此工具。")
    public String downloadCoverageReport(
            @P("报告ID，可选，默认最新") String reportId,
            @P("应用名称，可选") String appName) {

        String projectId = AgentContext.getCurrentProjectId();
        if (projectId == null) {
            return "错误：未找到项目上下文";
        }

        try {
            if ((reportId == null || reportId.trim().isEmpty()) && appName != null && !appName.trim().isEmpty()) {
                List<Map<String, Object>> apps = dataProvider.getApps(projectId);
                String appId = null;

                if (apps != null) {
                    for (Map<String, Object> app : apps) {
                        String name = (String) app.getOrDefault("name", "");
                        if (name.equalsIgnoreCase(appName.trim()) ||
                                name.toLowerCase().contains(appName.trim().toLowerCase())) {
                            appId = (String) app.get("id");
                            break;
                        }
                    }
                }

                if (appId != null) {
                    List<Map<String, Object>> reports = dataProvider.getCoverageReports(appId);
                    if (reports != null && !reports.isEmpty()) {
                        reportId = (String) reports.get(0).get("id");
                    }
                }
            }

            if (reportId == null || reportId.trim().isEmpty()) {
                return "错误：未找到可用的覆盖率报告。请提供报告ID或应用名称。";
            }

            String downloadUrl = dataProvider.generateReportDownloadUrl(reportId);

            if (downloadUrl == null || downloadUrl.trim().isEmpty()) {
                return "生成报告下载链接失败，报告ID：" + reportId;
            }

            StringBuilder sb = new StringBuilder();
            sb.append("## 覆盖率报告下载\n\n");
            sb.append("- 报告ID：`").append(reportId).append("`\n");
            sb.append("- 格式：Excel (.xlsx)\n");
            sb.append("- 下载链接：[点击下载](").append(downloadUrl).append(")\n\n");
            sb.append("> 报告包含完整的类覆盖率、方法覆盖率、分支覆盖率等详细数据。");

            return sb.toString();
        } catch (Exception e) {
            logger.error("下载覆盖率报告失败", e);
            return "下载覆盖率报告失败：" + e.getMessage();
        }
    }

    @Tool("检查应用的 Git 仓库配置是否正确，验证是否可以拉取代码。")
    public String checkGitConfiguration(
            @P("应用名称") String appName,
            @P("分支名，默认master") String branch) {

        String projectId = AgentContext.getCurrentProjectId();
        if (projectId == null) {
            return "错误：未找到项目上下文";
        }

        try {
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

            Map<String, Object> appDetail = dataProvider.getAppDetail(appId);
            if (appDetail == null || appDetail.isEmpty()) {
                return "获取应用详情失败";
            }

            String repoUrl = (String) appDetail.get("repoUrl");
            String username = (String) appDetail.get("gitUsername");
            String password = (String) appDetail.get("gitPassword");

            if (branch == null || branch.trim().isEmpty()) {
                branch = "master";
            }

            StringBuilder sb = new StringBuilder();
            sb.append("## Git 配置检查 - ").append(matchedName).append("\n\n");

            if (repoUrl == null || repoUrl.trim().isEmpty()) {
                sb.append("### 配置不完整\n\n");
                sb.append("- 仓库地址：**未配置**\n");
                sb.append("\n请在应用设置中配置 Git 仓库地址后再生成覆盖率。");
                return sb.toString();
            }

            sb.append("- 仓库地址：`").append(maskSensitiveUrl(repoUrl)).append("`\n");
            sb.append("- 用户名：").append(username != null && !username.trim().isEmpty() ? "已配置" : "未配置").append("\n");
            sb.append("- 密码/Token：").append(password != null && !password.trim().isEmpty() ? "已配置" : "未配置").append("\n");
            sb.append("- 测试分支：").append(branch).append("\n\n");

            boolean isValid = dataProvider.validateGitAccess(repoUrl, username, password, branch);

            if (isValid) {
                sb.append("### 配置验证通过\n\n");
                sb.append("Git 仓库连接正常，可以正常拉取代码。\n");
                sb.append("\n你现在可以说「生成覆盖率」来开始生成覆盖率报告。");
            } else {
                sb.append("### 配置验证失败\n\n");
                sb.append("可能原因：\n");
                sb.append("- 仓库地址错误\n");
                sb.append("- 用户名或密码/Token 错误\n");
                sb.append("- 分支不存在\n");
                sb.append("- 网络连接问题\n");
                sb.append("\n请检查 Git 配置后重试。");
            }

            return sb.toString();
        } catch (Exception e) {
            logger.error("检查 Git 配置失败", e);
            return "检查 Git 配置失败：" + e.getMessage();
        }
    }

    private String formatJobStatus(String status) {
        if (status == null) return "未知";
        switch (status.toUpperCase()) {
            case "COMPLETED": case "SUCCESS": return "已完成";
            case "RUNNING": case "IN_PROGRESS": return "执行中";
            case "FAILED": case "ERROR": return "失败";
            case "PENDING": case "QUEUED": return "排队中";
            default: return status;
        }
    }

    private String maskSensitiveUrl(String url) {
        if (url == null || !url.contains("@")) return url;
        return url.replaceAll("(https?://)[^@]+@", "$1***:***@");
    }

    private String getAppNameList(List<Map<String, Object>> apps) {
        if (apps == null || apps.isEmpty()) return "无";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < apps.size(); i++) {
            if (i > 0) sb.append(", ");
            sb.append(apps.get(i).getOrDefault("name", ""));
        }
        return sb.toString();
    }
}
