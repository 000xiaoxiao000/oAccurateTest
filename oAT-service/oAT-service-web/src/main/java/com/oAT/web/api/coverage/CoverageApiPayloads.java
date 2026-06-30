package com.oAT.web.api.coverage;

import com.oAT.web.coveragecore.model.SourceCoveragePayload;
import com.oAT.web.service.entity.CoverageTreeNode;

import java.util.List;

public final class CoverageApiPayloads {

    private CoverageApiPayloads() {
    }

    public static class CoverageOverviewPayload {
        private ProjectSummary project;
        private List<AppSummary> apps;
        private AppSummary app;
        private VersionSummary version;
        private CoverageReportSummary report;
        private CoverageReportSummary versionFullReport;
        private CoverageReportSummary currentCommitReport;
        private CoverageReportSummary incrementalReport;
        private Boolean hasNewerData;
        private CoverageComparisonSummary comparison;
        private CoverageComparisonSummary versionFullComparison;
        private CoverageComparisonSummary currentCommitComparison;
        private CoverageComparisonSummary incrementalComparison;
        private String currentUserRole;
        private String mascotPrimary;

        public ProjectSummary getProject() { return project; }
        public void setProject(ProjectSummary project) { this.project = project; }
        public List<AppSummary> getApps() { return apps; }
        public void setApps(List<AppSummary> apps) { this.apps = apps; }
        public AppSummary getApp() { return app; }
        public void setApp(AppSummary app) { this.app = app; }
        public VersionSummary getVersion() { return version; }
        public void setVersion(VersionSummary version) { this.version = version; }
        public CoverageReportSummary getReport() { return report; }
        public void setReport(CoverageReportSummary report) { this.report = report; }
        public CoverageReportSummary getVersionFullReport() { return versionFullReport; }
        public void setVersionFullReport(CoverageReportSummary versionFullReport) { this.versionFullReport = versionFullReport; }
        public CoverageReportSummary getCurrentCommitReport() { return currentCommitReport; }
        public void setCurrentCommitReport(CoverageReportSummary currentCommitReport) { this.currentCommitReport = currentCommitReport; }
        public CoverageReportSummary getIncrementalReport() { return incrementalReport; }
        public void setIncrementalReport(CoverageReportSummary incrementalReport) { this.incrementalReport = incrementalReport; }
        public Boolean getHasNewerData() { return hasNewerData; }
        public void setHasNewerData(Boolean hasNewerData) { this.hasNewerData = hasNewerData; }
        public CoverageComparisonSummary getComparison() { return comparison; }
        public void setComparison(CoverageComparisonSummary comparison) { this.comparison = comparison; }
        public CoverageComparisonSummary getVersionFullComparison() { return versionFullComparison; }
        public void setVersionFullComparison(CoverageComparisonSummary versionFullComparison) { this.versionFullComparison = versionFullComparison; }
        public CoverageComparisonSummary getCurrentCommitComparison() { return currentCommitComparison; }
        public void setCurrentCommitComparison(CoverageComparisonSummary currentCommitComparison) { this.currentCommitComparison = currentCommitComparison; }
        public CoverageComparisonSummary getIncrementalComparison() { return incrementalComparison; }
        public void setIncrementalComparison(CoverageComparisonSummary incrementalComparison) { this.incrementalComparison = incrementalComparison; }
        public String getCurrentUserRole() { return currentUserRole; }
        public void setCurrentUserRole(String currentUserRole) { this.currentUserRole = currentUserRole; }
        public String getMascotPrimary() { return mascotPrimary; }
        public void setMascotPrimary(String mascotPrimary) { this.mascotPrimary = mascotPrimary; }
    }

    public static class CoverageDetailsPayload {
        private CoverageReportSummary report;
        private AppSummary app;
        private VersionSummary version;
        private Boolean reportNeedRegenerate;
        private String currentUserRole;
        private String viewType;
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
        private PageSummary<ClassCoverageSummary> classPage;
        private List<CoverageTreeNode> treeNodes;

