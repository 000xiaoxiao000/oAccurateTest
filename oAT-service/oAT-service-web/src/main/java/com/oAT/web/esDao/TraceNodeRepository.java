package com.oAT.web.esDao;

import com.oAT.web.esDao.entity.TraceNodeIndex;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;

import java.util.List;

public interface TraceNodeRepository extends ElasticsearchRepository<TraceNodeIndex, String>{
    List<TraceNodeIndex> findByTraceId(String traceId, Pageable pageable);
    List<TraceNodeIndex> findByAppId(String appId);
    Page<TraceNodeIndex> findByAppIdAndCreateTimeGreaterThanOrderByCreateTimeAsc(String appId, String createTime, Pageable pageable);
}
