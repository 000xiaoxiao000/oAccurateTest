package com.oAT.ai.agent.tools;

import com.oAT.ai.agent.AgentContext;
import com.oAT.ai.agent.AgentDataProvider;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * AI调用链路差异比对工具
 * 支持两条或多条调用链之间的差异对比分析，用于：
 * - 正常 vs 异常请求的差异定位
 * - 不同版本的回归对比
 * - 相同接口不同参数的路径差异
 * - 性能回退的根因排查
 *
 * <p>比对维度：</p>
 * <ul>
 *   <li>调用路径差异 - 新增/缺失/变化的节点</li>
 *   <li>性能差异 - 同节点耗时变化</li>
 *   <li>错误状态差异 - 正确 vs 失败的关键区别</li>
 *   <li>覆盖率差异 - 不同链路的代码覆盖差异</li>
 * </ul>
 */
public class CallChainCompareTool {

    private static final Logger logger = LoggerFactory.getLogger(CallChainCompareTool.class);

    private final AgentDataProvider dataProvider;

    public CallChainCompareTool(AgentDataProvider dataProvider) {
        this.dataProvider = dataProvider;
    }

    @Tool("对比两条调用链的差异，包括调用路径变化、性能差异、错误状态差异等，用于正常vs异常的根因定位")
    public String compareCallChains(@P("第一条调用链ID（基准链路，通常是正常的）") String baselineTraceId,
                                     @P("第二条调用链ID（对比链路，通常是有问题的）") String compareTraceId) {
        if (baselineTraceId == null || baselineTraceId.trim().isEmpty()
                || compareTraceId == null || compareTraceId.trim().isEmpty()) {
            return "错误：请提供两个TraceID用于对比（baseline为正常链路，compare为问题链路）";
        }
        try {
            Map<String, Object> baseTrace = dataProvider.getTraceDetail(baselineTraceId);
            Map<String, Object> compTrace = dataProvider.getTraceDetail(compareTraceId);

            if (baseTrace == null || baseTrace.isEmpty()) {
                return "未找到基准链路 TraceID: " + baselineTraceId;
            }
            if (compTrace == null || compTrace.isEmpty()) {
                return "未找到对比链路 TraceID: " + compareTraceId;
            }

            return buildComparisonReport(baseTrace, compTrace, baselineTraceId, compareTraceId);
        } catch (Exception e) {
            logger.error("调用链对比失败", e);
            return "调用链对比失败：" + e.getMessage();
        }
    }

