package com.oAT.web.api.snapshot;

import com.oAT.agent.model.CodeNodeBean;
import com.oAT.agent.model.StackNodeVo;
import com.oAT.agent.model.TraceNode;
import com.oAT.web.coverage.CoverageStorage;
import com.oAT.web.coveragecore.model.CoverageLanguage;
import com.oAT.web.coveragecore.model.SourceCoverageBranch;
import com.oAT.web.coveragecore.model.SourceCoverageLine;
import com.oAT.web.coveragecore.model.SourceCoveragePayload;
import com.oAT.web.coveragecore.source.CoverageSourceContent;
import com.oAT.web.coveragecore.source.CoverageSourceContentService;
import com.oAT.web.esDao.StaticInfoRepository;
import com.oAT.web.esDao.entity.ClassCoverageIndex;
import com.oAT.web.esDao.entity.CoverageReportIndex;
import com.oAT.web.esDao.entity.StaticSourceInfo;
import com.oAT.web.esDao.entity.StaticSourceMethodInfo;
import com.oAT.web.esDao.entity.SystemSnapshot;
import com.oAT.web.service.SnapshotService;
import com.oAT.web.service.entity.AppVo;
import com.oAT.web.service.entity.SnapshotVo;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.util.HtmlUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static com.oAT.web.coveragecore.diff.BranchTargetProbeMaps.calculateBranchRate;
import static com.oAT.web.coveragecore.diff.BranchTargetProbeMaps.countBranchTargets;
import static com.oAT.web.coveragecore.diff.BranchTargetProbeMaps.mergeBranchTargetProbeMap;
import static com.oAT.web.coveragecore.diff.BranchTargetProbeMaps.normalizeCoveredBranchTargetProbeMap;
import static com.oAT.web.coveragecore.diff.BranchTargetProbeMaps.normalizeMethodBranchTargetProbeMap;

@Service
public class SnapshotCoverageReportService {

    private final SnapshotService snapshotService;
    private final StaticInfoRepository staticInfoRepository;
    private final CoverageStorage coverageStorage;
    private final CoverageSourceContentService coverageSourceContentService;
    private final SnapshotClassCoverageBuilder snapshotClassCoverageBuilder;

    public SnapshotCoverageReportService(SnapshotService snapshotService,
                                         StaticInfoRepository staticInfoRepository,
                                         CoverageStorage coverageStorage,
                                         CoverageSourceContentService coverageSourceContentService,
                                         SnapshotClassCoverageBuilder snapshotClassCoverageBuilder) {
        this.snapshotService = snapshotService;
        this.staticInfoRepository = staticInfoRepository;
        this.coverageStorage = coverageStorage;
        this.coverageSourceContentService = coverageSourceContentService;
        this.snapshotClassCoverageBuilder = snapshotClassCoverageBuilder;
    }

    public List<ClassCoverageSummary> buildSystemSnapshotClassStats(String appId, SystemSnapshot snapshot) {
        if (!StringUtils.hasText(appId) || snapshot == null || !StringUtils.hasText(snapshot.getTraceId())) {
            return new ArrayList<>();
        }

        Map<String, Set<String>> classMethods = new LinkedHashMap<>();
        Map<String, Set<Integer>> methodCoveredLines = new HashMap<>();
        Map<String, Set<Integer>> methodTotalLines = new HashMap<>();
        Map<String, Integer> methodComplexity = new HashMap<>();
        Map<String, Set<Integer>> methodTotalBranches = new HashMap<>();
        Map<String, Set<Integer>> methodCoveredBranches = new HashMap<>();
        Map<String, Set<String>> methodTotalBranchTargets = new HashMap<>();
        Map<String, Set<String>> methodCoveredBranchTargets = new HashMap<>();

        StackNodeVo[] codeNodes = loadTraceCodeNodes(snapshot.getTraceId());
        if (codeNodes != null) {
            for (StackNodeVo codeNode : codeNodes) {
                if (codeNode == null || !StringUtils.hasText(codeNode.getClassName())) {
                    continue;
                }
                String methodKey = codeNode.getMethodName() + "#" + codeNode.getMethodDescriptor();
                classMethods.computeIfAbsent(codeNode.getClassName(), key -> new LinkedHashSet<>()).add(methodKey);

                if (codeNode.getDoLines() != null) {
                    methodCoveredLines.computeIfAbsent(methodKey, key -> new LinkedHashSet<>()).addAll(codeNode.getDoLines());
                }
                if (codeNode.getExecuteBranch() != null) {
                    methodCoveredBranches.computeIfAbsent(methodKey, key -> new LinkedHashSet<>()).addAll(codeNode.getExecuteBranch());
                }
                addBranchTargetKeys(methodCoveredBranchTargets, methodKey, codeNode.getExecuteBranchTargetProbeMap());
            }
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
                addBranchTargetKeys(methodTotalBranchTargets, methodKey, normalizedTotalBranchTargetProbeMap);
                if (methodCoveredBranchTargets.containsKey(methodKey)) {
                    Set<String> normalizedKeys = new LinkedHashSet<>();
                    addBranchTargetKeysToSet(normalizedKeys, normalizedTotalBranchTargetProbeMap,
                            decodeBranchTargetKeys(methodCoveredBranchTargets.get(methodKey)));
                    methodCoveredBranchTargets.put(methodKey, normalizedKeys);
                }
            }
        }

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
            summary.setCoveredMethods(coveredMethods);
            summary.setTotalMethods(totalMethods);
            summary.setCoveredLines(coveredLines);
            summary.setTotalLines(totalLines);
            summary.setCoveredBranches(coveredBranches);
            summary.setTotalBranches(totalBranches);
            summary.setCoveredBranchTargets(coveredBranchTargets);
            summary.setTotalBranchTargets(totalBranchTargets);
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