        public CoverageReportSummary getReport() { return report; }
        public void setReport(CoverageReportSummary report) { this.report = report; }
        public AppSummary getApp() { return app; }
        public void setApp(AppSummary app) { this.app = app; }
        public VersionSummary getVersion() { return version; }
        public void setVersion(VersionSummary version) { this.version = version; }
        public Boolean getReportNeedRegenerate() { return reportNeedRegenerate; }
        public void setReportNeedRegenerate(Boolean reportNeedRegenerate) { this.reportNeedRegenerate = reportNeedRegenerate; }
        public String getCurrentUserRole() { return currentUserRole; }
        public void setCurrentUserRole(String currentUserRole) { this.currentUserRole = currentUserRole; }
        public String getViewType() { return viewType; }
        public void setViewType(String viewType) { this.viewType = viewType; }
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
        public PageSummary<ClassCoverageSummary> getClassPage() { return classPage; }
        public void setClassPage(PageSummary<ClassCoverageSummary> classPage) { this.classPage = classPage; }
        public List<CoverageTreeNode> getTreeNodes() { return treeNodes; }
        public void setTreeNodes(List<CoverageTreeNode> treeNodes) { this.treeNodes = treeNodes; }
    }

    public static class CoverageCodePayload {
        private AppSummary app;
        private CoverageReportSummary report;
        private String className;
        private List<MethodCoverageSummary> methods;
        private SourceCoveragePayload sourceCoverage;
        private String coloredSourceHtml;
        private String currentUserRole;

        public AppSummary getApp() { return app; }
        public void setApp(AppSummary app) { this.app = app; }
        public CoverageReportSummary getReport() { return report; }
        public void setReport(CoverageReportSummary report) { this.report = report; }
        public String getClassName() { return className; }
        public void setClassName(String className) { this.className = className; }
        public List<MethodCoverageSummary> getMethods() { return methods; }
        public void setMethods(List<MethodCoverageSummary> methods) { this.methods = methods; }
        public SourceCoveragePayload getSourceCoverage() { return sourceCoverage; }
        public void setSourceCoverage(SourceCoveragePayload sourceCoverage) { this.sourceCoverage = sourceCoverage; }
        public String getColoredSourceHtml() { return coloredSourceHtml; }
        public void setColoredSourceHtml(String coloredSourceHtml) { this.coloredSourceHtml = coloredSourceHtml; }
        public String getCurrentUserRole() { return currentUserRole; }
        public void setCurrentUserRole(String currentUserRole) { this.currentUserRole = currentUserRole; }
    }

    public static class CoverageComparisonSummary {
        private int addedCount;
        private int stableCount;
        private int decreasedCount;
        private List<MethodDiffSummary> addedMethods;
        private List<MethodDiffSummary> stableMethods;
        private List<MethodDiffSummary> decreasedMethods;

        public int getAddedCount() { return addedCount; }
        public void setAddedCount(int addedCount) { this.addedCount = addedCount; }
        public int getStableCount() { return stableCount; }
        public void setStableCount(int stableCount) { this.stableCount = stableCount; }
        public int getDecreasedCount() { return decreasedCount; }
        public void setDecreasedCount(int decreasedCount) { this.decreasedCount = decreasedCount; }
        public List<MethodDiffSummary> getAddedMethods() { return addedMethods; }
        public void setAddedMethods(List<MethodDiffSummary> addedMethods) { this.addedMethods = addedMethods; }
        public List<MethodDiffSummary> getStableMethods() { return stableMethods; }
        public void setStableMethods(List<MethodDiffSummary> stableMethods) { this.stableMethods = stableMethods; }
        public List<MethodDiffSummary> getDecreasedMethods() { return decreasedMethods; }
        public void setDecreasedMethods(List<MethodDiffSummary> decreasedMethods) { this.decreasedMethods = decreasedMethods; }
    }

    public static class MethodDiffSummary {
        private String className;
        private String methodName;
        private String methodDesc;

        public String getClassName() { return className; }
        public void setClassName(String className) { this.className = className; }
        public String getMethodName() { return methodName; }
        public void setMethodName(String methodName) { this.methodName = methodName; }
        public String getMethodDesc() { return methodDesc; }
        public void setMethodDesc(String methodDesc) { this.methodDesc = methodDesc; }
    }

