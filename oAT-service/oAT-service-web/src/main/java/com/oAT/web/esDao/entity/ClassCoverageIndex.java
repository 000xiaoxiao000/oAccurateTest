package com.oAT.web.esDao.entity;

import com.alibaba.excel.annotation.ExcelIgnore;
import com.alibaba.excel.annotation.ExcelProperty;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

import java.io.Serializable;
import java.util.List;

@Document(indexName = "class_coverage", type = "doc", shards = 2)
public class ClassCoverageIndex implements Serializable {
    @Id
    @ExcelIgnore
    private String id; // reportId + className hash
    @Field(type = FieldType.Keyword)
    @ExcelIgnore
    private String reportId;
    @Field(type = FieldType.Keyword)
    @ExcelIgnore
    private String appId;
    @Field(type = FieldType.Keyword)
    @ExcelProperty("类名")
    private String className;

    // Class Stats
    @ExcelProperty("方法总数")
    private int totalMethods;
    @ExcelProperty("已覆盖方法数")
    private int coveredMethods;
    @ExcelProperty("分支总数")
    private int totalBranches;
    @ExcelProperty("已覆盖分支数")
    private int coveredBranches;
    @ExcelProperty("总行数")
    private int totalLines;
    @ExcelProperty("已覆盖行数")
    private int coveredLines;
    @ExcelProperty("圈复杂度")
    private int totalComplexity;

    @Field(type = FieldType.Double)
    private Double lineRate;

    @Field(type = FieldType.Double)
    private Double branchRate;

    @Field(type = FieldType.Double)
    private Double methodRate;

    // Method Details
    @Field(type = FieldType.Object)
    @ExcelIgnore
    private List<MethodCoverageDetail> methods;

    public static class MethodCoverageDetail implements Serializable {
        private String methodName;
        private String methodDesc;
        private int totalLines;
        private int coveredLines;
        private int totalBranches;
        private int coveredBranches;
        private int complexity;
        private boolean isCovered;
        // Covered line numbers for coloring
        private List<Integer> coveredLineNumbers;
        // All line numbers from static info
        private List<Integer> totalLineNumbers;
        // Covered branch identifiers for idempotency
        private List<Integer> coveredBranchIds;
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
        public List<Integer> getCoveredBranchIds() { return coveredBranchIds; }
        public void setCoveredBranchIds(List<Integer> coveredBranchIds) { this.coveredBranchIds = coveredBranchIds; }
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getReportId() { return reportId; }
    public void setReportId(String reportId) { this.reportId = reportId; }
    public String getAppId() { return appId; }
    public void setAppId(String appId) { this.appId = appId; }
    public String getClassName() { return className; }
    public void setClassName(String className) { this.className = className; }
    public int getTotalMethods() { return totalMethods; }
    public void setTotalMethods(int totalMethods) { this.totalMethods = totalMethods; }
    public int getCoveredMethods() { return coveredMethods; }
    public void setCoveredMethods(int coveredMethods) { this.coveredMethods = coveredMethods; }
    public int getTotalBranches() { return totalBranches; }
    public void setTotalBranches(int totalBranches) { this.totalBranches = totalBranches; }
    public int getCoveredBranches() { return coveredBranches; }
    public void setCoveredBranches(int coveredBranches) { this.coveredBranches = coveredBranches; }
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
}
