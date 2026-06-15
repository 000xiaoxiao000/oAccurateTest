package com.oAT.web.coverage.universal;

import com.oAT.web.esDao.ClassCoverageRepository;
import com.oAT.web.esDao.CoverageReportRepository;
import com.oAT.web.esDao.entity.ClassCoverageIndex;
import com.oAT.web.esDao.entity.CoverageReportIndex;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class UniversalCoverageService {
    private final CoverageReportRepository coverageReportRepository;
    private final ClassCoverageRepository classCoverageRepository;

    public UniversalCoverageService(CoverageReportRepository coverageReportRepository,
                                    ClassCoverageRepository classCoverageRepository) {
        this.coverageReportRepository = coverageReportRepository;
        this.classCoverageRepository = classCoverageRepository;
    }

    public Map<String, UniversalCoverageFile> mergeFiles(List<UniversalCoverageFile> files) {
        Map<String, UniversalCoverageFile> merged = new LinkedHashMap<>();
        if (files == null) {
            return merged;
        }
        for (UniversalCoverageFile file : files) {
            if (file == null || file.getFilePath() == null) {
                continue;
            }
            merged.compute(file.getFilePath(), (key, existing) -> existing == null ? file : existing.merge(file));
        }
        return merged;
    }

    @Transactional
    public void saveReport(CoverageReportIndex report, String appId, Map<String, UniversalCoverageFile> coverageMap) {
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

        List<ClassCoverageIndex> batch = new ArrayList<>();
        for (UniversalCoverageFile fileCoverage : coverageMap.values()) {
            ClassCoverageIndex item = fileCoverage.toClassCoverageIndex(appId);
            item.setReportId(report.getId());
            item.setId(fileCoverage.stableId(report.getId()));
            if (item.getCoveredLines() > 0) {
                coveredClasses++;
            }
            totalMethods += item.getTotalMethods();
            coveredMethods += item.getCoveredMethods();
            totalBranches += item.getTotalBranches();
            coveredBranches += item.getCoveredBranches();
            totalBranchTargets += item.getTotalBranchTargets();
            coveredBranchTargets += item.getCoveredBranchTargets();
            totalLines += item.getTotalLines();
            coveredLines += item.getCoveredLines();
            totalComplexity += item.getTotalComplexity();
            batch.add(item);
        }

        report.setTotalClasses(coverageMap.size());
        report.setCoveredClasses(coveredClasses);
        report.setTotalMethods(totalMethods);
        report.setCoveredMethods(coveredMethods);
        report.setTotalBranches(totalBranches);
        report.setCoveredBranches(coveredBranches);
        report.setTotalBranchTargets(totalBranchTargets);
        report.setCoveredBranchTargets(coveredBranchTargets);
        report.setTotalLines(totalLines);
        report.setCoveredLines(coveredLines);
        report.setTotalComplexity(totalComplexity);

        coverageReportRepository.save(report);
        classCoverageRepository.saveAll(batch);
    }
}
