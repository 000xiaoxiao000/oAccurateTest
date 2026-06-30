package com.oAT.web.coveragecore.report;

import com.oAT.web.esDao.ClassCoverageRepository;
import com.oAT.web.esDao.CoverageReportRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class CoverageReportDeletionService {
    private static final Logger logger = LoggerFactory.getLogger(CoverageReportDeletionService.class);

    private final ClassCoverageRepository classCoverageRepository;
    private final CoverageReportRepository coverageReportRepository;

    public CoverageReportDeletionService(ClassCoverageRepository classCoverageRepository,
                                         CoverageReportRepository coverageReportRepository) {
        this.classCoverageRepository = classCoverageRepository;
        this.coverageReportRepository = coverageReportRepository;
    }

    public void deleteReport(String reportId) {
        if (!StringUtils.hasText(reportId)) {
            return;
        }
        logger.info("Deleting coverage report: {}", reportId);
        classCoverageRepository.deleteByReportId(reportId);
        coverageReportRepository.deleteById(reportId);
        logger.info("Coverage report and its details deleted successfully: {}", reportId);
    }
}
