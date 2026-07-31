package com.oAT.web.service;

import com.oAT.web.esDao.ProbeAlertEventRepository;
import com.oAT.web.esDao.entity.ProbeAlertEvent;
import com.oAT.web.esDao.entity.ProbeInstanceStatus;
import com.oAT.web.service.entity.AppVo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Date;

@Service
public class ProbeAlertEventService {
    private static final Logger logger = LoggerFactory.getLogger(ProbeAlertEventService.class);

    @Autowired
    private ProbeAlertEventRepository probeAlertEventRepository;

    @Autowired
    private ProbeAlertSseService probeAlertSseService;

    public ProbeAlertEvent createOnlineEvent(ProbeInstanceStatus status, AppVo app) {
        ProbeAlertEvent event = createBaseEvent(status, ProbeAlertEvent.EventType.ONLINE, "探针上线：" + describeProbe(status));
        return saveAndNotify(event, app, app != null && Boolean.TRUE.equals(app.getProbeAlertOnOnline()));
    }

    public ProbeAlertEvent createOfflineEvent(ProbeInstanceStatus status, AppVo app, int offlineThresholdSeconds) {
        String message = String.format("探针下线：%s 已超过 %s 秒未上报心跳", describeProbe(status), offlineThresholdSeconds);
        ProbeAlertEvent event = createBaseEvent(status, ProbeAlertEvent.EventType.OFFLINE, message);
        return saveAndNotify(event, app, app != null && Boolean.TRUE.equals(app.getProbeAlertOnOffline()));
    }

    public ProbeAlertEvent createRecoveredEvent(ProbeInstanceStatus status, AppVo app, Long offlineDurationMillis) {
        long offlineDurationSeconds = offlineDurationMillis == null ? 0 : Math.max(0L, offlineDurationMillis / 1000L);
        String message = String.format("探针恢复上线：%s，离线约 %s 秒", describeProbe(status), offlineDurationSeconds);
        ProbeAlertEvent event = createBaseEvent(status, ProbeAlertEvent.EventType.RECOVERED, message);
        event.setOfflineDurationMillis(offlineDurationMillis);
        return saveAndNotify(event, app, app != null && Boolean.TRUE.equals(app.getProbeAlertOnRecovered()));
    }

    private ProbeAlertEvent createBaseEvent(ProbeInstanceStatus status, ProbeAlertEvent.EventType eventType, String message) {
        Date now = new Date();
        ProbeAlertEvent event = new ProbeAlertEvent();
        event.setProjectId(status.getProjectId());
        event.setAppId(status.getAppId());
        event.setAppName(status.getAppName());
        event.setProbeKey(status.getProbeKey());
        event.setSessionId(status.getSessionId());
        event.setAddressIp(status.getAddressIp());
        event.setPid(status.getPid());
        event.setSystemDir(status.getSystemDir());
        event.setAgentVersion(status.getAgentVersion());
        event.setEventType(eventType.toString());
        event.setEventTime(now);
        event.setLastHeartbeatTime(status.getLastHeartbeatTime());
        event.setMessage(message);
        event.setCreateTime(now);
        event.setUpdateTime(now);
        return event;
    }

    private ProbeAlertEvent saveAndNotify(ProbeAlertEvent event, AppVo app, boolean eventTypeEnabled) {
        boolean alertEnabled = app != null
                && Boolean.TRUE.equals(app.getProbeAlertEnabled())
                && eventTypeEnabled;
        if (!alertEnabled) {
            logger.debug("跳过探针告警事件, appId={}, eventType={}, reason={}",
                    event.getAppId(), event.getEventType(), buildSkipReason(app, eventTypeEnabled));
            return null;
        }
        ProbeAlertEvent saved = probeAlertEventRepository.save(event);
        probeAlertSseService.broadcast(saved);
        return saved;
    }

    private String buildSkipReason(AppVo app, boolean eventTypeEnabled) {
        if (app == null) {
            return "应用配置为空";
        }
        if (!Boolean.TRUE.equals(app.getProbeAlertEnabled())) {
            return "未启用探针上下线告警";
        }
        if (!eventTypeEnabled) {
            return "当前事件类型未启用告警";
        }
        return "未知原因";
    }

    private String describeProbe(ProbeInstanceStatus status) {
        String appName = org.springframework.util.StringUtils.hasText(status.getAppName()) ? status.getAppName() : status.getAppId();
        return String.format("%s / %s / pid=%s", appName, status.getAddressIp(), status.getPid());
    }
}
