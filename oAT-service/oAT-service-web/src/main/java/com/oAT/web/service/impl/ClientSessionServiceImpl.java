package com.oAT.web.service.impl;

import com.oAT.agent.model.TraceNode;
import com.oAT.server.model.ClientInfoVo;
import com.oAT.server.model.ClientSessionVo;
import com.oAT.web.collector.ClientSessionApplicationBinder;
import com.oAT.web.collector.ClientProbeLifecycleDataService;
import com.oAT.web.collector.ClientSessionStateService;
import com.oAT.web.esDao.entity.*;
import com.oAT.web.language.java.JavaStaticSourceIngestService;
import com.oAT.web.service.ClientSessionService;
import com.oAT.web.service.ProbeStatusService;
import com.oAT.web.service.TraceNodeCache;
import com.oAT.web.service.TraceNodeFilter;
import com.oAT.web.service.entity.TraceItemSearchParam;
import com.oAT.web.service.entity.TraceItemVo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;

import java.util.*;
import java.util.concurrent.*;

import org.springframework.data.redis.core.RedisTemplate;
import java.util.stream.Collectors;

@Service
public class ClientSessionServiceImpl implements ClientSessionService, InitializingBean, ApplicationContextAware {
    static final Logger logger = LoggerFactory.getLogger(ClientSessionServiceImpl.class);

    TraceNodeCache nodeCache;
    @Value("${traceNode.cache.capacity:5000}")
    private int capacity; //缓存容量
    @Value("${traceNode.cache.validityTime:300}")
    private Integer validityTime;// 缓存有效期

    // 会话清除限期
    private int sessionClearValidity = 30 * 60 * 1000;
    // 会话在线有效期
    private int sessionOnlineValidity = 1000 * 20;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Autowired
    ProbeStatusService probeStatusService;

    private ApplicationContext applicationContext;
    private Map<String, TraceNodeFilter> nodeFilters;

    @Autowired
    private ClientSessionStateService clientSessionStateService;
    @Autowired
    private ClientProbeLifecycleDataService clientProbeLifecycleDataService;
    @Autowired
    private ClientSessionApplicationBinder clientSessionApplicationBinder;

    @Autowired
    private JavaStaticSourceIngestService javaStaticSourceIngestService;

    @Autowired
    @Qualifier("coverageExecutor")
    private Executor coverageExecutor;

    @Override
    public void afterPropertiesSet() {
        // 创建缓存
        nodeCache = new TraceNodeCache(capacity, validityTime, redisTemplate);
        nodeFilters = applicationContext.getBeansOfType(TraceNodeFilter.class);
    }

    @Override
    public void putTraceNode(TraceNode node) {
        for (TraceNodeFilter filter : nodeFilters.values()) {
            node = filter.doFilter(node);
        }
        nodeCache.put(node);
    }

    @Override
    public Map<String, TraceNode> getTraceNodes(String traceId) {
        return nodeCache.getTraceNodes(traceId);
    }

    @Override
    public List<TraceItemVo> getTraceItemByIndex(Integer lastUpdateIndex, boolean after, Integer size,
                                                  List<String> appIds) {
        List<TraceItemVo> list;
        if (after) {
            // New logic: getRange returns all items with index > lastUpdateIndex
            list = nodeCache.getRange(lastUpdateIndex, size, appIds);
        } else {
            // Need a way to fetch older items relative to lastUpdateIndex?
            // The original logic used list.remove(0).
            list = nodeCache.getRangeBefore(lastUpdateIndex, size, appIds);
        }
        return list;
    }

    @Override
    public List<TraceItemVo> getTraceItemByTime(Integer upToTime, TraceItemSearchParam param) {
        ArrayList<TraceItemVo> list = nodeCache.getRangeByTime(upToTime, new TraceNodeCache.CacheFilter() {
            @Override
            public boolean doFilter(TraceItemVo itemVo) {
                if (param.getAppIds() != null && !param.getAppIds().isEmpty()) {
                    if (!param.getAppIds().contains(itemVo.getAppId())) {
                        return false;
                    }
                }
                if (param.getClientIps() != null && !param.getClientIps().isEmpty()) {
                    if (!param.getClientIps().contains(itemVo.getClientIp())) {
                        return false;
                    }
                }
                return true;
            }
        });
        // 最大值返回限制
        if (list.size() > param.getMaxSize()) {
            return list.subList(0, param.getMaxSize());
        }
        return list;

    }

