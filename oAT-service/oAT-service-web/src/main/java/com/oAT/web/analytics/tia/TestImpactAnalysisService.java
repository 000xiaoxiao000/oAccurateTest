package com.oAT.web.analytics.tia;

import com.oAT.web.coveragecore.model.CoverageFootprint;
import com.oAT.web.coveragecore.model.CoverageLine;
import com.oAT.web.coveragecore.model.CoverageUnit;
import com.oAT.web.coveragecore.query.CoverageCoreQueryService;
import com.oAT.web.esDao.CaseCenterRepository;
import com.oAT.web.esDao.SystemSnapshotRepository;
import com.oAT.web.esDao.entity.CaseCenterIndex;
import com.oAT.web.esDao.entity.CoverageReportIndex;
import com.oAT.web.esDao.entity.SystemSnapshot;
import com.oAT.web.esDao.entity.Usecase;
import com.oAT.web.service.AppService;
import com.oAT.web.service.entity.AppVo;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class TestImpactAnalysisService {
    private final CoverageCoreQueryService coverageCoreQueryService;
    private final SystemSnapshotRepository systemSnapshotRepository;
    private final CaseCenterRepository caseCenterRepository;
    private final AppService appService;

    public TestImpactAnalysisService(CoverageCoreQueryService coverageCoreQueryService,
                                     SystemSnapshotRepository systemSnapshotRepository,
                                     CaseCenterRepository caseCenterRepository,
                                     AppService appService) {
        this.coverageCoreQueryService = coverageCoreQueryService;
        this.systemSnapshotRepository = systemSnapshotRepository;
        this.caseCenterRepository = caseCenterRepository;
        this.appService = appService;
    }

    public TestImpactAnalysisReport analyze(String reportId, String changedLines) {
        CoverageReportIndex reportIndex = coverageCoreQueryService.getReport(reportId);
        List<CoverageUnit> units = coverageCoreQueryService.listUnits(reportId);
        Map<String, Set<Integer>> changedLineMap = parseChangedLines(changedLines);
        boolean scopedToChangedLines = !changedLineMap.isEmpty();

        TestImpactAnalysisReport report = new TestImpactAnalysisReport();
        report.setReportId(reportId);
        report.setLanguage(reportIndex.getLanguage() == null ? reportIndex.getSourceType() : reportIndex.getLanguage());
        report.setChangedLineCount(changedLineMap.values().stream().mapToInt(Set::size).sum());

        Set<String> reportUnitKeys = buildReportUnitKeys(units);
        SnapshotUsecaseResolver usecaseResolver = new SnapshotUsecaseResolver(reportIndex, reportUnitKeys);
        Map<String, MutableImpactCase> impactMap = new LinkedHashMap<>();
        int footprintCount = 0;
        for (CoverageUnit unit : units) {
            String unitKey = firstText(unit.getUnitKey(), unit.getSourcePath(), unit.getDisplayName());
            for (CoverageLine line : unitLines(unit)) {
                if (line.getHits() <= 0 || (scopedToChangedLines && !isChangedLine(changedLineMap, unit, line.getLine()))) {
                    continue;
                }
                for (CoverageFootprint footprint : line.getFootprints()) {
                    if (footprint == null) {
                        continue;
                    }
                    footprintCount++;
                    String identity = footprintIdentity(footprint);
                    List<UsecaseImpact> footprintUsecases = mergeUsecases(
                            usecaseResolver.findUsecases(footprint.getTraceId()),
                            usecaseResolver.findCoverageFootprintUsecases(footprint));
                    if (footprintUsecases.isEmpty() && StringUtils.hasText(identity)) {
                        MutableImpactCase impacted = impactMap.computeIfAbsent(identity, ignored -> new MutableImpactCase(footprint));
                        impacted.coveredChangedLines++;
                        impacted.impactedUnits.add(unitKey + ":" + line.getLine());
                        continue;
                    }
                    for (UsecaseImpact usecase : footprintUsecases) {
                        String usecaseIdentity = "usecase:" + usecase.id();
                        MutableImpactCase usecaseImpact = impactMap.computeIfAbsent(usecaseIdentity,
                                ignored -> new MutableImpactCase(footprint, usecase));
                        usecaseImpact.coveredChangedLines++;
                        usecaseImpact.impactedUnits.add(unitKey + ":" + line.getLine());
                    }
                }
            }
        }
        boolean usedSnapshotFallback = false;
        if (!scopedToChangedLines) {
            usedSnapshotFallback = supplementReportSnapshotUsecases(usecaseResolver, impactMap);
        }

        List<TestImpactCase> impactedCases = impactMap.values().stream()
                .map(MutableImpactCase::toImpactCase)
                .sorted(Comparator.comparing(TestImpactCase::getCoveredChangedLines).reversed())
                .toList();
        report.setImpactedCases(impactedCases);
        report.setImpactedCaseCount((int) impactedCases.stream().filter(item -> StringUtils.hasText(item.getCaseName())).count());
        report.setImpactedTraceCount((int) impactedCases.stream().filter(item -> StringUtils.hasText(item.getTraceId())).count());
        if (!scopedToChangedLines) {
            report.getReasons().add("未提供变更行范围，已按当前报告内可关联用例或链路的已覆盖行估算");
        }
        if (usedSnapshotFallback) {
            report.getReasons().add("当前报告已按系统快照关联用例或覆盖单元补充推荐");
        } else if (footprintCount == 0) {
            report.getReasons().add("当前报告缺少用例或链路关联数据，无法推荐受影响用例");
        } else if (impactMap.isEmpty()) {
            report.getReasons().add("当前报告有覆盖足迹，但未记录测试用例名称，也未关联到测试用例");
        } else if (report.getImpactedCaseCount() == 0 && usecaseResolver.hasSystemSnapshots()) {
            report.getReasons().add("当前报告命中了系统快照链路，但这些快照未关联到测试用例");
        }
        return report;
    }

    private Map<String, Set<Integer>> parseChangedLines(String changedLines) {
        Map<String, Set<Integer>> result = new LinkedHashMap<>();
        if (!StringUtils.hasText(changedLines)) {
            return result;
        }
        String[] tokens = changedLines.split("[,;\\n]");
        for (String token : tokens) {
            if (!StringUtils.hasText(token)) {
                continue;
            }
            int separator = token.lastIndexOf(':');
            if (separator <= 0 || separator >= token.length() - 1) {
                continue;
            }
            String unit = normalizeUnitKey(token.substring(0, separator));
            try {
                String lineToken = token.substring(separator + 1).trim();
                int line = "*".equals(lineToken) ? -1 : Integer.parseInt(lineToken);
                if (line > 0 || line == -1) {
                    result.computeIfAbsent(unit, ignored -> new LinkedHashSet<>()).add(line);
                }
            } catch (NumberFormatException ignored) {
                // Ignore malformed changed-line tokens so CI callers can pass best-effort diffs.
            }
        }
        return result;
    }

    private boolean isChangedLine(Map<String, Set<Integer>> changedLineMap, CoverageUnit unit, int line) {
        return matchesChangedLine(changedLineMap, unit.getUnitKey(), line)
                || matchesChangedLine(changedLineMap, unit.getSourcePath(), line)
                || matchesChangedLine(changedLineMap, unit.getDisplayName(), line);
    }

    private boolean matchesChangedLine(Map<String, Set<Integer>> changedLineMap, String unitKey, int line) {
        if (!StringUtils.hasText(unitKey)) {
            return false;
        }
        String normalized = normalizeUnitKey(unitKey);
        for (Map.Entry<String, Set<Integer>> entry : changedLineMap.entrySet()) {
            String changedUnit = entry.getKey();
            if ((normalized.equals(changedUnit) || normalized.endsWith("/" + changedUnit) || changedUnit.endsWith("/" + normalized))
                    && (entry.getValue().contains(line) || entry.getValue().contains(-1))) {
                return true;
            }
        }
        return false;
    }

    private List<CoverageLine> unitLines(CoverageUnit unit) {
        if (!unit.getLines().isEmpty()) {
            return unit.getLines();
        }
        return unit.getFunctions().stream()
                .flatMap(function -> function.getLines().stream())
                .toList();
    }

    private String footprintIdentity(CoverageFootprint footprint) {
        return firstText(footprint.getCaseName(), footprint.getTraceId(), footprint.getBuildId());
    }

    private List<UsecaseImpact> mergeUsecases(List<UsecaseImpact> first, List<UsecaseImpact> second) {
        LinkedHashMap<String, UsecaseImpact> result = new LinkedHashMap<>();
        if (first != null) {
            for (UsecaseImpact usecase : first) {
                if (usecase != null && StringUtils.hasText(usecase.id())) {
                    result.putIfAbsent(usecase.id(), usecase);
                }
            }
        }
        if (second != null) {
            for (UsecaseImpact usecase : second) {
                if (usecase != null && StringUtils.hasText(usecase.id())) {
                    result.putIfAbsent(usecase.id(), usecase);
                }
            }
        }
        return new ArrayList<>(result.values());
    }

    private boolean supplementReportSnapshotUsecases(SnapshotUsecaseResolver usecaseResolver,
                                                     Map<String, MutableImpactCase> impactMap) {
        List<UsecaseImpact> reportSnapshotUsecases = usecaseResolver.findReportSnapshotUsecases();
        List<UsecaseImpact> reportSnapshotUnitImpacts = usecaseResolver.findReportSnapshotUnitImpacts();
        if (reportSnapshotUsecases.isEmpty() && reportSnapshotUnitImpacts.isEmpty()) {
            return false;
        }

        List<MutableImpactCase> currentImpacts = new ArrayList<>(impactMap.values());
        List<UsecaseImpact> reportSnapshotImpacts = new ArrayList<>(reportSnapshotUsecases);
        reportSnapshotImpacts.addAll(reportSnapshotUnitImpacts);
        Set<String> snapshotTraceIds = reportSnapshotImpacts.stream()
                .map(UsecaseImpact::traceId)
                .filter(StringUtils::hasText)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        Set<String> preservedImpactIdentities = new LinkedHashSet<>();
        for (UsecaseImpact usecase : reportSnapshotImpacts) {
            String usecaseIdentity = StringUtils.hasText(usecase.id())
                    ? "usecase:" + usecase.id()
                    : "snapshot:" + firstText(usecase.traceId(), String.valueOf(impactMap.size()));
            preservedImpactIdentities.add(usecaseIdentity);
            MutableImpactCase target = impactMap.computeIfAbsent(usecaseIdentity, ignored -> new MutableImpactCase(usecase));
            target.impactedUnits.addAll(usecase.impactedUnits());
            if (!StringUtils.hasText(usecase.traceId())) {
                continue;
            }
            for (MutableImpactCase source : currentImpacts) {
                if (source == target || source.hasUsecase() || !usecase.traceId().equals(source.traceId())) {
                    continue;
                }
                target.coveredChangedLines = Math.max(target.coveredChangedLines, source.coveredChangedLines);
                target.impactedUnits.addAll(source.impactedUnits);
            }
        }

        if (!snapshotTraceIds.isEmpty()) {
            impactMap.entrySet().removeIf(entry -> {
                MutableImpactCase value = entry.getValue();
                return !preservedImpactIdentities.contains(entry.getKey())
                        && !value.hasUsecase()
                        && snapshotTraceIds.contains(value.traceId());
            });
        }
        return true;
    }

    private String firstText(String... values) {
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

    private String normalizeUnitKey(String value) {
        return value == null ? "" : value.trim().replace('\\', '/');
    }

    private Set<String> buildReportUnitKeys(List<CoverageUnit> units) {
        Set<String> keys = new LinkedHashSet<>();
        for (CoverageUnit unit : units) {
            if (StringUtils.hasText(unit.getUnitKey())) keys.add(normalizeUnitKey(unit.getUnitKey()));
            if (StringUtils.hasText(unit.getSourcePath())) keys.add(normalizeUnitKey(unit.getSourcePath()));
            if (StringUtils.hasText(unit.getDisplayName())) keys.add(normalizeUnitKey(unit.getDisplayName()));
        }
        return keys;
    }

    private class MutableImpactCase {
        private final CoverageFootprint footprint;
        private final UsecaseImpact usecase;
        private int coveredChangedLines;
        private final Set<String> impactedUnits = new LinkedHashSet<>();

        private MutableImpactCase(CoverageFootprint footprint) {
            this(footprint, null);
        }

        private MutableImpactCase(UsecaseImpact usecase) {
            this(null, usecase);
        }

        private MutableImpactCase(CoverageFootprint footprint, UsecaseImpact usecase) {
            this.footprint = footprint;
            this.usecase = usecase;
            if (usecase != null) {
                this.impactedUnits.addAll(usecase.impactedUnits());
            }
        }

        private TestImpactCase toImpactCase() {
            TestImpactCase item = new TestImpactCase();
            item.setUsecaseId(usecase == null ? null : usecase.id());
            item.setCaseName(usecase == null
                    ? (footprint == null ? null : footprint.getCaseName())
                    : firstText(usecase.title(), footprint == null ? null : footprint.getCaseName(), usecase.id()));
            item.setTestStage(footprint == null ? null : footprint.getTestStage());
            item.setBuildId(footprint == null ? null : footprint.getBuildId());
            item.setTraceId(usecase != null && StringUtils.hasText(usecase.traceId()) ? usecase.traceId()
                    : (footprint == null ? null : footprint.getTraceId()));
            item.setCoveredChangedLines(coveredChangedLines);
            item.setImpactedUnits(List.copyOf(impactedUnits));
            return item;
        }

        private boolean hasUsecase() {
            return usecase != null && StringUtils.hasText(usecase.id());
        }

        private String traceId() {
            if (usecase != null && StringUtils.hasText(usecase.traceId())) {
                return usecase.traceId();
            }
            return footprint == null ? null : footprint.getTraceId();
        }
    }

    private class SnapshotUsecaseResolver {
        private final String projectId;
        private final String appId;
        private final String language;
        private final String commitId;
        private final String versionNumber;
        private final Set<String> reportUnitKeys;
        private final Map<String, List<SystemSnapshot>> snapshotsByTraceId = new HashMap<>();
        private final Map<String, List<UsecaseImpact>> usecasesByTraceId = new HashMap<>();
        private final Map<String, List<UsecaseImpact>> usecasesByCoverageFootprintKey = new HashMap<>();
        private boolean loadedSnapshots;

        private SnapshotUsecaseResolver(CoverageReportIndex reportIndex, Set<String> reportUnitKeys) {
            this.projectId = resolveProjectId(reportIndex);
            this.appId = reportIndex == null ? null : reportIndex.getAppId();
            this.language = reportIndex == null ? null : firstText(reportIndex.getLanguage(), reportIndex.getSourceType());
            this.commitId = reportIndex == null ? null : reportIndex.getRepoCommitId();
            this.versionNumber = reportIndex == null ? null : reportIndex.getVersionNumber();
            this.reportUnitKeys = reportUnitKeys != null ? reportUnitKeys : Collections.emptySet();
            loadReportSnapshots(reportIndex);
        }

        private List<UsecaseImpact> findUsecases(String traceId) {
            if (!StringUtils.hasText(traceId) || !StringUtils.hasText(projectId)) {
                return Collections.emptyList();
            }
            return usecasesByTraceId.computeIfAbsent(traceId, this::loadUsecasesByTraceId);
        }

        private List<UsecaseImpact> findCoverageFootprintUsecases(CoverageFootprint footprint) {
            String footprintKey = coverageFootprintKey(footprint);
            if (!StringUtils.hasText(footprintKey) || !StringUtils.hasText(projectId)) {
                return Collections.emptyList();
            }
            return usecasesByCoverageFootprintKey.computeIfAbsent(footprintKey, this::loadUsecasesByCoverageFootprintKey);
        }

        private boolean hasSystemSnapshots() {
            return loadedSnapshots || !snapshotsByTraceId.isEmpty();
        }

        private List<UsecaseImpact> findReportSnapshotUsecases() {
            LinkedHashMap<String, UsecaseImpact> result = new LinkedHashMap<>();
            for (List<SystemSnapshot> snapshots : snapshotsByTraceId.values()) {
                if (snapshots == null) {
                    continue;
                }
                for (SystemSnapshot snapshot : snapshots) {
                    for (UsecaseImpact usecase : loadUsecasesBySnapshot(snapshot)) {
                        mergeUsecaseImpact(result, usecase);
                    }
                }
            }
            return new ArrayList<>(result.values());
        }

        private List<UsecaseImpact> findReportSnapshotUnitImpacts() {
            List<UsecaseImpact> result = new ArrayList<>();
            Set<String> handledSnapshotIds = new LinkedHashSet<>();
            for (List<SystemSnapshot> snapshots : snapshotsByTraceId.values()) {
                if (snapshots == null) {
                    continue;
                }
                for (SystemSnapshot snapshot : snapshots) {
                    String snapshotIdentity = firstText(snapshot == null ? null : snapshot.getId(),
                            snapshot == null ? null : snapshot.getTraceId());
                    if (snapshot == null || !StringUtils.hasText(snapshot.getTraceId())
                            || !handledSnapshotIds.add(snapshotIdentity)) {
                        continue;
                    }
                    if (!loadUsecasesBySnapshot(snapshot).isEmpty()) {
                        continue;
                    }
                    List<String> snapshotUnits = resolveToReportUnits(snapshotImpactUnits(snapshot));
                    if (snapshotUnits.isEmpty()) {
                        continue;
                    }
                    result.add(new UsecaseImpact(null, null, snapshot.getTraceId(), snapshotUnits));
                }
            }
            return result;
        }

        private List<UsecaseImpact> loadUsecasesByCoverageFootprintKey(String footprintKey) {
            List<UsecaseImpact> result = new ArrayList<>();
            for (CaseCenterIndex index : caseCenterRepository
                    .findByUsecase_ProjectIdAndUsecase_CoverageFootprintsContaining(projectId, footprintKey)) {
                Usecase usecase = index == null ? null : index.getUsecase();
                if (usecase == null || !StringUtils.hasText(index.getId())) {
                    continue;
                }
                String title = firstText(usecase.getTitle(), index.getId());
                result.add(new UsecaseImpact(index.getId(), title, null, Collections.emptyList()));
            }
            return result;
        }

        private String coverageFootprintKey(CoverageFootprint footprint) {
            if (footprint == null || !StringUtils.hasText(appId) || !StringUtils.hasText(language)) {
                return null;
            }
            return String.join("|",
                    text(appId),
                    text(language),
                    text(footprint.getBuildId()),
                    text(footprint.getTestStage()),
                    text(footprint.getCaseName()),
                    text(commitId));
        }

        private String text(String value) {
            return StringUtils.hasText(value) ? value : "";
        }

        private List<UsecaseImpact> loadUsecasesByTraceId(String traceId) {
            List<SystemSnapshot> snapshots = snapshotsByTraceId.get(traceId);
            if ((snapshots == null || snapshots.isEmpty()) && StringUtils.hasText(projectId)) {
                snapshots = systemSnapshotRepository.findByProjectIdAndTraceId(projectId, traceId);
                addSnapshots(snapshots);
            }

            LinkedHashMap<String, UsecaseImpact> result = new LinkedHashMap<>();
            if (snapshots != null && !snapshots.isEmpty()) {
                for (SystemSnapshot snapshot : snapshots) {
                    for (UsecaseImpact usecase : loadUsecasesBySnapshot(snapshot)) {
                        mergeUsecaseImpact(result, usecase);
                    }
                }
            }

            // Supplement with usecases linked via my-snapshot (oat_snapshot) when no system-snapshot
            // covers this traceId, or to pick up any additional usecases that are only snapshot-linked.
            if (StringUtils.hasText(projectId)) {
                for (UsecaseImpact usecase : loadUsecasesByMySnapshotTraceId(traceId)) {
                    mergeUsecaseImpact(result, usecase);
                }
            }

            return new ArrayList<>(result.values());
        }

        private List<UsecaseImpact> loadUsecasesByMySnapshotTraceId(String traceId) {
            List<CaseCenterIndex> indexes = caseCenterRepository.findUsecasesByMySnapshotTraceId(projectId, traceId);
            if (indexes.isEmpty()) {
                return Collections.emptyList();
            }
            List<UsecaseImpact> result = new ArrayList<>();
            for (CaseCenterIndex index : indexes) {
                Usecase usecase = index == null ? null : index.getUsecase();
                if (usecase == null || !StringUtils.hasText(index.getId())) {
                    continue;
                }
                String title = firstText(usecase.getTitle(), index.getId());
                List<String> units = resolveToReportUnits(usecaseImpactUnits(usecase));
                result.add(new UsecaseImpact(index.getId(), title, traceId, units));
            }
            return result;
        }

        private void mergeUsecaseImpact(Map<String, UsecaseImpact> result, UsecaseImpact usecase) {
            if (usecase == null || !StringUtils.hasText(usecase.id())) {
                return;
            }
            UsecaseImpact existing = result.get(usecase.id());
            if (existing == null) {
                result.put(usecase.id(), usecase);
                return;
            }
            LinkedHashSet<String> impactedUnits = new LinkedHashSet<>(existing.impactedUnits());
            impactedUnits.addAll(usecase.impactedUnits());
            result.put(usecase.id(), new UsecaseImpact(
                    existing.id(),
                    firstText(existing.title(), usecase.title()),
                    firstText(existing.traceId(), usecase.traceId()),
                    new ArrayList<>(impactedUnits)));
        }

        private List<UsecaseImpact> loadUsecasesBySnapshot(SystemSnapshot snapshot) {
            if (snapshot == null || !StringUtils.hasText(snapshot.getId()) || !StringUtils.hasText(projectId)) {
                return Collections.emptyList();
            }
            List<UsecaseImpact> result = new ArrayList<>();
            List<CaseCenterIndex> indexes = caseCenterRepository
                    .findByUsecase_ProjectIdAndUsecase_SystemSnapshotsContaining(projectId, snapshot.getId());
            List<String> snapshotUnits = resolveToReportUnits(snapshotImpactUnits(snapshot));
            for (CaseCenterIndex index : indexes) {
                Usecase usecase = index == null ? null : index.getUsecase();
                if (usecase == null || !StringUtils.hasText(index.getId())) {
                    continue;
                }
                String title = firstText(usecase.getTitle(), index.getId());
                LinkedHashSet<String> usecaseUnits = new LinkedHashSet<>(snapshotUnits);
                usecaseUnits.addAll(resolveToReportUnits(usecaseImpactUnits(usecase)));
                result.add(new UsecaseImpact(index.getId(), title, snapshot.getTraceId(), new ArrayList<>(usecaseUnits)));
            }
            return result;
        }

        /**
         * Maps extracted unit names to actual keys present in the report.
         * For each extracted name, looks for a report unit whose key ends with the extracted name
         * (or vice versa) to handle root-relative vs absolute path mismatches.
         * Falls back to the raw extracted name when no match is found so that at least some
         * context is preserved for callers that do not need an exact match.
         */
        private List<String> resolveToReportUnits(List<String> extractedNames) {
            if (extractedNames.isEmpty() || reportUnitKeys.isEmpty()) {
                return extractedNames;
            }
            List<String> resolved = new ArrayList<>();
            for (String name : extractedNames) {
                if (!StringUtils.hasText(name)) {
                    continue;
                }
                String matched = matchReportUnitKey(name);
                resolved.add(matched != null ? matched : name);
            }
            return resolved;
        }

        private String matchReportUnitKey(String candidateName) {
            if (!StringUtils.hasText(candidateName) || reportUnitKeys.isEmpty()) {
                return null;
            }
            String normalized = normalizeUnitKey(candidateName);
            // 1. Exact match
            if (reportUnitKeys.contains(normalized)) {
                return normalized;
            }
            // 2. Suffix match (case-sensitive): handles root-relative vs absolute path differences
            for (String key : reportUnitKeys) {
                if (key.endsWith("/" + normalized) || normalized.endsWith("/" + key)) {
                    return key;
                }
            }
            // 3. Extension-tolerant match: snapshot codes store class names without file extensions
            // because agent class names come from bytecode/runtime and have no extension
            // (e.g. "src.views.Login" for .vue, "com.example.UserHandler" for .go).
            // Also case-insensitive to handle Go type names (UserHandler) vs file names (user_handler.go)
            // and similar conventions in Python and C/C++.
            String normalizedSlash = normalized.replace('.', '/').toLowerCase();
            for (String key : reportUnitKeys) {
                String keyWithoutExt = stripExtension(key).toLowerCase();
                if (keyWithoutExt.equals(normalizedSlash)
                        || keyWithoutExt.endsWith("/" + normalizedSlash)
                        || normalizedSlash.endsWith("/" + keyWithoutExt)) {
                    return key;
                }
            }
            // 4. Case-insensitive suffix match for languages where the path separator was already
            // a slash in the original (Go module paths, Python package paths, etc.)
            String normalizedLower = normalized.toLowerCase();
            for (String key : reportUnitKeys) {
                String keyLower = key.toLowerCase();
                if (keyLower.endsWith("/" + normalizedLower) || normalizedLower.endsWith("/" + keyLower)) {
                    return key;
                }
            }
            // 5. Partial file-name match for dotted Java class names converted to slash paths
            for (String key : reportUnitKeys) {
                if (key.contains(normalized) || normalized.contains(key)) {
                    return key;
                }
            }
            return null;
        }

        private String stripExtension(String path) {
            if (!StringUtils.hasText(path)) {
                return path;
            }
            int dotIndex = path.lastIndexOf('.');
            int slashIndex = path.lastIndexOf('/');
            if (dotIndex > slashIndex && dotIndex > 0) {
                return path.substring(0, dotIndex);
            }
            return path;
        }

        private List<String> usecaseImpactUnits(Usecase usecase) {
            if (usecase == null || usecase.getSrcStack() == null || usecase.getSrcStack().length == 0) {
                return Collections.emptyList();
            }
            return java.util.Arrays.stream(usecase.getSrcStack())
                    .map(this::stackCodeUnitName)
                    .filter(StringUtils::hasText)
                    .distinct()
                    .collect(Collectors.toList());
        }

        private List<String> snapshotImpactUnits(SystemSnapshot snapshot) {
            if (snapshot == null || snapshot.getCodes() == null || snapshot.getCodes().length == 0) {
                return Collections.emptyList();
            }
            return java.util.Arrays.stream(snapshot.getCodes())
                    .map(this::stackCodeUnitName)
                    .filter(StringUtils::hasText)
                    .distinct()
                    .collect(Collectors.toList());
        }

        private String stackCodeUnitName(String code) {
            if (!StringUtils.hasText(code)) {
                return null;
            }
            String normalized = code.trim();
            int splitIndex = normalized.indexOf(' ');
            String unitName = splitIndex < 0 ? normalized : normalized.substring(0, splitIndex);
            unitName = unitName.trim();
            if (isSourcePath(unitName)) {
                return unitName.replace('\\', '/');
            }
            return normalizeDottedSourcePath(unitName.replace('/', '.'));
        }

        private String normalizeDottedSourcePath(String value) {
            if (!StringUtils.hasText(value)) {
                return value;
            }
            String lower = value.toLowerCase();
            for (String extension : List.of(".vue", ".jsx", ".tsx", ".mjs", ".cjs", ".js", ".ts", ".go", ".py", ".cpp", ".cc", ".c", ".hpp", ".h")) {
                if (lower.endsWith(extension) && value.indexOf('/') < 0) {
                    int extensionStart = value.length() - extension.length();
                    String withoutExtension = value.substring(0, extensionStart).replace('.', '/');
                    return withoutExtension + extension;
                }
            }
            return value;
        }

        private boolean isSourcePath(String value) {
            if (!StringUtils.hasText(value)) {
                return false;
            }
            String normalized = value.replace('\\', '/').toLowerCase();
            return normalized.contains("/")
                    && (normalized.endsWith(".vue")
                    || normalized.endsWith(".js")
                    || normalized.endsWith(".jsx")
                    || normalized.endsWith(".ts")
                    || normalized.endsWith(".tsx")
                    || normalized.endsWith(".mjs")
                    || normalized.endsWith(".cjs")
                    || normalized.endsWith(".go")
                    || normalized.endsWith(".py")
                    || normalized.endsWith(".c")
                    || normalized.endsWith(".cc")
                    || normalized.endsWith(".cpp")
                    || normalized.endsWith(".h")
                    || normalized.endsWith(".hpp"));
        }

        private void loadReportSnapshots(CoverageReportIndex reportIndex) {
            List<String> snapshotIds = parseSnapshotIds(reportIndex == null ? null : reportIndex.getSnapshotIds());
            if (!snapshotIds.isEmpty()) {
                addSnapshots(systemSnapshotRepository.findAllById(snapshotIds));
                return;
            }
            loadAppSnapshotsFallback();
        }

        private void loadAppSnapshotsFallback() {
            if (!StringUtils.hasText(projectId) || !StringUtils.hasText(appId)) {
                return;
            }
            List<SystemSnapshot> snapshots = systemSnapshotRepository.findByProjectIdAndAppId(projectId, appId);
            if (snapshots == null || snapshots.isEmpty()) {
                return;
            }
            List<SystemSnapshot> versionMatched = snapshots.stream()
                    .filter(snapshot -> snapshot != null
                            && StringUtils.hasText(versionNumber)
                            && versionNumber.equals(snapshot.getVersion()))
                    .toList();
            addSnapshots(versionMatched.isEmpty() ? snapshots : versionMatched);
        }

        private void addSnapshots(List<SystemSnapshot> snapshots) {
            if (snapshots == null || snapshots.isEmpty()) {
                return;
            }
            loadedSnapshots = true;
            for (SystemSnapshot snapshot : snapshots) {
                if (snapshot == null || !StringUtils.hasText(snapshot.getTraceId())) {
                    continue;
                }
                snapshotsByTraceId.computeIfAbsent(snapshot.getTraceId(), ignored -> new ArrayList<>()).add(snapshot);
            }
        }

        private String resolveProjectId(CoverageReportIndex reportIndex) {
            List<String> snapshotIds = parseSnapshotIds(reportIndex == null ? null : reportIndex.getSnapshotIds());
            if (!snapshotIds.isEmpty()) {
                Optional<SystemSnapshot> snapshot = systemSnapshotRepository.findAllById(snapshotIds).stream()
                        .filter(item -> item != null && StringUtils.hasText(item.getProjectId()))
                        .findFirst();
                if (snapshot.isPresent()) {
                    return snapshot.get().getProjectId();
                }
            }
            if (reportIndex != null && StringUtils.hasText(reportIndex.getAppId())) {
                try {
                    AppVo app = appService.getApp(reportIndex.getAppId());
                    if (app != null && StringUtils.hasText(app.getCreateProjectId())) {
                        return app.getCreateProjectId();
                    }
                } catch (RuntimeException ignored) {
                    // TIA should still work for coverage footprints that already carry case names.
                }
            }
            return null;
        }

        private List<String> parseSnapshotIds(String raw) {
            if (!StringUtils.hasText(raw)) {
                return Collections.emptyList();
            }
            return java.util.Arrays.stream(raw.split(","))
                    .map(String::trim)
                    .filter(StringUtils::hasText)
                    .distinct()
                    .collect(Collectors.toList());
        }
    }

    private record UsecaseImpact(String id, String title, String traceId, List<String> impactedUnits) {}
}
