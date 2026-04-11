package com.oAT.agent.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class StackNodeVo implements java.io.Serializable {
    private static final long serialVersionUID = -7156032079009497957L;

    private String id = "";
    private long classId = -1L;
    private String className = "";
    private String methodName = "";
    private String methodDescriptor = "";

    /**
     * 执行到的代码行数
     */
    private ArrayList<Integer> doLines = new ArrayList<>(8);

    private ArrayList<Integer> executeMethodTotal = new ArrayList<>(1);

    /**
     * 已命中的分支行
     */
    private ArrayList<Integer> executeBranch = new ArrayList<>(4);
    /**
     * JaCoCo 风格分支目标命中：分支行 -> 已命中的目标探针编号集合
     */
    private Map<String, List<Integer>> executeBranchTargetProbeMap;

    /**
     * 圈复杂度
     */
    private String execCyclo = "0";

    /**
     * 是否是递归方法
     */
    private boolean isRecursive = false;
    /**
     * 是否是异步方法
     */
    private boolean isAsync = false;

    private boolean done;
    private Integer size;
    /**
     * nano time
     */
    private Long useTime;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public long getClassId() {
        return classId;
    }

    public void setClassId(long classId) {
        this.classId = classId;
    }

    public String getClassName() {
        return className;
    }

    public void setClassName(String className) {
        this.className = className;
    }

    public String getMethodName() {
        return methodName;
    }

    public void setMethodName(String methodName) {
        this.methodName = methodName;
    }

    public String getMethodDescriptor() {
        return methodDescriptor;
    }

    public void setMethodDescriptor(String methodDescriptor) {
        this.methodDescriptor = methodDescriptor;
    }

    public Long getUseTime() {
        return useTime;
    }

    public void setUseTime(Long useTime) {
        this.useTime = useTime;
    }

    public ArrayList<Integer> getDoLines() {
        return doLines;
    }

    public void setDoLines(ArrayList<Integer> doLines) {
        this.doLines = doLines;
    }

    public boolean isDone() {
        return done;
    }

    public void setDone(boolean done) {
        this.done = done;
    }

    public String getExecCyclo() {
        return execCyclo;
    }

    public void setExecCyclo(String execCyclo) {
        this.execCyclo = execCyclo;
    }

    public boolean isRecursive() {
        return isRecursive;
    }

    public void setRecursive(boolean recursive) {
        isRecursive = recursive;
    }

    public boolean isAsync() {
        return isAsync;
    }

    public void setAsync(boolean async) {
        isAsync = async;
    }

    public Integer getSize() {
        return size;
    }

    public void setSize(Integer size) {
        this.size = size;
    }

    public String parentId() {
        if ("0".equals(id)) {
            return "ROOT";
        }
        return id.substring(0, id.lastIndexOf("."));
    }

    public ArrayList<Integer> getExecuteMethodTotal() {
        return executeMethodTotal;
    }

    public void setExecuteMethodTotal(ArrayList<Integer> executeMethodTotal) {
        this.executeMethodTotal = executeMethodTotal;
    }

    public ArrayList<Integer> getExecuteBranch() {
        return executeBranch;
    }

    public void setExecuteBranch(ArrayList<Integer> executeBranch) {
        this.executeBranch = executeBranch;
    }

    public Map<String, List<Integer>> getExecuteBranchTargetProbeMap() {
        return executeBranchTargetProbeMap;
    }

    public void setExecuteBranchTargetProbeMap(Map<String, List<Integer>> executeBranchTargetProbeMap) {
        this.executeBranchTargetProbeMap = executeBranchTargetProbeMap;
    }

}
