package com.oAT.agent.jacoco.data;

import com.oAT.agent.common.Decompiler.ILanguageNames;
import com.oAT.agent.common.Decompiler.JavaNames;
import com.oAT.agent.common.StackTraceFormatter;
import com.oAT.agent.common.logger.Log;
import com.oAT.agent.common.logger.LogFactory;
import com.oAT.agent.jacoco.ClassProbeInfo;
import com.oAT.agent.jacoco.ClassProbeInfoRegistry;
import com.oAT.agent.jacoco.CoverageCollector;
import com.oAT.agent.model.StackNodeVo;

import java.util.*;

/**
 * 构建 StackNodeVo[] 数组，从 CoverageCollector 的探针快照 + ClassProbeInfo 元信息生成覆盖率报告。
 * <p>
 * 替代旧的从 StackSession + StackNode 树构建的方式。
 * 新方案在服务端（oAT-service-web）端合并，减轻目标系统运行时压力。
 * </p>
 */
public class StackNodeVoBuilder {
    private final static Log logger = LogFactory.getLog(StackNodeVoBuilder.class);

    /**
     * 从 CoverageCollector 的探针快照构建 codeNodes。
     * <p>
     * 遍历所有有覆盖率数据的 classId，结合 ClassProbeInfo 元信息，
     * 将 boolean[] 探针数据转换为结构化的 StackNodeVo 数组。
     * </p>
     *
     * @param collector 请求结束时收集的覆盖率数据聚合器
     * @return StackNodeVo[] 覆盖率节点数组
     */
    public StackNodeVo[] buildCodeNodes(CoverageCollector collector) {
        try {
            Map<Long, boolean[]> snapshots = collector.getProbeSnapshots();
            if (snapshots == null || snapshots.isEmpty()) {
                return new StackNodeVo[0];
            }

            ILanguageNames javaNames = new JavaNames();
            List<StackNodeVo> resultList = new ArrayList<>();

            int nodeIdx = 0;
            for (Map.Entry<Long, boolean[]> entry : snapshots.entrySet()) {
                long classId = entry.getKey();
                boolean[] probes = entry.getValue();

                ClassProbeInfo probeInfo = ClassProbeInfoRegistry.get(classId);
                if (probeInfo == null) {
                    continue;
                }

                // 按方法分组探针数据
                Map<Integer, List<Integer>> methodEntryToProbeIndices = new LinkedHashMap<>();
                for (int i = 0; i < probes.length; i++) {
                    if (probes[i]) {
                        int methodEntryIdx = probeInfo.getProbeMethodEntryIndex()[i];
                        methodEntryToProbeIndices.computeIfAbsent(methodEntryIdx, k -> new ArrayList<>()).add(i);
                    }
                }

                // 为每个方法生成 StackNodeVo
                boolean addedNodeForCurrentClass = false;
                for (Map.Entry<Integer, List<Integer>> methodEntry : methodEntryToProbeIndices.entrySet()) {
                    int methodEntryIdx = methodEntry.getKey();
                    List<Integer> executedProbeIndices = methodEntry.getValue();

                    String methodNameDesc = probeInfo.getMethodEntryToName().get(methodEntryIdx);
                    if (methodNameDesc == null) continue;

                    StackNodeVo nodeVo = new StackNodeVo();
                    nodeVo.setId("0." + (nodeIdx + 1));
                    nodeVo.setClassId(classId);

                    String originClassName = probeInfo.getClassName();
                    nodeVo.setClassName(CoverageNamingSupport.toOwnerQualifiedClassName(originClassName));

                    // Parse method name and desc from methodNameDesc (format: "methodName desc")
                    int spaceIdx = methodNameDesc.indexOf(' ');
                    String mName = spaceIdx > 0 ? methodNameDesc.substring(0, spaceIdx) : methodNameDesc;
                    String mDesc = spaceIdx > 0 ? methodNameDesc.substring(spaceIdx + 1) : "";
                    String displayMethodName = javaNames.getMethodName(originClassName, mName, mDesc, null);
                    if (CoverageNamingSupport.shouldIgnoreMethod(mName, displayMethodName)) {
                        continue;
                    }
                    nodeVo.setMethodName(displayMethodName);
                    nodeVo.setMethodDescriptor(mDesc);

                    // Line coverage: executed lines (from executed probes that are NOT branch probes)
                    ArrayList<Integer> doLines = new ArrayList<>();
                    Set<Integer> executedBranchLines = new HashSet<>();
                    for (int probeIdx : executedProbeIndices) {
                        boolean isBranch = probeIdx < probeInfo.getProbeIsBranch().length
                                && probeInfo.getProbeIsBranch()[probeIdx];
                        if (isBranch) {
                            Integer branchLine = probeInfo.getBranchProbeToLine().get(probeIdx);
                            if (branchLine != null) {
                                executedBranchLines.add(branchLine);
                            }
                        } else {
                            int line = probeInfo.getProbeLineNumbers()[probeIdx];
                            if (line > 0) {
                                doLines.add(line);
                            }
                        }
                    }
                    // Remove branch lines from doLines (branch lines are tracked separately)
                    doLines.removeAll(executedBranchLines);
                    nodeVo.setDoLines(doLines);

                    // Method coverage
                    ArrayList<Integer> execMethodList = new ArrayList<>(1);
                    int methodEntryLine = probeInfo.getProbeLineNumbers()[methodEntryIdx];
                    if (methodEntryLine > 0) execMethodList.add(methodEntryLine);
                    nodeVo.setExecuteMethodTotal(execMethodList);

                    // Branch coverage
                    nodeVo.setExecuteBranch(new ArrayList<>(executedBranchLines));

                    // MC/DC coverage
                    Map<String, List<List<String>>> mcdcData = buildMcdcCoverage(executedProbeIndices, probeInfo);
                    nodeVo.setMcdcCoverage(mcdcData);

                    // Cyclomatic complexity
                    nodeVo.setExecCyclo(String.valueOf(executedBranchLines.size()));

                    // Recursive/Async
                    Boolean recursive = probeInfo.getMethodEntryToRecursive().get(methodEntryIdx);
                    Boolean async = probeInfo.getMethodEntryToAsync().get(methodEntryIdx);
                    nodeVo.setRecursive(recursive != null && recursive);
                    nodeVo.setAsync(async != null && async);

                    nodeVo.setDone(true);
                    nodeVo.setSize(1);

                    resultList.add(nodeVo);
                    addedNodeForCurrentClass = true;
                }

                if (addedNodeForCurrentClass) {
                    nodeIdx++;
                }
            }

            return resultList.toArray(new StackNodeVo[0]);
        } catch (Throwable t) {
            logger.error("[Agent-EXCError]buildCodeNodes 异常: " + StackTraceFormatter.formatExceptionWithAgentMark(t));
            return new StackNodeVo[0];
        }
    }