    @Tool("将一条正常调用链与一条出错调用链进行智能对比，自动定位导致异常的根本原因")
    public String locateRootCause(@P("正常（成功）请求的TraceID") String successTraceId,
                                   @P("异常（失败）请求的TraceID") String failTraceId) {
        try {
            Map<String, Object> success = dataProvider.getTraceDetail(successTraceId);
            Map<String, Object> failure = dataProvider.getTraceDetail(failTraceId);

            if (success == null || success.isEmpty()) return "未找到正常请求 TraceID: " + successTraceId;
            if (failure == null || failure.isEmpty()) return "未找到异常请求 TraceID: " + failTraceId;

            StringBuilder sb = new StringBuilder();
            sb.append("## 异常根因定位分析\n\n");
            sb.append("### 请求基本信息对比\n\n");
            sb.append("| 维度 | ✅ 正常请求 | ❌ 异常请求 |\n");
            sb.append("|------|------------|-------------|\n");
            sb.append("| TraceID | ").append(successTraceId).append(" | ").append(failTraceId).append(" |\n");
            sb.append("| URL | ").append(safeStr(success.get("url"))).append(" | ").append(safeStr(failure.get("url"))).append(" |\n");
            sb.append("| 总耗时 | ").append(parseLong(success.get("duration"))).append("ms | ")
              .append(parseLong(failure.get("duration"))).append("ms |\n");
            sb.append("| 应用 | ").append(safeStr(success.get("appName"))).append(" | ")
              .append(safeStr(failure.get("appName"))).append(" |\n\n");

            // 提取节点
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> successNodes = (List<Map<String, Object>>) success.get("nodes");
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> failNodes = (List<Map<String, Object>>) failure.get("nodes");

            // 构建差异矩阵
            sb.append("### 节点级差异分析\n\n");
            if (successNodes != null && failNodes != null && !successNodes.isEmpty() && !failNodes.isEmpty()) {
                sb.append("| # | 节点名称 | 正常耗时 | 异常耗时 | ⏱️ 耗时差 | 状态变化 | 分析重点 |\n");
                sb.append("|---|---------|---------|---------|---------|---------|--------|\n");

                // 用名称匹配对应节点
                Set<String> processed = new HashSet<>();
                for (Map<String, Object> fNode : failNodes) {
                    String fName = safeStr(fNode.get("name"));
                    String fType = safeStr(fNode.get("type"));
                    long fDur = parseLong(fNode.get("duration"));
                    boolean fError = isNodeError(fNode);

                    Map<String, Object> sMatch = findMatchingNode(successNodes, fName);
                    if (sMatch != null) {
                        long sDur = parseLong(sMatch.get("duration"));
                        boolean sError = isNodeError(sMatch);
                        long diff = fDur - sDur;
                        String statusChange = sError == fError ? "→ 无变化" :
                                (sError && !fError ? "🟢 已恢复" : "❌ 新增异常");
                        String focus = determineFocus(diff, statusChange, fError, fName);

                        sb.append("| ").append(processed.size() + 1);
                        sb.append(" | ").append(fName.length() > 25 ? fName.substring(0, 22) + "..." : fName);
                        sb.append(" | ").append(sDur).append("ms");
                        sb.append(" | ").append(fDur).append("ms");
                        sb.append(" | ").append(formatDiff(diff));
                        sb.append(" | ").append(statusChange);
                        sb.append(" | ").append(focus).append(" |\n");
                        processed.add(fName);
                    } else {
                        // 只在异常中出现的新节点
                        sb.append("| ").append(processed.size() + 1);
                        sb.append(" | 🔴 **新增**: ").append(fName.length() > 20 ? fName.substring(0, 17) + "..." : fName);
                        sb.append(" | - | ").append(fDur).append("ms");
                        sb.append(" | N/A | ❌ 异常").append(" | **可能是根因节点** |\n");
                        processed.add(fName);
                    }
                }

                // 找出在正常中存在但在异常中消失的节点
                for (Map<String, Object> sNode : successNodes) {
                    String sName = safeStr(sNode.get("name"));
                    if (!processed.contains(sName)) {
                        Map<String, Object> fMatch = findMatchingNode(failNodes, sName);
                        if (fMatch == null) {
                            sb.append("| - | 🟡 **缺失**: ")
                              .append(sName.length() > 20 ? sName.substring(0, 17) + "..." : sName);
                            sb.append(" | ").append(parseLong(sNode.get("duration"))).append("ms | - | N/A | ⚠️ 未执行");
                            sb.append(" | 可能因前置节点异常而短路 |\n");
                        }
                    }
                }
            }

            // AI分析引导
            sb.append("\n### 根因推断\n\n");
            sb.append("请根据以上节点级差异分析，推断本次异常的根本原因：\n\n");
            sb.append("1. **首次异常出现位置** - 在调用链的哪个节点首次出现异常？\n");
            sb.append("2. **异常传播路径** - 异常是如何向下游传播的？\n");
            sb.append("3. **性能突变点** - 哪个节点的耗时增长最显著？是异常原因还是结果？\n");
            sb.append("4. **新增/缺失节点** - 是否有新节点加入或原有节点消失？这对异常意味着什么？\n");
            sb.append("5. **最终结论** - 给出明确的根因结论和置信度（高/中/低）\n");
            sb.append("6. **修复建议** - 给出具体的修复方案和验证步骤\n");

            return sb.toString();
        } catch (Exception e) {
            logger.error("异常根因定位失败", e);
            return "异常根因定位失败：" + e.getMessage();
        }
    }

