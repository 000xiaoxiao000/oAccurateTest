package com.oAT.web.language.java;

import com.oAT.web.coveragecore.model.CoverageBranch;
import com.oAT.web.coveragecore.model.CoverageFootprint;
import com.oAT.web.coveragecore.model.CoverageFunction;
import com.oAT.web.coveragecore.model.CoverageLanguage;
import com.oAT.web.coveragecore.model.CoverageLine;
import com.oAT.web.coveragecore.model.CoverageUnit;
import com.oAT.web.esDao.entity.ClassCoverageIndex;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
public class JavaCoverageUnitProjector {
    public CoverageUnit project(ClassCoverageIndex index) {
        return project(index, null);
    }

    public CoverageUnit project(ClassCoverageIndex index, String sourceCode) {
        String[] sourceLines = sourceCode == null ? null : sourceCode.split("\\r?\\n", -1);
        CoverageUnit unit = new CoverageUnit();
        unit.setLanguage(CoverageLanguage.JAVA);
        unit.setUnitKey(index.getClassName());
        unit.setDisplayName(StringUtils.hasText(index.getDisplayName()) ? index.getDisplayName() : index.getClassName());
        unit.setSourcePath(StringUtils.hasText(index.getSourcePath()) ? index.getSourcePath() : index.getClassName());
        unit.setFunctions(projectFunctions(index.getMethods(), sourceLines));
        unit.setLines(projectLines(index.getMethods(), sourceLines));
        unit.setBranches(projectBranches(index.getMethods(), sourceLines));
        return unit;
    }

    private List<CoverageFunction> projectFunctions(List<ClassCoverageIndex.MethodCoverageDetail> methods, String[] sourceLines) {
        List<CoverageFunction> functions = new ArrayList<>();
        if (methods == null) {
            return functions;
        }
        for (ClassCoverageIndex.MethodCoverageDetail method : methods) {
            if (method == null) {
                continue;
            }
            CoverageFunction function = new CoverageFunction();
            function.setSignature(method.getMethodName() + (StringUtils.hasText(method.getMethodDesc()) ? method.getMethodDesc() : ""));
            List<CoverageLine> methodLines = projectMethodLines(method, sourceLines);
            function.setStartLine(methodLines.isEmpty() ? minLine(method.getTotalLineNumbers()) : minCoverageLine(methodLines));
            function.setEndLine(methodLines.isEmpty() ? maxLine(method.getTotalLineNumbers()) : maxCoverageLine(methodLines));
            function.setComplexity(method.getComplexity());
            function.setLines(methodLines);
            function.setBranches(projectMethodBranches(method, sourceLines));
            functions.add(function);
        }
        return functions;
    }

    private List<CoverageLine> projectMethodLines(ClassCoverageIndex.MethodCoverageDetail method, String[] sourceLines) {
        List<CoverageLine> lines = new ArrayList<>();
        if (method.getTotalLineNumbers() == null) {
            return lines;
        }
        List<Integer> totalLineNumbers = executableLines(method.getTotalLineNumbers(), sourceLines);
        Set<Integer> coveredLines = remapCoveredLines(method.getCoveredLineNumbers(), totalLineNumbers, sourceLines);
        for (Integer lineNumber : totalLineNumbers) {
            if (lineNumber == null || lineNumber <= 0) {
                continue;
            }
            CoverageLine line = new CoverageLine();
            line.setLine(lineNumber);
            line.setHits(coveredLines.contains(lineNumber) ? 1 : 0);
            line.getFootprints().addAll(toFootprints(method.getLineFootprints() == null ? List.of() : method.getLineFootprints().get(lineNumber)));
            lines.add(line);
        }
        return lines;
    }

