package com.oAT.web.esDao;

import com.oAT.web.esDao.entity.StaticSourceInfo;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;

import java.util.List;

public interface StaticInfoRepository extends ElasticsearchRepository<StaticSourceInfo, String> {
    List<StaticSourceInfo> findByAppIdAndClassInfo_ClassName(String appId, String className);

    List<StaticSourceInfo> findByAppId(String appId);
}
