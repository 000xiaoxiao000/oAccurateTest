package com.oAT.web.service;

import com.oAT.agent.model.HttpTraceNode;
import com.oAT.agent.model.TraceNode;
import com.oAT.web.service.entity.TraceItemVo;
import org.springframework.data.redis.core.RedisTemplate;

import java.util.*;
import java.util.concurrent.TimeUnit;

public class TraceNodeCache {
    private final int capacity;
    private Integer validityTime;
    private RedisTemplate<String, Object> redisTemplate;

    private static final String TRACE_NODES_KEY_PREFIX = "oAT:trace:nodes:";
    private static final String TRACE_ITEMS_LIST_KEY = "oAT:trace:items";
    private static final String TRACE_ITEMS_ID_PREFIX = "oAT:trace:item:";
    private static final String TRACE_ITEMS_COUNTER_KEY = "oAT:trace:items:counter";

    public TraceNodeCache(int capacity, Integer validityTime, RedisTemplate<String, Object> redisTemplate) {
        this.capacity = capacity;
        this.validityTime = validityTime;
        this.redisTemplate = redisTemplate;
    }

    public void put(TraceNode node) {
        String nodesKey = TRACE_NODES_KEY_PREFIX + node.getTraceId();
        redisTemplate.opsForHash().put(nodesKey, node.getTraceNodeId(), node);
        redisTemplate.expire(nodesKey, validityTime, TimeUnit.SECONDS);

        if ("0".equals(node.getTraceNodeId()) && node instanceof HttpTraceNode) {
            TraceItemVo item = new TraceItemVo(node.getTraceId(),
                    ((HttpTraceNode) node).getRequestUrl(),
                    validityTime);
            item.setAddressIp(node.getAddressIp());
            item.setClientIp(((HttpTraceNode) node).getClientIp());
            if (node.getApp() != null) {
                item.setAppId(node.getApp().getAppId());
            }
            int index = add(item);
            item.setIndex(index);
            // Save again with index updated (already done in add, but can do it here for clarity)
            redisTemplate.opsForValue().set(TRACE_ITEMS_ID_PREFIX + item.getTraceId(), item, validityTime,
                    TimeUnit.SECONDS);
        }
    }

    public Map<String, TraceNode> getTraceNodes(String traceId) {
        Map<Object, Object> entries = redisTemplate.opsForHash().entries(TRACE_NODES_KEY_PREFIX + traceId);
        Map<String, TraceNode> result = new HashMap<>();
        for (Map.Entry<Object, Object> entry : entries.entrySet()) {
            result.put((String) entry.getKey(), (TraceNode) entry.getValue());
        }
        return result;
    }

    // 新增条目
    public synchronized int add(TraceItemVo item) {
        Long size = redisTemplate.opsForList().size(TRACE_ITEMS_LIST_KEY);
        if (size != null && size >= capacity) {
            // 简单淘汰：移除最旧的一个
            String oldTraceId = (String) redisTemplate.opsForList().rightPop(TRACE_ITEMS_LIST_KEY);
            if (oldTraceId != null) {
                redisTemplate.delete(TRACE_ITEMS_ID_PREFIX + oldTraceId);
            }
        }
        redisTemplate.opsForList().leftPush(TRACE_ITEMS_LIST_KEY, item.getTraceId());

        // Use a counter for the index to maintain frontend compatibility
        Long nextIndex = redisTemplate.opsForValue().increment(TRACE_ITEMS_COUNTER_KEY);
        int index = nextIndex != null ? nextIndex.intValue() : 0;
        item.setIndex(index);

        redisTemplate.opsForValue().set(TRACE_ITEMS_ID_PREFIX + item.getTraceId(), item, validityTime,
                TimeUnit.SECONDS);
        return index;
    }

    // 获取索引 - 基于 traceId 的获取
    public TraceItemVo get(String traceId) {
        return (TraceItemVo) redisTemplate.opsForValue().get(TRACE_ITEMS_ID_PREFIX + traceId);
    }

    public List<TraceItemVo> getRange(int lastKnownIndex, int maxSize, List<String> appIds) {
        // Since we LPUSH new items, index 0 is the newest.
        // We iterate from index 0 until we find an item with index <= lastKnownIndex.
        List<Object> traceIds = redisTemplate.opsForList().range(TRACE_ITEMS_LIST_KEY, 0, -1);
        List<TraceItemVo> result = new ArrayList<>();
        if (traceIds != null) {
            for (Object tidObj : traceIds) {
                String tid = (String) tidObj;
                TraceItemVo vo = get(tid);
                if (vo != null) {
                    if (vo.getIndex() <= lastKnownIndex) {
                        break; // Already have this and older ones
                    }
                    if (appIds == null || appIds.contains(vo.getAppId())) {
                        result.add(vo);
                    }
                } else {
                    // Item expired in String but still in List - remove it from list to cleanup
                    redisTemplate.opsForList().remove(TRACE_ITEMS_LIST_KEY, 0, tid);
                    break;
                }

                if (result.size() >= maxSize) {
                    break;
                }
            }
        }
        return result;
    }

    public List<TraceItemVo> getRangeBefore(int lastKnownIndex, int maxSize, List<String> appIds) {
        // Fetch older items whose index < lastKnownIndex
        List<Object> traceIds = redisTemplate.opsForList().range(TRACE_ITEMS_LIST_KEY, 0, -1);
        List<TraceItemVo> result = new ArrayList<>();
        if (traceIds != null) {
            for (Object tidObj : traceIds) {
                String tid = (String) tidObj;
                TraceItemVo vo = get(tid);
                if (vo != null) {
                    if (vo.getIndex() < lastKnownIndex) {
                        if (appIds == null || appIds.contains(vo.getAppId())) {
                            result.add(vo);
                        }
                    }
                } else {
                    // Item expired in String but still in List - remove it from list to cleanup
                    redisTemplate.opsForList().remove(TRACE_ITEMS_LIST_KEY, 0, tid);
                    break;
                }

                if (result.size() >= maxSize) {
                    break;
                }
            }
        }
        return result;
    }

    public int getCurrentIndex() {
        Object counter = redisTemplate.opsForValue().get(TRACE_ITEMS_COUNTER_KEY);
        if (counter instanceof Number) {
            return ((Number) counter).intValue();
        }
        return 0;
    }

    public int getSize() {
        return getCurrentIndex();
    }

    public ArrayList<TraceItemVo> getRangeByTime(Integer upToTime, CacheFilter filter) {
        ArrayList<TraceItemVo> list = new ArrayList<>();
        int queryUpToTime = upToTime == null || upToTime <= 0 ? 180 : upToTime;
        // Convert to long to avoid integer overflow
        long toTime = System.currentTimeMillis() - (queryUpToTime * 1000L);

        List<Object> traceIds = redisTemplate.opsForList().range(TRACE_ITEMS_LIST_KEY, 0, -1);
        if (traceIds != null) {
            for (Object tidObj : traceIds) {
                String tid = (String) tidObj;
                TraceItemVo vo = get(tid);
                if (vo != null) {
                    if (vo.getCacheTime() > toTime) {
                        if (filter.doFilter(vo)) {
                            list.add(vo);
                        }
                    } else {
                        // Once we see data older than toTime, we can stop because it's LIFO (leftPush used in add)
                        break;
                    }
                } else {
                    // Item expired in String but still in List - remove it from list to cleanup
                    redisTemplate.opsForList().remove(TRACE_ITEMS_LIST_KEY, 0, tid);
                    break;
                }
            }
        }
        return list;
    }

    public interface CacheFilter {
        boolean doFilter(TraceItemVo itemVo);
    }
}

