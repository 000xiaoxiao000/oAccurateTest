package com.oAT.agent.jacoco.data;

import com.oAT.agent.common.Decompiler.ILanguageNames;
import com.oAT.agent.common.Decompiler.JavaNames;
import com.oAT.agent.common.JsonUtil;
import com.oAT.agent.jacoco.instr.ClassInfo;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 聚合静态类/方法信息
 * @see CompactDataOutput
 */
public class CompactDataInput {

    // ================== 静态聚合结构 ==================
    /**
     * 方法静态结构
     */
    public static class MethodStaticInfo {
        public final String methodName;          // 转换后的方法名（含参数列表）
        public final String methodDesc;          // JVM 描述符/签名
        public final Set<Integer> methodLineNumberMap;   // 行号集合（过滤负数）
        public final Map<Integer, Set<Integer>> branchLineAndConditionNumberMap; // 分支行 -> 条件个数集合
        public final Set<Integer> branchLineNumberSet;   // 该方法所有分支行号集合（来自 totalBranchMap）
        public final int totalBranchCount;               // 分支总数（= branchLineNumberSet.size()）
        public final int cyclomaticComplexityMap;   // 圈复杂度
        public final boolean recursiveMap;          // 是否递归
        public final boolean asyncMethodMap;        // 是否异步
        public final String methodUri;              // 接口URI

