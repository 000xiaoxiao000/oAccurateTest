package com.oAT.web.language.java;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.oAT.web.esDao.StaticInfoRepository;
import com.oAT.web.esDao.entity.StaticSourceClassInfo;
import com.oAT.web.esDao.entity.StaticSourceInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

@Service
public class JavaStaticSourceIngestService {
    private static final Logger logger = LoggerFactory.getLogger(JavaStaticSourceIngestService.class);
    private static final String STATIC_DATA_CACHE_KEY_PREFIX = "oAT:static-data:";
    private static final long STATIC_DATA_CACHE_VALIDITY_MILLIS = TimeUnit.MINUTES.toMillis(30);

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Autowired
    private StaticInfoRepository staticInfoRepository;

    @Autowired
    private ObjectMapper objectMapper;

    public void saveStaticData(String appId, String data, Executor executor) {
        if (org.apache.commons.lang3.StringUtils.isBlank(appId) || org.apache.commons.lang3.StringUtils.isBlank(data)) {
            return;
        }
        String cacheKey = cacheStaticData(appId, data);
        int payloadBytes = data.getBytes(StandardCharsets.UTF_8).length;
        logger.info("静态源码缓存写入成功, appId={}, cacheKey={}, payloadBytes={}, ttlMinutes={}",
                appId, cacheKey, payloadBytes, TimeUnit.MILLISECONDS.toMinutes(STATIC_DATA_CACHE_VALIDITY_MILLIS));
        executor.execute(() -> persistStaticDataFromCache(appId, cacheKey));
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
