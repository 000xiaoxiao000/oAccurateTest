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
 * 性能分析工具
 * 提供应用性能指标、慢接口分析、响应时间统计等查询能力
 */
public class PerformanceAnalysisTool {

    private static final Logger logger = LoggerFactory.getLogger(PerformanceAnalysisTool.class);

    private final AgentDataProvider dataProvider;

    public PerformanceAnalysisTool(AgentDataProvider dataProvider) {
        this.dataProvider = dataProvider;
    }

    @Tool("获取应用的性能指标概览，包括平均响应时间、P95、P99等指标")
    public String getAppPerformanceOverview(@P("应用名称") String appName) {
        if (appName == null || appName.trim().isEmpty()) {
            return "错误：请提供应用名称";
        }
        try {
            String projectId = AgentContext.getCurrentProjectId();
            if (projectId == null) {
                return "错误：未找到项目上下文";
            }

            // 获取应用列表
            List<Map<String, Object>> apps = dataProvider.getApps(projectId);
            String appId = findAppIdByName(apps, appName.trim());
            if (appId == null) {
                return "未找到应用：" + appName + "。可用的应用有：" + getAppNameList(apps);
            }

            // 获取应用详情
            Map<String, Object> appDetail = dataProvider.getAppDetail(appId);
            if (appDetail == null || appDetail.isEmpty()) {
                return "获取应用详情失败";
            }

            StringBuilder sb = new StringBuilder();
            sb.append("## ").append(appName).append(" - 性能指标概览\n\n");
            sb.append("- 应用状态: ").append(Boolean.TRUE.equals(appDetail.get("online")) ? "✅ 在线" : "⚪ 离线").append("\n");
            sb.append("- 在线实例数: ").append(appDetail.getOrDefault("onlineCount", 0)).append("\n");
            
            // 从调用链数据中分析性能指标
            List<Map<String, Object>> traces = dataProvider.getTraceList(projectId, appId, 100);
            if (traces != null && !traces.isEmpty()) {
                PerformanceStats stats = calculatePerformanceStats(traces);
                if (stats.totalRequests == 0) {
                    sb.append("\n### 无法评估性能\n");
                    sb.append("已找到 ").append(traces.size()).append(" 条调用链，但没有可解析的响应耗时，在线状态不能代表性能。\n");
                    sb.append("请确认 Agent 上报了 useTime/duration 后再分析。\n");
                    return sb.toString();
                }
                sb.append("\n### 响应时间统计（最近 ").append(traces.size()).append(" 条调用链样本）\n");
                sb.append("- 平均响应时间: **").append(stats.avgTime).append("ms**\n");
                sb.append("- P50响应时间: **").append(stats.p50Time).append("ms**\n");
                sb.append("- P95响应时间: **").append(stats.p95Time).append("ms**\n");
                sb.append("- P99响应时间: **").append(stats.p99Time).append("ms**\n");
                sb.append("- 最慢请求: **").append(stats.maxTime).append("ms**\n");
                sb.append("- 有效耗时样本: ").append(stats.totalRequests).append("\n");
                sb.append("- HTTP 错误样本: ").append(stats.errorRequests).append("（").append(formatRate(stats.errorRequests, stats.totalRequests)).append("）\n");
                sb.append("\n### 判断\n").append(buildAssessment(stats)).append("\n");
            } else {
                sb.append("\n### 无法评估性能\n");
                sb.append("当前没有调用链样本。应用在线只说明 Agent 有心跳，不代表有请求流量或性能正常。\n");
                sb.append("请先访问应用产生请求，并确认 Agent 已上报调用链和耗时。\n");
            }

            return sb.toString();
        } catch (Exception e) {
            logger.error("获取应用性能概览失败", e);
            return "获取应用性能概览失败：" + e.getMessage();
        }
    }