    private List<CoverageBranch> projectMethodBranches(ClassCoverageIndex.MethodCoverageDetail method, String[] sourceLines) {
        List<CoverageBranch> branches = new ArrayList<>();
        if (method.getTotalBranchTargetProbeMap() == null) {
            return branches;
        }
        Map<String, List<Integer>> coveredMap = method.getCoveredBranchTargetProbeMap();
        Set<Integer> usedDisplayLines = new LinkedHashSet<>();
        for (Map.Entry<String, List<Integer>> entry : sortedBranchEntries(method.getTotalBranchTargetProbeMap())) {
            List<Integer> targets = entry.getValue();
            if (targets == null) {
                continue;
            }
            List<Integer> coveredTargets = coveredMap == null ? List.of() : coveredMap.getOrDefault(entry.getKey(), List.of());
            int displayLine = remapBranchDisplayLine(parseLine(entry.getKey()), sourceLines, usedDisplayLines);
            for (Integer target : effectiveBranchTargets(targets, displayLine, sourceLines)) {
                CoverageBranch branch = new CoverageBranch();
                branch.setLine(displayLine);
                branch.setGroupId(entry.getKey());
                branch.setBranchIndex(target == null ? 0 : target);
                branch.setHits(coveredTargets.contains(target) ? 1 : 0);
                branch.getFootprints().addAll(toFootprints(method.getBranchFootprints() == null ? List.of() : method.getBranchFootprints().get(branchKey(entry.getKey(), target))));
                branches.add(branch);
            }
        }
        return branches;
    }

    private List<CoverageLine> projectLines(List<ClassCoverageIndex.MethodCoverageDetail> methods, String[] sourceLines) {
        Set<Integer> totalLines = new LinkedHashSet<>();
        Set<Integer> coveredLines = new LinkedHashSet<>();
        if (methods != null) {
            for (ClassCoverageIndex.MethodCoverageDetail method : methods) {
                if (method == null) {
                    continue;
                }
                if (method.getTotalLineNumbers() != null) {
                    List<Integer> methodLines = executableLines(method.getTotalLineNumbers(), sourceLines);
                    totalLines.addAll(methodLines);
                    coveredLines.addAll(remapCoveredLines(method.getCoveredLineNumbers(), methodLines, sourceLines));
                }
            }
        }
        List<CoverageLine> lines = new ArrayList<>();
        for (Integer lineNumber : totalLines) {
            if (lineNumber == null || lineNumber <= 0) {
                continue;
            }
            CoverageLine line = new CoverageLine();
            line.setLine(lineNumber);
            line.setHits(coveredLines.contains(lineNumber) ? 1 : 0);
            line.getFootprints().addAll(toFootprints(lineFootprints(methods, lineNumber)));
            lines.add(line);
        }
        return lines;
    }

    private List<Integer> executableLines(List<Integer> lineNumbers, String[] sourceLines) {
        List<Integer> lines = new ArrayList<>();
        if (lineNumbers == null) {
            return lines;
        }
        Set<Integer> seen = new LinkedHashSet<>();
        for (Integer lineNumber : lineNumbers) {
            if (lineNumber == null || lineNumber <= 0 || isNonExecutableStructureLine(sourceLines, lineNumber)) {
                continue;
            }
            seen.add(lineNumber);
        }
        lines.addAll(seen);
        return lines;
    }

    private Set<Integer> remapCoveredLines(List<Integer> coveredLineNumbers, List<Integer> executableLines, String[] sourceLines) {
        Set<Integer> coveredLines = new LinkedHashSet<>();
        if (coveredLineNumbers == null || executableLines == null || executableLines.isEmpty()) {
            return coveredLines;
        }
        Set<Integer> executableLineSet = new LinkedHashSet<>(executableLines);
        for (Integer lineNumber : coveredLineNumbers) {
            if (lineNumber == null || lineNumber <= 0) {
                continue;
            }
            if (executableLineSet.contains(lineNumber)) {
                coveredLines.add(lineNumber);
                continue;
            }
            if (isNonExecutableStructureLine(sourceLines, lineNumber)) {
                Integer nearest = nearestExecutableLine(lineNumber, executableLines);
                if (nearest != null) {
                    coveredLines.add(nearest);
                }
            }
        }
        return coveredLines;
    }

