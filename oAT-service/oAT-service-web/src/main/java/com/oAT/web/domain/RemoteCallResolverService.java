package com.oAT.web.domain;

import com.oAT.web.esDao.ApiEndpointRepository;
import com.oAT.web.esDao.entity.ApiEndpointIndex;
import com.oAT.web.service.AppService;
import com.oAT.web.service.entity.AppVo;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class RemoteCallResolverService {
    private final AppService appService;
    private final ApiEndpointRepository apiEndpointRepository;

    public RemoteCallResolverService(AppService appService,
                                     ApiEndpointRepository apiEndpointRepository) {
        this.appService = appService;
        this.apiEndpointRepository = apiEndpointRepository;
    }

    public RemoteCallResolver build(String projectId) {
        List<AppVo> apps = appService.getAppList(projectId);
        List<ApiEndpointIndex> endpoints = apps.stream()
                .flatMap(app -> apiEndpointRepository.findByAppIdOrderByEndpointTypeAscUrlAsc(app.getId()).stream())
                .collect(Collectors.toList());
        return new RemoteCallResolver(apps, endpoints);
    }
}
