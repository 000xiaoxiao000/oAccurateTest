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

        SnapshotUsecaseResolver usecaseResolver = new SnapshotUsecaseResolver(reportIndex);
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
        boolean usedSnapshotUsecaseFallback = false;
        if (impactMap.isEmpty() && !scopedToChangedLines) {
            for (UsecaseImpact usecase : usecaseResolver.findReportSnapshotUsecases()) {
                String usecaseIdentity = "usecase:" + usecase.id();
                impactMap.computeIfAbsent(usecaseIdentity, ignored -> new MutableImpactCase(usecase));
                usedSnapshotUsecaseFallback = true;
            }
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
        if (usedSnapshotUsecaseFallback) {
            report.getReasons().add("当前报告缺少行级覆盖足迹，已按系统快照关联用例兜底推荐");
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

    private static class MutableImpactCase {
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
        }

        private TestImpactCase toImpactCase() {
            TestImpactCase item = new TestImpactCase();
            item.setUsecaseId(usecase == null ? null : usecase.id());
            item.setCaseName(usecase == null ? footprint.getCaseName() : usecase.title());
            item.setTestStage(footprint == null ? null : footprint.getTestStage());
            item.setBuildId(footprint == null ? null : footprint.getBuildId());
            item.setTraceId(usecase != null && StringUtils.hasText(usecase.traceId()) ? usecase.traceId()
                    : (footprint == null ? null : footprint.getTraceId()));
            item.setCoveredChangedLines(coveredChangedLines);
            item.setImpactedUnits(List.copyOf(impactedUnits));
            return item;
        }
    }

    private class SnapshotUsecaseResolver {
        private final String projectId;
        private final String appId;
        private final String language;
        private final String commitId;
        private final Map<String, List<SystemSnapshot>> snapshotsByTraceId = new HashMap<>();
        private final Map<String, List<UsecaseImpact>> usecasesByTraceId = new HashMap<>();
        private final Map<String, List<UsecaseImpact>> usecasesByCoverageFootprintKey = new HashMap<>();
        private boolean loadedSnapshots;

        private SnapshotUsecaseResolver(CoverageReportIndex reportIndex) {
            this.projectId = resolveProjectId(reportIndex);
            this.appId = reportIndex == null ? null : reportIndex.getAppId();
            this.language = reportIndex == null ? null : firstText(reportIndex.getLanguage(), reportIndex.getSourceType());
            this.commitId = reportIndex == null ? null : reportIndex.getRepoCommitId();
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
                        result.putIfAbsent(usecase.id(), usecase);
                    }
                }
            }
            return new ArrayList<>(result.values());
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
                result.add(new UsecaseImpact(index.getId(), title, null));
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
            if (snapshots == null || snapshots.isEmpty()) {
                return Collections.emptyList();
            }

            LinkedHashMap<String, UsecaseImpact> result = new LinkedHashMap<>();
            for (SystemSnapshot snapshot : snapshots) {
                for (UsecaseImpact usecase : loadUsecasesBySnapshot(snapshot)) {
                    result.putIfAbsent(usecase.id(), usecase);
                }
            }
            return new ArrayList<>(result.values());
        }

        private List<UsecaseImpact> loadUsecasesBySnapshot(SystemSnapshot snapshot) {
            if (snapshot == null || !StringUtils.hasText(snapshot.getId()) || !StringUtils.hasText(projectId)) {
                return Collections.emptyList();
            }
            List<UsecaseImpact> result = new ArrayList<>();
            List<CaseCenterIndex> indexes = caseCenterRepository
                    .findByUsecase_ProjectIdAndUsecase_SystemSnapshotsContaining(projectId, snapshot.getId());
            for (CaseCenterIndex index : indexes) {
                Usecase usecase = index == null ? null : index.getUsecase();
                if (usecase == null || !StringUtils.hasText(index.getId())) {
                    continue;
                }
                String title = firstText(usecase.getTitle(), index.getId());
                result.add(new UsecaseImpact(index.getId(), title, snapshot.getTraceId()));
            }
            return result;
        }

        private void loadReportSnapshots(CoverageReportIndex reportIndex) {
            List<String> snapshotIds = parseSnapshotIds(reportIndex == null ? null : reportIndex.getSnapshotIds());
            if (snapshotIds.isEmpty()) {
                return;
            }
            addSnapshots(systemSnapshotRepository.findAllById(snapshotIds));
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

    private record UsecaseImpact(String id, String title, String traceId) {}
}
