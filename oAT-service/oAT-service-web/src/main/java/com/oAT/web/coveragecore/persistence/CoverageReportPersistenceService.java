package com.oAT.web.coveragecore.persistence;

import com.oAT.web.esDao.ClassCoverageRepository;
import com.oAT.web.esDao.CoverageReportRepository;
import com.oAT.web.esDao.entity.ClassCoverageIndex;
import com.oAT.web.esDao.entity.CoverageReportIndex;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

@Service
public class CoverageReportPersistenceService {
    private final CoverageReportRepository coverageReportRepository;
    private final ClassCoverageRepository classCoverageRepository;

    public CoverageReportPersistenceService(CoverageReportRepository coverageReportRepository,
                                            ClassCoverageRepository classCoverageRepository) {
        this.coverageReportRepository = coverageReportRepository;
        this.classCoverageRepository = classCoverageRepository;
    }

    @Transactional
    public void saveReport(CoverageReportIndex report, List<ClassCoverageIndex> classCoverages) {
        List<ClassCoverageIndex> normalized = new ArrayList<>();
        for (ClassCoverageIndex classCoverage : classCoverages == null ? List.<ClassCoverageIndex>of() : classCoverages) {
            normalizeClassCoverage(report, classCoverage);
            normalized.add(classCoverage);
        }
        applyReportSummary(report, normalized);
        coverageReportRepository.save(report);
        classCoverageRepository.saveAll(normalized);
    }

    @Transactional
    public CoverageReportIndex saveReportSummary(CoverageReportIndex report) {
        return coverageReportRepository.save(report);
    }

    private void normalizeClassCoverage(CoverageReportIndex report, ClassCoverageIndex classCoverage) {
        classCoverage.setReportId(report.getId());
        if (!StringUtils.hasText(classCoverage.getId())) {
            classCoverage.setId(report.getId() + "_" + classCoverage.getClassName().hashCode());
        }
        if (classCoverage.getCoveredLines() > classCoverage.getTotalLines()) {
            classCoverage.setCoveredLines(classCoverage.getTotalLines());
        }
        if (classCoverage.getCoveredBranches() > classCoverage.getTotalBranches()) {
            classCoverage.setCoveredBranches(classCoverage.getTotalBranches());
        }
        if (classCoverage.getCoveredBranchTargets() > classCoverage.getTotalBranchTargets()) {
            classCoverage.setCoveredBranchTargets(classCoverage.getTotalBranchTargets());
        }
        if (classCoverage.getCoveredMethods() > classCoverage.getTotalMethods()) {
            classCoverage.setCoveredMethods(classCoverage.getTotalMethods());
        }
        classCoverage.setLineRate(classCoverage.getTotalLines() > 0 ? (double) classCoverage.getCoveredLines() / classCoverage.getTotalLines() * 100 : 0.0);
        classCoverage.setBranchRate(classCoverage.getTotalBranchTargets() > 0 ? (double) classCoverage.getCoveredBranchTargets() / classCoverage.getTotalBranchTargets() * 100 : 0.0);
        classCoverage.setMethodRate(classCoverage.getTotalMethods() > 0 ? (double) classCoverage.getCoveredMethods() / classCoverage.getTotalMethods() * 100 : 0.0);
    }

    private void applyReportSummary(CoverageReportIndex report, List<ClassCoverageIndex> classCoverages) {
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

        for (ClassCoverageIndex classCoverage : classCoverages) {
            if (classCoverage.getCoveredLines() > 0) {
                coveredClasses++;
            }
            totalMethods += classCoverage.getTotalMethods();
            coveredMethods += classCoverage.getCoveredMethods();
            totalBranches += classCoverage.getTotalBranches();
            coveredBranches += classCoverage.getCoveredBranches();
            totalBranchTargets += classCoverage.getTotalBranchTargets();
            coveredBranchTargets += classCoverage.getCoveredBranchTargets();
            totalLines += classCoverage.getTotalLines();
            coveredLines += classCoverage.getCoveredLines();
            totalComplexity += classCoverage.getTotalComplexity();
        }

        report.setTotalClasses(classCoverages.size());
        report.setCoveredClasses(Math.min(coveredClasses, report.getTotalClasses()));
        report.setTotalMethods(totalMethods);
        report.setCoveredMethods(Math.min(coveredMethods, totalMethods));
        report.setTotalBranches(totalBranches);
        report.setCoveredBranches(Math.min(coveredBranches, totalBranches));
        report.setTotalBranchTargets(totalBranchTargets);
        report.setCoveredBranchTargets(Math.min(coveredBranchTargets, totalBranchTargets));
        report.setTotalLines(totalLines);
        report.setCoveredLines(Math.min(coveredLines, totalLines));
        report.setTotalComplexity(totalComplexity);
    }
}
