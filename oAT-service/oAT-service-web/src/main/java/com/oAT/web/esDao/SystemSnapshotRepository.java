package com.oAT.web.esDao;

import com.oAT.web.esDao.entity.SystemSnapshot;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;

import java.util.List;

public interface SystemSnapshotRepository extends ElasticsearchRepository<SystemSnapshot, String>{

    List<SystemSnapshot> findByProjectIdAndAppIdAndDirectory(String projectId, String appId, String directory);

    List<SystemSnapshot> findByProjectIdAndAppId(String projectId, String appId);

    List<SystemSnapshot> findByProjectId(String projectId);

    List<SystemSnapshot> findByAppId(String appId);

    List<SystemSnapshot> findByProjectIdAndTraceId(String projectId, String traceId);

}
