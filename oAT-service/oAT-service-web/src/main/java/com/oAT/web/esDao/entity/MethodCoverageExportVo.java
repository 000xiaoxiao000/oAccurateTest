package com.oAT.web.esDao.entity;

import com.alibaba.excel.annotation.ExcelProperty;

public class MethodCoverageExportVo {

    @ExcelProperty("类名")
    private String className;

    @ExcelProperty("类-方法 (覆盖/总)")
    private String classMethodCoverage;

    @ExcelProperty("类-方法覆盖率")
    private String classMethodCoverageRate;

    @ExcelProperty("类-代码行 (覆盖/总)")
    private String classLineCoverage;

    @ExcelProperty("类-代码行覆盖率")
    private String classLineCoverageRate;

    @ExcelProperty("方法名")
    private String methodName;

    @ExcelProperty("方法描述")
    private String methodDesc;

    @ExcelProperty("方法-代码行 (覆盖/总)")
    private String lineCoverage;

    @ExcelProperty("方法-代码行覆盖率")
    private String lineCoverageRate;

    @ExcelProperty("方法-分支 (覆盖/总)")
    private String branchCoverage;

    @ExcelProperty("方法-分支覆盖率")
    private String branchCoverageRate;

    @ExcelProperty("方法-圈复杂度")
    private int complexity;

    @ExcelProperty("是否已覆盖")
    private String isCovered;

    public String getClassName() {
        return className;
    }

    public void setClassName(String className) {
        this.className = className;
    }

    public String getClassMethodCoverage() {
        return classMethodCoverage;
    }

    public void setClassMethodCoverage(String classMethodCoverage) {
        this.classMethodCoverage = classMethodCoverage;
    }

    public String getClassMethodCoverageRate() {
        return classMethodCoverageRate;
    }

    public void setClassMethodCoverageRate(String classMethodCoverageRate) {
        this.classMethodCoverageRate = classMethodCoverageRate;
    }

    public String getClassLineCoverage() {
        return classLineCoverage;
    }

    public void setClassLineCoverage(String classLineCoverage) {
        this.classLineCoverage = classLineCoverage;
    }

    public String getClassLineCoverageRate() {
        return classLineCoverageRate;
    }

    public void setClassLineCoverageRate(String classLineCoverageRate) {
        this.classLineCoverageRate = classLineCoverageRate;
    }

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

    public String getLineCoverage() {
        return lineCoverage;
    }

    public void setLineCoverage(String lineCoverage) {
        this.lineCoverage = lineCoverage;
    }

    public String getLineCoverageRate() {
        return lineCoverageRate;
    }

    public void setLineCoverageRate(String lineCoverageRate) {
        this.lineCoverageRate = lineCoverageRate;
    }

    public String getBranchCoverage() {
        return branchCoverage;
    }

    public void setBranchCoverage(String branchCoverage) {
        this.branchCoverage = branchCoverage;
    }

    public String getBranchCoverageRate() {
        return branchCoverageRate;
    }

    public void setBranchCoverageRate(String branchCoverageRate) {
        this.branchCoverageRate = branchCoverageRate;
    }

    public int getComplexity() {
        return complexity;
    }

    public void setComplexity(int complexity) {
        this.complexity = complexity;
    }

    public String getIsCovered() {
        return isCovered;
    }

    public void setIsCovered(String isCovered) {
        this.isCovered = isCovered;
    }
}
