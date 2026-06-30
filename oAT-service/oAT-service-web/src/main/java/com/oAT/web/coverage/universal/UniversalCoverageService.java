package com.oAT.web.coverage.universal;

import com.oAT.web.coveragecore.persistence.CoverageReportPersistenceService;
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
    private final CoverageReportPersistenceService coverageReportPersistenceService;

    public UniversalCoverageService(CoverageReportPersistenceService coverageReportPersistenceService) {
        this.coverageReportPersistenceService = coverageReportPersistenceService;
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
        List<ClassCoverageIndex> batch = new ArrayList<>();
        for (UniversalCoverageFile fileCoverage : coverageMap.values()) {
            ClassCoverageIndex item = fileCoverage.toClassCoverageIndex(appId);
            item.setId(fileCoverage.stableId(report.getId()));
            batch.add(item);
        }
        coverageReportPersistenceService.saveReport(report, batch);
    }
}
