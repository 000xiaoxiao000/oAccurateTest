package com.oAT.web.service;

import com.oAT.web.esDao.entity.ProbeAlertEvent;
import com.oAT.web.service.entity.ProbeAlertDashboardVo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
public class ProbeAlertSseService implements DisposableBean {
    private static final Logger logger = LoggerFactory.getLogger(ProbeAlertSseService.class);
    private static final long EMITTER_TIMEOUT_MILLIS = 0L;
    private final Map<String, CopyOnWriteArrayList<SseEmitter>> emittersByProject = new ConcurrentHashMap<>();

    @Autowired
    private ProbeAlertDashboardService probeAlertDashboardService;

    public SseEmitter subscribe(String projectId) {
        SseEmitter emitter = new SseEmitter(EMITTER_TIMEOUT_MILLIS);
        AtomicBoolean completed = new AtomicBoolean(false);
        if (!StringUtils.hasText(projectId)) {
            emitter.complete();
            return emitter;
        }
        emittersByProject.computeIfAbsent(projectId, key -> new CopyOnWriteArrayList<>()).add(emitter);
        logger.debug("探针告警 SSE 已连接, projectId={}, activeConnections={}", projectId, countProjectEmitters(projectId));
        emitter.onCompletion(() -> removeEmitter(projectId, emitter));
        emitter.onTimeout(() -> completeEmitter(projectId, emitter, completed));
        emitter.onError(error -> completeEmitter(projectId, emitter, completed));
        try {
            emitter.send(SseEmitter.event().name("connected").data("ok", MediaType.TEXT_PLAIN));
        } catch (Throwable e) {
            completeEmitter(projectId, emitter, completed);
        }
        return emitter;
    }

    public void broadcast(ProbeAlertEvent event) {
        if (event == null || !StringUtils.hasText(event.getProjectId())) {
            return;
        }
        List<SseEmitter> emitters = emittersByProject.get(event.getProjectId());
        if (emitters == null || emitters.isEmpty()) {
            logger.debug("探针告警 SSE 无在线连接, projectId={}, eventId={}, eventType={}", event.getProjectId(), event.getId(), event.getEventType());
            return;
        }
        ProbeAlertDashboardVo.ProbeAlertEventItemVo item = probeAlertDashboardService.toEventItem(event);
        logger.debug("探针告警 SSE 开始广播, projectId={}, eventId={}, eventType={}, connections={}",
                event.getProjectId(), event.getId(), event.getEventType(), emitters.size());
        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event()
                        .id(item.getId())
                        .name("probe-alert")
                        .data(item, MediaType.APPLICATION_JSON));
            } catch (Throwable e) {
                completeEmitter(event.getProjectId(), emitter, new AtomicBoolean(false));
                logger.debug("探针告警 SSE 连接已移除, projectId={}, eventId={}, error={}", event.getProjectId(), event.getId(), e.getMessage());
            }
        }
    }

    @Scheduled(fixedDelay = 25000)
    public void sendHeartbeat() {
        for (Map.Entry<String, CopyOnWriteArrayList<SseEmitter>> entry : emittersByProject.entrySet()) {
            String projectId = entry.getKey();
            for (SseEmitter emitter : entry.getValue()) {
                try {
                    emitter.send(SseEmitter.event().name("heartbeat").data(String.valueOf(System.currentTimeMillis()), MediaType.TEXT_PLAIN));
                } catch (Throwable e) {
                    completeEmitter(projectId, emitter, new AtomicBoolean(false));
                }
            }
        }
    }

    private int countProjectEmitters(String projectId) {
        List<SseEmitter> emitters = emittersByProject.get(projectId);
        return emitters == null ? 0 : emitters.size();
    }

    private void removeEmitter(String projectId, SseEmitter emitter) {
        List<SseEmitter> emitters = emittersByProject.get(projectId);
        if (emitters == null) {
            return;
        }
        emitters.remove(emitter);
        if (emitters.isEmpty()) {
            emittersByProject.remove(projectId);
        }
    }

    private void completeEmitter(String projectId, SseEmitter emitter, AtomicBoolean completed) {
        removeEmitter(projectId, emitter);
        if (completed.compareAndSet(false, true)) {
            try {
                emitter.complete();
            } catch (Exception e) {
                logger.debug("探针告警 SSE 连接结束时忽略异常, projectId={}, error={}", projectId, e.getMessage());
            }
        }
    }

    @Override
    public void destroy() {
        emittersByProject.clear();
    }
}
