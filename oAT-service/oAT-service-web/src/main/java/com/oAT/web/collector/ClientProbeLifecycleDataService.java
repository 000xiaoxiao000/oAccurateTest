package com.oAT.web.collector;

import com.oAT.server.model.ClientSessionVo;
import com.oAT.web.esDao.ClientRepository;
import com.oAT.web.esDao.entity.ClientIndex;
import com.oAT.web.esDao.entity.ClientSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Date;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

@Service
public class ClientProbeLifecycleDataService {

    private static final Logger logger = LoggerFactory.getLogger(ClientProbeLifecycleDataService.class);
    private static final String SANDBOX_COMMAND_KEY_PREFIX = "oAT:sandbox-command:";

    private final ClientRepository clientRepository;
    private final RedisTemplate<String, Object> redisTemplate;
    private final ClientSessionStateService clientSessionStateService;

    public ClientProbeLifecycleDataService(ClientRepository clientRepository,
                                           RedisTemplate<String, Object> redisTemplate,
                                           ClientSessionStateService clientSessionStateService) {
        this.clientRepository = clientRepository;
        this.redisTemplate = redisTemplate;
        this.clientSessionStateService = clientSessionStateService;
    }

    public void putPackageVerify(String sessionId, String packagePath, String gitCommitIdFromPackage) {
        if (!StringUtils.hasText(sessionId)) {
            logger.warn("[putPackageVerify]sessionId 为空，跳过写入包验证信息，packagePath: {}, gitCommitIdFromPackage: {}", packagePath, gitCommitIdFromPackage);
            return;
        }
        try {
            Optional<ClientIndex> optional = clientRepository.findById(sessionId);
            if (optional.isPresent()) {
                ClientIndex clientIndex = optional.get();
                ClientSession session = clientIndex.getSession();
                if (session == null) {
                    logger.warn("[putPackageVerify]Session 为空，无法写入包的验证信息");
                    return;
                }
                session.setPackageVerifyData(String.format("sessionId: %s, packagePath: %s, gitCommitIdFromPackage: %s",
                        sessionId, packagePath, gitCommitIdFromPackage));
                clientIndex.setUpdateTime(new Date());
                clientRepository.save(clientIndex);
            } else {
                logger.warn("[putPackageVerify]未找到对应的 ClientIndex，sessionId: {}", sessionId);
            }
        } catch (Exception e) {
            logger.error("[putPackageVerify]存储包验证信息失败，sessionId: {}, err: {}", sessionId, e.getMessage(), e);
        }
    }

    public String getPackageVerifyData(String sessionId) {
        if (!StringUtils.hasText(sessionId)) {
            return null;
        }
        try {
            Optional<ClientIndex> optional = clientRepository.findById(sessionId);
            if (optional.isPresent() && optional.get().getSession() != null) {
                return optional.get().getSession().getPackageVerifyData();
            }
        } catch (Exception e) {
            logger.error("[getPackageVerifyData]读取包验证信息失败，sessionId: {}, err: {}", sessionId, e.getMessage(), e);
        }
        return null;
    }

    public String getLatestPackageVerifyDataByAppId(String appId) {
        if (!StringUtils.hasText(appId)) {
            return null;
        }
        ClientIndex latest = null;
        for (ClientIndex clientIndex : clientRepository.findAll()) {
            ClientSession session = clientIndex.getSession();
            if (session == null || !StringUtils.hasText(session.getPackageVerifyData())) {
                continue;
            }
            if (session.getClientInfo() == null || !appId.equalsIgnoreCase(session.getClientInfo().getAppKey())) {
                continue;
            }
            if (latest == null
                    || latest.getUpdateTime() == null
                    || (clientIndex.getUpdateTime() != null && clientIndex.getUpdateTime().after(latest.getUpdateTime()))) {
                latest = clientIndex;
            }
        }
        return latest != null && latest.getSession() != null ? latest.getSession().getPackageVerifyData() : null;
    }

    public void putSandboxStatus(String sessionId, String status, int sessionClearValidity) {
        if (!StringUtils.hasText(sessionId)) {
            logger.warn("[putSandboxStatus]sessionId 为空，跳过写入 sandbox 状态");
            return;
        }
        try {
            Optional<ClientIndex> optional = clientRepository.findById(sessionId);
            if (optional.isPresent()) {
                ClientIndex clientIndex = optional.get();
                ClientSession session = clientIndex.getSession();
                if (session == null) {
                    logger.warn("[putSandboxStatus]Session 为空，无法写入 sandbox 状态");
                    return;
                }
                session.setSandboxStatus(status);
                clientIndex.setUpdateTime(new Date());
                clientRepository.save(clientIndex);
                ClientSessionVo onlineSession = clientSessionStateService.getClientSession(sessionId, sessionClearValidity);
                if (onlineSession != null) {
                    onlineSession.setSandboxStatus(status);
                    clientSessionStateService.cacheSession(onlineSession, sessionClearValidity);
                }
            } else {
                logger.warn("[putSandboxStatus]未找到对应的 ClientIndex，sessionId: {}", sessionId);
            }
        } catch (Exception e) {
            logger.error("[putSandboxStatus]存储 sandbox 状态失败，sessionId: {}, err: {}", sessionId, e.getMessage(), e);
        }
    }

    public void putSandboxCommand(String sessionId, String command) {
        if (!StringUtils.hasText(sessionId) || !StringUtils.hasText(command)) {
            return;
        }
        redisTemplate.opsForValue().set(SANDBOX_COMMAND_KEY_PREFIX + sessionId, command, 60, TimeUnit.SECONDS);
    }

    public String pollSandboxCommand(String sessionId) {
        if (!StringUtils.hasText(sessionId)) {
            return "";
        }
        String key = SANDBOX_COMMAND_KEY_PREFIX + sessionId;
        Object value = redisTemplate.opsForValue().get(key);
        if (value != null) {
            redisTemplate.delete(key);
        }
        return value == null ? "" : String.valueOf(value);
    }
}
