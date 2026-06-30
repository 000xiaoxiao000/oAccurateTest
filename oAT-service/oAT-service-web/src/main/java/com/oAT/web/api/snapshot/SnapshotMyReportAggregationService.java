package com.oAT.web.api.snapshot;

import com.oAT.agent.model.CodeNodeBean;
import com.oAT.agent.model.HttpTraceNode;
import com.oAT.agent.model.StackNodeVo;
import com.oAT.agent.model.TraceNode;
import com.oAT.web.coverage.CoverageStorage;
import com.oAT.web.esDao.StaticInfoRepository;
import com.oAT.web.esDao.entity.StaticSourceInfo;
import com.oAT.web.esDao.entity.StaticSourceMethodInfo;
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
import java.util.stream.Collectors;

import static com.oAT.web.coveragecore.diff.BranchTargetProbeMaps.normalizeMethodBranchTargetProbeMap;

@Service
public class SnapshotMyReportAggregationService {

    private final SnapshotService snapshotService;
    private final StaticInfoRepository staticInfoRepository;
    private final CoverageStorage coverageStorage;

    public SnapshotMyReportAggregationService(SnapshotService snapshotService,
                                              StaticInfoRepository staticInfoRepository,
                                              CoverageStorage coverageStorage) {
        this.snapshotService = snapshotService;
        this.staticInfoRepository = staticInfoRepository;
        this.coverageStorage = coverageStorage;
    }

    public MySnapshotReportAggregate buildMySnapshotReportAggregate(SnapshotVo snapshot) {
        Map<String, Set<Integer>> methodCoveredLines = new HashMap<>();
        Map<String, Set<Integer>> methodTotalLines = new HashMap<>();
        Map<String, Integer> methodComplexity = new HashMap<>();
        Map<String, Set<Integer>> methodTotalBranches = new HashMap<>();
        Map<String, Set<Integer>> methodCoveredBranches = new HashMap<>();
        Map<String, Set<String>> methodTotalBranchTargets = new HashMap<>();
        Map<String, Set<String>> methodCoveredBranchTargets = new HashMap<>();
        Map<String, Set<String>> classMethods = new LinkedHashMap<>();

        String appId = resolveMySnapshotAppId(snapshot);

        TraceNode rootNode = snapshotService.getTraceNode(snapshot.getTraceId(), "0");
        StackNodeVo[] codeNodes = loadCodeNodes(snapshot.getTraceId(), rootNode);
        if (codeNodes != null) {
            for (StackNodeVo codeNode : codeNodes) {
                String methodKey = codeNode.getMethodName() + "#" + codeNode.getMethodDescriptor();
                classMethods.computeIfAbsent(codeNode.getClassName(), key -> new LinkedHashSet<>()).add(methodKey);
                if (codeNode.getDoLines() != null) {
                    methodCoveredLines.computeIfAbsent(methodKey, key -> new LinkedHashSet<>()).addAll(codeNode.getDoLines());
                }
                if (codeNode.getExecuteBranch() != null) {
                    methodCoveredBranches.computeIfAbsent(methodKey, key -> new LinkedHashSet<>()).addAll(codeNode.getExecuteBranch());
                }
                addBranchTargetKeys(methodCoveredBranchTargets, methodKey, codeNode.getExecuteBranchTargetProbeMap(), null);
            }
        }

        if (StringUtils.hasText(appId)) {
            for (StaticSourceInfo staticInfo : staticInfoRepository.findByAppId(appId)) {
                if (staticInfo.getClassInfo() == null || staticInfo.getClassInfo().getMethodMaps() == null) {
                    continue;
                }
                for (StaticSourceMethodInfo methodInfo : staticInfo.getClassInfo().getMethodMaps().values()) {
                    String methodKey = methodInfo.getMethodName() + "#" + methodInfo.getMethodDesc();
                    if (!methodCoveredLines.containsKey(methodKey) && !methodCoveredBranches.containsKey(methodKey)) {
                        continue;
                    }
                    methodTotalLines.computeIfAbsent(methodKey, key -> new LinkedHashSet<>())
                            .addAll(methodInfo.getMethodLineNumberMap() != null ? methodInfo.getMethodLineNumberMap() : Collections.emptyList());
                    methodComplexity.put(methodKey, methodInfo.getCyclomaticComplexityMap() != null ? methodInfo.getCyclomaticComplexityMap() : 0);
                    methodTotalBranches.computeIfAbsent(methodKey, key -> new LinkedHashSet<>())
                            .addAll(methodInfo.getBranchLineNumberSet() != null ? methodInfo.getBranchLineNumberSet() : Collections.emptyList());
                    Map<String, List<Integer>> normalizedTotalBranchTargetProbeMap = normalizeMethodBranchTargetProbeMap(
                            methodInfo.getBranchLineAndTargetProbeMap(),
                            decodeBranchTargetKeys(methodCoveredBranchTargets.get(methodKey)));
                    addBranchTargetKeys(methodTotalBranchTargets, methodKey, normalizedTotalBranchTargetProbeMap, null);
                    if (methodCoveredBranchTargets.containsKey(methodKey)) {
                        Set<String> normalizedKeys = new LinkedHashSet<>();
                        addBranchTargetKeysToSet(normalizedKeys, normalizedTotalBranchTargetProbeMap,
                                decodeBranchTargetKeys(methodCoveredBranchTargets.get(methodKey)));
                        methodCoveredBranchTargets.put(methodKey, normalizedKeys);
                    }
                }
            }
        }

        List<ClassCoverageSummary> classStats = buildClassStats(classMethods, methodCoveredLines, methodTotalLines,
                methodComplexity, methodTotalBranches, methodCoveredBranches, methodTotalBranchTargets, methodCoveredBranchTargets);

        CoverageReportSummary report = new CoverageReportSummary();
        report.setAppId(appId);
        report.setSnapshotCount(1);
        fillReportSummary(report, classStats, methodCoveredLines, methodTotalLines, methodComplexity,
                methodTotalBranches, methodCoveredBranches, methodTotalBranchTargets, methodCoveredBranchTargets);
        return new MySnapshotReportAggregate(report, classStats);
    }

