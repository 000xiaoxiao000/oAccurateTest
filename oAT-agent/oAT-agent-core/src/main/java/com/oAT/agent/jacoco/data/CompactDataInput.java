package com.oAT.agent.jacoco.data;

import com.oAT.agent.common.Decompiler.ILanguageNames;
import com.oAT.agent.common.Decompiler.JavaNames;
import com.oAT.agent.common.JsonUtil;
import com.oAT.agent.jacoco.ClassProbeInfo;
import com.oAT.agent.jacoco.ClassProbeInfoRegistry;
import com.oAT.agent.jacoco.instr.ClassInfo;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 聚合静态类/方法信息
 * @see CompactDataOutput
 */
public class CompactDataInput {

    public static class MethodStaticInfo {
        private final String mergeKey;
        public final String methodName;
        public final String methodDesc;
        public final Set<Integer> methodLineNumberMap;
        public final Map<Integer, Set<Integer>> branchLineAndTargetProbeMap;
        public final Set<Integer> branchLineNumberSet;
        public final int totalBranchCount;
        public final int cyclomaticComplexityMap;
        public final boolean recursiveMap;
        public final boolean asyncMethodMap;

        public MethodStaticInfo(String mergeKey,
                                String methodName,
                                String methodDesc,
                                Set<Integer> lineNumbers,
                                Map<Integer, Set<Integer>> branchLineAndTargetProbeMap,
                                Set<Integer> branchLineNumberSet,
                                int cyclomaticComplexity,
                                boolean recursive,
                                boolean async) {
            this.mergeKey = mergeKey;
            this.methodName = methodName;
            this.methodDesc = methodDesc;
            this.methodLineNumberMap = (lineNumbers == null ? Collections.<Integer>emptySet() : Collections.unmodifiableSet(filterLines(lineNumbers)));
            this.branchLineNumberSet = branchLineNumberSet == null
                    ? Collections.<Integer>emptySet()
                    : Collections.unmodifiableSet(filterBranchLines(branchLineNumberSet));
            this.branchLineAndTargetProbeMap = branchLineAndTargetProbeMap == null
                    ? Collections.<Integer, Set<Integer>>emptyMap()
                    : Collections.unmodifiableMap(filterBranchLineTargetMap(branchLineAndTargetProbeMap));
            this.totalBranchCount = countBranchTargets(this.branchLineAndTargetProbeMap, this.branchLineNumberSet);
            this.cyclomaticComplexityMap = cyclomaticComplexity;
            this.recursiveMap = recursive;
            this.asyncMethodMap = async;
        }
        private static Set<Integer> filterLines(Set<Integer> src) {
            Set<Integer> r = new HashSet<Integer>();
            for (Integer i : src) { if (i != null && i >= 0) r.add(i); }
            return r;
        }
        private static Set<Integer> filterBranchLines(Set<Integer> src) {
            Set<Integer> r = new LinkedHashSet<Integer>();
            for (Integer i : src) {
                if (i != null && i > 0) {
                    r.add(i);
                }
            }
            return r;
        }
        private static Map<Integer, Set<Integer>> filterBranchLineTargetMap(Map<Integer, Set<Integer>> src) {
            Map<Integer, Set<Integer>> filtered = new LinkedHashMap<Integer, Set<Integer>>();
            for (Map.Entry<Integer, Set<Integer>> entry : src.entrySet()) {
                Integer branchLine = entry.getKey();
                if (branchLine != null && branchLine > 0) {
                    Set<Integer> targetProbeIds = new LinkedHashSet<Integer>();
                    if (entry.getValue() != null) {
                        for (Integer targetProbeId : entry.getValue()) {
                            if (targetProbeId != null && targetProbeId >= 0) {
                                targetProbeIds.add(targetProbeId);
                            }
                        }
                    }
                    filtered.put(branchLine, targetProbeIds);
                }
            }
            return filtered;
        }
        private static int countBranchTargets(Map<Integer, Set<Integer>> branchLineAndTargetProbeMap,
                                              Set<Integer> branchLineNumberSet) {
            int total = 0;
            if (branchLineAndTargetProbeMap != null) {
                for (Set<Integer> targetProbeIds : branchLineAndTargetProbeMap.values()) {
                    total += targetProbeIds == null ? 0 : targetProbeIds.size();
                }
            }
            if (total == 0 && branchLineNumberSet != null) {
                total = branchLineNumberSet.size();
            }
            return total;
        }
        public Map<String, Object> toMap() {
            Map<String, Object> m = new LinkedHashMap<String, Object>();
            m.put("methodName", methodName);
            m.put("methodDesc", methodDesc);
            m.put("methodLineNumberMap", methodLineNumberMap);
            m.put("branchLineNumberSet", branchLineNumberSet);
            Map<String, Set<Integer>> branchMapStr = new LinkedHashMap<String, Set<Integer>>();
            if (branchLineAndTargetProbeMap != null) {
                for (Map.Entry<Integer, Set<Integer>> entry : branchLineAndTargetProbeMap.entrySet()) {
                    branchMapStr.put(String.valueOf(entry.getKey()), entry.getValue());
                }
            }
            m.put("branchLineAndTargetProbeMap", branchMapStr);
            m.put("totalBranchCount", totalBranchCount);
            m.put("cyclomaticComplexityMap", cyclomaticComplexityMap);
            m.put("recursiveMap", recursiveMap);
            m.put("asyncMethodMap", asyncMethodMap);
            return m;
        }
        public MethodStaticInfo merge(MethodStaticInfo other) {
            if (other == null) return this;
            Set<Integer> mergedLines = new HashSet<Integer>(methodLineNumberMap);
            mergedLines.addAll(other.methodLineNumberMap);
            Set<Integer> mergedBranchLines = new LinkedHashSet<Integer>(branchLineNumberSet);
            mergedBranchLines.addAll(other.branchLineNumberSet);
            Map<Integer, Set<Integer>> mergedBranch = new LinkedHashMap<Integer, Set<Integer>>(branchLineAndTargetProbeMap);
            for (Map.Entry<Integer, Set<Integer>> entry : other.branchLineAndTargetProbeMap.entrySet()) {
                Integer branchLine = entry.getKey();
                Set<Integer> existing = mergedBranch.get(branchLine);
                if (existing == null) {
                    mergedBranch.put(branchLine, new LinkedHashSet<Integer>(entry.getValue()));
                } else {
                    Set<Integer> mergedTargets = new LinkedHashSet<Integer>(existing);
                    mergedTargets.addAll(entry.getValue());
                    mergedBranch.put(branchLine, mergedTargets);
                }
            }
            int complexity = Math.max(cyclomaticComplexityMap, other.cyclomaticComplexityMap);
            boolean recursive = recursiveMap || other.recursiveMap;
            boolean async = asyncMethodMap || other.asyncMethodMap;
            return new MethodStaticInfo(mergeKey, methodName, methodDesc, mergedLines, mergedBranch, mergedBranchLines, complexity, recursive, async);
        }
    }

