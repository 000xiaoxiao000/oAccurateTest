package com.oAT.web.domain.snapshot;

import com.oAT.agent.model.CodeNodeBean;
import com.oAT.agent.model.StackNodeVo;
import com.oAT.agent.model.TraceNode;
import com.oAT.web.esDao.StaticInfoRepository;
import com.oAT.web.esDao.SystemSnapshotRepository;
import com.oAT.web.esDao.entity.CoverageReportIndex;
import com.oAT.web.esDao.entity.StaticSourceInfo;
import com.oAT.web.esDao.entity.StaticSourceMethodInfo;
import com.oAT.web.esDao.entity.SystemSnapshot;
import com.oAT.web.service.SnapshotService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class SystemSnapshotCoverageCalculationService {
    private static final Logger logger = LoggerFactory.getLogger(SystemSnapshotCoverageCalculationService.class);

    @Autowired
    private SystemSnapshotRepository repository;

    @Autowired
    private StaticInfoRepository staticInfoRepository;

    @Autowired
    private SnapshotService snapshotService;

    @Autowired
    private com.oAT.web.coverage.CoverageStorage coverageStorage;

    public void calculateCoverage(String snapshotId) {
        SystemSnapshot snapshot = StringUtils.hasText(snapshotId)
                ? repository.findById(snapshotId).orElse(null)
                : null;
        if (snapshot == null) return;

        try {
            snapshot.setReportStatus(1);
            repository.save(snapshot);

            Collection<TraceNode> traceNodes = snapshotService.getTraceNodes(snapshot.getTraceId());
            Map<String, Map<String, StaticSourceMethodInfo>> staticMethodLookup = buildStaticMethodLookup(snapshot.getAppId());
            CoverageAccumulator accumulator = new CoverageAccumulator();

            for (StackNodeVo stackNode : loadSnapshotCodeNodes(snapshot.getTraceId(), traceNodes)) {
                String methodKey = stackNode.getMethodName() + "#" + stackNode.getMethodDescriptor();
                Map<String, StaticSourceMethodInfo> classMethodMap = staticMethodLookup.get(stackNode.getClassName());
                if (classMethodMap == null) continue;
                StaticSourceMethodInfo staticMethod = classMethodMap.get(methodKey);
                if (staticMethod == null) continue;
                accumulator.add(stackNode, staticMethod, methodKey);
            }

            snapshot.setCoverageReport(accumulator.toReport(snapshot.getAppId()));
            snapshot.setReportStatus(2);
            repository.save(snapshot);
        } catch (Exception e) {
            logger.error("Error calculating coverage for snapshot: " + snapshotId, e);
            snapshot.setReportStatus(3);
            repository.save(snapshot);
        }
    }

    private Map<String, Map<String, StaticSourceMethodInfo>> buildStaticMethodLookup(String appId) {
        List<StaticSourceInfo> staticInfos = staticInfoRepository.findByAppId(appId);
        Map<String, Map<String, StaticSourceMethodInfo>> lookup = new HashMap<>();
        for (StaticSourceInfo staticInfo : staticInfos) {
            if (staticInfo.getClassInfo() == null || staticInfo.getClassInfo().getMethodMaps() == null) {
                continue;
            }
            String className = staticInfo.getClassInfo().getClassName();
            Map<String, StaticSourceMethodInfo> methodMap = new HashMap<>();
            for (Map.Entry<String, StaticSourceMethodInfo> entry : staticInfo.getClassInfo().getMethodMaps().entrySet()) {
                StaticSourceMethodInfo methodInfo = entry.getValue();
                String methodKey = methodInfo.getMethodName() + "#" + methodInfo.getMethodDesc();
                methodMap.put(methodKey, methodInfo);
            }
            lookup.put(className, methodMap);
        }
        return lookup;
    }

    private List<StackNodeVo> loadSnapshotCodeNodes(String traceId, Collection<TraceNode> traceNodes) {
        List<StackNodeVo> snapshotCodeNodes = new ArrayList<>(coverageStorage.load(traceId));
        if (!snapshotCodeNodes.isEmpty()) {
            return snapshotCodeNodes;
        }
        for (TraceNode node : traceNodes) {
            if (node instanceof CodeNodeBean) {
                StackNodeVo[] codeNodes = ((CodeNodeBean) node).getCodeNodes();
                if (codeNodes != null && codeNodes.length > 0) {
                    snapshotCodeNodes.addAll(Arrays.asList(codeNodes));
                }
            }
        }
        return snapshotCodeNodes;
    }

    private static final class CoverageAccumulator {
        private final Set<String> classMethods = new HashSet<>();
        private final Map<String, Set<Integer>> methodTotalLinesMap = new HashMap<>();
        private final Map<String, Set<Integer>> methodCoveredLinesMap = new HashMap<>();
        private final Map<String, Set<Integer>> methodTotalBranchesMap = new HashMap<>();
        private final Map<String, Set<Integer>> methodCoveredBranchesMap = new HashMap<>();
        private final Map<String, Set<String>> methodTotalBranchTargetsMap = new HashMap<>();
        private final Map<String, Set<String>> methodCoveredBranchTargetsMap = new HashMap<>();
        private final Map<String, Integer> methodComplexityMap = new HashMap<>();

        private void add(StackNodeVo stackNode, StaticSourceMethodInfo staticMethod, String methodKey) {
            classMethods.add(stackNode.getClassName());
            methodTotalLinesMap.computeIfAbsent(methodKey, key -> new HashSet<>())
                    .addAll(staticMethod.getMethodLineNumberMap() != null ? staticMethod.getMethodLineNumberMap() : Collections.emptyList());
            if (stackNode.getDoLines() != null) {
                methodCoveredLinesMap.computeIfAbsent(methodKey, key -> new HashSet<>()).addAll(stackNode.getDoLines());
            }

            methodComplexityMap.put(methodKey,
                    staticMethod.getCyclomaticComplexityMap() != null ? staticMethod.getCyclomaticComplexityMap() : 0);
            methodTotalBranchesMap.computeIfAbsent(methodKey, key -> new HashSet<>())
                    .addAll(staticMethod.getBranchLineNumberSet() != null ? staticMethod.getBranchLineNumberSet() : Collections.emptyList());
            addBranchTargetKeys(methodTotalBranchTargetsMap, methodKey,
                    normalizeStaticBranchTargets(staticMethod.getBranchLineAndTargetProbeMap(),
                            stackNode.getExecuteBranchTargetProbeMap()));
            if (stackNode.getExecuteBranch() != null) {
                methodCoveredBranchesMap.computeIfAbsent(methodKey, key -> new HashSet<>()).addAll(stackNode.getExecuteBranch());
            }
            addBranchTargetKeys(methodCoveredBranchTargetsMap, methodKey, stackNode.getExecuteBranchTargetProbeMap());
            if (methodCoveredBranchTargetsMap.containsKey(methodKey)) {
                Set<String> normalizedKeys = new LinkedHashSet<>();
                Map<String, List<Integer>> normalizedStaticBranchTargets = normalizeStaticBranchTargets(
                        staticMethod.getBranchLineAndTargetProbeMap(), stackNode.getExecuteBranchTargetProbeMap());
                addBranchTargetKeysToSet(normalizedKeys, normalizedStaticBranchTargets,
                        decodeBranchTargetKeys(methodCoveredBranchTargetsMap.get(methodKey)));
                methodCoveredBranchTargetsMap.put(methodKey, normalizedKeys);
            }
        }

        private CoverageReportIndex toReport(String appId) {
            long totalLines = 0;
            long coveredLines = 0;
            long coveredMethods = 0;
            long totalBranches = 0;
            long coveredBranches = 0;
            long totalBranchTargets = 0;
            long coveredBranchTargets = 0;
            int totalComplexity = 0;

            for (String methodKey : methodTotalLinesMap.keySet()) {
                totalLines += methodTotalLinesMap.get(methodKey).size();
                coveredLines += methodCoveredLinesMap.getOrDefault(methodKey, Collections.emptySet()).size();
                if (methodCoveredLinesMap.containsKey(methodKey) && !methodCoveredLinesMap.get(methodKey).isEmpty()) {
                    coveredMethods++;
                }
                totalComplexity += methodComplexityMap.getOrDefault(methodKey, 0);
                totalBranches += methodTotalBranchesMap.getOrDefault(methodKey, Collections.emptySet()).size();
                coveredBranches += methodCoveredBranchesMap.getOrDefault(methodKey, Collections.emptySet()).size();
                totalBranchTargets += methodTotalBranchTargetsMap.getOrDefault(methodKey, Collections.emptySet()).size();
                coveredBranchTargets += methodCoveredBranchTargetsMap.getOrDefault(methodKey, Collections.emptySet()).size();
            }

            CoverageReportIndex report = new CoverageReportIndex();
            report.setAppId(appId);
            report.setCreateTime(new Date());
            report.setTotalClasses(classMethods.size());
            report.setCoveredClasses(classMethods.size());
            report.setTotalMethods(methodTotalLinesMap.size());
            report.setCoveredMethods(coveredMethods);
            report.setTotalLines(totalLines);
            report.setCoveredLines(coveredLines);
            report.setTotalBranches(totalBranches);
            report.setCoveredBranches(coveredBranches);
            report.setTotalBranchTargets(totalBranchTargets);
            report.setCoveredBranchTargets(coveredBranchTargets);
            report.setTotalComplexity(totalComplexity);
            return report;
        }

        private void addBranchTargetKeys(Map<String, Set<String>> target,
                                         String methodKey,
                                         Map<String, List<Integer>> branchTargetProbeMap) {
            if (branchTargetProbeMap == null || branchTargetProbeMap.isEmpty()) {
                return;
            }
            Set<String> keys = target.computeIfAbsent(methodKey, key -> new LinkedHashSet<>());
            for (Map.Entry<String, List<Integer>> entry : branchTargetProbeMap.entrySet()) {
                if (entry.getValue() == null) {
                    continue;
                }
                for (Integer branchTarget : entry.getValue()) {
                    if (branchTarget != null) {
                        keys.add(entry.getKey() + "#" + branchTarget);
                    }
                }
            }
        }

        private Map<String, List<Integer>> normalizeStaticBranchTargets(Map<String, List<Integer>> staticBranchTargets,
                                                                        Map<String, List<Integer>> executedBranchTargets) {
            if (staticBranchTargets == null || staticBranchTargets.isEmpty()) {
                return staticBranchTargets;
            }
            if (executedBranchTargets == null || executedBranchTargets.isEmpty()) {
                return staticBranchTargets;
            }

            Map<String, List<Integer>> normalized = new LinkedHashMap<>();
            for (Map.Entry<String, List<Integer>> entry : staticBranchTargets.entrySet()) {
                String branchLine = entry.getKey();
                List<Integer> staticTargets = entry.getValue();
                List<Integer> executedTargets = executedBranchTargets.get(branchLine);
                if (staticTargets == null || staticTargets.isEmpty()) {
                    continue;
                }
                if (executedTargets == null || executedTargets.isEmpty()) {
                    normalized.put(branchLine, new ArrayList<>(new LinkedHashSet<>(staticTargets)));
                    continue;
                }
                Set<Integer> staticSet = new LinkedHashSet<>(staticTargets);
                LinkedHashSet<Integer> executedSet = new LinkedHashSet<>(executedTargets);
                normalized.put(branchLine, new ArrayList<>(staticSet.containsAll(executedSet) ? executedSet : staticSet));
            }
            return normalized.isEmpty() ? staticBranchTargets : normalized;
        }

        private void addBranchTargetKeysToSet(Set<String> target,
                                              Map<String, List<Integer>> allowedBranchTargets,
                                              Map<String, List<Integer>> coveredBranchTargets) {
            if (allowedBranchTargets == null || allowedBranchTargets.isEmpty()
                    || coveredBranchTargets == null || coveredBranchTargets.isEmpty()) {
                return;
            }
            for (Map.Entry<String, List<Integer>> entry : allowedBranchTargets.entrySet()) {
                List<Integer> allowedValues = entry.getValue();
                List<Integer> coveredValues = coveredBranchTargets.get(entry.getKey());
                if (allowedValues == null || allowedValues.isEmpty() || coveredValues == null || coveredValues.isEmpty()) {
                    continue;
                }
                Set<Integer> allowed = new LinkedHashSet<>(allowedValues);
                for (Integer branchTarget : coveredValues) {
                    if (branchTarget != null && allowed.contains(branchTarget)) {
                        target.add(entry.getKey() + "#" + branchTarget);
                    }
                }
            }
        }

        private Map<String, List<Integer>> decodeBranchTargetKeys(Set<String> keys) {
            Map<String, List<Integer>> decoded = new LinkedHashMap<>();
            if (keys == null || keys.isEmpty()) {
                return decoded;
            }
            for (String key : keys) {
                if (!StringUtils.hasText(key)) {
                    continue;
                }
                int split = key.lastIndexOf('#');
                if (split <= 0 || split >= key.length() - 1) {
                    continue;
                }
                try {
                    int branchTarget = Integer.parseInt(key.substring(split + 1));
                    decoded.computeIfAbsent(key.substring(0, split), item -> new ArrayList<>()).add(branchTarget);
                } catch (NumberFormatException ignore) {
                }
            }
            return decoded;
        }
    }
}
