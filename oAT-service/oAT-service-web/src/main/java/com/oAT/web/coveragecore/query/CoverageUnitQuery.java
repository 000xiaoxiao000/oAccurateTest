package com.oAT.web.coveragecore.query;

public class CoverageUnitQuery {
    private String className;
    private String methodName;
    private Double minRate;
    private Double maxRate;
    private Double minBranchRate;
    private Double maxBranchRate;
    private Double minMethodRate;
    private Double maxMethodRate;
    private Integer minComplexity;
    private Integer maxComplexity;
    private Integer page;
    private Integer size;

    public String getClassName() { return className; }
    public void setClassName(String className) { this.className = className; }
    public String getMethodName() { return methodName; }
    public void setMethodName(String methodName) { this.methodName = methodName; }
    public Double getMinRate() { return minRate; }
    public void setMinRate(Double minRate) { this.minRate = minRate; }
    public Double getMaxRate() { return maxRate; }
    public void setMaxRate(Double maxRate) { this.maxRate = maxRate; }
    public Double getMinBranchRate() { return minBranchRate; }
    public void setMinBranchRate(Double minBranchRate) { this.minBranchRate = minBranchRate; }
    public Double getMaxBranchRate() { return maxBranchRate; }
    public void setMaxBranchRate(Double maxBranchRate) { this.maxBranchRate = maxBranchRate; }
    public Double getMinMethodRate() { return minMethodRate; }
    public void setMinMethodRate(Double minMethodRate) { this.minMethodRate = minMethodRate; }
    public Double getMaxMethodRate() { return maxMethodRate; }
    public void setMaxMethodRate(Double maxMethodRate) { this.maxMethodRate = maxMethodRate; }
    public Integer getMinComplexity() { return minComplexity; }
    public void setMinComplexity(Integer minComplexity) { this.minComplexity = minComplexity; }
    public Integer getMaxComplexity() { return maxComplexity; }
    public void setMaxComplexity(Integer maxComplexity) { this.maxComplexity = maxComplexity; }
    public Integer getPage() { return page; }
    public void setPage(Integer page) { this.page = page; }
    public Integer getSize() { return size; }
    public void setSize(Integer size) { this.size = size; }

    public int pageOrDefault() {
        return page == null || page < 0 ? 0 : page;
    }

    public int sizeOrDefault() {
        if (size == null || size <= 0) {
            return 20;
        }
        return Math.min(size, 500);
    }
}
