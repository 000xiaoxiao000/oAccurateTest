package com.oAT.agent.model;

import java.util.ArrayList;
import java.util.HashMap;
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

    //方法覆盖率
    //执行到的方法数
    private ArrayList<Integer> executeMethodTotal = new ArrayList<>(1);

    //分支覆盖率
    //执行到的分支数
    private ArrayList<Integer> executeBranch = new ArrayList<>(4);

    //条件覆盖率
    //执行到的条件数,map<分支行，第几个条件>
    private Map<String, List<String>> executeCondition = new HashMap<>(8);   //分支中的执行到的条件

    /**
     * MC/DC（修订的条件/判定覆盖）覆盖率数据
     * <p>
     * MC/DC 要求：对于判定中的每个条件C，至少存在一个测试用例使得：
     * - 判定结果因C的取值从真变为假（或从假变为真），而其他所有条件的取值保持不变。
     * <p>
     * 格式: Map<"分支行号", List<List<String>>>
     * - key: 分支行号
     * - value: 条件真值组合列表。每个 List<String> 是一次执行中各条件的 true/false 序列。
     *   通过多次执行收集不同的真值组合，判断是否满足 MC/DC 覆盖标准。
     * <p>
     * 替代旧的 execBranchConditionIsTrue 字段。
     */
    private Map<String, List<List<String>>> mcdcCoverage = new HashMap<>(8);

    /**
     * 圈复杂度
     */
    private String execCyclo = "0";  //方法中执行的圈复杂度

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

    // ========== Getter 和 Setter 方法 ==========

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

    public Map<String, List<String>> getExecuteCondition() {
        return executeCondition;
    }

    public void setExecuteCondition(Map<String, List<String>> executeCondition) {
        this.executeCondition = executeCondition;
    }

    public Map<String, List<List<String>>> getMcdcCoverage() {
        return mcdcCoverage;
    }

    public void setMcdcCoverage(Map<String, List<List<String>>> mcdcCoverage) {
        this.mcdcCoverage = mcdcCoverage;
    }
}
