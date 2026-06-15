package com.oAT.web.service.impl;

import com.alibaba.excel.EasyExcel;
import com.oAT.agent.model.HttpTraceNode;
import com.oAT.agent.model.StackNodeVo;
import com.oAT.agent.model.TraceNode;
import com.oAT.web.common.CoverageMethodKeyUtil;
import com.oAT.web.common.CoverageSourceClassUtil;
import com.oAT.web.common.FriendlyErrorMessageUtil;
import com.oAT.web.common.Job;
import com.oAT.web.esDao.*;
import com.oAT.web.esDao.entity.*;
import com.oAT.web.esDao.entity.ClassCoverageIndex.MethodCoverageDetail;
import com.oAT.web.service.AppService;
import com.oAT.web.service.CoverageService;
import com.oAT.web.service.GitService;
import com.oAT.web.service.entity.AppVo;
import com.oAT.web.service.entity.CoverageComparisonVo;
import com.oAT.web.service.entity.CoverageTreeNode;
import com.oAT.web.service.entity.GitDiffVo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import jakarta.servlet.http.HttpServletResponse;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import com.oAT.web.service.ResourceService;
import com.oAT.web.esDao.entity.MethodCoverageExportVo;

@Service
public class CoverageServiceImpl implements CoverageService, InitializingBean, StandardDate {
    private static final Logger logger = LoggerFactory.getLogger(CoverageServiceImpl.class);

    private final Map<String, List<String>> zipEntryCache = new ConcurrentHashMap<>();

    // Git Diff result cache to improve performance during report generation
    private final Map<String, Map<String, List<Integer>>> diffCache = new ConcurrentHashMap<>();

    @Autowired
    private com.oAT.web.coverage.CoverageStorage coverageStorage;
    @Autowired
    private StaticInfoRepository staticInfoRepository;
    @Autowired
    private TraceNodeRepository traceNodeRepository;
    @Autowired
    private CoverageReportRepository coverageReportRepository;
    @Autowired
    private ClassCoverageRepository classCoverageRepository;
    @Autowired
    private AppService appService;
    @Autowired
    private GitService gitService;
    @Autowired
    private ResourceService resourceService;
    @Autowired
    private VersionCenterRepository versionCenterRepository;
    @Autowired
    private SystemSnapshotRepository systemSnapshotRepository;

    @Autowired
    private SnapshotCommitMappingRepository snapshotCommitMappingRepository;

    private final Set<String> sourceClassIndexEnsuredReports = ConcurrentHashMap.newKeySet();
    private final Set<String> runningReportGenerationKeys = ConcurrentHashMap.newKeySet();

    private ExecutorService jobExecutors;
    private java.util.concurrent.ScheduledExecutorService jobCleanupExecutor;
    private List<Job<String>> jobs;

    private static final int TREE_NODE_SCAN_PAGE_SIZE = 2000;
    private static final int REPORT_TYPE_VERSION_FULL = 0;
    private static final int REPORT_TYPE_INCREMENTAL = 1;
    private static final int REPORT_TYPE_CURRENT_COMMIT = 2;

    @Override
    public void afterPropertiesSet() {
        jobExecutors = Executors.newFixedThreadPool(5);
        jobCleanupExecutor = Executors.newSingleThreadScheduledExecutor();
        jobs = Collections.synchronizedList(new LinkedList<>());
    }

    @Override
    public String startGenerateJob(String appId, String versionNumber, String branch, String commitId) {
        return startJob(appId, versionNumber, branch, commitId, REPORT_TYPE_VERSION_FULL, null, null);
    }

    @Override
    public String startGenerateCurrentCommitJob(String appId, String versionNumber, String branch, String commitId) {
        return startJob(appId, versionNumber, branch, commitId, REPORT_TYPE_CURRENT_COMMIT, null, null);
    }

    @Override
    public String startGenerateIncrementalJob(String appId, String versionNumber, String branch, String commitId,
                                            String baseVersionNumber, String baseCommitId) {
        return startJob(appId, versionNumber, branch, commitId, REPORT_TYPE_INCREMENTAL, baseVersionNumber, baseCommitId);
    }

    private String startJob(String appId, String versionNumber, String branch, String commitId,
                          Integer reportType, String baseVersionNumber, String baseCommitId) {
        String taskKey = buildReportGenerationKey(appId, versionNumber, commitId, reportType, baseVersionNumber, baseCommitId);
        if (!runningReportGenerationKeys.add(taskKey)) {
            throw new RuntimeException("该应用版本的覆盖率报告正在生成中，请等待当前任务完成后再试。");
        }

        String appDisplayName = appId;
        try {
            AppVo app = appService.getApp(appId);
            if (app != null) {
                appDisplayName = String.format("%s(%s)", appId, app.getName());
            }
        } catch (Exception e) {
            logger.warn("Get app info failed for logging: {}", appId);
        }

        final String finalAppDisplayName = appDisplayName;
        String jobName = finalAppDisplayName + ":" + versionNumber + " (" + reportTypeName(reportType) + ")";
        Job<String> job = new Job<>(jobName);
        jobs.add(job);
        jobExecutors.execute(() -> {
            try {
                job.state = Job.JobState.active;
                job.getProgress().next("初始化生成任务", 5);
                job.getLogger().info("开始为应用 [" + finalAppDisplayName + "] 生成" + reportTypeName(reportType) + "覆盖率报告...");

                String reportId = generateReportInternal(appId, versionNumber, branch, commitId, reportType, baseVersionNumber, baseCommitId, job);

                job.setData(reportId);
                job.state = Job.JobState.finish;
                job.getProgress().finish("报告生成完成");
                job.getLogger().info("报告生成成功: " + reportId);
                scheduleJobCleanup(job);
            } catch (Exception e) {
                logger.error("Generate report failed", e);
                job.state = Job.JobState.error;
                String errorMsg = toFriendlyError(e.getMessage());
                job.getProgress().updateName("生成失败: " + errorMsg);
                job.getLogger().error("生成报告失败: " + errorMsg);
                scheduleJobCleanup(job);
            } finally {
                runningReportGenerationKeys.remove(taskKey);
            }
        });
        return job.getId();
    }

    private String toFriendlyError(String msg) {
        return FriendlyErrorMessageUtil.general(new RuntimeException(msg));
    }

    /**
     * Schedule job cleanup after a delay to allow frontend to poll final state
     */
    private void scheduleJobCleanup(Job<String> job) {
        // Wait 30 seconds before removing the job to allow frontend polling
        jobCleanupExecutor.schedule(() -> {
            jobs.remove(job);
            logger.debug("Cleaned up job: {}", job.getId());
        }, 30, java.util.concurrent.TimeUnit.SECONDS);
    }

    @Override
    public Job<String> getJob(String jobId) {
        return jobs.stream().filter(j -> j.getId().equals(jobId)).findFirst().orElse(null);
    }

    @Override
    public CoverageReportIndex getReport(String reportId) {
        if (!StringUtils.hasText(reportId)) {
            return null;
        }
        return coverageReportRepository.findById(reportId).orElse(null);
    }

    private String buildReportGenerationKey(String appId, String versionNumber, String commitId,
                                           Integer reportType, String baseVersionNumber, String baseCommitId) {
        return String.join("|",
                appId == null ? "" : appId,
                versionNumber == null ? "" : versionNumber,
                commitId == null ? "" : commitId,
                String.valueOf(normalizeReportType(reportType)),
                baseVersionNumber == null ? "" : baseVersionNumber,
                baseCommitId == null ? "" : baseCommitId);
    }

    private String generateReportInternal(String appId, String versionNumber, String branch, String commitId,
                                        Integer reportType, String baseVersionNumber, String baseCommitId, Job<String> job) {
        if (job != null) job.getLogger().info("正在获取应用配置信息...");
        AppVo app = appService.getApp(appId);

        diffCache.clear();
        zipEntryCache.clear();

        if (normalizeReportType(reportType) == REPORT_TYPE_VERSION_FULL) {
            String aggregatedReportId = tryGenerateVersionFullFromCommitReports(appId, versionNumber, branch, commitId, job);
            if (StringUtils.hasText(aggregatedReportId)) {
                return aggregatedReportId;
            }
        }

        // 1. Get Static Source Info
        if (job != null) job.getProgress().next("加载静态源码信息", 10);

        // Filter static info by branch and commitId to ensure we use the correct code structure for current commit
        List<StaticSourceInfo> appStaticInfos = staticInfoRepository.findByAppId(appId);

        if (appStaticInfos.isEmpty()) {
            throw new RuntimeException("找不到静态源码数据，请确保已进行静态扫描或源码上传。");
        }

        // Filter out classes that don't exist in the current commit
        // This ensures coverage reports only include classes that exist at the specified branch/commit
        if (StringUtils.hasText(commitId)) {
            if (job != null) job.getLogger().info("正在校验当前 Commit [" + commitId.substring(0, Math.min(7, commitId.length())) + "] 中存在的类...");
            List<StaticSourceInfo> filteredStaticInfos = new ArrayList<>();

            // 尝试从本地已拉取的源码（zip）中获取文件列表
            List<String> entryNames = getZipEntryNames(appId, commitId);
            Set<String> entrySet = entryNames != null ? new HashSet<>(entryNames) : null;

            for (StaticSourceInfo si : appStaticInfos) {
                if (si.getClassInfo() != null) {
                    String className = si.getClassInfo().getClassName();
                    boolean exists;

                    if (entrySet != null) {
                        exists = hasSourceEntry(entrySet, className);
                    } else {
                        // 兜底方案：如果没找到 zip，再尝试调用 git
                        String content = gitService.getFileContent(app.getRepoAddress(), app.getRepoUserName(), app.getRepoPassword(), commitId, className);
                        exists = (content != null);
                    }

                    if (exists) {
                        filteredStaticInfos.add(si);
                    } else if (job != null) {
                        job.getLogger().info("类 " + className + " 在当前 Commit 中不存在（本地源码包中未找到），已从报告中剔除。");
                    }
                }
            }
            if (filteredStaticInfos.isEmpty()) {
                String sourceHint = entrySet != null ? "本地源码包" : "Git 仓库";
                logger.warn("No static source matched appId={}, commitId={} by {}, fallback to existing static source info.", appId, commitId, sourceHint);
                if (job != null) {
                    job.getLogger().info("当前 Commit 未匹配到静态源码文件，已降级使用应用现有静态源码数据继续生成报告。请确认版本源码包或 Git 仓库路径是否完整。");
                }
            } else {
                appStaticInfos = filteredStaticInfos;
            }
        }

        if (appStaticInfos.isEmpty()) {
            throw new RuntimeException("当前 Commit 中没有找到有效的静态源码数据，请检查 Git 分支/Commit 是否正确。");
        }

        // 1.1 Diff Logic for Incremental Report
        Map<String, List<Integer>> incrementalDiffMap = null;
        if (isIncrementalReport(reportType) && StringUtils.hasText(baseCommitId)) {
            if (job != null) job.getLogger().info("正在获取基准 Commit [" + baseCommitId + "] 的差异对比...");
            incrementalDiffMap = toDiffMap(gitService.getDiffDetail(app.getRepoAddress(), app.getRepoUserName(), app.getRepoPassword(),
                    baseCommitId, commitId));
            if (incrementalDiffMap == null || incrementalDiffMap.isEmpty()) {
                throw new RuntimeException("两个版本间没有代码差异，无法生成增量报告。");
            }
        }

        Map<String, ClassCoverageIndex> coverageMap = new HashMap<>();
        for (StaticSourceInfo staticInfo : appStaticInfos) {
            StaticSourceClassInfo classInfo = staticInfo.getClassInfo();
            if (classInfo == null) continue;
            String ownerClassName = resolveCoverageOwnerClassName(classInfo.getClassName());

            // If incremental report, only include classes that have changes
            if (isIncrementalReport(reportType)) {
                if (incrementalDiffMap == null || getChangedLinesForClass(incrementalDiffMap, ownerClassName) == null) {
                    continue;
                }
            }

            ClassCoverageIndex classCov = coverageMap.computeIfAbsent(ownerClassName,
                    key -> createInitialClassCoverage(appId, ownerClassName));
            appendStaticClassCoverage(classCov, classInfo, incrementalDiffMap);
        }

        if (coverageMap.isEmpty()) {
             throw new RuntimeException("筛选后没有可覆盖的代码行，无法生成报告。");
        }

        // 2. Build snapshot context and decide whether this run can reuse the previous report.
        SnapshotCoverageContext snapshotContext = buildSnapshotCoverageContext(app, versionNumber, commitId, reportType);
        if (snapshotContext.getSnapshotIds().isEmpty()) {
            throw new RuntimeException("当前版本下没有可用系统快照，无法生成覆盖率报告。");
        }
        if (job != null) {
            job.getLogger().info("本次将基于系统快照生成报告，快照数量: " + snapshotContext.getSnapshotIds().size());
        }

        if (job != null) job.getProgress().next("检查是否存在可复用报告", 12);

        CoverageReportIndex candidateLastReport =
                getBestReportForAccumulation(appId, versionNumber, branch, commitId, reportType);
        
        // For VERSION_FULL reports: check if commitId has changed - if yes, MUST regenerate
        // VERSION_FULL should aggregate ALL commits under the version, but code structure follows latest commit
        boolean mustRegenerateForCommitChange = false;
        if (normalizeReportType(reportType) == REPORT_TYPE_VERSION_FULL 
                && candidateLastReport != null 
                && StringUtils.hasText(commitId)
                && StringUtils.hasText(candidateLastReport.getRepoCommitId())
                && !commitId.equals(candidateLastReport.getRepoCommitId())) {
            mustRegenerateForCommitChange = true;
            if (job != null) {
                job.getLogger().info("版本全量报告：检测到当前 Commit 已变更 [" + 
                    candidateLastReport.getRepoCommitId().substring(0, Math.min(7, candidateLastReport.getRepoCommitId().length())) + " -> " + 
                    commitId.substring(0, Math.min(7, commitId.length())) + "]，将基于新 Commit 重新生成报告");
            }
        }
        
        // Can only reuse if: not a commit change AND snapshot data unchanged
        if (candidateLastReport != null 
                && !mustRegenerateForCommitChange
                && StringUtils.hasText(candidateLastReport.getSnapshotFingerprint())
                && candidateLastReport.getSnapshotFingerprint().equals(snapshotContext.getSnapshotFingerprint())
                && Objects.equals(candidateLastReport.getSnapshotLastUpdateTime(), snapshotContext.getLastSnapshotTime())) {
            if (job != null) {
                job.getLogger().info("快照指纹和更新时间完全一致，复用已有报告：" + candidateLastReport.getId());
                job.getProgress().next("复用已有报告（快照数据未变化）", 100);
            }
            return candidateLastReport.getId();
        }

        if (job != null) job.getProgress().next("分析历史报告并进行覆盖率数据累积", 15);

        CoverageReportIndex reusedReport = null;
        Map<String, List<Integer>> inheritanceDiffMap = null;

        List<String> traceIdsToProcess = new ArrayList<>(snapshotContext.getTraceIds());
        if (candidateLastReport != null) {
            List<String> previousSnapshotIds = parseSnapshotIds(candidateLastReport.getSnapshotIds());
            boolean appendOnlySnapshots = isAppendOnlySnapshotChange(previousSnapshotIds, snapshotContext.getSnapshotIds());
            boolean sameSnapshotSet = StringUtils.hasText(candidateLastReport.getSnapshotFingerprint())
                    && candidateLastReport.getSnapshotFingerprint().equals(snapshotContext.getSnapshotFingerprint());

            if (appendOnlySnapshots || sameSnapshotSet) {
                reusedReport = candidateLastReport;
                if (appendOnlySnapshots) {
                    traceIdsToProcess = new ArrayList<>(snapshotContext.getTraceIds().subList(previousSnapshotIds.size(), snapshotContext.getTraceIds().size()));
                } else {
                    traceIdsToProcess = Collections.emptyList();
                }

                if (job != null) {
                    job.getLogger().info("发现可继承报告，快照变更模式: " + (appendOnlySnapshots ? "追加" : "无变化"));
                }

                if (StringUtils.hasText(reusedReport.getRepoCommitId())
                        && StringUtils.hasText(commitId)
                        && !reusedReport.getRepoCommitId().equals(commitId)) {
                    if (job != null) job.getLogger().info("正在对比代码差异以精准迁移覆盖率数据...");
                    inheritanceDiffMap = getCachedDiff(app, reusedReport.getRepoCommitId(), commitId);
                }

                List<ClassCoverageIndex> lastClassCovs = classCoverageRepository.findByReportId(reusedReport.getId());
                for (ClassCoverageIndex lastCc : lastClassCovs) {
                    ClassCoverageIndex currentCc = coverageMap.get(lastCc.getClassName());
                    if (currentCc != null) {
                        reuseMatchedCoverageData(currentCc, lastCc, inheritanceDiffMap);
                    }
                }
            } else if (job != null) {
                job.getLogger().info("系统快照集合存在新增/删除/重排，将按当前快照全集全量重算覆盖率。");
            }
        }
        
        if (normalizeReportType(reportType) == REPORT_TYPE_VERSION_FULL) {
            baseVersionNumber = null;
            baseCommitId = null;
        }

        // 3. Aggregate TraceNode data from snapshot timeline
        if (job != null) job.getProgress().next("处理系统快照中的链路追踪数据", 50);
        
        // For VERSION_FULL reports: pre-load commit mappings and prepare per-commit coverage tracking
        boolean isVersionFullReport = normalizeReportType(reportType) == REPORT_TYPE_VERSION_FULL;
        Map<String, String> snapshotToCommitMap = new HashMap<>();
        Map<String, Map<String, ClassCoverageIndex>> perCommitCoverageSnapshots = new LinkedHashMap<>();
        
        if (isVersionFullReport && !snapshotContext.getSnapshotIds().isEmpty()) {
            // Batch query all snapshot-commit mappings once to avoid N+1 queries
            List<SnapshotCommitMapping> allMappings = snapshotCommitMappingRepository
                    .findByAppIdAndVersionNumber(appId, versionNumber);
            
            for (SnapshotCommitMapping mapping : allMappings) {
                if (mapping != null && StringUtils.hasText(mapping.getSnapshotId()) 
                        && StringUtils.hasText(mapping.getRepoCommitId())) {
                    snapshotToCommitMap.put(mapping.getSnapshotId(), mapping.getRepoCommitId());
                }
            }
            
            if (!snapshotToCommitMap.isEmpty()) {
                if (job != null) {
                    job.getLogger().info("版本全量报告：加载了 " + snapshotToCommitMap.size() + 
                        " 条快照-Commit 映射关系，将按 Commit 分组追踪覆盖率差异");
                }
                
                // Initialize per-commit coverage maps with empty baseline
                Set<String> uniqueCommits = new HashSet<>(snapshotToCommitMap.values());
                for (String commitKey : uniqueCommits) {
                    Map<String, ClassCoverageIndex> commitCoverageMap = new HashMap<>();
                    for (Map.Entry<String, ClassCoverageIndex> entry : coverageMap.entrySet()) {
                        ClassCoverageIndex emptyClassCov = createInitialClassCoverage(appId, entry.getKey());
                        emptyClassCov.setMethods(new ArrayList<>());
                        for (MethodCoverageDetail method : entry.getValue().getMethods()) {
                            MethodCoverageDetail emptyMethod = new MethodCoverageDetail();
                            emptyMethod.setMethodName(method.getMethodName());
                            emptyMethod.setMethodDesc(method.getMethodDesc());
                            emptyMethod.setTotalLines(method.getTotalLines());
                            emptyMethod.setTotalLineNumbers(method.getTotalLineNumbers());
                            emptyMethod.setTotalBranches(method.getTotalBranches());
                            emptyMethod.setTotalBranchTargets(method.getTotalBranchTargets());
                            emptyMethod.setTotalBranchTargetProbeMap(copyBranchTargetProbeMap(method.getTotalBranchTargetProbeMap()));
                            emptyMethod.setComplexity(method.getComplexity());
                            emptyMethod.setCoveredLineNumbers(new ArrayList<>());
                            emptyMethod.setCoveredBranchLines(new ArrayList<>());
                            emptyMethod.setCoveredBranchTargetProbeMap(new LinkedHashMap<>());
                            emptyClassCov.getMethods().add(emptyMethod);
                        }
                        emptyClassCov.setTotalMethods(entry.getValue().getTotalMethods());
                        emptyClassCov.setTotalLines(entry.getValue().getTotalLines());
                        emptyClassCov.setTotalBranches(entry.getValue().getTotalBranches());
                        emptyClassCov.setTotalBranchTargets(entry.getValue().getTotalBranchTargets());
                        emptyClassCov.setTotalComplexity(entry.getValue().getTotalComplexity());
                        commitCoverageMap.put(entry.getKey(), emptyClassCov);
                    }
                    perCommitCoverageSnapshots.put(commitKey, commitCoverageMap);
                }
            }
        }
        
        int processed = 0;
        int total = traceIdsToProcess.size();
        
        for (String traceId : traceIdsToProcess) {
            // Merge into global coverage map
            mergeSnapshotTraceCoverage(traceId, coverageMap);
            
            // For VERSION_FULL: also merge into per-commit map if this snapshot has a commit mapping
            if (isVersionFullReport && processed < snapshotContext.getSnapshotIds().size()) {
                String snapshotId = snapshotContext.getSnapshotIds().get(processed);
                String commitKey = snapshotToCommitMap.get(snapshotId);
                
                if (StringUtils.hasText(commitKey)) {
                    Map<String, ClassCoverageIndex> commitCoverageMap = perCommitCoverageSnapshots.get(commitKey);
                    if (commitCoverageMap != null) {
                        mergeSnapshotTraceCoverage(traceId, commitCoverageMap);
                    }
                }
            }
            
            processed++;
            if (job != null) {
                job.getProgress().total = total;
                job.getProgress().loaded = processed;
                job.getLogger().info("正在处理快照链路数据 (" + processed + "/" + total + ")");
            }
        }
        
        // For VERSION_FULL with multiple commit states: mark classes/methods with coverage differences
        if (isVersionFullReport && perCommitCoverageSnapshots.size() > 1) {
            if (job != null) {
                job.getLogger().info("版本全量报告：检测到 " + perCommitCoverageSnapshots.size() + 
                    " 个 Commit 的覆盖率数据，正在标记跨 Commit 的覆盖率差异...");
            }
            markCrossCommitCoverageChanges(coverageMap, perCommitCoverageSnapshots, job);
        }

        // 4. Final Calculation & Save
        if (job != null) job.getProgress().next("保存报告并计算摘要", 20);
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
        report.setBaseRepoCommitId(isIncrementalReport(reportType) ? (reusedReport != null ? reusedReport.getRepoCommitId() : baseCommitId) : null);

        report.setSnapshotFingerprint(snapshotContext.getSnapshotFingerprint());
        report.setSnapshotLastUpdateTime(snapshotContext.getLastSnapshotTime());
        report.setSnapshotCount(snapshotContext.getSnapshotIds().size());
        report.setSnapshotIds(String.join(",", snapshotContext.getSnapshotIds()));

        if (reusedReport != null && job != null) {
            String inheritanceInfo = String.format("继承自报告: %s, 快照累积至: %s",
                    reusedReport.getId(),
                    snapshotContext.getLastSnapshotTime());
            job.getLogger().info("覆盖率数据累积完成：" + inheritanceInfo);
        }

        saveAndCalculateSummary(report, coverageMap, inheritanceDiffMap);

        return report.getId();
    }

