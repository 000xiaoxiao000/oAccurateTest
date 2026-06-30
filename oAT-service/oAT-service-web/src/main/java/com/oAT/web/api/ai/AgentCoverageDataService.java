package com.oAT.web.api.ai;

import com.oAT.web.common.Job;
import com.oAT.web.coveragecore.model.CoverageBranch;
import com.oAT.web.coveragecore.model.CoverageFunction;
import com.oAT.web.coveragecore.model.CoverageLine;
import com.oAT.web.coveragecore.model.CoverageUnit;
import com.oAT.web.coveragecore.query.CoverageCoreQueryService;
import com.oAT.web.coveragecore.query.CoverageUnitPage;
import com.oAT.web.coveragecore.query.CoverageUnitQuery;
import com.oAT.web.coveragecore.report.CoverageReportCommandService;
import com.oAT.web.esDao.entity.CoverageReportIndex;
import com.oAT.web.service.AppService;
import com.oAT.web.service.entity.AppVo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class AgentCoverageDataService {
    private static final Logger logger = LoggerFactory.getLogger(AgentCoverageDataService.class);

    @Autowired
    private CoverageCoreQueryService coverageCoreQueryService;

    @Autowired
    private CoverageReportCommandService coverageReportCommandService;

    @Autowired
    private AppService appService;

    public List<Map<String, Object>> getCoverageReports(String appId) {
        List<Map<String, Object>> result = new ArrayList<>();
        try {
            List<CoverageReportIndex> reports = coverageReportCommandService.getReportsByAppId(appId);
            if (reports != null) {
                for (CoverageReportIndex report : reports) {
                    Map<String, Object> reportMap = new HashMap<>();
                    reportMap.put("id", report.getId());
                    reportMap.put("createTime", report.getCreateTime());
                    reportMap.put("lineRate", calculateRate(report.getCoveredLines(), report.getTotalLines()));
                    reportMap.put("branchRate", calculateRate(report.getCoveredBranchTargets(), report.getTotalBranchTargets()));
                    reportMap.put("methodRate", calculateRate(report.getCoveredMethods(), report.getTotalMethods()));
                    reportMap.put("reportType", report.getReportType());
                    result.add(reportMap);
                }
                result.sort((a, b) -> {
                    Object timeA = a.get("createTime");
                    Object timeB = b.get("createTime");
                    if (timeA == null && timeB == null) return 0;
                    if (timeA == null) return 1;
                    if (timeB == null) return -1;
                    return timeB.toString().compareTo(timeA.toString());
                });
            }
        } catch (Exception e) {
            logger.error("Get coverage reports failed: {}", appId, e);
        }
        return result;
    }

    public Map<String, Object> getCoverageReportDetail(String reportId) {
        Map<String, Object> result = new HashMap<>();
        try {
            CoverageReportIndex report = coverageReportCommandService.getReport(reportId);
            if (report != null) {
                result.put("id", report.getId());
                result.put("appId", report.getAppId());
                result.put("appName", getAppName(report.getAppId()));
                result.put("createTime", report.getCreateTime());
                result.put("lineRate", calculateRate(report.getCoveredLines(), report.getTotalLines()));
                result.put("branchRate", calculateRate(report.getCoveredBranchTargets(), report.getTotalBranchTargets()));
                result.put("methodRate", calculateRate(report.getCoveredMethods(), report.getTotalMethods()));
                result.put("reportType", report.getReportType());
                result.put("classCount", report.getTotalClasses());
                result.put("methodCount", report.getTotalMethods());
                result.put("branch", report.getRepoBranch());
                result.put("commitId", report.getRepoCommitId());
                result.put("versionNumber", report.getVersionNumber());
            }
        } catch (Exception e) {
            logger.error("Get coverage report detail failed: {}", reportId, e);
        }
        return result;
    }

    public List<Map<String, Object>> getCoverageTrend(String appId, int limit) {
        List<Map<String, Object>> result = new ArrayList<>();
        try {
            List<Map<String, Object>> trendData = coverageReportCommandService.getTrendData(appId, null);
            if (trendData != null) {
                int count = 0;
                for (Map<String, Object> data : trendData) {
                    if (count >= limit) break;
                    Map<String, Object> trendMap = new HashMap<>();
                    trendMap.put("createTime", data.get("createTime"));
                    trendMap.put("lineRate", data.get("lineRate"));
                    trendMap.put("branchRate", data.get("branchRate"));
                    trendMap.put("methodRate", data.get("methodRate"));
                    result.add(trendMap);
                    count++;
                }
            }
        } catch (Exception e) {
            logger.error("Get coverage trend failed: {}", appId, e);
        }
        return result;
    }

    public List<Map<String, Object>> getClassCoverageList(String reportId, Double minRate, Double maxRate) {
        List<Map<String, Object>> result = new ArrayList<>();
        try {
            CoverageUnitQuery query = new CoverageUnitQuery();
            query.setMinRate(minRate);
            query.setMaxRate(maxRate);
            query.setPage(0);
            query.setSize(50);
            CoverageUnitPage page = coverageCoreQueryService.listUnits(reportId, query);
            if (page != null && page.getContent() != null && !page.getContent().isEmpty()) {
                for (CoverageUnit unit : page.getContent()) {
                    UnitMetrics metrics = UnitMetrics.from(unit);
                    Map<String, Object> classMap = new HashMap<>();
                    classMap.put("className", firstText(unit.getUnitKey(), unit.getDisplayName(), unit.getSourcePath()));
                    classMap.put("lineRate", metrics.lineRate());
                    classMap.put("branchRate", metrics.branchRate());
                    classMap.put("methodRate", metrics.methodRate());
                    classMap.put("complexity", metrics.complexity());
                    result.add(classMap);
                }
            }
        } catch (Exception e) {
            logger.error("Get class coverage list failed: {}", reportId, e);
        }
        return result;
    }

    public String startCoverageGenerationJob(String appId, String versionNumber, String branch, String commitId) {
        try {
            String jobId = coverageReportCommandService.startVersionFullReport(appId, versionNumber, branch, commitId);
            logger.info("Started coverage generation job: appId={}, version={}, branch={}, commit={}, jobId={}",
                    appId, versionNumber, branch, commitId, jobId);
            return jobId;
        } catch (Exception e) {
            logger.error("Failed to start coverage generation job: appId={}, version={}, branch={}, commit={}",
                    appId, versionNumber, branch, commitId, e);
            return null;
        }
    }

    public Map<String, Object> getJobStatus(String jobId) {
        Map<String, Object> result = new HashMap<>();
        try {
            Job<String> job = coverageReportCommandService.getJob(jobId);
            if (job == null) {
                return result;
            }
            result.put("jobId", jobId);
            result.put("status", mapJobState(job.getState()));
            result.put("progress", job.getProgress() != null ? job.getProgress().getPercent() : 0);
            result.put("message", job.getProgress() != null ? job.getProgress().getName() : "");
            result.put("reportId", job.getData());
            result.put("startTime", job.getBegin());
            result.put("log", job.getLog());
        } catch (Exception e) {
            logger.error("Failed to get job status: jobId={}", jobId, e);
        }
        return result;
    }

    public String generateReportDownloadUrl(String reportId) {
        return "/api/coverage/reports/" + reportId + "/export";
    }

    private String mapJobState(Job.JobState state) {
        if (state == null) {
            return "UNKNOWN";
        }
        switch (state) {
            case finish:
                return "COMPLETED";
            case active:
                return "RUNNING";
            case error:
                return "FAILED";
            case wait:
                return "PENDING";
            case terminate:
                return "TERMINATED";
            default:
                return state.name().toUpperCase();
        }
    }

    private String getAppName(String appId) {
        if (appId == null) return "";
        try {
            AppVo app = appService.getApp(appId);
            return app != null ? app.getName() : appId;
        } catch (Exception e) {
            return appId;
        }
    }

    private double calculateRate(long covered, long total) {
        if (total <= 0) return 0.0;
        return (double) covered / total;
    }

    private String firstText(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return null;
    }

    private record UnitMetrics(int totalLines,
                               int coveredLines,
                               int totalBranches,
                               int coveredBranches,
                               int totalFunctions,
                               int coveredFunctions,
                               int complexity) {
        static UnitMetrics from(CoverageUnit unit) {
            List<CoverageLine> lines = !unit.getLines().isEmpty()
                    ? unit.getLines()
                    : unit.getFunctions().stream().flatMap(function -> function.getLines().stream()).toList();
            List<CoverageBranch> branches = !unit.getBranches().isEmpty()
                    ? unit.getBranches()
                    : unit.getFunctions().stream().flatMap(function -> function.getBranches().stream()).toList();
            int totalFunctions = unit.getFunctions().size();
            int coveredFunctions = (int) unit.getFunctions().stream().filter(UnitMetrics::isFunctionCovered).count();
            int complexity = unit.getFunctions().stream().mapToInt(CoverageFunction::getComplexity).sum();
            return new UnitMetrics(
                    lines.size(),
                    (int) lines.stream().filter(line -> line.getHits() > 0).count(),
                    branches.size(),
                    (int) branches.stream().filter(branch -> branch.getHits() > 0).count(),
                    totalFunctions,
                    coveredFunctions,
                    complexity);
        }

        private static boolean isFunctionCovered(CoverageFunction function) {
            return function.getLines().stream().anyMatch(line -> line.getHits() > 0)
                    || function.getBranches().stream().anyMatch(branch -> branch.getHits() > 0);
        }

        double lineRate() {
            return totalLines > 0 ? (double) coveredLines / totalLines * 100 : 0;
        }

        double branchRate() {
            return totalBranches > 0 ? (double) coveredBranches / totalBranches * 100 : 0;
        }

        double methodRate() {
            return totalFunctions > 0 ? (double) coveredFunctions / totalFunctions * 100 : 0;
        }
    }
}
