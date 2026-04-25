package com.oAT.web.service;

import com.oAT.server.model.ClientInfoVo;
import com.oAT.server.model.ClientSessionVo;
import org.springframework.stereotype.Service;
import org.springframework.util.DigestUtils;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.util.StringJoiner;

@Service
public class ProbeIdentityService {

    public String buildProbeKey(ClientSessionVo session) {
        if (session == null) {
            return null;
        }
        return buildProbeKey(session.getClientInfo());
    }

    public String buildProbeKey(ClientInfoVo clientInfo) {
        if (clientInfo == null || !StringUtils.hasText(clientInfo.getAppKey())) {
            return null;
        }
        StringJoiner joiner = new StringJoiner("|");
        joiner.add(safe(clientInfo.getAppKey()));
        joiner.add(safe(clientInfo.getAddressIp()));
        joiner.add(safe(clientInfo.getPid()));
        joiner.add(safe(clientInfo.getSystemDir()));
        return DigestUtils.md5DigestAsHex(joiner.toString().getBytes(StandardCharsets.UTF_8));
    }

    private String safe(String value) {
        return StringUtils.hasText(value) ? value.trim() : "";
    }
}
