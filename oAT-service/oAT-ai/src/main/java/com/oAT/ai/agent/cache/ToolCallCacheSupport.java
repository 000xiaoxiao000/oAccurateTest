package com.oAT.ai.agent.cache;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.oAT.ai.agent.AgentContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Tool call cache facade used by both native LangChain4j tool execution and fallback execution.
 */
public final class ToolCallCacheSupport {

    private static final Logger logger = LoggerFactory.getLogger(ToolCallCacheSupport.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final EnhancedToolCallCache CACHE = EnhancedToolCallCache.getInstance();
    private static final Set<String> NON_CACHEABLE_TOOL_PREFIXES = Set.of(
            "generate", "download", "clear", "delete", "remove", "create", "update", "save", "submit", "start", "stop", "cancel");
    private static final Set<String> NON_CACHEABLE_TOOL_NAMES = Set.of(
            "queryjobstatus", "checkgitconfiguration");

    private ToolCallCacheSupport() {
    }

    static {
        OBJECT_MAPPER.configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);
    }

    public static String getCachedResult(String toolName, Object arguments) {
        if (!isCacheable(toolName)) {
            return null;
        }
        String key = buildCacheKey(toolName, arguments);
        String cached = CACHE.get(key, String.class);
        if (cached != null) {
            logger.debug("Tool call cache hit: tool={}, key={}", toolName, key);
        }
        return cached;
    }

    public static void cacheResult(String toolName, Object arguments, String result) {
        if (!isCacheable(toolName) || result == null) {
            return;
        }
        String key = buildCacheKey(toolName, arguments);
        CACHE.put(key, result);
        logger.debug("Tool call cache put: tool={}, key={}, resultLength={}", toolName, key, result.length());
    }

    private static boolean isCacheable(String toolName) {
        String normalized = normalize(toolName);
        if (normalized.isEmpty() || NON_CACHEABLE_TOOL_NAMES.contains(normalized)) {
            return false;
        }
        String lowered = toolName.toLowerCase(Locale.ROOT);
        for (String prefix : NON_CACHEABLE_TOOL_PREFIXES) {
            if (lowered.startsWith(prefix)) {
                return false;
            }
        }
        return true;
    }

    private static String buildCacheKey(String toolName, Object arguments) {
        String payload = normalize(toolName) + "|" + currentContextKey() + "|" + normalizeArguments(arguments);
        return "tool:" + normalize(toolName) + ":" + sha256(payload);
    }

    private static String currentContextKey() {
        AgentContext context = AgentContext.getContext();
        if (context == null) {
            return "project=<none>|user=<none>|scope=<none>|entity=<none>";
        }
        return "project=" + safe(context.getProjectId())
                + "|user=" + safe(context.getUserId())
                + "|scope=" + safe(context.getMemoryScope())
                + "|entity=" + safe(context.getSelectedEntityId());
    }

    private static String normalizeArguments(Object arguments) {
        if (arguments == null) {
            return "";
        }
        try {
            if (arguments instanceof String) {
                String raw = ((String) arguments).trim();
                if (raw.startsWith("{") || raw.startsWith("[")) {
                    return normalizeArguments(OBJECT_MAPPER.readValue(raw, Object.class));
                }
            }
            if (arguments instanceof Map<?, ?>) {
                Map<?, ?> map = (Map<?, ?>) arguments;
                TreeMap<String, Object> sorted = new TreeMap<>();
                for (Map.Entry<?, ?> entry : map.entrySet()) {
                    sorted.put(String.valueOf(entry.getKey()), entry.getValue());
                }
                return OBJECT_MAPPER.writeValueAsString(sorted);
            }
            return OBJECT_MAPPER.writeValueAsString(arguments);
        } catch (JsonProcessingException e) {
            return String.valueOf(arguments);
        }
    }

    private static String normalize(String value) {
        if (value == null) {
            return "";
        }
        return value.trim().replaceAll("[^a-zA-Z0-9]", "").toLowerCase(Locale.ROOT);
    }

    private static String safe(String value) {
        return value == null ? "<none>" : value;
    }

    private static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                builder.append(String.format("%02x", b));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm is not available", e);
        }
    }
}
