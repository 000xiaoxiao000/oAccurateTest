package com.oAT.web.api.map;

import com.oAT.agent.model.TraceNode;
import com.oAT.web.domain.AppRelationLayer;
import com.oAT.web.domain.ImageElement;
import com.oAT.web.domain.RemoteCallResolver;
import com.oAT.web.esDao.ApiEndpointRepository;
import com.oAT.web.esDao.entity.ApiEndpointIndex;
import com.oAT.web.esDao.entity.SystemSnapshot;
import com.oAT.web.service.AppService;
import com.oAT.web.service.ClientSessionService;
import com.oAT.web.service.SnapshotService;
import com.oAT.web.service.SystemSnapshotService;
import com.oAT.web.service.entity.AppVo;
import com.oAT.web.service.entity.TraceItemSearchParam;
import com.oAT.web.service.entity.TraceItemVo;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class MapHomePayloadService {

    private final AppService appService;
    private final SystemSnapshotService systemSnapshotService;
    private final SnapshotService snapshotService;
    private final ClientSessionService clientSessionService;
    private final ApiEndpointRepository apiEndpointRepository;

    public MapHomePayloadService(AppService appService,
                                 SystemSnapshotService systemSnapshotService,
                                 SnapshotService snapshotService,
                                 ClientSessionService clientSessionService,
                                 ApiEndpointRepository apiEndpointRepository) {
        this.appService = appService;
        this.systemSnapshotService = systemSnapshotService;
        this.snapshotService = snapshotService;
        this.clientSessionService = clientSessionService;
        this.apiEndpointRepository = apiEndpointRepository;
    }

    public List<ImageElement> buildHomeMapData(String projectId) {
        List<AppVo> appList = appService.getAppList(projectId);
        List<SystemSnapshot> snapshots = systemSnapshotService.findAll(projectId);
        Map<String, Collection<TraceNode>> traceNodesBySnapshotId = snapshots.stream()
                .filter(snapshot -> StringUtils.hasText(snapshot.getTraceId()))
                .collect(Collectors.toMap(SystemSnapshot::getId, this::getTraceNodesSafely, (left, right) -> left));
        Collection<Collection<TraceNode>> liveTraceNodeGroups = getRecentLiveTraceNodeGroups(appList);
        List<ApiEndpointIndex> endpoints = appList.stream()
                .flatMap(app -> apiEndpointRepository.findByAppIdOrderByEndpointTypeAscUrlAsc(app.getId()).stream())
                .collect(Collectors.toList());
        RemoteCallResolver remoteCallResolver = new RemoteCallResolver(appList, endpoints);
        return new AppRelationLayer(appList, snapshots, traceNodesBySnapshotId, liveTraceNodeGroups, remoteCallResolver).elements();
    }

    private Collection<Collection<TraceNode>> getRecentLiveTraceNodeGroups(List<AppVo> appList) {
        TraceItemSearchParam searchParam = new TraceItemSearchParam();
        searchParam.setAppIds(appList.stream().map(AppVo::getId).collect(Collectors.toList()));
        searchParam.setMaxSize(500);
        return clientSessionService.getTraceItemByTime(24 * 60 * 60, searchParam).stream()
                .map(TraceItemVo::getTraceId)
                .filter(StringUtils::hasText)
                .distinct()
                .map(clientSessionService::getTraceNodes)
                .filter(nodes -> nodes != null && !nodes.isEmpty())
                .map(Map::values)
                .collect(Collectors.toList());
    }

    private Collection<TraceNode> getTraceNodesSafely(SystemSnapshot snapshot) {
        try {
            return snapshotService.getTraceNodes(snapshot.getTraceId());
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }
}
