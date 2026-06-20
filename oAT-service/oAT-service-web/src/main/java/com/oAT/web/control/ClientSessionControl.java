package com.oAT.web.control;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.oAT.agent.model.*;
import com.oAT.server.model.ClientInfoVo;
import com.oAT.server.model.ClientSessionVo;
import com.oAT.web.service.ClientSessionService;
import com.cedarsoftware.util.io.JsonWriter;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.util.Assert;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

@Controller
@RequestMapping("/client")
public class ClientSessionControl {
    Logger logger = LoggerFactory.getLogger(ClientSessionControl.class);
    @Autowired
    ClientSessionService sessionService;
    @Autowired
    private ObjectMapper objectMapper;

    @PostMapping("/upload")
    @ResponseBody
    public String upload(String sessionId, String type, String data) {
        Assert.notNull(sessionId, "param 'sessionId' must be not null");
        Assert.notNull(type, "param 'type' must be not null");
        Assert.notNull(data, "param 'data' must be not null");
        ClientSessionVo session = sessionService.getClientSession(sessionId);
        Assert.notNull(session, "非法的请求，找不到会话id=" + sessionId);
        if (session.getApplication() == null) {
            logger.warn("采集器未绑定应用，sessionId={}", sessionId);
            return "fail";
        }
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        try {
            TraceNode node = (TraceNode) objectMapper.readValue(data, Class.forName(type));
            /*if (type.equals(HttpTraceNode.class.getName())) {
                node = mapper.readValue(data, HttpTraceNode.class);
            } else if (type.equals(DubboTraceNode.class.getName())) {
                node = mapper.readValue(data, DubboTraceNode.class);
            }else if(type.equals(DubboRemoteTraceNode.class.getName())){
                node = mapper.readValue(data, DubboRemoteTraceNode.class);
            }else if (type.equals(SqlTraceNode.class.getName())) {
                node = mapper.readValue(data, SqlTraceNode.class);
            } else {
                throw new IllegalArgumentException("param type value is Invalid");
            }*/

            sessionService.putTraceNode(node);
        } catch (IOException | ClassNotFoundException e) {
            throw new IllegalArgumentException(e.getMessage());
        }
        return "succeed";
    }

    @PostMapping("/login")
    @ResponseBody
    public String doLogin(String clientInfo) {
        Assert.hasText(clientInfo, "参数'clientInfo' 不能为空");
        ClientSessionVo result;
        ObjectMapper mapper = new ObjectMapper();
        ClientInfoVo info;
        try {
            info = mapper.readValue(clientInfo, ClientInfoVo.class);
        } catch (IOException e) {
            throw new IllegalArgumentException("参数'clientInfo' json格式错误", e);
        }
        result = sessionService.doLogin(info);
        return JsonWriter.objectToJson(result);
    }

    @PostMapping("/heartbeat/{sessionId}/{appId}/{timesTamp}")
    @ResponseBody
    public String heartbeat(@PathVariable String sessionId,
                            @PathVariable String appId,
                            @PathVariable Long timesTamp) {
        if (StringUtils.isBlank(sessionId)) {
            logger.error("[heartbeat]sessionId 为空");
            return "fail";
        }
        if (StringUtils.isBlank(appId)) {
            logger.error("[heartbeat]appId 为空");
            return "fail";
        }
        sessionService.heartbeat(sessionId, appId, timesTamp);
        String command = sessionService.pollSandboxCommand(sessionId);
        return StringUtils.isBlank(command) ? "heartbeatOK" : "heartbeatOK:" + command;
    }

    @PostMapping("/uploadStaticData")
    @ResponseBody
    public String uploadStaticData(@RequestParam("appId") String appId,
                                   @RequestBody String data) {
        Assert.notNull(appId, "param 'appId' must be not null");
        Assert.notNull(data, "param 'data' must be not null");
        sessionService.saveStaticData(appId, data);
        return "succeed";
    }

    @PostMapping("/agentLogs")
    @ResponseBody
    public void agentLogs(String sessionId, String logs) {
        if (StringUtils.isBlank(sessionId)) {
            logger.error("[agentLogs]sessionId为空");
            return;
        }
        sessionService.putAgentLogs(sessionId, logs);
    }

    @PostMapping("/packageVerify")
    @ResponseBody
    public void packageVerify(@RequestParam Map<String, String> params) {
        String sessionId = params.get("sessionId");
        String packagePath = params.get("packagePath");
        String gitCommitIdFromPackage = params.get("gitCommitIdFromPackage");

        if (StringUtils.isBlank(sessionId)) {
            logger.warn("[packageVerify]sessionId 为空，忽略包验证信息上送，packagePath={}, gitCommitIdFromPackage={}", packagePath, gitCommitIdFromPackage);
            return;
        }

        // 存储验证结果到ES
        sessionService.putPackageVerify(sessionId, packagePath, gitCommitIdFromPackage);

    }

    @PostMapping("/sandbox/status")
    @ResponseBody
    public void sandboxStatus(String sessionId, String status) {
        if (StringUtils.isBlank(sessionId)) {
            logger.warn("[sandboxStatus]sessionId 为空，忽略 sandbox 状态");
            return;
        }
        sessionService.putSandboxStatus(sessionId, status);
    }
}
