package com.oAT.web.esDao.entity;

import com.alibaba.excel.annotation.ExcelIgnore;
import com.alibaba.excel.annotation.ExcelProperty;
import org.springframework.data.annotation.Id;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

public class ClassCoverageIndex implements Serializable {
    @Id
    @ExcelIgnore
    private String id;
    @ExcelIgnore
    private String reportId;
    @ExcelIgnore
    private String appId;
    @ExcelProperty("类名")
    private String className;

    @ExcelIgnore
    private String sourceType;

    @ExcelProperty("方法总数")
    private int totalMethods;
    @ExcelProperty("已覆盖方法数")
    private int coveredMethods;
    @ExcelProperty("分支总数")
    private int totalBranches;
    @ExcelProperty("已覆盖分支数")
    private int coveredBranches;
    @ExcelIgnore
    private int totalBranchTargets;
    @ExcelIgnore
    private int coveredBranchTargets;
    @ExcelProperty("总行数")
    private int totalLines;
    @ExcelProperty("已覆盖行数")
    private int coveredLines;
    @ExcelProperty("圈复杂度")
    private int totalComplexity;

    private Double lineRate;

    private Double branchRate;

    private Double methodRate;

    @ExcelIgnore
    private List<MethodCoverageDetail> methods;

    @ExcelIgnore
    private Boolean hasCodeChanges;

    public static class MethodCoverageDetail implements Serializable {
        private String methodName;
        private String methodDesc;
        private int totalLines;
        private int coveredLines;
        private int totalBranches;
        private int coveredBranches;
        private int complexity;
        private boolean isCovered;
        private List<Integer> coveredLineNumbers;
        private List<Integer> totalLineNumbers;
        private List<Integer> coveredBranchLines;
        private Map<String, List<Integer>> totalBranchTargetProbeMap;
        private Map<String, List<Integer>> coveredBranchTargetProbeMap;
        private int totalBranchTargets;
        private int coveredBranchTargets;
        private Double branchRate;
        private boolean hasCodeChanges;
        public String getMethodName() { return methodName; }
        public void setMethodName(String methodName) { this.methodName = methodName; }
        public String getMethodDesc() { return methodDesc; }
        public void setMethodDesc(String methodDesc) { this.methodDesc = methodDesc; }
        public int getTotalLines() { return totalLines; }
        public void setTotalLines(int totalLines) { this.totalLines = totalLines; }
        public int getCoveredLines() { return coveredLines; }
        public void setCoveredLines(int coveredLines) { this.coveredLines = coveredLines; }
        public int getTotalBranches() { return totalBranches; }
        public void setTotalBranches(int totalBranches) { this.totalBranches = totalBranches; }
        public int getCoveredBranches() { return coveredBranches; }
        public void setCoveredBranches(int coveredBranches) { this.coveredBranches = coveredBranches; }
        public int getComplexity() { return complexity; }
        public void setComplexity(int complexity) { this.complexity = complexity; }
        public boolean isCovered() { return isCovered; }
        public void setCovered(boolean covered) { isCovered = covered; }
        public List<Integer> getCoveredLineNumbers() { return coveredLineNumbers; }
        public void setCoveredLineNumbers(List<Integer> coveredLineNumbers) { this.coveredLineNumbers = coveredLineNumbers; }
        public List<Integer> getTotalLineNumbers() { return totalLineNumbers; }
        public void setTotalLineNumbers(List<Integer> totalLineNumbers) { this.totalLineNumbers = totalLineNumbers; }
        public List<Integer> getCoveredBranchLines() { return coveredBranchLines; }
        public void setCoveredBranchLines(List<Integer> coveredBranchLines) { this.coveredBranchLines = coveredBranchLines; }
        public Map<String, List<Integer>> getTotalBranchTargetProbeMap() { return totalBranchTargetProbeMap; }
        public void setTotalBranchTargetProbeMap(Map<String, List<Integer>> totalBranchTargetProbeMap) { this.totalBranchTargetProbeMap = totalBranchTargetProbeMap; }
        public Map<String, List<Integer>> getCoveredBranchTargetProbeMap() { return coveredBranchTargetProbeMap; }
        public void setCoveredBranchTargetProbeMap(Map<String, List<Integer>> coveredBranchTargetProbeMap) { this.coveredBranchTargetProbeMap = coveredBranchTargetProbeMap; }
        public int getTotalBranchTargets() { return totalBranchTargets; }
        public void setTotalBranchTargets(int totalBranchTargets) { this.totalBranchTargets = totalBranchTargets; }
        public int getCoveredBranchTargets() { return coveredBranchTargets; }
        public void setCoveredBranchTargets(int coveredBranchTargets) { this.coveredBranchTargets = coveredBranchTargets; }
        public Double getBranchRate() { return branchRate; }
        public void setBranchRate(Double branchRate) { this.branchRate = branchRate; }
        public boolean isHasCodeChanges() { return hasCodeChanges; }
        public void setHasCodeChanges(boolean hasCodeChanges) { this.hasCodeChanges = hasCodeChanges; }
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getReportId() { return reportId; }
    public void setReportId(String reportId) { this.reportId = reportId; }
    public String getAppId() { return appId; }
    public void setAppId(String appId) { this.appId = appId; }
    public String getClassName() { return className; }
    public void setClassName(String className) { this.className = className; }
    public String getSourceType() { return sourceType; }
    public void setSourceType(String sourceType) { this.sourceType = sourceType; }
    public int getTotalMethods() { return totalMethods; }
    public void setTotalMethods(int totalMethods) { this.totalMethods = totalMethods; }
    public int getCoveredMethods() { return coveredMethods; }
    public void setCoveredMethods(int coveredMethods) { this.coveredMethods = coveredMethods; }
    public int getTotalBranches() { return totalBranches; }
    public void setTotalBranches(int totalBranches) { this.totalBranches = totalBranches; }
    public int getCoveredBranches() { return coveredBranches; }
    public void setCoveredBranches(int coveredBranches) { this.coveredBranches = coveredBranches; }
    public int getTotalBranchTargets() { return totalBranchTargets; }
    public void setTotalBranchTargets(int totalBranchTargets) { this.totalBranchTargets = totalBranchTargets; }
    public int getCoveredBranchTargets() { return coveredBranchTargets; }
    public void setCoveredBranchTargets(int coveredBranchTargets) { this.coveredBranchTargets = coveredBranchTargets; }
    public int getTotalLines() { return totalLines; }
    public void setTotalLines(int totalLines) { this.totalLines = totalLines; }
    public int getCoveredLines() { return coveredLines; }
    public void setCoveredLines(int coveredLines) { this.coveredLines = coveredLines; }
    public int getTotalComplexity() { return totalComplexity; }
    public void setTotalComplexity(int totalComplexity) { this.totalComplexity = totalComplexity; }

    public Double getLineRate() { return lineRate; }
    public void setLineRate(Double lineRate) { this.lineRate = lineRate; }
    public Double getBranchRate() { return branchRate; }
    public void setBranchRate(Double branchRate) { this.branchRate = branchRate; }
    public Double getMethodRate() { return methodRate; }
    public void setMethodRate(Double methodRate) { this.methodRate = methodRate; }

    public List<MethodCoverageDetail> getMethods() { return methods; }
    public void setMethods(List<MethodCoverageDetail> methods) { this.methods = methods; }

    public Boolean getHasCodeChanges() { return hasCodeChanges; }
    public void setHasCodeChanges(Boolean hasCodeChanges) { this.hasCodeChanges = hasCodeChanges; }
}
