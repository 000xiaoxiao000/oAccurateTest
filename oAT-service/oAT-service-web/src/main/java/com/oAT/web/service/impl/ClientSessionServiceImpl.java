package com.oAT.web.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.oAT.agent.model.Application;
import com.oAT.agent.model.TraceNode;
import com.oAT.server.model.ClientInfoVo;
import com.oAT.server.model.ClientSessionVo;
import com.oAT.web.esDao.ClientRepository;
import com.oAT.web.esDao.StaticInfoRepository;
import com.oAT.web.esDao.SystemRepository;
import com.oAT.web.esDao.entity.*;
import com.oAT.web.service.AppService;
import com.oAT.web.service.ClientSessionService;
import com.oAT.web.service.TraceNodeCache;
import com.oAT.web.service.TraceNodeFilter;
import com.oAT.web.service.entity.AppVo;
import com.oAT.web.service.entity.TraceItemSearchParam;
import com.oAT.web.service.entity.TraceItemVo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeanUtils;
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
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.io.StringReader;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.*;
import java.nio.charset.StandardCharsets;

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

    private static final String SESSIONS_KEY_PREFIX = "oAT:sessions:";
    private static final String STATIC_DATA_CACHE_KEY_PREFIX = "oAT:static-data:";
    private static final long STATIC_DATA_CACHE_VALIDITY_MILLIS = TimeUnit.MINUTES.toMillis(30);

    @Autowired
    AppService appService;

    @Autowired
    private ClientRepository clientRepository;
    private ApplicationContext applicationContext;
    private Map<String, TraceNodeFilter> nodeFilters;

    @Autowired
    private SystemRepository systemRepository;

    @Autowired
    private StaticInfoRepository staticInfoRepository;

    @Autowired
    private ObjectMapper objectMapper;

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
        ClientSessionVo vo = (ClientSessionVo) redisTemplate.opsForValue().get(SESSIONS_KEY_PREFIX + sessionId);
        if (vo == null) {
            Optional<ClientIndex> clientIndex = clientRepository.findById(sessionId);
            if (clientIndex.isPresent()) {
                vo = convert(clientIndex.get());
                redisTemplate.opsForValue().set(SESSIONS_KEY_PREFIX + sessionId, vo, sessionClearValidity, TimeUnit.MILLISECONDS);
            }
        }
        return vo;
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
        // loginTime
        try {
            if (vo.getLoginTime() == null && index.getSession() != null && index.getSession().getLoginTime() != null) {
                vo.setLoginTime(new SimpleDateFormat(ClientIndex.getDateFormat()).parse(index.getSession().getLoginTime()));
            }
        } catch (Exception ignore) {
        }
        // Application 组装
        if (vo.getClientInfo() != null && vo.getClientInfo().getAppKey() != null) {
            Optional<SystemIndex> byId = systemRepository.findById(vo.getClientInfo().getAppKey());
            if (byId.isPresent()) {
                App app = byId.get().getApp();
                vo.setApplication(new Application(vo.getClientInfo().getAppKey(), app.getName(), app.getSrcName()));
            }
        }
        // configs
        String configs = index.getSession() != null ? index.getSession().getConfigs() : null;
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        try {
            if (org.apache.commons.lang3.StringUtils.isNotBlank(configs)) {
                Properties properties = objectMapper.readValue(configs, Properties.class);
                vo.setConfigs(properties);
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

    @Override
    public ClientSessionVo doLogin(ClientInfoVo clientInfoVo) {
        Assert.hasText(clientInfoVo.getPid(), "Client pid must be not null");
        Assert.hasText(clientInfoVo.getSystemDir(), "Client dir must be not null");
        Assert.hasText(clientInfoVo.getAgentVersion(), "Agent version must be not null");

        ClientSessionVo result = new ClientSessionVo();
        ClientSession session = new ClientSession();
        ClientInfo clientInfo = new ClientInfo();
        BeanUtils.copyProperties(clientInfoVo, clientInfo);
        session.setClientInfo(clientInfo);
        session.setStatus(ClientSession.Status.active.toString());
        session.setLoginTime(new SimpleDateFormat(StandardDate.dateFormat).format(new Date()));
        ClientIndex clientIndex = clientRepository.save(new ClientIndex(session));

        result.setSessionId(clientIndex.getId());
        result.setClientInfo(clientInfoVo);
        result.setLoginTime(new Date());
        result.setLastHeartbeatTime(System.currentTimeMillis());
        if (clientInfoVo.getAppKey() != null) {
            AppVo appVo = appService.getApp(clientInfoVo.getAppKey());
            result.setApplication(new Application(appVo.getId(), appVo.getName(), appVo.getSrcName()));

            if (StringUtils.hasText(appVo.getProperties())) {
                Properties p = new Properties();
                try {
                    p.load(new StringReader(appVo.getProperties()));
                } catch (IOException e) {
                    throw new RuntimeException(String.format("名称:$s id:%s 属性配置装载失败: ", appVo.getName(), appVo.getId()));
                }
                result.setConfigs(p);
            } else {
                result.setConfigs(new Properties());
            }
        }


        // TODO 客户端自定义上传节点
//        result.setUploadUrls();
        // 保存至 Redis
        redisTemplate.opsForValue().set(SESSIONS_KEY_PREFIX + result.getSessionId(), result, sessionClearValidity, TimeUnit.MILLISECONDS);
        return result;
    }

    @Override
    public List<ClientSessionVo> getOnlineSessions() {
        Set<String> keys = redisTemplate.keys(SESSIONS_KEY_PREFIX + "*");
        if (keys == null) return Collections.emptyList();
        return keys.stream()
                .map(key -> (ClientSessionVo) redisTemplate.opsForValue().get(key))
                .filter(vo -> vo != null && (System.currentTimeMillis() - vo.getLastHeartbeatTime() < sessionOnlineValidity))
                .collect(Collectors.toList());
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
        vo.setLastHeartbeatTime(System.currentTimeMillis());
        redisTemplate.opsForValue().set(SESSIONS_KEY_PREFIX + sessionId, vo, sessionClearValidity, TimeUnit.MILLISECONDS);
    }

    @Override
    public void saveStaticData(String appId, String data) {
        if (org.apache.commons.lang3.StringUtils.isBlank(appId) || org.apache.commons.lang3.StringUtils.isBlank(data)) {
            return;
        }
        String cacheKey = cacheStaticData(appId, data);
        int payloadBytes = data.getBytes(StandardCharsets.UTF_8).length;
        logger.info("静态源码缓存写入成功, appId={}, cacheKey={}, payloadBytes={}, ttlMinutes={}",
                appId, cacheKey, payloadBytes, TimeUnit.MILLISECONDS.toMinutes(STATIC_DATA_CACHE_VALIDITY_MILLIS));
        coverageExecutor.execute(() -> persistStaticDataFromCache(appId, cacheKey));
        logger.info("静态源码异步落 ES 已投递, appId={}, cacheKey={}", appId, cacheKey);
    }

    private String cacheStaticData(String appId, String data) {
        String cacheKey = STATIC_DATA_CACHE_KEY_PREFIX + appId + ":" + System.currentTimeMillis() + ":" + UUID.randomUUID();
        redisTemplate.opsForValue().set(Objects.requireNonNull(cacheKey), Objects.requireNonNull(data),
                STATIC_DATA_CACHE_VALIDITY_MILLIS, TimeUnit.MILLISECONDS);
        return cacheKey;
    }

    private void persistStaticDataFromCache(String appId, String cacheKey) {
        long startTime = System.currentTimeMillis();
        Object cachedPayload = redisTemplate.opsForValue().get(Objects.requireNonNull(cacheKey));
        if (!(cachedPayload instanceof String) || !StringUtils.hasText((String) cachedPayload)) {
            logger.warn("静态源码缓存不存在或为空, appId={}, cacheKey={}", appId, cacheKey);
            return;
        }

        String payload = (String) cachedPayload;
        JsonNode data;
        try {
            data = objectMapper.readTree(payload);
        } catch (Exception e) {
            logger.error("解析静态源码缓存失败, appId={}, cacheKey={}", appId, cacheKey, e);
            return;
        }

        int classCount = data.size();
        logger.info("静态源码开始落 ES, appId={}, cacheKey={}, classCount={}, payloadBytes={}",
                appId, cacheKey, classCount, payload.getBytes(StandardCharsets.UTF_8).length);
        try {
            StaticDataPersistStats stats = persistStaticDataToEs(appId, data);
            redisTemplate.delete(Objects.requireNonNull(cacheKey));
            logger.info("静态源码落 ES 完成, appId={}, cacheKey={}, classCount={}, created={}, updated={}, skipped={}, failed={}, elapsedMs={}",
                    appId, cacheKey, classCount, stats.createdCount, stats.updatedCount, stats.skippedCount,
                    stats.failedCount, System.currentTimeMillis() - startTime);
        } catch (Exception e) {
            logger.error("静态源码缓存落库 ES 失败, appId={}, cacheKey={}, classCount={}, elapsedMs={}",
                    appId, cacheKey, classCount, System.currentTimeMillis() - startTime, e);
        }
    }

    private StaticDataPersistStats persistStaticDataToEs(String appId, JsonNode data) {
        StaticDataPersistStats stats = new StaticDataPersistStats();
        Iterator<Map.Entry<String, JsonNode>> fields = data.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> entry = fields.next();
            try {
                StaticSourceClassInfo classInfo = objectMapper.treeToValue(entry.getValue(), StaticSourceClassInfo.class);
                if (classInfo != null && StringUtils.hasText(classInfo.getClassName())) {
                    List<StaticSourceInfo> list = staticInfoRepository.findByAppIdAndClassInfo_ClassName(appId, classInfo.getClassName());
                    StaticSourceInfo index;
                    if (!list.isEmpty()) {
                        index = list.get(0);
                        index.setClassInfo(classInfo);
                        index.setUpdateTime(new Date());
                        stats.updatedCount++;
                    } else {
                        index = new StaticSourceInfo(classInfo);
                        index.setAppId(appId);
                        stats.createdCount++;
                    }
                    staticInfoRepository.save(index);
                } else {
                    stats.skippedCount++;
                }
            } catch (Exception e) {
                stats.failedCount++;
                logger.error("保存静态源码信息失败 key:{}", entry.getKey(), e);
            }
        }
        return stats;
    }

    private static final class StaticDataPersistStats {
        private int createdCount;
        private int updatedCount;
        private int skippedCount;
        private int failedCount;
    }

}
