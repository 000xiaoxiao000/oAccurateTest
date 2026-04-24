package com.oAT.web.esDao;

import com.oAT.web.esDao.entity.ApiEndpointIndex;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;

import java.util.List;

public interface ApiEndpointRepository extends ElasticsearchRepository<ApiEndpointIndex, String> {
    List<ApiEndpointIndex> findByAppIdOrderByEndpointTypeAscUrlAsc(String appId);

    void deleteByAppId(String appId);
}
