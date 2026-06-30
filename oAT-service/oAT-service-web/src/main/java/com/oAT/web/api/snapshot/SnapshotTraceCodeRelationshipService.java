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
public class SnapshotTraceCodeRelationshipService {

    private final SnapshotService snapshotService;
    private final CoverageStorage coverageStorage;
    private final StaticInfoRepository staticInfoRepository;

    public SnapshotTraceCodeRelationshipService(SnapshotService snapshotService,
                                                CoverageStorage coverageStorage,
                                                StaticInfoRepository staticInfoRepository) {
        this.snapshotService = snapshotService;
        this.coverageStorage = coverageStorage;
        this.staticInfoRepository = staticInfoRepository;
    }

    public List<MySnapshotCodeRelationshipGroupSummary> buildTraceCodeRelationships(String traceId, String appId) {
        if (!StringUtils.hasText(traceId)) {
            return new ArrayList<>();
        }
        TraceNode traceNode = snapshotService.getTraceNode(traceId, "0");
        StackNodeVo[] codeNodes = loadCodeNodes(traceId, traceNode);
        if (codeNodes == null || codeNodes.length == 0) {
            return new ArrayList<>();
        }

        String currentAppId = StringUtils.hasText(appId) ? appId : null;
        if (!StringUtils.hasText(currentAppId) && traceNode.getApp() != null) {
            currentAppId = traceNode.getApp().getAppId();
        }

        Map<String, Map<String, List<MySnapshotCodeRelationshipMethodSummary>>> codeRelationships = new LinkedHashMap<>();
        Map<String, Set<Integer>> methodCoveredLines = new HashMap<>();
        Map<String, Set<Integer>> methodTotalLines = new HashMap<>();
        Map<String, Set<Integer>> methodTotalBranches = new HashMap<>();
        Map<String, Set<Integer>> methodCoveredBranches = new HashMap<>();
        Map<String, Set<String>> methodTotalBranchTargets = new HashMap<>();
        Map<String, Set<String>> methodCoveredBranchTargets = new HashMap<>();

        String requestUrl = resolveEntryGroupName(traceNode);
        Map<String, List<MySnapshotCodeRelationshipMethodSummary>> childNodes =
                codeRelationships.computeIfAbsent(requestUrl, key -> new LinkedHashMap<>());

        for (StackNodeVo node : codeNodes) {
            if (node == null || !StringUtils.hasText(node.getClassName())) {
                continue;
            }
            String methodKey = relationshipMethodKey(node.getClassName(), node.getMethodName(), node.getMethodDescriptor());
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

        if (StringUtils.hasText(currentAppId)) {
            for (StaticSourceInfo staticInfo : staticInfoRepository.findByAppId(currentAppId)) {
                if (staticInfo.getClassInfo() == null || staticInfo.getClassInfo().getMethodMaps() == null) {
                    continue;
                }
                String className = staticInfo.getClassInfo().getClassName();
                for (StaticSourceMethodInfo methodInfo : staticInfo.getClassInfo().getMethodMaps().values()) {
                    String methodKey = relationshipMethodKey(className, methodInfo.getMethodName(), methodInfo.getMethodDesc());
                    if (!methodCoveredLines.containsKey(methodKey) && !methodCoveredBranches.containsKey(methodKey)) {
                        continue;
                    }
                    methodTotalLines.computeIfAbsent(methodKey, key -> new LinkedHashSet<>())
                            .addAll(methodInfo.getMethodLineNumberMap() != null ? methodInfo.getMethodLineNumberMap() : Collections.emptyList());
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

        for (Map<String, List<MySnapshotCodeRelationshipMethodSummary>> groups : codeRelationships.values()) {
            groups.values().stream().flatMap(List::stream).forEach(method -> {
                String methodKey = relationshipMethodKey(method.getClassName(), method.getMethodName(), method.getMethodDescriptor());
                method.setLineTotalCount(methodTotalLines.getOrDefault(methodKey, Collections.emptySet()).size());
                method.setCoveredLineCount(methodCoveredLines.getOrDefault(methodKey, Collections.emptySet()).size());
                method.setBranchTotalCount(methodTotalBranches.getOrDefault(methodKey, Collections.emptySet()).size());
                method.setBranchCoveredCount(methodCoveredBranches.getOrDefault(methodKey, Collections.emptySet()).size());
                method.setBranchTargetTotalCount(methodTotalBranchTargets.getOrDefault(methodKey, Collections.emptySet()).size());
                method.setBranchTargetCoveredCount(methodCoveredBranchTargets.getOrDefault(methodKey, Collections.emptySet()).size());
            });
        }

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

    private String relationshipMethodKey(String className, String methodName, String methodDescriptor) {
        return safeString(className) + "#" + safeString(methodName) + "#" + safeString(methodDescriptor);
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