    private Integer nearestExecutableLine(int lineNumber, List<Integer> executableLines) {
        Integer nearest = null;
        int nearestDistance = Integer.MAX_VALUE;
        for (Integer candidate : executableLines) {
            if (candidate == null) {
                continue;
            }
            int distance = Math.abs(candidate - lineNumber);
            if (distance < nearestDistance || (distance == nearestDistance && candidate < lineNumber)) {
                nearest = candidate;
                nearestDistance = distance;
            }
        }
        return nearest;
    }

    private boolean isNonExecutableStructureLine(String[] sourceLines, int lineNumber) {
        if (sourceLines == null || lineNumber <= 0 || lineNumber > sourceLines.length) {
            return false;
        }
        String text = sourceLines[lineNumber - 1];
        if (text == null) {
            return true;
        }
        String trimmed = text.trim();
        return trimmed.isEmpty() || trimmed.matches("[{};]+");
    }

    private List<CoverageBranch> projectBranches(List<ClassCoverageIndex.MethodCoverageDetail> methods, String[] sourceLines) {
        List<CoverageBranch> branches = new ArrayList<>();
        if (methods == null) {
            return branches;
        }
        Set<Integer> usedDisplayLines = new LinkedHashSet<>();
        for (ClassCoverageIndex.MethodCoverageDetail method : methods) {
            if (method == null || method.getTotalBranchTargetProbeMap() == null) {
                continue;
            }
            Map<String, List<Integer>> coveredMap = method.getCoveredBranchTargetProbeMap();
            for (Map.Entry<String, List<Integer>> entry : sortedBranchEntries(method.getTotalBranchTargetProbeMap())) {
                List<Integer> targets = entry.getValue();
                if (targets == null) {
                    continue;
                }
                List<Integer> coveredTargets = coveredMap == null ? List.of() : coveredMap.getOrDefault(entry.getKey(), List.of());
                int displayLine = remapBranchDisplayLine(parseLine(entry.getKey()), sourceLines, usedDisplayLines);
                for (Integer target : effectiveBranchTargets(targets, displayLine, sourceLines)) {
                    CoverageBranch branch = new CoverageBranch();
                    branch.setLine(displayLine);
                    branch.setGroupId(entry.getKey());
                    branch.setBranchIndex(target == null ? 0 : target);
                    branch.setHits(coveredTargets.contains(target) ? 1 : 0);
                    branch.getFootprints().addAll(toFootprints(branchFootprints(methods, branchKey(entry.getKey(), target))));
                    branches.add(branch);
                }
            }
        }
        return branches;
    }

    private List<Integer> effectiveBranchTargets(List<Integer> targets, int displayLine, String[] sourceLines) {
        LinkedHashSet<Integer> effectiveTargets = new LinkedHashSet<>();
        if (targets != null) {
            for (Integer target : targets) {
                if (target != null) {
                    effectiveTargets.add(target);
                }
            }
        }
        if (isBranchExpressionLine(sourceLines, displayLine) && effectiveTargets.size() == 1) {
            int nextTarget = effectiveTargets.stream().mapToInt(Integer::intValue).max().orElse(0) + 1;
            effectiveTargets.add(nextTarget);
        }
        return new ArrayList<>(effectiveTargets);
    }

    private List<Map.Entry<String, List<Integer>>> sortedBranchEntries(Map<String, List<Integer>> branchMap) {
        if (branchMap == null || branchMap.isEmpty()) {
            return List.of();
        }
        return branchMap.entrySet().stream()
                .sorted((left, right) -> Integer.compare(parseLine(left.getKey()), parseLine(right.getKey())))
                .toList();
    }

    private int remapBranchDisplayLine(int rawLine, String[] sourceLines, Set<Integer> usedDisplayLines) {
        if (rawLine <= 0 || sourceLines == null || rawLine > sourceLines.length) {
            return rawLine;
        }
        for (int candidate = rawLine; candidate <= Math.min(sourceLines.length, rawLine + 4); candidate++) {
            if (usedDisplayLines.contains(candidate)) {
                continue;
            }
            if (isBranchExpressionLine(sourceLines, candidate)) {
                usedDisplayLines.add(candidate);
                return candidate;
            }
        }
        usedDisplayLines.add(rawLine);
        return rawLine;
    }

