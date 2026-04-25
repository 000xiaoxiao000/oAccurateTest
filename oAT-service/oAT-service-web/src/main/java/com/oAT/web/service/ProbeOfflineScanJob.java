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
            logger.error("探针离线扫描任务执行失败", e);
        }
    }
}
