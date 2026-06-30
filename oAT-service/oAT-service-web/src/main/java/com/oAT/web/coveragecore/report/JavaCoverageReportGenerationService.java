package com.oAT.web.coveragecore.report;

import com.oAT.web.common.Job;
import com.oAT.web.coveragecore.diff.CoverageDiffService;
import com.oAT.web.esDao.StaticInfoRepository;
import com.oAT.web.esDao.entity.ClassCoverageIndex;
import com.oAT.web.esDao.entity.CoverageReportIndex;
import com.oAT.web.esDao.entity.StaticSourceClassInfo;
import com.oAT.web.esDao.entity.StaticSourceInfo;
import com.oAT.web.language.java.JavaCoverageClassMatcher;
import com.oAT.web.language.java.JavaSourcePresenceService;
import com.oAT.web.language.java.JavaStaticCoverageStructureService;
import com.oAT.web.service.AppService;
import com.oAT.web.service.entity.AppVo;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class JavaCoverageReportGenerationService implements JavaCoverageReportEngine {

    private final StaticInfoRepository staticInfoRepository;
    private final AppService appService;
    private final JavaSourcePresenceService javaSourcePresenceService;
    private final CoverageDiffService coverageDiffService;
    private final JavaCoverageClassMatcher javaCoverageClassMatcher;
    private final JavaStaticCoverageStructureService javaStaticCoverageStructureService;
    private final CoverageReportSummaryPersistenceService coverageReportSummaryPersistenceService;
    private final CoverageSnapshotSelectionService coverageSnapshotSelectionService;
    private final CoverageVersionFullCommitReportService coverageVersionFullCommitReportService;
    private final CoverageSnapshotTraceAggregationService coverageSnapshotTraceAggregationService;
    private final CoverageReportReuseService coverageReportReuseService;

    public JavaCoverageReportGenerationService(StaticInfoRepository staticInfoRepository,
                                               AppService appService,
                                               JavaSourcePresenceService javaSourcePresenceService,
                                               CoverageDiffService coverageDiffService,
                                               JavaCoverageClassMatcher javaCoverageClassMatcher,
                                               JavaStaticCoverageStructureService javaStaticCoverageStructureService,
                                               CoverageReportSummaryPersistenceService coverageReportSummaryPersistenceService,
                                               CoverageSnapshotSelectionService coverageSnapshotSelectionService,
                                               CoverageVersionFullCommitReportService coverageVersionFullCommitReportService,
                                               CoverageSnapshotTraceAggregationService coverageSnapshotTraceAggregationService,
                                               CoverageReportReuseService coverageReportReuseService) {
        this.staticInfoRepository = staticInfoRepository;
        this.appService = appService;
        this.javaSourcePresenceService = javaSourcePresenceService;
        this.coverageDiffService = coverageDiffService;
        this.javaCoverageClassMatcher = javaCoverageClassMatcher;
        this.javaStaticCoverageStructureService = javaStaticCoverageStructureService;
        this.coverageReportSummaryPersistenceService = coverageReportSummaryPersistenceService;
        this.coverageSnapshotSelectionService = coverageSnapshotSelectionService;
        this.coverageVersionFullCommitReportService = coverageVersionFullCommitReportService;
        this.coverageSnapshotTraceAggregationService = coverageSnapshotTraceAggregationService;
        this.coverageReportReuseService = coverageReportReuseService;
    }

    @Override
    public String generateReport(String appId, String versionNumber, String branch, String commitId,
                                 Integer reportType, String baseVersionNumber, String baseCommitId, Job<String> job) {
        if (job != null) job.getLogger().info("正在获取应用配置信息...");
        AppVo app = appService.getApp(appId);

        coverageDiffService.clearCache();
        javaSourcePresenceService.clearCache();

        if (CoverageReportTypes.normalize(reportType) == CoverageReportTypes.VERSION_FULL) {
            String aggregatedReportId = coverageVersionFullCommitReportService.tryGenerate(appId, versionNumber, branch, commitId, job);
            if (StringUtils.hasText(aggregatedReportId)) {
                return aggregatedReportId;
            }
        }

        if (job != null) job.getProgress().next("加载静态源码信息", 10);
        List<StaticSourceInfo> appStaticInfos = staticInfoRepository.findByAppId(appId);
        if (appStaticInfos.isEmpty()) {
            throw new RuntimeException("找不到静态源码数据，请确保已进行静态扫描或源码上传。");
        }

        if (StringUtils.hasText(commitId)) {
            if (job != null) {
                job.getLogger().info("正在校验当前 Commit [" + commitId.substring(0, Math.min(7, commitId.length())) + "] 中存在的类...");
            }
            JavaSourcePresenceService.SourcePresenceFilterResult sourcePresence =
                    javaSourcePresenceService.filterExistingClasses(app, commitId, appStaticInfos);
            if (sourcePresence.isFiltered()) {
                for (String className : sourcePresence.getMissingClassNames()) {
                    if (job != null) {
                        job.getLogger().info("类 " + className + " 在当前 Commit 中不存在（" + sourcePresence.getSourceHint() + "中未找到），已从报告中剔除。");
                    }
                }
                appStaticInfos = sourcePresence.getStaticInfos();
            } else if (StringUtils.hasText(sourcePresence.getSourceHint()) && job != null) {
                job.getLogger().info("当前 Commit 未匹配到静态源码文件，已降级使用应用现有静态源码数据继续生成报告。请确认版本源码包或 Git 仓库路径是否完整。");
            }
        }

        if (appStaticInfos.isEmpty()) {
            throw new RuntimeException("当前 Commit 中没有找到有效的静态源码数据，请检查 Git 分支/Commit 是否正确。");
        }

        Map<String, List<Integer>> incrementalDiffMap = null;
        if (CoverageReportTypes.isIncremental(reportType) && StringUtils.hasText(baseCommitId)) {
            if (job != null) job.getLogger().info("正在获取基准 Commit [" + baseCommitId + "] 的差异对比...");
            incrementalDiffMap = coverageDiffService.getUncachedDiffMap(app, baseCommitId, commitId);
            if (incrementalDiffMap == null || incrementalDiffMap.isEmpty()) {
                throw new RuntimeException("两个版本间没有代码差异，无法生成增量报告。");
            }
        }

        Map<String, ClassCoverageIndex> coverageMap = buildInitialCoverageMap(appId, reportType, incrementalDiffMap, appStaticInfos);
        if (coverageMap.isEmpty()) {
            throw new RuntimeException("筛选后没有可覆盖的代码行，无法生成报告。");
        }

        CoverageSnapshotContext snapshotContext = coverageSnapshotSelectionService.buildSnapshotCoverageContext(app, versionNumber, commitId, reportType);
        if (snapshotContext.getSnapshotIds().isEmpty()) {
            throw new RuntimeException("当前版本下没有可用系统快照，无法生成覆盖率报告。");
        }
        if (job != null) {
            job.getLogger().info("本次将基于系统快照生成报告，快照数量: " + snapshotContext.getSnapshotIds().size());
            job.getProgress().next("检查是否存在可复用报告", 12);
            job.getProgress().next("分析历史报告并进行覆盖率数据累积", 15);
        }

        CoverageReportReuseService.CoverageReuseResult reuseResult = coverageReportReuseService.prepareReuse(
                app, appId, versionNumber, branch, commitId, reportType, snapshotContext, coverageMap, job);
        if (StringUtils.hasText(reuseResult.getExistingReportId())) {
            return reuseResult.getExistingReportId();
        }

        if (CoverageReportTypes.normalize(reportType) == CoverageReportTypes.VERSION_FULL) {
            baseVersionNumber = null;
            baseCommitId = null;
        }

        if (job != null) job.getProgress().next("处理系统快照中的链路追踪数据", 50);
        coverageSnapshotTraceAggregationService.aggregateTraceCoverage(
                appId, versionNumber, reportType, snapshotContext, reuseResult.getTraceIdsToProcess(), coverageMap, job);

        if (job != null) job.getProgress().next("保存报告并计算摘要", 20);
        CoverageReportIndex report = buildReport(appId, versionNumber, branch, commitId, reportType,
                baseVersionNumber, baseCommitId, snapshotContext, reuseResult);
        coverageReportSummaryPersistenceService.saveAndCalculateSummary(report, coverageMap, reuseResult.getInheritanceDiffMap());
        return report.getId();
    }

    private Map<String, ClassCoverageIndex> buildInitialCoverageMap(String appId,
                                                                    Integer reportType,
                                                                    Map<String, List<Integer>> incrementalDiffMap,
                                                                    List<StaticSourceInfo> appStaticInfos) {
        Map<String, ClassCoverageIndex> coverageMap = new HashMap<>();
        for (StaticSourceInfo staticInfo : appStaticInfos) {
            StaticSourceClassInfo classInfo = staticInfo.getClassInfo();
            if (classInfo == null) {
                continue;
            }
            String ownerClassName = javaCoverageClassMatcher.resolveCoverageOwnerClassName(classInfo.getClassName());
            if (CoverageReportTypes.isIncremental(reportType)
                    && (incrementalDiffMap == null || coverageDiffService.getChangedLinesForClass(incrementalDiffMap, ownerClassName) == null)) {
                continue;
            }
            ClassCoverageIndex classCov = coverageMap.computeIfAbsent(ownerClassName,
                    key -> javaStaticCoverageStructureService.createInitialClassCoverage(appId, ownerClassName));
            javaStaticCoverageStructureService.appendStaticClassCoverage(classCov, classInfo, incrementalDiffMap);
        }
        return coverageMap;
    }

    private CoverageReportIndex buildReport(String appId,
                                            String versionNumber,
                                            String branch,
                                            String commitId,
                                            Integer reportType,
                                            String baseVersionNumber,
                                            String baseCommitId,
                                            CoverageSnapshotContext snapshotContext,
                                            CoverageReportReuseService.CoverageReuseResult reuseResult) {
        CoverageReportIndex report = new CoverageReportIndex();
        report.setId(UUID.randomUUID().toString());
        report.setAppId(appId);
        report.setVersionNumber(versionNumber);
        report.setRepoBranch(branch);
        report.setRepoCommitId(commitId);
        report.setCreateTime(new Date());
        report.setLastProcessedTime(snapshotContext.getLastSnapshotTime());
        report.setReportType(reportType);
        report.setBaseVersionNumber(baseVersionNumber);
        report.setBaseRepoCommitId(CoverageReportTypes.isIncremental(reportType)
                ? (reuseResult.getReusedReport() != null ? reuseResult.getReusedReport().getRepoCommitId() : baseCommitId)
                : null);
        report.setSnapshotFingerprint(snapshotContext.getSnapshotFingerprint());
        report.setSnapshotLastUpdateTime(snapshotContext.getLastSnapshotTime());
        report.setSnapshotCount(snapshotContext.getSnapshotIds().size());
        report.setSnapshotIds(String.join(",", snapshotContext.getSnapshotIds()));
        return report;
    }
}
