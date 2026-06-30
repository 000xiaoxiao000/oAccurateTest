package com.oAT.web.collector;

import com.oAT.agent.model.Application;
import com.oAT.server.model.ClientInfoVo;
import com.oAT.server.model.ClientSessionVo;
import com.oAT.web.service.AppService;
import com.oAT.web.service.entity.AppVo;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.io.StringReader;
import java.util.Properties;

@Service
public class ClientSessionApplicationBinder {

    private final AppService appService;

    public ClientSessionApplicationBinder(AppService appService) {
        this.appService = appService;
    }

    public void bindApplication(ClientSessionVo session, ClientInfoVo clientInfoVo) {
        if (session == null || clientInfoVo == null || !StringUtils.hasText(clientInfoVo.getAppKey())) {
            return;
        }
        AppVo app = appService.getApp(clientInfoVo.getAppKey());
        if (app == null) {
            session.setConfigs(new Properties());
            return;
        }
        session.setApplication(new Application(app.getId(), app.getName(), app.getSrcName()));
        session.setConfigs(loadAgentProperties(app));
    }

    private Properties loadAgentProperties(AppVo app) {
        Properties properties = new Properties();
        if (app == null || !StringUtils.hasText(app.getProperties())) {
            return properties;
        }
        try {
            properties.load(new StringReader(app.getProperties()));
            return properties;
        } catch (IOException e) {
            throw new RuntimeException(String.format("名称:%s id:%s 属性配置装载失败", app.getName(), app.getId()), e);
        }
    }
}
