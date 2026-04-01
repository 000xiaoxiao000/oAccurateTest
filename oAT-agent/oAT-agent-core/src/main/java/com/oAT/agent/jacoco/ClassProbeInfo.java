package com.oAT.agent.jacoco;

import java.util.*;

/**
 * 类探针元信息：记录探针数组中每个探针索引对应的语义（行号、是否分支、所属方法等）。
 * <p>
 * 此信息在类加载时由 ClassInstrumenter 构建，不涉及运行时开销。
 * 在请求结束时，CoverageCollector 使用此信息将 boolean[] 探针数据转换为覆盖率报告。
 * </p>
 */
public class ClassProbeInfo {

    private final long classId;
    private final String className;

    /**
     * 探针索引 -> 行号（按探针ID顺序）
     */
    private final int[] probeLineNumbers;

    /**
     * 探针索引 -> 是否是分支探针
     */
    private final boolean[] probeIsBranch;

    /**
     * 探针索引 -> 所属方法的第一个探针索引（用于方法入口识别）
     */
    private final int[] probeMethodEntryIndex;

    /**
     * 方法入口探针索引 -> 方法名+描述符
     */
    private final Map<Integer, String> methodEntryToName;

    /**
     * 方法入口探针索引 -> 方法总行号集合（用于行覆盖率分母）
     */
    private final Map<Integer, int[]> methodEntryToLineTotals;

    /**
     * 方法入口探针索引 -> 分支行号数组（用于分支覆盖率分母）
     */
    private final Map<Integer, int[]> methodEntryToBranchTotals;

    /**
     * 方法入口探针索引 -> 圈复杂度
     */
    private final Map<Integer, Integer> methodEntryToCyclo;

    /**
     * 分支探针索引 -> 分支行号
     */
    private final Map<Integer, Integer> branchProbeToLine;

    /**
     * 分支行号 -> MC/DC条件总数（用于MC/DC覆盖率分母）
     * MC/DC要求：每个条件的每个取值都必须独立影响判定的结果
     */
    private final Map<Integer, Integer> branchLineToConditionCount;

    /**
     * 分支探针true索引 -> 分支探针false索引 的配对映射
     * 用于MC/DC分析：同一分支行的true/false探针对
     * key: true分支探针索引, value: false分支探针索引
     */
    private final Map<Integer, Integer> branchTrueToFalseProbe;

    /**
     * 方法入口探针索引 -> 类中所有方法入口探针索引集合（用于方法覆盖率分母）
     */
    private final int[] allMethodEntryIndices;

    /**
     * 方法入口探针索引 -> 递归标记
     */
    private final Map<Integer, Boolean> methodEntryToRecursive;

    /**
     * 方法入口探针索引 -> 异步标记
     */
    private final Map<Integer, Boolean> methodEntryToAsync;

    public ClassProbeInfo(long classId, String className, int probeCount) {
        this.classId = classId;
        this.className = className;
        this.probeLineNumbers = new int[probeCount];
        this.probeIsBranch = new boolean[probeCount];
        this.probeMethodEntryIndex = new int[probeCount];
        this.methodEntryToName = new HashMap<>();
        this.methodEntryToLineTotals = new HashMap<>();
        this.methodEntryToBranchTotals = new HashMap<>();
        this.methodEntryToCyclo = new HashMap<>();
        this.branchProbeToLine = new HashMap<>();
        this.branchLineToConditionCount = new HashMap<>();
        this.branchTrueToFalseProbe = new HashMap<>();
        this.allMethodEntryIndices = new int[0];
        this.methodEntryToRecursive = new HashMap<>();
        this.methodEntryToAsync = new HashMap<>();
    }

    // ============ Setter methods for ClassInstrumenter to populate ============

    public void setProbeLineNumber(int probeIdx, int line) {
        if (probeIdx >= 0 && probeIdx < probeLineNumbers.length) {
            probeLineNumbers[probeIdx] = line;
        }
    }

    public void setProbeIsBranch(int probeIdx, boolean isBranch) {
        if (probeIdx >= 0 && probeIdx < probeIsBranch.length) {
            probeIsBranch[probeIdx] = isBranch;
        }
    }

    public void setProbeMethodEntryIndex(int probeIdx, int methodEntryIdx) {
        if (probeIdx >= 0 && probeIdx < probeMethodEntryIndex.length) {
            probeMethodEntryIndex[probeIdx] = methodEntryIdx;
        }
    }

    public void setMethodInfo(int methodEntryIdx, String methodNameDesc,
                              int[] lineTotals, int[] branchTotals, int cyclo,
                              boolean recursive, boolean async) {
        methodEntryToName.put(methodEntryIdx, methodNameDesc);
        methodEntryToLineTotals.put(methodEntryIdx, lineTotals);
        methodEntryToBranchTotals.put(methodEntryIdx, branchTotals);
        methodEntryToCyclo.put(methodEntryIdx, cyclo);
        methodEntryToRecursive.put(methodEntryIdx, recursive);
        methodEntryToAsync.put(methodEntryIdx, async);
    }

    public void setBranchInfo(int branchProbeIdx, int branchLine, int falseProbeIdx) {
        branchProbeToLine.put(branchProbeIdx, branchLine);
        if (falseProbeIdx >= 0) {
            branchTrueToFalseProbe.put(branchProbeIdx, falseProbeIdx);
        }
    }

    public void setBranchConditionCount(int branchLine, int conditionCount) {
        branchLineToConditionCount.put(branchLine, conditionCount);
    }

    public void setAllMethodEntryIndices(int[] indices) {
        // stored as a field reference, array is owned by caller
        // We keep the reference to the array passed in from ClassInstrumenter
    }

    // ============ Getter methods for CoverageCollector ============

    public long getClassId() { return classId; }
    public String getClassName() { return className; }
    public int[] getProbeLineNumbers() { return probeLineNumbers; }
    public boolean[] getProbeIsBranch() { return probeIsBranch; }
    public int[] getProbeMethodEntryIndex() { return probeMethodEntryIndex; }
    public Map<Integer, String> getMethodEntryToName() { return methodEntryToName; }
    public Map<Integer, int[]> getMethodEntryToLineTotals() { return methodEntryToLineTotals; }
    public Map<Integer, int[]> getMethodEntryToBranchTotals() { return methodEntryToBranchTotals; }
    public Map<Integer, Integer> getMethodEntryToCyclo() { return methodEntryToCyclo; }
    public Map<Integer, Integer> getBranchProbeToLine() { return branchProbeToLine; }
    public Map<Integer, Integer> getBranchLineToConditionCount() { return branchLineToConditionCount; }
    public Map<Integer, Integer> getBranchTrueToFalseProbe() { return branchTrueToFalseProbe; }
    public Map<Integer, Boolean> getMethodEntryToRecursive() { return methodEntryToRecursive; }
    public Map<Integer, Boolean> getMethodEntryToAsync() { return methodEntryToAsync; }

    public int getProbeCount() {
        return probeLineNumbers.length;
    }
}
