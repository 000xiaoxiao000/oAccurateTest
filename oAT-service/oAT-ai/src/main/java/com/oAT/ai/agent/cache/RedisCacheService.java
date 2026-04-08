package com.oAT.ai.agent.cache;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.util.concurrent.TimeUnit;

/**
 * Redis 缓存服务
 * 用于 AI 模块的数据缓存，替代内存缓存实现分布式缓存
 * <p>
 * 支持特性：
 * 1. 工具调用结果缓存（TTL=5分钟）
 * 2. 语义缓存（相似问题命中，TTL=30分钟）
 * 3. 会话上下文缓存（TTL=2小时）
 * 4. 用户偏好缓存（TTL=24小时）
 * </p>
 */
public class RedisCacheService {

    private static final Logger logger = LoggerFactory.getLogger(RedisCacheService.class);

    private final StringRedisTemplate redisTemplate;

    /** Key 前缀 */
    public static final String PREFIX_TOOL_CALL = "ai:tool:";
    public static final String PREFIX_SEMANTIC = "ai:semantic:";
    public static final String PREFIX_SESSION = "ai:session:";
    public static final String PREFIX_USER_PREF = "ai:userpref:";
    public static final String PREFIX_LLM_RESPONSE = "ai:llm:";

    /** 默认 TTL */
    private static final long DEFAULT_TOOL_TTL_MINUTES = 5;
    private static final long DEFAULT_SEMANTIC_TTL_MINUTES = 30;
    private static final long DEFAULT_SESSION_TTL_HOURS = 2;
    private static final long DEFAULT_USER_PREF_TTL_HOURS = 24;

