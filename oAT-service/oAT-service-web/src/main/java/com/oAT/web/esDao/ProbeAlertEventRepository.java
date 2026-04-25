package com.oAT.web.esDao;

import com.oAT.web.esDao.entity.ProbeAlertEvent;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;

import java.util.List;

public interface ProbeAlertEventRepository extends ElasticsearchRepository<ProbeAlertEvent, String> {
    List<ProbeAlertEvent> findByAppId(String appId);
}
