package com.oAT.web.coverage;

import com.fasterxml.jackson.databind.JsonNode;
import com.oAT.web.common.UtilJson;
import com.oAT.web.coverage.FrontendCoverageReportRepository.FrontendCoverageReport;
import com.oAT.web.esDao.ClassCoverageRepository;
import com.oAT.web.esDao.CoverageReportRepository;
import com.oAT.web.esDao.entity.ClassCoverageIndex;
import com.oAT.web.esDao.entity.CoverageReportIndex;
import com.oAT.web.service.AppService;
import com.oAT.web.service.entity.AppVo;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class FrontendCoverageService {
    public static final String SOURCE_TYPE_FRONTEND = "FRONTEND";

    private final FrontendCoverageReportRepository frontendCoverageReportRepository;
    private final CoverageReportRepository coverageReportRepository;
    private final ClassCoverageRepository classCoverageRepository;
    private final AppService appService;

    public FrontendCoverageService(FrontendCoverageReportRepository frontendCoverageReportRepository,
                                   CoverageReportRepository coverageReportRepository,
                                   ClassCoverageRepository classCoverageRepository,
                                   AppService appService) {
        this.frontendCoverageReportRepository = frontendCoverageReportRepository;
        this.coverageReportRepository = coverageReportRepository;
        this.classCoverageRepository = classCoverageRepository;
        this.appService = appService;
    }

    public String saveReport(String projectId, String appId, FrontendCoverageReportRequest request) {
        Assert.hasText(projectId, "projectId不能为空");
        Assert.hasText(appId, "appId不能为空");
        Assert.notNull(request, "请求体不能为空");
        Assert.notNull(request.getCoverage(), "coverage不能为空");

        AppVo app = appService.getApp(appId);
        Assert.notNull(app, "应用不存在");

        FrontendCoverageReport report = new FrontendCoverageReport();
        report.projectId = projectId;
        report.appId = appId;
        report.commitId = firstText(request.getCommitId(), app.getCurrentCommitId());
        report.versionNumber = firstText(request.getVersionNumber(), app.getCurrentVersion(), report.commitId);
        report.branch = firstText(request.getBranch(), app.getCurrentBranch());
        report.caseName = request.getCaseName();
        report.timestamp = request.getTimestamp();
        report.coverageJson = UtilJson.writeValueAsString(request.getCoverage());
        return frontendCoverageReportRepository.save(report);
    }

    @Transactional
    public CoverageReportIndex generateReport(String projectId, String appId, FrontendCoverageGenerateRequest request) {
        Assert.hasText(projectId, "projectId不能为空");
        Assert.hasText(appId, "appId不能为空");
        AppVo app = appService.getApp(appId);
        Assert.notNull(app, "应用不存在");

        String versionNumber = firstText(request == null ? null : request.getVersionNumber(), app.getCurrentVersion());
        String branch = firstText(request == null ? null : request.getBranch(), app.getCurrentBranch());
        String commitId = firstText(request == null ? null : request.getCommitId(), app.getCurrentCommitId());
        Assert.hasText(versionNumber, "versionNumber不能为空");

        List<FrontendCoverageReport> rawReports = frontendCoverageReportRepository.findByAppAndVersion(appId, versionNumber, commitId);
        Assert.isTrue(!rawReports.isEmpty(), "没有可生成的前端覆盖率上报数据");

        Map<String, ClassCoverageIndex> coverageMap = new LinkedHashMap<>();
        long lastTimestamp = 0L;
        for (FrontendCoverageReport rawReport : rawReports) {
            lastTimestamp = Math.max(lastTimestamp, rawReport.timestamp == null ? 0L : rawReport.timestamp);
            mergeIstanbulCoverage(rawReport.coverageJson, coverageMap, appId);
        }

        CoverageReportIndex report = new CoverageReportIndex();
        report.setId(UUID.randomUUID().toString());
        report.setAppId(appId);
        report.setVersionNumber(versionNumber);
        report.setRepoBranch(branch);
        report.setRepoCommitId(commitId);
        report.setCreateTime(new Date());
        report.setSourceType(SOURCE_TYPE_FRONTEND);
        report.setReportType(0);
        report.setLastProcessedTime(lastTimestamp > 0 ? String.valueOf(lastTimestamp) : String.valueOf(System.currentTimeMillis()));

        saveSummary(report, coverageMap);
        return report;
    }

    private void mergeIstanbulCoverage(String coverageJson, Map<String, ClassCoverageIndex> coverageMap, String appId) {
        try {
            JsonNode root = UtilJson.getObjectMapper().readTree(coverageJson);
            Iterator<Map.Entry<String, JsonNode>> fields = root.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> entry = fields.next();
                JsonNode fileNode = entry.getValue();
                String filePath = firstText(text(fileNode.get("path")), entry.getKey());
                if (!StringUtils.hasText(filePath)) {
                    continue;
                }
                ClassCoverageIndex next = parseFileCoverage(filePath, fileNode, appId);
                ClassCoverageIndex existing = coverageMap.get(filePath);
                coverageMap.put(filePath, existing == null ? next : mergeFileCoverage(existing, next));
            }
        } catch (Exception e) {
            throw new IllegalArgumentException("解析 Istanbul 覆盖率失败", e);
        }
    }

    private ClassCoverageIndex parseFileCoverage(String filePath, JsonNode fileNode, String appId) {
        Map<Integer, Integer> lineCounts = new LinkedHashMap<>();
        JsonNode statementMap = fileNode.get("statementMap");
        JsonNode statementCounts = fileNode.get("s");
        if (statementMap != null && statementCounts != null) {
            Iterator<Map.Entry<String, JsonNode>> statements = statementMap.fields();
            while (statements.hasNext()) {
                Map.Entry<String, JsonNode> statement = statements.next();
                int line = statement.getValue().path("start").path("line").asInt(0);
                if (line <= 0) {
                    line = statement.getValue().path("loc").path("start").path("line").asInt(0);
                }
                if (line <= 0) {
                    continue;
                }
                int count = statementCounts.path(statement.getKey()).asInt(0);
                lineCounts.merge(line, count, Math::max);
            }
        }

        List<ClassCoverageIndex.MethodCoverageDetail> methods = parseFunctions(fileNode);
        BranchStats branchStats = parseBranches(fileNode);

        ClassCoverageIndex classCoverage = new ClassCoverageIndex();
        classCoverage.setAppId(appId);
        classCoverage.setClassName(filePath);
        classCoverage.setSourceType(SOURCE_TYPE_FRONTEND);
        classCoverage.setTotalLines(lineCounts.size());
        classCoverage.setCoveredLines((int) lineCounts.values().stream().filter(count -> count > 0).count());
        classCoverage.setTotalMethods(methods.size());
        classCoverage.setCoveredMethods((int) methods.stream().filter(ClassCoverageIndex.MethodCoverageDetail::isCovered).count());
        classCoverage.setTotalBranches(branchStats.totalBranches);
        classCoverage.setCoveredBranches(branchStats.coveredBranches);
        classCoverage.setTotalBranchTargets(branchStats.totalTargets);
        classCoverage.setCoveredBranchTargets(branchStats.coveredTargets);
        classCoverage.setTotalComplexity(methods.size() + branchStats.totalBranches);
        classCoverage.setMethods(methods);
        classCoverage.setLineRate(rate(classCoverage.getCoveredLines(), classCoverage.getTotalLines()));
        classCoverage.setMethodRate(rate(classCoverage.getCoveredMethods(), classCoverage.getTotalMethods()));
        classCoverage.setBranchRate(rate(classCoverage.getCoveredBranchTargets(), classCoverage.getTotalBranchTargets()));
        return classCoverage;
    }

    private List<ClassCoverageIndex.MethodCoverageDetail> parseFunctions(JsonNode fileNode) {
        List<ClassCoverageIndex.MethodCoverageDetail> methods = new ArrayList<>();
        JsonNode functionMap = fileNode.get("fnMap");
        JsonNode functionCounts = fileNode.get("f");
        if (functionMap == null || functionCounts == null) {
            return methods;
        }
        Iterator<Map.Entry<String, JsonNode>> functions = functionMap.fields();
        while (functions.hasNext()) {
            Map.Entry<String, JsonNode> function = functions.next();
            JsonNode fn = function.getValue();
            int startLine = fn.path("loc").path("start").path("line").asInt(0);
            int endLine = fn.path("loc").path("end").path("line").asInt(startLine);
            int count = functionCounts.path(function.getKey()).asInt(0);
            Set<Integer> totalLines = new LinkedHashSet<>();
            for (int line = Math.max(1, startLine); line <= Math.max(startLine, endLine); line++) {
                totalLines.add(line);
            }
            ClassCoverageIndex.MethodCoverageDetail method = new ClassCoverageIndex.MethodCoverageDetail();
            method.setMethodName(firstText(text(fn.get("name")), "(anonymous)"));
            method.setMethodDesc(startLine > 0 ? "lines " + startLine + "-" + endLine : "");
            method.setTotalLineNumbers(new ArrayList<>(totalLines));
            method.setCoveredLineNumbers(count > 0 ? new ArrayList<>(totalLines) : new ArrayList<>());
            method.setTotalLines(totalLines.size());
            method.setCoveredLines(count > 0 ? totalLines.size() : 0);
            method.setCovered(count > 0);
            method.setComplexity(1);
            method.setTotalBranches(0);
            method.setCoveredBranches(0);
            method.setTotalBranchTargets(0);
            method.setCoveredBranchTargets(0);
            method.setBranchRate(0D);
            methods.add(method);
        }
        return methods;
    }

    private BranchStats parseBranches(JsonNode fileNode) {
        BranchStats stats = new BranchStats();
        JsonNode branchCounts = fileNode.get("b");
        if (branchCounts == null) {
            return stats;
        }
        Iterator<Map.Entry<String, JsonNode>> branches = branchCounts.fields();
        while (branches.hasNext()) {
            JsonNode counts = branches.next().getValue();
            if (!counts.isArray()) {
                continue;
            }
            stats.totalBranches++;
            int coveredTargetsForBranch = 0;
            for (JsonNode countNode : counts) {
                stats.totalTargets++;
                if (countNode.asInt(0) > 0) {
                    stats.coveredTargets++;
                    coveredTargetsForBranch++;
                }
            }
            if (coveredTargetsForBranch > 0) {
                stats.coveredBranches++;
            }
        }
        return stats;
    }

    private ClassCoverageIndex mergeFileCoverage(ClassCoverageIndex left, ClassCoverageIndex right) {
        left.setCoveredLines(Math.min(left.getTotalLines(), left.getCoveredLines() + right.getCoveredLines()));
        left.setCoveredMethods(Math.min(left.getTotalMethods(), left.getCoveredMethods() + right.getCoveredMethods()));
        left.setCoveredBranches(Math.min(left.getTotalBranches(), left.getCoveredBranches() + right.getCoveredBranches()));
        left.setCoveredBranchTargets(Math.min(left.getTotalBranchTargets(), left.getCoveredBranchTargets() + right.getCoveredBranchTargets()));
        left.setLineRate(rate(left.getCoveredLines(), left.getTotalLines()));
        left.setMethodRate(rate(left.getCoveredMethods(), left.getTotalMethods()));
        left.setBranchRate(rate(left.getCoveredBranchTargets(), left.getTotalBranchTargets()));

        Map<String, ClassCoverageIndex.MethodCoverageDetail> rightMethods = new LinkedHashMap<>();
        for (ClassCoverageIndex.MethodCoverageDetail method : right.getMethods()) {
            rightMethods.put(method.getMethodName() + "\n" + method.getMethodDesc(), method);
        }
        for (ClassCoverageIndex.MethodCoverageDetail method : left.getMethods()) {
            ClassCoverageIndex.MethodCoverageDetail next = rightMethods.get(method.getMethodName() + "\n" + method.getMethodDesc());
            if (next != null && next.isCovered()) {
                method.setCovered(true);
                method.setCoveredLines(method.getTotalLines());
                method.setCoveredLineNumbers(method.getTotalLineNumbers());
            }
        }
        return left;
    }

    private void saveSummary(CoverageReportIndex report, Map<String, ClassCoverageIndex> coverageMap) {
        long coveredClasses = 0;
        long totalMethods = 0;
        long coveredMethods = 0;
        long totalBranches = 0;
        long coveredBranches = 0;
        long totalBranchTargets = 0;
        long coveredBranchTargets = 0;
        long totalLines = 0;
        long coveredLines = 0;
        int totalComplexity = 0;

        List<ClassCoverageIndex> batch = new ArrayList<>();
        for (ClassCoverageIndex item : coverageMap.values()) {
            item.setReportId(report.getId());
            item.setId(report.getId() + "_" + item.getClassName().hashCode());
            if (item.getCoveredLines() > 0) {
                coveredClasses++;
            }
            totalMethods += item.getTotalMethods();
            coveredMethods += item.getCoveredMethods();
            totalBranches += item.getTotalBranches();
            coveredBranches += item.getCoveredBranches();
            totalBranchTargets += item.getTotalBranchTargets();
            coveredBranchTargets += item.getCoveredBranchTargets();
            totalLines += item.getTotalLines();
            coveredLines += item.getCoveredLines();
            totalComplexity += item.getTotalComplexity();
            batch.add(item);
        }

        report.setTotalClasses(coverageMap.size());
        report.setCoveredClasses(coveredClasses);
        report.setTotalMethods(totalMethods);
        report.setCoveredMethods(coveredMethods);
        report.setTotalBranches(totalBranches);
        report.setCoveredBranches(coveredBranches);
        report.setTotalBranchTargets(totalBranchTargets);
        report.setCoveredBranchTargets(coveredBranchTargets);
        report.setTotalLines(totalLines);
        report.setCoveredLines(coveredLines);
        report.setTotalComplexity(totalComplexity);

        coverageReportRepository.save(report);
        classCoverageRepository.saveAll(batch);
    }

    private Double rate(int covered, int total) {
        return total > 0 ? (double) covered / total * 100D : 0D;
    }

    private String text(JsonNode node) {
        return node == null || node.isNull() ? null : node.asText();
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

    private static class BranchStats {
        int totalBranches;
        int coveredBranches;
        int totalTargets;
        int coveredTargets;
    }

    public static class FrontendCoverageReportRequest {
        private String commitId;
        private String versionNumber;
        private String branch;
        private String caseName;
        private Long timestamp;
        private JsonNode coverage;

        public String getCommitId() { return commitId; }
        public void setCommitId(String commitId) { this.commitId = commitId; }
        public String getVersionNumber() { return versionNumber; }
        public void setVersionNumber(String versionNumber) { this.versionNumber = versionNumber; }
        public String getBranch() { return branch; }
        public void setBranch(String branch) { this.branch = branch; }
        public String getCaseName() { return caseName; }
        public void setCaseName(String caseName) { this.caseName = caseName; }
        public Long getTimestamp() { return timestamp; }
        public void setTimestamp(Long timestamp) { this.timestamp = timestamp; }
        public JsonNode getCoverage() { return coverage; }
        public void setCoverage(JsonNode coverage) { this.coverage = coverage; }
    }

    public static class FrontendCoverageGenerateRequest {
        private String versionNumber;
        private String branch;
        private String commitId;

        public String getVersionNumber() { return versionNumber; }
        public void setVersionNumber(String versionNumber) { this.versionNumber = versionNumber; }
        public String getBranch() { return branch; }
        public void setBranch(String branch) { this.branch = branch; }
        public String getCommitId() { return commitId; }
        public void setCommitId(String commitId) { this.commitId = commitId; }
    }
}
