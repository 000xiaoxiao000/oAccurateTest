package com.oAT.web.esDao;

import com.oAT.web.esDao.entity.ClientIndex;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;

public interface ClientRepository extends ElasticsearchRepository<ClientIndex, String>{
}
