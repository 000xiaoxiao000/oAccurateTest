package com.oAT.web.service;

import com.oAT.server.model.ClientSessionVo;
import com.oAT.web.service.entity.AppVo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
public class OnlineInstanceSseService implements DisposableBean {
    private static final Logger logger = LoggerFactory.getLogger(OnlineInstanceSseService.class);
    private static final long EMITTER_TIMEOUT_MILLIS = 0L;
    private final Map<String, CopyOnWriteArrayList<SseEmitter>> emittersByProject = new ConcurrentHashMap<>();
    private final Map<String, Map<String, Integer>> lastCountsByProject = new ConcurrentHashMap<>();

    @Autowired
    private AppService appService;

    @Autowired
    private ClientSessionService sessionService;

    public SseEmitter subscribe(String projectId) {
        SseEmitter emitter = new SseEmitter(EMITTER_TIMEOUT_MILLIS);
        AtomicBoolean completed = new AtomicBoolean(false);
        if (!StringUtils.hasText(projectId)) {
            emitter.complete();
            return emitter;
        }
        emittersByProject.computeIfAbsent(projectId, key -> new CopyOnWriteArrayList<>()).add(emitter);
        logger.debug("在线实例 SSE 已连接, projectId={}, activeConnections={}", projectId, countProjectEmitters(projectId));
        emitter.onCompletion(() -> removeEmitter(projectId, emitter));
        emitter.onTimeout(() -> completeEmitter(projectId, emitter, completed));
        emitter.onError(error -> completeEmitter(projectId, emitter, completed));
        try {
            emitter.send(SseEmitter.event().name("connected").data("ok", MediaType.TEXT_PLAIN));
            Map<String, Integer> counts = buildCounts(projectId);
            lastCountsByProject.put(projectId, counts);
            sendCounts(projectId, emitter, counts);
        } catch (Throwable e) {
            completeEmitter(projectId, emitter, completed);
        }
        return emitter;
    }

    @Scheduled(fixedDelayString = "${online.instances.sse.scanDelayMillis:5000}")
    public void publishChangedCounts() {
        for (String projectId : emittersByProject.keySet()) {
            List<SseEmitter> emitters = emittersByProject.get(projectId);
            if (emitters == null || emitters.isEmpty()) {
                continue;
            }
            Map<String, Integer> counts = buildCounts(projectId);
            Map<String, Integer> previous = lastCountsByProject.put(projectId, counts);
            if (!counts.equals(previous)) {
                broadcastCounts(projectId, counts);
            } else {
                sendHeartbeat(projectId, emitters);
            }
        }
    }

    public Map<String, Integer> buildCounts(String projectId) {
        List<AppVo> apps = appService.getAppList(projectId);
        Map<String, Integer> counts = new HashMap<>();
        Set<String> appIds = new HashSet<>();
        for (AppVo app : apps) {
            counts.put(app.getId(), 0);
            appIds.add(app.getId());
        }
        for (ClientSessionVo session : sessionService.getOnlineSessions()) {
            if (session.getClientInfo() == null || !StringUtils.hasText(session.getClientInfo().getAppKey())) {
                continue;
            }
            String appId = session.getClientInfo().getAppKey();
            if (appIds.contains(appId)) {
                counts.put(appId, counts.get(appId) + 1);
            }
        }
        return counts;
    }

    private void broadcastCounts(String projectId, Map<String, Integer> counts) {
        List<SseEmitter> emitters = emittersByProject.get(projectId);
        if (emitters == null || emitters.isEmpty()) {
            return;
        }
        logger.debug("在线实例 SSE 广播, projectId={}, connections={}", projectId, emitters.size());
        for (SseEmitter emitter : emitters) {
            sendCounts(projectId, emitter, counts);
        }
    }

    private void sendCounts(String projectId, SseEmitter emitter, Map<String, Integer> counts) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("success", true);
        payload.put("counts", counts);
        payload.put("serverTime", System.currentTimeMillis());
        try {
            emitter.send(SseEmitter.event().name("online-counts").data(payload, MediaType.APPLICATION_JSON));
        } catch (Throwable e) {
            completeEmitter(projectId, emitter, new AtomicBoolean(false));
        }
    }

    private void sendHeartbeat(String projectId, List<SseEmitter> emitters) {
        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event().name("heartbeat").data(String.valueOf(System.currentTimeMillis()), MediaType.TEXT_PLAIN));
            } catch (Throwable e) {
                completeEmitter(projectId, emitter, new AtomicBoolean(false));
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
            lastCountsByProject.remove(projectId);
        }
    }

    private void completeEmitter(String projectId, SseEmitter emitter, AtomicBoolean completed) {
        removeEmitter(projectId, emitter);
        if (completed.compareAndSet(false, true)) {
            try {
                emitter.complete();
            } catch (Exception e) {
                logger.debug("在线实例 SSE 连接结束时忽略异常, projectId={}, error={}", projectId, e.getMessage());
            }
        }
    }

    @Override
    public void destroy() {
        emittersByProject.clear();
        lastCountsByProject.clear();
    }
}
