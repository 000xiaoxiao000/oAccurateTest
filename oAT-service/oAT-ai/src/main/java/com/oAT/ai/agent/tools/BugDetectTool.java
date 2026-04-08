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
 * AI代码Bug检测工具
 * 利用LLM对指定类/方法的源码进行智能分析，检测潜在Bug和代码缺陷
 *
 * <p>检测维度：</p>
 * <ul>
 *   <li>空指针风险 - 未做null检查的变量使用</li>
 *   <li>资源泄漏 - 未关闭的Stream/Connection</li>
 *   <li>并发问题 - 非线程安全的共享变量访问</li>
 *   <li>逻辑错误 - 条件判断遗漏、死代码等</li>
 *   <li>异常处理 - 吞掉异常、异常类型不匹配等</li>
 *   <li>性能问题 - 循环内重复计算、N+1查询模式等</li>
 * </ul>
 */
public class BugDetectTool {

    private static final Logger logger = LoggerFactory.getLogger(BugDetectTool.class);

    private final AgentDataProvider dataProvider;

    public BugDetectTool(AgentDataProvider dataProvider) {
        this.dataProvider = dataProvider;
    }

    @Tool("对指定的Java类进行AI智能Bug检测，分析源码中的潜在缺陷、空指针风险、资源泄漏、并发问题等")
    public String detectBugs(@P("要检测的Java类全限定名（如com.example.UserService）") String className) {
        if (className == null || className.trim().isEmpty()) {
            return "错误：请提供要检测的类名";
        }
        try {
            String projectId = AgentContext.getCurrentProjectId();
            if (projectId == null) {
                return "错误：未找到项目上下文";
            }

            // 获取源码
            String sourceCode = dataProvider.getSourceCode(className);
            if (sourceCode == null || sourceCode.trim().isEmpty()) {
                return "未找到类 " + className + " 的源码，可能该类未被插桩或不在当前项目中。\n" +
                       "提示：请确保类名正确（需要完整的包路径），且该类已被 oAT 探针覆盖。";
            }

            // 获取覆盖率数据作为辅助参考
            List<Map<String, Object>> apps = dataProvider.getApps(projectId);
            String coverageInfo = "";
            if (apps != null && !apps.isEmpty()) {
                for (Map<String, Object> app : apps) {
                    String appId = (String) app.get("id");
                    List<Map<String, Object>> reports = dataProvider.getCoverageReports(appId);
                    if (reports != null && !reports.isEmpty()) {
                        String reportId = (String) reports.get(0).get("id");
                        List<Map<String, Object>> classes = dataProvider.getClassCoverageList(reportId, null, null);
                        if (classes != null) {
                            for (Map<String, Object> cls : classes) {
                                String cn = (String) cls.getOrDefault("className", "");
                                if (cn.equals(className) || cn.endsWith("." + className)) {
                                    Double lineRate = parseDouble(cls.get("lineRate"));
                                    Double branchRate = parseDouble(cls.get("branchRate"));
                                    Object complexityObj = cls.get("complexity");
                                    coverageInfo = String.format(
                                            "\n\n【覆盖率参考】行覆盖率: %s, 分支覆盖率: %s, 复杂度: %s",
                                            lineRate != null ? String.format("%.1f%%", lineRate * 100) : "-",
                                            branchRate != null ? String.format("%.1f%%", branchRate * 100) : "-",
                                            complexityObj != null ? complexityObj.toString() : "-");
                                    break;
                                }
                            }
                        }
                    }
                }
            }

            // 构建结构化的分析请求（返回给LLM进行分析）
            StringBuilder analysisRequest = new StringBuilder();
            analysisRequest.append("## 请分析以下Java类的源码，检测其中潜在的Bug和代码缺陷\n\n");
            analysisRequest.append("### 类名：").append(className).append("\n\n");
            analysisRequest.append("```java\n").append(truncateSource(sourceCode)).append("\n```\n");
            analysisRequest.append(coverageInfo);
            analysisRequest.append("\n\n### 分析要求\n");
            analysisRequest.append("请从以下维度逐一分析，给出具体发现（如果某维度无问题请说明\"未发现明显问题\"）：\n\n");
            analysisRequest.append("**1. 空指针风险** - 变量解引用前是否做了null检查？Optional/集合操作是否安全？\n");
            analysisRequest.append("**2. 资源泄漏** - Stream/Connection/InputStream等是否在finally/try-with-resources中关闭？\n");
            analysisRequest.append("**3. 并发问题** - 共享可变状态是否有同步保护？是否有竞态条件？\n");
            analysisRequest.append("**4. 逻辑错误** - 条件分支是否完整？是否有不可达代码？边界条件是否处理？\n");
            analysisRequest.append("**5. 异常处理** - 是否有空的catch块？异常信息是否丢失？异常类型是否精确？\n");
            analysisRequest.append("**6. 性能隐患** - 循环内是否有重复计算？是否有N+1查询？大对象是否合理回收？\n\n");
            analysisRequest.append("### 输出格式要求\n");
            analysisRequest.append("每个发现按以下格式输出：\n");
            analysisRequest.append("- 🔴🟡🟢 **[严重程度] [类别] 行号范围**: 具体描述\n");
            analysisRequest.append("  - 当前代码片段（引用原文）\n");
            analysisRequest.append("  - 风险说明\n");
            analysisRequest.append("  - 修复建议（给出修改后的代码示例）\n\n");
            analysisRequest.append("最后给出总结：共发现 X 个问题（严重 Y / 中等 Z / 建议 W），优先修复建议排序。");

            return analysisRequest.toString();
        } catch (Exception e) {
            logger.error("代码Bug检测失败", e);
            return "代码Bug检测失败：" + e.getMessage();
        }
    }

