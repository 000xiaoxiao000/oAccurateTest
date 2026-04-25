package com.oAT.web.service.entity;

import java.util.ArrayList;
import java.util.List;

public class ProbeAlertDashboardVo {
    private long onlineCount;
    private long offlineCount;
    private long recentEventCount;
    private long failedNotifyCount;
    private String latestEventTimeText;
    private String latestEventMessage;
    private List<ProbeStatusItemVo> statuses = new ArrayList<>();
    private List<ProbeAlertEventItemVo> recentEvents = new ArrayList<>();

    public long getOnlineCount() {
        return onlineCount;
    }

    public void setOnlineCount(long onlineCount) {
        this.onlineCount = onlineCount;
    }

    public long getOfflineCount() {
        return offlineCount;
    }

    public void setOfflineCount(long offlineCount) {
        this.offlineCount = offlineCount;
    }

    public long getRecentEventCount() {
        return recentEventCount;
    }

    public void setRecentEventCount(long recentEventCount) {
        this.recentEventCount = recentEventCount;
    }

    public long getFailedNotifyCount() {
        return failedNotifyCount;
    }

    public void setFailedNotifyCount(long failedNotifyCount) {
        this.failedNotifyCount = failedNotifyCount;
    }

    public String getLatestEventTimeText() {
        return latestEventTimeText;
    }

    public void setLatestEventTimeText(String latestEventTimeText) {
        this.latestEventTimeText = latestEventTimeText;
    }

    public String getLatestEventMessage() {
        return latestEventMessage;
    }

    public void setLatestEventMessage(String latestEventMessage) {
        this.latestEventMessage = latestEventMessage;
    }

    public List<ProbeStatusItemVo> getStatuses() {
        return statuses;
    }

    public void setStatuses(List<ProbeStatusItemVo> statuses) {
        this.statuses = statuses;
    }

    public List<ProbeAlertEventItemVo> getRecentEvents() {
        return recentEvents;
    }

    public void setRecentEvents(List<ProbeAlertEventItemVo> recentEvents) {
        this.recentEvents = recentEvents;
    }

    public static class ProbeStatusItemVo {
        private String probeKey;
        private String status;
        private String statusLabel;
        private String statusColor;
        private String addressIp;
        private String pid;
        private String systemDir;
        private String agentVersion;
        private String sessionId;
        private String lastHeartbeatTimeText;
        private String onlineSinceText;
        private String offlineSinceText;
        private String lastAlertEventType;
        private String lastAlertTimeText;

        public String getProbeKey() {
            return probeKey;
        }

        public void setProbeKey(String probeKey) {
            this.probeKey = probeKey;
        }

        public String getStatus() {
            return status;
        }

        public void setStatus(String status) {
            this.status = status;
        }

        public String getStatusLabel() {
            return statusLabel;
        }

        public void setStatusLabel(String statusLabel) {
            this.statusLabel = statusLabel;
        }

        public String getStatusColor() {
            return statusColor;
        }

        public void setStatusColor(String statusColor) {
            this.statusColor = statusColor;
        }

        public String getAddressIp() {
            return addressIp;
        }

        public void setAddressIp(String addressIp) {
            this.addressIp = addressIp;
        }

        public String getPid() {
            return pid;
        }

        public void setPid(String pid) {
            this.pid = pid;
        }

        public String getSystemDir() {
            return systemDir;
        }

        public void setSystemDir(String systemDir) {
            this.systemDir = systemDir;
        }

        public String getAgentVersion() {
            return agentVersion;
        }

        public void setAgentVersion(String agentVersion) {
            this.agentVersion = agentVersion;
        }

        public String getSessionId() {
            return sessionId;
        }

        public void setSessionId(String sessionId) {
            this.sessionId = sessionId;
        }

        public String getLastHeartbeatTimeText() {
            return lastHeartbeatTimeText;
        }

        public void setLastHeartbeatTimeText(String lastHeartbeatTimeText) {
            this.lastHeartbeatTimeText = lastHeartbeatTimeText;
        }

        public String getOnlineSinceText() {
            return onlineSinceText;
        }

        public void setOnlineSinceText(String onlineSinceText) {
            this.onlineSinceText = onlineSinceText;
        }

        public String getOfflineSinceText() {
            return offlineSinceText;
        }

        public void setOfflineSinceText(String offlineSinceText) {
            this.offlineSinceText = offlineSinceText;
        }

        public String getLastAlertEventType() {
            return lastAlertEventType;
        }

        public void setLastAlertEventType(String lastAlertEventType) {
            this.lastAlertEventType = lastAlertEventType;
        }

        public String getLastAlertTimeText() {
            return lastAlertTimeText;
        }

        public void setLastAlertTimeText(String lastAlertTimeText) {
            this.lastAlertTimeText = lastAlertTimeText;
        }
    }

    public static class ProbeAlertEventItemVo {
        private String id;
        private String eventType;
        private String eventTypeLabel;
        private String eventTypeColor;
        private String appName;
        private String probeText;
        private String eventTimeText;
        private String message;
        private String notifyStatus;
        private String notifyStatusLabel;
        private String notifyStatusColor;
        private String notifyResponse;
        private String notifyError;

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public String getEventType() {
            return eventType;
        }

        public void setEventType(String eventType) {
            this.eventType = eventType;
        }

        public String getEventTypeLabel() {
            return eventTypeLabel;
        }

        public void setEventTypeLabel(String eventTypeLabel) {
            this.eventTypeLabel = eventTypeLabel;
        }

        public String getEventTypeColor() {
            return eventTypeColor;
        }

        public void setEventTypeColor(String eventTypeColor) {
            this.eventTypeColor = eventTypeColor;
        }

        public String getAppName() {
            return appName;
        }

        public void setAppName(String appName) {
            this.appName = appName;
        }

        public String getProbeText() {
            return probeText;
        }

        public void setProbeText(String probeText) {
            this.probeText = probeText;
        }

        public String getEventTimeText() {
            return eventTimeText;
        }

        public void setEventTimeText(String eventTimeText) {
            this.eventTimeText = eventTimeText;
        }

        public String getMessage() {
            return message;
        }

        public void setMessage(String message) {
            this.message = message;
        }

        public String getNotifyStatus() {
            return notifyStatus;
        }

        public void setNotifyStatus(String notifyStatus) {
            this.notifyStatus = notifyStatus;
        }

        public String getNotifyStatusLabel() {
            return notifyStatusLabel;
        }

        public void setNotifyStatusLabel(String notifyStatusLabel) {
            this.notifyStatusLabel = notifyStatusLabel;
        }

        public String getNotifyStatusColor() {
            return notifyStatusColor;
        }

        public void setNotifyStatusColor(String notifyStatusColor) {
            this.notifyStatusColor = notifyStatusColor;
        }

        public String getNotifyResponse() {
            return notifyResponse;
        }

        public void setNotifyResponse(String notifyResponse) {
            this.notifyResponse = notifyResponse;
        }

        public String getNotifyError() {
            return notifyError;
        }

        public void setNotifyError(String notifyError) {
            this.notifyError = notifyError;
        }
    }
}