    @Tool("对比同一个接口在不同时间段的多条调用链，检测性能回归或行为变化")
    public String compareOverTime(@P("接口URL关键词") String urlKeyword,
                                   @P("较早时间的TraceID列表，逗号分隔") String earlierTraceIds,
                                   @P("较近时间的TraceID列表，逗号分隔") String laterTraceIds) {
        if (urlKeyword == null || urlKeyword.trim().isEmpty()
                || earlierTraceIds == null || laterTraceIds == null) {
            return "错误：请提供URL关键词和两组TraceID（早期+近期）";
        }

        String[] earlyIds = earlierTraceIds.split(",");
        String[] lateIds = laterTraceIds.split(",");

        StringBuilder sb = new StringBuilder();
        sb.append("## 接口性能回归分析\n\n");
        sb.append("**目标URL**: ").append(urlKeyword).append("\n");
        sb.append("**早期样本**: ").append(earlyIds.length).append(" 条 | ");
        sb.append("**近期样本**: ").append(lateIds.length).append(" 条\n\n");

        // 收集早期数据
        List<Map<String, Object>> earlyTraces = collectTraces(earlyIds);
        List<Map<String, Object>> lateTraces = collectTraces(lateIds);

        // 统计对比
        Stats earlyStats = calcStats(earlyTraces);
        Stats lateStats = calcStats(lateTraces);

        sb.append("### 性能对比\n\n");
        sb.append("| 指标 | 早期版本 | 近期版本 | 变化 |\n");
        sb.append("|------|---------|---------|------|\n");
        sb.append("| 样本量 | ").append(earlyStats.count).append(" | ").append(lateStats.count).append(" | - |\n");
        sb.append("| 平均耗时 | ").append(String.format("%.0fms", earlyStats.avg)).append(" | ")
          .append(String.format("%.0fms", lateStats.avg)).append(" | ").append(formatDiff(lateStats.avg - earlyStats.avg)).append(" |\n");
        sb.append("| 中位耗时 | ").append(String.format("%.0fms", earlyStats.median)).append(" | ")
          .append(String.format("%.0fms", lateStats.median)).append(" | ").append(formatDiff(lateStats.median - earlyStats.median)).append(" |\n");
        sb.append("| P90耗时 | ").append(String.format("%.0fms", earlyStats.p90)).append(" | ")
          .append(String.format("%.0fms", lateStats.p90)).append(" | ").append(formatDiff(lateStats.p90 - earlyStats.p90)).append(" |\n");
        sb.append("| 最大耗时 | ").append(earlyStats.max).append("ms | ").append(lateStats.max).append("ms | ")
          .append(formatDiff(lateStats.max - earlyStats.max)).append(" |\n");
        sb.append("| 错误率 | ").append(String.format("%.1f%%", earlyStats.errRate * 100)).append(" | ")
          .append(String.format("%.1f%%", lateStats.errRate * 100)).append(" | ")
          .append(String.format("%+.1f%%", (lateStats.errRate - earlyStats.errRate) * 100)).append(" |\n\n");

        // 回归判定
        sb.append("### 回归判定\n\n");
        boolean perfRegression = lateStats.avg > earlyStats.avg * 1.2; // 平均耗时增加20%
        boolean errRegression = lateStats.errRate > earlyStats.errRate * 1.5; // 错误率增加50%
        boolean p99Regression = lateStats.p90 > earlyStats.p90 * 1.3; // P90增加30%

        if (perfRegression || errRegression || p99Regression) {
            sb.append("⚠️ **检测到可能的回归！**\n\n");
            if (perfRegression) sb.append("- 🔄 **性能回归**: 平均耗时增加超过20%\n");
            if (errRegression) sb.append("- ❌ **错误率上升**: 错误率增加超过50%\n");
            if (p99Regression) sb.append("- ⏱️ **尾部延迟恶化**: P90耗时增加超过30%\n");
        } else {
            sb.append("✅ **未检测到明显回归**，各项指标稳定或有所改善。\n");
        }

        sb.append("\n### 请进一步分析\n\n");
        sb.append("1. **变化归因** - 如果有回归，分析可能的代码变更（部署了什么新版本？）\n");
        sb.append("2. **热点节点** - 对比两组数据的各节点耗时分布，找出变慢的具体环节\n");
        sb.append("3. **配置变更** - 是否有JVM参数、连接池大小等配置调整？\n");
        sb.append("4. **环境因素** - 网络、数据库负载等基础设施因素\n");
        sb.append("5. **建议措施** - 如果确认回归，给出回滚或优化建议\n");

        return sb.toString();
    }

