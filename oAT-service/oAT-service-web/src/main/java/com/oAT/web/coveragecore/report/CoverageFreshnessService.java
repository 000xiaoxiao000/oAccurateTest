package com.oAT.web.coveragecore.report;

import com.oAT.web.esDao.CoverageReportRepository;
import com.oAT.web.esDao.SnapshotCommitMappingRepository;
import com.oAT.web.esDao.SystemSnapshotRepository;
import com.oAT.web.esDao.entity.CoverageReportIndex;
import com.oAT.web.esDao.entity.SnapshotCommitMapping;
import com.oAT.web.esDao.entity.SystemSnapshot;
import com.oAT.web.service.AppService;
import com.oAT.web.service.entity.AppVo;
import com.oAT.web.esDao.entity.StandardDate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.security.MessageDigest;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class CoverageFreshnessService {
    private static final Logger logger = LoggerFactory.getLogger(CoverageFreshnessService.class);
    private static final int REPORT_TYPE_VERSION_FULL = 0;
    private static final int REPORT_TYPE_CURRENT_COMMIT = 2;

    private final AppService appService;
    private final CoverageReportRepository coverageReportRepository;
    private final SystemSnapshotRepository systemSnapshotRepository;
    private final SnapshotCommitMappingRepository snapshotCommitMappingRepository;

    public CoverageFreshnessService(AppService appService,
                                    CoverageReportRepository coverageReportRepository,
                                    SystemSnapshotRepository systemSnapshotRepository,
                                    SnapshotCommitMappingRepository snapshotCommitMappingRepository) {
        this.appService = appService;
        this.coverageReportRepository = coverageReportRepository;
        this.systemSnapshotRepository = systemSnapshotRepository;
        this.snapshotCommitMappingRepository = snapshotCommitMappingRepository;
    }

    public boolean hasNewerData(String appId, String versionNumber, CoverageReportIndex report) {
        if (report == null) {
            AppVo app = appService.getApp(appId);
            SnapshotCoverageContext latestSnapshotContext = buildSnapshotCoverageContext(app, versionNumber, null, null);
            return !latestSnapshotContext.getSnapshotIds().isEmpty();
        }

        if (normalizeReportType(report.getReportType()) == REPORT_TYPE_VERSION_FULL) {
            List<CoverageReportIndex> commitReports = selectVersionCommitReports(appId, versionNumber);
            if (!commitReports.isEmpty()) {
                CoverageReportIndex latestCommitReport = selectLatestCodeReport(commitReports, report.getRepoBranch(), report.getRepoCommitId());
                String expectedFingerprint = buildCommitReportFingerprint(commitReports);
                boolean fingerprintChanged = !Objects.equals(report.getSnapshotFingerprint(), expectedFingerprint);
                boolean latestCommitChanged = latestCommitReport != null
                        && !Objects.equals(normalizeCommitKey(report.getRepoCommitId()), normalizeCommitKey(latestCommitReport.getRepoCommitId()));
                return fingerprintChanged || latestCommitChanged;
            }
        }

        AppVo app = appService.getApp(appId);
        SnapshotCoverageContext latestSnapshotContext = buildSnapshotCoverageContext(app, versionNumber, null, null);
        if (!StringUtils.hasText(report.getSnapshotFingerprint())) {
            return !latestSnapshotContext.getSnapshotIds().isEmpty();
        }
        if (!Objects.equals(report.getSnapshotFingerprint(), latestSnapshotContext.getSnapshotFingerprint())) {
            return true;
        }
        return !Objects.equals(report.getSnapshotLastUpdateTime(), latestSnapshotContext.getLastSnapshotTime());
    }

    private List<CoverageReportIndex> selectVersionCommitReports(String appId, String versionNumber) {
        List<CoverageReportIndex> reports = coverageReportRepository.findByAppIdAndVersionNumber(appId, versionNumber);
        if (reports == null || reports.isEmpty()) {
            return Collections.emptyList();
        }
        Map<String, CoverageReportIndex> latestByCommit = new LinkedHashMap<>();
        for (CoverageReportIndex report : reports) {
            if (report == null || !StringUtils.hasText(report.getId())) {
                continue;
            }
            if (normalizeReportType(report.getReportType()) != REPORT_TYPE_CURRENT_COMMIT) {
                continue;
            }
            String commitKey = normalizeCommitKey(report.getRepoCommitId());
            if (!StringUtils.hasText(commitKey)) {
                commitKey = report.getId();
            }
            CoverageReportIndex existing = latestByCommit.get(commitKey);
            if (existing == null || isReportAfter(report, existing)) {
                latestByCommit.put(commitKey, report);
            }
        }
        List<CoverageReportIndex> selected = new ArrayList<>(latestByCommit.values());
        selected.sort(Comparator.comparing(CoverageReportIndex::getCreateTime, Comparator.nullsLast(Comparator.naturalOrder())));
        return selected;
    }

    private CoverageReportIndex selectLatestCodeReport(List<CoverageReportIndex> commitReports, String branch, String latestCommitId) {
        if (commitReports == null || commitReports.isEmpty()) {
            return null;
        }
        if (StringUtils.hasText(latestCommitId)) {
            String expectedCommit = normalizeCommitKey(latestCommitId);
            for (CoverageReportIndex report : commitReports) {
                if (expectedCommit.equals(normalizeCommitKey(report.getRepoCommitId()))) {
                    return report;
                }
            }
        }
        List<CoverageReportIndex> candidates = new ArrayList<>(commitReports);
        if (StringUtils.hasText(branch)) {
            List<CoverageReportIndex> branchMatches = candidates.stream()
                    .filter(report -> branch.equals(report.getRepoBranch()))
                    .collect(Collectors.toList());
            if (!branchMatches.isEmpty()) {
                candidates = branchMatches;
            }
        }
        candidates.sort((a, b) -> {
            if (a.getCreateTime() == null) {
                return 1;
            }
            if (b.getCreateTime() == null) {
                return -1;
            }
            return b.getCreateTime().compareTo(a.getCreateTime());
        });
        return candidates.get(0);
    }

    private String buildCommitReportFingerprint(List<CoverageReportIndex> commitReports) {
        if (commitReports == null || commitReports.isEmpty()) {
            return "";
        }
        return sha256Hex(commitReports.stream()
                .map(report -> String.join("|",
                        report.getId() == null ? "" : report.getId(),
                        normalizeCommitKey(report.getRepoCommitId()) == null ? "" : normalizeCommitKey(report.getRepoCommitId()),
                        report.getCreateTime() == null ? "" : String.valueOf(report.getCreateTime().getTime()),
                        report.getSnapshotFingerprint() == null ? "" : report.getSnapshotFingerprint()))
                .collect(Collectors.joining(";")));
    }

    private SnapshotCoverageContext buildSnapshotCoverageContext(AppVo app,
                                                                 String versionNumber,
                                                                 String commitId,
                                                                 Integer reportType) {
        SnapshotCoverageContext context = new SnapshotCoverageContext();
        if (app == null || !StringUtils.hasText(app.getId()) || !StringUtils.hasText(app.getCreateProjectId())) {
            return context;
        }
        List<SystemSnapshot> allSnapshots = systemSnapshotRepository.findByProjectIdAndAppId(app.getCreateProjectId(), app.getId());
        if (allSnapshots == null || allSnapshots.isEmpty()) {
            return context;
        }
        List<SystemSnapshot> selectedSnapshots = new ArrayList<>(allSnapshots);
        if (StringUtils.hasText(versionNumber)) {
            List<SystemSnapshot> versionMatched = new ArrayList<>();
            for (SystemSnapshot snapshot : allSnapshots) {
                if (snapshot != null && versionNumber.equals(trim(snapshot.getVersion()))) {
                    versionMatched.add(snapshot);
                }
            }
            if (!versionMatched.isEmpty()) {
                selectedSnapshots = versionMatched;
            }
        }
        if (normalizeReportType(reportType) == REPORT_TYPE_CURRENT_COMMIT && StringUtils.hasText(commitId)) {
            List<SnapshotCommitMapping> mappings = snapshotCommitMappingRepository.findByAppIdAndRepoCommitId(app.getId(), commitId);
            if (mappings != null && !mappings.isEmpty()) {
                Set<String> commitSnapshotIds = mappings.stream()
                        .map(SnapshotCommitMapping::getSnapshotId)
                        .collect(Collectors.toSet());
                selectedSnapshots = selectedSnapshots.stream()
                        .filter(snapshot -> commitSnapshotIds.contains(snapshot.getId()))
                        .collect(Collectors.toList());
            }
        }
        selectedSnapshots.sort((a, b) -> {
            String timeA = getSnapshotEffectiveTime(a);
            String timeB = getSnapshotEffectiveTime(b);
            int timeCmp = timeA.compareTo(timeB);
            if (timeCmp != 0) {
                return timeCmp;
            }
            String idA = a.getId() == null ? "" : a.getId();
            String idB = b.getId() == null ? "" : b.getId();
            return idA.compareTo(idB);
        });

        List<String> snapshotIds = new ArrayList<>();
        List<String> traceIds = new ArrayList<>();
        List<String> fingerprints = new ArrayList<>();
        String lastSnapshotTime = null;
        for (SystemSnapshot snapshot : selectedSnapshots) {
            if (snapshot == null || !StringUtils.hasText(snapshot.getId()) || !StringUtils.hasText(snapshot.getTraceId())) {
                continue;
            }
            String effectiveTime = getSnapshotEffectiveTime(snapshot);
            snapshotIds.add(snapshot.getId());
            traceIds.add(snapshot.getTraceId());
            fingerprints.add(snapshot.getId() + "|" + effectiveTime + "|" + snapshot.getTraceId());
            if (lastSnapshotTime == null || effectiveTime.compareTo(lastSnapshotTime) > 0) {
                lastSnapshotTime = effectiveTime;
            }
        }
        context.setSnapshotIds(snapshotIds);
        context.setTraceIds(traceIds);
        context.setLastSnapshotTime(lastSnapshotTime);
        context.setSnapshotFingerprint(sha256Hex(String.join(";", fingerprints)));
        return context;
    }

    private String getSnapshotEffectiveTime(SystemSnapshot snapshot) {
        if (snapshot == null) {
            return "";
        }
        Date updateTime = snapshot.getUpdateTime();
        if (updateTime != null) {
            return new SimpleDateFormat(StandardDate.dateFormat).format(updateTime);
        }
        Date createTime = snapshot.getCreateTime();
        return createTime == null ? "" : new SimpleDateFormat(StandardDate.dateFormat).format(createTime);
    }

    private String sha256Hex(String text) {
        if (!StringUtils.hasText(text)) {
            return "";
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(text.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder();
            for (byte value : bytes) {
                builder.append(String.format("%02x", value));
            }
            return builder.toString();
        } catch (Exception e) {
            logger.warn("Compute snapshot fingerprint failed", e);
            return String.valueOf(text.hashCode());
        }
    }

    private String normalizeCommitKey(String commitId) {
        return StringUtils.hasText(commitId) ? commitId.trim().toLowerCase(Locale.ROOT) : null;
    }

    private boolean isReportAfter(CoverageReportIndex candidate, CoverageReportIndex current) {
        if (candidate == null) {
            return false;
        }
        if (current == null) {
            return true;
        }
        if (candidate.getCreateTime() == null) {
            return false;
        }
        if (current.getCreateTime() == null) {
            return true;
        }
        return candidate.getCreateTime().after(current.getCreateTime());
    }

    private int normalizeReportType(Integer reportType) {
        return reportType == null ? REPORT_TYPE_VERSION_FULL : reportType;
    }

    private String trim(String value) {
        return value == null ? null : value.trim();
    }

    private static class SnapshotCoverageContext {
        private List<String> snapshotIds = new ArrayList<>();
        private List<String> traceIds = new ArrayList<>();
        private String snapshotFingerprint;
        private String lastSnapshotTime;

        public List<String> getSnapshotIds() { return snapshotIds; }
        public void setSnapshotIds(List<String> snapshotIds) { this.snapshotIds = snapshotIds == null ? new ArrayList<>() : snapshotIds; }
        public List<String> getTraceIds() { return traceIds; }
        public void setTraceIds(List<String> traceIds) { this.traceIds = traceIds == null ? new ArrayList<>() : traceIds; }
        public String getSnapshotFingerprint() { return snapshotFingerprint; }
        public void setSnapshotFingerprint(String snapshotFingerprint) { this.snapshotFingerprint = snapshotFingerprint; }
        public String getLastSnapshotTime() { return lastSnapshotTime; }
        public void setLastSnapshotTime(String lastSnapshotTime) { this.lastSnapshotTime = lastSnapshotTime; }
    }
}