    public static class ClassStaticInfo {
        public final long classId;
        public final String className;
        private final Map<String, MethodStaticInfo> methods = new LinkedHashMap<String, MethodStaticInfo>();
        private final AtomicInteger methodIndex = new AtomicInteger();
        public ClassStaticInfo(long classId, String className) {
            this.classId = classId;
            this.className = className;
        }
        public synchronized void addOrMergeMethod(MethodStaticInfo info) {
            for (Map.Entry<String, MethodStaticInfo> e : methods.entrySet()) {
                MethodStaticInfo exist = e.getValue();
                if (exist.mergeKey.equals(info.mergeKey)) {
                    methods.put(e.getKey(), exist.merge(info));
                    return;
                }
            }
            String key = String.valueOf(methodIndex.getAndIncrement());
            methods.put(key, info);
        }
        public Map<String, MethodStaticInfo> getMethods() { return methods; }
        public Map<String, Object> toMap() {
            Map<String, Object> m = new LinkedHashMap<String, Object>();
            m.put("classId", String.valueOf(classId));
            m.put("className", className);
            Map<String, Object> methodMaps = new LinkedHashMap<String, Object>();
            for (Map.Entry<String, MethodStaticInfo> e : methods.entrySet()) {
                methodMaps.put(e.getKey(), e.getValue().toMap());
            }
            m.put("methodMaps", methodMaps);
            return m;
        }
    }

    private static final Map<String, ClassStaticInfo> CLASS_STATIC_INFO = new ConcurrentHashMap<String, ClassStaticInfo>();