    public static class PageSummary<T> {
        private List<T> content;
        private int page;
        private int size;
        private long totalElements;
        private int totalPages;

        public List<T> getContent() { return content; }
        public void setContent(List<T> content) { this.content = content; }
        public int getPage() { return page; }
        public void setPage(int page) { this.page = page; }
        public int getSize() { return size; }
        public void setSize(int size) { this.size = size; }
        public long getTotalElements() { return totalElements; }
        public void setTotalElements(long totalElements) { this.totalElements = totalElements; }
        public int getTotalPages() { return totalPages; }
        public void setTotalPages(int totalPages) { this.totalPages = totalPages; }
    }

    public static class ProjectSummary {
        private String id;
        private String name;
        private String describe;

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getDescribe() { return describe; }
        public void setDescribe(String describe) { this.describe = describe; }
    }

    public static class AppSummary {
        private String id;
        private String name;
        private String currentVersion;
        private String currentBranch;
        private String currentCommitId;
        private String sourceType;

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getCurrentVersion() { return currentVersion; }
        public void setCurrentVersion(String currentVersion) { this.currentVersion = currentVersion; }
        public String getCurrentBranch() { return currentBranch; }
        public void setCurrentBranch(String currentBranch) { this.currentBranch = currentBranch; }
        public String getCurrentCommitId() { return currentCommitId; }
        public void setCurrentCommitId(String currentCommitId) { this.currentCommitId = currentCommitId; }
        public String getSourceType() { return sourceType; }
        public void setSourceType(String sourceType) { this.sourceType = sourceType; }
    }

    public static class VersionSummary {
        private String id;
        private String versionNumber;
        private String describe;
        private String repoBranch;
        private String repoCommitId;
        private String programFile;
        private String programName;
        private String createTimeText;

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getVersionNumber() { return versionNumber; }
        public void setVersionNumber(String versionNumber) { this.versionNumber = versionNumber; }
        public String getDescribe() { return describe; }
        public void setDescribe(String describe) { this.describe = describe; }
        public String getRepoBranch() { return repoBranch; }
        public void setRepoBranch(String repoBranch) { this.repoBranch = repoBranch; }
        public String getRepoCommitId() { return repoCommitId; }
        public void setRepoCommitId(String repoCommitId) { this.repoCommitId = repoCommitId; }
        public String getProgramFile() { return programFile; }
        public void setProgramFile(String programFile) { this.programFile = programFile; }
        public String getProgramName() { return programName; }
        public void setProgramName(String programName) { this.programName = programName; }
        public String getCreateTimeText() { return createTimeText; }
        public void setCreateTimeText(String createTimeText) { this.createTimeText = createTimeText; }
    }

