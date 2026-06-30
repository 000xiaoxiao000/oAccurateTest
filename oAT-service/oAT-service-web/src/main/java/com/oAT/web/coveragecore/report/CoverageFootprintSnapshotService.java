package com.oAT.web.coveragecore.report;

import com.oAT.web.coverage.FrontendCoverageReportRepository;
import com.oAT.web.coverage.UniversalCoverageRawRepository;
import com.oAT.web.coveragecore.model.CoverageLanguage;
import com.oAT.web.service.AppService;
import com.oAT.web.service.entity.AppVo;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class CoverageFootprintSnapshotService {

    private final FrontendCoverageReportRepository frontendCoverageReportRepository;
    private final UniversalCoverageRawRepository universalCoverageRawRepository;
    private final AppService appService;

    public CoverageFootprintSnapshotService(FrontendCoverageReportRepository frontendCoverageReportRepository,
                                            UniversalCoverageRawRepository universalCoverageRawRepository,
                                            AppService appService) {
        this.frontendCoverageReportRepository = frontendCoverageReportRepository;
        this.universalCoverageRawRepository = universalCoverageRawRepository;
        this.appService = appService;
    }

    public List<CoverageFootprintSnapshot> listFootprints(String projectId,
                                                          String appId,
                                                          String language,
                                                          String versionNumber,
                                                          String commitId) {
        List<CoverageFootprintSnapshot> result = new ArrayList<>();
        CoverageLanguage selectedLanguage = StringUtils.hasText(language) ? CoverageLanguage.from(language) : null;
        if (selectedLanguage == null || selectedLanguage == CoverageLanguage.FRONTEND) {
            for (FrontendCoverageReportRepository.FrontendCoverageReport report :
                    frontendCoverageReportRepository.findFootprints(projectId, appId, versionNumber, commitId)) {
                result.add(fromFrontend(report));
            }
        }
        if (selectedLanguage == null) {
            addUniversal(result, projectId, appId, null, versionNumber, commitId);
        } else if (selectedLanguage != CoverageLanguage.JAVA && selectedLanguage != CoverageLanguage.FRONTEND) {
            addUniversal(result, projectId, appId, selectedLanguage.name(), versionNumber, commitId);
        }
        result.sort((left, right) -> Long.compare(
                right.getTimestamp() == null ? 0L : right.getTimestamp(),
                left.getTimestamp() == null ? 0L : left.getTimestamp()));
        enrichAppNames(result);
        return result;
    }

    public List<CoverageFootprintSnapshot> findFootprintsByKeys(String projectId, String[] footprintKeys) {
        if (!StringUtils.hasText(projectId) || footprintKeys == null || footprintKeys.length == 0) {
            return new ArrayList<>();
        }
        Set<String> requestedKeys = Arrays.stream(footprintKeys)
                .filter(StringUtils::hasText)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (requestedKeys.isEmpty()) {
            return new ArrayList<>();
        }
        Map<String, CoverageFootprintSnapshot> allByKey = listFootprints(projectId, null, null, null, null).stream()
                .filter(snapshot -> StringUtils.hasText(snapshot.getSnapshotKey()))
                .collect(Collectors.toMap(CoverageFootprintSnapshot::getSnapshotKey, Function.identity(), (left, right) -> left));
        List<CoverageFootprintSnapshot> result = new ArrayList<>();
        for (String key : requestedKeys) {
            CoverageFootprintSnapshot snapshot = allByKey.get(key);
            if (snapshot != null) {
                result.add(snapshot);
            }
        }
        return result;
    }

    public CoverageFootprintSnapshot getFootprint(String projectId, String footprintKey) {
        CoverageFootprintSnapshot snapshot = findByKey(projectId, footprintKey);
        if (snapshot == null) {
            throw new IllegalArgumentException("覆盖率足迹不存在: " + footprintKey);
        }
        return snapshot;
    }

    public CoverageFootprintSnapshot deleteFootprint(String projectId, String footprintKey) {
        CoverageFootprintSnapshot snapshot = findByKey(projectId, footprintKey);
        if (snapshot == null) {
            throw new IllegalArgumentException("覆盖率足迹不存在: " + footprintKey);
        }
        int deleted;
        if (CoverageLanguage.FRONTEND.name().equalsIgnoreCase(snapshot.getLanguage())) {
            deleted = frontendCoverageReportRepository.deleteById(snapshot.getRawReportId());
        } else {
            deleted = universalCoverageRawRepository.deleteById(snapshot.getRawReportId());
        }
        if (deleted <= 0) {
            throw new IllegalArgumentException("覆盖率足迹删除失败: " + footprintKey);
        }
        return snapshot;
    }

    private CoverageFootprintSnapshot findByKey(String projectId, String footprintKey) {
        if (!StringUtils.hasText(projectId) || !StringUtils.hasText(footprintKey)) {
            return null;
        }
        CoverageFootprintSnapshot byRawReportId = fromRawReportId(projectId, footprintKey);
        if (byRawReportId != null) {
            enrichAppNames(List.of(byRawReportId));
            return byRawReportId;
        }
        return listFootprints(projectId, null, null, null, null).stream()
                .filter(snapshot -> footprintKey.equals(snapshot.getSnapshotKey()))
                .findFirst()
                .orElse(null);
    }

    private CoverageFootprintSnapshot fromRawReportId(String projectId, String rawReportId) {
        FrontendCoverageReportRepository.FrontendCoverageReport frontend = frontendCoverageReportRepository.findById(rawReportId);
        if (frontend != null && projectId.equals(frontend.projectId)) {
            return fromFrontend(frontend);
        }
        UniversalCoverageRawRepository.UniversalCoverageRawReport universal = universalCoverageRawRepository.findById(rawReportId);
        if (universal != null && projectId.equals(universal.projectId)) {
            return fromUniversal(universal);
        }
        return null;
    }

    private void enrichAppNames(List<CoverageFootprintSnapshot> snapshots) {
        Map<String, String> appNameCache = new HashMap<>();
        for (CoverageFootprintSnapshot snapshot : snapshots) {
            if (!StringUtils.hasText(snapshot.getAppId())) {
                continue;
            }
            String appName = appNameCache.computeIfAbsent(snapshot.getAppId(), appId -> {
                try {
                    AppVo app = appService.getApp(appId);
                    return app == null ? appId : firstText(app.getName(), app.getSrcName(), appId);
                } catch (Exception ignored) {
                    return appId;
                }
            });
            snapshot.setAppName(appName);
        }
    }

    private void addUniversal(List<CoverageFootprintSnapshot> result,
                              String projectId,
                              String appId,
                              String sourceType,
                              String versionNumber,
                              String commitId) {
        for (UniversalCoverageRawRepository.UniversalCoverageRawReport report :
                universalCoverageRawRepository.findFootprints(projectId, appId, sourceType, versionNumber, commitId)) {
            result.add(fromUniversal(report));
        }
    }

    private CoverageFootprintSnapshot fromFrontend(FrontendCoverageReportRepository.FrontendCoverageReport report) {
        CoverageFootprintSnapshot snapshot = new CoverageFootprintSnapshot();
        snapshot.setRawReportId(report.id);
        snapshot.setProjectId(report.projectId);
        snapshot.setAppId(report.appId);
        snapshot.setLanguage(CoverageLanguage.FRONTEND.name());
        snapshot.setVersionNumber(report.versionNumber);
        snapshot.setBranch(report.branch);
        snapshot.setCommitId(report.commitId);
        snapshot.setBuildId(report.buildId);
        snapshot.setTestStage(report.testStage);
        snapshot.setCaseName(report.caseName);
        snapshot.setTimestamp(report.timestamp);
        snapshot.setSnapshotKey(snapshotKey(snapshot));
        return snapshot;
    }

    private CoverageFootprintSnapshot fromUniversal(UniversalCoverageRawRepository.UniversalCoverageRawReport report) {
        CoverageFootprintSnapshot snapshot = new CoverageFootprintSnapshot();
        snapshot.setRawReportId(report.id);
        snapshot.setProjectId(report.projectId);
        snapshot.setAppId(report.appId);
        snapshot.setLanguage(report.sourceType);
        snapshot.setVersionNumber(report.versionNumber);
        snapshot.setBranch(report.branch);
        snapshot.setCommitId(report.commitId);
        snapshot.setBuildId(report.buildId);
        snapshot.setTestStage(report.testStage);
        snapshot.setCaseName(report.caseName);
        snapshot.setTimestamp(report.timestamp);
        snapshot.setSnapshotKey(snapshotKey(snapshot));
        return snapshot;
    }

    private String snapshotKey(CoverageFootprintSnapshot snapshot) {
        return String.join("|",
                text(snapshot.getAppId()),
                text(snapshot.getLanguage()),
                text(snapshot.getBuildId()),
                text(snapshot.getTestStage()),
                text(snapshot.getCaseName()),
                text(snapshot.getCommitId()));
    }

    private String text(String value) {
        return StringUtils.hasText(value) ? value : "";
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

    public static class CoverageFootprintSnapshot {
        private String snapshotKey;
        private String rawReportId;
        private String projectId;
        private String appId;
        private String appName;
        private String language;
        private String versionNumber;
        private String branch;
        private String commitId;
        private String buildId;
        private String testStage;
        private String caseName;
        private Long timestamp;

        public String getSnapshotKey() { return snapshotKey; }
        public void setSnapshotKey(String snapshotKey) { this.snapshotKey = snapshotKey; }
        public String getRawReportId() { return rawReportId; }
        public void setRawReportId(String rawReportId) { this.rawReportId = rawReportId; }
        public String getProjectId() { return projectId; }
        public void setProjectId(String projectId) { this.projectId = projectId; }
        public String getAppId() { return appId; }
        public void setAppId(String appId) { this.appId = appId; }
        public String getAppName() { return appName; }
        public void setAppName(String appName) { this.appName = appName; }
        public String getLanguage() { return language; }
        public void setLanguage(String language) { this.language = language; }
        public String getVersionNumber() { return versionNumber; }
        public void setVersionNumber(String versionNumber) { this.versionNumber = versionNumber; }
        public String getBranch() { return branch; }
        public void setBranch(String branch) { this.branch = branch; }
        public String getCommitId() { return commitId; }
        public void setCommitId(String commitId) { this.commitId = commitId; }
        public String getBuildId() { return buildId; }
        public void setBuildId(String buildId) { this.buildId = buildId; }
        public String getTestStage() { return testStage; }
        public void setTestStage(String testStage) { this.testStage = testStage; }
        public String getCaseName() { return caseName; }
        public void setCaseName(String caseName) { this.caseName = caseName; }
        public Long getTimestamp() { return timestamp; }
        public void setTimestamp(Long timestamp) { this.timestamp = timestamp; }
    }
}
