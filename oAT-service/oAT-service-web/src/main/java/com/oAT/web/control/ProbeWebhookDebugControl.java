package com.oAT.web.control;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/webhook")
public class ProbeWebhookDebugControl {
    private static final Logger logger = LoggerFactory.getLogger(ProbeWebhookDebugControl.class);

    @PostMapping("/oat/probe-alert")
    public Map<String, Object> receiveProbeAlert(@RequestBody Map<String, Object> payload) {
        logger.info("收到探针上下线测试 Webhook: {}", payload);
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("message", "probe alert received");
        return result;
    }
}
