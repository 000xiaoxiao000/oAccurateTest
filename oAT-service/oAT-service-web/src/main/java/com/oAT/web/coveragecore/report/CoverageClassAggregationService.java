package com.oAT.web.coveragecore.report;

import com.oAT.web.common.CoverageMethodKeyUtil;
import com.oAT.web.common.Job;
import com.oAT.web.esDao.entity.ClassCoverageIndex;
import com.oAT.web.esDao.entity.ClassCoverageIndex.MethodCoverageDetail;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import static com.oAT.web.coveragecore.diff.BranchTargetProbeMaps.appendBranchTargetProbeMap;
import static com.oAT.web.coveragecore.diff.BranchTargetProbeMaps.calculateBranchRate;
import static com.oAT.web.coveragecore.diff.BranchTargetProbeMaps.copyBranchTargetProbeMap;
import static com.oAT.web.coveragecore.diff.BranchTargetProbeMaps.countBranchTargets;

@Service
public class CoverageClassAggregationService {

    public Map<String, ClassCoverageIndex> loadLatestCodeCoverageSkeleton(List<ClassCoverageIndex> latestClasses) {
        Map<String, ClassCoverageIndex> coverageMap = new LinkedHashMap<>();
        if (latestClasses == null) {
            return coverageMap;
        }
        for (ClassCoverageIndex latestClass : latestClasses) {
            if (latestClass == null || !StringUtils.hasText(latestClass.getClassName())) {
                continue;
            }
            ClassCoverageIndex skeleton = cloneClassCoverage(latestClass);
            resetCoverageCounters(skeleton);
            coverageMap.put(skeleton.getClassName(), skeleton);
        }
        return coverageMap;
    }

    public Map<String, ClassCoverageIndex> toCoverageMap(List<ClassCoverageIndex> classCoverages) {
        Map<String, ClassCoverageIndex> coverageMap = new LinkedHashMap<>();
        if (classCoverages == null) {
            return coverageMap;
        }
        for (ClassCoverageIndex classCoverage : classCoverages) {
            if (classCoverage != null && StringUtils.hasText(classCoverage.getClassName())) {
                coverageMap.put(classCoverage.getClassName(), classCoverage);
            }
        }
        return coverageMap;
    }

    public void mergeCommitCoverageIntoLatestSkeleton(Map<String, ClassCoverageIndex> targetMap,
                                                      Map<String, ClassCoverageIndex> commitCoverageMap) {
        for (Map.Entry<String, ClassCoverageIndex> entry : commitCoverageMap.entrySet()) {
            ClassCoverageIndex targetClass = targetMap.get(entry.getKey());
            if (targetClass == null || targetClass.getMethods() == null) {
                continue;
            }
            ClassCoverageIndex sourceClass = entry.getValue();
            if (sourceClass == null || sourceClass.getMethods() == null) {
                continue;
            }
            Map<String, MethodCoverageDetail> targetMethods = targetClass.getMethods().stream()
                    .collect(Collectors.toMap(method -> buildMethodKey(method.getMethodName(), method.getMethodDesc()), method -> method, (a, b) -> a, LinkedHashMap::new));
            for (MethodCoverageDetail sourceMethod : sourceClass.getMethods()) {
                MethodCoverageDetail targetMethod = targetMethods.get(buildMethodKey(sourceMethod.getMethodName(), sourceMethod.getMethodDesc()));
                if (targetMethod == null) {
                    continue;
                }
                mergeMethodCoverage(targetMethod, sourceMethod);
            }
        }
    }

