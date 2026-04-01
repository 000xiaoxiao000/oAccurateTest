package com.oAT.agent.jacoco;

import java.util.concurrent.ConcurrentHashMap;

/**
 * 全局探针注册表：classId -> boolean[] 探针数组
 * <p>
 * 每个被插桩的类在首次执行时，通过 $jacocoInit() 方法将自身的探针数组注册到此注册表中。
 * 探针数组中每个 boolean 元素对应一个探针点（行/分支/条件），true 表示已执行。
 * </p>
 */
public final class CoverageData {

    /**
     * classId -> 探针数组
     * key: 类的唯一标识（CRC64）
     * value: boolean[]，索引为探针ID，值为true表示该探针已被执行
     */
    private static final ConcurrentHashMap<Long, boolean[]> PROBE_REGISTRY = new ConcurrentHashMap<>(1024);

    /**
     * 注册探针数组。由插桩类的 $jacocoInit() 方法调用。
     *
     * @param classId   类的唯一标识
     * @param probes    探针数组
     */
    public static void register(long classId, boolean[] probes) {
        PROBE_REGISTRY.put(classId, probes);
    }

    /**
     * 获取指定类的探针数组。
     *
     * @param classId 类的唯一标识
     * @return 探针数组的快照（浅拷贝），如果不存在返回空数组
     */
    public static boolean[] getProbes(long classId) {
        boolean[] probes = PROBE_REGISTRY.get(classId);
        if (probes == null) {
            return new boolean[0];
        }
        // 浅拷贝：保证快照一致性
        boolean[] snapshot = new boolean[probes.length];
        System.arraycopy(probes, 0, snapshot, 0, probes.length);
        return snapshot;
    }

    /**
     * 获取指定类的探针数组（直接引用，高性能但非线程安全快照）。
     * 仅在确定不会并发修改时使用。
     *
     * @param classId 类的唯一标识
     * @return 探针数组引用，如果不存在返回null
     */
    public static boolean[] getProbesDirect(long classId) {
        return PROBE_REGISTRY.get(classId);
    }

    /**
     * 获取已注册的类ID集合。
     *
     * @return 所有已注册的 classId
     */
    public static long[] getRegisteredClassIds() {
        return PROBE_REGISTRY.keySet().stream().mapToLong(Long::longValue).toArray();
    }

    /**
     * 获取已注册的类数量。
     */
    public static int getRegisteredClassCount() {
        return PROBE_REGISTRY.size();
    }

    /**
     * 清除指定类的探针数组（重置覆盖率）。
     *
     * @param classId 类的唯一标识
     */
    public static void reset(long classId) {
        boolean[] probes = PROBE_REGISTRY.get(classId);
        if (probes != null) {
            java.util.Arrays.fill(probes, false);
        }
    }

    /**
     * 清除所有探针数组（重置全部覆盖率）。
     */
    public static void resetAll() {
        for (boolean[] probes : PROBE_REGISTRY.values()) {
            java.util.Arrays.fill(probes, false);
        }
    }

    private CoverageData() {
    }
}
