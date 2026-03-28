package com.oAT.web.service.impl;

import com.alibaba.excel.EasyExcel;
import com.oAT.agent.model.HttpTraceNode;
import com.oAT.agent.model.StackNodeVo;
import com.oAT.agent.model.TraceNode;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import javax.servlet.http.HttpServletResponse;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URLEncoder;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import com.oAT.web.service.ResourceService;
import com.oAT.web.esDao.entity.MethodCoverageExportVo;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.sort.SortBuilders;
import org.elasticsearch.search.sort.SortOrder;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.query.DeleteQuery;
import org.springframework.data.elasticsearch.core.query.NativeSearchQueryBuilder;
import org.springframework.data.elasticsearch.core.query.SourceFilter;
import org.springframework.data.elasticsearch.core.query.FetchSourceFilter;

@Service
public class CoverageServiceImpl implements CoverageService, InitializingBean {
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
    private List<Job<String>> jobs;

    private static final int TREE_NODE_SCAN_PAGE_SIZE = 2000;

    @Override
    public void afterPropertiesSet() {
        jobExecutors = Executors.newFixedThreadPool(5);
        jobs = Collections.synchronizedList(new LinkedList<>());
    }

    @Override
    public String startGenerateJob(String appId, String versionNumber, String branch, String commitId) {
        return startJob(appId, versionNumber, branch, commitId, 0, null, null);
    }

    @Override
    public String startGenerateIncrementalJob(String appId, String versionNumber, String branch, String commitId,
                                            String baseVersionNumber, String baseCommitId) {
        return startJob(appId, versionNumber, branch, commitId, 1, baseVersionNumber, baseCommitId);
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
        String jobName = finalAppDisplayName + ":" + versionNumber + (reportType == 1 ? " (增量)" : " (全量)");
        Job<String> job = new Job<>(jobName);
        jobs.add(job);
        jobExecutors.execute(() -> {
            try {
                job.state = Job.JobState.active;
                job.getProgress().next("初始化生成任务", 5);
                job.getLogger().info("开始为应用 [" + finalAppDisplayName + "] 生成" + (reportType == 1 ? "增量" : "全量") + "覆盖率报告...");

                String reportId = generateReportInternal(appId, versionNumber, branch, commitId, reportType, baseVersionNumber, baseCommitId, job);

                job.setData(reportId);
                job.state = Job.JobState.finish;
                job.getProgress().finish("报告生成完成");
                job.getLogger().info("报告生成成功: " + reportId);
            } catch (Exception e) {
                logger.error("Generate report failed", e);
                job.state = Job.JobState.error;
                String errorMsg = toFriendlyError(e.getMessage());
                job.getProgress().updateName("生成失败: " + errorMsg);
                job.getLogger().error("生成报告失败: " + errorMsg);
            }
        });
        return job.getId();
    }

    private String toFriendlyError(String msg) {
        if (msg == null) {
            return "未知错误";
        }
        if (msg.contains("search_phase_execution_exception") || msg.contains("all shards failed")) {
            return "数据底座(Elasticsearch)查询异常，请检查索引是否存在或服务是否正常。";
        }
        if (msg.contains("Connection refused") || msg.contains("Connection timed out")) {
            return "网络连接失败，请检查相关服务(Git/ES)是否在线。";
        }
        return msg;
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

        // Clear diff cache for this task
        diffCache.clear();

        // 1. Get Static Source Info
        if (job != null) job.getProgress().next("加载静态源码信息", 10);

        // Filter static info by branch and commitId to ensure we use the correct code structure for current commit
        List<StaticSourceInfo> appStaticInfos = staticInfoRepository.findByAppId(appId);

        if (appStaticInfos.isEmpty()) {
            throw new RuntimeException("找不到静态源码数据，请确保已进行静态扫描或源码上传。");
        }

        // Filter out classes that don't exist in the current commit
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
                        // 转换类名为可能的路径格式进行匹配
                        String pathLike = className.replace('.', '/') + ".java";
                        exists = entrySet.stream().anyMatch(e -> e.endsWith(pathLike));
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
            appStaticInfos = filteredStaticInfos;
        }

        if (appStaticInfos.isEmpty()) {
            throw new RuntimeException("当前 Commit 中没有找到有效的静态源码数据，请检查 Git 分支/Commit 是否正确。");
        }

