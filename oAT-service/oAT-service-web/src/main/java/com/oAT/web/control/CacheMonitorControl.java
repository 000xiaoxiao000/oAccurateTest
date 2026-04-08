package com.oAT.web.control;

import com.oAT.ai.agent.cache.EnhancedToolCallCache;
import com.oAT.web.control.entity.ResultNotified;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

/**
 * 缓存监控控制器
 * 提供缓存统计和管理接口
 */
@Controller
@RequestMapping("/api/cache")
public class CacheMonitorControl {

    private final EnhancedToolCallCache cache = EnhancedToolCallCache.getInstance();

    /**
     * 获取缓存统计信息
     */
    @GetMapping("/stats")
    @ResponseBody
    public ResultNotified<?> getCacheStats() {
        try {
            EnhancedToolCallCache.CacheStats stats = cache.getStats();
            
            Map<String, Object> result = new HashMap<>();
            result.put("totalEntries", stats.getTotalEntries());
            result.put("expiredEntries", stats.getExpiredEntries());
            result.put("totalHits", stats.getTotalHits());
            result.put("totalSize", stats.getTotalSize());
            result.put("totalSizeKB", stats.getTotalSize() / 1024);
            result.put("globalHits", stats.getGlobalHits());
            result.put("globalMisses", stats.getGlobalMisses());
            result.put("hitRate", String.format("%.2f%%", stats.getHitRate()));
            result.put("maxSize", 2000);
            result.put("utilization", String.format("%.2f%%", 
                (double) stats.getTotalEntries() / 2000 * 100));
            
            return new ResultNotified<>(true, "获取缓存统计成功", (Serializable) result);
        } catch (Exception e) {
            return new ResultNotified<>(false, "获取缓存统计失败：" + e.getMessage());
        }
    }

    /**
     * 清空所有缓存
     */
    @PostMapping("/clear")
    @ResponseBody
    public ResultNotified<String> clearCache() {
        try {
            cache.clear();
            return new ResultNotified<>(true, "缓存已清空");
        } catch (Exception e) {
            return new ResultNotified<>(false, "清空缓存失败：" + e.getMessage());
        }
    }

    /**
     * 删除指定缓存
     */
    @DeleteMapping("/{key}")
    @ResponseBody
    public ResultNotified<String> removeCache(@PathVariable String key) {
        try {
            cache.remove(key);
            return new ResultNotified<>(true, "缓存已删除：" + key);
        } catch (Exception e) {
            return new ResultNotified<>(false, "删除缓存失败：" + e.getMessage());
        }
    }

    /**
     * 获取缓存健康状态
     */
    @GetMapping("/health")
    @ResponseBody
    public ResultNotified<?> getCacheHealth() {
        try {
            EnhancedToolCallCache.CacheStats stats = cache.getStats();
            
            Map<String, Object> health = new HashMap<>();
            health.put("status", "healthy");
            health.put("utilization", (double) stats.getTotalEntries() / 2000 * 100);
            health.put("hitRate", stats.getHitRate());
            
            // 判断健康状态
            String status = "healthy";
            if (stats.getHitRate() < 50) {
                status = "warning";
            }
            if (stats.getTotalEntries() >= 1900) {
                status = "critical";
            }
            health.put("status", status);
            
            return new ResultNotified<>(true, "缓存健康检查完成", (Serializable) health);
        } catch (Exception e) {
            return new ResultNotified<>(false, "健康检查失败：" + e.getMessage());
        }
    }
}