    @Tool("对比两条调用链的代码覆盖率差异，找出测试盲区和覆盖缺口")
    public String compareCoverage(@P("第一条调用链ID") String traceId1,
                                   @P("第二条调用链ID") String traceId2) {
        if (traceId1 == null || traceId2 == null) {
            return "错误：请提供两个TraceID";
        }
        try {
            Map<String, Object> t1 = dataProvider.getTraceDetail(traceId1);
            Map<String, Object> t2 = dataProvider.getTraceDetail(traceId2);

            if (t1 == null || t2 == null) {
                return "未找到其中一条调用链的数据";
            }

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> n1 = (List<Map<String, Object>>) t1.get("nodes");
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> n2 = (List<Map<String, Object>>) t2.get("nodes");

            Set<String> classes1 = extractClassNames(n1);
            Set<String> classes2 = extractClassNames(n2);

            StringBuilder sb = new StringBuilder();
            sb.append("## 调用链覆盖率差异对比\n\n");
            sb.append("**链路1** (").append(traceId1).append("): ")
              .append(n1 != null ? n1.size() : 0).append(" 个节点, ")
              .append(classes1.size()).append(" 个类\n");
            sb.append("**链路2** (").append(traceId2).append("): ")
              .append(n2 != null ? n2.size() : 0).append(" 个节点, ")
              .append(classes2.size()).append(" 个类\n\n");

            // 类集合差异
            Set<String> onlyIn1 = new TreeSet<>(classes1); onlyIn1.removeAll(classes2);
            Set<String> onlyIn2 = new TreeSet<>(classes2); onlyIn2.removeAll(classes1);
            Set<String> common = new TreeSet<>(classes1); common.retainAll(classes2);

            sb.append("### 类覆盖对比\n\n");
            sb.append("| 分类 | 数量 | 说明 |\n|------|------|------|\n");
            sb.append("| 共同覆盖 | ").append(common.size()).append(" | 两条链路都经过的类 |\n");
            sb.append("| 仅链路1 | ").append(onlyIn1.size()).append(" | 链路1独有 |\n");
            sb.append("| 仅链路2 | ").append(onlyIn2.size()).append(" | 链路2独有 |\n");
            sb.append("| 合并总计 | ").append(classes1.size() + classes2.size() - common.size()).append(" | 去重后的总类数 |\n\n");

            if (!onlyIn1.isEmpty()) {
                sb.append("**仅链路1覆盖的类**:\n");
                for (String c : onlyIn1) sb.append("- ").append(c).append("\n");
            }
            if (!onlyIn2.isEmpty()) {
                sb.append("\n**仅链路2覆盖的类**:\n");
                for (String c : onlyIn2) sb.append("- ").append(c).append("\n");
            }

            sb.append("\n### 分析建议\n\n");
            sb.append("请分析以上差异并给出：\n");
            sb.append("1. **互补性评估** - 两条链路的覆盖是否形成互补？合并后能提升多少覆盖率？\n");
            sb.append("2. **测试盲区** - 哪些重要的类/方法在两条链路中都未被覆盖？\n");
            sb.append("3. **测试补充建议** - 为了达到更好的覆盖率，应该设计什么样的测试场景？\n");
            sb.append("4. **优先级排序** - 按重要性列出需要额外覆盖的类和方法\n");

            return sb.toString();
        } catch (Exception e) {
            logger.error("覆盖率对比失败", e);
            return "覆盖率对比失败：" + e.getMessage();
        }
    }

    // ========== 内部构建方法 ==========