    private String tryGenerateVersionFullFromCommitReports(String appId, String versionNumber, String branch,
                                                           String latestCommitId, Job<String> job) {
        List<CoverageReportIndex> commitReports = selectVersionCommitReports(appId, versionNumber);
        if (commitReports.isEmpty()) {
            if (job != null) {
                job.getLogger().info("未找到可汇总的本次 Commit 覆盖率报告，将按当前版本快照生成版本全量报告。");
            }
            return null;
        }

        CoverageReportIndex latestCommitReport = selectLatestCodeReport(commitReports, branch, latestCommitId);
        if (latestCommitReport == null) {
            return null;
        }

        if (job != null) {
            job.getProgress().next("汇总本版本 Commit 覆盖率", 45);
            job.getLogger().info("版本全量报告将汇总 " + commitReports.size() + " 份本次 Commit 报告，源码着色使用最新 Commit [" + shortCommit(latestCommitReport.getRepoCommitId()) + "]。");
        }

        CoverageReportIndex reusableReport = findReusableVersionFullReport(appId, versionNumber, latestCommitReport, commitReports);
        if (reusableReport != null) {
            if (job != null) {
                job.getLogger().info("Commit 报告集合未变化，复用已有版本全量报告：" + reusableReport.getId());
                job.getProgress().next("复用已有版本全量报告", 100);
            }
            return reusableReport.getId();
        }

        Map<String, ClassCoverageIndex> coverageMap = loadLatestCodeCoverageSkeleton(latestCommitReport);
        if (coverageMap.isEmpty()) {
            if (job != null) {
                job.getLogger().info("最新 Commit 报告缺少类覆盖率明细，将按当前版本快照生成版本全量报告。");
            }
            return null;
        }

        Map<String, Map<String, ClassCoverageIndex>> perCommitSnapshots = new LinkedHashMap<>();
        int processed = 0;
        for (CoverageReportIndex commitReport : commitReports) {
            List<ClassCoverageIndex> classCoverages = classCoverageRepository.findByReportId(commitReport.getId());
            if (classCoverages == null || classCoverages.isEmpty()) {
                continue;
            }
            Map<String, ClassCoverageIndex> commitCoverageMap = toCoverageMap(classCoverages);
            perCommitSnapshots.put(commitReport.getId(), commitCoverageMap);
            mergeCommitCoverageIntoLatestSkeleton(coverageMap, commitCoverageMap);
            processed++;
            if (job != null) {
                job.getProgress().loaded = processed;
                job.getProgress().total = commitReports.size();
            }
        }

        if (perCommitSnapshots.isEmpty()) {
            if (job != null) {
                job.getLogger().info("本次 Commit 报告明细为空，将按当前版本快照生成版本全量报告。");
            }
            return null;
        }

        recalculateClassCoverageCounters(coverageMap.values());
        markCrossCommitCoverageChanges(coverageMap, perCommitSnapshots, job);

        CoverageReportIndex report = new CoverageReportIndex();
        report.setId(UUID.randomUUID().toString());
        report.setAppId(appId);
        report.setVersionNumber(versionNumber);
        report.setRepoBranch(StringUtils.hasText(branch) ? branch : latestCommitReport.getRepoBranch());
        report.setRepoCommitId(latestCommitReport.getRepoCommitId());
        report.setCreateTime(new Date());
        report.setLastProcessedTime(latestCommitReport.getLastProcessedTime());
        report.setReportType(REPORT_TYPE_VERSION_FULL);
        report.setSnapshotFingerprint(buildCommitReportFingerprint(commitReports));
        report.setSnapshotLastUpdateTime(buildCommitReportLastUpdateTime(commitReports));
        report.setSnapshotCount(commitReports.stream()
                .mapToInt(reportIndex -> reportIndex.getSnapshotCount() == null ? 0 : Math.max(0, reportIndex.getSnapshotCount()))
                .sum());
        report.setSnapshotIds(commitReports.stream()
                .map(CoverageReportIndex::getId)
                .filter(StringUtils::hasText)
                .collect(Collectors.joining(",")));

        if (job != null) {
            job.getProgress().next("保存版本全量汇总报告", 20);
        }
        saveAndCalculateSummary(report, coverageMap, null);
        return report.getId();
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
            if (a.getCreateTime() == null) return 1;
            if (b.getCreateTime() == null) return -1;
            return b.getCreateTime().compareTo(a.getCreateTime());
        });
        return candidates.get(0);
    }

    private CoverageReportIndex findReusableVersionFullReport(String appId, String versionNumber,
                                                              CoverageReportIndex latestCommitReport,
                                                              List<CoverageReportIndex> commitReports) {
        List<CoverageReportIndex> reports = coverageReportRepository.findByAppIdAndVersionNumberAndReportType(appId, versionNumber, REPORT_TYPE_VERSION_FULL);
        if (reports == null || reports.isEmpty()) {
            return null;
        }
        String fingerprint = buildCommitReportFingerprint(commitReports);
        String latestCommit = normalizeCommitKey(latestCommitReport.getRepoCommitId());
        for (CoverageReportIndex report : reports) {
            if (!Objects.equals(fingerprint, report.getSnapshotFingerprint())) {
                continue;
            }
            if (StringUtils.hasText(latestCommit) && !latestCommit.equals(normalizeCommitKey(report.getRepoCommitId()))) {
                continue;
            }
            return report;
        }
        return null;
    }

    private Map<String, ClassCoverageIndex> loadLatestCodeCoverageSkeleton(CoverageReportIndex latestCommitReport) {
        List<ClassCoverageIndex> latestClasses = classCoverageRepository.findByReportId(latestCommitReport.getId());
        Map<String, ClassCoverageIndex> coverageMap = new LinkedHashMap<>();
        if (latestClasses == null) {
            return coverageMap;
        }
        for (ClassCoverageIndex latestClass : latestClasses) {
            if (latestClass == null || !StringUtils.hasText(latestClass.getClassName())) {
                continue;
            }
            ClassCoverageIndex skeleton = cloneClassCoverage(latestClass);
            resetCoverageCounters(skeleton);
            coverageMap.put(skeleton.getClassName(), skeleton);
        }
        return coverageMap;
    }

    private Map<String, ClassCoverageIndex> toCoverageMap(List<ClassCoverageIndex> classCoverages) {
        Map<String, ClassCoverageIndex> coverageMap = new LinkedHashMap<>();
        if (classCoverages == null) {
            return coverageMap;
        }
        for (ClassCoverageIndex classCoverage : classCoverages) {
            if (classCoverage != null && StringUtils.hasText(classCoverage.getClassName())) {
                coverageMap.put(classCoverage.getClassName(), classCoverage);
            }
        }
        return coverageMap;
    }

    private void mergeCommitCoverageIntoLatestSkeleton(Map<String, ClassCoverageIndex> targetMap,
                                                       Map<String, ClassCoverageIndex> commitCoverageMap) {
        for (Map.Entry<String, ClassCoverageIndex> entry : commitCoverageMap.entrySet()) {
            ClassCoverageIndex targetClass = targetMap.get(entry.getKey());
            if (targetClass == null || targetClass.getMethods() == null) {
                continue;
            }
            ClassCoverageIndex sourceClass = entry.getValue();
            if (sourceClass == null || sourceClass.getMethods() == null) {
                continue;
            }
            Map<String, MethodCoverageDetail> targetMethods = targetClass.getMethods().stream()
                    .collect(Collectors.toMap(method -> buildMethodKey(method.getMethodName(), method.getMethodDesc()), method -> method, (a, b) -> a, LinkedHashMap::new));
            for (MethodCoverageDetail sourceMethod : sourceClass.getMethods()) {
                MethodCoverageDetail targetMethod = targetMethods.get(buildMethodKey(sourceMethod.getMethodName(), sourceMethod.getMethodDesc()));
                if (targetMethod == null) {
                    continue;
                }
                mergeMethodCoverage(targetMethod, sourceMethod);
            }
        }
    }

    private void mergeMethodCoverage(MethodCoverageDetail targetMethod, MethodCoverageDetail sourceMethod) {
        targetMethod.setCoveredLineNumbers(unionIntegerLists(targetMethod.getCoveredLineNumbers(), sourceMethod.getCoveredLineNumbers()));
        targetMethod.setCoveredLines(targetMethod.getCoveredLineNumbers() == null ? 0 : targetMethod.getCoveredLineNumbers().size());
        targetMethod.setCoveredBranchLines(unionIntegerLists(targetMethod.getCoveredBranchLines(), sourceMethod.getCoveredBranchLines()));
        targetMethod.setCoveredBranches(Math.min(targetMethod.getTotalBranches(), targetMethod.getCoveredBranchLines() == null ? 0 : targetMethod.getCoveredBranchLines().size()));
        targetMethod.setCoveredBranchTargetProbeMap(mergeCoveredBranchTargetProbeMaps(targetMethod.getCoveredBranchTargetProbeMap(), sourceMethod.getCoveredBranchTargetProbeMap()));
        targetMethod.setCoveredBranchTargets(Math.min(targetMethod.getTotalBranchTargets(), countBranchTargets(targetMethod.getCoveredBranchTargetProbeMap())));
        targetMethod.setCovered(targetMethod.getCoveredLines() > 0 || targetMethod.getCoveredBranchTargets() > 0);
        targetMethod.setBranchRate(calculateBranchRate(targetMethod.getCoveredBranchTargets(), targetMethod.getTotalBranchTargets()));
    }

    private void recalculateClassCoverageCounters(Collection<ClassCoverageIndex> classCoverages) {
        if (classCoverages == null) {
            return;
        }
        for (ClassCoverageIndex classCoverage : classCoverages) {
            int coveredMethods = 0;
            int coveredLines = 0;
            int coveredBranches = 0;
            int coveredBranchTargets = 0;
            if (classCoverage.getMethods() != null) {
                for (MethodCoverageDetail method : classCoverage.getMethods()) {
                    boolean methodCovered = method.getCoveredLines() > 0 || method.getCoveredBranchTargets() > 0 || method.isCovered();
                    method.setCovered(methodCovered);
                    if (methodCovered) {
                        coveredMethods++;
                    }
                    coveredLines += method.getCoveredLines();
                    coveredBranches += method.getCoveredBranches();
                    coveredBranchTargets += method.getCoveredBranchTargets();
                }
            }
            classCoverage.setCoveredMethods(Math.min(classCoverage.getTotalMethods(), coveredMethods));
            classCoverage.setCoveredLines(Math.min(classCoverage.getTotalLines(), coveredLines));
            classCoverage.setCoveredBranches(Math.min(classCoverage.getTotalBranches(), coveredBranches));
            classCoverage.setCoveredBranchTargets(Math.min(classCoverage.getTotalBranchTargets(), coveredBranchTargets));
        }
    }

    private List<Integer> unionIntegerLists(List<Integer> first, List<Integer> second) {
        LinkedHashSet<Integer> values = new LinkedHashSet<>();
        if (first != null) values.addAll(first);
        if (second != null) values.addAll(second);
        return new ArrayList<>(values);
    }

    private Map<String, List<Integer>> mergeCoveredBranchTargetProbeMaps(Map<String, List<Integer>> first,
                                                                         Map<String, List<Integer>> second) {
        Map<String, LinkedHashSet<Integer>> merged = new LinkedHashMap<>();
        appendBranchTargetProbeMap(merged, first);
        appendBranchTargetProbeMap(merged, second);
        Map<String, List<Integer>> result = new LinkedHashMap<>();
        for (Map.Entry<String, LinkedHashSet<Integer>> entry : merged.entrySet()) {
            result.put(entry.getKey(), new ArrayList<>(entry.getValue()));
        }
        return result;
    }

    private void resetCoverageCounters(ClassCoverageIndex classCoverage) {
        classCoverage.setCoveredMethods(0);
        classCoverage.setCoveredLines(0);
        classCoverage.setCoveredBranches(0);
        classCoverage.setCoveredBranchTargets(0);
        classCoverage.setHasCodeChanges(false);
        if (classCoverage.getMethods() == null) {
            return;
        }
        for (MethodCoverageDetail method : classCoverage.getMethods()) {
            method.setCovered(false);
            method.setCoveredLines(0);
            method.setCoveredLineNumbers(new ArrayList<>());
            method.setCoveredBranches(0);
            method.setCoveredBranchLines(new ArrayList<>());
            method.setCoveredBranchTargets(0);
            method.setCoveredBranchTargetProbeMap(new LinkedHashMap<>());
            method.setBranchRate(calculateBranchRate(0, method.getTotalBranchTargets()));
            method.setHasCodeChanges(false);
        }
    }

    private ClassCoverageIndex cloneClassCoverage(ClassCoverageIndex original) {
        return deepCloneCoverageMap(Collections.singletonMap(original.getClassName(), original)).get(original.getClassName());
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

    private String buildCommitReportLastUpdateTime(List<CoverageReportIndex> commitReports) {
        Date latest = null;
        if (commitReports != null) {
            for (CoverageReportIndex report : commitReports) {
                Date candidate = report.getCreateTime();
                if (candidate != null && (latest == null || candidate.after(latest))) {
                    latest = candidate;
                }
            }
        }
        return latest == null ? null : new SimpleDateFormat(StandardDate.dateFormat).format(latest);
    }

    private String normalizeCommitKey(String commitId) {
        return StringUtils.hasText(commitId) ? commitId.trim().toLowerCase(Locale.ROOT) : null;
    }

    private boolean isReportAfter(CoverageReportIndex candidate, CoverageReportIndex current) {
        if (candidate == null) return false;
        if (current == null) return true;
        if (candidate.getCreateTime() == null) return false;
        if (current.getCreateTime() == null) return true;
        return candidate.getCreateTime().after(current.getCreateTime());
    }

    private String shortCommit(String commitId) {
        if (!StringUtils.hasText(commitId)) {
            return "-";
        }
        return commitId.substring(0, Math.min(7, commitId.length()));
    }

    private CoverageReportIndex getBestReportForAccumulation(String appId, String versionNumber, String branch,
                                                             String commitId, Integer reportType) {
        List<CoverageReportIndex> reports = coverageReportRepository.findByAppIdAndVersionNumber(appId, versionNumber);
        if (reports == null || reports.isEmpty()) {
            return null;
        }

        List<CoverageReportIndex> filtered = new ArrayList<>();
        int normalizedType = normalizeReportType(reportType);
        for (CoverageReportIndex report : reports) {
            if (normalizeReportType(report.getReportType()) == normalizedType) {
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

    private void mergeSnapshotTraceCoverage(String traceId, Map<String, ClassCoverageIndex> coverageMap) {
        if (!StringUtils.hasText(traceId)) {
            logger.debug("跳过覆盖率合并：traceId 为空");
            return;
        }

        List<StackNodeVo> codeNodes = coverageStorage.load(traceId);
        
        if (codeNodes.isEmpty()) {
            Optional<TraceNodeIndex> rootOptional = traceNodeRepository.findById(traceId + "_0");
            TraceNode rootNode = rootOptional.map(TraceNodeIndex::toTraceNode).orElse(null);

            if (!(rootNode instanceof HttpTraceNode)) {
                List<TraceNodeIndex> traceNodes = traceNodeRepository.findByTraceId(traceId, PageRequest.of(0, 200));
                for (TraceNodeIndex traceNodeIndex : traceNodes) {
                    TraceNode node = traceNodeIndex.toTraceNode();
                    if (node instanceof HttpTraceNode) {
                        rootNode = node;
                        break;
                    }
                }
            }

            if (!(rootNode instanceof HttpTraceNode)) {
                String nodeType = rootNode != null ? rootNode.getClass().getSimpleName() : "null";
                logger.debug("跳过覆盖率合并：traceId={} 不是 HTTP 请求（节点类型: {}），仅 HTTP 请求包含代码覆盖率数据", traceId, nodeType);
                return;
            }

            HttpTraceNode httpNode = (HttpTraceNode) rootNode;
            StackNodeVo[] legacyCodeNodes = httpNode.getCodeNodes();
            if (legacyCodeNodes != null && legacyCodeNodes.length > 0) {
                codeNodes = Arrays.asList(legacyCodeNodes);
            }
        }

        if (codeNodes.isEmpty()) {
            logger.warn("跳过覆盖率合并：traceId={} 的 codeNodes 为空，可能是 Java Agent 未正确采集覆盖率数据", traceId);
            return;
        }

        logger.info("开始合并覆盖率数据：traceId={}, codeNodes 数量={}, coverageMap 大小={}", 
                traceId, codeNodes.size(), coverageMap.size());
        
        int matchedCount = 0;
        int unmatchedCount = 0;
        for (StackNodeVo sn : codeNodes) {
            String originalClassName = sn.getClassName();
            String resolvedClassName = resolveCoverageOwnerClassName(originalClassName);
            ClassCoverageIndex classCov = coverageMap.get(resolvedClassName);
            
            // 如果直接匹配失败，尝试智能匹配（处理包名前缀缺失等情况）
            if (classCov == null) {
                classCov = findClassCoverageByFuzzyMatch(coverageMap, resolvedClassName);
            }
            
            if (classCov != null) {
                mergeStackNode(classCov, sn);
                matchedCount++;
            } else {
                unmatchedCount++;
                if (unmatchedCount <= 5) {
                    logger.warn("未匹配到类覆盖率数据：原始类名={}, 解析后={}, 可用类名示例={}", 
                            originalClassName, resolvedClassName, 
                            coverageMap.keySet().stream().limit(5).collect(Collectors.joining(", ")));
                }
            }
        }
        
        logger.info("覆盖率合并完成：traceId={}, 匹配成功={}, 未匹配={}", traceId, matchedCount, unmatchedCount);
        
        if (matchedCount == 0 && unmatchedCount > 0) {
            logger.error("严重警告：所有 codeNodes 都未能匹配到静态源码数据！请检查类名格式是否一致。");
            logger.error("codeNodes 中的类名示例：{}", 
                    codeNodes.stream().limit(3).map(StackNodeVo::getClassName).collect(Collectors.joining(", ")));
            logger.error("coverageMap 中的类名示例：{}", 
                    coverageMap.keySet().stream().limit(10).collect(Collectors.joining(", ")));
        }
    }

    /**
     * Helper class to track method coverage state for comparison
     */
    private static class MethodCoverageSnapshot {
        int coveredLines;
        int totalLines;
        int coveredBranchTargets;
        int totalBranchTargets;
        
        MethodCoverageSnapshot(MethodCoverageDetail method) {
            this.coveredLines = method.getCoveredLines();
            this.totalLines = method.getTotalLines();
            this.coveredBranchTargets = method.getCoveredBranchTargets();
            this.totalBranchTargets = method.getTotalBranchTargets();
        }
        
        boolean hasDifference(MethodCoverageDetail current) {
            return this.coveredLines != current.getCoveredLines()
                || this.coveredBranchTargets != current.getCoveredBranchTargets();
        }
    }
    
    /**
     * Capture initial coverage state for version full report difference detection
     */
    private void captureInitialCoverageState(Map<String, ClassCoverageIndex> coverageMap,
                                            Map<String, Map<String, MethodCoverageSnapshot>> initialState) {
        for (Map.Entry<String, ClassCoverageIndex> entry : coverageMap.entrySet()) {
            String className = entry.getKey();
            ClassCoverageIndex classCov = entry.getValue();
            
            Map<String, MethodCoverageSnapshot> methodSnapshots = new HashMap<>();
            if (classCov.getMethods() != null) {
                for (MethodCoverageDetail method : classCov.getMethods()) {
                    String methodKey = buildMethodKey(method.getMethodName(), method.getMethodDesc());
                    methodSnapshots.put(methodKey, new MethodCoverageSnapshot(method));
                }
            }
            initialState.put(className, methodSnapshots);
        }
    }
    
    /**
     * Mark classes and methods with coverage differences across snapshots
     */
    private void markCoverageChanges(Map<String, ClassCoverageIndex> coverageMap,
                                    Map<String, Map<String, MethodCoverageSnapshot>> initialState,
                                    Job<String> job) {
        int classesWithChanges = 0;
        int methodsWithChanges = 0;
        
        for (Map.Entry<String, ClassCoverageIndex> entry : coverageMap.entrySet()) {
            String className = entry.getKey();
            ClassCoverageIndex classCov = entry.getValue();
            Map<String, MethodCoverageSnapshot> initialMethods = initialState.get(className);
            
            if (initialMethods == null || classCov.getMethods() == null) {
                continue;
            }
            
            boolean classHasChanges = false;
            for (MethodCoverageDetail method : classCov.getMethods()) {
                String methodKey = buildMethodKey(method.getMethodName(), method.getMethodDesc());
                MethodCoverageSnapshot initialSnapshot = initialMethods.get(methodKey);
                
                if (initialSnapshot != null && initialSnapshot.hasDifference(method)) {
                    method.setHasCodeChanges(true);
                    classHasChanges = true;
                    methodsWithChanges++;
                }
            }
            
            if (classHasChanges) {
                classCov.setHasCodeChanges(true);
                classesWithChanges++;
            }
        }
        
        if (job != null && (classesWithChanges > 0 || methodsWithChanges > 0)) {
            job.getLogger().info(String.format(
                "检测到覆盖率差异：%d 个类、%d 个方法在不同快照间存在覆盖率变化",
                classesWithChanges, methodsWithChanges
            ));
        }
    }

    private Map<String, ClassCoverageIndex> deepCloneCoverageMap(Map<String, ClassCoverageIndex> source) {
        if (source == null) {
            return new HashMap<>();
        }
        Map<String, ClassCoverageIndex> clone = new HashMap<>();
        for (Map.Entry<String, ClassCoverageIndex> entry : source.entrySet()) {
            ClassCoverageIndex original = entry.getValue();
            ClassCoverageIndex copy = new ClassCoverageIndex();
            copy.setId(original.getId());
            copy.setReportId(original.getReportId());
            copy.setAppId(original.getAppId());
            copy.setClassName(original.getClassName());
            copy.setTotalMethods(original.getTotalMethods());
            copy.setCoveredMethods(original.getCoveredMethods());
            copy.setTotalLines(original.getTotalLines());
            copy.setCoveredLines(original.getCoveredLines());
            copy.setTotalBranches(original.getTotalBranches());
            copy.setCoveredBranches(original.getCoveredBranches());
            copy.setTotalBranchTargets(original.getTotalBranchTargets());
            copy.setCoveredBranchTargets(original.getCoveredBranchTargets());
            copy.setTotalComplexity(original.getTotalComplexity());
            copy.setLineRate(original.getLineRate());
            copy.setBranchRate(original.getBranchRate());
            copy.setMethodRate(original.getMethodRate());
            if (original.getMethods() != null) {
                List<MethodCoverageDetail> methodsCopy = new ArrayList<>();
                for (MethodCoverageDetail m : original.getMethods()) {
                    MethodCoverageDetail mc = new MethodCoverageDetail();
                    mc.setMethodName(m.getMethodName());
                    mc.setMethodDesc(m.getMethodDesc());
                    mc.setTotalLines(m.getTotalLines());
                    mc.setCoveredLines(m.getCoveredLines());
                    mc.setTotalBranches(m.getTotalBranches());
                    mc.setCoveredBranches(m.getCoveredBranches());
                    mc.setTotalBranchTargets(m.getTotalBranchTargets());
                    mc.setCoveredBranchTargets(m.getCoveredBranchTargets());
                    mc.setBranchRate(m.getBranchRate());
                    mc.setComplexity(m.getComplexity());
                    mc.setCovered(m.isCovered());
                    mc.setTotalLineNumbers(m.getTotalLineNumbers() != null ? new ArrayList<>(m.getTotalLineNumbers()) : null);
                    mc.setCoveredLineNumbers(m.getCoveredLineNumbers() != null ? new ArrayList<>(m.getCoveredLineNumbers()) : new ArrayList<>());
                    mc.setCoveredBranchLines(m.getCoveredBranchLines() != null ? new ArrayList<>(m.getCoveredBranchLines()) : new ArrayList<>());
                    mc.setTotalBranchTargetProbeMap(copyBranchTargetProbeMap(m.getTotalBranchTargetProbeMap()));
                    mc.setCoveredBranchTargetProbeMap(copyBranchTargetProbeMap(m.getCoveredBranchTargetProbeMap()));
                    methodsCopy.add(mc);
                }
                copy.setMethods(methodsCopy);
            } else {
                copy.setMethods(new ArrayList<>());
            }
            clone.put(entry.getKey(), copy);
        }
        return clone;
    }

    private void markCrossCommitCoverageChanges(Map<String, ClassCoverageIndex> finalCoverageMap,
                                                Map<String, Map<String, ClassCoverageIndex>> perCommitSnapshots,
                                                Job<String> job) {
        int classesWithChanges = 0;
        int methodsWithChanges = 0;

        for (Map.Entry<String, ClassCoverageIndex> entry : finalCoverageMap.entrySet()) {
            String className = entry.getKey();
            ClassCoverageIndex finalClassCov = entry.getValue();
            if (finalClassCov.getMethods() == null) {
                continue;
            }

            boolean classHasChanges = false;
            for (MethodCoverageDetail method : finalClassCov.getMethods()) {
                String key = buildMethodKey(method.getMethodName(), method.getMethodDesc());
                Set<String> signatures = new LinkedHashSet<>();
                for (Map<String, ClassCoverageIndex> snapshot : perCommitSnapshots.values()) {
                    ClassCoverageIndex snapshotClass = snapshot.get(className);
                    MethodCoverageDetail snapshotMethod = findMethodByKey(snapshotClass, key);
                    signatures.add(buildCoverageSignature(snapshotMethod));
                }
                if (signatures.size() > 1) {
                    method.setHasCodeChanges(true);
                    classHasChanges = true;
                    methodsWithChanges++;
                }
            }

            if (classHasChanges) {
                finalClassCov.setHasCodeChanges(true);
                classesWithChanges++;
            }
        }

        if (job != null && (classesWithChanges > 0 || methodsWithChanges > 0)) {
            job.getLogger().info(String.format(
                "跨 Commit 覆盖率差异标记完成：%d 个类、%d 个方法存在覆盖率变化",
                classesWithChanges, methodsWithChanges
            ));
        }
    }

    private MethodCoverageDetail findMethodByKey(ClassCoverageIndex classCoverage, String methodKey) {
        if (classCoverage == null || classCoverage.getMethods() == null) {
            return null;
        }
        for (MethodCoverageDetail method : classCoverage.getMethods()) {
            if (Objects.equals(methodKey, buildMethodKey(method.getMethodName(), method.getMethodDesc()))) {
                return method;
            }
        }
        return null;
    }

    private String buildCoverageSignature(MethodCoverageDetail method) {
        if (method == null) {
            return "missing";
        }
        return method.getTotalLines() + ":" + sortedIntegerList(method.getTotalLineNumbers())
                + "|" + method.getCoveredLines() + ":" + sortedIntegerList(method.getCoveredLineNumbers())
                + "|" + method.getTotalBranchTargets() + ":" + normalizedBranchTargetProbeMap(method.getTotalBranchTargetProbeMap())
                + "|" + method.getCoveredBranchTargets() + ":" + normalizedBranchTargetProbeMap(method.getCoveredBranchTargetProbeMap());
    }

    private String sortedIntegerList(List<Integer> values) {
        if (values == null || values.isEmpty()) {
            return "[]";
        }
        List<Integer> sorted = new ArrayList<>(new LinkedHashSet<>(values));
        Collections.sort(sorted);
        return sorted.toString();
    }

    private String normalizedBranchTargetProbeMap(Map<String, List<Integer>> probeMap) {
        if (probeMap == null || probeMap.isEmpty()) {
            return "{}";
        }
        List<String> parts = new ArrayList<>();
        for (Map.Entry<String, List<Integer>> entry : probeMap.entrySet()) {
            parts.add(entry.getKey() + "=" + sortedIntegerList(entry.getValue()));
        }
        Collections.sort(parts);
        return parts.toString();
    }

    private ClassCoverageIndex createInitialClassCoverage(String appId, String className) {
        ClassCoverageIndex classCov = new ClassCoverageIndex();
        classCov.setAppId(appId);
        classCov.setClassName(className);
        classCov.setMethods(new ArrayList<>());
        return classCov;
    }

    private void appendStaticClassCoverage(ClassCoverageIndex classCov, StaticSourceClassInfo classInfo,
                                           Map<String, List<Integer>> incrementalDiffMap) {
        List<Integer> changedLinesInClass = getChangedLinesForClass(incrementalDiffMap, classCov.getClassName());
        if (classInfo.getMethodMaps() != null) {
            for (StaticSourceMethodInfo mInfo : classInfo.getMethodMaps().values()) {
                MethodCoverageDetail md = new MethodCoverageDetail();
                md.setMethodName(mInfo.getMethodName());
                md.setMethodDesc(mInfo.getMethodDesc());

                List<Integer> methodLines = mInfo.getMethodLineNumberMap();
                Map<String, List<Integer>> totalBranchTargetProbeMap = changedLinesInClass != null
                        ? filterBranchTargetProbeMap(mInfo.getBranchLineAndTargetProbeMap(), changedLinesInClass)
                        : copyBranchTargetProbeMap(mInfo.getBranchLineAndTargetProbeMap());
                if (changedLinesInClass != null) {
                    // Filter lines based on diff
                    List<Integer> filteredLines = new ArrayList<>();
                    if (methodLines != null) {
                        for (Integer ln : methodLines) {
                            if (changedLinesInClass.contains(ln)) {
                                filteredLines.add(ln);
                            }
                        }
                    }
                    if (filteredLines.isEmpty()) {
                        continue; // For incremental report, only include methods with actual changes
                    }
                    md.setTotalLineNumbers(filteredLines);

                    List<Integer> branchLines = mInfo.getBranchLineNumberSet();
                    if (branchLines != null && !branchLines.isEmpty()) {
                        int filteredBranches = 0;
                        for (Integer bl : branchLines) {
                            if (changedLinesInClass.contains(bl)) {
                                filteredBranches++;
                            }
                        }
                        md.setTotalBranches(filteredBranches);
                    } else {
                        md.setTotalBranches(0);
                    }
                } else {
                    md.setTotalLineNumbers(methodLines);
                    md.setTotalBranches(mInfo.getTotalBranchCount() != null ? mInfo.getTotalBranchCount() : 0);
                }

                md.setTotalLines(md.getTotalLineNumbers() != null ? md.getTotalLineNumbers().size() : 0);
                md.setComplexity(mInfo.getCyclomaticComplexityMap() != null ? mInfo.getCyclomaticComplexityMap() : 0);
                md.setCoveredLineNumbers(new ArrayList<>());
                md.setCoveredBranchLines(new ArrayList<>());
                md.setTotalBranchTargetProbeMap(totalBranchTargetProbeMap);
                md.setCoveredBranchTargetProbeMap(new LinkedHashMap<>());
                md.setTotalBranchTargets(countBranchTargets(totalBranchTargetProbeMap));
                md.setCoveredBranchTargets(0);
                md.setBranchRate(calculateBranchRate(0, md.getTotalBranchTargets()));
                classCov.getMethods().add(md);
                classCov.setTotalLines(classCov.getTotalLines() + md.getTotalLines());
                classCov.setTotalBranches(classCov.getTotalBranches() + md.getTotalBranches());
                classCov.setTotalBranchTargets(classCov.getTotalBranchTargets() + md.getTotalBranchTargets());
                classCov.setTotalComplexity(classCov.getTotalComplexity() + md.getComplexity());
            }
        }
        classCov.setTotalMethods(classCov.getMethods().size());
    }

    private void saveAndCalculateSummary(CoverageReportIndex report, Map<String, ClassCoverageIndex> coverageMap, Map<String, List<Integer>> diffMap) {
        long totalClasses = coverageMap.size();
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

        // Incremental Stats
        long incTotalClasses = 0;
        long incCoveredClasses = 0;
        long incTotalLines = 0;
        long incCoveredLines = 0;
        long incTotalMethods = 0;
        long incCoveredMethods = 0;
        long incTotalBranches = 0;
        long incCoveredBranches = 0;
        long incTotalBranchTargets = 0;
        long incCoveredBranchTargets = 0;
        int incTotalComplexity = 0;

        List<ClassCoverageIndex> toSave = new ArrayList<>();
        for (ClassCoverageIndex classCov : coverageMap.values()) {
            classCov.setReportId(report.getId());
            classCov.setId(report.getId() + "_" + classCov.getClassName().hashCode());

            // 最终兜底：确保覆盖数不超过总数，防止出现负数或大于100%的情况
            if (classCov.getCoveredLines() > classCov.getTotalLines()) {
                classCov.setCoveredLines(classCov.getTotalLines());
            }
            if (classCov.getCoveredBranches() > classCov.getTotalBranches()) {
                classCov.setCoveredBranches(classCov.getTotalBranches());
            }
            if (classCov.getCoveredMethods() > classCov.getTotalMethods()) {
                classCov.setCoveredMethods(classCov.getTotalMethods());
            }

            // Compute rates for indexing/filtering
            classCov.setLineRate(classCov.getTotalLines() > 0 ? (double) classCov.getCoveredLines() / classCov.getTotalLines() * 100 : 0.0);
            classCov.setBranchRate(calculateBranchRate(classCov.getCoveredBranchTargets(), classCov.getTotalBranchTargets()));
            classCov.setMethodRate(classCov.getTotalMethods() > 0 ? (double) classCov.getCoveredMethods() / classCov.getTotalMethods() * 100 : 0.0);

            if (classCov.getCoveredLines() > 0) coveredClasses++;

            totalMethods += classCov.getTotalMethods();
            coveredMethods += classCov.getCoveredMethods();
            totalLines += classCov.getTotalLines();
            coveredLines += classCov.getCoveredLines();
            totalBranches += classCov.getTotalBranches();
            coveredBranches += classCov.getCoveredBranches();
            totalBranchTargets += classCov.getTotalBranchTargets();
            coveredBranchTargets += classCov.getCoveredBranchTargets();
            totalComplexity += classCov.getTotalComplexity();

            // Incremental Calculation (Summary for the report)
            // If it's an incremental report (isIncrementalReport(reportType)), coverageMap already contains only incremental data
            if (isIncrementalReport(report.getReportType())) {
                incTotalClasses++;
                if (classCov.getCoveredLines() > 0) incCoveredClasses++;

                incTotalLines += classCov.getTotalLines();
                incCoveredLines += classCov.getCoveredLines();
                incTotalMethods += classCov.getTotalMethods();
                incCoveredMethods += classCov.getCoveredMethods();
                incTotalBranches += classCov.getTotalBranches();
                incCoveredBranches += classCov.getCoveredBranches();
                incTotalBranchTargets += classCov.getTotalBranchTargets();
                incCoveredBranchTargets += classCov.getCoveredBranchTargets();
                incTotalComplexity += classCov.getTotalComplexity();
            } else if (diffMap != null && !diffMap.isEmpty()) {
                // For full report, calculate inc stats vs diffMap (comparison with previous)
                List<Integer> changedLines = getChangedLinesForClass(diffMap, classCov.getClassName());
                if (changedLines == null || changedLines.isEmpty()) {
                    toSave.add(classCov);
                    continue;
                }
                Set<Integer> changedLineSet = new HashSet<>(changedLines);

                boolean classHasChanges = !changedLineSet.isEmpty();
                if (classHasChanges) incTotalClasses++;

                Set<Integer> coveredLineSet = new HashSet<>();
                int methodsWithChanges = 0;
                int methodsWithChangesCovered = 0;
                boolean anyChangedLineCovered = false;
                int classIncTotalComplexity = 0;
                int classIncTotalBranches = 0;
                int classIncCoveredBranches = 0;
                int classIncTotalBranchTargets = 0;
                int classIncCoveredBranchTargets = 0;

                for (MethodCoverageDetail md : classCov.getMethods()) {
                    boolean methodHasChanges = false;
                    List<Integer> methodLines = md.getTotalLineNumbers();
                    if (methodLines != null) {
                        for (Integer ln : methodLines) {
                            if (changedLineSet.contains(ln)) {
                                methodHasChanges = true;
                                break;
                            }
                        }
                    }

                    if (methodHasChanges) {
                        methodsWithChanges++;
                        boolean changedLineCovered = false;
                        if (md.getCoveredLineNumbers() != null) {
                            for (Integer cln : md.getCoveredLineNumbers()) {
                                if (changedLineSet.contains(cln)) {
                                    changedLineCovered = true;
                                    break;
                                }
                            }
                        }
                        if (changedLineCovered) {
                            methodsWithChangesCovered++;
                            anyChangedLineCovered = true;
                        }

                        classIncTotalBranches += md.getTotalBranches();
                        classIncCoveredBranches += md.getCoveredBranches();
                        classIncTotalBranchTargets += md.getTotalBranchTargets();
                        classIncCoveredBranchTargets += md.getCoveredBranchTargets();
                        classIncTotalComplexity += md.getComplexity();
                    }

                    if (md.getCoveredLineNumbers() != null) {
                        coveredLineSet.addAll(md.getCoveredLineNumbers());
                    }
                }

                for (Integer line : changedLineSet) {
                    if (coveredLineSet.contains(line)) {
                        incCoveredLines++;
                    }
                }

                if (anyChangedLineCovered) incCoveredClasses++;

                incTotalLines += changedLineSet.size();
                incTotalMethods += methodsWithChanges;
                incCoveredMethods += methodsWithChangesCovered;
                incTotalBranches += classIncTotalBranches;
                incCoveredBranches += classIncCoveredBranches;
                incTotalBranchTargets += classIncTotalBranchTargets;
                incCoveredBranchTargets += classIncCoveredBranchTargets;
                incTotalComplexity += classIncTotalComplexity;
            }

            toSave.add(classCov);
            if (toSave.size() >= 100) {
                classCoverageRepository.saveAll(toSave);
                toSave.clear();
            }
        }
        if (!toSave.isEmpty()) {
            classCoverageRepository.saveAll(toSave);
        }

        // 最终兜底：确保 report 级别的覆盖数不超过总数
        if (coveredMethods > totalMethods) coveredMethods = totalMethods;
        if (coveredLines > totalLines) coveredLines = totalLines;
        if (coveredBranches > totalBranches) coveredBranches = totalBranches;
        if (coveredBranchTargets > totalBranchTargets) coveredBranchTargets = totalBranchTargets;
        if (coveredClasses > totalClasses) coveredClasses = totalClasses;

        report.setTotalClasses(totalClasses);
        report.setCoveredClasses(coveredClasses);
        report.setTotalMethods(totalMethods);
        report.setCoveredMethods(coveredMethods);
        report.setTotalLines(totalLines);
        report.setCoveredLines(coveredLines);
        report.setTotalBranches(totalBranches);
        report.setCoveredBranches(coveredBranches);
        report.setTotalBranchTargets(totalBranchTargets);
        report.setCoveredBranchTargets(coveredBranchTargets);
        report.setTotalComplexity(totalComplexity);

        // 同样对增量指标进行兜底
        if (incCoveredLines > incTotalLines) incCoveredLines = incTotalLines;
        if (incCoveredMethods > incTotalMethods) incCoveredMethods = incTotalMethods;
        if (incCoveredBranches > incTotalBranches) incCoveredBranches = incTotalBranches;
        if (incCoveredBranchTargets > incTotalBranchTargets) incCoveredBranchTargets = incTotalBranchTargets;
        if (incCoveredClasses > incTotalClasses) incCoveredClasses = incTotalClasses;

        report.setIncTotalClasses(incTotalClasses);
        report.setIncCoveredClasses(incCoveredClasses);
        report.setIncTotalLines(incTotalLines);
        report.setIncCoveredLines(incCoveredLines);
        report.setIncTotalMethods(incTotalMethods);
        report.setIncCoveredMethods(incCoveredMethods);
        report.setIncTotalBranches(incTotalBranches);
        report.setIncCoveredBranches(incCoveredBranches);
        report.setIncTotalBranchTargets(incTotalBranchTargets);
        report.setIncCoveredBranchTargets(incCoveredBranchTargets);
        report.setIncTotalComplexity(incTotalComplexity);

        coverageReportRepository.save(report);
    }

    @Override
    public CoverageReportIndex getLatestReport(String appId, String versionNumber) {
        return getLatestReport(appId, versionNumber, null);
    }

    @Override
    public CoverageReportIndex getLatestReport(String appId, String versionNumber, String commitId) {
        List<CoverageReportIndex> reports;
        if (StringUtils.hasText(commitId)) {
            reports = coverageReportRepository.findByAppIdAndVersionNumberAndRepoCommitId(appId, versionNumber, commitId);
        } else {
            reports = coverageReportRepository.findByAppIdAndVersionNumber(appId, versionNumber);
        }
        return getLatest(reports);
    }

    @Override
    public CoverageReportIndex getLatestReportByType(String appId, String versionNumber, Integer reportType) {
        return getLatestReportByType(appId, versionNumber, reportType, null);
    }

    @Override
    public CoverageReportIndex getLatestReportByType(String appId, String versionNumber, Integer reportType, String commitId) {
        List<CoverageReportIndex> reports;
        if (StringUtils.hasText(commitId)) {
            reports = coverageReportRepository.findByAppIdAndVersionNumberAndReportTypeAndRepoCommitId(appId, versionNumber, reportType, commitId);
        } else {
            reports = coverageReportRepository.findByAppIdAndVersionNumberAndReportType(appId, versionNumber, reportType);
        }
        return getLatest(reports);
    }

    private CoverageReportIndex getLatest(List<CoverageReportIndex> reports) {
        if (reports != null && !reports.isEmpty()) {
            List<CoverageReportIndex> modifiableReports = new ArrayList<>(reports);
            modifiableReports.sort((a, b) -> {
                if (a.getCreateTime() == null) return 1;
                if (b.getCreateTime() == null) return -1;
                return b.getCreateTime().compareTo(a.getCreateTime());
            });
            return modifiableReports.get(0);
        }
        return null;
    }

    @Override
    public void exportReport(String reportId, HttpServletResponse response) throws IOException {
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setCharacterEncoding("utf-8");
        String fileName = URLEncoder.encode("CoverageReport_" + reportId, StandardCharsets.UTF_8).replaceAll("\\+", "%20");
        response.setHeader("Content-disposition", "attachment;filename*=utf-8''" + fileName + ".xlsx");

        try (com.alibaba.excel.ExcelWriter excelWriter = EasyExcel.write(response.getOutputStream(), ClassCoverageIndex.class).build()) {
            com.alibaba.excel.write.metadata.WriteSheet writeSheet = EasyExcel.writerSheet("Class Coverage").build();

            // Export in chunks to prevent memory issues for very large reports
            int pageSize = 100;
            int pageNum = 0;
            Page<ClassCoverageIndex> page;
            do {
                page = classCoverageRepository.findByReportId(reportId, PageRequest.of(pageNum, pageSize));
                excelWriter.write(page.getContent(), writeSheet);
                pageNum++;
            } while (page.hasNext());
        }
    }

    @Override
    public void exportMethodReport(String reportId, HttpServletResponse response) throws IOException {
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setCharacterEncoding("utf-8");
        String fileName = URLEncoder.encode("MethodCoverageReport_" + reportId, StandardCharsets.UTF_8).replaceAll("\\+", "%20");
        response.setHeader("Content-disposition", "attachment;filename*=utf-8''" + fileName + ".xlsx");

        try (com.alibaba.excel.ExcelWriter excelWriter = EasyExcel.write(response.getOutputStream(), MethodCoverageExportVo.class).build()) {
            com.alibaba.excel.write.metadata.WriteSheet writeSheet = EasyExcel.writerSheet("Method Coverage").build();

            int pageSize = 100;
            int pageNum = 0;
            Page<ClassCoverageIndex> page;
            do {
                page = classCoverageRepository.findByReportId(reportId, PageRequest.of(pageNum, pageSize));
                List<MethodCoverageExportVo> writeData = new ArrayList<>();
                for (ClassCoverageIndex classIdx : page.getContent()) {
                    if (classIdx.getMethods() != null) {
                        for (ClassCoverageIndex.MethodCoverageDetail m : classIdx.getMethods()) {
                            MethodCoverageExportVo vo = new MethodCoverageExportVo();
                            vo.setClassName(classIdx.getClassName());
                            vo.setClassMethodCoverage(classIdx.getCoveredMethods() + " / " + classIdx.getTotalMethods());
                            vo.setClassMethodCoverageRate(classIdx.getTotalMethods() > 0 ? String.format("%.2f%%", (double) classIdx.getCoveredMethods() / classIdx.getTotalMethods() * 100) : "0.00%");
                            vo.setClassLineCoverage(classIdx.getCoveredLines() + " / " + classIdx.getTotalLines());
                            vo.setClassLineCoverageRate(classIdx.getTotalLines() > 0 ? String.format("%.2f%%", (double) classIdx.getCoveredLines() / classIdx.getTotalLines() * 100) : "0.00%");

                            vo.setMethodName(m.getMethodName());
                            vo.setMethodDesc(m.getMethodDesc());
                            vo.setLineCoverage(m.getCoveredLines() + " / " + m.getTotalLines());
                            vo.setLineCoverageRate(m.getTotalLines() > 0 ? String.format("%.2f%%", (double) m.getCoveredLines() / m.getTotalLines() * 100) : "0.00%");
                            vo.setBranchCoverage(m.getCoveredBranchTargets() + " / " + m.getTotalBranchTargets());
                            vo.setBranchCoverageRate(m.getTotalBranchTargets() > 0 ? String.format("%.2f%%", (double) m.getCoveredBranchTargets() / m.getTotalBranchTargets() * 100) : "0.00%");
                            vo.setComplexity(m.getComplexity());
                            vo.setIsCovered(m.isCovered() ? "是" : "否");
                            writeData.add(vo);
                        }
                    }
                }
                excelWriter.write(writeData, writeSheet);
                pageNum++;
            } while (page.hasNext());
        }
    }

    @Override
    public ClassCoverageIndex getClassCoverage(String reportId, String className) {
        if (!StringUtils.hasText(reportId) || !StringUtils.hasText(className)) {
            return null;
        }
        return classCoverageRepository.findById(reportId + "_" + className.hashCode()).orElse(null);
    }

    @Override
    public void ensureSourceClassesIndexed(String reportId) {
        if (!StringUtils.hasText(reportId) || sourceClassIndexEnsuredReports.contains(reportId)) {
            return;
        }
        CoverageReportIndex report = getReport(reportId);
        if (report == null || !StringUtils.hasText(report.getAppId())) {
            return;
        }
        synchronized (sourceClassIndexEnsuredReports) {
            if (sourceClassIndexEnsuredReports.contains(reportId)) {
                return;
            }
            try {
                fillMissingSourceClassCoverage(report);
            } catch (Exception e) {
                logger.warn("补齐覆盖率源码类失败, reportId={}", reportId, e);
            } finally {
                sourceClassIndexEnsuredReports.add(reportId);
            }
        }
    }

    @Override
    public CoverageComparisonVo getComparison(String reportId) {
        CoverageComparisonVo comparison = new CoverageComparisonVo();
        if (!StringUtils.hasText(reportId)) {
            return comparison;
        }
        CoverageReportIndex currentReport = coverageReportRepository.findById(reportId).orElse(null);
        if (currentReport == null) return comparison;

        CoverageReportIndex previousReport = selectPreviousReport(currentReport);
        if (previousReport == null) return comparison;

        List<ClassCoverageIndex> currentCcList = classCoverageRepository.findByReportId(currentReport.getId());
        List<ClassCoverageIndex> previousCcList = classCoverageRepository.findByReportId(previousReport.getId());

        Map<String, Boolean> prevMap = new HashMap<>(); // className#methodName#methodDesc -> isCovered
        for (ClassCoverageIndex cc : previousCcList) {
            if (cc.getMethods() != null) {
                for (MethodCoverageDetail m : cc.getMethods()) {
                    prevMap.put(buildMethodKey(cc.getClassName(), m), isMethodCoveredForComparison(m));
                }
            }
        }

        for (ClassCoverageIndex cc : currentCcList) {
            if (cc.getMethods() != null) {
                for (MethodCoverageDetail m : cc.getMethods()) {
                    String key = buildMethodKey(cc.getClassName(), m);
                    boolean curCovered = isMethodCoveredForComparison(m);
                    Boolean prevCovered = prevMap.get(key);

                    if (prevCovered == null) {
                        // New method or newly scanned? Treat as added if cur covered
                        if (curCovered) {
                            comparison.getAddedMethods().add(new CoverageComparisonVo.MethodDiff(cc.getClassName(), m.getMethodName(), m.getMethodDesc()));
                        }
                        continue;
                    }

                    if (curCovered) {
                        if (!prevCovered) {
                            comparison.getAddedMethods().add(new CoverageComparisonVo.MethodDiff(cc.getClassName(), m.getMethodName(), m.getMethodDesc()));
                        } else {
                            comparison.getStableMethods().add(new CoverageComparisonVo.MethodDiff(cc.getClassName(), m.getMethodName(), m.getMethodDesc()));
                        }
                    } else if (prevCovered) {
                        comparison.getDecreasedMethods().add(new CoverageComparisonVo.MethodDiff(cc.getClassName(), m.getMethodName(), m.getMethodDesc()));
                    }
                }
            }
        }

        comparison.setAddedCount(comparison.getAddedMethods().size());
        comparison.setStableCount(comparison.getStableMethods().size());
        comparison.setDecreasedCount(comparison.getDecreasedMethods().size());

        return comparison;
    }

    private void fillMissingSourceClassCoverage(CoverageReportIndex report) {
        File codeFile = resolveReportSourceFile(report);
        if (codeFile == null || !codeFile.exists()) {
            return;
        }

        Set<String> sourceClassNames = scanJavaSourceClassNames(codeFile);
        if (sourceClassNames.isEmpty()) {
            return;
        }

        Set<String> existingClassNames = classCoverageRepository.findByReportId(report.getId()).stream()
                .map(ClassCoverageIndex::getClassName)
                .filter(StringUtils::hasText)
                .collect(Collectors.toSet());
        Set<String> existingRootPackages = existingClassNames.stream()
                .map(this::firstPackageSegment)
                .filter(StringUtils::hasText)
                .collect(Collectors.toSet());

        List<ClassCoverageIndex> missingClasses = new ArrayList<>();
        for (String className : sourceClassNames) {
            String ownerClassName = resolveCoverageOwnerClassName(className);
            if (!existingRootPackages.isEmpty() && !existingRootPackages.contains(firstPackageSegment(ownerClassName))) {
                continue;
            }
            if (existingClassNames.contains(ownerClassName)) {
                continue;
            }
            ClassCoverageIndex classCov = createInitialClassCoverage(report.getAppId(), ownerClassName);
            classCov.setReportId(report.getId());
            classCov.setId(report.getId() + "_" + ownerClassName.hashCode());
            classCov.setLineRate(0.0);
            classCov.setBranchRate(0.0);
            classCov.setMethodRate(0.0);
            missingClasses.add(classCov);
            existingClassNames.add(ownerClassName);
        }

        if (!missingClasses.isEmpty()) {
            classCoverageRepository.saveAll(missingClasses);
            logger.info("已从源码包补齐覆盖率类, reportId={}, count={}", report.getId(), missingClasses.size());
        }
    }

    private File resolveReportSourceFile(CoverageReportIndex report) {
        VersionCenterIndex vIndex = findVersionIndexForReport(report);
        if (vIndex == null || vIndex.getVersionItem() == null || !StringUtils.hasText(vIndex.getVersionItem().getProgramFile())) {
            return null;
        }
        File codeFile = new File(vIndex.getVersionItem().getProgramFile());
        return codeFile.isAbsolute() ? codeFile : new File(resourceService.getCacheRoot(), vIndex.getVersionItem().getProgramFile());
    }

    private VersionCenterIndex findVersionIndexForReport(CoverageReportIndex report) {
        if (report == null) {
            return null;
        }
        List<VersionCenterIndex> vIndices = null;
        if (StringUtils.hasText(report.getVersionNumber()) && StringUtils.hasText(report.getRepoBranch()) && StringUtils.hasText(report.getRepoCommitId())) {
            vIndices = versionCenterRepository.findTop1ByVersionItem_AppIdAndVersionItem_VersionNumberAndVersionItem_RepoBranchAndVersionItem_RepoCommitId(
                    report.getAppId(), report.getVersionNumber(), report.getRepoBranch(), report.getRepoCommitId());
        }
        if ((vIndices == null || vIndices.isEmpty()) && StringUtils.hasText(report.getRepoCommitId()) && !"head".equalsIgnoreCase(report.getRepoCommitId())) {
            vIndices = versionCenterRepository.findTop1ByVersionItem_AppIdAndVersionItem_RepoCommitId(report.getAppId(), report.getRepoCommitId());
        }
        if ((vIndices == null || vIndices.isEmpty()) && StringUtils.hasText(report.getVersionNumber())) {
            vIndices = versionCenterRepository.findTop1ByVersionItem_AppIdAndVersionItem_VersionNumber(report.getAppId(), report.getVersionNumber());
        }
        if (vIndices == null || vIndices.isEmpty()) {
            vIndices = versionCenterRepository.findTop1ByVersionItem_AppIdOrderByCreateTimeDesc(report.getAppId());
        }
        return vIndices == null || vIndices.isEmpty() ? null : vIndices.get(0);
    }

    private Set<String> scanJavaSourceClassNames(File codeFile) {
        Set<String> classNames = new TreeSet<>();
        try {
            if (codeFile.isDirectory()) {
                try (Stream<java.nio.file.Path> stream = java.nio.file.Files.walk(codeFile.toPath())) {
                    stream.filter(path -> !java.nio.file.Files.isDirectory(path))
                            .map(path -> path.toString().replace('\\', '/'))
                            .filter(path -> path.endsWith(".java"))
                            .map(this::toClassNameFromSourcePath)
                            .filter(StringUtils::hasText)
                            .forEach(classNames::add);
                }
            } else if (isArchiveFile(codeFile)) {
                try (ZipFile zip = new ZipFile(codeFile)) {
                    Enumeration<? extends ZipEntry> entries = zip.entries();
                    while (entries.hasMoreElements()) {
                        ZipEntry entry = entries.nextElement();
                        if (!entry.isDirectory() && entry.getName().replace('\\', '/').endsWith(".java")) {
                            String className = toClassNameFromSourcePath(entry.getName());
                            if (StringUtils.hasText(className)) {
                                classNames.add(className);
                            }
                        }
                    }
                }
            }
        } catch (IOException e) {
            logger.warn("扫描源码包类失败, file={}", codeFile.getAbsolutePath(), e);
        }
        return classNames;
    }

    private boolean isArchiveFile(File file) {
        String lowerName = file.getName().toLowerCase(Locale.ROOT);
        return lowerName.endsWith(".zip") || lowerName.endsWith(".jar") || lowerName.endsWith(".war");
    }

    private String toClassNameFromSourcePath(String sourcePath) {
        if (!StringUtils.hasText(sourcePath)) {
            return null;
        }
        String normalized = sourcePath.replace('\\', '/');
        
        // 只处理主代码路径，排除测试代码
        int sourceRootIndex = normalized.lastIndexOf("/src/main/java/");
        int startIndex = sourceRootIndex >= 0 ? sourceRootIndex + "/src/main/java/".length() : -1;
        
        if (startIndex < 0) {
            if (normalized.startsWith("src/main/java/")) {
                startIndex = "src/main/java/".length();
            } else {
                // 不处理测试路径（src/test/java/）和其他路径
                return null;
            }
        }
        
        String relativePath = normalized.substring(startIndex);
        if (!relativePath.endsWith(".java") || relativePath.contains("/target/") || relativePath.contains("/build/")) {
            return null;
        }
        return relativePath.substring(0, relativePath.length() - ".java".length()).replace('/', '.');
    }

    private String firstPackageSegment(String className) {
        if (!StringUtils.hasText(className)) {
            return null;
        }
        int dotIndex = className.indexOf('.');
        return dotIndex > 0 ? className.substring(0, dotIndex) : className;
    }

    private CoverageReportIndex selectPreviousReport(CoverageReportIndex currentReport) {
        if (currentReport == null) {
            return null;
        }
        if (isIncrementalReport(currentReport.getReportType())) {
            CoverageReportIndex baseReport = selectExplicitBaseReport(currentReport);
            if (baseReport != null) {
                return baseReport;
            }
        }
        List<CoverageReportIndex> repoReports = coverageReportRepository.findByAppIdAndVersionNumber(currentReport.getAppId(), currentReport.getVersionNumber());
        return selectOlderSameTypeReport(currentReport, repoReports);
    }

    private CoverageReportIndex selectExplicitBaseReport(CoverageReportIndex currentReport) {
        if (!StringUtils.hasText(currentReport.getBaseVersionNumber())) {
            return null;
        }
        List<CoverageReportIndex> baseReports = coverageReportRepository.findByAppIdAndVersionNumber(currentReport.getAppId(), currentReport.getBaseVersionNumber());
        if (baseReports == null || baseReports.isEmpty()) {
            return null;
        }
        List<CoverageReportIndex> candidates = new ArrayList<>();
        for (CoverageReportIndex report : baseReports) {
            if (report == null || !StringUtils.hasText(report.getId()) || report.getId().equals(currentReport.getId())) {
                continue;
            }
            if (isIncrementalReport(report.getReportType())) {
                continue;
            }
            if (StringUtils.hasText(currentReport.getBaseRepoCommitId())
                    && !Objects.equals(normalizeText(currentReport.getBaseRepoCommitId()), normalizeText(report.getRepoCommitId()))) {
                continue;
            }
            candidates.add(report);
        }
        return latestReport(candidates);
    }

    private CoverageReportIndex selectOlderSameTypeReport(CoverageReportIndex currentReport, List<CoverageReportIndex> repoReports) {
        if (repoReports == null || repoReports.isEmpty()) {
            return null;
        }
        List<CoverageReportIndex> olderReports = new ArrayList<>();
        for (CoverageReportIndex report : repoReports) {
            if (report == null || !StringUtils.hasText(report.getId()) || report.getId().equals(currentReport.getId())) {
                continue;
            }
            if (normalizeReportType(report.getReportType()) != normalizeReportType(currentReport.getReportType())) {
                continue;
            }
            if (currentReport.getCreateTime() != null && report.getCreateTime() != null
                    && !report.getCreateTime().before(currentReport.getCreateTime())) {
                continue;
            }
            olderReports.add(report);
        }

        if (olderReports.isEmpty()) {
            return null;
        }

        olderReports.sort((a, b) -> {
            if (a.getCreateTime() == null) return 1;
            if (b.getCreateTime() == null) return -1;
            return b.getCreateTime().compareTo(a.getCreateTime());
        });

        if (StringUtils.hasText(currentReport.getRepoBranch())) {
            for (CoverageReportIndex report : olderReports) {
                if (currentReport.getRepoBranch().equals(report.getRepoBranch())) {
                    return report;
                }
            }
        }

        return olderReports.get(0);
    }

    private CoverageReportIndex latestReport(List<CoverageReportIndex> reports) {
        if (reports == null || reports.isEmpty()) {
            return null;
        }
        reports.sort((a, b) -> {
            if (a.getCreateTime() == null) return 1;
            if (b.getCreateTime() == null) return -1;
            return b.getCreateTime().compareTo(a.getCreateTime());
        });
        return reports.get(0);
    }

    private String normalizeText(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private boolean isIncrementalReport(Integer reportType) {
        return normalizeReportType(reportType) == REPORT_TYPE_INCREMENTAL;
    }

    private String reportTypeName(Integer reportType) {
        int normalizedType = normalizeReportType(reportType);
        if (normalizedType == REPORT_TYPE_INCREMENTAL) {
            return "增量";
        }
        if (normalizedType == REPORT_TYPE_CURRENT_COMMIT) {
            return "本次 Commit";
        }
        return "版本全量";
    }

    private int normalizeReportType(Integer reportType) {
        return reportType == null ? REPORT_TYPE_VERSION_FULL : reportType;
    }

    private String buildMethodKey(String className, MethodCoverageDetail method) {
        return className + "#" + buildMethodKey(method.getMethodName(), method.getMethodDesc());
    }

    private String buildMethodKey(String methodName, String methodDesc) {
        return CoverageMethodKeyUtil.buildMethodKey(methodName, methodDesc);
    }

    private boolean isMethodCoveredForComparison(MethodCoverageDetail method) {
        if (method == null) {
            return false;
        }
        if (method.getCoveredLines() > 0) {
            return true;
        }
        return method.getCoveredLineNumbers() != null && !method.getCoveredLineNumbers().isEmpty() || method.isCovered();
    }

    @Override
    public List<Map<String, Object>> getTrendData(String appId, String versionNumber) {
        List<CoverageReportIndex> repoReports = coverageReportRepository.findByAppIdAndVersionNumber(appId, versionNumber);
        if (repoReports == null || repoReports.isEmpty()) return Collections.emptyList();

        List<CoverageReportIndex> reports = new ArrayList<>(repoReports);
        reports.sort((a, b) -> {
            if (a.getCreateTime() == null) return 1;
            if (b.getCreateTime() == null) return -1;
            return b.getCreateTime().compareTo(a.getCreateTime());
        });

        List<Map<String, Object>> trend = new ArrayList<>();
        java.text.SimpleDateFormat sdfTrend = new java.text.SimpleDateFormat("MM-dd HH:mm");

        for (CoverageReportIndex report : reports) {
            Map<String, Object> point = new HashMap<>();
            point.put("time", report.getCreateTime() == null ? null : sdfTrend.format(report.getCreateTime()));
            point.put("timestamp", report.getCreateTime() == null ? null : report.getCreateTime().getTime());
            point.put("reportId", report.getId());
            point.put("versionNumber", report.getVersionNumber());
            point.put("repoBranch", report.getRepoBranch());
            point.put("repoCommitId", report.getRepoCommitId());
            point.put("reportType", normalizeReportType(report.getReportType()));
            point.put("baseVersionNumber", report.getBaseVersionNumber());
            point.put("baseRepoCommitId", report.getBaseRepoCommitId());
            point.put("lineCoverage", report.getTotalLines() > 0 ? (double) report.getCoveredLines() / report.getTotalLines() * 100 : 0);
            point.put("methodCoverage", report.getTotalMethods() > 0 ? (double) report.getCoveredMethods() / report.getTotalMethods() * 100 : 0);
            point.put("branchCoverage", calculateBranchRate(report.getCoveredBranchTargets(), report.getTotalBranchTargets()));
            trend.add(point);
        }
        return trend;
    }

    @Override
    public boolean hasNewerData(String appId, String lastProcessedTime) {
        if (lastProcessedTime == null) return true;
        try {
            String queryTime = lastProcessedTime;
            if (queryTime.length() == 19) {
                queryTime += ",000";
            }
            Date createTime = parse(queryTime);
            Page<TraceNodeIndex> page = traceNodeRepository.findByAppIdAndCreateTimeGreaterThanOrderByCreateTimeAsc(appId, createTime, PageRequest.of(0, 1));
            return page.getTotalElements() > 0;
        } catch (Exception e) {
            logger.warn("Parse lastProcessedTime failed: {}", lastProcessedTime, e);
            return true;
        }
    }

    @Override
    public boolean hasNewerData(String appId, String versionNumber, CoverageReportIndex report) {
        if (report == null) {
            AppVo app = appService.getApp(appId);
            SnapshotCoverageContext latestSnapshotContext = buildSnapshotCoverageContext(app, versionNumber);
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
        SnapshotCoverageContext latestSnapshotContext = buildSnapshotCoverageContext(app, versionNumber);

        if (!StringUtils.hasText(report.getSnapshotFingerprint())) {
            // 兼容旧报告：仍保留老逻辑兜底
            return hasNewerData(appId, report.getLastProcessedTime())
                    || !latestSnapshotContext.getSnapshotIds().isEmpty();
        }

        if (!Objects.equals(report.getSnapshotFingerprint(), latestSnapshotContext.getSnapshotFingerprint())) {
            return true;
        }

        return !Objects.equals(report.getSnapshotLastUpdateTime(), latestSnapshotContext.getLastSnapshotTime());
    }

    private SnapshotCoverageContext buildSnapshotCoverageContext(AppVo app, String versionNumber) {
        return buildSnapshotCoverageContext(app, versionNumber, null, null);
    }

    private SnapshotCoverageContext buildSnapshotCoverageContext(AppVo app, String versionNumber, 
                                                                 String commitId, Integer reportType) {
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
            List<SnapshotCommitMapping> mappings = snapshotCommitMappingRepository
                    .findByAppIdAndRepoCommitId(app.getId(), commitId);
            
            if (mappings != null && !mappings.isEmpty()) {
                Set<String> commitSnapshotIds = mappings.stream()
                        .map(SnapshotCommitMapping::getSnapshotId)
                        .collect(java.util.stream.Collectors.toSet());
                
                selectedSnapshots = selectedSnapshots.stream()
                        .filter(s -> commitSnapshotIds.contains(s.getId()))
                        .collect(java.util.stream.Collectors.toList());
                
                logger.info("本次 Commit 报告：通过关联表过滤快照，commitId={}, 匹配快照数={}", 
                        commitId, selectedSnapshots.size());
            } else {
                logger.warn("本次 Commit 报告：commitId={} 未找到关联快照，降级使用版本匹配", commitId);
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

    private List<String> parseSnapshotIds(String rawSnapshotIds) {
        if (!StringUtils.hasText(rawSnapshotIds)) {
            return Collections.emptyList();
        }

        List<String> ids = new ArrayList<>();
        for (String item : rawSnapshotIds.split(",")) {
            String id = trim(item);
            if (StringUtils.hasText(id)) {
                ids.add(id);
            }
        }
        return ids;
    }

    private boolean isAppendOnlySnapshotChange(List<String> oldIds, List<String> newIds) {
        if (oldIds == null || oldIds.isEmpty() || newIds == null || newIds.size() < oldIds.size()) {
            return false;
        }
        for (int i = 0; i < oldIds.size(); i++) {
            if (!Objects.equals(oldIds.get(i), newIds.get(i))) {
                return false;
            }
        }
        return true;
    }

    private String trim(String value) {
        return value == null ? null : value.trim();
    }

    private String sha256Hex(String text) {
        if (!StringUtils.hasText(text)) {
            return "";
        }
        try {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(text.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : bytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            logger.warn("Compute snapshot fingerprint failed", e);
            return String.valueOf(text.hashCode());
        }
    }

    private Map<String, List<Integer>> getCachedDiff(AppVo app, String oldCommit, String newCommit) {
        String cacheKey = app.getId() + ":" + oldCommit + ":" + newCommit;
        return diffCache.computeIfAbsent(cacheKey, k ->
                toDiffMap(gitService.getDiffDetail(app.getRepoAddress(), app.getRepoUserName(), app.getRepoPassword(), oldCommit, newCommit))
        );
    }

    private Map<String, List<Integer>> toDiffMap(List<GitDiffVo> diffs) {
        Map<String, List<Integer>> diffMap = new HashMap<>();
        if (diffs == null || diffs.isEmpty()) {
            return diffMap;
        }
        for (GitDiffVo diff : diffs) {
            if (diff == null || !StringUtils.hasText(diff.getClassName())) {
                continue;
            }
            String key = diff.getClassName();
            if ("DELETE".equals(diff.getChangeType())) {
                key += ":DELETED";
            }
            diffMap.put(key, diff.getChangedLines() == null ? Collections.emptyList() : diff.getChangedLines());
        }
        return diffMap;
    }

    private Map<String, List<Integer>> copyBranchTargetProbeMap(Map<String, List<Integer>> source) {
        if (source == null || source.isEmpty()) {
            return null;
        }
        Map<String, List<Integer>> copy = new LinkedHashMap<>();
        for (Map.Entry<String, List<Integer>> entry : source.entrySet()) {
            List<Integer> values = entry.getValue() == null ? Collections.emptyList() : new ArrayList<>(new LinkedHashSet<>(entry.getValue()));
            copy.put(entry.getKey(), values);
        }
        return copy;
    }

    private Map<String, List<Integer>> filterBranchTargetProbeMap(Map<String, List<Integer>> source, Collection<Integer> retainedLines) {
        if (source == null || source.isEmpty() || retainedLines == null || retainedLines.isEmpty()) {
            return null;
        }
        Set<Integer> lineSet = retainedLines instanceof Set ? (Set<Integer>) retainedLines : new HashSet<>(retainedLines);
        Map<String, List<Integer>> filtered = new LinkedHashMap<>();
        for (Map.Entry<String, List<Integer>> entry : source.entrySet()) {
            Integer branchLine = parsePositiveInt(entry.getKey());
            if (branchLine != null && lineSet.contains(branchLine)) {
                filtered.put(entry.getKey(), entry.getValue() == null ? Collections.emptyList() : new ArrayList<>(new LinkedHashSet<>(entry.getValue())));
            }
        }
        return filtered.isEmpty() ? null : filtered;
    }

    private Map<String, List<Integer>> mergeBranchTargetProbeMap(Map<String, List<Integer>> current,
                                                              Map<String, List<Integer>> incoming) {
        if ((current == null || current.isEmpty()) && (incoming == null || incoming.isEmpty())) {
            return new LinkedHashMap<>();
        }
        Map<String, LinkedHashSet<Integer>> merged = new LinkedHashMap<>();
        appendBranchTargetProbeMap(merged, current);
        appendBranchTargetProbeMap(merged, incoming);
        Map<String, List<Integer>> result = new LinkedHashMap<>();
        for (Map.Entry<String, LinkedHashSet<Integer>> entry : merged.entrySet()) {
            result.put(entry.getKey(), new ArrayList<>(entry.getValue()));
        }
        return result;
    }

    private Map<String, List<Integer>> normalizeMethodBranchTargetProbeMap(Map<String, List<Integer>> total,
                                                                            Map<String, List<Integer>> covered) {
        if (total == null || total.isEmpty()) {
            return new LinkedHashMap<>();
        }
        if (covered == null || covered.isEmpty()) {
            return copyBranchTargetProbeMap(total);
        }
        Map<String, List<Integer>> normalized = new LinkedHashMap<>();
        for (Map.Entry<String, List<Integer>> entry : total.entrySet()) {
            String branchLine = entry.getKey();
            List<Integer> totalValues = entry.getValue();
            if (totalValues == null || totalValues.isEmpty()) {
                continue;
            }
            LinkedHashSet<Integer> totalSet = new LinkedHashSet<>(totalValues);
            List<Integer> coveredValues = covered.get(branchLine);
            if (coveredValues == null || coveredValues.isEmpty()) {
                normalized.put(branchLine, new ArrayList<>(totalSet));
                continue;
            }
            LinkedHashSet<Integer> coveredSet = new LinkedHashSet<>(coveredValues);
            if (totalSet.containsAll(coveredSet)) {
                normalized.put(branchLine, new ArrayList<>(coveredSet));
            } else {
                normalized.put(branchLine, new ArrayList<>(totalSet));
            }
        }
        return normalized.isEmpty() ? copyBranchTargetProbeMap(total) : normalized;
    }

    private Map<String, List<Integer>> normalizeCoveredBranchTargetProbeMap(Map<String, List<Integer>> total,
                                                                         Map<String, List<Integer>> covered) {
        if (total == null || total.isEmpty() || covered == null || covered.isEmpty()) {
            return new LinkedHashMap<>();
        }
        Map<String, List<Integer>> normalized = new LinkedHashMap<>();
        for (Map.Entry<String, List<Integer>> entry : total.entrySet()) {
            List<Integer> totalValues = entry.getValue();
            if (totalValues == null || totalValues.isEmpty()) {
                continue;
            }
            Set<Integer> allowed = new LinkedHashSet<>(totalValues);
            List<Integer> coveredValues = covered.get(entry.getKey());
            if (coveredValues == null || coveredValues.isEmpty()) {
                continue;
            }
            LinkedHashSet<Integer> matched = new LinkedHashSet<>();
            for (Integer value : coveredValues) {
                if (value != null && allowed.contains(value)) {
                    matched.add(value);
                }
            }
            if (!matched.isEmpty()) {
                normalized.put(entry.getKey(), new ArrayList<>(matched));
            }
        }
        return normalized;
    }

    private void appendBranchTargetProbeMap(Map<String, LinkedHashSet<Integer>> target, Map<String, List<Integer>> source) {
        if (source == null || source.isEmpty()) {
            return;
        }
        for (Map.Entry<String, List<Integer>> entry : source.entrySet()) {
            LinkedHashSet<Integer> values = target.computeIfAbsent(entry.getKey(), key -> new LinkedHashSet<>());
            if (entry.getValue() != null) {
                values.addAll(entry.getValue());
            }
        }
    }

    private int countBranchTargets(Map<String, List<Integer>> branchTargetProbeMap) {
        if (branchTargetProbeMap == null || branchTargetProbeMap.isEmpty()) {
            return 0;
        }
        int total = 0;
        for (List<Integer> values : branchTargetProbeMap.values()) {
            total += values == null ? 0 : new LinkedHashSet<>(values).size();
        }
        return total;
    }

    private double calculateBranchRate(long coveredBranchTargets, long totalBranchTargets) {
        return totalBranchTargets > 0 ? (double) coveredBranchTargets / totalBranchTargets * 100 : 0.0;
    }

    private void reuseMatchedCoverageData(ClassCoverageIndex currentCc, ClassCoverageIndex lastCc, Map<String, List<Integer>> diffMap) {
        List<Integer> changedLines = getChangedLinesForClass(diffMap, currentCc.getClassName());

        for (MethodCoverageDetail currentMd : currentCc.getMethods()) {
            lastCc.getMethods().stream()
                    .filter(m -> m.getMethodName().equals(currentMd.getMethodName()) && m.getMethodDesc().equals(currentMd.getMethodDesc()))
                    .findFirst()
                    .ifPresent(lastMd -> {
                        boolean methodChanged = false;
                        if (changedLines != null && currentMd.getTotalLineNumbers() != null) {
                            for (Integer ln : currentMd.getTotalLineNumbers()) {
                                if (changedLines.contains(ln)) {
                                    methodChanged = true;
                                    break;
                                }
                            }
                        }

                        if (!methodChanged) {
                            currentMd.setCoveredLineNumbers(new ArrayList<>(lastMd.getCoveredLineNumbers() != null ? lastMd.getCoveredLineNumbers() : Collections.emptyList()));
                            currentMd.setCoveredLines(lastMd.getCoveredLines());
                            currentMd.setCoveredBranchLines(new ArrayList<>(lastMd.getCoveredBranchLines() != null ? lastMd.getCoveredBranchLines() : Collections.emptyList()));
                            currentMd.setCoveredBranches(lastMd.getCoveredBranches());
                            currentMd.setCoveredBranchTargetProbeMap(copyBranchTargetProbeMap(lastMd.getCoveredBranchTargetProbeMap()));
                            currentMd.setCoveredBranchTargets(lastMd.getCoveredBranchTargets());
                            currentMd.setBranchRate(lastMd.getBranchRate());
                            currentMd.setCovered(lastMd.isCovered());
                        }
                    });
        }
        recalculateClassStats(currentCc);
    }

    private void recalculateClassStats(ClassCoverageIndex cc) {
        int coveredMethods = (int) cc.getMethods().stream().filter(MethodCoverageDetail::isCovered).count();
        int coveredLines = cc.getMethods().stream().mapToInt(MethodCoverageDetail::getCoveredLines).sum();
        int coveredBranches = cc.getMethods().stream().mapToInt(MethodCoverageDetail::getCoveredBranches).sum();
        int coveredBranchTargets = cc.getMethods().stream().mapToInt(MethodCoverageDetail::getCoveredBranchTargets).sum();
        int totalBranchTargets = cc.getMethods().stream().mapToInt(MethodCoverageDetail::getTotalBranchTargets).sum();

        cc.setCoveredMethods(coveredMethods);
        cc.setCoveredLines(coveredLines);
        cc.setCoveredBranches(coveredBranches);
        cc.setCoveredBranchTargets(coveredBranchTargets);
        cc.setTotalBranchTargets(totalBranchTargets);
        cc.setBranchRate(calculateBranchRate(coveredBranchTargets, totalBranchTargets));
    }

    private void mergeStackNode(ClassCoverageIndex classCov, StackNodeVo sn) {
        Optional<MethodCoverageDetail> methodOpt = findBestMethodCoverage(classCov, sn);

        if (methodOpt.isPresent()) {
            MethodCoverageDetail md = methodOpt.get();

            Set<Integer> staticLineNumbers = new HashSet<>(md.getTotalLineNumbers() != null ? md.getTotalLineNumbers() : Collections.emptyList());
            Set<Integer> coveredLines = new HashSet<>(md.getCoveredLineNumbers() != null ? md.getCoveredLineNumbers() : Collections.emptyList());
            if (sn.getDoLines() != null) {
                for (Integer line : sn.getDoLines()) {
                    if (staticLineNumbers.contains(line)) {
                        coveredLines.add(line);
                    }
                }
            }
            md.setCoveredLineNumbers(new ArrayList<>(coveredLines));
            md.setCoveredLines(md.getCoveredLineNumbers().size());

            Set<Integer> coveredBranchLines = new HashSet<>(md.getCoveredBranchLines() != null ? md.getCoveredBranchLines() : Collections.emptyList());
            if (sn.getExecuteBranch() != null) {
                coveredBranchLines.addAll(sn.getExecuteBranch());
            }
            if (coveredBranchLines.size() > md.getTotalBranches()) {
                md.setCoveredBranches(md.getTotalBranches());
                List<Integer> list = new ArrayList<>(coveredBranchLines);
                md.setCoveredBranchLines(list.subList(0, md.getTotalBranches()));
            } else {
                md.setCoveredBranchLines(new ArrayList<>(coveredBranchLines));
                md.setCoveredBranches(md.getCoveredBranchLines().size());
            }
            Map<String, List<Integer>> coveredBranchTargetProbeMap = mergeBranchTargetProbeMap(
                    md.getCoveredBranchTargetProbeMap(), sn.getExecuteBranchTargetProbeMap());
            Map<String, List<Integer>> normalizedTotalBranchTargetProbeMap = normalizeMethodBranchTargetProbeMap(
                    md.getTotalBranchTargetProbeMap(), coveredBranchTargetProbeMap);
            coveredBranchTargetProbeMap = normalizeCoveredBranchTargetProbeMap(
                    normalizedTotalBranchTargetProbeMap, coveredBranchTargetProbeMap);
            md.setTotalBranchTargetProbeMap(normalizedTotalBranchTargetProbeMap);
            md.setTotalBranchTargets(countBranchTargets(normalizedTotalBranchTargetProbeMap));
            md.setCoveredBranchTargetProbeMap(coveredBranchTargetProbeMap);
            md.setCoveredBranchTargets(countBranchTargets(coveredBranchTargetProbeMap));
            md.setBranchRate(calculateBranchRate(md.getCoveredBranchTargets(), md.getTotalBranchTargets()));

            md.setCovered(md.getCoveredLines() > 0);
        }

        int classCoveredMethods = (int) classCov.getMethods().stream().filter(MethodCoverageDetail::isCovered).count();
        int classCoveredLines = classCov.getMethods().stream().mapToInt(MethodCoverageDetail::getCoveredLines).sum();
        int classCoveredBranches = classCov.getMethods().stream().mapToInt(MethodCoverageDetail::getCoveredBranches).sum();
        int classCoveredBranchTargets = classCov.getMethods().stream().mapToInt(MethodCoverageDetail::getCoveredBranchTargets).sum();

        classCov.setCoveredMethods(classCoveredMethods);
        classCov.setCoveredLines(classCoveredLines);
        classCov.setCoveredBranches(classCoveredBranches);
        classCov.setCoveredBranchTargets(classCoveredBranchTargets);
        classCov.setBranchRate(calculateBranchRate(classCoveredBranchTargets, classCov.getTotalBranchTargets()));
    }

    private Optional<MethodCoverageDetail> findBestMethodCoverage(ClassCoverageIndex classCov, StackNodeVo sn) {
        List<MethodCoverageDetail> candidates = classCov.getMethods().stream()
                .filter(m -> m.getMethodName().equals(sn.getMethodName()) && m.getMethodDesc().equals(sn.getMethodDescriptor()))
                .collect(java.util.stream.Collectors.toList());
        if (candidates.isEmpty()) {
            return Optional.empty();
        }
        if (candidates.size() == 1) {
            return Optional.of(candidates.get(0));
        }

        Set<Integer> executedLines = new HashSet<>(sn.getDoLines() != null ? sn.getDoLines() : Collections.emptyList());
        Set<Integer> executedBranches = new HashSet<>(sn.getExecuteBranch() != null ? sn.getExecuteBranch() : Collections.emptyList());

        MethodCoverageDetail best = null;
        int bestScore = Integer.MIN_VALUE;
        for (MethodCoverageDetail candidate : candidates) {
            int score = 0;
            if (candidate.getTotalLineNumbers() != null) {
                for (Integer line : candidate.getTotalLineNumbers()) {
                    if (executedLines.contains(line)) {
                        score += 2;
                    }
                    if (executedBranches.contains(line)) {
                        score += 1;
                    }
                }
            }
            if (best == null || score > bestScore) {
                best = candidate;
                bestScore = score;
            }
        }
        return Optional.ofNullable(best);
    }

    @Override
    public String getColoredSource(String appId, String reportId, String className) {
        ClassCoverageIndex classCov = getClassCoverage(reportId, className);
        if (classCov == null) return "Coverage data not found for class: " + className;

        return getColoredSource(appId, classCov);
    }

    @Override
    public String getColoredSource(String appId, ClassCoverageIndex classCov) {
        String className = classCov.getClassName();
        List<String> sourcePathCandidates = CoverageSourceClassUtil.buildSourcePathCandidates(className);
        VersionCenterIndex vIndex = null;

        String reportId = classCov.getReportId();
        if (StringUtils.hasText(reportId)) {
            CoverageReportIndex report = coverageReportRepository.findById(reportId).orElse(null);
            if (report != null) {
                List<VersionCenterIndex> vIndices = versionCenterRepository.findTop1ByVersionItem_AppIdAndVersionItem_VersionNumberAndVersionItem_RepoBranchAndVersionItem_RepoCommitId(
                        appId, report.getVersionNumber(), report.getRepoBranch(), report.getRepoCommitId());
                vIndex = (vIndices != null && !vIndices.isEmpty()) ? vIndices.get(0) : null;

                if (vIndex == null && StringUtils.hasText(report.getRepoCommitId()) && !"head".equalsIgnoreCase(report.getRepoCommitId())) {
                    vIndices = versionCenterRepository.findTop1ByVersionItem_AppIdAndVersionItem_RepoCommitId(appId, report.getRepoCommitId());
                    vIndex = (vIndices != null && !vIndices.isEmpty()) ? vIndices.get(0) : null;
                }

                if (vIndex == null && StringUtils.hasText(report.getRepoBranch())) {
                    vIndices = versionCenterRepository.findByVersionItem_AppIdAndVersionItem_RepoBranch(appId, report.getRepoBranch());
                    if (vIndices != null && !vIndices.isEmpty()) {
                        vIndex = vIndices.stream()
                                .filter(v -> report.getVersionNumber().equals(v.getVersionItem().getVersionNumber()))
                                .findFirst().orElse(null);
                    }
                }

                if (vIndex == null && StringUtils.hasText(report.getVersionNumber())) {
                    vIndices = versionCenterRepository.findTop1ByVersionItem_AppIdAndVersionItem_VersionNumber(appId, report.getVersionNumber());
                    vIndex = (vIndices != null && !vIndices.isEmpty()) ? vIndices.get(0) : null;
                }
            }
        }

        if (vIndex == null) {
            List<VersionCenterIndex> vIndices = versionCenterRepository.findTop1ByVersionItem_AppIdOrderByCreateTimeDesc(appId);
            vIndex = (vIndices != null && !vIndices.isEmpty()) ? vIndices.get(0) : null;
        }

        if (vIndex == null || vIndex.getVersionItem() == null) {
            logger.warn("Source code not found in Version Center for appId: {}", appId);
            return "Source code not found in Version Center for this app.";
        }

        VersionItem item = vIndex.getVersionItem();
        String programFile = item.getProgramFile();
        if (!StringUtils.hasText(programFile)) {
            return "No program file recorded for this version record.";
        }

        File codeFile = new File(programFile);
        if (!codeFile.isAbsolute()) {
            codeFile = new File(resourceService.getCacheRoot(), programFile);
        }

        if (!codeFile.exists()) {
            return "Code file not found at " + codeFile.getAbsolutePath();
        }

        String content = null;

        try {
            if (codeFile.getName().toLowerCase().endsWith(".zip") ||
                    codeFile.getName().toLowerCase().endsWith(".jar") ||
                    codeFile.getName().toLowerCase().endsWith(".war")) {

                try (ZipFile zip = new ZipFile(codeFile)) {
                    String[] prefixes = {"", "src/main/java/", "src/test/java/", "src/"};
                    for (String sourcePath : sourcePathCandidates) {
                        for (String prefix : prefixes) {
                            ZipEntry entry = zip.getEntry(prefix + sourcePath);
                            if (entry != null) {
                                try (InputStream is = zip.getInputStream(entry)) {
                                    content = new String(readAllBytes(is), java.nio.charset.StandardCharsets.UTF_8);
                                }
                                break;
                            }
                        }
                        if (content != null) {
                            break;
                        }
                    }

                    if (content == null) {
                        String cacheKey = codeFile.getAbsolutePath();
                        List<String> entries = zipEntryCache.computeIfAbsent(cacheKey, k -> {
                            List<String> list = new ArrayList<>();
                            Enumeration<? extends ZipEntry> en = zip.entries();
                            while (en.hasMoreElements()) {
                                list.add(en.nextElement().getName());
                            }
                            return list;
                        });

                        String bestMatch = null;
                        for (String entryName : entries) {
                            String normalizedName = entryName.replace('\\', '/');
                            for (String sourcePath : sourcePathCandidates) {
                                if (normalizedName.endsWith("/" + sourcePath) || normalizedName.equals(sourcePath)) {
                                    if (bestMatch == null) {
                                        bestMatch = entryName;
                                    } else if (normalizedName.contains("/src/main/java/") && !bestMatch.contains("/src/main/java/")) {
                                        bestMatch = entryName;
                                    } else if (normalizedName.contains("/src/test/java/") && !bestMatch.contains("/src/main/java/") && !bestMatch.contains("/src/test/java/")) {
                                        bestMatch = entryName;
                                    }
                                    break;
                                }
                            }
                        }

                        if (bestMatch != null) {
                            ZipEntry entry = zip.getEntry(bestMatch);
                            if (entry != null) {
                                try (InputStream is = zip.getInputStream(entry)) {
                                    content = new String(readAllBytes(is), java.nio.charset.StandardCharsets.UTF_8);
                                }
                            }
                        }
                    }
                }
            } else if (codeFile.isDirectory()) {
                String[] prefixes = {"", "src/main/java/", "src/test/java/", "src/"};
                for (String sourcePath : sourcePathCandidates) {
                    for (String prefix : prefixes) {
                        File f = new File(codeFile, prefix + sourcePath);
                        if (f.exists()) {
                            content = new String(java.nio.file.Files.readAllBytes(f.toPath()), java.nio.charset.StandardCharsets.UTF_8);
                            break;
                        }
                    }
                    if (content != null) {
                        break;
                    }
                }

                if (content == null) {
                    try (Stream<java.nio.file.Path> stream = java.nio.file.Files.walk(codeFile.toPath())) {
                        java.nio.file.Path foundPath = stream
                                .filter(p -> !java.nio.file.Files.isDirectory(p))
                                .filter(p -> {
                                    String pathStr = p.toString().replace('\\', '/');
                                    for (String sourcePath : sourcePathCandidates) {
                                        if (pathStr.endsWith("/" + sourcePath) || pathStr.equals(sourcePath)) {
                                            return true;
                                        }
                                    }
                                    return false;
                                })
                                .sorted((p1, p2) -> {
                                    String s1 = p1.toString().replace('\\', '/');
                                    String s2 = p2.toString().replace('\\', '/');
                                    int score1 = s1.contains("/src/main/java/") ? 2 : (s1.contains("/src/test/java/") ? 1 : 0);
                                    int score2 = s2.contains("/src/main/java/") ? 2 : (s2.contains("/src/test/java/") ? 1 : 0);
                                    return Integer.compare(score2, score1);
                                })
                                .findFirst()
                                .orElse(null);

                        if (foundPath != null) {
                            content = new String(java.nio.file.Files.readAllBytes(foundPath), java.nio.charset.StandardCharsets.UTF_8);
                        }
                    }
                }
            }
        } catch (IOException e) {
            logger.error("Error reading source code for {}", className, e);
            return "Error reading source code: " + e.getMessage();
        }

        if (content == null) {
            return "Source code for " + className + " not found in " + codeFile.getName();
        }

        return applyColoring(content, classCov, false);
    }

    private String applyColoring(String content, ClassCoverageIndex classCov, boolean showBranchDetails) {
        String[] lines = content.split("\\r?\\n");
        StringBuilder sb = new StringBuilder();

        Map<Integer, String> lineColors = new HashMap<>();
        Map<Integer, String> branchLineColors = new HashMap<>();
        Map<Integer, String> branchLineDetails = new HashMap<>();
        Map<String, Integer> methodStartLines = new HashMap<>();

        if (classCov.getMethods() != null) {
            for (int i = 0; i < classCov.getMethods().size(); i++) {
                MethodCoverageDetail md = classCov.getMethods().get(i);
                List<Integer> totalLines = md.getTotalLineNumbers();
                List<Integer> coveredLines = md.getCoveredLineNumbers();

                if (totalLines != null && !totalLines.isEmpty()) {
                    List<Integer> sortedLines = new ArrayList<>(totalLines);
                    Collections.sort(sortedLines);
                    methodStartLines.put("method_" + i, sortedLines.get(0));

                    for (Integer lineNum : totalLines) {
                        if (coveredLines != null && coveredLines.contains(lineNum)) {
                            lineColors.put(lineNum, "green");
                        } else if (!"green".equals(lineColors.get(lineNum))) {
                            lineColors.put(lineNum, "red");
                        }
                    }
                }
                mergeBranchLineColors(branchLineColors,
                        branchLineDetails,
                        md.getTotalBranchTargetProbeMap(),
                        md.getCoveredBranchTargetProbeMap());
            }
        }

        int totalLineCount = lines.length;
        int lineWidth = String.valueOf(totalLineCount).length();

        sb.append("<pre style='font-family: monospace; white-space: pre; display:inline-block; min-width:100%; box-sizing:border-box;'>");
        for (int i = 0; i < lines.length; i++) {
            int lineNum = i + 1;

            for (Map.Entry<String, Integer> entry : methodStartLines.entrySet()) {
                if (entry.getValue() == lineNum) {
                    sb.append("<a name='").append(entry.getKey()).append("'></a>");
                }
            }

            String color = branchLineColors.containsKey(lineNum) ? branchLineColors.get(lineNum) : lineColors.get(lineNum);
            String style = "position:relative;";
            if ("green".equals(color)) {
                style += "background-color: #ace1af;";
            } else if ("orange".equals(color)) {
                style += "background-color: #ffe5b4;";
            } else if ("red".equals(color)) {
                style += "background-color: #f5c6cb;";
            }
            String branchClassAttr = "";
            if (branchLineDetails.containsKey(lineNum)) {
                branchClassAttr = " class='branch-line branch-" + (color == null ? "green" : color) + "'";
                style += "--line-number-width: " + lineWidth + ".2em;";
            }
            sb.append("<div style='display:flex;min-width:max-content;").append(style).append("'");
            if (StringUtils.hasText(branchClassAttr)) {
                sb.append(branchClassAttr);
            }
            sb.append(">");
            sb.append("<span style='color: #999; flex-shrink:0; width: ").append(lineWidth).append(".2em; text-align: right; display: inline-block; user-select:none; margin-right: 20px;'>").append(lineNum).append("</span>")
                    .append(escapeHtml(lines[i]));
            if (branchLineDetails.containsKey(lineNum)) {
                sb.append("<span class='branch-flag' aria-hidden='true'></span>");
                sb.append("<span class='branch-tooltip'>")
                        .append(escapeHtml(branchLineDetails.get(lineNum)))
                        .append("</span>");
            }
            if (showBranchDetails && branchLineDetails.containsKey(lineNum)) {
                sb.append("<span style='margin-left: 16px; color: #666; font-size: 12px; white-space: nowrap;'>// ")
                        .append(escapeHtml(branchLineDetails.get(lineNum)))
                        .append("</span>");
            }
            sb.append("</div>");
        }
        sb.append("</pre>");

        return sb.toString();
    }

    private void mergeBranchLineColors(Map<Integer, String> branchLineColors,
                                       Map<Integer, String> branchLineDetails,
                                       Map<String, List<Integer>> totalBranchTargetProbeMap,
                                       Map<String, List<Integer>> coveredBranchTargetProbeMap) {
        if (totalBranchTargetProbeMap == null || totalBranchTargetProbeMap.isEmpty()) {
            return;
        }
        for (Map.Entry<String, List<Integer>> entry : totalBranchTargetProbeMap.entrySet()) {
            Integer branchLine = parsePositiveInt(entry.getKey());
            if (branchLine == null) {
                continue;
            }
            LinkedHashSet<Integer> totalSet = entry.getValue() == null ? new LinkedHashSet<>() : new LinkedHashSet<>(entry.getValue());
            LinkedHashSet<Integer> coveredSet = new LinkedHashSet<>();
            if (coveredBranchTargetProbeMap != null) {
                List<Integer> covered = coveredBranchTargetProbeMap.get(entry.getKey());
                if (covered != null) {
                    coveredSet.addAll(covered);
                }
            }
            int totalCount = totalSet.size();
            int coveredCount = coveredSet.size();
            String color = coveredCount <= 0 ? "red" : (coveredCount >= totalCount ? "green" : "orange");
            String currentColor = branchLineColors.get(branchLine);
            if ("green".equals(color)) {
                branchLineColors.put(branchLine, currentColor == null ? "green" : currentColor);
            } else {
                branchLineColors.put(branchLine, pickCoverageColor(currentColor, color));
            }
            branchLineDetails.put(branchLine, mergeBranchDetailText(
                    branchLineDetails.get(branchLine),
                    buildBranchDetailText(totalSet, coveredSet)));
        }
    }

    private String mergeBranchDetailText(String currentDetail, String newDetail) {
        if (!StringUtils.hasText(currentDetail)) {
            return newDetail;
        }
        if (!StringUtils.hasText(newDetail) || currentDetail.equals(newDetail)) {
            return currentDetail;
        }
        return currentDetail + " | " + newDetail;
    }

    private String buildBranchDetailText(Set<Integer> totalSet, Set<Integer> coveredSet) {
        String totalText = totalSet == null || totalSet.isEmpty()
                ? "[]"
                : totalSet.stream().sorted().map(String::valueOf).collect(Collectors.joining(", ", "[", "]"));
        String coveredText = coveredSet == null || coveredSet.isEmpty()
                ? "[]"
                : coveredSet.stream().sorted().map(String::valueOf).collect(Collectors.joining(", ", "[", "]"));
        String status;
        String statusIcon;
        if (coveredSet == null || coveredSet.isEmpty()) {
            status = "未覆盖";
            statusIcon = "🔴";
        } else if (totalSet != null && coveredSet.size() >= totalSet.size()) {
            status = "全覆盖";
            statusIcon = "🟢";
        } else {
            status = "部分覆盖";
            statusIcon = "🟠";
        }
        return statusIcon + " 分支状态：" + status + "\n已处理分支： " + coveredText + "\n总分支： " + totalText;
    }

    private String pickCoverageColor(String currentColor, String newColor) {
        if (currentColor == null) {
            return newColor;
        }
        if ("red".equals(currentColor) || "red".equals(newColor)) {
            return "red";
        }
        if ("orange".equals(currentColor) || "orange".equals(newColor)) {
            return "orange";
        }
        return "green";
    }

    private Integer parsePositiveInt(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            int parsed = Integer.parseInt(value);
            return parsed > 0 ? parsed : null;
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private boolean hasSourceEntry(Set<String> entrySet, String className) {
        if (entrySet == null || entrySet.isEmpty()) {
            return false;
        }
        for (String sourcePath : CoverageSourceClassUtil.buildSourcePathCandidates(className)) {
            for (String entryName : entrySet) {
                String normalizedName = entryName.replace('\\', '/');
                if (normalizedName.endsWith("/" + sourcePath) || normalizedName.equals(sourcePath)) {
                    return true;
                }
            }
        }
        return false;
    }

    private List<Integer> getChangedLinesForClass(Map<String, List<Integer>> diffMap, String className) {
        if (diffMap == null || diffMap.isEmpty() || !StringUtils.hasText(className)) {
            return null;
        }
        for (String candidateClassName : CoverageSourceClassUtil.buildSourceClassCandidates(className)) {
            List<Integer> changedLines = diffMap.get(candidateClassName);
            if (changedLines != null) {
                return changedLines;
            }
        }
        return null;
    }

    private String resolveCoverageOwnerClassName(String className) {
        return CoverageSourceClassUtil.resolveSourceOwnerClassName(className);
    }

    /**
     * 智能匹配类覆盖率数据，处理类名格式不一致的情况。
     * 
     * 常见问题：
     * 1. codeNodes 中的类名可能缺少包前缀（如 "web3Server.config.MultipartConfig"）
     * 2. 静态源码中的类名是完整的（如 "com.web3Server.config.MultipartConfig"）
     * 
     * 匹配策略：
     * 1. 优先精确匹配
     * 2. 如果精确匹配失败，尝试后缀匹配（coverageMap 中的类名以 codeNodes 类名结尾）
     * 3. 返回最短匹配（最可能是正确的完整类名）
     */
    private ClassCoverageIndex findClassCoverageByFuzzyMatch(Map<String, ClassCoverageIndex> coverageMap, String targetClassName) {
        if (!StringUtils.hasText(targetClassName)) {
            return null;
        }
        
        // 策略1：精确匹配（已经在调用方尝试过，这里再试一次以防万一）
        ClassCoverageIndex exactMatch = coverageMap.get(targetClassName);
        if (exactMatch != null) {
            return exactMatch;
        }
        
        // 策略2：后缀匹配（处理包名前缀缺失）
        List<Map.Entry<String, ClassCoverageIndex>> suffixMatches = new ArrayList<>();
        for (Map.Entry<String, ClassCoverageIndex> entry : coverageMap.entrySet()) {
            String candidateName = entry.getKey();
            // 检查是否以 targetClassName 结尾，且前一个字符是点号（确保是完整的包路径）
            if (candidateName.endsWith(targetClassName)) {
                int endIndex = candidateName.length() - targetClassName.length();
                if (endIndex == 0 || candidateName.charAt(endIndex - 1) == '.') {
                    suffixMatches.add(entry);
                }
            }
        }
        
        if (suffixMatches.isEmpty()) {
            return null;
        }
        
        // 如果有多个匹配，选择最短的（最可能是正确的完整类名）
        suffixMatches.sort(Comparator.comparingInt(e -> e.getKey().length()));
        ClassCoverageIndex bestMatch = suffixMatches.get(0).getValue();
        
        if (suffixMatches.size() > 1) {
            logger.info("类名模糊匹配：目标={}, 找到 {} 个后缀匹配，选择最短的: {}", 
                    targetClassName, suffixMatches.size(), suffixMatches.get(0).getKey());
        } else {
            logger.info("类名模糊匹配成功：目标={}, 匹配到={}", targetClassName, suffixMatches.get(0).getKey());
        }
        
        return bestMatch;
    }

    private byte[] readAllBytes(InputStream is) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        int nRead;
        byte[] data = new byte[4096];
        while ((nRead = is.read(data, 0, data.length)) != -1) {
            buffer.write(data, 0, nRead);
        }
        return buffer.toByteArray();
    }

    private List<String> getZipEntryNames(String appId, String commitId) {
        if (!StringUtils.hasText(appId) || !StringUtils.hasText(commitId)) {
            return null;
        }

        List<VersionCenterIndex> versions = versionCenterRepository.findTop1ByVersionItem_AppIdAndVersionItem_RepoCommitId(appId, commitId);
        if (versions == null || versions.isEmpty()) {
            return null;
        }

        VersionItem item = versions.get(0).getVersionItem();
        if (item == null || !StringUtils.hasText(item.getProgramFile())) {
            return null;
        }

        String programFile = item.getProgramFile();
        File codeFile = new File(programFile);
        if (!codeFile.isAbsolute()) {
            codeFile = new File(resourceService.getCacheRoot(), programFile);
        }

        if (!codeFile.exists()) {
            return null;
        }

        String cacheKey = codeFile.getAbsolutePath();
        return zipEntryCache.computeIfAbsent(cacheKey, k -> {
            List<String> list = new ArrayList<>();
            try (ZipFile zip = new ZipFile(new File(k))) {
                Enumeration<? extends ZipEntry> en = zip.entries();
                while (en.hasMoreElements()) {
                    list.add(en.nextElement().getName());
                }
            } catch (IOException e) {
                logger.error("Failed to read zip entries from {}", k, e);
            }
            return list;
        });
    }

    private String escapeHtml(String text) {
        if (text == null) {
            return "";
        }
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    @Override
    public Page<ClassCoverageIndex> getClassCoveragePage(String reportId, String className, String methodName,
                                                         Double minLineRate, Double maxLineRate,
                                                         Double minBranchRate, Double maxBranchRate,
                                                         Double minMethodRate, Double maxMethodRate,
                                                         Integer minComplexity, Integer maxComplexity,
                                                         Pageable pageable) {
        if (reportId == null || reportId.isEmpty()) {
            return new org.springframework.data.domain.PageImpl<>(java.util.Collections.emptyList(), pageable, 0);
        }
        return classCoverageRepository.findByReportIdFiltered(reportId, className, methodName,
                minLineRate, maxLineRate, minBranchRate, maxBranchRate, minMethodRate, maxMethodRate,
                minComplexity, maxComplexity, pageable);
    }

    @Override
    public List<CoverageTreeNode> getTreeNodes(String reportId, String parentPackage, String classNameSearch, String methodNameSearch,
                                               Double minRate, Double maxRate,
                                               Double minBranchRate, Double maxBranchRate,
                                               Double minMethodRate, Double maxMethodRate,
                                               Integer minComplexity, Integer maxComplexity) {
        if (reportId == null || reportId.isEmpty()) {
            return java.util.Collections.emptyList();
        }

        String normalizedParent = StringUtils.hasText(parentPackage) ? parentPackage.trim() : "";
        String prefix = buildTreePrefix(normalizedParent);
        List<ClassCoverageIndex> classes = classCoverageRepository.findTreeCandidates(reportId, classNameSearch, methodNameSearch,
                minRate, maxRate, minBranchRate, maxBranchRate, minMethodRate, maxMethodRate,
                minComplexity, maxComplexity, prefix);

        Map<String, CoverageTreeNode> nodesMap = new HashMap<>();
        Set<String> exactClassNames = classes.stream()
                .map(ClassCoverageIndex::getClassName)
                .map(this::normalizeCoverageTreeName)
                .filter(StringUtils::hasText)
                .collect(Collectors.toSet());

        for (ClassCoverageIndex cc : classes) {
            String classFullName = normalizeCoverageTreeName(cc.getClassName());
            if (!StringUtils.hasText(classFullName) || !classFullName.startsWith(prefix)) {
                continue;
            }

            String remaining = classFullName.substring(prefix.length());
            TreeNodeIdentity identity = resolveImmediateTreeNode(prefix, remaining, classFullName, exactClassNames);

            String nodeKey = identity.type + ":" + identity.fullName;
            CoverageTreeNode node = nodesMap.get(nodeKey);
            if (node == null) {
                node = new CoverageTreeNode();
                node.setFullName(identity.fullName);
                node.setName(identity.nodeName);
                node.setType(identity.type);
                node.setParentId(normalizedParent);
                node.setId(reportId + ":" + identity.type + ":" + identity.fullName);
                nodesMap.put(nodeKey, node);
            }

            boolean hasChildren = hasTreeChildren(classFullName, identity.fullName);
            if (hasChildren) {
                node.setHasChildren(true);
            }

            boolean aggregateStats = "package".equals(identity.type)
                    || classFullName.equals(identity.fullName)
                    || !exactClassNames.contains(identity.fullName);
            if (aggregateStats) {
                node.setTotalMethods(node.getTotalMethods() + cc.getTotalMethods());
                node.setCoveredMethods(node.getCoveredMethods() + cc.getCoveredMethods());
                node.setTotalBranches(node.getTotalBranches() + cc.getTotalBranches());
                node.setCoveredBranches(node.getCoveredBranches() + cc.getCoveredBranches());
                node.setTotalBranchTargets(node.getTotalBranchTargets() + cc.getTotalBranchTargets());
                node.setCoveredBranchTargets(node.getCoveredBranchTargets() + cc.getCoveredBranchTargets());
                node.setTotalLines(node.getTotalLines() + cc.getTotalLines());
                node.setCoveredLines(node.getCoveredLines() + cc.getCoveredLines());
                node.setTotalComplexity(node.getTotalComplexity() + cc.getTotalComplexity());
            }
        }

        List<CoverageTreeNode> nodes = new ArrayList<>(nodesMap.values());
        for (CoverageTreeNode node : nodes) {
            node.setLineRate(rate(node.getCoveredLines(), node.getTotalLines()));
            node.setBranchRate(rate(node.getCoveredBranchTargets(), node.getTotalBranchTargets()));
            node.setMethodRate(rate(node.getCoveredMethods(), node.getTotalMethods()));
        }
        nodes.sort(Comparator.comparingInt((CoverageTreeNode node) -> "package".equals(node.getType()) ? 0 : 1)
                .thenComparing(CoverageTreeNode::getFullName, Comparator.nullsLast(String::compareTo)));
        return nodes;
    }

    private String normalizeCoverageTreeName(String className) {
        if (!StringUtils.hasText(className)) {
            return className;
        }
        return CoverageSourceClassUtil.isPathLikeName(className)
                ? CoverageSourceClassUtil.normalizePathName(className)
                : className;
    }

    private String buildTreePrefix(String parent) {
        if (!StringUtils.hasText(parent)) {
            return "";
        }
        return CoverageSourceClassUtil.isPathLikeName(parent) ? CoverageSourceClassUtil.normalizePathName(parent) + "/" : parent + ".";
    }

    private boolean hasTreeChildren(String classFullName, String nodeFullName) {
        if (!StringUtils.hasText(classFullName) || !StringUtils.hasText(nodeFullName)) {
            return false;
        }
        String separator = CoverageSourceClassUtil.isPathLikeName(classFullName) ? "/" : ".";
        return classFullName.startsWith(nodeFullName + separator);
    }

    private double rate(long covered, long total) {
        return total <= 0 ? 0D : (double) covered / total * 100;
    }

    private TreeNodeIdentity resolveImmediateTreeNode(String prefix, String remaining, String classFullName, Set<String> exactClassNames) {
        if (CoverageSourceClassUtil.isPathLikeName(classFullName)) {
            if (!StringUtils.hasText(prefix)) {
                TreeNodeIdentity sourceRoot = resolvePathSourceRootNode(classFullName);
                if (sourceRoot != null) {
                    return sourceRoot;
                }
            }
            String normalizedRemaining = CoverageSourceClassUtil.normalizePathName(remaining);
            boolean leadingSlash = normalizedRemaining.startsWith("/");
            if (leadingSlash) {
                normalizedRemaining = normalizedRemaining.substring(1);
            }
            String[] segments = normalizedRemaining.split("/");
            if (segments.length == 0) {
                return new TreeNodeIdentity(CoverageSourceClassUtil.displayFileName(classFullName), classFullName, "class");
            }
            if (segments.length == 1) {
                String fullName = prefix + (leadingSlash && prefix.isEmpty() ? "/" : "") + segments[0];
                return new TreeNodeIdentity(segments[0], fullName, "class");
            }
            String nodeName = segments[0];
            String fullName = prefix + (leadingSlash && prefix.isEmpty() ? "/" : "") + nodeName;
            return new TreeNodeIdentity(nodeName, fullName, "package");
        }

        String[] segments = remaining.split("\\.");
        if (segments.length == 0) {
            return new TreeNodeIdentity(classFullName, classFullName, "class");
        }

        if (segments.length == 1) {
            String fullName = prefix + segments[0];
            if (exactClassNames.contains(fullName)) {
                String nodeName = CoverageSourceClassUtil.toTreeDisplayName(segments[0], "class");
                return new TreeNodeIdentity(nodeName, fullName, "class");
            }
            return new TreeNodeIdentity(segments[0], fullName, "package");
        }

        String nodeName = segments[0];
        String fullName = prefix + nodeName;
        return new TreeNodeIdentity(nodeName, fullName, "package");
    }

    private TreeNodeIdentity resolvePathSourceRootNode(String classFullName) {
        String normalized = CoverageSourceClassUtil.normalizePathName(classFullName);
        String[] sourceMarkers = {"/src/", "/app/", "/pages/", "/components/", "/lib/"};
        for (String marker : sourceMarkers) {
            int markerIndex = normalized.indexOf(marker);
            if (markerIndex <= 0) {
                continue;
            }
            String rootPath = normalized.substring(0, markerIndex);
            String rootName = CoverageSourceClassUtil.displayFileName(rootPath);
            if (StringUtils.hasText(rootName)) {
                return new TreeNodeIdentity(rootName, rootPath, "package");
            }
        }
        return null;
    }

    private static class TreeNodeIdentity {
        private final String nodeName;
        private final String fullName;
        private final String type;

        private TreeNodeIdentity(String nodeName, String fullName, String type) {
            this.nodeName = nodeName;
            this.fullName = fullName;
            this.type = type;
        }
    }

    @Override
    public List<CoverageReportIndex> getReportsByAppId(String appId) {
        return coverageReportRepository.findByAppId(appId);
    }

    @Override
    public void deleteReport(String reportId) {
        if (!StringUtils.hasText(reportId)) {
            return;
        }
        logger.info("Deleting coverage report: {}", reportId);

        classCoverageRepository.deleteByReportId(reportId);
        coverageReportRepository.deleteById(reportId);
        logger.info("Coverage report and its details deleted successfully: {}", reportId);
    }

    private static class SnapshotCoverageContext {
        private List<String> snapshotIds = new ArrayList<>();
        private List<String> traceIds = new ArrayList<>();
        private String snapshotFingerprint;
        private String lastSnapshotTime;

        public List<String> getSnapshotIds() {
            return snapshotIds;
        }

        public void setSnapshotIds(List<String> snapshotIds) {
            this.snapshotIds = snapshotIds;
        }

        public List<String> getTraceIds() {
            return traceIds;
        }

        public void setTraceIds(List<String> traceIds) {
            this.traceIds = traceIds;
        }

        public String getSnapshotFingerprint() {
            return snapshotFingerprint;
        }

        public void setSnapshotFingerprint(String snapshotFingerprint) {
            this.snapshotFingerprint = snapshotFingerprint;
        }

        public String getLastSnapshotTime() {
            return lastSnapshotTime;
        }

        public void setLastSnapshotTime(String lastSnapshotTime) {
            this.lastSnapshotTime = lastSnapshotTime;
        }
    }
}