    /**
     * 构造函数
     *
     * @param redisTemplate Spring 的 StringRedisTemplate 实例
     */
    public RedisCacheService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
        // 确保 key 使用 String 序列化
        this.redisTemplate.setKeySerializer(new StringRedisSerializer());
        this.redisTemplate.setHashKeySerializer(new StringRedisSerializer());
        logger.info("RedisCacheService initialized");
    }

    // ==================== 工具调用结果缓存 ====================

    /**
     * 缓存工具调用结果
     */
    public void cacheToolResult(String toolName, String paramsHash, String result) {
        try {
            String key = PREFIX_TOOL_CALL + toolName + ":" + paramsHash;
            redisTemplate.opsForValue().set(key, result, DEFAULT_TOOL_TTL_MINUTES, TimeUnit.MINUTES);
            logger.debug("Cached tool result: {}", key);
        } catch (Exception e) {
            logger.warn("Failed to cache tool result for {}: {}", toolName, e.getMessage());
        }
    }

    /**
     * 获取工具调用缓存
     */
    public String getToolResult(String toolName, String paramsHash) {
        try {
            String key = PREFIX_TOOL_CALL + toolName + ":" + paramsHash;
            return redisTemplate.opsForValue().get(key);
        } catch (Exception e) {
            logger.warn("Failed to get tool result for {}: {}", toolName, e.getMessage());
            return null;
        }
    }

    // ==================== 语义缓存 ====================

    /**
     * 缓存语义相似的问答结果（用于向量数据库的 fallback）
     */
    public void cacheSemanticAnswer(String questionHash, String answer) {
        try {
            String key = PREFIX_SEMANTIC + questionHash;
            redisTemplate.opsForValue().set(key, answer, DEFAULT_SEMANTIC_TTL_MINUTES, TimeUnit.MINUTES);
        } catch (Exception e) {
            logger.warn("Failed to cache semantic answer: {}", e.getMessage());
        }
    }

    /**
     * 获取语义缓存的答案
     */
    public String getSemanticAnswer(String questionHash) {
        try {
            String key = PREFIX_SEMANTIC + questionHash;
            return redisTemplate.opsForValue().get(key);
        } catch (Exception e) {
            return null;
        }
    }

    // ==================== 会话上下文缓存 ====================

    /**
     * 缓存会话摘要（用于多轮对话优化）
     */
    public void cacheSessionSummary(String sessionId, String summaryJson) {
        try {
            String key = PREFIX_SESSION + "summary:" + sessionId;
            redisTemplate.opsForValue().set(key, summaryJson, DEFAULT_SESSION_TTL_HOURS, TimeUnit.HOURS);
        } catch (Exception e) {
            logger.warn("Failed to cache session summary: {}", e.getMessage());
        }
    }

    /**
     * 获取会话摘要
     */
    public String getSessionSummary(String sessionId) {
        try {
            String key = PREFIX_SESSION + "summary:" + sessionId;
            return redisTemplate.opsForValue().get(key);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 追加会话消息到 Redis List（用于完整多轮历史）
     */
    public void appendSessionMessage(String sessionId, String messageJson) {
        try {
            String key = PREFIX_SESSION + "messages:" + sessionId;
            Long length = redisTemplate.opsForList().rightPush(key, messageJson);
            if (length != null && length == 1) {
                // 首条消息时设置 TTL
                redisTemplate.expire(key, DEFAULT_SESSION_TTL_HOURS, TimeUnit.HOURS);
            }
            // 保持最多 50 条消息
            if (length != null && length > 50) {
                redisTemplate.opsForList().trim(key, -50, -1);
            }
        } catch (Exception e) {
            logger.warn("Failed to append session message: {}", e.getMessage());
        }
    }

    /**
     * 获取会话消息历史（最近 N 条）
     */
    public java.util.List<String> getSessionMessages(String sessionId, int count) {
        try {
            String key = PREFIX_SESSION + "messages:" + sessionId;
            return redisTemplate.opsForList().range(key, -count, -1);
        } catch (Exception e) {
            return java.util.Collections.emptyList();
        }
    }

    // ==================== 用户偏好缓存 ====================

    /**
     * 缓存用户偏好设置
     */
    public void cacheUserPreference(String userId, String prefKey, String prefValue) {
        try {
            String key = PREFIX_USER_PREF + userId;
            redisTemplate.opsForHash().put(key, prefKey, prefValue);
            redisTemplate.expire(key, DEFAULT_USER_PREF_TTL_HOURS, TimeUnit.HOURS);
        } catch (Exception e) {
            logger.warn("Failed to cache user preference: {}", e.getMessage());
        }
    }

    /**
     * 获取用户偏好设置
     */
    public String getUserPreference(String userId, String prefKey) {
        try {
            String key = PREFIX_USER_PREF + userId;
            return (String) redisTemplate.opsForHash().get(key, prefKey);
        } catch (Exception e) {
            return null;
        }
    }

    // ==================== LLM 响应缓存 ====================

    /**
     * 缓存 LLM 响应（用于相同问题的快速响应）
     */
    public void cacheLLMResponse(String questionHash, String responseJson) {
        try {
            String key = PREFIX_LLM_RESPONSE + questionHash;
            redisTemplate.opsForValue().set(key, responseJson, DEFAULT_SEMANTIC_TTL_MINUTES, TimeUnit.MINUTES);
        } catch (Exception e) {
            logger.warn("Failed to cache LLM response: {}", e.getMessage());
        }
    }

    /**
     * 获取缓存的 LLM 响应
     */
    public String getLLMResponse(String questionHash) {
        try {
            String key = PREFIX_LLM_RESPONSE + questionHash;
            return redisTemplate.opsForValue().get(key);
        } catch (Exception e) {
            return null;
        }
    }

    // ==================== 统计与清理 ====================

    /**
     * 清除指定前缀的所有缓存
     */
    public void clearByPrefix(String prefix) {
        try {
            var keys = redisTemplate.keys(prefix + "*");
            if (keys != null && !keys.isEmpty()) {
                redisTemplate.delete(keys);
                logger.info("Cleared {} keys with prefix: {}", keys.size(), prefix);
            }
        } catch (Exception e) {
            logger.error("Failed to clear cache by prefix {}: {}", prefix, e.getMessage());
        }
    }

    /**
     * 获取缓存统计信息
     */
    public java.util.Map<String, Object> getStats() {
        java.util.Map<String, Object> stats = new java.util.HashMap<>();
        try {
            var toolKeys = redisTemplate.keys(PREFIX_TOOL_CALL + "*");
            var semanticKeys = redisTemplate.keys(PREFIX_SEMANTIC + "*");
            var sessionKeys = redisTemplate.keys(PREFIX_SESSION + "*");
            var llmKeys = redisTemplate.keys(PREFIX_LLM_RESPONSE + "*");

            stats.put("toolCacheSize", toolKeys != null ? toolKeys.size() : 0);
            stats.put("semanticCacheSize", semanticKeys != null ? semanticKeys.size() : 0);
            stats.put("sessionCacheSize", sessionKeys != null ? sessionKeys.size() : 0);
            stats.put("llmCacheSize", llmKeys != null ? llmKeys.size() : 0);
            stats.put("available", true);
        } catch (Exception e) {
            stats.put("available", false);
            stats.put("error", e.getMessage());
        }
        return stats;
    }

    /**
     * 检查 Redis 是否可用
     */
    public boolean isAvailable() {
        try {
            return Boolean.TRUE.equals(redisTemplate.getConnectionFactory()
                .getConnection().ping());
        } catch (Exception e) {
            return false;
        }
    }
}
