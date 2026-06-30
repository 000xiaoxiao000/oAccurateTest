package com.oAT.web.coveragecore.report;

import com.oAT.web.coveragecore.diff.CoverageDiffService;
import com.oAT.web.coveragecore.persistence.CoverageReportPersistenceService;
import com.oAT.web.esDao.ClassCoverageRepository;
import com.oAT.web.esDao.entity.ClassCoverageIndex;
import com.oAT.web.esDao.entity.ClassCoverageIndex.MethodCoverageDetail;
import com.oAT.web.esDao.entity.CoverageReportIndex;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.oAT.web.coveragecore.diff.BranchTargetProbeMaps.calculateBranchRate;

@Service
public class CoverageReportSummaryPersistenceService {

    private final ClassCoverageRepository classCoverageRepository;
    private final CoverageReportPersistenceService coverageReportPersistenceService;
    private final CoverageDiffService coverageDiffService;

    public CoverageReportSummaryPersistenceService(ClassCoverageRepository classCoverageRepository,
                                                   CoverageReportPersistenceService coverageReportPersistenceService,
                                                   CoverageDiffService coverageDiffService) {
        this.classCoverageRepository = classCoverageRepository;
        this.coverageReportPersistenceService = coverageReportPersistenceService;
        this.coverageDiffService = coverageDiffService;
    }

    public void saveAndCalculateSummary(CoverageReportIndex report,
                                        Map<String, ClassCoverageIndex> coverageMap,
                                        Map<String, List<Integer>> diffMap) {
        long totalClasses = coverageMap.size();
        long coveredClasses = 0;
        long totalMethods = 0;
        long coveredMethods = 0;
        long totalBranches = 0;
        long coveredBranches = 0;
        long totalBranchTargets = 0;
        long coveredBranchTargets = 0;
        long totalLines = 0;
        long coveredLines = 0;
        int totalComplexity = 0;

        long incTotalClasses = 0;
        long incCoveredClasses = 0;
        long incTotalLines = 0;
        long incCoveredLines = 0;
        long incTotalMethods = 0;
        long incCoveredMethods = 0;
        long incTotalBranches = 0;
        long incCoveredBranches = 0;
        long incTotalBranchTargets = 0;
        long incCoveredBranchTargets = 0;
        int incTotalComplexity = 0;

        List<ClassCoverageIndex> toSave = new ArrayList<>();
        for (ClassCoverageIndex classCov : coverageMap.values()) {
            classCov.setReportId(report.getId());
            classCov.setId(report.getId() + "_" + classCov.getClassName().hashCode());

            if (classCov.getCoveredLines() > classCov.getTotalLines()) {
                classCov.setCoveredLines(classCov.getTotalLines());
            }
            if (classCov.getCoveredBranches() > classCov.getTotalBranches()) {
                classCov.setCoveredBranches(classCov.getTotalBranches());
            }
            if (classCov.getCoveredMethods() > classCov.getTotalMethods()) {
                classCov.setCoveredMethods(classCov.getTotalMethods());
            }

            classCov.setLineRate(classCov.getTotalLines() > 0 ? (double) classCov.getCoveredLines() / classCov.getTotalLines() * 100 : 0.0);
            classCov.setBranchRate(calculateBranchRate(classCov.getCoveredBranchTargets(), classCov.getTotalBranchTargets()));
            classCov.setMethodRate(classCov.getTotalMethods() > 0 ? (double) classCov.getCoveredMethods() / classCov.getTotalMethods() * 100 : 0.0);

            if (classCov.getCoveredLines() > 0) coveredClasses++;

            totalMethods += classCov.getTotalMethods();
            coveredMethods += classCov.getCoveredMethods();
            totalLines += classCov.getTotalLines();
            coveredLines += classCov.getCoveredLines();
            totalBranches += classCov.getTotalBranches();
            coveredBranches += classCov.getCoveredBranches();
            totalBranchTargets += classCov.getTotalBranchTargets();
            coveredBranchTargets += classCov.getCoveredBranchTargets();
            totalComplexity += classCov.getTotalComplexity();

            if (CoverageReportTypes.isIncremental(report.getReportType())) {
                incTotalClasses++;
                if (classCov.getCoveredLines() > 0) incCoveredClasses++;

                incTotalLines += classCov.getTotalLines();
                incCoveredLines += classCov.getCoveredLines();
                incTotalMethods += classCov.getTotalMethods();
                incCoveredMethods += classCov.getCoveredMethods();
                incTotalBranches += classCov.getTotalBranches();
                incCoveredBranches += classCov.getCoveredBranches();
                incTotalBranchTargets += classCov.getTotalBranchTargets();
                incCoveredBranchTargets += classCov.getCoveredBranchTargets();
                incTotalComplexity += classCov.getTotalComplexity();
            } else if (diffMap != null && !diffMap.isEmpty()) {
                IncrementalClassSummary incrementalSummary = calculateChangedClassSummary(classCov, diffMap);
                if (!incrementalSummary.hasChangedLines()) {
                    saveBatch(toSave, classCov);
                    continue;
                }
                incTotalClasses++;
                if (incrementalSummary.anyChangedLineCovered) incCoveredClasses++;

                incTotalLines += incrementalSummary.totalLines;
                incCoveredLines += incrementalSummary.coveredLines;
                incTotalMethods += incrementalSummary.totalMethods;
                incCoveredMethods += incrementalSummary.coveredMethods;
                incTotalBranches += incrementalSummary.totalBranches;
                incCoveredBranches += incrementalSummary.coveredBranches;
                incTotalBranchTargets += incrementalSummary.totalBranchTargets;
                incCoveredBranchTargets += incrementalSummary.coveredBranchTargets;
                incTotalComplexity += incrementalSummary.totalComplexity;
            }

            saveBatch(toSave, classCov);
        }
        if (!toSave.isEmpty()) {
            classCoverageRepository.saveAll(toSave);
        }

        coveredMethods = Math.min(coveredMethods, totalMethods);
        coveredLines = Math.min(coveredLines, totalLines);
        coveredBranches = Math.min(coveredBranches, totalBranches);
        coveredBranchTargets = Math.min(coveredBranchTargets, totalBranchTargets);
        coveredClasses = Math.min(coveredClasses, totalClasses);

        report.setTotalClasses(totalClasses);
        report.setCoveredClasses(coveredClasses);
        report.setTotalMethods(totalMethods);
        report.setCoveredMethods(coveredMethods);
        report.setTotalLines(totalLines);
        report.setCoveredLines(coveredLines);
        report.setTotalBranches(totalBranches);
        report.setCoveredBranches(coveredBranches);
        report.setTotalBranchTargets(totalBranchTargets);
        report.setCoveredBranchTargets(coveredBranchTargets);
        report.setTotalComplexity(totalComplexity);

        incCoveredLines = Math.min(incCoveredLines, incTotalLines);
        incCoveredMethods = Math.min(incCoveredMethods, incTotalMethods);
        incCoveredBranches = Math.min(incCoveredBranches, incTotalBranches);
        incCoveredBranchTargets = Math.min(incCoveredBranchTargets, incTotalBranchTargets);
        incCoveredClasses = Math.min(incCoveredClasses, incTotalClasses);

        report.setIncTotalClasses(incTotalClasses);
        report.setIncCoveredClasses(incCoveredClasses);
        report.setIncTotalLines(incTotalLines);
        report.setIncCoveredLines(incCoveredLines);
        report.setIncTotalMethods(incTotalMethods);
        report.setIncCoveredMethods(incCoveredMethods);
        report.setIncTotalBranches(incTotalBranches);
        report.setIncCoveredBranches(incCoveredBranches);
        report.setIncTotalBranchTargets(incTotalBranchTargets);
        report.setIncCoveredBranchTargets(incCoveredBranchTargets);
        report.setIncTotalComplexity(incTotalComplexity);

        coverageReportPersistenceService.saveReportSummary(report);
    }

