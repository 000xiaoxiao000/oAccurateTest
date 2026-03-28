package com.oAT.web.esDao;

import com.oAT.web.esDao.entity.ResourceIndex;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;


public interface ResourceRepository extends ElasticsearchRepository<ResourceIndex, String> {}