    public MySnapshotCodeReportAggregate buildMySnapshotsCodeReportAggregate(List<SnapshotVo> snapshots) {
        Map<String, Map<String, List<MySnapshotCodeRelationshipMethodSummary>>> codeRelationships = new LinkedHashMap<>();
        Map<String, Set<Integer>> methodCoveredLines = new HashMap<>();
        Map<String, Set<Integer>> methodTotalLines = new HashMap<>();
        Map<String, Integer> methodComplexity = new HashMap<>();
        Map<String, Set<Integer>> methodTotalBranches = new HashMap<>();
        Map<String, Set<Integer>> methodCoveredBranches = new HashMap<>();
        Map<String, Set<String>> methodTotalBranchTargets = new HashMap<>();
        Map<String, Set<String>> methodCoveredBranchTargets = new HashMap<>();
        Map<String, Set<String>> classMethods = new LinkedHashMap<>();
        Map<String, String> classToAppId = new LinkedHashMap<>();

        for (SnapshotVo snapshot : snapshots) {
            TraceNode traceNode = snapshotService.getTraceNode(snapshot.getTraceId(), "0");
            StackNodeVo[] codeNodes = loadCodeNodes(snapshot.getTraceId(), traceNode);
            if (codeNodes == null || codeNodes.length == 0) {
                continue;
            }

            String requestUrl = resolveEntryGroupName(traceNode);
            Map<String, List<MySnapshotCodeRelationshipMethodSummary>> childNodes =
                    codeRelationships.computeIfAbsent(requestUrl, key -> new LinkedHashMap<>());

            String currentAppId = snapshot.getAppId();
            if (!StringUtils.hasText(currentAppId) && traceNode.getApp() != null) {
                currentAppId = traceNode.getApp().getAppId();
            }

            for (StackNodeVo node : codeNodes) {
                if (node == null) {
                    continue;
                }
                String methodKey = node.getMethodName() + "#" + node.getMethodDescriptor();
                classMethods.computeIfAbsent(node.getClassName(), key -> new LinkedHashSet<>()).add(methodKey);
                if (StringUtils.hasText(currentAppId)) {
                    classToAppId.putIfAbsent(node.getClassName(), currentAppId);
                }

                childNodes.computeIfAbsent(node.parentId(), key -> new ArrayList<>())
                        .add(new MySnapshotCodeRelationshipMethodSummary(
                                node.getClassName(),
                                node.getMethodName(),
                                node.getMethodDescriptor(),
                                node.getDoLines() == null ? new ArrayList<>() : new ArrayList<>(node.getDoLines())));

                if (node.getDoLines() != null) {
                    methodCoveredLines.computeIfAbsent(methodKey, key -> new LinkedHashSet<>()).addAll(node.getDoLines());
                }
                if (node.getExecuteBranch() != null) {
                    methodCoveredBranches.computeIfAbsent(methodKey, key -> new LinkedHashSet<>()).addAll(node.getExecuteBranch());
                }
                addBranchTargetKeys(methodCoveredBranchTargets, methodKey, node.getExecuteBranchTargetProbeMap(), null);
            }
        }

        for (String appId : new LinkedHashSet<>(classToAppId.values())) {
            if (!StringUtils.hasText(appId)) {
                continue;
            }
            for (StaticSourceInfo staticInfo : staticInfoRepository.findByAppId(appId)) {
                if (staticInfo.getClassInfo() == null || staticInfo.getClassInfo().getMethodMaps() == null) {
                    continue;
                }
                for (StaticSourceMethodInfo methodInfo : staticInfo.getClassInfo().getMethodMaps().values()) {
                    String methodKey = methodInfo.getMethodName() + "#" + methodInfo.getMethodDesc();
                    if (!methodCoveredLines.containsKey(methodKey) && !methodCoveredBranches.containsKey(methodKey)) {
                        continue;
                    }
                    methodTotalLines.computeIfAbsent(methodKey, key -> new LinkedHashSet<>())
                            .addAll(methodInfo.getMethodLineNumberMap() != null ? methodInfo.getMethodLineNumberMap() : Collections.emptyList());
                    methodComplexity.put(methodKey, methodInfo.getCyclomaticComplexityMap() != null ? methodInfo.getCyclomaticComplexityMap() : 0);
                    methodTotalBranches.computeIfAbsent(methodKey, key -> new LinkedHashSet<>())
                            .addAll(methodInfo.getBranchLineNumberSet() != null ? methodInfo.getBranchLineNumberSet() : Collections.emptyList());
                    Map<String, List<Integer>> normalizedTotalBranchTargetProbeMap = normalizeMethodBranchTargetProbeMap(
                            methodInfo.getBranchLineAndTargetProbeMap(),
                            decodeBranchTargetKeys(methodCoveredBranchTargets.get(methodKey)));
                    addBranchTargetKeys(methodTotalBranchTargets, methodKey, normalizedTotalBranchTargetProbeMap, null);
                    if (methodCoveredBranchTargets.containsKey(methodKey)) {
                        Set<String> normalizedKeys = new LinkedHashSet<>();
                        addBranchTargetKeysToSet(normalizedKeys, normalizedTotalBranchTargetProbeMap,
                                decodeBranchTargetKeys(methodCoveredBranchTargets.get(methodKey)));
                        methodCoveredBranchTargets.put(methodKey, normalizedKeys);
                    }
                }
            }
        }

        enrichRelationshipCounts(codeRelationships, methodCoveredLines, methodTotalLines, methodTotalBranches,
                methodCoveredBranches, methodTotalBranchTargets, methodCoveredBranchTargets);

        List<ClassCoverageSummary> classStats = buildClassStats(classMethods, methodCoveredLines, methodTotalLines,
                methodComplexity, methodTotalBranches, methodCoveredBranches, methodTotalBranchTargets, methodCoveredBranchTargets);

        CoverageReportSummary report = new CoverageReportSummary();
        report.setAppId(classToAppId.values().stream().filter(StringUtils::hasText).findFirst().orElse(null));
        report.setSnapshotCount(snapshots.size());
        fillReportSummary(report, classStats, methodCoveredLines, methodTotalLines, methodComplexity,
                methodTotalBranches, methodCoveredBranches, methodTotalBranchTargets, methodCoveredBranchTargets);

        List<MySnapshotCodeRelationshipGroupSummary> groups = buildRelationshipGroups(codeRelationships);
        return new MySnapshotCodeReportAggregate(report, classStats, groups, report.getAppId());
    }

