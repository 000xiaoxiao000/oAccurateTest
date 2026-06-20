package com.oAT.agent.jacoco;

import java.util.HashMap;
import java.util.Map;

/**
 * 类探针元信息：记录探针数组中每个探针索引对应的语义（行号、是否分支、所属方法等）。
 */
public class ClassProbeInfo {

    private final long classId;
    private final String className;
    private final int[] probeLineNumbers;
    private final boolean[] probeIsBranch;
    private final int[] probeMethodEntryIndex;
    private final Map<Integer, String> methodEntryToName;
    private final Map<Integer, int[]> methodEntryToLineTotals;
    private final Map<Integer, int[]> methodEntryToBranchTotals;
    private final Map<Integer, Integer> methodEntryToCyclo;
    private final Map<Integer, Integer> branchProbeToLine;
    private final Map<Integer, Integer> branchProbeToPathId;
    private final Map<Integer, Boolean> methodEntryToRecursive;
    private final Map<Integer, Boolean> methodEntryToAsync;

    public ClassProbeInfo(long classId, String className, int probeCount) {
        this.classId = classId;
        this.className = className;
        this.probeLineNumbers = new int[probeCount];
        this.probeIsBranch = new boolean[probeCount];
        this.probeMethodEntryIndex = new int[probeCount];
        this.methodEntryToName = new HashMap();
        this.methodEntryToLineTotals = new HashMap();
        this.methodEntryToBranchTotals = new HashMap();
        this.methodEntryToCyclo = new HashMap();
        this.branchProbeToLine = new HashMap();
        this.branchProbeToPathId = new HashMap();
        this.methodEntryToRecursive = new HashMap();
        this.methodEntryToAsync = new HashMap();
    }

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

    public void setBranchInfo(int branchProbeIdx, int branchLine, int branchPathId) {
        branchProbeToLine.put(branchProbeIdx, branchLine);
        branchProbeToPathId.put(branchProbeIdx, branchPathId);
    }

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
    public Map<Integer, Integer> getBranchProbeToPathId() { return branchProbeToPathId; }
    public Map<Integer, Boolean> getMethodEntryToRecursive() { return methodEntryToRecursive; }
    public Map<Integer, Boolean> getMethodEntryToAsync() { return methodEntryToAsync; }

    public int getProbeCount() {
        return probeLineNumbers.length;
    }
}
