package com.oAT.web.api.snapshot;

import com.oAT.agent.model.CodeNodeBean;
import com.oAT.agent.model.StackNodeVo;
import com.oAT.agent.model.TraceNode;
import com.oAT.web.coverage.CoverageStorage;
import com.oAT.web.esDao.StaticInfoRepository;
import com.oAT.web.esDao.entity.ClassCoverageIndex;
import com.oAT.web.esDao.entity.StaticSourceInfo;
import com.oAT.web.esDao.entity.StaticSourceMethodInfo;
import com.oAT.web.esDao.entity.SystemSnapshot;
import com.oAT.web.service.SnapshotService;
import com.oAT.web.service.entity.SnapshotVo;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.oAT.web.coveragecore.diff.BranchTargetProbeMaps.calculateBranchRate;
import static com.oAT.web.coveragecore.diff.BranchTargetProbeMaps.countBranchTargets;
import static com.oAT.web.coveragecore.diff.BranchTargetProbeMaps.mergeBranchTargetProbeMap;
import static com.oAT.web.coveragecore.diff.BranchTargetProbeMaps.normalizeCoveredBranchTargetProbeMap;
import static com.oAT.web.coveragecore.diff.BranchTargetProbeMaps.normalizeMethodBranchTargetProbeMap;

@Service
public class SnapshotClassCoverageBuilder {

    private final SnapshotService snapshotService;
    private final StaticInfoRepository staticInfoRepository;
    private final CoverageStorage coverageStorage;

    public SnapshotClassCoverageBuilder(SnapshotService snapshotService,
                                        StaticInfoRepository staticInfoRepository,
                                        CoverageStorage coverageStorage) {
        this.snapshotService = snapshotService;
        this.staticInfoRepository = staticInfoRepository;
        this.coverageStorage = coverageStorage;
    }

    public ClassCoverageIndex buildSystemSnapshotClassCoverage(String appId, SystemSnapshot snapshot, String className) {
        ClassCoverageIndex aggregatedClassCoverage = initialClassCoverage(appId, className);
        Map<String, StaticSourceMethodInfo> classStaticMethods = loadClassStaticMethods(appId, className);
        Map<String, ClassCoverageIndex.MethodCoverageDetail> methodMap = new LinkedHashMap<>();
        for (TraceNode node : snapshotService.getTraceNodes(snapshot.getTraceId())) {
            mergeClassCodeNodes(className, classStaticMethods, methodMap, loadCodeNodes(snapshot.getTraceId(), node));
        }
        finishClassCoverage(aggregatedClassCoverage, methodMap);
        return aggregatedClassCoverage;
    }

    public ClassCoverageIndex buildMySnapshotsClassCoverage(String appId, List<SnapshotVo> snapshots, String className) {
        ClassCoverageIndex aggregatedClassCoverage = initialClassCoverage(appId, className);
        Map<String, StaticSourceMethodInfo> classStaticMethods = loadClassStaticMethods(appId, className);
        Map<String, ClassCoverageIndex.MethodCoverageDetail> methodMap = new LinkedHashMap<>();
        for (SnapshotVo snapshot : snapshots) {
            TraceNode traceNode = snapshotService.getTraceNode(snapshot.getTraceId(), "0");
            mergeClassCodeNodes(className, classStaticMethods, methodMap, loadCodeNodes(snapshot.getTraceId(), traceNode));
        }
        finishClassCoverage(aggregatedClassCoverage, methodMap);
        return aggregatedClassCoverage;
    }

    public ClassCoverageIndex buildMySnapshotClassCoverage(String appId, SnapshotVo snapshot, String className) {
        ClassCoverageIndex aggregatedClassCoverage = initialClassCoverage(appId, className);
        Map<String, StaticSourceMethodInfo> classStaticMethods = loadClassStaticMethods(appId, className);
        Map<String, ClassCoverageIndex.MethodCoverageDetail> methodMap = new LinkedHashMap<>();
        for (TraceNode node : snapshotService.getTraceNodes(snapshot.getTraceId())) {
            mergeClassCodeNodes(className, classStaticMethods, methodMap, loadCodeNodes(snapshot.getTraceId(), node));
        }
        finishClassCoverage(aggregatedClassCoverage, methodMap);
        return aggregatedClassCoverage;
    }

    private ClassCoverageIndex initialClassCoverage(String appId, String className) {
        ClassCoverageIndex classCoverage = new ClassCoverageIndex();
        classCoverage.setClassName(className);
        classCoverage.setAppId(appId);
        return classCoverage;
    }

