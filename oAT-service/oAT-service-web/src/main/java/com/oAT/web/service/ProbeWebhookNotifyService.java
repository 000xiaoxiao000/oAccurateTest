package com.oAT.web.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.oAT.web.esDao.ProbeAlertEventRepository;
import com.oAT.web.esDao.entity.ProbeAlertEvent;
import com.oAT.web.service.entity.AppVo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.OutputStream;
import java.net.ConnectException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class ProbeWebhookNotifyService {
    private static final Logger logger = LoggerFactory.getLogger(ProbeWebhookNotifyService.class);

    @Autowired
    private ProbeAlertEventRepository probeAlertEventRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @Async
    public void sendAsync(ProbeAlertEvent event, AppVo app) {
        if (event == null || app == null || !org.springframework.util.StringUtils.hasText(app.getProbeWebhookUrl())) {
            return;
        }
        try {
            String body = objectMapper.writeValueAsString(buildPayload(event, app));
            HttpURLConnection connection = (HttpURLConnection) new URL(app.getProbeWebhookUrl()).openConnection();
            connection.setRequestMethod("POST");
            connection.setConnectTimeout(3000);
            connection.setReadTimeout(5000);
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "application/json;charset=UTF-8");
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            connection.setRequestProperty("Content-Length", String.valueOf(bytes.length));
            try (OutputStream outputStream = connection.getOutputStream()) {
                outputStream.write(bytes);
            }
            int responseCode = connection.getResponseCode();
            event.setNotifyStatus(responseCode >= 200 && responseCode < 300
                    ? ProbeAlertEvent.NotifyStatus.SUCCESS.toString()
                    : ProbeAlertEvent.NotifyStatus.FAILED.toString());
            event.setNotifyResponse("HTTP " + responseCode);
            event.setNotifyError(null);
            if (responseCode >= 200 && responseCode < 300) {
                logger.info("探针上下线 Webhook 通知成功, eventId={}, appId={}, eventType={}, webhookUrl={}, responseCode={}",
                        event.getId(), event.getAppId(), event.getEventType(), app.getProbeWebhookUrl(), responseCode);
            } else {
                logger.warn("探针上下线 Webhook 返回非成功状态, eventId={}, appId={}, eventType={}, webhookUrl={}, responseCode={}",
                        event.getId(), event.getAppId(), event.getEventType(), app.getProbeWebhookUrl(), responseCode);
            }
        } catch (ConnectException e) {
            event.setNotifyStatus(ProbeAlertEvent.NotifyStatus.FAILED.toString());
            event.setNotifyError(e.getMessage());
            logger.warn("探针上下线 Webhook 连接失败，目标服务不可达或端口未监听, eventId={}, appId={}, eventType={}, webhookUrl={}, error={}",
                    event.getId(), event.getAppId(), event.getEventType(), app.getProbeWebhookUrl(), e.getMessage());
        } catch (Exception e) {
            event.setNotifyStatus(ProbeAlertEvent.NotifyStatus.FAILED.toString());
            event.setNotifyError(e.getMessage());
            logger.error("探针上下线 Webhook 通知失败, eventId={}, appId={}, eventType={}, webhookUrl={}",
                    event.getId(), event.getAppId(), event.getEventType(), app.getProbeWebhookUrl(), e);
        } finally {
            event.setUpdateTime(new Date());
            probeAlertEventRepository.save(event);
        }
    }

    private Map<String, Object> buildPayload(ProbeAlertEvent event, AppVo app) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("eventType", event.getEventType());
        payload.put("eventTime", event.getEventTime());
        payload.put("projectId", event.getProjectId());
        payload.put("appId", event.getAppId());
        payload.put("appName", event.getAppName());
        payload.put("probeKey", event.getProbeKey());
        payload.put("sessionId", event.getSessionId());
        payload.put("addressIp", event.getAddressIp());
        payload.put("pid", event.getPid());
        payload.put("systemDir", event.getSystemDir());
        payload.put("agentVersion", event.getAgentVersion());
        payload.put("lastHeartbeatTime", event.getLastHeartbeatTime());
        payload.put("offlineThresholdSeconds", app.getProbeOfflineThresholdSeconds());
        payload.put("offlineDurationMillis", event.getOfflineDurationMillis());
        payload.put("message", event.getMessage());
        return payload;
    }
}