    /**
     * 构建通用对比报告
     */
    private String buildComparisonReport(Map<String, Object> base, Map<String, Object> comp,
                                         String baseId, String compId) {
        StringBuilder sb = new StringBuilder();
        sb.append("## 调用链差异对比报告\n\n");
        sb.append("### 基本信息\n\n");
        sb.append("| 维度 | 基准链路 | 对比链路 |\n|------|---------|--------|\n");
        sb.append("| TraceID | ").append(baseId).append(" | ").append(compId).append(" |\n");
        sb.append("| URL | ").append(safeStr(base.get("url"))).append(" | ").append(safeStr(comp.get("url"))).append(" |\n");

        long baseDur = parseLong(base.get("duration"));
        long compDur = parseLong(comp.get("duration"));
        sb.append("| 总耗时 | ").append(baseDur).append("ms | ").append(compDur).append("ms (差值: ").append(formatDiff(compDur - baseDur).trim()).append(") |\n");
        sb.append("| 应用 | ").append(safeStr(base.get("appName"))).append(" | ").append(safeStr(comp.get("appName"))).append(" |\n\n");

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> baseNodes = (List<Map<String, Object>>) base.get("nodes");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> compNodes = (List<Map<String, Object>>) comp.get("nodes");

        if ((baseNodes == null || baseNodes.isEmpty()) && (compNodes == null || compNodes.isEmpty())) {
            sb.append("两条链路均无详细节点数据。\n");
            return sb.toString();
        }

        // 节点对比表格
        sb.append("### 节点级别对比\n\n");

        if (baseNodes != null && compNodes != null && !baseNodes.isEmpty() && !compNodes.isEmpty()) {
            sb.append("| 节点 | 基准耗时 | 对比耗时 | 耗时变化 | 基准状态 | 对比状态 | 备注 |\n");
            sb.append("|------|---------|---------|---------|---------|---------|------|\n");

            Set<String> seen = new HashSet<>();

            // 遍历对比链路的每个节点
            for (Map<String, Object> cNode : compNodes) {
                String name = safeStr(cNode.get("name"));
                long cDur = parseLong(cNode.get("duration"));
                boolean cErr = isNodeError(cNode);

                Map<String, Object> bMatch = findMatchingNode(baseNodes, name);
                if (bMatch != null) {
                    long bDur = parseLong(bMatch.get("duration"));
                    boolean bErr = isNodeError(bMatch);
                    sb.append("| ").append(name);
                    sb.append(" | ").append(bDur).append("ms");
                    sb.append(" | ").append(cDur).append("ms");
                    sb.append(" | ").append(formatDiff(cDur - bDur));
                    sb.append(" | ").append(bErr ? "❌" : "✅");
                    sb.append(" | ").append(cErr ? "❌" : "✅");
                    if (bErr != cErr) sb.append(" | ⚠️ **状态变化!**");
                    else if (Math.abs(cDur - bDur) > Math.max(bDur * 0.3, 50))
                        sb.append(" | ⏱️ 显著变化");
                    else sb.append(" |");
                    sb.append(" |\n");
                    seen.add(name);
                } else {
                    sb.append("| 🔴 **新增**: ").append(name);
                    sb.append(" | - | ").append(cDur).append("ms | N/A | - | ");
                    sb.append(cErr ? "❌" : "✅").append(" | 仅在对比链路中出现 |\n");
                    seen.add(name);
                }
            }

            // 基准中有但对比中没有的
            for (Map<String, Object> bNode : baseNodes) {
                String bName = safeStr(bNode.get("name"));
                if (!seen.contains(bName)) {
                    sb.append("| 🟢 **缺失**: ").append(bName);
                    sb.append(" | ").append(parseLong(bNode.get("duration"))).append("ms | - | N/A");
                    sb.append(" | ").append(isNodeError(bNode) ? "❌" : "✅");
                    sb.append(" | - | 仅在基准链路中出现 |\n");
                }
            }
        }

        sb.append("\n### AI综合分析\n\n");
        sb.append("请基于以上对比数据，从以下维度给出分析：\n\n");
        sb.append("1. **路径差异** - 两条链路的调用路径有何不同？新增/缺失的节点说明了什么？\n");
        sb.append("2. **性能变化** - 哪些节点发生了显著的性能变化？可能的原因？\n");
        sb.append("3. **状态变化** - 是否有从正常变为异常（或反向）的节点？\n");
        sb.append("4. **关键发现** - 最重要的1-2个差异点和其业务含义\n");
        sb.append("5. **建议行动** - 基于差异分析的下一步操作建议\n");

        return sb.toString();
    }