        // 1.1 Diff Logic for Incremental Report
        Map<String, List<Integer>> incrementalDiffMap = null;
        if (reportType == 1 && StringUtils.hasText(baseCommitId)) {
            if (job != null) job.getLogger().info("正在获取基准 Commit [" + baseCommitId + "] 的差异对比...");
            incrementalDiffMap = gitService.getDiff(app.getRepoAddress(), app.getRepoUserName(), app.getRepoPassword(),
                    baseCommitId, commitId);
            if (incrementalDiffMap == null || incrementalDiffMap.isEmpty()) {
                throw new RuntimeException("两个版本间没有代码差异，无法生成增量报告。");
            }
        }

        Map<String, ClassCoverageIndex> coverageMap = new HashMap<>();
        for (StaticSourceInfo staticInfo : appStaticInfos) {
            StaticSourceClassInfo classInfo = staticInfo.getClassInfo();
            if (classInfo == null) continue;

            // If incremental report, only include classes that have changes
            if (reportType == 1) {
                if (incrementalDiffMap == null || !incrementalDiffMap.containsKey(classInfo.getClassName())) {
                    continue;
                }
            }

            ClassCoverageIndex classCov = createInitialClassCoverage(appId, classInfo, incrementalDiffMap);
            if (classCov.getTotalLines() > 0 || reportType == 0) {
                 coverageMap.put(classCov.getClassName(), classCov);
            }
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
            ClassCoverageIndex classCov = coverageMap.get(sn.getClassName());
            if (classCov != null) {
                mergeStackNode(classCov, sn);
            }
        }
    }

    private ClassCoverageIndex createInitialClassCoverage(String appId, StaticSourceClassInfo classInfo, Map<String, List<Integer>> incrementalDiffMap) {
        ClassCoverageIndex classCov = new ClassCoverageIndex();
        classCov.setAppId(appId);
        classCov.setClassName(classInfo.getClassName());

        List<MethodCoverageDetail> methodDetails = new ArrayList<>();
        int totalLines = 0;
        int totalBranches = 0;
        int totalComplexity = 0;

        List<Integer> changedLinesInClass = incrementalDiffMap != null ? incrementalDiffMap.get(classInfo.getClassName()) : null;

        if (classInfo.getMethodMaps() != null) {
            for (StaticSourceMethodInfo mInfo : classInfo.getMethodMaps().values()) {
                MethodCoverageDetail md = new MethodCoverageDetail();
                md.setMethodName(mInfo.getMethodName());
                md.setMethodDesc(mInfo.getMethodDesc());

                List<Integer> methodLines = mInfo.getMethodLineNumberMap();
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

                    // Filter branches for incremental
                    // Simple heuristic: if method changed, count only branches that are on changed lines (if mapped)
                    // Or keep all branches for changed methods. Let's try to filter if branchLineNumberSet is available.
                    List<Integer> branchLines = mInfo.getBranchLineNumberSet();
                    if (branchLines != null && !branchLines.isEmpty()) {
                        int filteredBranches = 0;
                        for (Integer bl : branchLines) {
                             if (changedLinesInClass.contains(bl)) {
                                 filteredBranches++;
                             }
                        }
                        // totalBranchCount might be higher than branchLineNumberSet size if multiple branches on same line
                        // Adjust proportionally? Or just use branchLines size.
                        // Let's use simple logic: if any branch line in changedLines, include its proportion of branches.
                        if (mInfo.getTotalBranchCount() != null && mInfo.getTotalBranchCount() > 0) {
                             double ratio = (double) filteredBranches / branchLines.size();
                             md.setTotalBranches((int) Math.ceil(mInfo.getTotalBranchCount() * ratio));
                        } else {
                             md.setTotalBranches(0);
                        }
                    } else {
                        // If no branch mapping, include all branches of the modified method
                        md.setTotalBranches(mInfo.getTotalBranchCount() != null ? mInfo.getTotalBranchCount() : 0);
                    }
                } else {
                    md.setTotalLineNumbers(methodLines);
                    md.setTotalBranches(mInfo.getTotalBranchCount() != null ? mInfo.getTotalBranchCount() : 0);
                }

                md.setTotalLines(md.getTotalLineNumbers() != null ? md.getTotalLineNumbers().size() : 0);
                md.setComplexity(mInfo.getCyclomaticComplexityMap() != null ? mInfo.getCyclomaticComplexityMap() : 0);
                md.setCoveredLineNumbers(new ArrayList<>());
                md.setCoveredBranchIds(new ArrayList<>());
                methodDetails.add(md);

                totalLines += md.getTotalLines();
                totalBranches += md.getTotalBranches();
                totalComplexity += md.getComplexity();
            }
        }

        classCov.setTotalMethods(methodDetails.size());
        classCov.setTotalLines(totalLines);
        classCov.setTotalBranches(totalBranches);
        classCov.setTotalComplexity(totalComplexity);
        classCov.setMethods(methodDetails);
        return classCov;
    }

    private void saveAndCalculateSummary(CoverageReportIndex report, Map<String, ClassCoverageIndex> coverageMap, Map<String, List<Integer>> diffMap) {
        long totalClasses = coverageMap.size();
        long coveredClasses = 0;
        long totalMethods = 0;
        long coveredMethods = 0;
        long totalBranches = 0;
        long coveredBranches = 0;
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
            classCov.setBranchRate(classCov.getTotalBranches() > 0 ? (double) classCov.getCoveredBranches() / classCov.getTotalBranches() * 100 : 0.0);
            classCov.setMethodRate(classCov.getTotalMethods() > 0 ? (double) classCov.getCoveredMethods() / classCov.getTotalMethods() * 100 : 0.0);

            if (classCov.getCoveredLines() > 0) coveredClasses++;

            totalMethods += classCov.getTotalMethods();
            coveredMethods += classCov.getCoveredMethods();
            totalLines += classCov.getTotalLines();
            coveredLines += classCov.getCoveredLines();
            totalBranches += classCov.getTotalBranches();
            coveredBranches += classCov.getCoveredBranches();
            totalComplexity += classCov.getTotalComplexity();

            // Incremental Calculation (Summary for the report)
            // If it's an incremental report (reportType == 1), coverageMap already contains only incremental data
            if (report.getReportType() != null && report.getReportType() == 1) {
                incTotalClasses++;
                if (classCov.getCoveredLines() > 0) incCoveredClasses++;

                incTotalLines += classCov.getTotalLines();
                incCoveredLines += classCov.getCoveredLines();
                incTotalMethods += classCov.getTotalMethods();
                incCoveredMethods += classCov.getCoveredMethods();
                incTotalBranches += classCov.getTotalBranches();
                incCoveredBranches += classCov.getCoveredBranches();
                incTotalComplexity += classCov.getTotalComplexity();
            } else if (diffMap != null && diffMap.containsKey(classCov.getClassName())) {
                // For full report, calculate inc stats vs diffMap (comparison with previous)
                List<Integer> changedLines = diffMap.get(classCov.getClassName());
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
        if (coveredClasses > totalClasses) coveredClasses = totalClasses;

        report.setTotalClasses(totalClasses);
        report.setCoveredClasses(coveredClasses);
        report.setTotalMethods(totalMethods);
        report.setCoveredMethods(coveredMethods);
        report.setTotalLines(totalLines);
        report.setCoveredLines(coveredLines);
        report.setTotalBranches(totalBranches);
        report.setCoveredBranches(coveredBranches);
        report.setTotalComplexity(totalComplexity);

        // 同样对增量指标进行兜底
        if (incCoveredLines > incTotalLines) incCoveredLines = incTotalLines;
        if (incCoveredMethods > incTotalMethods) incCoveredMethods = incTotalMethods;
        if (incCoveredBranches > incTotalBranches) incCoveredBranches = incTotalBranches;
        if (incCoveredClasses > incTotalClasses) incCoveredClasses = incTotalClasses;

        report.setIncTotalClasses(incTotalClasses);
        report.setIncCoveredClasses(incCoveredClasses);
        report.setIncTotalLines(incTotalLines);
        report.setIncCoveredLines(incCoveredLines);
        report.setIncTotalMethods(incTotalMethods);
        report.setIncCoveredMethods(incCoveredMethods);
        report.setIncTotalBranches(incTotalBranches);
        report.setIncCoveredBranches(incCoveredBranches);
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
        String fileName = URLEncoder.encode("CoverageReport_" + reportId, "UTF-8").replaceAll("\\+", "%20");
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
        String fileName = URLEncoder.encode("MethodCoverageReport_" + reportId, "UTF-8").replaceAll("\\+", "%20");
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
                            vo.setClassTotalMethods(classIdx.getTotalMethods());
                            vo.setClassCoveredMethods(classIdx.getCoveredMethods());
                            vo.setClassTotalLines(classIdx.getTotalLines());
                            vo.setClassCoveredLines(classIdx.getCoveredLines());

                            vo.setMethodName(m.getMethodName());
                            vo.setMethodDesc(m.getMethodDesc());
                            vo.setTotalLines(m.getTotalLines());
                            vo.setCoveredLines(m.getCoveredLines());
                            vo.setLineCoverageRate(m.getTotalLines() > 0 ? String.format("%.2f%%", (double) m.getCoveredLines() / m.getTotalLines() * 100) : "0.00%");
                            vo.setTotalBranches(m.getTotalBranches());
                            vo.setCoveredBranches(m.getCoveredBranches());
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

        List<CoverageReportIndex> repoReports = coverageReportRepository.findByAppIdAndVersionNumber(currentReport.getAppId(), currentReport.getVersionNumber());
        if (repoReports == null || repoReports.isEmpty()) return comparison;

        CoverageReportIndex previousReport = selectPreviousReport(currentReport, repoReports);
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

    private CoverageReportIndex selectPreviousReport(CoverageReportIndex currentReport, List<CoverageReportIndex> repoReports) {
        List<CoverageReportIndex> sameTypeReports = new ArrayList<>();
        for (CoverageReportIndex report : repoReports) {
            if (report == null || !StringUtils.hasText(report.getId())) {
                continue;
            }
            if (normalizeReportType(report.getReportType()) == normalizeReportType(currentReport.getReportType())) {
                sameTypeReports.add(report);
            }
        }

        if (sameTypeReports.isEmpty()) {
            return null;
        }

        sameTypeReports.sort((a, b) -> {
            if (a.getCreateTime() == null) return 1;
            if (b.getCreateTime() == null) return -1;
            return b.getCreateTime().compareTo(a.getCreateTime());
        });

        int currentIndex = -1;
        for (int i = 0; i < sameTypeReports.size(); i++) {
            if (sameTypeReports.get(i).getId().equals(currentReport.getId())) {
                currentIndex = i;
                break;
            }
        }
        if (currentIndex < 0 || currentIndex + 1 >= sameTypeReports.size()) {
            return null;
        }

        List<CoverageReportIndex> olderReports = sameTypeReports.subList(currentIndex + 1, sameTypeReports.size());

        if (StringUtils.hasText(currentReport.getRepoBranch())) {
            for (CoverageReportIndex report : olderReports) {
                if (currentReport.getRepoBranch().equals(report.getRepoBranch())) {
                    return report;
                }
            }
        }

        return olderReports.get(0);
    }

    private int normalizeReportType(Integer reportType) {
        return reportType == null ? 0 : reportType;
    }

    private String buildMethodKey(String className, MethodCoverageDetail method) {
        return String.valueOf(className) + "#" + String.valueOf(method.getMethodName()) + "#" + String.valueOf(method.getMethodDesc());
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
        reports.sort(Comparator.comparing(CoverageReportIndex::getCreateTime, (a, b) -> {
            if (a == null) return -1;
            if (b == null) return 1;
            return a.compareTo(b);
        }));

        List<Map<String, Object>> trend = new ArrayList<>();
        java.text.SimpleDateFormat sdfTrend = new java.text.SimpleDateFormat("MM-dd HH:mm");

        for (CoverageReportIndex report : reports) {
            Map<String, Object> point = new HashMap<>();
            point.put("time", sdfTrend.format(report.getCreateTime()));
            point.put("timestamp", report.getCreateTime().getTime());
            point.put("lineCoverage", report.getTotalLines() > 0 ? (double) report.getCoveredLines() / report.getTotalLines() * 100 : 0);
            point.put("methodCoverage", report.getTotalMethods() > 0 ? (double) report.getCoveredMethods() / report.getTotalMethods() * 100 : 0);
            point.put("branchCoverage", report.getTotalBranches() > 0 ? (double) report.getCoveredBranches() / report.getTotalBranches() * 100 : 0);
            trend.add(point);
        }
        return trend;
    }

    @Override
    public boolean hasNewerData(String appId, String lastProcessedTime) {
        if (lastProcessedTime == null) return true;
        String queryTime = lastProcessedTime;
        if (queryTime.length() == 19) {
            queryTime += ",000";
        }
        Page<TraceNodeIndex> page = traceNodeRepository.findByAppIdAndCreateTimeGreaterThanOrderByCreateTimeAsc(appId, queryTime, PageRequest.of(0, 1));
        return page.getTotalElements() > 0;
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
        if (StringUtils.hasText(snapshot.getUpdateTime())) {
            return snapshot.getUpdateTime();
        }
        return snapshot.getCreateTime() == null ? "" : snapshot.getCreateTime();
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
                gitService.getDiff(app.getRepoAddress(), app.getRepoUserName(), app.getRepoPassword(), oldCommit, newCommit)
        );
    }

    private void reuseMatchedCoverageData(ClassCoverageIndex currentCc, ClassCoverageIndex lastCc, Map<String, List<Integer>> diffMap) {
        List<Integer> changedLines = diffMap != null ? diffMap.get(currentCc.getClassName()) : null;

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
                            currentMd.setCoveredBranchIds(new ArrayList<>(lastMd.getCoveredBranchIds() != null ? lastMd.getCoveredBranchIds() : Collections.emptyList()));
                            currentMd.setCoveredBranches(lastMd.getCoveredBranches());
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

        cc.setCoveredMethods(coveredMethods);
        cc.setCoveredLines(coveredLines);
        cc.setCoveredBranches(coveredBranches);
    }

    private void mergeStackNode(ClassCoverageIndex classCov, StackNodeVo sn) {
        Optional<MethodCoverageDetail> methodOpt = classCov.getMethods().stream()
                .filter(m -> m.getMethodName().equals(sn.getMethodName()) && m.getMethodDesc().equals(sn.getMethodDescriptor()))
                .findFirst();

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

            Set<Integer> coveredBranchIds = new HashSet<>(md.getCoveredBranchIds() != null ? md.getCoveredBranchIds() : Collections.emptyList());
            if (sn.getExecuteBranch() != null) {
                coveredBranchIds.addAll(sn.getExecuteBranch());
            }
            if (coveredBranchIds.size() > md.getTotalBranches()) {
                md.setCoveredBranches(md.getTotalBranches());
                List<Integer> list = new ArrayList<>(coveredBranchIds);
                md.setCoveredBranchIds(list.subList(0, md.getTotalBranches()));
            } else {
                md.setCoveredBranchIds(new ArrayList<>(coveredBranchIds));
                md.setCoveredBranches(md.getCoveredBranchIds().size());
            }

            md.setCovered(md.getCoveredLines() > 0);
        }

        int classCoveredMethods = (int) classCov.getMethods().stream().filter(MethodCoverageDetail::isCovered).count();
        int classCoveredLines = classCov.getMethods().stream().mapToInt(MethodCoverageDetail::getCoveredLines).sum();
        int classCoveredBranches = classCov.getMethods().stream().mapToInt(MethodCoverageDetail::getCoveredBranches).sum();

        classCov.setCoveredMethods(classCoveredMethods);
        classCov.setCoveredLines(classCoveredLines);
        classCov.setCoveredBranches(classCoveredBranches);
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

        String sourcePath = className.replace('.', '/') + ".java";
        String content = null;

        try {
            if (codeFile.getName().toLowerCase().endsWith(".zip") ||
                    codeFile.getName().toLowerCase().endsWith(".jar") ||
                    codeFile.getName().toLowerCase().endsWith(".war")) {

                try (ZipFile zip = new ZipFile(codeFile)) {
                    String[] prefixes = {"", "src/main/java/", "src/test/java/", "src/"};
                    for (String prefix : prefixes) {
                        ZipEntry entry = zip.getEntry(prefix + sourcePath);
                        if (entry != null) {
                            try (InputStream is = zip.getInputStream(entry)) {
                                content = new String(readAllBytes(is), java.nio.charset.StandardCharsets.UTF_8);
                            }
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
                            if (normalizedName.endsWith("/" + sourcePath) || normalizedName.equals(sourcePath)) {
                                if (bestMatch == null) {
                                    bestMatch = entryName;
                                } else if (normalizedName.contains("/src/main/java/") && !bestMatch.contains("/src/main/java/")) {
                                    bestMatch = entryName;
                                } else if (normalizedName.contains("/src/test/java/") && !bestMatch.contains("/src/main/java/") && !bestMatch.contains("/src/test/java/")) {
                                    bestMatch = entryName;
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
                for (String prefix : prefixes) {
                    File f = new File(codeFile, prefix + sourcePath);
                    if (f.exists()) {
                        content = new String(java.nio.file.Files.readAllBytes(f.toPath()), java.nio.charset.StandardCharsets.UTF_8);
                        break;
                    }
                }

                if (content == null) {
                    try (Stream<java.nio.file.Path> stream = java.nio.file.Files.walk(codeFile.toPath())) {
                        java.nio.file.Path foundPath = stream
                                .filter(p -> !java.nio.file.Files.isDirectory(p))
                                .filter(p -> {
                                    String pathStr = p.toString().replace('\\', '/');
                                    return pathStr.endsWith("/" + sourcePath) || pathStr.equals(sourcePath);
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

        return applyColoring(content, classCov);
    }

    private String applyColoring(String content, ClassCoverageIndex classCov) {
        String[] lines = content.split("\\r?\\n");
        StringBuilder sb = new StringBuilder();

        Map<Integer, String> lineColors = new HashMap<>();
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
            }
        }

        sb.append("<pre style='font-family: monospace; white-space: pre;'>");
        for (int i = 0; i < lines.length; i++) {
            int lineNum = i + 1;

            for (Map.Entry<String, Integer> entry : methodStartLines.entrySet()) {
                if (entry.getValue() == lineNum) {
                    sb.append("<a name='").append(entry.getKey()).append("'></a>");
                }
            }

            String color = lineColors.get(lineNum);
            String style = "";
            if ("green".equals(color)) {
                style = "background-color: #ace1af;";
            } else if ("red".equals(color)) {
                style = "background-color: #f5c6cb;";
            }

            sb.append("<div style='").append(style).append("'>")
                    .append("<span style='color: #999; margin-right: 10px;'>").append(lineNum).append("</span>")
                    .append(escapeHtml(lines[i]))
                    .append("</div>");
        }
        sb.append("</pre>");

        return sb.toString();
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

        NativeSearchQueryBuilder queryBuilder = new NativeSearchQueryBuilder()
                .withQuery(boolQuery)
                .withPageable(pageable);

        return classCoverageRepository.search(queryBuilder.build());
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

        int page = 0;
        Page<ClassCoverageIndex> classPage;
        do {
            NativeSearchQueryBuilder queryBuilder = new NativeSearchQueryBuilder()
                    .withQuery(boolQuery)
                    .withSourceFilter(sourceFilter)
                    .withSort(SortBuilders.fieldSort("className").order(SortOrder.ASC))
                    .withPageable(PageRequest.of(page, TREE_NODE_SCAN_PAGE_SIZE));

            classPage = classCoverageRepository.search(queryBuilder.build());
            for (ClassCoverageIndex cc : classPage.getContent()) {
                String className = cc.getClassName();
                if (!className.startsWith(prefix)) {
                    continue;
                }

                String remaining = className.substring(prefix.length());
                int dotIdx = remaining.indexOf('.');

                String nodeName;
                String type;
                String fullName;

                if (dotIdx == -1) {
                    nodeName = remaining;
                    type = "class";
                    fullName = className;
                } else {
                    nodeName = remaining.substring(0, dotIdx);
                    type = "package";
                    fullName = prefix + nodeName;
                }

                CoverageTreeNode node = nodesMap.get(fullName);
                if (node == null) {
                    node = new CoverageTreeNode();
                    node.setFullName(fullName);
                    node.setName(nodeName);
                    node.setType(type);
                    node.setParentId(normalizedParent);
                    node.setId(reportId + ":" + fullName);
                    nodesMap.put(fullName, node);
                }

                node.setTotalMethods(node.getTotalMethods() + cc.getTotalMethods());
                node.setCoveredMethods(node.getCoveredMethods() + cc.getCoveredMethods());
                node.setTotalBranches(node.getTotalBranches() + cc.getTotalBranches());
                node.setCoveredBranches(node.getCoveredBranches() + cc.getCoveredBranches());
                node.setTotalLines(node.getTotalLines() + cc.getTotalLines());
                node.setCoveredLines(node.getCoveredLines() + cc.getCoveredLines());
                node.setTotalComplexity(node.getTotalComplexity() + cc.getTotalComplexity());
                node.setHasChildren("package".equals(type));
            }
            page++;
        } while (classPage.hasNext());

        nodesMap.values().forEach(node -> {
            if (node.getTotalLines() > 0) node.setLineRate((double) node.getCoveredLines() / node.getTotalLines() * 100);
            else node.setLineRate(0.0);

            if (node.getTotalBranches() > 0) node.setBranchRate((double) node.getCoveredBranches() / node.getTotalBranches() * 100);
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

        DeleteQuery deleteQuery = new DeleteQuery();
        deleteQuery.setQuery(QueryBuilders.termQuery("reportId", reportId));
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