    public String resolveMySnapshotAppId(SnapshotVo snapshot) {
        String appId = snapshot.getAppId();
        if (StringUtils.hasText(appId)) {
            return appId;
        }
        TraceNode traceNode = snapshotService.getTraceNode(snapshot.getTraceId(), "0");
        if (traceNode != null && traceNode.getApp() != null) {
            return traceNode.getApp().getAppId();
        }
        return null;
    }

    private List<ClassCoverageSummary> buildClassStats(Map<String, Set<String>> classMethods,
                                                       Map<String, Set<Integer>> methodCoveredLines,
                                                       Map<String, Set<Integer>> methodTotalLines,
                                                       Map<String, Integer> methodComplexity,
                                                       Map<String, Set<Integer>> methodTotalBranches,
                                                       Map<String, Set<Integer>> methodCoveredBranches,
                                                       Map<String, Set<String>> methodTotalBranchTargets,
                                                       Map<String, Set<String>> methodCoveredBranchTargets) {
        List<ClassCoverageSummary> classStats = new ArrayList<>();
        for (Map.Entry<String, Set<String>> entry : classMethods.entrySet()) {
            String className = entry.getKey();
            Set<String> methods = entry.getValue();
            long totalMethods = methods.size();
            long coveredMethods = 0;
            long totalLines = 0;
            long coveredLines = 0;
            long totalBranches = 0;
            long coveredBranches = 0;
            long totalBranchTargets = 0;
            long coveredBranchTargets = 0;
            int totalComplexity = 0;

            for (String methodKey : methods) {
                totalLines += methodTotalLines.getOrDefault(methodKey, Collections.emptySet()).size();
                coveredLines += methodCoveredLines.getOrDefault(methodKey, Collections.emptySet()).size();
                if (methodCoveredLines.containsKey(methodKey) && !methodCoveredLines.get(methodKey).isEmpty()) {
                    coveredMethods++;
                }
                totalComplexity += methodComplexity.getOrDefault(methodKey, 0);
                totalBranches += methodTotalBranches.getOrDefault(methodKey, Collections.emptySet()).size();
                coveredBranches += methodCoveredBranches.getOrDefault(methodKey, Collections.emptySet()).size();
                totalBranchTargets += methodTotalBranchTargets.getOrDefault(methodKey, Collections.emptySet()).size();
                coveredBranchTargets += methodCoveredBranchTargets.getOrDefault(methodKey, Collections.emptySet()).size();
            }

            ClassCoverageSummary summary = new ClassCoverageSummary();
            summary.setClassName(className);
            summary.setTotalMethods(totalMethods);
            summary.setCoveredMethods(coveredMethods);
            summary.setTotalLines(totalLines);
            summary.setCoveredLines(coveredLines);
            summary.setTotalBranches(totalBranches);
            summary.setCoveredBranches(coveredBranches);
            summary.setTotalBranchTargets(totalBranchTargets);
            summary.setCoveredBranchTargets(coveredBranchTargets);
            summary.setBranchRate(totalBranchTargets > 0 ? coveredBranchTargets * 100.0 / totalBranchTargets : 0);
            summary.setTotalComplexity(totalComplexity);
            classStats.add(summary);
        }

        classStats.sort((left, right) -> {
            double leftRate = left.getTotalLines() > 0 ? (double) left.getCoveredLines() / left.getTotalLines() : 0;
            double rightRate = right.getTotalLines() > 0 ? (double) right.getCoveredLines() / right.getTotalLines() : 0;
            int compare = Double.compare(rightRate, leftRate);
            if (compare != 0) {
                return compare;
            }
            return safeString(left.getClassName()).compareToIgnoreCase(safeString(right.getClassName()));
        });
        return classStats;
    }

