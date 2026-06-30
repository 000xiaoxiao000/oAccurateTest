package com.oAT.web.collector;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.oAT.agent.model.Application;
import com.oAT.server.model.ClientInfoVo;
import com.oAT.server.model.ClientSessionVo;
import com.oAT.web.esDao.ClientRepository;
import com.oAT.web.esDao.SystemRepository;
import com.oAT.web.esDao.entity.App;
import com.oAT.web.esDao.entity.ClientIndex;
import com.oAT.web.esDao.entity.ClientInfo;
import com.oAT.web.esDao.entity.ClientSession;
import com.oAT.web.esDao.entity.SystemIndex;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeanUtils;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;

import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
public class ClientSessionStateService {

    private static final Logger logger = LoggerFactory.getLogger(ClientSessionStateService.class);
    private static final String SESSIONS_KEY_PREFIX = "oAT:sessions:";

    private final RedisTemplate<String, Object> redisTemplate;
    private final ClientRepository clientRepository;
    private final SystemRepository systemRepository;
    private final ObjectMapper objectMapper;

    public ClientSessionStateService(RedisTemplate<String, Object> redisTemplate,
                                     ClientRepository clientRepository,
                                     SystemRepository systemRepository,
                                     ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.clientRepository = clientRepository;
        this.systemRepository = systemRepository;
        this.objectMapper = objectMapper;
    }

    public ClientSessionVo getClientSession(String sessionId, int sessionClearValidity) {
        ClientSessionVo vo = (ClientSessionVo) redisTemplate.opsForValue().get(SESSIONS_KEY_PREFIX + sessionId);
        if (vo != null) {
            return vo;
        }
        Optional<ClientIndex> clientIndex = clientRepository.findById(sessionId);
        if (clientIndex.isPresent()) {
            vo = convert(clientIndex.get());
            redisTemplate.opsForValue().set(SESSIONS_KEY_PREFIX + sessionId, vo, sessionClearValidity, TimeUnit.MILLISECONDS);
        }
        return vo;
    }

    public ClientIndex createActiveClientIndex(ClientInfoVo clientInfoVo) {
        Assert.hasText(clientInfoVo.getPid(), "Client pid must be not null");
        Assert.hasText(clientInfoVo.getSystemDir(), "Client dir must be not null");
        Assert.hasText(clientInfoVo.getAgentVersion(), "Agent version must be not null");
        ClientSession session = new ClientSession();
        ClientInfo clientInfo = new ClientInfo();
        BeanUtils.copyProperties(clientInfoVo, clientInfo);
        session.setClientInfo(clientInfo);
        session.setStatus(ClientSession.Status.active.toString());
        session.setLoginTime(new SimpleDateFormat(com.oAT.web.esDao.entity.StandardDate.dateFormat).format(new Date()));
        return clientRepository.save(new ClientIndex(session));
    }

    public void cacheSession(ClientSessionVo session, int sessionClearValidity) {
        redisTemplate.opsForValue().set(SESSIONS_KEY_PREFIX + session.getSessionId(), session, sessionClearValidity, TimeUnit.MILLISECONDS);
    }

    public List<ClientSessionVo> getOnlineSessions(int sessionOnlineValidity) {
        Set<String> keys = redisTemplate.keys(SESSIONS_KEY_PREFIX + "*");
        if (keys == null) {
            return Collections.emptyList();
        }
        return keys.stream()
                .map(key -> (ClientSessionVo) redisTemplate.opsForValue().get(key))
                .filter(vo -> vo != null && (System.currentTimeMillis() - vo.getLastHeartbeatTime() < sessionOnlineValidity))
                .collect(Collectors.toList());
    }

