package com.oAT.web.service;

import com.oAT.server.model.ClientInfoVo;
import com.oAT.server.model.ClientSessionVo;
import com.oAT.web.esDao.ProbeInstanceStatusRepository;
import com.oAT.web.esDao.entity.ProbeAlertEvent;
import com.oAT.web.esDao.entity.ProbeInstanceStatus;
import com.oAT.web.service.entity.AppVo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Date;
import java.util.List;
import java.util.Optional;

@Service
public class ProbeStatusService {
    private static final Logger logger = LoggerFactory.getLogger(ProbeStatusService.class);
    private static final int DEFAULT_OFFLINE_THRESHOLD_SECONDS = 90;
    private static final int MIN_OFFLINE_THRESHOLD_SECONDS = 30;

    @Autowired
    private ProbeIdentityService probeIdentityService;
    @Autowired
    private ProbeInstanceStatusRepository probeInstanceStatusRepository;
    @Autowired
    private ProbeAlertEventService probeAlertEventService;
    @Autowired
    private AppService appService;

    public void onLogin(ClientSessionVo session) {
        upsertOnlineStatus(session, true);
    }

    public void onHeartbeat(ClientSessionVo session) {
        upsertOnlineStatus(session, false);
    }

    public void scanOfflineProbes() {
        List<ProbeInstanceStatus> onlineStatuses = probeInstanceStatusRepository.findByStatus(ProbeInstanceStatus.Status.ONLINE.toString());
        long nowMillis = System.currentTimeMillis();
        if (logger.isDebugEnabled()) {
            logger.debug("开始扫描探针离线状态, onlineCount={}", onlineStatuses.size());
        }
        for (ProbeInstanceStatus status : onlineStatuses) {
            try {
                AppVo app = appService.getApp(status.getAppId());
                if (app == null) {
                    logger.warn("探针离线扫描跳过，找不到应用配置, probeKey={}, appId={}", status.getProbeKey(), status.getAppId());
                    continue;
                }
                int thresholdSeconds = resolveOfflineThresholdSeconds(app);
                Long lastHeartbeatTime = status.getLastHeartbeatTime();
                if (lastHeartbeatTime == null) {
                    logger.warn("探针离线扫描跳过，缺少最后心跳时间, probeKey={}, appId={}", status.getProbeKey(), status.getAppId());
                    continue;
                }
                long offlineMillis = nowMillis - lastHeartbeatTime;
                if (offlineMillis <= thresholdSeconds * 1000L) {
                    continue;
                }
                logger.info("探针判定下线, probeKey={}, appId={}, thresholdSeconds={}, offlineSeconds={}",
                        status.getProbeKey(), status.getAppId(), thresholdSeconds, offlineMillis / 1000L);
                Date now = new Date(nowMillis);
                status.setStatus(ProbeInstanceStatus.Status.OFFLINE.toString());
                status.setOfflineSince(now);
                status.setLastStatusChangeTime(now);
                status.setUpdateTime(now);
                ProbeAlertEvent event = probeAlertEventService.createOfflineEvent(status, app, thresholdSeconds);
                status.setLastAlertEventType(event.getEventType());
                status.setLastAlertTime(event.getEventTime());
                probeInstanceStatusRepository.save(status);
            } catch (Exception e) {
                logger.error("扫描探针离线状态失败, probeKey={}, appId={}", status.getProbeKey(), status.getAppId(), e);
            }
        }
    }

