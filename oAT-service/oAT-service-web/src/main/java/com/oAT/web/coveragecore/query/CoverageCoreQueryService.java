package com.oAT.web.coveragecore.query;

import com.oAT.web.coveragecore.model.CoverageLanguage;
import com.oAT.web.coveragecore.model.BuildSession;
import com.oAT.web.coveragecore.model.CoverageBranch;
import com.oAT.web.coveragecore.model.CoverageFunction;
import com.oAT.web.coveragecore.model.CoverageLine;
import com.oAT.web.coveragecore.model.CoverageModule;
import com.oAT.web.coveragecore.model.CoverageUnit;
import com.oAT.web.coveragecore.model.SourceCoverageBranch;
import com.oAT.web.coveragecore.model.SourceCoverageLine;
import com.oAT.web.coveragecore.model.SourceCoveragePayload;
import com.oAT.web.coveragecore.report.CoverageMethodSummary;
import com.oAT.web.coveragecore.source.CoverageSourceContent;
import com.oAT.web.coveragecore.source.CoverageSourceContentService;
import com.oAT.web.esDao.ClassCoverageRepository;
import com.oAT.web.esDao.CoverageReportRepository;
import com.oAT.web.esDao.entity.ClassCoverageIndex;
import com.oAT.web.esDao.entity.CoverageReportIndex;
import com.oAT.web.language.java.JavaCoverageUnitProjector;
import com.oAT.web.language.universal.ClassCoverageUnitProjector;
import com.oAT.web.service.entity.CoverageTreeNode;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class CoverageCoreQueryService {
    private final CoverageReportRepository coverageReportRepository;
    private final ClassCoverageRepository classCoverageRepository;
    private final JavaCoverageUnitProjector javaCoverageUnitProjector;
    private final ClassCoverageUnitProjector classCoverageUnitProjector;
    private final CoverageSourceContentService coverageSourceContentService;

    public CoverageCoreQueryService(CoverageReportRepository coverageReportRepository,
                                    ClassCoverageRepository classCoverageRepository,
                                    JavaCoverageUnitProjector javaCoverageUnitProjector,
                                    ClassCoverageUnitProjector classCoverageUnitProjector,
                                    CoverageSourceContentService coverageSourceContentService) {
        this.coverageReportRepository = coverageReportRepository;
        this.classCoverageRepository = classCoverageRepository;
        this.javaCoverageUnitProjector = javaCoverageUnitProjector;
        this.classCoverageUnitProjector = classCoverageUnitProjector;
        this.coverageSourceContentService = coverageSourceContentService;
    }

    public CoverageReportIndex getReport(String reportId) {
        Assert.hasText(reportId, "reportId不能为空");
        return coverageReportRepository.findById(reportId).orElseThrow(() -> new IllegalArgumentException("覆盖率报告不存在"));
    }

    public List<CoverageReportIndex> listReportsByAppId(String appId) {
        Assert.hasText(appId, "appId不能为空");
        return coverageReportRepository.findByAppId(appId);
    }

    public List<CoverageUnit> listUnits(String reportId) {
        CoverageReportIndex report = getReport(reportId);
        CoverageLanguage language = CoverageLanguage.from(report.getLanguage() == null ? report.getSourceType() : report.getLanguage());
        List<CoverageUnit> units = new ArrayList<>();
        for (ClassCoverageIndex index : classCoverageRepository.findByReportId(reportId)) {
            if (language == CoverageLanguage.JAVA) {
                CoverageSourceContent content = coverageSourceContentService.loadSource(report, index);
                units.add(javaCoverageUnitProjector.project(index, content.getContent()));
            } else {
                units.add(classCoverageUnitProjector.project(index, language));
            }
        }
        return units;
    }

    public CoverageUnitPage listUnits(String reportId, CoverageUnitQuery query) {
        CoverageUnitQuery effectiveQuery = query == null ? new CoverageUnitQuery() : query;
        List<CoverageUnit> filtered = listUnits(reportId).stream()
                .filter(unit -> matchesQuery(unit, effectiveQuery))
                .toList();
        int page = effectiveQuery.pageOrDefault();
        int size = effectiveQuery.sizeOrDefault();
        int fromIndex = Math.min(page * size, filtered.size());
        int toIndex = Math.min(fromIndex + size, filtered.size());

        CoverageUnitPage result = new CoverageUnitPage();
        result.setContent(filtered.subList(fromIndex, toIndex));
        result.setPage(page);
        result.setSize(size);
        result.setTotalElements(filtered.size());
        result.setTotalPages(size <= 0 ? 0 : (int) Math.ceil((double) filtered.size() / size));
        return result;
    }

    public List<CoverageTreeNode> listTreeNodes(String reportId,
                                                String parentPackage,
                                                CoverageUnitQuery query) {
        CoverageUnitQuery effectiveQuery = query == null ? new CoverageUnitQuery() : query;
        String parent = parentPackage == null ? "" : parentPackage.trim();
        List<CoverageUnit> units = listUnits(reportId).stream()
                .filter(unit -> matchesQuery(unit, effectiveQuery))
                .toList();
        if (parent.isBlank()) {
            return topLevelTreeNodes(units);
        }
        return childTreeNodes(units, parent);
    }

    public List<CoverageModule> listModules(String reportId) {
        return listModules(listUnits(reportId));
    }

    public List<CoverageModule> listModules(String reportId, CoverageUnitQuery query) {
        return listModules(listUnits(reportId).stream()
                .filter(unit -> matchesQuery(unit, query == null ? new CoverageUnitQuery() : query))
                .toList());
    }

    private List<CoverageModule> listModules(List<CoverageUnit> units) {
        Map<String, CoverageModule> moduleMap = new LinkedHashMap<>();
        for (CoverageUnit unit : units) {
            String moduleKey = moduleKey(unit);
            CoverageModule module = moduleMap.computeIfAbsent(moduleKey, key -> {
                CoverageModule next = new CoverageModule();
                next.setLanguage(unit.getLanguage());
                next.setModuleKey(key);
                return next;
            });
            module.getUnits().add(unit);
        }
        return new ArrayList<>(moduleMap.values());
    }

    private List<CoverageTreeNode> topLevelTreeNodes(List<CoverageUnit> units) {
        Map<String, List<CoverageUnit>> packageUnits = new LinkedHashMap<>();
        List<CoverageUnit> rootUnits = new ArrayList<>();
        for (CoverageUnit unit : units) {
            String path = treePath(unit);
            int separator = firstSeparator(path);
            if (separator > 0) {
                packageUnits.computeIfAbsent(path.substring(0, separator), ignored -> new ArrayList<>()).add(unit);
            } else {
                rootUnits.add(unit);
            }
        }
        List<CoverageTreeNode> nodes = new ArrayList<>();
        packageUnits.forEach((name, items) -> nodes.add(toPackageTreeNode("", name, name, items)));
        rootUnits.forEach(unit -> nodes.add(toUnitTreeNode("", unit)));
        return nodes.stream()
                .sorted(Comparator.comparing(CoverageTreeNode::getType).thenComparing(CoverageTreeNode::getName))
                .toList();
    }

    private List<CoverageTreeNode> childTreeNodes(List<CoverageUnit> units, String parent) {
        String normalizedParent = normalizeTreePath(parent);
        Map<String, List<CoverageUnit>> packageUnits = new LinkedHashMap<>();
        List<CoverageUnit> directUnits = new ArrayList<>();
        for (CoverageUnit unit : units) {
            String path = treePath(unit);
            if (!path.startsWith(normalizedParent + "/")) {
                continue;
            }
            String remainder = path.substring(normalizedParent.length() + 1);
            int separator = firstSeparator(remainder);
            if (separator > 0) {
                String childName = remainder.substring(0, separator);
                String childFullName = normalizedParent + "/" + childName;
                packageUnits.computeIfAbsent(childFullName, ignored -> new ArrayList<>()).add(unit);
            } else {
                directUnits.add(unit);
            }
        }
        List<CoverageTreeNode> nodes = new ArrayList<>();
        packageUnits.forEach((fullName, items) -> nodes.add(toPackageTreeNode(normalizedParent, leafName(fullName), fullName, items)));
        directUnits.forEach(unit -> nodes.add(toUnitTreeNode(normalizedParent, unit)));
        return nodes.stream()
                .sorted(Comparator.comparing(CoverageTreeNode::getType).thenComparing(CoverageTreeNode::getName))
                .toList();
    }

    private CoverageTreeNode toPackageTreeNode(String parentId, String name, String fullName, List<CoverageUnit> units) {
        CoverageTreeNode node = new CoverageTreeNode();
        node.setId(fullName);
        node.setParentId(parentId);
        node.setName(name);
        node.setFullName(fullName);
        node.setType("package");
        node.setHasChildren(true);
        applyMetrics(node, aggregateMetrics(units));
        return node;
    }

    private CoverageTreeNode toUnitTreeNode(String parentId, CoverageUnit unit) {
        CoverageTreeNode node = new CoverageTreeNode();
        String key = firstText(unit.getUnitKey(), unit.getDisplayName(), unit.getSourcePath());
        node.setId(key);
        node.setParentId(parentId);
        node.setName(leafName(firstText(unit.getDisplayName(), unit.getSourcePath(), unit.getUnitKey())));
        node.setFullName(key);
        node.setType("class");
        node.setAnchor(key);
        node.setHasChildren(false);
        applyMetrics(node, UnitMetrics.from(unit));
        return node;
    }

    private void applyMetrics(CoverageTreeNode node, UnitMetrics metrics) {
        node.setTotalMethods(metrics.totalFunctions());
        node.setCoveredMethods(metrics.coveredFunctions());
        node.setTotalBranches(metrics.totalBranches());
        node.setCoveredBranches(metrics.coveredBranches());
        node.setTotalBranchTargets(metrics.totalBranches());
        node.setCoveredBranchTargets(metrics.coveredBranches());
        node.setTotalLines(metrics.totalLines());
        node.setCoveredLines(metrics.coveredLines());
        node.setTotalComplexity(metrics.complexity());
        node.setLineRate(metrics.lineRate());
        node.setBranchRate(metrics.branchRate());
        node.setMethodRate(metrics.methodRate());
    }

    private UnitMetrics aggregateMetrics(List<CoverageUnit> units) {
        int totalLines = 0;
        int coveredLines = 0;
        int totalBranches = 0;
        int coveredBranches = 0;
        int totalFunctions = 0;
        int coveredFunctions = 0;
        int complexity = 0;
        for (CoverageUnit unit : units) {
            UnitMetrics metrics = UnitMetrics.from(unit);
            totalLines += metrics.totalLines();
            coveredLines += metrics.coveredLines();
            totalBranches += metrics.totalBranches();
            coveredBranches += metrics.coveredBranches();
            totalFunctions += metrics.totalFunctions();
            coveredFunctions += metrics.coveredFunctions();
            complexity += metrics.complexity();
        }
        return new UnitMetrics(totalLines, coveredLines, totalBranches, coveredBranches, totalFunctions, coveredFunctions, complexity);
    }

    public SourceCoveragePayload getSourceCoverage(String reportId, String unitKey) {
        Assert.hasText(reportId, "reportId不能为空");
        Assert.hasText(unitKey, "unitKey不能为空");
        UnitProjection projection = getUnitProjection(reportId, unitKey);
        CoverageReportIndex report = projection.report();
        ClassCoverageIndex classCoverage = projection.classCoverage();
        CoverageUnit unit = projection.unit();

        SourceCoveragePayload payload = new SourceCoveragePayload();
        payload.setLanguage(unit.getLanguage());
        payload.setSourcePath(firstText(unit.getSourcePath(), unit.getDisplayName(), unit.getUnitKey()));

        Map<Integer, SourceCoverageLine> lineMap = new LinkedHashMap<>();
        unit.getLines().forEach(line -> mergeLine(lineMap, line.getLine(), line.getHits()));
        unit.getFunctions().forEach(function -> function.getLines().forEach(line -> mergeLine(lineMap, line.getLine(), line.getHits())));
        CoverageSourceContent content = coverageSourceContentService.loadSource(report, classCoverage);
        if (content.getContent() != null) {
            String[] sourceLines = splitSourceLines(content.getContent());
            for (int index = 0; index < sourceLines.length; index++) {
                int lineNumber = index + 1;
                SourceCoverageLine line = lineMap.computeIfAbsent(lineNumber, key -> {
                    SourceCoverageLine next = new SourceCoverageLine();
                    next.setLine(key);
                    return next;
                });
                line.setText(sourceLines[index]);
            }
            payload.setSourcePath(firstText(content.getSourcePath(), payload.getSourcePath()));
        } else if (content.getMessage() != null) {
            SourceCoverageLine line = lineMap.computeIfAbsent(1, key -> {
                SourceCoverageLine next = new SourceCoverageLine();
                next.setLine(key);
                return next;
            });
            line.setText(content.getMessage());
        }
        payload.setLines(lineMap.values().stream()
                .sorted(Comparator.comparing(SourceCoverageLine::getLine))
                .toList());

        List<SourceCoverageBranch> branches = new ArrayList<>();
        List<CoverageBranch> sourceBranches = !unit.getBranches().isEmpty()
                ? unit.getBranches()
                : unit.getFunctions().stream().flatMap(function -> function.getBranches().stream()).toList();
        sourceBranches.forEach(branch -> branches.add(toSourceBranch(branch.getLine(), branch.getGroupId(), branch.getBranchIndex(), branch.getHits())));
        payload.setBranches(branches.stream()
                .sorted(Comparator.comparing(SourceCoverageBranch::getLine).thenComparing(SourceCoverageBranch::getBranchIndex))
                .toList());
        return payload;
    }

    public List<CoverageMethodSummary> listMethods(String reportId, String unitKey) {
        UnitProjection projection = getUnitProjection(reportId, unitKey);
        return projection.unit().getFunctions().stream()
                .map(this::toMethodSummary)
                .sorted(Comparator.comparing(CoverageMethodSummary::getMethodName, Comparator.nullsLast(String::compareTo)))
                .toList();
    }

    public List<BuildSession> listBuildSessions(String appId) {
        Assert.hasText(appId, "appId不能为空");
        Map<String, BuildSession> sessionMap = new LinkedHashMap<>();
        for (CoverageReportIndex report : coverageReportRepository.findByAppId(appId)) {
            String key = buildSessionKey(report);
            sessionMap.compute(key, (ignored, existing) -> {
                if (existing == null) {
                    return BuildSession.fromReport(report);
                }
                existing.merge(report);
                return existing;
            });
        }
        return sessionMap.values().stream()
                .sorted(Comparator.comparing(BuildSession::getLastReportTime, Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
    }

    private String buildSessionKey(CoverageReportIndex report) {
        String buildId = report.getBuildId();
        if (buildId == null || buildId.isBlank()) {
            buildId = "legacy:" + nullSafe(report.getVersionNumber()) + ":" + nullSafe(report.getRepoCommitId());
        }
        return buildId;
    }

    private String nullSafe(String value) {
        return value == null ? "" : value;
    }

    private String moduleKey(CoverageUnit unit) {
        String value = firstText(unit.getSourcePath(), unit.getDisplayName(), unit.getUnitKey());
        if (value == null || value.isBlank()) {
            return "default";
        }
        String normalized = value.replace('\\', '/');
        int slash = normalized.lastIndexOf('/');
        if (slash > 0) {
            return normalized.substring(0, slash);
        }
        int dot = normalized.lastIndexOf('.');
        if (dot > 0) {
            return normalized.substring(0, dot);
        }
        return "default";
    }

    private String treePath(CoverageUnit unit) {
        return normalizeTreePath(firstText(unit.getSourcePath(), unit.getDisplayName(), unit.getUnitKey()));
    }

    private String normalizeTreePath(String value) {
        if (value == null || value.isBlank()) {
            return "default";
        }
        return value.trim().replace('\\', '/').replace('.', '/');
    }

    private int firstSeparator(String value) {
        return value == null ? -1 : value.indexOf('/');
    }

    private String leafName(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String normalized = value.replace('\\', '/');
        int separator = normalized.lastIndexOf('/');
        if (separator >= 0 && separator < normalized.length() - 1) {
            return normalized.substring(separator + 1);
        }
        return normalized;
    }

    private String firstText(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private boolean matchesUnit(CoverageUnit unit, String unitKey) {
        return unitKey.equals(unit.getUnitKey())
                || unitKey.equals(unit.getDisplayName())
                || unitKey.equals(unit.getSourcePath());
    }

    private boolean matchesQuery(CoverageUnit unit, CoverageUnitQuery query) {
        if (unit == null) {
            return false;
        }
        if (hasText(query.getClassName()) && !containsAny(unit, query.getClassName())) {
            return false;
        }
        if (hasText(query.getMethodName()) && !containsMethod(unit, query.getMethodName())) {
            return false;
        }
        UnitMetrics metrics = UnitMetrics.from(unit);
        return within(metrics.lineRate(), query.getMinRate(), query.getMaxRate())
                && within(metrics.branchRate(), query.getMinBranchRate(), query.getMaxBranchRate())
                && within(metrics.methodRate(), query.getMinMethodRate(), query.getMaxMethodRate())
                && within(metrics.complexity(), query.getMinComplexity(), query.getMaxComplexity());
    }

    private boolean containsAny(CoverageUnit unit, String needle) {
        String normalizedNeedle = needle.trim().toLowerCase();
        return nullSafe(unit.getUnitKey()).toLowerCase().contains(normalizedNeedle)
                || nullSafe(unit.getDisplayName()).toLowerCase().contains(normalizedNeedle)
                || nullSafe(unit.getSourcePath()).toLowerCase().contains(normalizedNeedle);
    }

    private boolean containsMethod(CoverageUnit unit, String needle) {
        String normalizedNeedle = needle.trim().toLowerCase();
        return unit.getFunctions().stream()
                .map(function -> nullSafe(function.getSignature()).toLowerCase())
                .anyMatch(signature -> signature.contains(normalizedNeedle));
    }

    private boolean within(double value, Double min, Double max) {
        return (min == null || value >= min) && (max == null || value <= max);
    }

    private boolean within(int value, Integer min, Integer max) {
        return (min == null || value >= min) && (max == null || value <= max);
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private boolean matchesClassCoverage(ClassCoverageIndex index, String unitKey) {
        return unitKey.equals(index.getClassName())
                || unitKey.equals(index.getDisplayName())
                || unitKey.equals(index.getSourcePath())
                || unitKey.equals(normalizeSourcePath(index.getClassName()))
                || unitKey.equals(normalizeSourcePath(index.getDisplayName()))
                || unitKey.equals(normalizeSourcePath(index.getSourcePath()));
    }

    private String normalizeSourcePath(String value) {
        if (value == null || value.isBlank()) {
            return value;
        }
        String normalized = value.trim().replace('\\', '/');
        String[] sourceRoots = {"/src/", "/packages/", "/apps/", "/lib/", "/components/", "/views/", "/pages/"};
        for (String sourceRoot : sourceRoots) {
            int index = normalized.indexOf(sourceRoot);
            if (index >= 0) {
                return normalized.substring(index + 1);
            }
        }
        return normalized;
    }

    private UnitProjection getUnitProjection(String reportId, String unitKey) {
        CoverageReportIndex report = getReport(reportId);
        CoverageLanguage language = CoverageLanguage.from(report.getLanguage() == null ? report.getSourceType() : report.getLanguage());
        ClassCoverageIndex classCoverage = classCoverageRepository.findByReportId(reportId).stream()
                .filter(candidate -> matchesClassCoverage(candidate, unitKey))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("覆盖率源码单元不存在"));
        CoverageUnit unit;
        if (language == CoverageLanguage.JAVA) {
            CoverageSourceContent content = coverageSourceContentService.loadSource(report, classCoverage);
            unit = javaCoverageUnitProjector.project(classCoverage, content.getContent());
        } else {
            unit = classCoverageUnitProjector.project(classCoverage, language);
        }
        return new UnitProjection(report, classCoverage, unit);
    }

    private CoverageMethodSummary toMethodSummary(CoverageFunction function) {
        CoverageMethodSummary summary = new CoverageMethodSummary();
        summary.setMethodName(function.getSignature());
        summary.setMethodDesc(lineRange(function));
        summary.setTotalLines(function.getLines().size());
        summary.setCoveredLines((int) function.getLines().stream().filter(line -> line.getHits() > 0).count());
        summary.setTotalBranches(function.getBranches().size());
        summary.setCoveredBranches((int) function.getBranches().stream().filter(branch -> branch.getHits() > 0).count());
        summary.setTotalBranchTargets(function.getBranches().size());
        summary.setCoveredBranchTargets(summary.getCoveredBranches());
        summary.setBranchRate(summary.getTotalBranchTargets() > 0
                ? (double) summary.getCoveredBranchTargets() / summary.getTotalBranchTargets() * 100
                : null);
        summary.setComplexity(function.getComplexity());
        summary.setCovered(summary.getCoveredLines() > 0 || summary.getCoveredBranchTargets() > 0);
        summary.setStartLine(function.getStartLine());
        summary.setEndLine(function.getEndLine());
        return summary;
    }

    private String lineRange(CoverageFunction function) {
        if (function.getStartLine() <= 0 && function.getEndLine() <= 0) {
            return null;
        }
        return function.getStartLine() + "-" + function.getEndLine();
    }

    private String[] splitSourceLines(String content) {
        String[] lines = content.split("\\r?\\n", -1);
        int end = lines.length;
        while (end > 0 && lines[end - 1].isEmpty()) {
            end--;
        }
        if (end == lines.length) {
            return lines;
        }
        String[] trimmed = new String[end];
        System.arraycopy(lines, 0, trimmed, 0, end);
        return trimmed;
    }

    private void mergeLine(Map<Integer, SourceCoverageLine> lineMap, int lineNumber, int hits) {
        if (lineNumber <= 0) {
            return;
        }
        SourceCoverageLine line = lineMap.computeIfAbsent(lineNumber, key -> {
            SourceCoverageLine next = new SourceCoverageLine();
            next.setLine(key);
            next.setText("");
            return next;
        });
        line.setHits(Math.max(line.getHits(), hits));
    }

    private SourceCoverageBranch toSourceBranch(int line, String groupId, int branchIndex, int hits) {
        SourceCoverageBranch branch = new SourceCoverageBranch();
        branch.setLine(line);
        branch.setGroupId(groupId);
        branch.setBranchIndex(branchIndex);
        branch.setHits(hits);
        return branch;
    }

    private record UnitMetrics(int totalLines,
                               int coveredLines,
                               int totalBranches,
                               int coveredBranches,
                               int totalFunctions,
                               int coveredFunctions,
                               int complexity) {
        static UnitMetrics from(CoverageUnit unit) {
            List<CoverageLine> lines = !unit.getLines().isEmpty()
                    ? unit.getLines()
                    : unit.getFunctions().stream().flatMap(function -> function.getLines().stream()).toList();
            List<CoverageBranch> branches = !unit.getBranches().isEmpty()
                    ? unit.getBranches()
                    : unit.getFunctions().stream().flatMap(function -> function.getBranches().stream()).toList();
            int totalFunctions = unit.getFunctions().size();
            int coveredFunctions = (int) unit.getFunctions().stream().filter(UnitMetrics::isFunctionCovered).count();
            int complexity = unit.getFunctions().stream().mapToInt(CoverageFunction::getComplexity).sum();
            return new UnitMetrics(
                    lines.size(),
                    (int) lines.stream().filter(line -> line.getHits() > 0).count(),
                    branches.size(),
                    (int) branches.stream().filter(branch -> branch.getHits() > 0).count(),
                    totalFunctions,
                    coveredFunctions,
                    complexity);
        }

        private static boolean isFunctionCovered(CoverageFunction function) {
            return function.getLines().stream().anyMatch(line -> line.getHits() > 0)
                    || function.getBranches().stream().anyMatch(branch -> branch.getHits() > 0);
        }

        double lineRate() {
            return totalLines > 0 ? (double) coveredLines / totalLines * 100 : 0;
        }

        double branchRate() {
            return totalBranches > 0 ? (double) coveredBranches / totalBranches * 100 : 0;
        }

        double methodRate() {
            return totalFunctions > 0 ? (double) coveredFunctions / totalFunctions * 100 : 0;
        }
    }

    private record UnitProjection(CoverageReportIndex report, ClassCoverageIndex classCoverage, CoverageUnit unit) {}
}