    @Tool("对指定的方法级别代码进行深度Bug检测，支持传入具体的源码片段")
    public String detectBugsInMethod(@P("类全限定名") String className,
                                      @P("方法名称") String methodName,
                                      @P("可选：自定义源码片段，如果不传则自动获取") String sourceCodeSnippet) {
        if (className == null || className.trim().isEmpty()) {
            return "错误：请提供要检测的类全限定名";
        }
        try {
            String projectId = AgentContext.getCurrentProjectId();
            if (projectId == null) {
                return "错误：未找到项目上下文";
            }

            String sourceCode = (sourceCodeSnippet != null && !sourceCodeSnippet.trim().isEmpty())
                    ? sourceCodeSnippet
                    : dataProvider.getSourceCode(className);

            if (sourceCode == null || sourceCode.trim().isEmpty()) {
                return "未找到类 " + className + " 的源码";
            }

            // 如果指定了方法名，尝试提取该方法
            String targetSource = sourceCode;
            if (methodName != null && !methodName.trim().isEmpty()) {
                String extracted = extractMethod(sourceCode, methodName.trim());
                if (extracted == null) {
                    logger.warn("未找到方法 {} 在类 {} 中，将分析整个类", methodName, className);
                } else {
                    targetSource = extracted;
                }
            }

            StringBuilder sb = new StringBuilder();
            sb.append("## 方法级深度Bug检测\n\n");
            sb.append("**目标**: ").append(className);
            if (methodName != null) {
                sb.append(".").append(methodName);
            }
            sb.append("\n\n```java\n").append(truncateSource(targetSource, 3000)).append("\n```\n\n");
            sb.append("### 深度分析要求\n");
            sb.append("请对该代码进行逐行级别的深度分析：\n\n");
            sb.append("1. **控制流分析** - if/else/switch分支完整性，循环终止条件正确性\n");
            sb.append("2. **数据流分析** - 变量初始化顺序，未初始化使用，类型转换安全\n");
            sb.append("3. **API误用检测** - 集合操作的ConcurrentModificationException风险\n");
            sb.append("4. **边界条件** - 整数溢出、数组越界、除零风险\n");
            sb.append("5. **安全漏洞** - SQL注入/XSS/敏感信息泄露\n");
            sb.append("6. **编码规范** - 命名规范、魔法数字、过长方法\n\n");
            sb.append("输出格式：每个问题用表格形式展示 | 位置 | 严重度 | 问题 | 修复建议 |\n");

            return sb.toString();
        } catch (Exception e) {
            logger.error("方法级Bug检测失败", e);
            return "方法级Bug检测失败：" + e.getMessage();
        }
    }

    @Tool("批量检测多个类的Bug，生成汇总报告")
    public String batchDetectBugs(@P("类全限定名列表，逗号分隔") String classNames) {
        if (classNames == null || classNames.trim().isEmpty()) {
            return "错误：请提供要检测的类名列表（逗号分隔）";
        }
        try {
            String[] classes = classNames.split(",");
            StringBuilder sb = new StringBuilder();
            sb.append("## 批量代码Bug检测报告\n\n");
            sb.append("待检测类（").append(classes.length).append("个）：\n");

            for (int i = 0; i < classes.length; i++) {
                String cn = classes[i].trim();
                if (!cn.isEmpty()) {
                    sb.append(i + 1).append(". ").append(cn).append("\n");
                }
            }
            sb.append("\n请逐一调用 detectBugs 工具对上述每个类进行检测，最后生成一份汇总报告，包含：\n");
            sb.append("- 各类的问题数量统计（按严重度分类）\n");
            sb.append("- TOP 10 最需关注的问题清单\n");
            sb.append("- 项目整体代码健康评分（满分10分）\n");
            sb.append("- 优先修复路线图\n");

            return sb.toString();
        } catch (Exception e) {
            logger.error("批量Bug检测失败", e);
            return "批量Bug检测失败：" + e.getMessage();
        }
    }

    // ========== 内部辅助方法 ==========

    /**
     * 截断过长的源码，保留关键部分
     */
    private String truncateSource(String source) {
        return truncateSource(source, 8000);
    }

    private String truncateSource(String source, int maxLength) {
        if (source == null) return "";
        if (source.length() <= maxLength) return source;
        // 保留开头和结尾
        int half = maxLength / 2;
        return source.substring(0, half) + "\n// ... [中间代码已截断] ...\n" +
               source.substring(source.length() - half);
    }

    /**
     * 从源码中提取指定方法的代码（简单文本匹配）
     */
    private String extractMethod(String source, String methodName) {
        // 简单匹配：找方法签名开始位置
        int idx = source.indexOf(methodName + "(");
        if (idx < 0) return null;

        // 回退找到方法签名的起始位置（找最近的换行或public/private/protected等）
        int start = Math.max(0, source.lastIndexOf('\n', idx));
        if (start > 0) start++; // 跳过换行符

        // 找方法体结束位置（简单计数花括号）
        int braceCount = 0;
        boolean foundOpen = false;
        int end = start;
        for (int i = start; i < source.length(); i++) {
            char c = source.charAt(i);
            if (c == '{') {
                braceCount++;
                foundOpen = true;
            } else if (c == '}') {
                braceCount--;
                if (foundOpen && braceCount == 0) {
                    end = i + 1;
                    break;
                }
            }
        }

        if (end <= start) return null;
        return source.substring(start, end);
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
}