        public MethodStaticInfo(String methodName,
                                String methodDesc,
                                Set<Integer> lineNumbers,
                                Map<Integer, Set<Integer>> branchLineAndConditionNumberMap,
                                Set<Integer> branchLineNumberSet,
                                int cyclomaticComplexity,
                                boolean recursive,
                                boolean async,
                                String methodUri) {
            this.methodName = methodName;
            this.methodDesc = methodDesc;
            this.methodLineNumberMap = (lineNumbers == null ? Collections.emptySet() : Collections.unmodifiableSet(filterLines(lineNumbers)));
            this.branchLineNumberSet = branchLineNumberSet == null ? Collections.emptySet() : Collections.unmodifiableSet(new LinkedHashSet<>(branchLineNumberSet));
            this.branchLineAndConditionNumberMap = branchLineAndConditionNumberMap == null ? Collections.emptyMap() : Collections.unmodifiableMap(new HashMap<>(branchLineAndConditionNumberMap));
            this.totalBranchCount = this.branchLineNumberSet.size();
            this.cyclomaticComplexityMap = cyclomaticComplexity;
            this.recursiveMap = recursive;
            this.asyncMethodMap = async;
            this.methodUri = methodUri;
        }
        private static Set<Integer> filterLines(Set<Integer> src) {
            Set<Integer> r = new HashSet<>();
            for (Integer i : src) { if (i != null && i >= 0) r.add(i); }
            return r;
        }
        public Map<String, Object> toMap() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("methodName", methodName);
            m.put("methodDesc", methodDesc);
            m.put("methodLineNumberMap", methodLineNumberMap);
            m.put("branchLineNumberSet", branchLineNumberSet);
            Map<String, Set<Integer>> branchMapStr = new LinkedHashMap<>();
            if (branchLineAndConditionNumberMap != null) {
                for (Map.Entry<Integer, Set<Integer>> entry : branchLineAndConditionNumberMap.entrySet()) {
                    branchMapStr.put(String.valueOf(entry.getKey()), entry.getValue());
                }
            }
            m.put("branchLineAndConditionNumberMap", branchMapStr);
            m.put("totalBranchCount", totalBranchCount);
            m.put("cyclomaticComplexityMap", cyclomaticComplexityMap);
            m.put("recursiveMap", recursiveMap);
            m.put("asyncMethodMap", asyncMethodMap);
            m.put("methodUri", methodUri);
            return m;
        }
        public MethodStaticInfo merge(MethodStaticInfo other) {
            if (other == null) return this;
            Set<Integer> mergedLines = new HashSet<>(methodLineNumberMap);
            mergedLines.addAll(other.methodLineNumberMap);
            Set<Integer> mergedBranchLines = new LinkedHashSet<>(branchLineNumberSet);
            mergedBranchLines.addAll(other.branchLineNumberSet);
            Map<Integer, Set<Integer>> mergedBranch = new LinkedHashMap<>(branchLineAndConditionNumberMap);
            other.branchLineAndConditionNumberMap.forEach((k,v)-> mergedBranch.merge(k, v, (a,b)-> { Set<Integer> c=new HashSet<>(a); c.addAll(b); return c; }));
            int complexity = Math.max(cyclomaticComplexityMap, other.cyclomaticComplexityMap); // 保留较大复杂度
            boolean recursive = recursiveMap || other.recursiveMap;
            boolean async = asyncMethodMap || other.asyncMethodMap;
            String uri = (methodUri != null && !methodUri.isEmpty()) ? methodUri : other.methodUri;
            return new MethodStaticInfo(methodName, methodDesc, mergedLines, mergedBranch, mergedBranchLines, complexity, recursive, async, uri);
        }
    }

    /**
     * 类静态结构
     */
    public static class ClassStaticInfo {
        public final long classId;
        public final String className; // 转换后的全限定类名
        // 方法 Map：按需求 key 使用递增序号字符串 -> MethodStaticInfo
        private final Map<String, MethodStaticInfo> methods = new LinkedHashMap<>();
        private final AtomicInteger methodIndex = new AtomicInteger();
        public ClassStaticInfo(long classId, String className) {
            this.classId = classId;
            this.className = className;
        }
        public synchronized void addOrMergeMethod(MethodStaticInfo info) {
            // 尝试按 methodName+methodDesc 查找已存在方法进行合并
            for (Map.Entry<String, MethodStaticInfo> e : methods.entrySet()) {
                MethodStaticInfo exist = e.getValue();
                if (exist.methodName.equals(info.methodName) && exist.methodDesc.equals(info.methodDesc)) {
                    methods.put(e.getKey(), exist.merge(info));
                    return;
                }
            }
            String key = String.valueOf(methodIndex.getAndIncrement());
            methods.put(key, info);
        }
        public Map<String, MethodStaticInfo> getMethods() { return methods; }
        public Map<String, Object> toMap() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("classId", String.valueOf(classId));
            m.put("className", className);
            Map<String, Object> methodMaps = new LinkedHashMap<>();
            for (Map.Entry<String, MethodStaticInfo> e : methods.entrySet()) {
                methodMaps.put(e.getKey(), e.getValue().toMap());
            }
            m.put("methodMaps", methodMaps); // 根据需求使用中文 key
            return m;
        }
    }

    /**
     * 全量类静态信息缓存：className(转换后) -> ClassStaticInfo
     */
    private static final Map<String, ClassStaticInfo> CLASS_STATIC_INFO = new ConcurrentHashMap<>();

    /**
     * 聚合一次 ClassInfo 收集到的静态信息
     * @param info ClassInfo
     */
    public static void collectClassStaticInfo(ClassInfo info) {
        if (info == null) return;
        ILanguageNames javaNames = new JavaNames();
        String originClassName = info.getClassName(); // VM 格式
        String finalClassName = javaNames.getQualifiedClassName(originClassName);

        ClassStaticInfo cInfo = CLASS_STATIC_INFO.computeIfAbsent(finalClassName, k -> new ClassStaticInfo(info.getClassId(), finalClassName));

        Map<String, Set<Integer>> methodLineNumberMap = info.getMethodLineNumberMap();
        Map<String, Integer> branchMap = info.getTotalBranchMap(); // 新增：类中所有分支行号映射
        Map<Integer, Set<Integer>> branchCondMap = info.getBranchLineAndConditionNumberMap();
        Map<String, Integer> cycloMap = info.getCyclomaticComplexityMap();
        Map<String, Boolean> recursiveMap = info.getRecursiveMap();
        Map<String, Boolean> asyncMap = info.getAsyncMethodMap();
        Map<String, String> uriMap = info.getMethodUriMap();

        for (Map.Entry<String, Set<Integer>> entry : methodLineNumberMap.entrySet()) {
            String fullKey = entry.getKey(); // class + method + desc
            Set<Integer> lineNums = entry.getValue();
            String methodName; // 原始方法名
            String methodDesc; // JVM 描述符或签名
            try {
                int firstSpace = fullKey.indexOf(' ');
                int secondSpace = fullKey.indexOf(' ', firstSpace + 1);
                if (firstSpace < 0 || secondSpace < 0) {
                    continue;
                }
                methodName = fullKey.substring(firstSpace + 1, secondSpace);
                methodDesc = fullKey.substring(secondSpace + 1);
            } catch (Throwable t) {
                continue;
            }
            String shortMethodKey = methodName + " " + methodDesc; // 用于 cyclomaticComplexityMap 和 branchMap 索引

            int cyclo = cycloMap.getOrDefault(shortMethodKey, 1);
            boolean recursive = recursiveMap.getOrDefault(fullKey, false);
            boolean async = asyncMap.getOrDefault(fullKey, false);
            String uri = uriMap.getOrDefault(fullKey, "");

            // 使用 JavaNames 生成展示方法名（含参数列表）
            String displayMethodName = javaNames.getMethodName(originClassName, methodName, methodDesc, null);

            Map<Integer, Set<Integer>> methodBranchCond = new LinkedHashMap<>();
            for (Map.Entry<Integer, Set<Integer>> bEntry : branchCondMap.entrySet()) {
                if (lineNums.contains(bEntry.getKey())) {
                    methodBranchCond.put(bEntry.getKey(), bEntry.getValue());
                }
            }

            // 统计该方法的所有分支行号集合（来源于 totalBranchMap）
            Set<Integer> methodBranchLines = new LinkedHashSet<>();
            if (branchMap != null && !branchMap.isEmpty()) {
                for (Map.Entry<String, Integer> bEntry : branchMap.entrySet()) {
                    // branchMap 的 key 结构：methodName + " " + methodDesc + " " + branchLineNumber
                    String key = bEntry.getKey();
                    int lastSpace = key.lastIndexOf(' ');
                    if (lastSpace > 0) {
                        String methodNameDescPart = key.substring(0, lastSpace); // methodName + desc
                        if (shortMethodKey.equals(methodNameDescPart)) {
                            Integer branchLine = bEntry.getValue();
                            if (branchLine != null && branchLine >= 0) {
                                methodBranchLines.add(branchLine);
                            }
                        }
                    }
                }
            }

            MethodStaticInfo mInfo = new MethodStaticInfo(displayMethodName, methodDesc, lineNums, methodBranchCond, methodBranchLines, cyclo, recursive, async, uri);
            cInfo.addOrMergeMethod(mInfo);
        }
    }

    /**
     * 获取所有聚合的类静态信息（只读）
     */
    public static Map<String, ClassStaticInfo> getAllClassStaticInfo() {
        return Collections.unmodifiableMap(CLASS_STATIC_INFO);
    }

    /**
     * 导出为简单 JSON 字符串
     */
    public static String exportAsJson() {
        Map<String, Object> root = new LinkedHashMap<>();
        for (Map.Entry<String, ClassStaticInfo> ce : CLASS_STATIC_INFO.entrySet()) {
            root.put(ce.getKey(), ce.getValue().toMap());
        }
        return JsonUtil.toJson(root);
    }
}