    @Tool("获取应用的慢接口列表，按响应时间降序排列")
    public String getSlowEndpoints(@P("应用名称") String appName, @P("返回数量限制，默认10") int limit) {
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

            List<Map<String, Object>> apps = dataProvider.getApps(projectId);
            String appId = findAppIdByName(apps, appName.trim());
            if (appId == null) {
                return "未找到应用：" + appName;
            }

            List<Map<String, Object>> traces = dataProvider.getTraceList(projectId, appId, 200);
            if (traces == null || traces.isEmpty()) {
                return "应用【" + appName + "】暂无调用链数据";
            }

            List<Map<String, Object>> timedTraces = new java.util.ArrayList<>(traces);
            timedTraces.removeIf(trace -> getDuration(trace) == null || getDuration(trace) <= 0);
            if (timedTraces.isEmpty()) {
                return "应用【" + appName + "】有调用链记录，但没有可解析的响应耗时，无法识别慢接口。";
            }

            // 找出慢接口
            timedTraces.sort((a, b) -> {
                long timeA = defaultLong(getDuration(a));
                long timeB = defaultLong(getDuration(b));
                return Long.compare(timeB, timeA); // 降序
            });

            StringBuilder sb = new StringBuilder();
            sb.append("## ").append(appName).append(" - 慢接口TOP ").append(limit).append("\n\n");
            sb.append("| 排名 | 接口路径 | 响应时间 | 状态码 | 时间 |\n");
            sb.append("|------|----------|----------|--------|------|\n");

            int count = 0;
            for (Map<String, Object> trace : timedTraces) {
                if (count >= limit) break;
                count++;
                
                String url = (String) trace.getOrDefault("url", "-");
                Long duration = getDuration(trace);
                Integer statusCode = getStatusCode(trace);
                String createTime = safeString(trace.getOrDefault("createTime", "-"));

                String durationStr = duration != null ? duration + "ms" : "-";
                String statusStr = statusCode != null ? statusCode.toString() : "-";
                
                // 标记特别慢的请求
                String marker = "";
                if (duration != null && duration > 2000) {
                    marker = " 🔴";
                } else if (duration != null && duration > 1000) {
                    marker = " 🟡";
                }

                sb.append("| ").append(count);
                sb.append(" | ").append(url);
                sb.append(" | ").append(durationStr).append(marker);
                sb.append(" | ").append(statusStr);
                sb.append(" | ").append(createTime);
                sb.append(" |\n");
            }

            sb.append("\n> 🔴 超过2秒 | 🟡 超过1秒\n");
            return sb.toString();
        } catch (Exception e) {
            logger.error("获取慢接口列表失败", e);
            return "获取慢接口列表失败：" + e.getMessage();
        }
    }

    @Tool("获取应用的接口调用频次统计")
    public String getEndpointCallFrequency(@P("应用名称") String appName) {
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

            List<Map<String, Object>> traces = dataProvider.getTraceList(projectId, appId, 500);
            if (traces == null || traces.isEmpty()) {
                return "应用【" + appName + "】暂无调用链数据";
            }

            // 统计接口调用频次
            Map<String, Integer> endpointCount = new java.util.HashMap<>();
            for (Map<String, Object> trace : traces) {
                String url = (String) trace.get("url");
                if (url != null && !url.isEmpty()) {
                    // 去除参数部分
                    String path = url.split("\\?")[0];
                    endpointCount.put(path, endpointCount.getOrDefault(path, 0) + 1);
                }
            }

            if (endpointCount.isEmpty()) {
                return "应用【" + appName + "】的调用链没有可识别的接口路径，无法统计调用频次。";
            }

            // 排序
            List<Map.Entry<String, Integer>> sortedEndpoints = new java.util.ArrayList<>(endpointCount.entrySet());
            sortedEndpoints.sort((a, b) -> b.getValue().compareTo(a.getValue()));

            StringBuilder sb = new StringBuilder();
            sb.append("## ").append(appName).append(" - 接口调用频次TOP 20\n\n");
            sb.append("| 排名 | 接口路径 | 调用次数 | 占比 |\n");
            sb.append("|------|----------|----------|------|\n");

            int total = traces.size();
            int count = 0;
            for (Map.Entry<String, Integer> entry : sortedEndpoints) {
                if (count >= 20) break;
                count++;
                
                double percentage = (entry.getValue() * 100.0) / total;
                sb.append("| ").append(count);
                sb.append(" | ").append(entry.getKey());
                sb.append(" | ").append(entry.getValue());
                sb.append(" | ").append(String.format("%.1f%%", percentage));
                sb.append(" |\n");
            }

            sb.append("\n> 统计范围：最近最多 500 条调用链样本；这不是全量吞吐量。\n");

            return sb.toString();
        } catch (Exception e) {
            logger.error("获取接口调用频次失败", e);
            return "获取接口调用频次失败：" + e.getMessage();
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

    private String getAppNameList(List<Map<String, Object>> apps) {
        if (apps == null || apps.isEmpty()) return "无";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < apps.size(); i++) {
            if (i > 0) sb.append(", ");
            sb.append(apps.get(i).getOrDefault("name", ""));
        }
        return sb.toString();
    }

    private PerformanceStats calculatePerformanceStats(List<Map<String, Object>> traces) {
        PerformanceStats stats = new PerformanceStats();
        List<Long> durations = new java.util.ArrayList<>();

        for (Map<String, Object> trace : traces) {
            Long duration = getDuration(trace);
            if (duration != null && duration > 0) {
                durations.add(duration);
                stats.totalTime += duration;
                if (duration > stats.maxTime) {
                    stats.maxTime = duration;
                }
                Integer statusCode = getStatusCode(trace);
                if ((statusCode != null && statusCode >= 400) || Boolean.TRUE.equals(trace.get("hasError")) || Boolean.TRUE.equals(trace.get("error"))) {
                    stats.errorRequests++;
                }
            }
        }

        stats.totalRequests = durations.size();
        if (stats.totalRequests > 0) {
            stats.avgTime = stats.totalTime / stats.totalRequests;
            durations.sort(Long::compareTo);
            stats.p50Time = durations.get((int) (durations.size() * 0.5));
            stats.p95Time = durations.get((int) (durations.size() * 0.95));
            stats.p99Time = durations.get((int) (durations.size() * 0.99));
        }

        return stats;
    }

    private String buildAssessment(PerformanceStats stats) {
        if (stats.p95Time > 2000) return "🔴 P95 超过 2 秒，建议优先查看慢接口并定位对应调用节点。";
        if (stats.p95Time > 1000) return "🟡 P95 超过 1 秒，存在明显慢请求，建议查看慢接口 TOP 列表。";
        if (stats.errorRequests > 0) return "🟡 样本中存在 HTTP 错误或异常，性能结论需要结合错误链路一起判断。";
        return "🟢 当前样本的 P95 未超过 1 秒，未发现明显性能异常；这只代表样本窗口，不能替代持续监控。";
    }

    private String formatRate(int numerator, int denominator) {
        return denominator <= 0 ? "0.0%" : String.format("%.1f%%", numerator * 100.0 / denominator);
    }

    private Long getDuration(Map<String, Object> trace) {
        Long duration = parseLong(trace.get("duration"));
        return duration != null ? duration : parseLong(trace.get("useTime"));
    }

    private Integer getStatusCode(Map<String, Object> trace) {
        Integer statusCode = parseInt(trace.get("statusCode"));
        return statusCode != null ? statusCode : parseInt(trace.get("responseCode"));
    }

    private String safeString(Object value) {
        return value == null ? "" : value.toString();
    }

    private long defaultLong(Long value) {
        return value == null ? 0L : value;
    }

    private Long parseLong(Object obj) {
        if (obj == null) return null;
        if (obj instanceof Number) return ((Number) obj).longValue();
        try {
            return Long.parseLong(obj.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Integer parseInt(Object obj) {
        if (obj == null) return null;
        if (obj instanceof Number) return ((Number) obj).intValue();
        try {
            return Integer.parseInt(obj.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static class PerformanceStats {
        long totalTime = 0;
        long maxTime = 0;
        long avgTime = 0;
        long p50Time = 0;
        long p95Time = 0;
        long p99Time = 0;
        int totalRequests = 0;
        int errorRequests = 0;
    }
}
