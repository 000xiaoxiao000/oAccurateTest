package com.oAT.web.collector;

import com.oAT.server.model.ClientInfoVo;
import com.oAT.server.model.ClientSessionVo;
import com.oAT.web.coverage.FrontendCoverageReportRepository;
import com.oAT.web.coverage.UniversalCoverageRawRepository;
import com.oAT.web.coveragecore.model.CoverageLanguage;
import com.oAT.web.service.AppService;
import com.oAT.web.service.ClientSessionService;
import com.oAT.web.service.entity.AppVo;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class CollectorSourceService {
    private final AppService appService;
    private final ClientSessionService clientSessionService;
    private final FrontendCoverageReportRepository frontendCoverageReportRepository;
    private final UniversalCoverageRawRepository universalCoverageRawRepository;
    private final long batchSilentThresholdMillis;

    public CollectorSourceService(AppService appService,
                                  ClientSessionService clientSessionService,
                                  FrontendCoverageReportRepository frontendCoverageReportRepository,
                                  UniversalCoverageRawRepository universalCoverageRawRepository,
                                  @Value("${collector.batch.silent-threshold-millis:3600000}") long batchSilentThresholdMillis) {
        this.appService = appService;
        this.clientSessionService = clientSessionService;
        this.frontendCoverageReportRepository = frontendCoverageReportRepository;
        this.universalCoverageRawRepository = universalCoverageRawRepository;
        this.batchSilentThresholdMillis = batchSilentThresholdMillis;
    }

    public List<CollectorSource> listProjectSources(String projectId) {
        List<CollectorSource> result = new ArrayList<>();
        for (AppVo app : appService.getAppList(projectId)) {
            result.addAll(listAppSources(projectId, app));
        }
        return result;
    }

    public List<CollectorSource> listAppSources(String projectId, String appId) {
        AppVo app = appService.getApp(appId);
        if (app == null) {
            return new ArrayList<>();
        }
        return listAppSources(projectId, app);
    }

    private List<CollectorSource> listAppSources(String projectId, AppVo app) {
        CoverageLanguage language = CoverageLanguage.from(app.getLanguage());
        if (language == CoverageLanguage.JAVA) {
            return residentSources(projectId, app);
        }
        List<CollectorSource> sources = new ArrayList<>();
        sources.add(batchSource(projectId, app, language));
        return sources;
    }

    private List<CollectorSource> residentSources(String projectId, AppVo app) {
        List<CollectorSource> sources = new ArrayList<>();
        for (ClientSessionVo session : clientSessionService.getOnlineSessionsByAppId(app.getId())) {
            CollectorSource source = new CollectorSource();
            source.setSourceId(session.getSessionId());
            source.setProjectId(projectId);
            source.setAppId(app.getId());
            source.setAppName(app.getName());
            source.setLanguage(CoverageLanguage.JAVA.name());
            source.setCollectorType(CollectorSource.CollectorType.RESIDENT);
            source.setHealth(CollectorSource.Health.ONLINE);
            source.setSessionId(session.getSessionId());
            source.setLastSeenTime(session.getLastHeartbeatTime());
            source.setSandboxStatus(session.getSandboxStatus());
            ClientInfoVo clientInfo = session.getClientInfo();
            if (clientInfo != null) {
                source.setAddressIp(clientInfo.getAddressIp());
                source.setPid(clientInfo.getPid());
                source.setAgentVersion(clientInfo.getAgentVersion());
            }
            sources.add(source);
        }
        if (sources.isEmpty()) {
            CollectorSource source = baseSource(projectId, app, CoverageLanguage.JAVA);
            source.setSourceId(app.getId() + ":JAVA");
            source.setCollectorType(CollectorSource.CollectorType.RESIDENT);
            source.setHealth(CollectorSource.Health.OFFLINE);
            sources.add(source);
        }
        return sources;
    }

    private CollectorSource batchSource(String projectId, AppVo app, CoverageLanguage language) {
        CollectorSource source = baseSource(projectId, app, language);
        source.setSourceId(app.getId() + ":" + language.name());
        source.setCollectorType(CollectorSource.CollectorType.BATCH);
        source.setReportIntervalMillis(batchSilentThresholdMillis);
        Long lastSeen = latestBatchReportTime(app.getId(), language);
        source.setLastSeenTime(lastSeen);
        if (lastSeen == null || lastSeen <= 0) {
            source.setHealth(CollectorSource.Health.UNKNOWN);
        } else if (System.currentTimeMillis() - lastSeen > batchSilentThresholdMillis) {
            source.setHealth(CollectorSource.Health.SILENT);
        } else {
            source.setHealth(CollectorSource.Health.ONLINE);
        }
        return source;
    }

    private CollectorSource baseSource(String projectId, AppVo app, CoverageLanguage language) {
        CollectorSource source = new CollectorSource();
        source.setProjectId(projectId);
        source.setAppId(app.getId());
        source.setAppName(app.getName());
        source.setLanguage(language.name());
        return source;
    }

    private Long latestBatchReportTime(String appId, CoverageLanguage language) {
        if (language == CoverageLanguage.FRONTEND) {
            return frontendCoverageReportRepository.findLatestTimestampByApp(appId);
        }
        return universalCoverageRawRepository.findLatestTimestampByAppAndType(appId, language.name());
    }
}