    private void fillReportSummary(CoverageReportSummary report,
                                   List<ClassCoverageSummary> classStats,
                                   Map<String, Set<Integer>> methodCoveredLines,
                                   Map<String, Set<Integer>> methodTotalLines,
                                   Map<String, Integer> methodComplexity,
                                   Map<String, Set<Integer>> methodTotalBranches,
                                   Map<String, Set<Integer>> methodCoveredBranches,
                                   Map<String, Set<String>> methodTotalBranchTargets,
                                   Map<String, Set<String>> methodCoveredBranchTargets) {
        report.setTotalClasses(classStats.size());
        report.setCoveredClasses(classStats.size());
        report.setTotalMethods(methodTotalLines.size());
        report.setCoveredMethods(methodCoveredLines.values().stream().filter(lines -> !lines.isEmpty()).count());
        report.setTotalLines(methodTotalLines.values().stream().mapToLong(Set::size).sum());
        report.setCoveredLines(methodCoveredLines.values().stream().mapToLong(Set::size).sum());
        report.setTotalBranches(methodTotalBranches.values().stream().mapToLong(Set::size).sum());
        report.setCoveredBranches(methodCoveredBranches.values().stream().mapToLong(Set::size).sum());
        report.setTotalBranchTargets(methodTotalBranchTargets.values().stream().mapToLong(Set::size).sum());
        report.setCoveredBranchTargets(methodCoveredBranchTargets.values().stream().mapToLong(Set::size).sum());
        report.setTotalComplexity(methodComplexity.values().stream().mapToInt(Integer::intValue).sum());
    }

