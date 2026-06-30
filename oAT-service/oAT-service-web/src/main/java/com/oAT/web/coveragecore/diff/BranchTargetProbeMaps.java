package com.oAT.web.coveragecore.diff;

import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class BranchTargetProbeMaps {
    private BranchTargetProbeMaps() {
    }

    public static Map<String, List<Integer>> copyBranchTargetProbeMap(Map<String, List<Integer>> source) {
        if (source == null || source.isEmpty()) {
            return null;
        }
        Map<String, List<Integer>> copy = new LinkedHashMap<>();
        for (Map.Entry<String, List<Integer>> entry : source.entrySet()) {
            List<Integer> values = entry.getValue() == null
                    ? Collections.emptyList()
                    : new ArrayList<>(new LinkedHashSet<>(entry.getValue()));
            copy.put(entry.getKey(), values);
        }
        return copy;
    }

    public static Map<String, List<Integer>> filterBranchTargetProbeMap(Map<String, List<Integer>> source,
                                                                        Collection<Integer> retainedLines) {
        if (source == null || source.isEmpty() || retainedLines == null || retainedLines.isEmpty()) {
            return null;
        }
        Set<Integer> lineSet = retainedLines instanceof Set ? (Set<Integer>) retainedLines : new HashSet<>(retainedLines);
        Map<String, List<Integer>> filtered = new LinkedHashMap<>();
        for (Map.Entry<String, List<Integer>> entry : source.entrySet()) {
            Integer branchLine = parsePositiveInt(entry.getKey());
            if (branchLine != null && lineSet.contains(branchLine)) {
                filtered.put(entry.getKey(), entry.getValue() == null
                        ? Collections.emptyList()
                        : new ArrayList<>(new LinkedHashSet<>(entry.getValue())));
            }
        }
        return filtered.isEmpty() ? null : filtered;
    }

    public static Map<String, List<Integer>> mergeBranchTargetProbeMap(Map<String, List<Integer>> current,
                                                                       Map<String, List<Integer>> incoming) {
        if ((current == null || current.isEmpty()) && (incoming == null || incoming.isEmpty())) {
            return new LinkedHashMap<>();
        }
        Map<String, LinkedHashSet<Integer>> merged = new LinkedHashMap<>();
        appendBranchTargetProbeMap(merged, current);
        appendBranchTargetProbeMap(merged, incoming);
        Map<String, List<Integer>> result = new LinkedHashMap<>();
        for (Map.Entry<String, LinkedHashSet<Integer>> entry : merged.entrySet()) {
            result.put(entry.getKey(), new ArrayList<>(entry.getValue()));
        }
        return result;
    }

    public static Map<String, List<Integer>> normalizeMethodBranchTargetProbeMap(Map<String, List<Integer>> total,
                                                                                 Map<String, List<Integer>> covered) {
        if (total == null || total.isEmpty()) {
            return new LinkedHashMap<>();
        }
        if (covered == null || covered.isEmpty()) {
            return copyBranchTargetProbeMap(total);
        }
        Map<String, List<Integer>> normalized = new LinkedHashMap<>();
        for (Map.Entry<String, List<Integer>> entry : total.entrySet()) {
            String branchLine = entry.getKey();
            List<Integer> totalValues = entry.getValue();
            if (totalValues == null || totalValues.isEmpty()) {
                continue;
            }
            LinkedHashSet<Integer> totalSet = new LinkedHashSet<>(totalValues);
            List<Integer> coveredValues = covered.get(branchLine);
            if (coveredValues == null || coveredValues.isEmpty()) {
                normalized.put(branchLine, new ArrayList<>(totalSet));
                continue;
            }
            LinkedHashSet<Integer> coveredSet = new LinkedHashSet<>(coveredValues);
            if (totalSet.containsAll(coveredSet)) {
                normalized.put(branchLine, new ArrayList<>(coveredSet));
            } else {
                normalized.put(branchLine, new ArrayList<>(totalSet));
            }
        }
        return normalized.isEmpty() ? copyBranchTargetProbeMap(total) : normalized;
    }

    public static Map<String, List<Integer>> normalizeCoveredBranchTargetProbeMap(Map<String, List<Integer>> total,
                                                                                  Map<String, List<Integer>> covered) {
        if (total == null || total.isEmpty() || covered == null || covered.isEmpty()) {
            return new LinkedHashMap<>();
        }
        Map<String, List<Integer>> normalized = new LinkedHashMap<>();
        for (Map.Entry<String, List<Integer>> entry : total.entrySet()) {
            List<Integer> totalValues = entry.getValue();
            if (totalValues == null || totalValues.isEmpty()) {
                continue;
            }
            Set<Integer> allowed = new LinkedHashSet<>(totalValues);
            List<Integer> coveredValues = covered.get(entry.getKey());
            if (coveredValues == null || coveredValues.isEmpty()) {
                continue;
            }
            LinkedHashSet<Integer> matched = new LinkedHashSet<>();
            for (Integer value : coveredValues) {
                if (value != null && allowed.contains(value)) {
                    matched.add(value);
                }
            }
            if (!matched.isEmpty()) {
                normalized.put(entry.getKey(), new ArrayList<>(matched));
            }
        }
        return normalized;
    }

    public static void appendBranchTargetProbeMap(Map<String, LinkedHashSet<Integer>> target,
                                                  Map<String, List<Integer>> source) {
        if (source == null || source.isEmpty()) {
            return;
        }
        for (Map.Entry<String, List<Integer>> entry : source.entrySet()) {
            LinkedHashSet<Integer> values = target.computeIfAbsent(entry.getKey(), key -> new LinkedHashSet<>());
            if (entry.getValue() != null) {
                values.addAll(entry.getValue());
            }
        }
    }

    public static int countBranchTargets(Map<String, List<Integer>> branchTargetProbeMap) {
        if (branchTargetProbeMap == null || branchTargetProbeMap.isEmpty()) {
            return 0;
        }
        int total = 0;
        for (List<Integer> values : branchTargetProbeMap.values()) {
            total += values == null ? 0 : new LinkedHashSet<>(values).size();
        }
        return total;
    }

    public static double calculateBranchRate(long coveredBranchTargets, long totalBranchTargets) {
        return totalBranchTargets > 0 ? (double) coveredBranchTargets / totalBranchTargets * 100 : 0.0;
    }

    private static Integer parsePositiveInt(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            int parsed = Integer.parseInt(value);
            return parsed > 0 ? parsed : null;
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
