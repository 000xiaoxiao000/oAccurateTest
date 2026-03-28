package com.oAT.web.esDao;

import com.oAT.web.esDao.entity.ClassCoverageIndex;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;

import java.util.List;

public interface ClassCoverageRepository extends ElasticsearchRepository<ClassCoverageIndex, String> {
    Page<ClassCoverageIndex> findByReportId(String reportId, Pageable pageable);
    List<ClassCoverageIndex> findByReportId(String reportId);
}