    public void disableDuplicateOnlineSessions(ClientInfoVo clientInfoVo, List<ClientSessionVo> onlineSessions) {
        if (clientInfoVo == null) {
            return;
        }
        for (ClientSessionVo session : onlineSessions) {
            if (session == null || session.getClientInfo() == null || session.getSessionId() == null) {
                continue;
            }
            ClientInfoVo existing = session.getClientInfo();
            boolean sameRuntime = Objects.equals(existing.getAppKey(), clientInfoVo.getAppKey())
                    && Objects.equals(existing.getPid(), clientInfoVo.getPid())
                    && Objects.equals(existing.getSystemDir(), clientInfoVo.getSystemDir());
            if (!sameRuntime) {
                continue;
            }
            redisTemplate.delete(SESSIONS_KEY_PREFIX + session.getSessionId());
            try {
                clientRepository.findById(session.getSessionId()).ifPresent(clientIndex -> {
                    ClientSession stored = clientIndex.getSession();
                    if (stored != null) {
                        stored.setStatus(ClientSession.Status.disable.toString());
                        clientIndex.setUpdateTime(new Date());
                        clientRepository.save(clientIndex);
                    }
                });
            } catch (Exception e) {
                logger.warn("[doLogin]禁用重复在线会话失败, sessionId={}", session.getSessionId(), e);
            }
        }
    }

    public void heartbeat(String sessionId, ClientSessionVo vo, long heartbeatTime, int sessionClearValidity) {
        vo.setLastHeartbeatTime(heartbeatTime);
        cacheSession(vo, sessionClearValidity);
        try {
            clientRepository.findById(sessionId).ifPresent(clientIndex -> {
                ClientSession session = clientIndex.getSession();
                if (session == null) {
                    return;
                }
                session.setLastHeartbeatTime(heartbeatTime);
                session.setStatus(ClientSession.Status.active.toString());
                clientIndex.setUpdateTime(new Date(heartbeatTime));
                clientRepository.save(clientIndex);
            });
        } catch (Exception e) {
            logger.warn("[heartbeat]同步 client 心跳到 MySQL 失败, sessionId={}", sessionId, e);
        }
    }

    private ClientSessionVo convert(ClientIndex index) {
        ClientSessionVo vo = new ClientSessionVo();
        vo.setSessionId(index.getSessionId());
        if (index.getSession() != null) {
            vo.setStatus(index.getSession().getStatus());
            vo.setDisable(ClientSession.Status.disable.toString().equals(index.getSession().getStatus()));
            if (index.getSession().getClientInfo() != null) {
                ClientInfoVo clientInfo = new ClientInfoVo();
                BeanUtils.copyProperties(index.getSession().getClientInfo(), clientInfo);
                vo.setClientInfo(clientInfo);
            }
            BeanUtils.copyProperties(index.getSession(), vo);
        }
        try {
            if (vo.getLoginTime() == null && index.getSession() != null && index.getSession().getLoginTime() != null) {
                vo.setLoginTime(new SimpleDateFormat(ClientIndex.getDateFormat()).parse(index.getSession().getLoginTime()));
            }
        } catch (Exception ignore) {
        }
        if (vo.getClientInfo() != null && vo.getClientInfo().getAppKey() != null) {
            Optional<SystemIndex> byId = systemRepository.findById(vo.getClientInfo().getAppKey());
            if (byId.isPresent()) {
                App app = byId.get().getApp();
                vo.setApplication(new Application(vo.getClientInfo().getAppKey(), app.getName(), app.getSrcName()));
            }
        }
        String configs = index.getSession() != null ? index.getSession().getConfigs() : null;
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        try {
            if (org.apache.commons.lang3.StringUtils.isNotBlank(configs)) {
                vo.setConfigs(objectMapper.readValue(configs, Properties.class));
            } else {
                vo.setConfigs(new Properties());
            }
        } catch (JsonProcessingException e) {
            logger.error("[convert]反序列化ClientSession的configs失败: {}", e.getMessage(), e);
            vo.setConfigs(new Properties());
        }
        if (vo.getLastHeartbeatTime() == null) {
            vo.setLastHeartbeatTime(System.currentTimeMillis());
        }
        if (vo.getOnlineTime() == null && vo.getLoginTime() != null) {
            long onlineMillis = vo.getLastHeartbeatTime() - vo.getLoginTime().getTime();
            vo.setOnlineTime(String.valueOf(onlineMillis));
        }
        return vo;
    }
}