    private boolean isBranchExpressionLine(String[] sourceLines, int lineNumber) {
        if (sourceLines == null || lineNumber <= 0 || lineNumber > sourceLines.length) {
            return false;
        }
        String text = sourceLines[lineNumber - 1];
        if (text == null) {
            return false;
        }
        String trimmed = text.trim();
        return trimmed.contains("?")
                || trimmed.contains("&&")
                || trimmed.contains("||")
                || trimmed.matches(".*\\b(if|for|while|switch|catch)\\b.*");
    }

    private int minLine(List<Integer> lines) {
        if (lines == null || lines.isEmpty()) {
            return 0;
        }
        return lines.stream().filter(line -> line != null && line > 0).min(Integer::compareTo).orElse(0);
    }

    private int maxLine(List<Integer> lines) {
        if (lines == null || lines.isEmpty()) {
            return 0;
        }
        return lines.stream().filter(line -> line != null && line > 0).max(Integer::compareTo).orElse(0);
    }

    private int minCoverageLine(List<CoverageLine> lines) {
        if (lines == null || lines.isEmpty()) {
            return 0;
        }
        return lines.stream().map(CoverageLine::getLine).filter(line -> line != null && line > 0).min(Integer::compareTo).orElse(0);
    }

    private int maxCoverageLine(List<CoverageLine> lines) {
        if (lines == null || lines.isEmpty()) {
            return 0;
        }
        return lines.stream().map(CoverageLine::getLine).filter(line -> line != null && line > 0).max(Integer::compareTo).orElse(0);
    }

    private int parseLine(String key) {
        if (!StringUtils.hasText(key)) {
            return 0;
        }
        try {
            return Integer.parseInt(key.replaceAll("[^0-9].*$", ""));
        } catch (Exception e) {
            return 0;
        }
    }

    private String branchKey(String groupId, Integer branchIndex) {
        return (StringUtils.hasText(groupId) ? groupId : "0") + ":" + (branchIndex == null ? 0 : branchIndex);
    }

    private List<ClassCoverageIndex.CoverageFootprintRecord> lineFootprints(List<ClassCoverageIndex.MethodCoverageDetail> methods, Integer lineNumber) {
        List<ClassCoverageIndex.CoverageFootprintRecord> result = new ArrayList<>();
        if (methods == null || lineNumber == null) {
            return result;
        }
        for (ClassCoverageIndex.MethodCoverageDetail method : methods) {
            if (method != null && method.getLineFootprints() != null) {
                List<ClassCoverageIndex.CoverageFootprintRecord> records = method.getLineFootprints().get(lineNumber);
                if (records != null) {
                    result.addAll(records);
                }
            }
        }
        return result;
    }

    private List<ClassCoverageIndex.CoverageFootprintRecord> branchFootprints(List<ClassCoverageIndex.MethodCoverageDetail> methods, String branchKey) {
        List<ClassCoverageIndex.CoverageFootprintRecord> result = new ArrayList<>();
        if (methods == null || branchKey == null) {
            return result;
        }
        for (ClassCoverageIndex.MethodCoverageDetail method : methods) {
            if (method != null && method.getBranchFootprints() != null) {
                List<ClassCoverageIndex.CoverageFootprintRecord> records = method.getBranchFootprints().get(branchKey);
                if (records != null) {
                    result.addAll(records);
                }
            }
        }
        return result;
    }

    private List<CoverageFootprint> toFootprints(List<ClassCoverageIndex.CoverageFootprintRecord> records) {
        List<CoverageFootprint> footprints = new ArrayList<>();
        if (records == null) {
            return footprints;
        }
        for (ClassCoverageIndex.CoverageFootprintRecord record : records) {
            if (record == null) {
                continue;
            }
            CoverageFootprint footprint = new CoverageFootprint();
            footprint.setTraceId(record.getTraceId());
            footprint.setCaseName(record.getCaseName());
            footprint.setTestStage(record.getTestStage());
            footprint.setBuildId(record.getBuildId());
            footprint.setTimestamp(record.getTimestamp());
            footprints.add(footprint);
        }
        return footprints;
    }
}