    public ClassCoverageIndex buildSystemSnapshotClassCoverage(String appId, SystemSnapshot snapshot, String className) {
        return snapshotClassCoverageBuilder.buildSystemSnapshotClassCoverage(appId, snapshot, className);
    }

    public ClassCoverageIndex buildMySnapshotsClassCoverage(String appId, List<SnapshotVo> snapshots, String className) {
        return snapshotClassCoverageBuilder.buildMySnapshotsClassCoverage(appId, snapshots, className);
    }

    public ClassCoverageIndex buildMySnapshotClassCoverage(String appId, SnapshotVo snapshot, String className) {
        return snapshotClassCoverageBuilder.buildMySnapshotClassCoverage(appId, snapshot, className);
    }

    public List<MethodCoverageSummary> toMethodCoverageSummaries(List<ClassCoverageIndex.MethodCoverageDetail> methods) {
        if (methods == null) {
            return new ArrayList<>();
        }
        List<MethodCoverageSummary> result = new ArrayList<>();
        for (ClassCoverageIndex.MethodCoverageDetail method : methods) {
            MethodCoverageSummary summary = new MethodCoverageSummary();
            summary.setMethodName(method.getMethodName());
            summary.setMethodDesc(method.getMethodDesc());
            summary.setTotalLines(method.getTotalLines());
            summary.setCoveredLines(method.getCoveredLines());
            summary.setTotalBranches(method.getTotalBranches());
            summary.setCoveredBranches(method.getCoveredBranches());
            summary.setComplexity(method.getComplexity());
            summary.setCovered(method.isCovered());
            summary.setTotalBranchTargets(method.getTotalBranchTargets());
            summary.setCoveredBranchTargets(method.getCoveredBranchTargets());
            summary.setBranchRate(method.getBranchRate());
            result.add(summary);
        }
        return result;
    }

    public SourceCoveragePayload toSourceCoveragePayload(ClassCoverageIndex classCoverage,
                                                         CoverageReportIndex sourceReport,
                                                         AppVo app) {
        SourceCoveragePayload payload = new SourceCoveragePayload();
        payload.setLanguage(CoverageLanguage.from(firstText(classCoverage.getLanguage(), app == null ? null : app.getLanguage(), "JAVA")));
        payload.setSourcePath(firstText(classCoverage.getSourcePath(), classCoverage.getDisplayName(), classCoverage.getClassName()));
        Map<Integer, SourceCoverageLine> lineMap = new LinkedHashMap<>();
        if (classCoverage.getMethods() != null) {
            for (ClassCoverageIndex.MethodCoverageDetail method : classCoverage.getMethods()) {
                if (method.getTotalLineNumbers() != null) {
                    Set<Integer> covered = method.getCoveredLineNumbers() == null ? Collections.emptySet() : new LinkedHashSet<>(method.getCoveredLineNumbers());
                    for (Integer lineNumber : method.getTotalLineNumbers()) {
                        if (lineNumber == null || lineNumber <= 0) {
                            continue;
                        }
                        SourceCoverageLine line = lineMap.computeIfAbsent(lineNumber, key -> {
                            SourceCoverageLine next = new SourceCoverageLine();
                            next.setLine(key);
                            next.setText("");
                            return next;
                        });
                        line.setHits(covered.contains(lineNumber) ? 1 : Math.max(line.getHits(), 0));
                    }
                }
            }
        }

        CoverageSourceContent sourceContent = sourceReport == null
                ? CoverageSourceContent.message(null)
                : coverageSourceContentService.loadSource(sourceReport, classCoverage);
        if (sourceContent.getContent() != null) {
            String[] sourceLines = sourceContent.getContent().split("\\r?\\n", -1);
            for (int index = 0; index < sourceLines.length; index++) {
                int lineNumber = index + 1;
                SourceCoverageLine line = lineMap.computeIfAbsent(lineNumber, key -> {
                    SourceCoverageLine next = new SourceCoverageLine();
                    next.setLine(key);
                    return next;
                });
                line.setText(sourceLines[index]);
            }
            payload.setSourcePath(firstText(sourceContent.getSourcePath(), payload.getSourcePath()));
        }
        payload.setLines(lineMap.values().stream()
                .sorted(java.util.Comparator.comparing(SourceCoverageLine::getLine))
                .collect(Collectors.toList()));
        payload.setBranches(toSourceCoverageBranches(classCoverage));
        return payload;
    }

