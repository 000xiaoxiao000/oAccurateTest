package com.oAT.web.coveragecore.report;

import com.oAT.web.common.Job;
import com.oAT.web.coveragecore.diff.CoverageDiffService;
import com.oAT.web.esDao.ClassCoverageRepository;
import com.oAT.web.esDao.CoverageReportRepository;
import com.oAT.web.esDao.entity.ClassCoverageIndex;
import com.oAT.web.esDao.entity.CoverageReportIndex;
import com.oAT.web.language.java.JavaTraceCoverageMergeService;
import com.oAT.web.service.entity.AppVo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
public class CoverageReportReuseService {

    @Autowired
    private CoverageReportRepository coverageReportRepository;

    @Autowired
    private ClassCoverageRepository classCoverageRepository;

    @Autowired
    private CoverageDiffService coverageDiffService;

    @Autowired
    private JavaTraceCoverageMergeService javaTraceCoverageMergeService;

    @Autowired
    private CoverageSnapshotContextSupport coverageSnapshotContextSupport;

    public CoverageReuseResult prepareReuse(AppVo app, String appId, String versionNumber, String branch,
                                            String commitId, Integer reportType,
                                            CoverageSnapshotContext snapshotContext,
                                            Map<String, ClassCoverageIndex> coverageMap,
                                            Job<String> job) {
        CoverageReportIndex candidateLastReport = getBestReportForAccumulation(appId, versionNumber, branch, commitId, reportType);
        boolean mustRegenerateForCommitChange = mustRegenerateForCommitChange(candidateLastReport, commitId, reportType, job);
        if (canReuseReportDirectly(candidateLastReport, snapshotContext, mustRegenerateForCommitChange)) {
            if (job != null) {
                job.getLogger().info("快照指纹和更新时间完全一致，复用已有报告：" + candidateLastReport.getId());
                job.getProgress().next("复用已有报告（快照数据未变化）", 100);
            }
            return CoverageReuseResult.reuseExisting(candidateLastReport.getId());
        }

        CoverageReportIndex reusedReport = null;
        Map<String, List<Integer>> inheritanceDiffMap = null;
        List<String> traceIdsToProcess = new ArrayList<>(snapshotContext.getTraceIds());
        if (candidateLastReport != null) {
            List<String> previousSnapshotIds = coverageSnapshotContextSupport.parseSnapshotIds(candidateLastReport.getSnapshotIds());
            boolean appendOnlySnapshots = coverageSnapshotContextSupport.isAppendOnlySnapshotChange(previousSnapshotIds, snapshotContext.getSnapshotIds());
            boolean sameSnapshotSet = StringUtils.hasText(candidateLastReport.getSnapshotFingerprint())
                    && candidateLastReport.getSnapshotFingerprint().equals(snapshotContext.getSnapshotFingerprint());

            if (appendOnlySnapshots || sameSnapshotSet) {
                reusedReport = candidateLastReport;
                traceIdsToProcess = appendOnlySnapshots
                        ? new ArrayList<>(snapshotContext.getTraceIds().subList(previousSnapshotIds.size(), snapshotContext.getTraceIds().size()))
                        : new ArrayList<>();
                if (job != null) {
                    job.getLogger().info("发现可继承报告，快照变更模式: " + (appendOnlySnapshots ? "追加" : "无变化"));
                }

                if (StringUtils.hasText(reusedReport.getRepoCommitId())
                        && StringUtils.hasText(commitId)
                        && !reusedReport.getRepoCommitId().equals(commitId)) {
                    if (job != null) job.getLogger().info("正在对比代码差异以精准迁移覆盖率数据...");
                    inheritanceDiffMap = coverageDiffService.getDiffMap(app, reusedReport.getRepoCommitId(), commitId);
                }

                List<ClassCoverageIndex> lastClassCoverages = classCoverageRepository.findByReportId(reusedReport.getId());
                for (ClassCoverageIndex lastClassCoverage : lastClassCoverages) {
                    ClassCoverageIndex currentClassCoverage = coverageMap.get(lastClassCoverage.getClassName());
                    if (currentClassCoverage != null) {
                        javaTraceCoverageMergeService.reuseMatchedCoverageData(currentClassCoverage, lastClassCoverage, inheritanceDiffMap);
                    }
                }
            } else if (job != null) {
                job.getLogger().info("系统快照集合存在新增/删除/重排，将按当前快照全集全量重算覆盖率。");
            }
        }
        return CoverageReuseResult.continueWith(reusedReport, inheritanceDiffMap, traceIdsToProcess);
    }