    private IncrementalClassSummary calculateChangedClassSummary(ClassCoverageIndex classCov,
                                                                 Map<String, List<Integer>> diffMap) {
        List<Integer> changedLines = coverageDiffService.getChangedLinesForClass(diffMap, classCov.getClassName());
        if (changedLines == null || changedLines.isEmpty()) {
            return IncrementalClassSummary.empty();
        }
        Set<Integer> changedLineSet = new HashSet<>(changedLines);
        Set<Integer> coveredLineSet = new HashSet<>();
        IncrementalClassSummary summary = new IncrementalClassSummary();
        summary.totalLines = changedLineSet.size();

        for (MethodCoverageDetail md : classCov.getMethods()) {
            boolean methodHasChanges = false;
            List<Integer> methodLines = md.getTotalLineNumbers();
            if (methodLines != null) {
                for (Integer ln : methodLines) {
                    if (changedLineSet.contains(ln)) {
                        methodHasChanges = true;
                        break;
                    }
                }
            }

            if (methodHasChanges) {
                summary.totalMethods++;
                boolean changedLineCovered = false;
                if (md.getCoveredLineNumbers() != null) {
                    for (Integer cln : md.getCoveredLineNumbers()) {
                        if (changedLineSet.contains(cln)) {
                            changedLineCovered = true;
                            break;
                        }
                    }
                }
                if (changedLineCovered) {
                    summary.coveredMethods++;
                    summary.anyChangedLineCovered = true;
                }

                summary.totalBranches += md.getTotalBranches();
                summary.coveredBranches += md.getCoveredBranches();
                summary.totalBranchTargets += md.getTotalBranchTargets();
                summary.coveredBranchTargets += md.getCoveredBranchTargets();
                summary.totalComplexity += md.getComplexity();
            }

            if (md.getCoveredLineNumbers() != null) {
                coveredLineSet.addAll(md.getCoveredLineNumbers());
            }
        }

        for (Integer line : changedLineSet) {
            if (coveredLineSet.contains(line)) {
                summary.coveredLines++;
            }
        }
        return summary;
    }

    private void saveBatch(List<ClassCoverageIndex> toSave, ClassCoverageIndex classCov) {
        toSave.add(classCov);
        if (toSave.size() >= 100) {
            classCoverageRepository.saveAll(toSave);
            toSave.clear();
        }
    }

    private static class IncrementalClassSummary {
        private long totalLines;
        private long coveredLines;
        private long totalMethods;
        private long coveredMethods;
        private long totalBranches;
        private long coveredBranches;
        private long totalBranchTargets;
        private long coveredBranchTargets;
        private int totalComplexity;
        private boolean anyChangedLineCovered;

        private static IncrementalClassSummary empty() {
            return new IncrementalClassSummary();
        }

        private boolean hasChangedLines() {
            return totalLines > 0;
        }
    }
}
