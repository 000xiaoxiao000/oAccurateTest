package com.oAT.web.esDao.entity;

import com.alibaba.excel.annotation.ExcelProperty;

public class MethodCoverageExportVo {

    @ExcelProperty("类名")
    private String className;

    @ExcelProperty("类-方法总数")
    private int classTotalMethods;

    @ExcelProperty("类-已覆盖方法数")
    private int classCoveredMethods;

    @ExcelProperty("类-总行数")
    private int classTotalLines;

    @ExcelProperty("类-已覆盖行数")
    private int classCoveredLines;

    @ExcelProperty("方法名")
    private String methodName;

    @ExcelProperty("方法描述")
    private String methodDesc;

    @ExcelProperty("方法-总行数")
    private int totalLines;

    @ExcelProperty("方法-已覆盖行数")
    private int coveredLines;

    @ExcelProperty("方法-行覆盖率")
    private String lineCoverageRate;

    @ExcelProperty("方法-分支总数")
    private int totalBranches;

    @ExcelProperty("方法-已覆盖分支数")
    private int coveredBranches;

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

    public int getClassTotalMethods() {
        return classTotalMethods;
    }

    public void setClassTotalMethods(int classTotalMethods) {
        this.classTotalMethods = classTotalMethods;
    }

    public int getClassCoveredMethods() {
        return classCoveredMethods;
    }

    public void setClassCoveredMethods(int classCoveredMethods) {
        this.classCoveredMethods = classCoveredMethods;
    }

    public int getClassTotalLines() {
        return classTotalLines;
    }

    public void setClassTotalLines(int classTotalLines) {
        this.classTotalLines = classTotalLines;
    }

    public int getClassCoveredLines() {
        return classCoveredLines;
    }

    public void setClassCoveredLines(int classCoveredLines) {
        this.classCoveredLines = classCoveredLines;
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

    public int getTotalLines() {
        return totalLines;
    }

    public void setTotalLines(int totalLines) {
        this.totalLines = totalLines;
    }

    public int getCoveredLines() {
        return coveredLines;
    }

    public void setCoveredLines(int coveredLines) {
        this.coveredLines = coveredLines;
    }

    public String getLineCoverageRate() {
        return lineCoverageRate;
    }

    public void setLineCoverageRate(String lineCoverageRate) {
        this.lineCoverageRate = lineCoverageRate;
    }

    public int getTotalBranches() {
        return totalBranches;
    }

    public void setTotalBranches(int totalBranches) {
        this.totalBranches = totalBranches;
    }

    public int getCoveredBranches() {
        return coveredBranches;
    }

    public void setCoveredBranches(int coveredBranches) {
        this.coveredBranches = coveredBranches;
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