    public static class CoverageReportSummary {
        private String id;
        private String appId;
        private String versionNumber;
        private String repoBranch;
        private String repoCommitId;
        private String createTimeText;
        private String sourceType;
        private String lastProcessedTime;
        private long totalClasses;
        private long coveredClasses;
        private long totalMethods;
        private long coveredMethods;
        private long totalBranches;
        private long coveredBranches;
        private long totalBranchTargets;
        private long coveredBranchTargets;
        private long totalLines;
        private long coveredLines;
        private int totalComplexity;
        private Integer snapshotCount;
        private Integer reportType;
        private String baseVersionNumber;
        private String baseRepoCommitId;

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getAppId() { return appId; }
        public void setAppId(String appId) { this.appId = appId; }
        public String getVersionNumber() { return versionNumber; }
        public void setVersionNumber(String versionNumber) { this.versionNumber = versionNumber; }
        public String getRepoBranch() { return repoBranch; }
        public void setRepoBranch(String repoBranch) { this.repoBranch = repoBranch; }
        public String getRepoCommitId() { return repoCommitId; }
        public void setRepoCommitId(String repoCommitId) { this.repoCommitId = repoCommitId; }
        public String getCreateTimeText() { return createTimeText; }
        public void setCreateTimeText(String createTimeText) { this.createTimeText = createTimeText; }
        public String getSourceType() { return sourceType; }
        public void setSourceType(String sourceType) { this.sourceType = sourceType; }
        public String getLastProcessedTime() { return lastProcessedTime; }
        public void setLastProcessedTime(String lastProcessedTime) { this.lastProcessedTime = lastProcessedTime; }
        public long getTotalClasses() { return totalClasses; }
        public void setTotalClasses(long totalClasses) { this.totalClasses = totalClasses; }
        public long getCoveredClasses() { return coveredClasses; }
        public void setCoveredClasses(long coveredClasses) { this.coveredClasses = coveredClasses; }
        public long getTotalMethods() { return totalMethods; }
        public void setTotalMethods(long totalMethods) { this.totalMethods = totalMethods; }
        public long getCoveredMethods() { return coveredMethods; }
        public void setCoveredMethods(long coveredMethods) { this.coveredMethods = coveredMethods; }
        public long getTotalBranches() { return totalBranches; }
        public void setTotalBranches(long totalBranches) { this.totalBranches = totalBranches; }
        public long getCoveredBranches() { return coveredBranches; }
        public void setCoveredBranches(long coveredBranches) { this.coveredBranches = coveredBranches; }
        public long getTotalBranchTargets() { return totalBranchTargets; }
        public void setTotalBranchTargets(long totalBranchTargets) { this.totalBranchTargets = totalBranchTargets; }
        public long getCoveredBranchTargets() { return coveredBranchTargets; }
        public void setCoveredBranchTargets(long coveredBranchTargets) { this.coveredBranchTargets = coveredBranchTargets; }
        public long getTotalLines() { return totalLines; }
        public void setTotalLines(long totalLines) { this.totalLines = totalLines; }
        public long getCoveredLines() { return coveredLines; }
        public void setCoveredLines(long coveredLines) { this.coveredLines = coveredLines; }
        public int getTotalComplexity() { return totalComplexity; }
        public void setTotalComplexity(int totalComplexity) { this.totalComplexity = totalComplexity; }
        public Integer getSnapshotCount() { return snapshotCount; }
        public void setSnapshotCount(Integer snapshotCount) { this.snapshotCount = snapshotCount; }
        public Integer getReportType() { return reportType; }
        public void setReportType(Integer reportType) { this.reportType = reportType; }
        public String getBaseVersionNumber() { return baseVersionNumber; }
        public void setBaseVersionNumber(String baseVersionNumber) { this.baseVersionNumber = baseVersionNumber; }
        public String getBaseRepoCommitId() { return baseRepoCommitId; }
        public void setBaseRepoCommitId(String baseRepoCommitId) { this.baseRepoCommitId = baseRepoCommitId; }
    }

    public static class ClassCoverageSummary {
        private String className;
        private int totalMethods;
        private int coveredMethods;
        private int totalBranches;
        private int coveredBranches;
        private int totalBranchTargets;
        private int coveredBranchTargets;
        private int totalLines;
        private int coveredLines;
        private int totalComplexity;
        private Double lineRate;
        private Double branchRate;
        private Double methodRate;
        private boolean hasCodeChanges;

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
        public boolean isHasCodeChanges() { return hasCodeChanges; }
        public void setHasCodeChanges(boolean hasCodeChanges) { this.hasCodeChanges = hasCodeChanges; }
    }

    public static class MethodCoverageSummary {
        private String methodName;
        private String methodDesc;
        private int totalLines;
        private int coveredLines;
        private int totalBranches;
        private int coveredBranches;
        private int complexity;
        private boolean covered;
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
        public boolean isCovered() { return covered; }
        public void setCovered(boolean covered) { this.covered = covered; }
        public int getTotalBranchTargets() { return totalBranchTargets; }
        public void setTotalBranchTargets(int totalBranchTargets) { this.totalBranchTargets = totalBranchTargets; }
        public int getCoveredBranchTargets() { return coveredBranchTargets; }
        public void setCoveredBranchTargets(int coveredBranchTargets) { this.coveredBranchTargets = coveredBranchTargets; }
        public Double getBranchRate() { return branchRate; }
        public void setBranchRate(Double branchRate) { this.branchRate = branchRate; }
        public boolean isHasCodeChanges() { return hasCodeChanges; }
        public void setHasCodeChanges(boolean hasCodeChanges) { this.hasCodeChanges = hasCodeChanges; }
    }
}