    private void enrichRelationshipCounts(Map<String, Map<String, List<MySnapshotCodeRelationshipMethodSummary>>> codeRelationships,
                                          Map<String, Set<Integer>> methodCoveredLines,
                                          Map<String, Set<Integer>> methodTotalLines,
                                          Map<String, Set<Integer>> methodTotalBranches,
                                          Map<String, Set<Integer>> methodCoveredBranches,
                                          Map<String, Set<String>> methodTotalBranchTargets,
                                          Map<String, Set<String>> methodCoveredBranchTargets) {
        for (Map<String, List<MySnapshotCodeRelationshipMethodSummary>> groups : codeRelationships.values()) {
            for (List<MySnapshotCodeRelationshipMethodSummary> methods : groups.values()) {
                for (MySnapshotCodeRelationshipMethodSummary method : methods) {
                    String methodKey = method.getMethodName() + "#" + method.getMethodDescriptor();
                    method.setLineTotalCount(methodTotalLines.getOrDefault(methodKey, Collections.emptySet()).size());
                    method.setCoveredLineCount(methodCoveredLines.getOrDefault(methodKey, Collections.emptySet()).size());
                    method.setBranchTotalCount(methodTotalBranches.getOrDefault(methodKey, Collections.emptySet()).size());
                    method.setBranchCoveredCount(methodCoveredBranches.getOrDefault(methodKey, Collections.emptySet()).size());
                    method.setBranchTargetTotalCount(methodTotalBranchTargets.getOrDefault(methodKey, Collections.emptySet()).size());
                    method.setBranchTargetCoveredCount(methodCoveredBranchTargets.getOrDefault(methodKey, Collections.emptySet()).size());
                }
            }
        }
    }