    private Map<String, StaticSourceMethodInfo> loadClassStaticMethods(String appId, String className) {
        List<StaticSourceInfo> staticInfos = StringUtils.hasText(appId) ? staticInfoRepository.findByAppId(appId) : Collections.emptyList();
        Map<String, StaticSourceMethodInfo> classStaticMethods = new HashMap<>();
        for (StaticSourceInfo staticInfo : staticInfos) {
            if (staticInfo.getClassInfo() == null
                    || !className.equals(staticInfo.getClassInfo().getClassName())
                    || staticInfo.getClassInfo().getMethodMaps() == null) {
                continue;
            }
            for (StaticSourceMethodInfo methodInfo : staticInfo.getClassInfo().getMethodMaps().values()) {
                classStaticMethods.put(methodInfo.getMethodName() + "#" + methodInfo.getMethodDesc(), methodInfo);
            }
            break;
        }
        return classStaticMethods;
    }

    private void mergeClassCodeNodes(String className,
                                     Map<String, StaticSourceMethodInfo> classStaticMethods,
                                     Map<String, ClassCoverageIndex.MethodCoverageDetail> methodMap,
                                     StackNodeVo[] codeNodes) {
        if (codeNodes == null) {
            return;
        }
        for (StackNodeVo codeNode : codeNodes) {
            if (codeNode == null || !className.equals(codeNode.getClassName())) {
                continue;
            }
            String methodKey = codeNode.getMethodName() + "#" + codeNode.getMethodDescriptor();
            ClassCoverageIndex.MethodCoverageDetail detail = methodMap.computeIfAbsent(methodKey,
                    key -> initialMethodCoverage(codeNode, classStaticMethods.get(methodKey)));
            mergeExecutedCoverage(detail, codeNode);
        }
    }

    private ClassCoverageIndex.MethodCoverageDetail initialMethodCoverage(StackNodeVo codeNode,
                                                                          StaticSourceMethodInfo staticMethod) {
        ClassCoverageIndex.MethodCoverageDetail detail = new ClassCoverageIndex.MethodCoverageDetail();
        detail.setMethodName(codeNode.getMethodName());
        detail.setMethodDesc(codeNode.getMethodDescriptor());
        List<Integer> totalLines = staticMethod != null && staticMethod.getMethodLineNumberMap() != null
                ? staticMethod.getMethodLineNumberMap()
                : Collections.emptyList();
        detail.setTotalLineNumbers(new ArrayList<>(totalLines));
        detail.setTotalLines(totalLines.size());
        detail.setTotalBranches(staticMethod != null && staticMethod.getTotalBranchCount() != null
                ? staticMethod.getTotalBranchCount()
                : 0);
        Map<String, List<Integer>> totalBranchTargets = normalizeMethodBranchTargetProbeMap(
                staticMethod != null ? staticMethod.getBranchLineAndTargetProbeMap() : null,
                codeNode.getExecuteBranchTargetProbeMap());
        detail.setTotalBranchTargetProbeMap(totalBranchTargets);
        detail.setTotalBranchTargets(countBranchTargets(totalBranchTargets));
        detail.setCoveredBranchTargetProbeMap(new LinkedHashMap<>());
        detail.setCoveredBranchTargets(0);
        detail.setBranchRate(0.0);
        detail.setComplexity(staticMethod != null && staticMethod.getCyclomaticComplexityMap() != null
                ? staticMethod.getCyclomaticComplexityMap()
                : 0);
        detail.setCoveredLineNumbers(new ArrayList<>());
        detail.setCoveredBranchLines(new ArrayList<>());
        return detail;
    }

    private void mergeExecutedCoverage(ClassCoverageIndex.MethodCoverageDetail detail, StackNodeVo codeNode) {
        if (codeNode.getDoLines() != null) {
            Set<Integer> covered = new LinkedHashSet<>(detail.getCoveredLineNumbers());
            covered.addAll(codeNode.getDoLines());
            detail.setCoveredLineNumbers(new ArrayList<>(covered));
            detail.setCoveredLines(detail.getCoveredLineNumbers().size());
            detail.setCovered(detail.getCoveredLines() > 0);
        }
        if (codeNode.getExecuteBranch() != null) {
            Set<Integer> coveredBranchLines = new LinkedHashSet<>(detail.getCoveredBranchLines());
            coveredBranchLines.addAll(codeNode.getExecuteBranch());
            detail.setCoveredBranchLines(new ArrayList<>(coveredBranchLines));
            detail.setCoveredBranches(detail.getCoveredBranchLines().size());
        }
        if (codeNode.getExecuteBranchTargetProbeMap() != null) {
            Map<String, List<Integer>> coveredBranchTargetProbeMap = mergeBranchTargetProbeMap(
                    detail.getCoveredBranchTargetProbeMap(),
                    codeNode.getExecuteBranchTargetProbeMap());
            Map<String, List<Integer>> normalizedTotalBranchTargetProbeMap = normalizeMethodBranchTargetProbeMap(
                    detail.getTotalBranchTargetProbeMap(),
                    coveredBranchTargetProbeMap);
            coveredBranchTargetProbeMap = normalizeCoveredBranchTargetProbeMap(
                    normalizedTotalBranchTargetProbeMap,
                    coveredBranchTargetProbeMap);
            detail.setTotalBranchTargetProbeMap(normalizedTotalBranchTargetProbeMap);
            detail.setTotalBranchTargets(countBranchTargets(normalizedTotalBranchTargetProbeMap));
            detail.setCoveredBranchTargetProbeMap(coveredBranchTargetProbeMap);
            detail.setCoveredBranchTargets(countBranchTargets(coveredBranchTargetProbeMap));
            detail.setBranchRate(calculateBranchRate(detail.getCoveredBranchTargets(), detail.getTotalBranchTargets()));
        }
    }