    public static void collectClassStaticInfo(ClassInfo info) {
        if (info == null) return;
        ILanguageNames javaNames = new JavaNames();
        String originClassName = info.getClassName();
        String finalClassName = CoverageNamingSupport.toOwnerQualifiedClassName(originClassName);

        Map<String, Set<Integer>> methodLineNumberMap = info.getMethodLineNumberMap();
        Map<String, Integer> branchMap = info.getTotalBranchMap();
        Map<Integer, Set<Integer>> branchTargetMap = info.getBranchLineAndTargetProbeMap();
        Map<String, Integer> cycloMap = info.getCyclomaticComplexityMap();
        Map<String, Boolean> recursiveMap = info.getRecursiveMap();
        Map<String, Boolean> asyncMap = info.getAsyncMethodMap();

        ClassStaticInfo classStaticInfo = CLASS_STATIC_INFO.get(finalClassName);
        if (classStaticInfo == null) {
            ClassStaticInfo newInfo = new ClassStaticInfo(info.getClassId(), finalClassName);
            ClassStaticInfo existing = ((ConcurrentHashMap<String, ClassStaticInfo>) CLASS_STATIC_INFO)
                    .putIfAbsent(finalClassName, newInfo);
            classStaticInfo = existing == null ? newInfo : existing;
        }

        for (Map.Entry<String, Set<Integer>> entry : methodLineNumberMap.entrySet()) {
            String fullKey = entry.getKey();
            Set<Integer> lineNums = entry.getValue();
            String methodName;
            String methodDesc;
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
            String shortMethodKey = methodName + " " + methodDesc;

            Integer cycloValue = cycloMap.get(shortMethodKey);
            Boolean recursiveValue = recursiveMap.get(fullKey);
            Boolean asyncValue = asyncMap.get(fullKey);
            int cyclo = cycloValue == null ? 1 : cycloValue.intValue();
            boolean recursive = recursiveValue != null && recursiveValue.booleanValue();
            boolean async = asyncValue != null && asyncValue.booleanValue();

            String displayMethodName = javaNames.getMethodName(originClassName, methodName, methodDesc, null);
            if (CoverageNamingSupport.shouldIgnoreMethod(methodName, displayMethodName)) {
                continue;
            }

            Map<Integer, Set<Integer>> methodBranchTargets = buildMethodBranchTargets(
                    shortMethodKey, lineNums, branchMap, branchTargetMap, info);

            Set<Integer> methodBranchLines = new LinkedHashSet<Integer>(methodBranchTargets.keySet());

            String mergeKey = CoverageNamingSupport.buildMethodMergeKey(originClassName, methodName, methodDesc);
            MethodStaticInfo mInfo = new MethodStaticInfo(mergeKey, displayMethodName, methodDesc, lineNums,
                    methodBranchTargets, methodBranchLines, cyclo, recursive, async);
            classStaticInfo.addOrMergeMethod(mInfo);
        }
    }

    private static Map<Integer, Set<Integer>> buildMethodBranchTargets(String shortMethodKey,
                                                                        Set<Integer> lineNums,
                                                                        Map<String, Integer> branchMap,
                                                                        Map<Integer, Set<Integer>> branchTargetMap,
                                                                        ClassInfo info) {
        Map<Integer, Set<Integer>> methodBranchTargets = new LinkedHashMap();
        Map<String, int[]> branchTargetSiteMap = info == null ? null : info.getTotalBranchTargetMap();
        if (branchTargetSiteMap != null && !branchTargetSiteMap.isEmpty()) {
            for (Map.Entry<String, int[]> entry : branchTargetSiteMap.entrySet()) {
                BranchSiteKey siteKey = parseBranchSiteKey(entry.getKey());
                if (siteKey == null || !shortMethodKey.equals(siteKey.methodNameDesc)) {
                    continue;
                }
                if (siteKey.line <= 0 || !lineNums.contains(Integer.valueOf(siteKey.line))) {
                    continue;
                }
                Set<Integer> targets = methodBranchTargets.get(Integer.valueOf(siteKey.line));
                if (targets == null) {
                    targets = new LinkedHashSet<Integer>();
                    methodBranchTargets.put(Integer.valueOf(siteKey.line), targets);
                }
                int[] targetIds = entry.getValue();
                if (targetIds != null) {
                    for (int i = 0; i < targetIds.length; i++) {
                        if (targetIds[i] > 0) {
                            targets.add(Integer.valueOf(targetIds[i]));
                        }
                    }
                }
            }
            if (!methodBranchTargets.isEmpty()) {
                return methodBranchTargets;
            }
        }
        if (branchMap != null && !branchMap.isEmpty()) {
            for (Map.Entry<String, Integer> bEntry : branchMap.entrySet()) {
                String key = bEntry.getKey();
                int lastSpace = key.lastIndexOf(' ');
                if (lastSpace <= 0) {
                    continue;
                }
                String methodNameDescPart = key.substring(0, lastSpace);
                if (!shortMethodKey.equals(methodNameDescPart)) {
                    continue;
                }
                Integer branchLine = bEntry.getValue();
                if (branchLine == null || branchLine <= 0 || !lineNums.contains(branchLine)) {
                    continue;
                }
                int inferredTargetCount = inferBranchTargetCount(shortMethodKey, branchLine, info);
                if (inferredTargetCount > 0) {
                    LinkedHashSet<Integer> inferredTargets = new LinkedHashSet();
                    for (int i = 1; i <= inferredTargetCount; i++) {
                        inferredTargets.add(i);
                    }
                    methodBranchTargets.put(branchLine, inferredTargets);
                    continue;
                }
                Set<Integer> targets = branchTargetMap.get(branchLine);
                if (targets != null && !targets.isEmpty()) {
                    methodBranchTargets.put(branchLine, new LinkedHashSet(targets));
                }
            }
        }
        return methodBranchTargets;
    }