    /**
     * 构建 MC/DC 覆盖率数据。
     * <p>
     * 对于每个分支行，检查 true/false 探针对是否都被执行过。
     * MC/DC 的核心要求是：每个条件必须独立影响判定结果。
     * 通过分析 true 和 false 探针的执行情况来生成条件组合数据。
     * </p>
     *
     * @param executedProbeIndices 本次执行中被触发的探针索引
     * @param probeInfo            类探针元信息
     * @return MC/DC 覆盖数据 Map<branchLine, List<List<String>>>
     */
    private Map<String, List<List<String>>> buildMcdcCoverage(List<Integer> executedProbeIndices,
                                                               ClassProbeInfo probeInfo) {
        Map<String, List<List<String>>> mcdc = new LinkedHashMap<>();
        Map<Integer, Integer> branchTrueToFalse = probeInfo.getBranchTrueToFalseProbe();
        Map<Integer, Integer> branchConditionCounts = probeInfo.getBranchLineToConditionCount();

        // Group executed branch probes by branch line
        Map<Integer, boolean[]> branchLineExecStatus = new LinkedHashMap<>();
        for (int probeIdx : executedProbeIndices) {
            Integer branchLine = probeInfo.getBranchProbeToLine().get(probeIdx);
            if (branchLine == null) continue;

            boolean[] status = branchLineExecStatus.computeIfAbsent(branchLine, k -> new boolean[2]); // [falseExecuted, trueExecuted]
            Integer falseProbeIdx = branchTrueToFalse.get(probeIdx);
            if (falseProbeIdx != null) {
                // This is a true probe
                status[1] = true;
            } else {
                // This is a false probe
                status[0] = true;
            }
        }

        // Build MC/DC data for each branch line
        for (Map.Entry<Integer, boolean[]> entry : branchLineExecStatus.entrySet()) {
            int branchLine = entry.getKey();
            boolean[] status = entry.getValue();
            int conditionCount = branchConditionCounts.getOrDefault(branchLine, 1);

            List<List<String>> combinations = new ArrayList<>();

            // Both true and false executed: full MC/DC pair for this branch
            if (status[0] && status[1]) {
                List<String> combo = new ArrayList<>(conditionCount);
                for (int i = 0; i < conditionCount; i++) {
                    combo.add("true");
                }
                combinations.add(combo);
                combo = new ArrayList<>(conditionCount);
                for (int i = 0; i < conditionCount; i++) {
                    combo.add("false");
                }
                combinations.add(combo);
            } else if (status[1]) {
                // Only true branch executed
                List<String> combo = new ArrayList<>(conditionCount);
                for (int i = 0; i < conditionCount; i++) {
                    combo.add("true");
                }
                combinations.add(combo);
            } else if (status[0]) {
                // Only false branch executed
                List<String> combo = new ArrayList<>(conditionCount);
                for (int i = 0; i < conditionCount; i++) {
                    combo.add("false");
                }
                combinations.add(combo);
            }

            if (!combinations.isEmpty()) {
                mcdc.put(String.valueOf(branchLine), combinations);
            }
        }

        return mcdc;
    }
}
