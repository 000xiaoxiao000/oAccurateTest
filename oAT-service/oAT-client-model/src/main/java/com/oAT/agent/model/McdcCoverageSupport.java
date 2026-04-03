package com.oAT.agent.model;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * MC/DC 覆盖数据构建与合并工具。
 */
public final class McdcCoverageSupport {

    private McdcCoverageSupport() {
    }

    public static Map<String, List<List<String>>> buildAllCoverageFromConditionSets(
            Map<Integer, ? extends Collection<Integer>> branchLineToConditions) {
        if (branchLineToConditions == null || branchLineToConditions.isEmpty()) {
            return null;
        }
        Map<String, List<List<String>>> result = new LinkedHashMap<>();
        for (Map.Entry<Integer, ? extends Collection<Integer>> entry : branchLineToConditions.entrySet()) {
            if (!isValidBranchLine(entry.getKey())) {
                continue;
            }
            int conditionCount = entry.getValue() == null ? 0 : entry.getValue().size();
            putIfPresent(result, String.valueOf(entry.getKey()), buildAllCoverageForConditionCount(conditionCount));
        }
        return result.isEmpty() ? null : result;
    }

    public static Map<String, List<List<String>>> buildAllCoverageFromConditionCounts(
            Map<Integer, Integer> branchLineToConditionCounts) {
        if (branchLineToConditionCounts == null || branchLineToConditionCounts.isEmpty()) {
            return null;
        }
        Map<String, List<List<String>>> result = new LinkedHashMap<>();
        for (Map.Entry<Integer, Integer> entry : branchLineToConditionCounts.entrySet()) {
            if (!isValidBranchLine(entry.getKey())) {
                continue;
            }
            putIfPresent(result, String.valueOf(entry.getKey()),
                    buildAllCoverageForConditionCount(entry.getValue() == null ? 0 : entry.getValue()));
        }
        return result.isEmpty() ? null : result;
    }

    public static List<List<String>> buildAllCoverageForConditionCount(int conditionCount) {
        int normalizedCount = normalizeConditionCount(conditionCount);
        int combinationCount = 1 << normalizedCount;
        List<List<String>> combinations = new ArrayList<>(combinationCount);
        for (int mask = 0; mask < combinationCount; mask++) {
            List<String> combination = new ArrayList<>(normalizedCount);
            for (int bit = normalizedCount - 1; bit >= 0; bit--) {
                combination.add(((mask >> bit) & 1) == 1 ? "true" : "false");
            }
            combinations.add(combination);
        }
        return combinations;
    }

    public static List<List<String>> buildObservedCoverageForBranch(int conditionCount,
                                                                    boolean falseExecuted,
                                                                    boolean trueExecuted) {
        int normalizedCount = normalizeConditionCount(conditionCount);
        List<List<String>> combinations = new ArrayList<>(2);
        if (trueExecuted) {
            combinations.add(buildFixedCombination(normalizedCount, "true"));
        }
        if (falseExecuted) {
            combinations.add(buildFixedCombination(normalizedCount, "false"));
        }
        return combinations;
    }

    public static Map<String, List<List<String>>> mergeCoverage(Map<String, List<List<String>>> current,
                                                                Map<String, List<List<String>>> incoming) {
        if ((current == null || current.isEmpty()) && (incoming == null || incoming.isEmpty())) {
            return null;
        }
        Map<String, List<List<String>>> merged = deepCopy(current);
        if (merged == null) {
            merged = new LinkedHashMap<>();
        }
        if (incoming == null || incoming.isEmpty()) {
            return merged == null || merged.isEmpty() ? null : merged;
        }
        for (Map.Entry<String, List<List<String>>> entry : incoming.entrySet()) {
            List<List<String>> mergedCombinations = mergeCombinationList(merged.get(entry.getKey()), entry.getValue());
            putIfPresent(merged, entry.getKey(), mergedCombinations);
        }
        return merged.isEmpty() ? null : merged;
    }

    public static Map<String, List<List<String>>> deepCopy(Map<String, List<List<String>>> coverage) {
        if (coverage == null || coverage.isEmpty()) {
            return null;
        }
        Map<String, List<List<String>>> copy = new LinkedHashMap<>();
        for (Map.Entry<String, List<List<String>>> entry : coverage.entrySet()) {
            copy.put(entry.getKey(), deepCopyCombinationList(entry.getValue()));
        }
        return copy.isEmpty() ? null : copy;
    }

    private static List<List<String>> mergeCombinationList(List<List<String>> current, List<List<String>> incoming) {
        LinkedHashSet<String> uniqueKeys = new LinkedHashSet<>();
        List<List<String>> merged = new ArrayList<>();
        appendUniqueCombinations(merged, uniqueKeys, current);
        appendUniqueCombinations(merged, uniqueKeys, incoming);
        return merged;
    }

    private static void appendUniqueCombinations(List<List<String>> target,
                                                 LinkedHashSet<String> uniqueKeys,
                                                 List<List<String>> source) {
        if (source == null || source.isEmpty()) {
            return;
        }
        for (List<String> combination : source) {
            List<String> copied = combination == null ? Collections.emptyList() : new ArrayList<>(combination);
            String uniqueKey = String.join("|", copied);
            if (uniqueKeys.add(uniqueKey)) {
                target.add(copied);
            }
        }
    }

    private static List<List<String>> deepCopyCombinationList(List<List<String>> combinations) {
        if (combinations == null || combinations.isEmpty()) {
            return new ArrayList<>();
        }
        List<List<String>> copy = new ArrayList<>(combinations.size());
        for (List<String> combination : combinations) {
            copy.add(combination == null ? new ArrayList<>() : new ArrayList<>(combination));
        }
        return copy;
    }

    private static List<String> buildFixedCombination(int conditionCount, String value) {
        List<String> combination = new ArrayList<>(conditionCount);
        for (int i = 0; i < conditionCount; i++) {
            combination.add(value);
        }
        return combination;
    }

    private static int normalizeConditionCount(int conditionCount) {
        return conditionCount > 0 ? conditionCount : 1;
    }

    private static boolean isValidBranchLine(Integer branchLine) {
        return branchLine != null && branchLine > 0;
    }

    private static void putIfPresent(Map<String, List<List<String>>> target, String branchLine,
                                     List<List<String>> combinations) {
        if (combinations != null && !combinations.isEmpty()) {
            target.put(branchLine, combinations);
        }
    }
}
