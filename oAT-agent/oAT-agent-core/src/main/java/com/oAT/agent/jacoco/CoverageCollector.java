package com.oAT.agent.jacoco;

import com.oAT.agent.common.StackTraceFormatter;
import com.oAT.agent.common.logger.Log;
import com.oAT.agent.common.logger.LogFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 请求级覆盖数据聚合器。
 * <p>
 * 在请求开始时创建，请求结束时从 {@link CoverageData} 全局注册表中收集
 * 所有被触发的类的探针数据快照，并结合 {@link ClassProbeInfo} 元信息转换为结构化的覆盖率报告。
 * <p>
 * 替代旧的 StackSession 机制，不再在目标系统的每个方法中插入 begin/end 调用。
 * </p>
 */
public class CoverageCollector {

    private static final Log logger = LogFactory.getLog(CoverageCollector.class);

    /**
     * 当前线程绑定的 CoverageCollector
     */
    private static final ThreadLocal<CoverageCollector> CURRENT = new ThreadLocal<CoverageCollector>();

    /**
     * 快照开始时间（System.nanoTime）
     */
    private final long beginNanoTime;

    /**
     * 快照开始时间（System.currentTimeMillis）
     */
    private final long beginTimeMillis;

    /**
     * 请求结束时收集到的 classId -> 探针数组快照
     */
    private final Map<Long, boolean[]> probeSnapshots = new LinkedHashMap<Long, boolean[]>();

    /**
     * 在请求期间被触发的 classId 集合（用于增量收集）
     * 使用 ConcurrentHashMap 的 key set 特性来跟踪哪些类被新触发
     */
    private final Set<Long> touchedClassIds =
            Collections.newSetFromMap(new ConcurrentHashMap<Long, Boolean>());

    /**
     * 已知被触发的 classId（快照前已触发的），用于去重
     */
    private final Set<Long> previousTouchedClassIds = new HashSet<Long>();

    public CoverageCollector() {
        this.beginNanoTime = System.nanoTime();
        this.beginTimeMillis = System.currentTimeMillis();
    }

    /**
     * 开始收集。
     */
    public static CoverageCollector begin() {
        CoverageCollector collector = new CoverageCollector();
        CURRENT.set(collector);
        return collector;
    }

    /**
     * 结束收集，生成探针快照。
     */
    public static CoverageCollector end() {
        CoverageCollector collector = CURRENT.get();
        if (collector != null) {
            collector.collectSnapshots();
        }
        return collector;
    }

    /**
     * 获取当前线程的 CoverageCollector。
     */
    public static CoverageCollector getCurrent() {
        return CURRENT.get();
    }

    /**
     * 移除当前线程的 CoverageCollector。
     */
    public static void remove() {
        CURRENT.remove();
    }

    /**
     * 从全局 CoverageData 收集所有已注册类的探针快照。
     * 在请求结束时调用。
     */
    public void collectSnapshots() {
        try {
            // 收集所有已注册的 classId 的探针快照
            long[] classIds = CoverageData.getRegisteredClassIds();
            for (long classId : classIds) {
                boolean[] probes = CoverageData.getProbes(classId);
                if (probes != null && probes.length > 0) {
                    // 检查是否有任何探针被执行
                    boolean hasExecuted = false;
                    for (boolean p : probes) {
                        if (p) {
                            hasExecuted = true;
                            break;
                        }
                    }
                    if (hasExecuted) {
                        probeSnapshots.put(classId, probes);
                    }
                }
            }
        } catch (Throwable t) {
            logger.error("[Agent-EXCError]collectSnapshots 异常: " +
                    StackTraceFormatter.formatExceptionWithAgentMark(t));
        }
    }

    // ============ Getter methods ============

    public long getBeginNanoTime() {
        return beginNanoTime;
    }

    public long getBeginTimeMillis() {
        return beginTimeMillis;
    }

    public Map<Long, boolean[]> getProbeSnapshots() {
        return Collections.unmodifiableMap(probeSnapshots);
    }

    /**
     * 获取有覆盖率数据的 classId 列表。
     */
    public long[] getCoveredClassIds() {
        long[] result = new long[probeSnapshots.size()];
        int index = 0;
        for (Long classId : probeSnapshots.keySet()) {
            result[index++] = classId.longValue();
        }
        return result;
    }

    /**
     * 获取指定类的探针快照。
     */
    public boolean[] getProbeSnapshot(long classId) {
        return probeSnapshots.get(classId);
    }

    /**
     * 获取总执行时间（纳秒）。
     */
    public long getElapsedTime() {
        return System.nanoTime() - beginNanoTime;
    }
}
