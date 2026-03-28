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
    /**
     * 代码行数，某个方法的代码总行数
     */
    private ArrayList<Integer> lineTotal = new ArrayList<>(4);

    //方法覆盖率
    //执行到的方法数
    private ArrayList<Integer> executeMethodTotal = new ArrayList<>(1);
    //方法总数
    private ArrayList<Integer> methodTotal = new ArrayList<>(4);

    //分支覆盖率
    //执行到的分支数
    private ArrayList<Integer> executeBranch = new ArrayList<>(4);
    //分支总数
    private ArrayList<Integer> branchTotal = new ArrayList<>(4);

    //条件覆盖率
    //执行到的条件数,map<分支行，第几个条件>
    private Map<String, List<String>> executeCondition = new HashMap<>(8);   //分支中的执行到的条件
    /**
     * 分支中的执行到的条件,用于条件组合的覆盖
     * if(真),if(假),两种分别执行到，为全部覆盖，执行到其中一个为部分覆盖，最后还有没执行到为未覆盖
     * 通过执行多次的记录比对决定最后覆盖情况
     */
    private String execBranchConditionIsTrue;

    /**
     * 圈复杂度
     */
    private String execCyclo = "0";  //方法中执行的圈复杂度
    private int cyclo = 0;  //方法中的圈复杂度

    /**
     * 是否是递归方法
     */
    private boolean isRecursive = false;    // 本节点是递归方法
    /**
     * 是否是异步方法
     */
    private boolean isAsync = false;    // 本节点是异步方法

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

    public ArrayList<Integer> getLineTotal() {
        return lineTotal;
    }

    public void setLineTotal(ArrayList<Integer> lineTotal) {
        this.lineTotal = lineTotal;
    }

    public boolean isDone() {
        return done;
    }

    public void setDone(boolean done) {
        this.done = done;
    }

    public int getCyclo() {
        return cyclo;
    }

    public void setCyclo(int cyclo) {
        this.cyclo = cyclo;
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

    public ArrayList<Integer> getMethodTotal() {
        return methodTotal;
    }

    public void setMethodTotal(ArrayList<Integer> methodTotal) {
        this.methodTotal = methodTotal;
    }

    public ArrayList<Integer> getExecuteBranch() {
        return executeBranch;
    }

    public void setExecuteBranch(ArrayList<Integer> executeBranch) {
        this.executeBranch = executeBranch;
    }

    public ArrayList<Integer> getBranchTotal() {
        return branchTotal;
    }

    public void setBranchTotal(ArrayList<Integer> branchTotal) {
        this.branchTotal = branchTotal;
    }

    public Map<String, List<String>> getExecuteCondition() {
        return executeCondition;
    }

    public void setExecuteCondition(Map<String, List<String>> executeCondition) {
        this.executeCondition = executeCondition;
    }

    public String getExecBranchConditionIsTrue() {
        return execBranchConditionIsTrue;
    }

    public void setExecBranchConditionIsTrue(String execBranchConditionIsTrue) {
        this.execBranchConditionIsTrue = execBranchConditionIsTrue;
    }
}
