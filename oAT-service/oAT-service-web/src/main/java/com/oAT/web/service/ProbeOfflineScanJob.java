package com.oAT.web.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class ProbeOfflineScanJob {
    private static final Logger logger = LoggerFactory.getLogger(ProbeOfflineScanJob.class);

    @Autowired
    private ProbeStatusService probeStatusService;

    @Scheduled(fixedDelayString = "${probe.alert.scan-interval-millis:10000}", initialDelayString = "${probe.alert.scan-initial-delay-millis:10000}")
    public void scanOffline() {
        try {
            probeStatusService.scanOfflineProbes();
        } catch (Exception e) {
            if (isInterrupted(e)) {
                Thread.currentThread().interrupt();
                logger.warn("探针离线扫描任务被中断，本次扫描已停止");
                return;
            }
            logger.error("探针离线扫描任务执行失败", e);
        }
    }

    private boolean isInterrupted(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof InterruptedException) {
                return true;
            }
            String message = current.getMessage();
            if (message != null && message.toLowerCase().contains("interrupted")) {
                return true;
            }
            current = current.getCause();
        }
        return Thread.currentThread().isInterrupted();
    }
}