    private void upsertOnlineStatus(ClientSessionVo session, boolean login) {
        if (session == null || session.getClientInfo() == null) {
            return;
        }
        ClientInfoVo clientInfo = session.getClientInfo();
        String probeKey = probeIdentityService.buildProbeKey(session);
        if (!StringUtils.hasText(probeKey)) {
            return;
        }
        AppVo app;
        try {
            app = appService.getApp(clientInfo.getAppKey());
        } catch (Exception e) {
            logger.warn("探针状态更新跳过，找不到应用 appId={}, sessionId={}", clientInfo.getAppKey(), session.getSessionId());
            return;
        }
        Date now = new Date();
        long heartbeatTime = session.getLastHeartbeatTime() != null ? session.getLastHeartbeatTime() : now.getTime();
        Optional<ProbeInstanceStatus> optional = probeInstanceStatusRepository.findById(probeKey);
        if (optional.isPresent()) {
            ProbeInstanceStatus status = optional.get();
            boolean recovered = ProbeInstanceStatus.Status.OFFLINE.toString().equals(status.getStatus());
            Long offlineDurationMillis = status.getOfflineSince() == null ? null : now.getTime() - status.getOfflineSince().getTime();
            fillStatus(status, session, app, probeKey, heartbeatTime);
            status.setStatus(ProbeInstanceStatus.Status.ONLINE.toString());
            status.setOfflineSince(null);
            if (recovered) {
                status.setOnlineSince(now);
                status.setLastStatusChangeTime(now);
                logger.info("探针恢复上线, probeKey={}, appId={}, offlineSeconds={}",
                        status.getProbeKey(), status.getAppId(), offlineDurationMillis == null ? 0 : offlineDurationMillis / 1000L);
                ProbeAlertEvent event = probeAlertEventService.createRecoveredEvent(status, app, offlineDurationMillis);
                status.setLastAlertEventType(event.getEventType());
                status.setLastAlertTime(event.getEventTime());
            } else if (login) {
                status.setOnlineSince(now);
                status.setLastStatusChangeTime(now);
                logger.info("探针新会话上线, probeKey={}, appId={}, sessionId={}", probeKey, status.getAppId(), status.getSessionId());
                ProbeAlertEvent event = probeAlertEventService.createOnlineEvent(status, app);
                status.setLastAlertEventType(event.getEventType());
                status.setLastAlertTime(event.getEventTime());
            }
            status.setUpdateTime(now);
            probeInstanceStatusRepository.save(status);
            return;
        }

        ProbeInstanceStatus status = new ProbeInstanceStatus();
        status.setId(probeKey);
        status.setProbeKey(probeKey);
        fillStatus(status, session, app, probeKey, heartbeatTime);
        status.setStatus(ProbeInstanceStatus.Status.ONLINE.toString());
        status.setOnlineSince(now);
        status.setLastStatusChangeTime(now);
        status.setCreateTime(now);
        status.setUpdateTime(now);
        if (login) {
            logger.info("探针首次上线, probeKey={}, appId={}, sessionId={}", probeKey, status.getAppId(), status.getSessionId());
            ProbeAlertEvent event = probeAlertEventService.createOnlineEvent(status, app);
            status.setLastAlertEventType(event.getEventType());
            status.setLastAlertTime(event.getEventTime());
        }
        probeInstanceStatusRepository.save(status);
    }

    private void fillStatus(ProbeInstanceStatus status, ClientSessionVo session, AppVo app, String probeKey, long heartbeatTime) {
        ClientInfoVo clientInfo = session.getClientInfo();
        status.setProbeKey(probeKey);
        status.setProjectId(app.getCreateProjectId());
        status.setAppId(clientInfo.getAppKey());
        status.setAppName(app.getName());
        status.setSessionId(session.getSessionId());
        status.setAddressIp(clientInfo.getAddressIp());
        status.setPid(clientInfo.getPid());
        status.setSystemDir(clientInfo.getSystemDir());
        status.setAgentVersion(clientInfo.getAgentVersion());
        status.setLoginTime(session.getLoginTime());
        status.setLastHeartbeatTime(heartbeatTime);
    }

    private int resolveOfflineThresholdSeconds(AppVo app) {
        Integer threshold = app == null ? null : app.getProbeOfflineThresholdSeconds();
        if (threshold == null || threshold <= 0) {
            return DEFAULT_OFFLINE_THRESHOLD_SECONDS;
        }
        return Math.max(MIN_OFFLINE_THRESHOLD_SECONDS, threshold);
    }
}