    public void recalculateClassCoverageCounters(Collection<ClassCoverageIndex> classCoverages) {
        if (classCoverages == null) {
            return;
        }
        for (ClassCoverageIndex classCoverage : classCoverages) {
            int coveredMethods = 0;
            int coveredLines = 0;
            int coveredBranches = 0;
            int coveredBranchTargets = 0;
            if (classCoverage.getMethods() != null) {
                for (MethodCoverageDetail method : classCoverage.getMethods()) {
                    boolean methodCovered = method.getCoveredLines() > 0 || method.getCoveredBranchTargets() > 0 || method.isCovered();
                    method.setCovered(methodCovered);
                    if (methodCovered) {
                        coveredMethods++;
                    }
                    coveredLines += method.getCoveredLines();
                    coveredBranches += method.getCoveredBranches();
                    coveredBranchTargets += method.getCoveredBranchTargets();
                }
            }
            classCoverage.setCoveredMethods(Math.min(classCoverage.getTotalMethods(), coveredMethods));
            classCoverage.setCoveredLines(Math.min(classCoverage.getTotalLines(), coveredLines));
            classCoverage.setCoveredBranches(Math.min(classCoverage.getTotalBranches(), coveredBranches));
            classCoverage.setCoveredBranchTargets(Math.min(classCoverage.getTotalBranchTargets(), coveredBranchTargets));
        }
    }

    public Map<String, ClassCoverageIndex> deepCloneCoverageMap(Map<String, ClassCoverageIndex> source) {
        if (source == null) {
            return new HashMap<>();
        }
        Map<String, ClassCoverageIndex> clone = new HashMap<>();
        for (Map.Entry<String, ClassCoverageIndex> entry : source.entrySet()) {
            ClassCoverageIndex original = entry.getValue();
            ClassCoverageIndex copy = new ClassCoverageIndex();
            copy.setId(original.getId());
            copy.setReportId(original.getReportId());
            copy.setAppId(original.getAppId());
            copy.setClassName(original.getClassName());
            copy.setTotalMethods(original.getTotalMethods());
            copy.setCoveredMethods(original.getCoveredMethods());
            copy.setTotalLines(original.getTotalLines());
            copy.setCoveredLines(original.getCoveredLines());
            copy.setTotalBranches(original.getTotalBranches());
            copy.setCoveredBranches(original.getCoveredBranches());
            copy.setTotalBranchTargets(original.getTotalBranchTargets());
            copy.setCoveredBranchTargets(original.getCoveredBranchTargets());
            copy.setTotalComplexity(original.getTotalComplexity());
            copy.setLineRate(original.getLineRate());
            copy.setBranchRate(original.getBranchRate());
            copy.setMethodRate(original.getMethodRate());
            copy.setHasCodeChanges(original.getHasCodeChanges());
            if (original.getMethods() != null) {
                List<MethodCoverageDetail> methodsCopy = new ArrayList<>();
                for (MethodCoverageDetail m : original.getMethods()) {
                    MethodCoverageDetail mc = new MethodCoverageDetail();
                    mc.setMethodName(m.getMethodName());
                    mc.setMethodDesc(m.getMethodDesc());
                    mc.setTotalLines(m.getTotalLines());
                    mc.setCoveredLines(m.getCoveredLines());
                    mc.setTotalBranches(m.getTotalBranches());
                    mc.setCoveredBranches(m.getCoveredBranches());
                    mc.setTotalBranchTargets(m.getTotalBranchTargets());
                    mc.setCoveredBranchTargets(m.getCoveredBranchTargets());
                    mc.setBranchRate(m.getBranchRate());
                    mc.setComplexity(m.getComplexity());
                    mc.setCovered(m.isCovered());
                    mc.setHasCodeChanges(m.isHasCodeChanges());
                    mc.setTotalLineNumbers(m.getTotalLineNumbers() != null ? new ArrayList<>(m.getTotalLineNumbers()) : null);
                    mc.setCoveredLineNumbers(m.getCoveredLineNumbers() != null ? new ArrayList<>(m.getCoveredLineNumbers()) : new ArrayList<>());
                    mc.setCoveredBranchLines(m.getCoveredBranchLines() != null ? new ArrayList<>(m.getCoveredBranchLines()) : new ArrayList<>());
                    mc.setTotalBranchTargetProbeMap(copyBranchTargetProbeMap(m.getTotalBranchTargetProbeMap()));
                    mc.setCoveredBranchTargetProbeMap(copyBranchTargetProbeMap(m.getCoveredBranchTargetProbeMap()));
                    methodsCopy.add(mc);
                }
                copy.setMethods(methodsCopy);
            } else {
                copy.setMethods(new ArrayList<>());
            }
            clone.put(entry.getKey(), copy);
        }
        return clone;
    }

