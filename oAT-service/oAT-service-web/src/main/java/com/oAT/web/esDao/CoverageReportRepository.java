package com.oAT.web.esDao;

import com.oAT.web.esDao.entity.CoverageReportIndex;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;

import java.util.List;

public interface CoverageReportRepository extends ElasticsearchRepository<CoverageReportIndex, String> {
    List<CoverageReportIndex> findByAppId(String appId);
    List<CoverageReportIndex> findByAppIdAndVersionNumber(String appId, String versionNumber);
    long countByAppIdAndVersionNumber(String appId, String versionNumber);
    List<CoverageReportIndex> findByAppIdAndVersionNumberAndReportType(String appId, String versionNumber, Integer reportType);
    List<CoverageReportIndex> findByAppIdAndVersionNumberAndRepoCommitId(String appId, String versionNumber, String repoCommitId);
    List<CoverageReportIndex> findByAppIdAndVersionNumberAndReportTypeAndRepoCommitId(String appId, String versionNumber, Integer reportType, String repoCommitId);
    List<CoverageReportIndex> findByAppIdAndRepoBranch(String appId, String repoBranch);
}