    // ========== 辅助方法 ==========

    private List<Map<String, Object>> collectTraces(String[] ids) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (String id : ids) {
            id = id.trim();
            if (!id.isEmpty()) {
                Map<String, Object> t = dataProvider.getTraceDetail(id);
                if (t != null) result.add(t);
            }
        }
        return result;
    }

    private Stats calcStats(List<Map<String, Object>> traces) {
        Stats s = new Stats();
        s.count = traces.size();
        if (traces.isEmpty()) return s;

        List<Long> durations = new ArrayList<>();
        int errors = 0;
        for (Map<String, Object> t : traces) {
            long d = parseLong(t.get("duration"));
            durations.add(d);
            if ("error".equals(t.get("status")) || t.get("error") != null) errors++;
        }
        Collections.sort(durations);

        long sum = 0;
        for (long d : durations) sum += d;
        s.avg = (double) sum / durations.size();
        s.sum = sum;
        s.min = durations.get(0);
        s.max = durations.get(durations.size() - 1);
        s.median = durations.get(durations.size() / 2);
        s.p90 = durations.get((int)(durations.size() * 0.9));
        s.errRate = (double) errors / traces.size();
        return s;
    }

    private Set<String> extractClassNames(List<Map<String, Object>> nodes) {
        Set<String> names = new HashSet<>();
        if (nodes == null) return names;
        for (Map<String, Object> node : nodes) {
            String className = safeStr(node.get("className"));
            if (className != null && !className.isEmpty()) names.add(className);
            // 也尝试从name字段提取
            String name = safeStr(node.get("name"));
            if (name != null && name.contains(".")) {
                String extracted = name.contains("(") ? name.substring(0, name.indexOf('(')) : name;
                if (extracted.contains(".")) {
                    names.add(extracted.substring(0, extracted.lastIndexOf('.')));
                }
            }
        }
        return names;
    }

    private Map<String, Object> findMatchingNode(List<Map<String, Object>> nodes, String name) {
        if (nodes == null) return null;
        // 精确匹配
        for (Map<String, Object> node : nodes) {
            if (name.equals(node.get("name"))) return node;
        }
        // 模糊匹配（包含）
        for (Map<String, Object> node : nodes) {
            String nodeName = safeStr(node.get("name"));
            if (nodeName.equals(name) || nodeName.contains(name) || name.contains(nodeName)) return node;
        }
        return null;
    }

    private boolean isNodeError(Map<String, Object> node) {
        return Boolean.TRUE.equals(node.get("error"))
                || "error".equals(safeStr(node.get("type")))
                || node.get("exception") != null
                || node.get("errorMessage") != null;
    }

    private String formatDiff(long diff) {
        if (diff == 0) return "±0ms";
        return (diff > 0 ? "+" : "") + diff + "ms";
    }

    private String formatDiff(double diff) {
        if (Math.abs(diff) < 0.01) return "±0";
        return (diff > 0 ? "+" : "") + String.format("%.1f", diff);
    }

    private String determineFocus(long diff, String statusChange, boolean isError, String name) {
        List<String> reasons = new ArrayList<>(3);
        if (Math.abs(diff) > 200 || Math.abs(diff) > parseLong(name) * 0.5) reasons.add("耗时显著变化");
        if (statusChange.contains("新增异常")) reasons.add("状态异常");
        if (isError) reasons.add("异常节点");
        if (reasons.isEmpty()) return "-";
        return String.join(", ", reasons);
    }

    private static long parseLong(Object obj) {
        if (obj == null) return 0L;
        if (obj instanceof Number) return ((Number) obj).longValue();
        try { return Long.parseLong(obj.toString()); } catch (Exception e) { return 0L; }
    }

    private static String safeStr(Object obj) { return safeStr(obj, ""); }
    private static String safeStr(Object obj, String def) { return obj == null ? def : obj.toString(); }

    /** 统计信息内部类 */
    static class Stats {
        int count;
        double avg, median, p90, errRate;
        long min, max, sum;
    }
}
