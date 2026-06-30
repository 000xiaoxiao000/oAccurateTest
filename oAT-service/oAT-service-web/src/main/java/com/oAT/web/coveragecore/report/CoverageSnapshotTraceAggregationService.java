package com.oAT.web.coveragecore.report;

import com.oAT.web.common.Job;
import com.oAT.web.esDao.SnapshotCommitMappingRepository;
import com.oAT.web.esDao.entity.ClassCoverageIndex;
import com.oAT.web.esDao.entity.ClassCoverageIndex.MethodCoverageDetail;
import com.oAT.web.esDao.entity.SnapshotCommitMapping;
import com.oAT.web.language.java.JavaStaticCoverageStructureService;
import com.oAT.web.language.java.JavaTraceCoverageMergeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.oAT.web.coveragecore.diff.BranchTargetProbeMaps.copyBranchTargetProbeMap;

@Service
public class CoverageSnapshotTraceAggregationService {

    @Autowired
    private SnapshotCommitMappingRepository snapshotCommitMappingRepository;

    @Autowired
    private JavaStaticCoverageStructureService javaStaticCoverageStructureService;

    @Autowired
    private JavaTraceCoverageMergeService javaTraceCoverageMergeService;

    @Autowired
    private CoverageClassAggregationService coverageClassAggregationService;

    public void aggregateTraceCoverage(String appId, String versionNumber, Integer reportType,
                                       CoverageSnapshotContext snapshotContext, List<String> traceIdsToProcess,
                                       Map<String, ClassCoverageIndex> coverageMap, Job<String> job) {
        boolean isVersionFullReport = CoverageReportTypes.normalize(reportType) == CoverageReportTypes.VERSION_FULL;
        Map<String, String> snapshotToCommitMap = new HashMap<>();
        Map<String, Map<String, ClassCoverageIndex>> perCommitCoverageSnapshots = new LinkedHashMap<>();

        if (isVersionFullReport && !snapshotContext.getSnapshotIds().isEmpty()) {
            loadSnapshotCommitCoverageBaselines(appId, versionNumber, snapshotContext, coverageMap,
                    snapshotToCommitMap, perCommitCoverageSnapshots, job);
        }

        int processed = 0;
        int total = traceIdsToProcess.size();
        for (String traceId : traceIdsToProcess) {
            javaTraceCoverageMergeService.mergeSnapshotTraceCoverage(traceId, coverageMap);
            if (isVersionFullReport && processed < snapshotContext.getSnapshotIds().size()) {
                String snapshotId = snapshotContext.getSnapshotIds().get(processed);
                String commitKey = snapshotToCommitMap.get(snapshotId);
                if (StringUtils.hasText(commitKey)) {
                    Map<String, ClassCoverageIndex> commitCoverageMap = perCommitCoverageSnapshots.get(commitKey);
                    if (commitCoverageMap != null) {
                        javaTraceCoverageMergeService.mergeSnapshotTraceCoverage(traceId, commitCoverageMap);
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

        if (isVersionFullReport && perCommitCoverageSnapshots.size() > 1) {
            if (job != null) {
                job.getLogger().info("版本全量报告：检测到 " + perCommitCoverageSnapshots.size()
                        + " 个 Commit 的覆盖率数据，正在标记跨 Commit 的覆盖率差异...");
            }
            coverageClassAggregationService.markCrossCommitCoverageChanges(coverageMap, perCommitCoverageSnapshots, job);
        }
    }

    private void loadSnapshotCommitCoverageBaselines(String appId, String versionNumber,
                                                     CoverageSnapshotContext snapshotContext,
                                                     Map<String, ClassCoverageIndex> coverageMap,
                                                     Map<String, String> snapshotToCommitMap,
                                                     Map<String, Map<String, ClassCoverageIndex>> perCommitCoverageSnapshots,
                                                     Job<String> job) {
        List<SnapshotCommitMapping> mappings = snapshotCommitMappingRepository.findByAppIdAndVersionNumber(appId, versionNumber);
        for (SnapshotCommitMapping mapping : mappings) {
            if (mapping != null && StringUtils.hasText(mapping.getSnapshotId())
                    && StringUtils.hasText(mapping.getRepoCommitId())) {
                snapshotToCommitMap.put(mapping.getSnapshotId(), mapping.getRepoCommitId());
            }
        }

        if (snapshotToCommitMap.isEmpty()) {
            return;
        }
        if (job != null) {
            job.getLogger().info("版本全量报告：加载了 " + snapshotToCommitMap.size()
                    + " 条快照-Commit 映射关系，将按 Commit 分组追踪覆盖率差异");
        }

        Set<String> uniqueCommits = new HashSet<>(snapshotToCommitMap.values());
        for (String commitKey : uniqueCommits) {
            perCommitCoverageSnapshots.put(commitKey, createEmptyCoverageMap(appId, coverageMap));
        }
    }

    private Map<String, ClassCoverageIndex> createEmptyCoverageMap(String appId,
                                                                   Map<String, ClassCoverageIndex> coverageMap) {
        Map<String, ClassCoverageIndex> commitCoverageMap = new HashMap<>();
        for (Map.Entry<String, ClassCoverageIndex> entry : coverageMap.entrySet()) {
            ClassCoverageIndex emptyClassCoverage = javaStaticCoverageStructureService.createInitialClassCoverage(appId, entry.getKey());
            emptyClassCoverage.setMethods(new ArrayList<>());
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
                emptyClassCoverage.getMethods().add(emptyMethod);
            }
            emptyClassCoverage.setTotalMethods(entry.getValue().getTotalMethods());
            emptyClassCoverage.setTotalLines(entry.getValue().getTotalLines());
            emptyClassCoverage.setTotalBranches(entry.getValue().getTotalBranches());
            emptyClassCoverage.setTotalBranchTargets(entry.getValue().getTotalBranchTargets());
            emptyClassCoverage.setTotalComplexity(entry.getValue().getTotalComplexity());
            commitCoverageMap.put(entry.getKey(), emptyClassCoverage);
        }
        return commitCoverageMap;
    }
}
