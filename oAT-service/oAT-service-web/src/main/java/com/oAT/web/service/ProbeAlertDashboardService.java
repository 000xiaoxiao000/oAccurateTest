package com.oAT.web.service;

import com.oAT.web.esDao.ProbeAlertEventRepository;
import com.oAT.web.esDao.ProbeInstanceStatusRepository;
import com.oAT.web.esDao.entity.ProbeAlertEvent;
import com.oAT.web.esDao.entity.ProbeInstanceStatus;
import com.oAT.web.service.entity.ProbeAlertDashboardVo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.text.SimpleDateFormat;
import java.util.Comparator;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class ProbeAlertDashboardService {
    private static final int DEFAULT_EVENT_LIMIT = 50;
    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    @Autowired
    private ProbeInstanceStatusRepository probeInstanceStatusRepository;

    @Autowired
    private ProbeAlertEventRepository probeAlertEventRepository;

    public ProbeAlertDashboardVo getDashboard(String appId) {
        return getDashboard(appId, DEFAULT_EVENT_LIMIT);
    }

    public ProbeAlertDashboardVo getDashboard(String appId, int eventLimit) {
        ProbeAlertDashboardVo dashboard = new ProbeAlertDashboardVo();
        List<ProbeInstanceStatus> statuses = probeInstanceStatusRepository.findByAppId(appId).stream()
                .sorted(Comparator.comparing(ProbeInstanceStatus::getUpdateTime, Comparator.nullsLast(Comparator.reverseOrder())))
                .collect(Collectors.toList());
        List<ProbeAlertEvent> events = sortedEvents(probeAlertEventRepository.findByAppId(appId), eventLimit);

        dashboard.setOnlineCount(statuses.stream().filter(this::isOnline).count());
        dashboard.setOfflineCount(statuses.stream().filter(this::isOffline).count());
        dashboard.setRecentEventCount(events.size());
        dashboard.setFailedNotifyCount(events.stream().filter(this::isNotifyFailed).count());
        dashboard.setStatuses(statuses.stream().map(this::toStatusItem).collect(Collectors.toList()));
        dashboard.setRecentEvents(events.stream().map(this::toEventItem).collect(Collectors.toList()));
        if (!events.isEmpty()) {
            ProbeAlertEvent latest = events.get(0);
            dashboard.setLatestEventTimeText(formatDate(latest.getEventTime()));
            dashboard.setLatestEventMessage(latest.getMessage());
        }
        return dashboard;
    }

    public List<ProbeAlertDashboardVo.ProbeAlertEventItemVo> getRecentProjectEvents(String projectId, int eventLimit) {
        if (!StringUtils.hasText(projectId)) {
            return Collections.emptyList();
        }
        return sortedEvents(probeAlertEventRepository.findByProjectId(projectId), eventLimit).stream()
                .map(this::toEventItem)
                .collect(Collectors.toList());
    }

    private List<ProbeAlertEvent> sortedEvents(List<ProbeAlertEvent> events, int eventLimit) {
        return events.stream()
                .sorted(Comparator.comparing(ProbeAlertEvent::getEventTime, Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(Math.max(1, eventLimit))
                .collect(Collectors.toList());
    }

    private ProbeAlertDashboardVo.ProbeStatusItemVo toStatusItem(ProbeInstanceStatus status) {
        ProbeAlertDashboardVo.ProbeStatusItemVo item = new ProbeAlertDashboardVo.ProbeStatusItemVo();
        item.setProbeKey(status.getProbeKey());
        item.setStatus(status.getStatus());
        item.setStatusLabel(isOffline(status) ? "离线" : "在线");
        item.setStatusColor(isOffline(status) ? "red" : "green");
        item.setAddressIp(status.getAddressIp());
        item.setPid(status.getPid());
        item.setSystemDir(status.getSystemDir());
        item.setAgentVersion(status.getAgentVersion());
        item.setSessionId(status.getSessionId());
        item.setLastHeartbeatTimeText(formatMillis(status.getLastHeartbeatTime()));
        item.setOnlineSinceText(formatDate(status.getOnlineSince()));
        item.setOfflineSinceText(formatDate(status.getOfflineSince()));
        item.setLastAlertEventType(status.getLastAlertEventType());
        item.setLastAlertTimeText(formatDate(status.getLastAlertTime()));
        return item;
    }

    private ProbeAlertDashboardVo.ProbeAlertEventItemVo toEventItem(ProbeAlertEvent event) {
        ProbeAlertDashboardVo.ProbeAlertEventItemVo item = new ProbeAlertDashboardVo.ProbeAlertEventItemVo();
        item.setId(event.getId());
        item.setEventType(event.getEventType());
        item.setEventTypeLabel(eventTypeLabel(event.getEventType()));
        item.setEventTypeColor(eventTypeColor(event.getEventType()));
        item.setAppName(event.getAppName());
        item.setProbeText(buildProbeText(event));
        item.setEventTimeText(formatDate(event.getEventTime()));
        item.setMessage(event.getMessage());
        item.setNotifyStatus(event.getNotifyStatus());
        item.setNotifyStatusLabel(notifyStatusLabel(event.getNotifyStatus()));
        item.setNotifyStatusColor(notifyStatusColor(event.getNotifyStatus()));
        item.setNotifyResponse(event.getNotifyResponse());
        item.setNotifyError(event.getNotifyError());
        return item;
    }

    private boolean isOnline(ProbeInstanceStatus status) {
        return ProbeInstanceStatus.Status.ONLINE.toString().equals(status.getStatus());
    }

    private boolean isOffline(ProbeInstanceStatus status) {
        return ProbeInstanceStatus.Status.OFFLINE.toString().equals(status.getStatus());
    }

    private boolean isNotifyFailed(ProbeAlertEvent event) {
        return ProbeAlertEvent.NotifyStatus.FAILED.toString().equals(event.getNotifyStatus());
    }

    private String eventTypeLabel(String eventType) {
        if (ProbeAlertEvent.EventType.OFFLINE.toString().equals(eventType)) {
            return "下线";
        }
        if (ProbeAlertEvent.EventType.RECOVERED.toString().equals(eventType)) {
            return "恢复上线";
        }
        if (ProbeAlertEvent.EventType.ONLINE.toString().equals(eventType)) {
            return "上线";
        }
        return emptyFallback(eventType);
    }

    private String eventTypeColor(String eventType) {
        if (ProbeAlertEvent.EventType.OFFLINE.toString().equals(eventType)) {
            return "red";
        }
        if (ProbeAlertEvent.EventType.RECOVERED.toString().equals(eventType)) {
            return "green";
        }
        return "blue";
    }

    private String notifyStatusLabel(String notifyStatus) {
        if (ProbeAlertEvent.NotifyStatus.SUCCESS.toString().equals(notifyStatus)) {
            return "已通知";
        }
        if (ProbeAlertEvent.NotifyStatus.FAILED.toString().equals(notifyStatus)) {
            return "通知失败";
        }
        if (ProbeAlertEvent.NotifyStatus.SKIPPED.toString().equals(notifyStatus)) {
            return "未通知";
        }
        if (ProbeAlertEvent.NotifyStatus.PENDING.toString().equals(notifyStatus)) {
            return "待通知";
        }
        return emptyFallback(notifyStatus);
    }

    private String notifyStatusColor(String notifyStatus) {
        if (ProbeAlertEvent.NotifyStatus.SUCCESS.toString().equals(notifyStatus)) {
            return "green";
        }
        if (ProbeAlertEvent.NotifyStatus.FAILED.toString().equals(notifyStatus)) {
            return "red";
        }
        if (ProbeAlertEvent.NotifyStatus.PENDING.toString().equals(notifyStatus)) {
            return "yellow";
        }
        return "grey";
    }

    private String buildProbeText(ProbeAlertEvent event) {
        StringBuilder builder = new StringBuilder();
        if (StringUtils.hasText(event.getAddressIp())) {
            builder.append(event.getAddressIp());
        }
        if (StringUtils.hasText(event.getPid())) {
            if (builder.length() > 0) {
                builder.append(" / ");
            }
            builder.append("pid=").append(event.getPid());
        }
        if (StringUtils.hasText(event.getSystemDir())) {
            if (builder.length() > 0) {
                builder.append(" / ");
            }
            builder.append(event.getSystemDir());
        }
        return builder.length() == 0 ? "-" : builder.toString();
    }

    private String formatMillis(Long millis) {
        if (millis == null || millis <= 0) {
            return "-";
        }
        return formatDate(new Date(millis));
    }

    private String formatDate(Date date) {
        if (date == null) {
            return "-";
        }
        synchronized (DATE_FORMAT) {
            return DATE_FORMAT.format(date);
        }
    }

    private String emptyFallback(String value) {
        return StringUtils.hasText(value) ? value : "-";
    }
}