    private List<MySnapshotCodeRelationshipGroupSummary> buildRelationshipGroups(Map<String, Map<String, List<MySnapshotCodeRelationshipMethodSummary>>> codeRelationships) {
        List<MySnapshotCodeRelationshipGroupSummary> groups = new ArrayList<>();
        for (Map.Entry<String, Map<String, List<MySnapshotCodeRelationshipMethodSummary>>> entry : codeRelationships.entrySet()) {
            List<MySnapshotCodeRelationshipMethodSummary> methods = entry.getValue().values().stream()
                    .flatMap(List::stream)
                    .collect(Collectors.toList());
            methods.sort((left, right) -> {
                double leftRate = left.getLineTotalCount() > 0 ? (double) left.getCoveredLineCount() / left.getLineTotalCount() : 0;
                double rightRate = right.getLineTotalCount() > 0 ? (double) right.getCoveredLineCount() / right.getLineTotalCount() : 0;
                int compare = Double.compare(rightRate, leftRate);
                if (compare != 0) {
                    return compare;
                }
                return safeString(left.getClassName()).compareToIgnoreCase(safeString(right.getClassName()));
            });
            MySnapshotCodeRelationshipGroupSummary group = new MySnapshotCodeRelationshipGroupSummary();
            group.setRequestUrl(entry.getKey());
            group.setMethods(methods);
            groups.add(group);
        }
        groups.sort((left, right) -> safeString(left.getRequestUrl()).compareToIgnoreCase(safeString(right.getRequestUrl())));
        return groups;
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

    private String resolveEntryGroupName(TraceNode traceNode) {
        if (traceNode instanceof HttpTraceNode) {
            return ((HttpTraceNode) traceNode).getRequestUrl();
        }
        if (traceNode == null) {
            return "unknown";
        }
        return traceNode.toType();
    }

    private Map<String, List<Integer>> decodeBranchTargetKeys(Set<String> encodedKeys) {
        if (encodedKeys == null || encodedKeys.isEmpty()) {
            return new LinkedHashMap<>();
        }
        Map<String, List<Integer>> decoded = new LinkedHashMap<>();
        for (String key : encodedKeys) {
            if (!StringUtils.hasText(key) || !key.contains(":")) {
                continue;
            }
            int index = key.indexOf(':');
            String branchLine = key.substring(0, index);
            String targetProbe = key.substring(index + 1);
            if (!StringUtils.hasText(branchLine) || !StringUtils.hasText(targetProbe)) {
                continue;
            }
            try {
                decoded.computeIfAbsent(branchLine, value -> new ArrayList<>()).add(Integer.parseInt(targetProbe));
            } catch (NumberFormatException ignored) {
            }
        }
        return decoded;
    }

    private void addBranchTargetKeys(Map<String, Set<String>> target,
                                     String methodKey,
                                     Map<String, List<Integer>> branchTargetProbeMap,
                                     Map<String, List<Integer>> fallbackBranchTargetProbeMap) {
        Map<String, List<Integer>> source = branchTargetProbeMap;
        if ((source == null || source.isEmpty()) && fallbackBranchTargetProbeMap != null && !fallbackBranchTargetProbeMap.isEmpty()) {
            source = fallbackBranchTargetProbeMap;
        }
        if (source == null || source.isEmpty()) {
            return;
        }
        Set<String> keys = target.computeIfAbsent(methodKey, key -> new LinkedHashSet<>());
        addBranchTargetKeysToSet(keys, source, null);
    }

    private void addBranchTargetKeysToSet(Set<String> target,
                                          Map<String, List<Integer>> branchTargetProbeMap,
                                          Map<String, List<Integer>> fallbackBranchTargetProbeMap) {
        Map<String, List<Integer>> source = branchTargetProbeMap;
        if ((source == null || source.isEmpty()) && fallbackBranchTargetProbeMap != null && !fallbackBranchTargetProbeMap.isEmpty()) {
            source = fallbackBranchTargetProbeMap;
        }
        if (source == null || source.isEmpty()) {
            return;
        }
        for (Map.Entry<String, List<Integer>> entry : source.entrySet()) {
            if (!StringUtils.hasText(entry.getKey()) || entry.getValue() == null) {
                continue;
            }
            for (Integer probe : entry.getValue()) {
                if (probe != null) {
                    target.add(entry.getKey() + ":" + probe);
                }
            }
        }
    }

    private String safeString(String value) {
        return value == null ? "" : value;
    }
}
