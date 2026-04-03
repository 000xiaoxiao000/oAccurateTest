package com.oAT.agent.jacoco.data;

import com.oAT.agent.common.Decompiler.ILanguageNames;
import com.oAT.agent.common.Decompiler.JavaNames;
import com.oAT.agent.common.StackTraceFormatter;
import com.oAT.agent.common.logger.Log;
import com.oAT.agent.common.logger.LogFactory;
import com.oAT.agent.jacoco.ClassProbeInfo;
import com.oAT.agent.jacoco.ClassProbeInfoRegistry;
import com.oAT.agent.jacoco.CoverageCollector;
import com.oAT.agent.model.McdcCoverageSupport;
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

                Map<Integer, List<Integer>> methodEntryToProbeIndices = groupExecutedProbesByMethod(probes, probeInfo);
                if (methodEntryToProbeIndices.isEmpty()) {
                    continue;
                }

                boolean addedNodeForCurrentClass = false;
                String originClassName = probeInfo.getClassName();
                for (Map.Entry<Integer, List<Integer>> methodEntry : methodEntryToProbeIndices.entrySet()) {
                    int methodEntryIdx = methodEntry.getKey();
                    List<Integer> executedProbeIndices = methodEntry.getValue();
                    StackNodeVo nodeVo = buildMethodNode(classId, nodeIdx, methodEntryIdx, executedProbeIndices,
                            probeInfo, originClassName, javaNames);
                    if (nodeVo == null) {
                        continue;
                    }

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

    private Map<Integer, List<Integer>> groupExecutedProbesByMethod(boolean[] probes, ClassProbeInfo probeInfo) {
        Map<Integer, List<Integer>> methodEntryToProbeIndices = new LinkedHashMap<>();
        for (int i = 0; i < probes.length; i++) {
            if (!probes[i]) {
                continue;
            }
            int methodEntryIdx = probeInfo.getProbeMethodEntryIndex()[i];
            methodEntryToProbeIndices.computeIfAbsent(methodEntryIdx, k -> new ArrayList<>()).add(i);
        }
        return methodEntryToProbeIndices;
    }

    private StackNodeVo buildMethodNode(long classId, int nodeIdx, int methodEntryIdx, List<Integer> executedProbeIndices,
                                        ClassProbeInfo probeInfo, String originClassName, ILanguageNames javaNames) {
        String methodNameDesc = probeInfo.getMethodEntryToName().get(methodEntryIdx);
        if (methodNameDesc == null) {
            return null;
        }

        MethodSignature methodSignature = parseMethodSignature(methodNameDesc);
        String displayMethodName = javaNames.getMethodName(originClassName, methodSignature.methodName,
                methodSignature.methodDesc, null);
        if (CoverageNamingSupport.shouldIgnoreMethod(methodSignature.methodName, displayMethodName)) {
            return null;
        }

        CoverageLines coverageLines = collectCoverageLines(executedProbeIndices, probeInfo);

        StackNodeVo nodeVo = new StackNodeVo();
        nodeVo.setId("0." + (nodeIdx + 1));
        nodeVo.setClassId(classId);
        nodeVo.setClassName(CoverageNamingSupport.toOwnerQualifiedClassName(originClassName));
        nodeVo.setMethodName(displayMethodName);
        nodeVo.setMethodDescriptor(methodSignature.methodDesc);
        nodeVo.setDoLines(new ArrayList<>(coverageLines.executedLines));
        nodeVo.setExecuteMethodTotal(buildExecutedMethodEntries(methodEntryIdx, probeInfo));
        nodeVo.setExecuteBranch(new ArrayList<>(coverageLines.executedBranchLines));
        nodeVo.setMcdcCoverage(buildMcdcCoverage(executedProbeIndices, probeInfo));
        nodeVo.setExecCyclo(String.valueOf(coverageLines.executedBranchLines.size()));
        nodeVo.setRecursive(Boolean.TRUE.equals(probeInfo.getMethodEntryToRecursive().get(methodEntryIdx)));
        nodeVo.setAsync(Boolean.TRUE.equals(probeInfo.getMethodEntryToAsync().get(methodEntryIdx)));
        nodeVo.setDone(true);
        nodeVo.setSize(1);
        return nodeVo;
    }

    private ArrayList<Integer> buildExecutedMethodEntries(int methodEntryIdx, ClassProbeInfo probeInfo) {
        ArrayList<Integer> execMethodList = new ArrayList<>(1);
        int methodEntryLine = probeInfo.getProbeLineNumbers()[methodEntryIdx];
        if (methodEntryLine > 0) {
            execMethodList.add(methodEntryLine);
        }
        return execMethodList;
    }

    private CoverageLines collectCoverageLines(List<Integer> executedProbeIndices, ClassProbeInfo probeInfo) {
        LinkedHashSet<Integer> executedLines = new LinkedHashSet<>();
        LinkedHashSet<Integer> executedBranchLines = new LinkedHashSet<>();
        boolean[] branchFlags = probeInfo.getProbeIsBranch();
        int[] probeLineNumbers = probeInfo.getProbeLineNumbers();

        for (int probeIdx : executedProbeIndices) {
            boolean isBranch = probeIdx < branchFlags.length && branchFlags[probeIdx];
            if (isBranch) {
                Integer branchLine = probeInfo.getBranchProbeToLine().get(probeIdx);
                if (branchLine != null && branchLine > 0) {
                    executedBranchLines.add(branchLine);
                }
                continue;
            }

            int line = probeLineNumbers[probeIdx];
            if (line > 0) {
                executedLines.add(line);
            }
        }

        executedLines.removeAll(executedBranchLines);
        return new CoverageLines(executedLines, executedBranchLines);
    }

    private MethodSignature parseMethodSignature(String methodNameDesc) {
        int spaceIdx = methodNameDesc.indexOf(' ');
        String methodName = spaceIdx > 0 ? methodNameDesc.substring(0, spaceIdx) : methodNameDesc;
        String methodDesc = spaceIdx > 0 ? methodNameDesc.substring(spaceIdx + 1) : "";
        return new MethodSignature(methodName, methodDesc);
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
        Map<Integer, BranchExecutionStatus> branchExecutionMap = collectBranchExecutionStatus(executedProbeIndices, probeInfo);
        if (branchExecutionMap.isEmpty()) {
            return null;
        }

        Map<String, List<List<String>>> mcdc = new LinkedHashMap<>();
        Map<Integer, Integer> branchConditionCounts = probeInfo.getBranchLineToConditionCount();
        for (Map.Entry<Integer, BranchExecutionStatus> entry : branchExecutionMap.entrySet()) {
            int branchLine = entry.getKey();
            BranchExecutionStatus status = entry.getValue();
            int conditionCount = branchConditionCounts.getOrDefault(branchLine, 1);
            List<List<String>> combinations = McdcCoverageSupport.buildObservedCoverageForBranch(
                    conditionCount, status.falseExecuted, status.trueExecuted);
            if (!combinations.isEmpty()) {
                mcdc.put(String.valueOf(branchLine), combinations);
            }
        }
        return mcdc.isEmpty() ? null : mcdc;
    }

    private Map<Integer, BranchExecutionStatus> collectBranchExecutionStatus(List<Integer> executedProbeIndices,
                                                                             ClassProbeInfo probeInfo) {
        Map<Integer, BranchExecutionStatus> branchExecutionMap = new LinkedHashMap<>();
        Map<Integer, Integer> branchTrueToFalse = probeInfo.getBranchTrueToFalseProbe();
        for (int probeIdx : executedProbeIndices) {
            Integer branchLine = probeInfo.getBranchProbeToLine().get(probeIdx);
            if (branchLine == null || branchLine <= 0) {
                continue;
            }
            BranchExecutionStatus status = branchExecutionMap.computeIfAbsent(branchLine, key -> new BranchExecutionStatus());
            if (branchTrueToFalse.containsKey(probeIdx)) {
                status.trueExecuted = true;
            } else {
                status.falseExecuted = true;
            }
        }
        return branchExecutionMap;
    }

    private static final class MethodSignature {
        private final String methodName;
        private final String methodDesc;

        private MethodSignature(String methodName, String methodDesc) {
            this.methodName = methodName;
            this.methodDesc = methodDesc;
        }
    }

    private static final class CoverageLines {
        private final LinkedHashSet<Integer> executedLines;
        private final LinkedHashSet<Integer> executedBranchLines;

        private CoverageLines(LinkedHashSet<Integer> executedLines, LinkedHashSet<Integer> executedBranchLines) {
            this.executedLines = executedLines;
            this.executedBranchLines = executedBranchLines;
        }
    }

    private static final class BranchExecutionStatus {
        private boolean falseExecuted;
        private boolean trueExecuted;
    }
}
