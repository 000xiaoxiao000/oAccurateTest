package com.oAT.web.esDao.entity;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

public class StaticSourceMethodInfo implements Serializable {
    private String methodName;
    private String methodDesc;
    private List<Integer> methodLineNumberMap;
    private List<Integer> branchLineNumberSet;
    private Map<String, List<Integer>> branchLineAndConditionNumberMap;
    private Integer totalBranchCount;
    private Integer cyclomaticComplexityMap;
    private Boolean recursiveMap;
    private Boolean asyncMethodMap;
    private String methodUri;

    public String getMethodName() {
        return methodName;
    }

    public void setMethodName(String methodName) {
        this.methodName = methodName;
    }

    public String getMethodDesc() {
        return methodDesc;
    }

    public void setMethodDesc(String methodDesc) {
        this.methodDesc = methodDesc;
    }

    public List<Integer> getMethodLineNumberMap() {
        return methodLineNumberMap;
    }

    public void setMethodLineNumberMap(List<Integer> methodLineNumberMap) {
        this.methodLineNumberMap = methodLineNumberMap;
    }

    public List<Integer> getBranchLineNumberSet() {
        return branchLineNumberSet;
    }

    public void setBranchLineNumberSet(List<Integer> branchLineNumberSet) {
        this.branchLineNumberSet = branchLineNumberSet;
    }

    public Map<String, List<Integer>> getBranchLineAndConditionNumberMap() {
        return branchLineAndConditionNumberMap;
    }

    public void setBranchLineAndConditionNumberMap(Map<String, List<Integer>> branchLineAndConditionNumberMap) {
        this.branchLineAndConditionNumberMap = branchLineAndConditionNumberMap;
    }

    public Integer getTotalBranchCount() {
        return totalBranchCount;
    }

    public void setTotalBranchCount(Integer totalBranchCount) {
        this.totalBranchCount = totalBranchCount;
    }

    public Integer getCyclomaticComplexityMap() {
        return cyclomaticComplexityMap;
    }

    public void setCyclomaticComplexityMap(Integer cyclomaticComplexityMap) {
        this.cyclomaticComplexityMap = cyclomaticComplexityMap;
    }

    public Boolean getRecursiveMap() {
        return recursiveMap;
    }

    public void setRecursiveMap(Boolean recursiveMap) {
        this.recursiveMap = recursiveMap;
    }

    public Boolean getAsyncMethodMap() {
        return asyncMethodMap;
    }

    public void setAsyncMethodMap(Boolean asyncMethodMap) {
        this.asyncMethodMap = asyncMethodMap;
    }

    public String getMethodUri() {
        return methodUri;
    }

    public void setMethodUri(String methodUri) {
        this.methodUri = methodUri;
    }
}