    public String toLegacyColoredSource(SourceCoveragePayload sourceCoverage) {
        if (sourceCoverage == null || sourceCoverage.getLines() == null || sourceCoverage.getLines().isEmpty()) {
            return "Coverage data not found";
        }
        StringBuilder html = new StringBuilder();
        html.append("<pre class=\"coverage-source\">");
        for (SourceCoverageLine line : sourceCoverage.getLines()) {
            String cssClass = line.getHits() > 0 ? "covered" : "uncovered";
            html.append("<span class=\"line ")
                    .append(cssClass)
                    .append("\" data-line=\"")
                    .append(line.getLine())
                    .append("\">")
                    .append(HtmlUtils.htmlEscape(line.getText() == null ? "" : line.getText()))
                    .append("</span>\n");
        }
        html.append("</pre>");
        return html.toString();
    }

    public CoverageReportIndex toSourceReportContext(AppVo app, SnapshotVo snapshot) {
        if (app == null) {
            return null;
        }
        CoverageReportIndex report = new CoverageReportIndex();
        report.setAppId(app.getId());
        report.setVersionNumber(firstText(snapshot == null ? null : snapshot.getVersionNumber(), app.getCurrentVersion()));
        report.setRepoBranch(firstText(snapshot == null ? null : snapshot.getRepoBranch(), app.getCurrentBranch()));
        report.setRepoCommitId(firstText(snapshot == null ? null : snapshot.getRepoCommitId(), app.getCurrentCommitId()));
        report.setLanguage(app.getLanguage());
        report.setSourceType(app.getLanguage());
        return report;
    }

    private StackNodeVo[] loadTraceCodeNodes(String traceId) {
        if (!StringUtils.hasText(traceId)) {
            return null;
        }
        List<StackNodeVo> stored = coverageStorage.load(traceId);
        if (stored != null && !stored.isEmpty()) {
            return stored.toArray(new StackNodeVo[0]);
        }
        List<StackNodeVo> merged = new ArrayList<>();
        for (TraceNode node : snapshotService.getTraceNodes(traceId)) {
            if (node instanceof CodeNodeBean) {
                StackNodeVo[] codeNodes = ((CodeNodeBean) node).getCodeNodes();
                if (codeNodes != null && codeNodes.length > 0) {
                    Collections.addAll(merged, codeNodes);
                }
            }
        }
        return merged.isEmpty() ? null : merged.toArray(new StackNodeVo[0]);
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
                                     Map<String, List<Integer>> branchTargetProbeMap) {
        if (branchTargetProbeMap == null || branchTargetProbeMap.isEmpty()) {
            return;
        }
        Set<String> keys = target.computeIfAbsent(methodKey, key -> new LinkedHashSet<>());
        addBranchTargetKeysToSet(keys, branchTargetProbeMap, null);
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

    private List<SourceCoverageBranch> toSourceCoverageBranches(ClassCoverageIndex classCoverage) {
        List<SourceCoverageBranch> branches = new ArrayList<>();
        if (classCoverage.getMethods() == null) {
            return branches;
        }
        for (ClassCoverageIndex.MethodCoverageDetail method : classCoverage.getMethods()) {
            Map<String, List<Integer>> total = method.getTotalBranchTargetProbeMap();
            if (total == null) {
                continue;
            }
            Map<String, List<Integer>> covered = method.getCoveredBranchTargetProbeMap() == null
                    ? Collections.emptyMap()
                    : method.getCoveredBranchTargetProbeMap();
            for (Map.Entry<String, List<Integer>> entry : total.entrySet()) {
                List<Integer> targets = entry.getValue() == null ? Collections.emptyList() : entry.getValue();
                List<Integer> coveredTargets = covered.getOrDefault(entry.getKey(), Collections.emptyList());
                for (Integer target : targets) {
                    SourceCoverageBranch branch = new SourceCoverageBranch();
                    branch.setLine(parseBranchLine(entry.getKey()));
                    branch.setGroupId(entry.getKey());
                    branch.setBranchIndex(target == null ? 0 : target);
                    branch.setHits(coveredTargets.contains(target) ? 1 : 0);
                    branches.add(branch);
                }
            }
        }
        return branches;
    }

    private int parseBranchLine(String key) {
        if (!StringUtils.hasText(key)) {
            return 0;
        }
        try {
            return Integer.parseInt(key.replaceAll("[^0-9].*$", ""));
        } catch (Exception e) {
            return 0;
        }
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

    private static String firstText(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return null;
    }
}