    public void markCrossCommitCoverageChanges(Map<String, ClassCoverageIndex> finalCoverageMap,
                                               Map<String, Map<String, ClassCoverageIndex>> perCommitSnapshots,
                                               Job<String> job) {
        int classesWithChanges = 0;
        int methodsWithChanges = 0;

        for (Map.Entry<String, ClassCoverageIndex> entry : finalCoverageMap.entrySet()) {
            String className = entry.getKey();
            ClassCoverageIndex finalClassCov = entry.getValue();
            if (finalClassCov.getMethods() == null) {
                continue;
            }

            boolean classHasChanges = false;
            for (MethodCoverageDetail method : finalClassCov.getMethods()) {
                String key = buildMethodKey(method.getMethodName(), method.getMethodDesc());
                Set<String> signatures = new LinkedHashSet<>();
                for (Map<String, ClassCoverageIndex> snapshot : perCommitSnapshots.values()) {
                    ClassCoverageIndex snapshotClass = snapshot.get(className);
                    MethodCoverageDetail snapshotMethod = findMethodByKey(snapshotClass, key);
                    signatures.add(buildCoverageSignature(snapshotMethod));
                }
                if (signatures.size() > 1) {
                    method.setHasCodeChanges(true);
                    classHasChanges = true;
                    methodsWithChanges++;
                }
            }

            if (classHasChanges) {
                finalClassCov.setHasCodeChanges(true);
                classesWithChanges++;
            }
        }

        if (job != null && (classesWithChanges > 0 || methodsWithChanges > 0)) {
            job.getLogger().info(String.format(
                    "跨 Commit 覆盖率差异标记完成：%d 个类、%d 个方法存在覆盖率变化",
                    classesWithChanges, methodsWithChanges
            ));
        }
    }

    private ClassCoverageIndex cloneClassCoverage(ClassCoverageIndex original) {
        return deepCloneCoverageMap(Collections.singletonMap(original.getClassName(), original)).get(original.getClassName());
    }

    private void mergeMethodCoverage(MethodCoverageDetail targetMethod, MethodCoverageDetail sourceMethod) {
        targetMethod.setCoveredLineNumbers(unionIntegerLists(targetMethod.getCoveredLineNumbers(), sourceMethod.getCoveredLineNumbers()));
        targetMethod.setCoveredLines(targetMethod.getCoveredLineNumbers() == null ? 0 : targetMethod.getCoveredLineNumbers().size());
        targetMethod.setCoveredBranchLines(unionIntegerLists(targetMethod.getCoveredBranchLines(), sourceMethod.getCoveredBranchLines()));
        targetMethod.setCoveredBranches(Math.min(targetMethod.getTotalBranches(), targetMethod.getCoveredBranchLines() == null ? 0 : targetMethod.getCoveredBranchLines().size()));
        targetMethod.setCoveredBranchTargetProbeMap(mergeCoveredBranchTargetProbeMaps(targetMethod.getCoveredBranchTargetProbeMap(), sourceMethod.getCoveredBranchTargetProbeMap()));
        targetMethod.setCoveredBranchTargets(Math.min(targetMethod.getTotalBranchTargets(), countBranchTargets(targetMethod.getCoveredBranchTargetProbeMap())));
        targetMethod.setCovered(targetMethod.getCoveredLines() > 0 || targetMethod.getCoveredBranchTargets() > 0);
        targetMethod.setBranchRate(calculateBranchRate(targetMethod.getCoveredBranchTargets(), targetMethod.getTotalBranchTargets()));
    }

