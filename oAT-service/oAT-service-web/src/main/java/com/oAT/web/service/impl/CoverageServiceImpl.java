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
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import javax.servlet.http.HttpServletResponse;
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
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.data.elasticsearch.core.query.NativeSearchQuery;
import org.springframework.data.elasticsearch.core.query.NativeSearchQueryBuilder;
import org.springframework.data.elasticsearch.core.query.SourceFilter;
import org.springframework.data.elasticsearch.core.query.FetchSourceFilter;

@Service
public class CoverageServiceImpl implements CoverageService, InitializingBean, StandardDate {
    private static final Logger logger = LoggerFactory.getLogger(CoverageServiceImpl.class);

    private final Map<String, List<String>> zipEntryCache = new ConcurrentHashMap<>();

    // Git Diff result cache to improve performance during report generation
    private final Map<String, Map<String, List<Integer>>> diffCache = new ConcurrentHashMap<>();

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
    private ElasticsearchOperations elasticsearchOperations;

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
        // 先获取应用名称，以便显示更直观
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
                // Delay cleanup to allow frontend to poll the final state
                scheduleJobCleanup(job);
            } catch (Exception e) {
                logger.error("Generate report failed", e);
                job.state = Job.JobState.error;
                String errorMsg = toFriendlyError(e.getMessage());
                job.getProgress().updateName("生成失败: " + errorMsg);
                job.getLogger().error("生成报告失败: " + errorMsg);
                // Delay cleanup to allow frontend to poll the final state
                scheduleJobCleanup(job);
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

    private String generateReportInternal(String appId, String versionNumber, String branch, String commitId,
                                        Integer reportType, String baseVersionNumber, String baseCommitId, Job<String> job) {
        if (job != null) job.getLogger().info("正在获取应用配置信息...");
        AppVo app = appService.getApp(appId);

        // Clear caches for this task
        diffCache.clear();
        zipEntryCache.clear();

        // 1. Get Static Source Info
        if (job != null) job.getProgress().next("加载静态源码信息", 10);

        // Filter static info by branch and commitId to ensure we use the correct code structure for current commit
        List<StaticSourceInfo> appStaticInfos = staticInfoRepository.findByAppId(appId);

        if (appStaticInfos.isEmpty()) {
            throw new RuntimeException("找不到静态源码数据，请确保已进行静态扫描或源码上传。");
        }

        // Filter out classes that don't exist in the current commit for incremental report.
        // Full report must keep existing static source data; otherwise current Commit reports can be emptied
        // when source packages are absent, multi-module paths differ, or static data comes from runtime upload.
        if (StringUtils.hasText(commitId) && normalizeReportType(reportType) != REPORT_TYPE_VERSION_FULL) {
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
        SnapshotCoverageContext snapshotContext = buildSnapshotCoverageContext(app, versionNumber);
        if (snapshotContext.getSnapshotIds().isEmpty()) {
            throw new RuntimeException("当前版本下没有可用系统快照，无法生成覆盖率报告。");
        }
        if (job != null) {
            job.getLogger().info("本次将基于系统快照生成报告，快照数量: " + snapshotContext.getSnapshotIds().size());
        }

        if (job != null) job.getProgress().next("分析历史报告并进行覆盖率数据累积", 15);

        CoverageReportIndex candidateLastReport =
                getBestReportForAccumulation(appId, versionNumber, branch, commitId, reportType);
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

        // 3. Aggregate TraceNode data from snapshot timeline
        if (job != null) job.getProgress().next("处理系统快照中的链路追踪数据", 50);
        int processed = 0;
        int total = traceIdsToProcess.size();
        for (String traceId : traceIdsToProcess) {
            mergeSnapshotTraceCoverage(traceId, coverageMap);
            processed++;
            if (job != null) {
                job.getProgress().total = total;
                job.getProgress().loaded = processed;
                job.getLogger().info("正在处理快照链路数据 (" + processed + "/" + total + ")");
            }
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
        report.setBaseRepoCommitId(reusedReport != null ? reusedReport.getRepoCommitId() : baseCommitId);

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
            return null;
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
            return;
        }

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
            return;
        }

        HttpTraceNode httpNode = (HttpTraceNode) rootNode;
        StackNodeVo[] codeNodes = httpNode.getCodeNodes();
        if (codeNodes == null) {
            return;
        }

        for (StackNodeVo sn : codeNodes) {
            ClassCoverageIndex classCov = coverageMap.get(resolveCoverageOwnerClassName(sn.getClassName()));
            if (classCov != null) {
                mergeStackNode(classCov, sn);
            }
        }
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
            } else {
                // For full report, calculate inc stats vs diffMap (comparison with previous)
                List<Integer> changedLines = getChangedLinesForClass(diffMap, classCov.getClassName());
                if (changedLines == null) {
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
        AppVo app = appService.getApp(appId);
        SnapshotCoverageContext latestSnapshotContext = buildSnapshotCoverageContext(app, versionNumber);

        if (report == null) {
            return !latestSnapshotContext.getSnapshotIds().isEmpty();
        }

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

        BoolQueryBuilder boolQuery = QueryBuilders.boolQuery()
                .must(QueryBuilders.termQuery("reportId", reportId));

        if (className != null && !className.isEmpty()) {
            boolQuery.must(QueryBuilders.wildcardQuery("className", "*" + className + "*"));
        }

        if (methodName != null && !methodName.isEmpty()) {
            boolQuery.must(QueryBuilders.wildcardQuery("methods.methodName", "*" + methodName + "*"));
        }

        addRangeQuery(boolQuery, "lineRate", minLineRate, maxLineRate);
        addRangeQuery(boolQuery, "branchRate", minBranchRate, maxBranchRate);
        addRangeQuery(boolQuery, "methodRate", minMethodRate, maxMethodRate);
        addRangeQuery(boolQuery, "totalComplexity", minComplexity, maxComplexity);

        NativeSearchQuery query = new NativeSearchQueryBuilder()
                .withQuery(boolQuery)
                .withPageable(pageable)
                .build();

        SearchHits<ClassCoverageIndex> searchHits = elasticsearchOperations.search(query, ClassCoverageIndex.class);
        List<ClassCoverageIndex> content = searchHits.getSearchHits().stream()
                .map(SearchHit::getContent)
                .collect(Collectors.toList());
        return new org.springframework.data.domain.PageImpl<>(content, pageable, searchHits.getTotalHits());
    }

    private void addRangeQuery(BoolQueryBuilder boolQuery, String field, Number min, Number max) {
        if (min != null || max != null) {
            org.elasticsearch.index.query.RangeQueryBuilder rangeQuery = QueryBuilders.rangeQuery(field);
            if (min != null) rangeQuery.gte(min);
            if (max != null) rangeQuery.lte(max);
            boolQuery.filter(rangeQuery);
        }
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

        BoolQueryBuilder boolQuery = QueryBuilders.boolQuery()
                .must(QueryBuilders.termQuery("reportId", reportId));

        if (classNameSearch != null && !classNameSearch.isEmpty()) {
            boolQuery.must(QueryBuilders.wildcardQuery("className", "*" + classNameSearch.toLowerCase() + "*"));
        }

        if (methodNameSearch != null && !methodNameSearch.isEmpty()) {
            boolQuery.must(QueryBuilders.wildcardQuery("methods.methodName", "*" + methodNameSearch + "*"));
        }

        addRangeQuery(boolQuery, "lineRate", minRate, maxRate);
        addRangeQuery(boolQuery, "branchRate", minBranchRate, maxBranchRate);
        addRangeQuery(boolQuery, "methodRate", minMethodRate, maxMethodRate);
        addRangeQuery(boolQuery, "totalComplexity", minComplexity, maxComplexity);

        String normalizedParent = StringUtils.hasText(parentPackage) ? parentPackage.trim() : "";
        String prefix = normalizedParent.isEmpty() ? "" : normalizedParent + ".";
        if (!prefix.isEmpty()) {
            // 按层级前缀过滤，只扫描当前包的后代类，避免每次懒加载都扫全量报告。
            boolQuery.filter(QueryBuilders.prefixQuery("className", prefix));
        }

        SourceFilter sourceFilter = new FetchSourceFilter(null, new String[]{"methods"});
        Map<String, CoverageTreeNode> nodesMap = new HashMap<>();
        Set<String> exactClassNames = new HashSet<>();

        int page = 0;
        Page<ClassCoverageIndex> classPage;
        do {
            NativeSearchQueryBuilder queryBuilder = new NativeSearchQueryBuilder()
                    .withQuery(boolQuery)
                    .withSourceFilter(sourceFilter)
                    .withPageable(PageRequest.of(page, TREE_NODE_SCAN_PAGE_SIZE, Sort.by(new Sort.Order(Sort.Direction.ASC, "className"))));

            SearchHits<ClassCoverageIndex> searchHits = elasticsearchOperations.search(queryBuilder.build(), ClassCoverageIndex.class);
            List<ClassCoverageIndex> content = searchHits.getSearchHits().stream()
                    .map(SearchHit::getContent)
                    .collect(Collectors.toList());
            classPage = new org.springframework.data.domain.PageImpl<>(content, PageRequest.of(page, TREE_NODE_SCAN_PAGE_SIZE), searchHits.getTotalHits());
            for (ClassCoverageIndex cc : classPage.getContent()) {
                String classFullName = cc.getClassName();
                if (!classFullName.startsWith(prefix)) {
                    continue;
                }
                exactClassNames.add(classFullName);
            }
            page++;
        } while (classPage.hasNext());

        page = 0;
        do {
            NativeSearchQueryBuilder queryBuilder = new NativeSearchQueryBuilder()
                    .withQuery(boolQuery)
                    .withSourceFilter(sourceFilter)
                    .withPageable(PageRequest.of(page, TREE_NODE_SCAN_PAGE_SIZE, Sort.by(new Sort.Order(Sort.Direction.ASC, "className"))));

            SearchHits<ClassCoverageIndex> searchHits2 = elasticsearchOperations.search(queryBuilder.build(), ClassCoverageIndex.class);
            List<ClassCoverageIndex> content2 = searchHits2.getSearchHits().stream()
                    .map(SearchHit::getContent)
                    .collect(Collectors.toList());
            classPage = new org.springframework.data.domain.PageImpl<>(content2, PageRequest.of(page, TREE_NODE_SCAN_PAGE_SIZE), searchHits2.getTotalHits());
            for (ClassCoverageIndex cc : classPage.getContent()) {
                String classFullName = cc.getClassName();
                if (!classFullName.startsWith(prefix)) {
                    continue;
                }

                String remaining = classFullName.substring(prefix.length());
                TreeNodeIdentity identity = resolveImmediateTreeNode(prefix, remaining, classFullName);

                CoverageTreeNode node = nodesMap.get(identity.fullName);
                if (node == null) {
                    node = new CoverageTreeNode();
                    node.setFullName(identity.fullName);
                    node.setName(identity.nodeName);
                    node.setType(identity.type);
                    node.setParentId(normalizedParent);
                    node.setId(reportId + ":" + identity.fullName);
                    nodesMap.put(identity.fullName, node);
                }

                boolean hasChildren = classFullName.startsWith(identity.fullName + ".");
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
            page++;
        } while (classPage.hasNext());

        nodesMap.values().forEach(node -> {
            if (node.getTotalLines() > 0) node.setLineRate((double) node.getCoveredLines() / node.getTotalLines() * 100);
            else node.setLineRate(0.0);

            if (node.getTotalBranchTargets() > 0) node.setBranchRate((double) node.getCoveredBranchTargets() / node.getTotalBranchTargets() * 100);
            else node.setBranchRate(0.0);

            if (node.getTotalMethods() > 0) node.setMethodRate((double) node.getCoveredMethods() / node.getTotalMethods() * 100);
            else node.setMethodRate(0.0);
        });

        List<CoverageTreeNode> result = new ArrayList<>(nodesMap.values());
        result.sort((a, b) -> {
            if (!a.getType().equals(b.getType())) return "package".equals(a.getType()) ? -1 : 1;
            return a.getName().compareTo(b.getName());
        });

        return result;
    }

    private TreeNodeIdentity resolveImmediateTreeNode(String prefix, String remaining, String classFullName) {
        String[] segments = remaining.split("\\.");
        if (segments.length == 0) {
            return new TreeNodeIdentity(classFullName, classFullName, "class");
        }

        int firstTypeSegment = CoverageSourceClassUtil.findFirstTypeSegmentIndex(segments);
        if (firstTypeSegment <= 0) {
            String nodeName = CoverageSourceClassUtil.toTreeDisplayName(segments[0], "class");
            return new TreeNodeIdentity(nodeName, prefix + segments[0], "class");
        }

        String nodeName = segments[0];
        String fullName = prefix + nodeName;
        return new TreeNodeIdentity(nodeName, fullName, "package");
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

        NativeSearchQuery deleteQuery = new NativeSearchQueryBuilder()
                .withQuery(QueryBuilders.termQuery("reportId", reportId))
                .build();
        elasticsearchOperations.delete(deleteQuery, ClassCoverageIndex.class);

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