    /**
     * 基于会话ID 获取客户端会话
     *
     * @param sessionId
     * @return
     */
    @Override
    public ClientSessionVo getClientSession(String sessionId) {
        return clientSessionStateService.getClientSession(sessionId, sessionClearValidity);
    }

    @Override
    public ClientSessionVo doLogin(ClientInfoVo clientInfoVo) {
        Assert.hasText(clientInfoVo.getPid(), "Client pid must be not null");
        Assert.hasText(clientInfoVo.getSystemDir(), "Client dir must be not null");
        Assert.hasText(clientInfoVo.getAgentVersion(), "Agent version must be not null");
        clientSessionStateService.disableDuplicateOnlineSessions(clientInfoVo, getOnlineSessions());

        ClientSessionVo result = new ClientSessionVo();
        ClientIndex clientIndex = clientSessionStateService.createActiveClientIndex(clientInfoVo);

        result.setSessionId(clientIndex.getId());
        result.setClientInfo(clientInfoVo);
        result.setLoginTime(new Date());
        result.setLastHeartbeatTime(System.currentTimeMillis());
        clientSessionApplicationBinder.bindApplication(result, clientInfoVo);


        // TODO 客户端自定义上传节点
//        result.setUploadUrls();
        // 保存至 Redis
        clientSessionStateService.cacheSession(result, sessionClearValidity);
        probeStatusService.onLogin(result);
        return result;
    }

    @Override
    public List<ClientSessionVo> getOnlineSessions() {
        return clientSessionStateService.getOnlineSessions(sessionOnlineValidity);
    }

    @Override
    public List<ClientSessionVo> getOnlineSessionsByAppId(String appId) {
        return getOnlineSessions().stream()
                .filter(vo -> vo.getClientInfo() != null && appId.equalsIgnoreCase(vo.getClientInfo().getAppKey()))
                .collect(Collectors.toList());
    }

    @Override
    public void setApplicationContext(@NonNull ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }

    @Override
    public void heartbeat(String sessionId, String appId, Long timesTamp) {
        ClientSessionVo vo = getClientSession(sessionId);
        Assert.notNull(vo, "找不到指定客户端session id=" + sessionId);
        long heartbeatTime = System.currentTimeMillis();
        clientSessionStateService.heartbeat(sessionId, vo, heartbeatTime, sessionClearValidity);
        probeStatusService.onHeartbeat(vo);
    }

    @Override
    public void saveStaticData(String appId, String data) {
        javaStaticSourceIngestService.saveStaticData(appId, data, coverageExecutor);
    }

    // 将包验证信息存储到 MySQL
    @Override
    public void putPackageVerify(String sessionId, String packagePath, String gitCommitIdFromPackage) {
        clientProbeLifecycleDataService.putPackageVerify(sessionId, packagePath, gitCommitIdFromPackage);
    }

    @Override
    public String getPackageVerifyData(String sessionId) {
        return clientProbeLifecycleDataService.getPackageVerifyData(sessionId);
    }

    @Override
    public String getLatestPackageVerifyDataByAppId(String appId) {
        return clientProbeLifecycleDataService.getLatestPackageVerifyDataByAppId(appId);
    }

    @Override
    public void putSandboxStatus(String sessionId, String status) {
        clientProbeLifecycleDataService.putSandboxStatus(sessionId, status, sessionClearValidity);
    }

    @Override
    public void putSandboxCommand(String sessionId, String command) {
        clientProbeLifecycleDataService.putSandboxCommand(sessionId, command);
    }

    @Override
    public String pollSandboxCommand(String sessionId) {
        return clientProbeLifecycleDataService.pollSandboxCommand(sessionId);
    }

}
