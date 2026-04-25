package com.oAT.web.esDao;

import com.oAT.web.esDao.entity.ProbeInstanceStatus;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;

import java.util.List;

public interface ProbeInstanceStatusRepository extends ElasticsearchRepository<ProbeInstanceStatus, String> {
    List<ProbeInstanceStatus> findByStatus(String status);

    List<ProbeInstanceStatus> findByAppId(String appId);
}