    private boolean mustRegenerateForCommitChange(CoverageReportIndex candidateLastReport, String commitId,
                                                  Integer reportType, Job<String> job) {
        boolean mustRegenerate = CoverageReportTypes.normalize(reportType) == CoverageReportTypes.VERSION_FULL
                && candidateLastReport != null
                && StringUtils.hasText(commitId)
                && StringUtils.hasText(candidateLastReport.getRepoCommitId())
                && !commitId.equals(candidateLastReport.getRepoCommitId());
        if (mustRegenerate && job != null) {
            job.getLogger().info("版本全量报告：检测到当前 Commit 已变更 ["
                    + candidateLastReport.getRepoCommitId().substring(0, Math.min(7, candidateLastReport.getRepoCommitId().length()))
                    + " -> " + commitId.substring(0, Math.min(7, commitId.length()))
                    + "]，将基于新 Commit 重新生成报告");
        }
        return mustRegenerate;
    }

    private boolean canReuseReportDirectly(CoverageReportIndex candidateLastReport,
                                           CoverageSnapshotContext snapshotContext,
                                           boolean mustRegenerateForCommitChange) {
        return candidateLastReport != null
                && !mustRegenerateForCommitChange
                && StringUtils.hasText(candidateLastReport.getSnapshotFingerprint())
                && candidateLastReport.getSnapshotFingerprint().equals(snapshotContext.getSnapshotFingerprint())
                && Objects.equals(candidateLastReport.getSnapshotLastUpdateTime(), snapshotContext.getLastSnapshotTime());
    }

    private CoverageReportIndex getBestReportForAccumulation(String appId, String versionNumber, String branch,
                                                             String commitId, Integer reportType) {
        List<CoverageReportIndex> reports = coverageReportRepository.findByAppIdAndVersionNumber(appId, versionNumber);
        if (reports == null || reports.isEmpty()) {
            return null;
        }

        List<CoverageReportIndex> filtered = new ArrayList<>();
        int normalizedType = CoverageReportTypes.normalize(reportType);
        for (CoverageReportIndex report : reports) {
            if (CoverageReportTypes.normalize(report.getReportType()) == normalizedType) {
                filtered.add(report);
            }
        }
        if (filtered.isEmpty()) {
            return null;
        }

        filtered.sort((a, b) -> {
            if (a.getCreateTime() == null) return 1;
            if (b.getCreateTime() == null) return -1;
            return b.getCreateTime().compareTo(a.getCreateTime());
        });

        if (StringUtils.hasText(commitId)) {
            for (CoverageReportIndex report : filtered) {
                if (commitId.equals(report.getRepoCommitId())) {
                    return report;
                }
            }
            if (StringUtils.hasText(branch)) {
                for (CoverageReportIndex report : filtered) {
                    if (branch.equals(report.getRepoBranch())) {
                        return report;
                    }
                }
            }
            return filtered.get(0);
        }

        if (StringUtils.hasText(branch)) {
            for (CoverageReportIndex report : filtered) {
                if (branch.equals(report.getRepoBranch())) {
                    return report;
                }
            }
        }
        return filtered.get(0);
    }

    public static final class CoverageReuseResult {
        private final String existingReportId;
        private final CoverageReportIndex reusedReport;
        private final Map<String, List<Integer>> inheritanceDiffMap;
        private final List<String> traceIdsToProcess;

        private CoverageReuseResult(String existingReportId, CoverageReportIndex reusedReport,
                                    Map<String, List<Integer>> inheritanceDiffMap,
                                    List<String> traceIdsToProcess) {
            this.existingReportId = existingReportId;
            this.reusedReport = reusedReport;
            this.inheritanceDiffMap = inheritanceDiffMap;
            this.traceIdsToProcess = traceIdsToProcess;
        }

        public static CoverageReuseResult reuseExisting(String reportId) {
            return new CoverageReuseResult(reportId, null, null, new ArrayList<>());
        }

        public static CoverageReuseResult continueWith(CoverageReportIndex reusedReport,
                                                       Map<String, List<Integer>> inheritanceDiffMap,
                                                       List<String> traceIdsToProcess) {
            return new CoverageReuseResult(null, reusedReport, inheritanceDiffMap, traceIdsToProcess);
        }

        public String getExistingReportId() {
            return existingReportId;
        }

        public CoverageReportIndex getReusedReport() {
            return reusedReport;
        }

        public Map<String, List<Integer>> getInheritanceDiffMap() {
            return inheritanceDiffMap;
        }

        public List<String> getTraceIdsToProcess() {
            return traceIdsToProcess;
        }
    }
}
