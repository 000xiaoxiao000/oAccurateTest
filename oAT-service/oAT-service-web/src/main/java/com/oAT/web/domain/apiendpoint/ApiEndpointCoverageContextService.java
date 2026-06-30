package com.oAT.web.domain.apiendpoint;

import com.oAT.web.esDao.CaseCenterRepository;
import com.oAT.web.esDao.SystemSnapshotRepository;
import com.oAT.web.esDao.TraceNodeRepository;
import com.oAT.web.esDao.entity.CaseCenterIndex;
import com.oAT.web.esDao.entity.Snapshot;
import com.oAT.web.esDao.entity.SystemSnapshot;
import com.oAT.web.esDao.entity.TraceNodeIndex;
import com.oAT.web.esDao.entity.Usecase;
import com.oAT.web.service.entity.ApiEndpointViewVo;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class ApiEndpointCoverageContextService {

    private final TraceNodeRepository traceNodeRepository;
    private final CaseCenterRepository caseCenterRepository;
    private final SystemSnapshotRepository systemSnapshotRepository;

    public ApiEndpointCoverageContextService(TraceNodeRepository traceNodeRepository,
                                             CaseCenterRepository caseCenterRepository,
                                             SystemSnapshotRepository systemSnapshotRepository) {
        this.traceNodeRepository = traceNodeRepository;
        this.caseCenterRepository = caseCenterRepository;
        this.systemSnapshotRepository = systemSnapshotRepository;
    }

    public Map<String, Integer> buildCoverageMap(String appId) {
        Map<String, TraceNodeIndex> traceMap = new LinkedHashMap<>();
        addTraceIndexes(traceMap, traceNodeRepository.findByAppId(appId), appId);
        for (String traceId : findSnapshotTraceIds(appId)) {
            addTraceIndexes(traceMap, traceNodeRepository.findByTraceId(traceId, PageRequest.of(0, 10000)), appId);
        }
        return buildCoverageMap(traceMap);
    }

    public Map<String, Integer> buildCoverageMap(String appId, java.util.Collection<String> traceIds) {
        Map<String, TraceNodeIndex> traceMap = new LinkedHashMap<>();
        if (traceIds != null) {
            for (String traceId : traceIds) {
                if (StringUtils.hasText(traceId)) {
                    addTraceIndexes(traceMap, traceNodeRepository.findByTraceId(traceId, PageRequest.of(0, 10000)), appId);
                }
            }
        }
        return buildCoverageMap(traceMap);
    }

    public Map<String, List<ApiEndpointViewVo.UsecaseLinkVo>> buildUsecaseLinksByCoverageKey(String appId) {
        Map<String, List<ApiEndpointViewVo.UsecaseLinkVo>> result = new LinkedHashMap<>();
        Map<String, TraceNodeIndex> traceMap = new LinkedHashMap<>();

        List<CaseCenterIndex> snapshots = caseCenterRepository.findBySnapshot_AppId(appId);
        for (CaseCenterIndex snapshotIndex : snapshots) {
            if (snapshotIndex == null || snapshotIndex.getSnapshot() == null || !StringUtils.hasText(snapshotIndex.getId())) {
                continue;
            }
            Snapshot snapshot = snapshotIndex.getSnapshot();
            if (!StringUtils.hasText(snapshot.getTraceId())) {
                continue;
            }
            List<ApiEndpointViewVo.UsecaseLinkVo> links = findUsecasesBySnapshot(snapshot.getProjectId(), snapshotIndex.getId());
            if (links.isEmpty()) {
                continue;
            }
            addUsecaseLinksByTraceId(result, traceMap, appId, snapshot.getTraceId(), links);
        }

        List<SystemSnapshot> systemSnapshots = systemSnapshotRepository.findByAppId(appId);
        for (SystemSnapshot snapshot : systemSnapshots) {
            if (snapshot == null || !StringUtils.hasText(snapshot.getId()) || !StringUtils.hasText(snapshot.getTraceId())) {
                continue;
            }
            List<ApiEndpointViewVo.UsecaseLinkVo> links = findUsecasesBySystemSnapshot(snapshot.getProjectId(), snapshot.getId());
            if (links.isEmpty()) {
                continue;
            }
            addUsecaseLinksByTraceId(result, traceMap, appId, snapshot.getTraceId(), links);
        }
        return result;
    }

    private Map<String, Integer> buildCoverageMap(Map<String, TraceNodeIndex> traceMap) {
        Map<String, Integer> hitMap = new HashMap<>();
        for (TraceNodeIndex item : traceMap.values()) {
            if (item == null || item.getType() == null) {
                continue;
            }
            String type = item.getType();
            if ("http".equals(type)) {
                addHit(hitMap, "HTTP", item.getHttpMethod(), item.getHttpUrl());
            } else if ("httpClient".equals(type)) {
                addHit(hitMap, "HTTP_CLIENT", item.getRemoteMethod(), item.getRemoteUrl());
            } else if ("feign".equals(type)) {
                addHit(hitMap, "FEIGN", item.getRemoteMethod(), firstText(item.getRemoteUrl()));
            } else if ("dubbo".equals(type)) {
                String rpcTarget = item.getRemoteInterface();
                if (StringUtils.hasText(rpcTarget) && StringUtils.hasText(item.getRemoteMethod())) {
                    rpcTarget = rpcTarget + "#" + item.getRemoteMethod();
                }
                addHit(hitMap, "RPC", "INVOKE", rpcTarget);
            }
        }
        return hitMap;
    }

    private void addTraceIndexes(Map<String, TraceNodeIndex> traceMap, List<TraceNodeIndex> traces, String appId) {
        if (traces == null) {
            return;
        }
        for (TraceNodeIndex trace : traces) {
            if (trace == null) {
                continue;
            }
            if (StringUtils.hasText(appId) && !appId.equals(trace.getAppId())) {
                continue;
            }
            String key = StringUtils.hasText(trace.getId()) ? trace.getId() : trace.getTraceId() + "_" + trace.getTraceNodeId();
            traceMap.putIfAbsent(key, trace);
        }
    }

    private Set<String> findSnapshotTraceIds(String appId) {
        Set<String> traceIds = new LinkedHashSet<>();
        List<CaseCenterIndex> caseCenters = caseCenterRepository.findBySnapshot_AppId(appId);
        for (CaseCenterIndex item : caseCenters) {
            if (item == null || item.getSnapshot() == null) {
                continue;
            }
            if (appId.equals(item.getSnapshot().getAppId()) && StringUtils.hasText(item.getSnapshot().getTraceId())) {
                traceIds.add(item.getSnapshot().getTraceId());
            }
        }
        List<SystemSnapshot> systemSnapshots = systemSnapshotRepository.findByAppId(appId);
        for (SystemSnapshot snapshot : systemSnapshots) {
            if (snapshot != null && appId.equals(snapshot.getAppId()) && StringUtils.hasText(snapshot.getTraceId())) {
                traceIds.add(snapshot.getTraceId());
            }
        }
        return traceIds;
    }

    private void addHit(Map<String, Integer> hitMap, String type, String httpMethod, String target) {
        if (!StringUtils.hasText(target)) {
            return;
        }
        String key = ApiEndpointCoverageMatcher.coverageKey(type, httpMethod, target);
        hitMap.put(key, hitMap.getOrDefault(key, 0) + 1);
    }

    private String firstText(String... values) {
        if (values == null) {
            return "";
        }
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return "";
    }

    private void addUsecaseLinksByTraceId(Map<String, List<ApiEndpointViewVo.UsecaseLinkVo>> result,
                                          Map<String, TraceNodeIndex> traceMap,
                                          String appId,
                                          String traceId,
                                          List<ApiEndpointViewVo.UsecaseLinkVo> links) {
        addTraceIndexes(traceMap, traceNodeRepository.findByTraceId(traceId, PageRequest.of(0, 10000)), appId);
        for (TraceNodeIndex item : traceMap.values()) {
            if (!traceId.equals(item.getTraceId())) {
                continue;
            }
            String key = ApiEndpointCoverageMatcher.coverageKey(item);
            if (StringUtils.hasText(key)) {
                addUsecaseLinks(result, key, links);
            }
        }
    }

    private void addUsecaseLinks(Map<String, List<ApiEndpointViewVo.UsecaseLinkVo>> result,
                                 String key,
                                 List<ApiEndpointViewVo.UsecaseLinkVo> links) {
        List<ApiEndpointViewVo.UsecaseLinkVo> target = result.computeIfAbsent(key, ignored -> new ArrayList<>());
        Set<String> existingIds = target.stream().map(ApiEndpointViewVo.UsecaseLinkVo::getId).collect(Collectors.toSet());
        for (ApiEndpointViewVo.UsecaseLinkVo link : links) {
            if (link != null && StringUtils.hasText(link.getId()) && existingIds.add(link.getId())) {
                target.add(link);
            }
        }
    }

    private List<ApiEndpointViewVo.UsecaseLinkVo> findUsecasesBySnapshot(String projectId, String snapshotId) {
        return caseCenterRepository.findByUsecase_SnapshotsContaining(snapshotId).stream()
                .filter(index -> index != null && index.getUsecase() != null && projectId.equals(index.getUsecase().getProjectId()))
                .map(this::toUsecaseLinkVo)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    private List<ApiEndpointViewVo.UsecaseLinkVo> findUsecasesBySystemSnapshot(String projectId, String snapshotId) {
        return caseCenterRepository.findByUsecase_SystemSnapshotsContaining(snapshotId).stream()
                .filter(index -> index != null && index.getUsecase() != null && projectId.equals(index.getUsecase().getProjectId()))
                .map(this::toUsecaseLinkVo)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    private ApiEndpointViewVo.UsecaseLinkVo toUsecaseLinkVo(CaseCenterIndex index) {
        Usecase usecase = index.getUsecase();
        if (usecase == null) {
            return null;
        }
        ApiEndpointViewVo.UsecaseLinkVo link = new ApiEndpointViewVo.UsecaseLinkVo();
        link.setId(index.getId());
        link.setTitle(usecase.getTitle());
        link.setDirectory(usecase.getDirectory());
        return link;
    }
}