    private List<Integer> unionIntegerLists(List<Integer> first, List<Integer> second) {
        LinkedHashSet<Integer> values = new LinkedHashSet<>();
        if (first != null) values.addAll(first);
        if (second != null) values.addAll(second);
        return new ArrayList<>(values);
    }

    private Map<String, List<Integer>> mergeCoveredBranchTargetProbeMaps(Map<String, List<Integer>> first,
                                                                         Map<String, List<Integer>> second) {
        Map<String, LinkedHashSet<Integer>> merged = new LinkedHashMap<>();
        appendBranchTargetProbeMap(merged, first);
        appendBranchTargetProbeMap(merged, second);
        Map<String, List<Integer>> result = new LinkedHashMap<>();
        for (Map.Entry<String, LinkedHashSet<Integer>> entry : merged.entrySet()) {
            result.put(entry.getKey(), new ArrayList<>(entry.getValue()));
        }
        return result;
    }

    private void resetCoverageCounters(ClassCoverageIndex classCoverage) {
        classCoverage.setCoveredMethods(0);
        classCoverage.setCoveredLines(0);
        classCoverage.setCoveredBranches(0);
        classCoverage.setCoveredBranchTargets(0);
        classCoverage.setHasCodeChanges(false);
        if (classCoverage.getMethods() == null) {
            return;
        }
        for (MethodCoverageDetail method : classCoverage.getMethods()) {
            method.setCovered(false);
            method.setCoveredLines(0);
            method.setCoveredLineNumbers(new ArrayList<>());
            method.setCoveredBranches(0);
            method.setCoveredBranchLines(new ArrayList<>());
            method.setCoveredBranchTargets(0);
            method.setCoveredBranchTargetProbeMap(new LinkedHashMap<>());
            method.setBranchRate(calculateBranchRate(0, method.getTotalBranchTargets()));
            method.setHasCodeChanges(false);
        }
    }

    private MethodCoverageDetail findMethodByKey(ClassCoverageIndex classCoverage, String methodKey) {
        if (classCoverage == null || classCoverage.getMethods() == null) {
            return null;
        }
        for (MethodCoverageDetail method : classCoverage.getMethods()) {
            if (Objects.equals(methodKey, buildMethodKey(method.getMethodName(), method.getMethodDesc()))) {
                return method;
            }
        }
        return null;
    }

    private String buildCoverageSignature(MethodCoverageDetail method) {
        if (method == null) {
            return "missing";
        }
        return method.getTotalLines() + ":" + sortedIntegerList(method.getTotalLineNumbers())
                + "|" + method.getCoveredLines() + ":" + sortedIntegerList(method.getCoveredLineNumbers())
                + "|" + method.getTotalBranchTargets() + ":" + normalizedBranchTargetProbeMap(method.getTotalBranchTargetProbeMap())
                + "|" + method.getCoveredBranchTargets() + ":" + normalizedBranchTargetProbeMap(method.getCoveredBranchTargetProbeMap());
    }

    private String sortedIntegerList(List<Integer> values) {
        if (values == null || values.isEmpty()) {
            return "[]";
        }
        List<Integer> sorted = new ArrayList<>(new LinkedHashSet<>(values));
        Collections.sort(sorted);
        return sorted.toString();
    }

    private String normalizedBranchTargetProbeMap(Map<String, List<Integer>> probeMap) {
        if (probeMap == null || probeMap.isEmpty()) {
            return "{}";
        }
        List<String> parts = new ArrayList<>();
        for (Map.Entry<String, List<Integer>> entry : probeMap.entrySet()) {
            parts.add(entry.getKey() + "=" + sortedIntegerList(entry.getValue()));
        }
        Collections.sort(parts);
        return parts.toString();
    }

    private String buildMethodKey(String methodName, String methodDesc) {
        return CoverageMethodKeyUtil.buildMethodKey(methodName, methodDesc);
    }
}