    private static BranchSiteKey parseBranchSiteKey(String key) {
        if (key == null) {
            return null;
        }
        int lastSpace = key.lastIndexOf(' ');
        if (lastSpace <= 0) {
            return null;
        }
        int secondLastSpace = key.lastIndexOf(' ', lastSpace - 1);
        if (secondLastSpace <= 0) {
            return null;
        }
        try {
            int line = Integer.parseInt(key.substring(secondLastSpace + 1, lastSpace));
            String methodNameDesc = key.substring(0, secondLastSpace);
            return new BranchSiteKey(methodNameDesc, line);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static final class BranchSiteKey {
        private final String methodNameDesc;
        private final int line;

        private BranchSiteKey(String methodNameDesc, int line) {
            this.methodNameDesc = methodNameDesc;
            this.line = line;
        }
    }

    private static int inferBranchTargetCount(String shortMethodKey, int branchLine, ClassInfo info) {
        if (info == null) {
            return 0;
        }
        int maxTargetId = 0;
        for (ClassProbeInfo probeInfo : ClassProbeInfoRegistry.all()) {
            if (probeInfo == null || probeInfo.getClassId() != info.getClassId()) {
                continue;
            }
            for (Map.Entry<Integer, String> methodEntry : probeInfo.getMethodEntryToName().entrySet()) {
                if (!shortMethodKey.equals(methodEntry.getValue())) {
                    continue;
                }
                Integer methodEntryIdx = methodEntry.getKey();
                int[] probeMethodEntryIndex = probeInfo.getProbeMethodEntryIndex();
                for (int probeIdx = 0; probeIdx < probeMethodEntryIndex.length; probeIdx++) {
                    if (probeMethodEntryIndex[probeIdx] != methodEntryIdx) {
                        continue;
                    }
                    if (probeIdx >= probeInfo.getProbeIsBranch().length || !probeInfo.getProbeIsBranch()[probeIdx]) {
                        continue;
                    }
                    Integer probeBranchLine = probeInfo.getBranchProbeToLine().get(probeIdx);
                    if (probeBranchLine == null || probeBranchLine != branchLine) {
                        continue;
                    }
                    Integer branchTargetId = probeInfo.getBranchProbeToPathId().get(probeIdx);
                    if (branchTargetId != null && branchTargetId > maxTargetId) {
                        maxTargetId = branchTargetId;
                    }
                }
            }
        }
        return maxTargetId;
    }

    public static Map<String, ClassStaticInfo> getAllClassStaticInfo() {
        return Collections.unmodifiableMap(CLASS_STATIC_INFO);
    }

    public static String exportAsJson() {
        Map<String, Object> root = new LinkedHashMap();
        for (Map.Entry<String, ClassStaticInfo> ce : CLASS_STATIC_INFO.entrySet()) {
            root.put(ce.getKey(), ce.getValue().toMap());
        }
        return JsonUtil.toJson(root);
    }
}
