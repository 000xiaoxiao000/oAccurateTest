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
     * 分支探针索引 -> 分支中的条件编号（同一源码行内从 1 开始）
     */
    private final Map<Integer, Integer> branchProbeToConditionNumber;

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
        this.branchProbeToConditionNumber = new HashMap<>();
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

    public void setBranchInfo(int branchProbeIdx, int branchLine, int conditionNumber) {
        branchProbeToLine.put(branchProbeIdx, branchLine);
        branchProbeToConditionNumber.put(branchProbeIdx, conditionNumber);
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
    public Map<Integer, Integer> getBranchProbeToConditionNumber() { return branchProbeToConditionNumber; }
    public Map<Integer, Boolean> getMethodEntryToRecursive() { return methodEntryToRecursive; }
    public Map<Integer, Boolean> getMethodEntryToAsync() { return methodEntryToAsync; }

    public int getProbeCount() {
        return probeLineNumbers.length;
    }
}
