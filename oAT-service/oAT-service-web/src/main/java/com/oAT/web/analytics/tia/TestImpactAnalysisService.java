package com.oAT.web.analytics.tia;

import com.oAT.web.coveragecore.model.CoverageFootprint;
import com.oAT.web.coveragecore.model.CoverageLine;
import com.oAT.web.coveragecore.model.CoverageUnit;
import com.oAT.web.coveragecore.query.CoverageCoreQueryService;
import com.oAT.web.esDao.entity.CoverageReportIndex;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class TestImpactAnalysisService {
    private final CoverageCoreQueryService coverageCoreQueryService;

    public TestImpactAnalysisService(CoverageCoreQueryService coverageCoreQueryService) {
        this.coverageCoreQueryService = coverageCoreQueryService;
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
                    String identity = footprintIdentity(footprint);
                    if (!StringUtils.hasText(identity)) {
                        continue;
                    }
                    footprintCount++;
                    MutableImpactCase impacted = impactMap.computeIfAbsent(identity, ignored -> new MutableImpactCase(footprint));
                    impacted.coveredChangedLines++;
                    impacted.impactedUnits.add(unitKey + ":" + line.getLine());
                }
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
            report.getReasons().add("未提供 changedLines，结果基于当前报告内所有带 footprint 的已覆盖行");
        }
        if (footprintCount == 0) {
            report.getReasons().add("当前报告缺少可归因 footprint，无法推荐受影响用例");
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
        private int coveredChangedLines;
        private final Set<String> impactedUnits = new LinkedHashSet<>();

        private MutableImpactCase(CoverageFootprint footprint) {
            this.footprint = footprint;
        }

        private TestImpactCase toImpactCase() {
            TestImpactCase item = new TestImpactCase();
            item.setCaseName(footprint.getCaseName());
            item.setTestStage(footprint.getTestStage());
            item.setBuildId(footprint.getBuildId());
            item.setTraceId(footprint.getTraceId());
            item.setCoveredChangedLines(coveredChangedLines);
            item.setImpactedUnits(List.copyOf(impactedUnits));
            return item;
        }
    }
}
