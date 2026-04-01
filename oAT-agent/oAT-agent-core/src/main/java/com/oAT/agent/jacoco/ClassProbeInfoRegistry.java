package com.oAT.agent.jacoco;

import java.util.concurrent.ConcurrentHashMap;

/**
 * 类探针元信息全局注册表：classId -> ClassProbeInfo
 * <p>
 * 在类加载时由 ClassInstrumenter 注册，包含探针索引到行号/分支/方法的映射关系，
 * 用于在请求结束时将 boolean[] 探针数据转换为有意义的覆盖率报告。
 * </p>
 */
public final class ClassProbeInfoRegistry {

    private static final ConcurrentHashMap<Long, ClassProbeInfo> REGISTRY = new ConcurrentHashMap<>(1024);

    /**
     * 注册类的探针元信息。
     */
    public static void register(long classId, ClassProbeInfo info) {
        REGISTRY.put(classId, info);
    }

    /**
     * 获取类的探针元信息。
     */
    public static ClassProbeInfo get(long classId) {
        return REGISTRY.get(classId);
    }

    /**
     * 获取所有已注册的 classId。
     */
    public static long[] getRegisteredClassIds() {
        return REGISTRY.keySet().stream().mapToLong(Long::longValue).toArray();
    }

    /**
     * 获取已注册的类数量。
     */
    public static int size() {
        return REGISTRY.size();
    }

    private ClassProbeInfoRegistry() {
    }
}
