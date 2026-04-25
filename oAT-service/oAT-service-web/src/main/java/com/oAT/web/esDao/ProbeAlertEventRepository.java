package com.oAT.web.esDao;

import com.oAT.web.esDao.entity.ProbeAlertEvent;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;

public interface ProbeAlertEventRepository extends ElasticsearchRepository<ProbeAlertEvent, String> {
}