    private void finishClassCoverage(ClassCoverageIndex aggregatedClassCoverage,
                                     Map<String, ClassCoverageIndex.MethodCoverageDetail> methodMap) {
        List<ClassCoverageIndex.MethodCoverageDetail> methods = new ArrayList<>(methodMap.values());
        methods.sort((left, right) -> {
            double leftRate = left.getTotalLines() > 0 ? (double) left.getCoveredLines() / left.getTotalLines() : 0;
            double rightRate = right.getTotalLines() > 0 ? (double) right.getCoveredLines() / right.getTotalLines() : 0;
            int compare = Double.compare(rightRate, leftRate);
            if (compare != 0) {
                return compare;
            }
            return safeString(left.getMethodName()).compareToIgnoreCase(safeString(right.getMethodName()));
        });

        aggregatedClassCoverage.setMethods(methods);
        aggregatedClassCoverage.setTotalMethods(methods.size());
        aggregatedClassCoverage.setCoveredMethods((int) methods.stream().filter(ClassCoverageIndex.MethodCoverageDetail::isCovered).count());
        aggregatedClassCoverage.setTotalLines(methods.stream().mapToInt(ClassCoverageIndex.MethodCoverageDetail::getTotalLines).sum());
        aggregatedClassCoverage.setCoveredLines(methods.stream().mapToInt(ClassCoverageIndex.MethodCoverageDetail::getCoveredLines).sum());
        aggregatedClassCoverage.setTotalBranches(methods.stream().mapToInt(ClassCoverageIndex.MethodCoverageDetail::getTotalBranches).sum());
        aggregatedClassCoverage.setCoveredBranches(methods.stream().mapToInt(ClassCoverageIndex.MethodCoverageDetail::getCoveredBranches).sum());
        aggregatedClassCoverage.setTotalBranchTargets(methods.stream().mapToInt(ClassCoverageIndex.MethodCoverageDetail::getTotalBranchTargets).sum());
        aggregatedClassCoverage.setCoveredBranchTargets(methods.stream().mapToInt(ClassCoverageIndex.MethodCoverageDetail::getCoveredBranchTargets).sum());
        aggregatedClassCoverage.setTotalComplexity(methods.stream().mapToInt(ClassCoverageIndex.MethodCoverageDetail::getComplexity).sum());
        aggregatedClassCoverage.setMethodRate(calculateRate(aggregatedClassCoverage.getCoveredMethods(), aggregatedClassCoverage.getTotalMethods()));
        aggregatedClassCoverage.setLineRate(calculateRate(aggregatedClassCoverage.getCoveredLines(), aggregatedClassCoverage.getTotalLines()));
        aggregatedClassCoverage.setBranchRate(calculateRate(aggregatedClassCoverage.getCoveredBranchTargets(), aggregatedClassCoverage.getTotalBranchTargets()));
    }

    private StackNodeVo[] loadCodeNodes(String traceId, TraceNode node) {
        if (StringUtils.hasText(traceId)) {
            List<StackNodeVo> stored = coverageStorage.load(traceId);
            if (stored != null && !stored.isEmpty()) {
                if (node == null || "0".equals(node.getTraceNodeId())) {
                    return stored.toArray(new StackNodeVo[0]);
                }
                return null;
            }
        }
        if (node instanceof CodeNodeBean) {
            return ((CodeNodeBean) node).getCodeNodes();
        }
        return null;
    }

    private double calculateRate(long covered, long total) {
        if (total <= 0) {
            return 0;
        }
        return covered * 100.0 / total;
    }

    private String safeString(String value) {
        return value == null ? "" : value;
    }
}
