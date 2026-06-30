package com.oAT.web.analytics.testgap;

import com.oAT.web.analytics.gate.QualityGateResult;
import com.oAT.web.coveragecore.model.CoverageLine;
import com.oAT.web.coveragecore.model.CoverageUnit;
import com.oAT.web.coveragecore.query.CoverageCoreQueryService;
import com.oAT.web.esDao.entity.CoverageReportIndex;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;

@Service
public class TestGapAnalysisService {
    private final CoverageCoreQueryService coverageCoreQueryService;

    public TestGapAnalysisService(CoverageCoreQueryService coverageCoreQueryService) {
        this.coverageCoreQueryService = coverageCoreQueryService;
    }

    public TestGapReport analyze(String reportId) {
        CoverageReportIndex reportIndex = coverageCoreQueryService.getReport(reportId);
        List<CoverageUnit> units = coverageCoreQueryService.listUnits(reportId);
        TestGapReport report = new TestGapReport();
        report.setReportId(reportId);
        report.setLanguage(reportIndex.getLanguage() == null ? reportIndex.getSourceType() : reportIndex.getLanguage());
        report.setTotalUnits(units.size());

        List<TestGapUnit> riskyUnits = units.stream()
                .map(this::toGapUnit)
                .filter(unit -> unit.getUncoveredLines() > 0)
                .sorted(Comparator.comparing(TestGapUnit::getUncoveredLines).reversed())
                .toList();
        int totalLines = units.stream().mapToInt(unit -> unitLines(unit).size()).sum();
        int uncoveredLines = riskyUnits.stream().mapToInt(TestGapUnit::getUncoveredLines).sum();
        report.setRiskyUnits(riskyUnits.size());
        report.setTotalLines(totalLines);
        report.setUncoveredLines(uncoveredLines);
        report.setLineCoverageRate(rate(totalLines - uncoveredLines, totalLines));
        report.setUnits(riskyUnits);
        return report;
    }

    public QualityGateResult evaluateGate(String reportId, double minLineCoverageRate) {
        TestGapReport gapReport = analyze(reportId);
        CoverageReportIndex reportIndex = coverageCoreQueryService.getReport(reportId);
        QualityGateResult result = new QualityGateResult();
        result.setReportId(reportId);
        result.setAppId(reportIndex.getAppId());
        result.setVersionNumber(reportIndex.getVersionNumber());
        result.setRepoCommitId(reportIndex.getRepoCommitId());
        result.setLanguage(reportIndex.getLanguage() == null ? reportIndex.getSourceType() : reportIndex.getLanguage());
        result.setBuildId(reportIndex.getBuildId());
        result.setTestStage(reportIndex.getTestStage());
        result.setMinLineCoverageRate(minLineCoverageRate);
        result.setActualLineCoverageRate(gapReport.getLineCoverageRate());
        result.setUncoveredLines(gapReport.getUncoveredLines());
        result.setRiskyUnits(gapReport.getRiskyUnits());
        boolean passed = gapReport.getLineCoverageRate() >= minLineCoverageRate;
        result.setPassed(passed);
        if (!passed) {
            result.getReasons().add("line coverage " + formatRate(gapReport.getLineCoverageRate()) + " below gate " + formatRate(minLineCoverageRate));
        }
        if (gapReport.getUncoveredLines() > 0) {
            result.getReasons().add(gapReport.getUncoveredLines() + " uncovered lines across " + gapReport.getRiskyUnits() + " units");
        }
        return result;
    }

    private TestGapUnit toGapUnit(CoverageUnit unit) {
        List<CoverageLine> lines = unitLines(unit);
        List<Integer> uncoveredLineNumbers = lines.stream()
                .filter(line -> line.getHits() <= 0)
                .map(CoverageLine::getLine)
                .sorted()
                .toList();
        TestGapUnit gapUnit = new TestGapUnit();
        gapUnit.setUnitKey(unit.getUnitKey());
        gapUnit.setDisplayName(unit.getDisplayName());
        gapUnit.setSourcePath(unit.getSourcePath());
        gapUnit.setTotalLines(lines.size());
        gapUnit.setUncoveredLines(uncoveredLineNumbers.size());
        gapUnit.setLineCoverageRate(rate(lines.size() - uncoveredLineNumbers.size(), lines.size()));
        gapUnit.setUncoveredLineNumbers(uncoveredLineNumbers);
        return gapUnit;
    }

    private List<CoverageLine> unitLines(CoverageUnit unit) {
        if (!unit.getLines().isEmpty()) {
            return unit.getLines();
        }
        return unit.getFunctions().stream()
                .flatMap(function -> function.getLines().stream())
                .toList();
    }

    private double rate(int covered, int total) {
        return total <= 0 ? 0 : (double) covered / total * 100;
    }

    private String formatRate(double rate) {
        return String.format("%.2f%%", rate);
    }
}
