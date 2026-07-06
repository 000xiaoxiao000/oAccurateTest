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
        CoverageUnit unit = new CoverageUnit();
        unit.setLanguage(CoverageLanguage.JAVA);
        unit.setUnitKey(index.getClassName());
        unit.setDisplayName(StringUtils.hasText(index.getDisplayName()) ? index.getDisplayName() : index.getClassName());
        unit.setSourcePath(StringUtils.hasText(index.getSourcePath()) ? index.getSourcePath() : index.getClassName());
        unit.setFunctions(projectFunctions(index.getMethods()));
        unit.setLines(projectLines(index.getMethods()));
        unit.setBranches(projectBranches(index.getMethods()));
        return unit;
    }

    private List<CoverageFunction> projectFunctions(List<ClassCoverageIndex.MethodCoverageDetail> methods) {
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
            function.setStartLine(minLine(method.getTotalLineNumbers()));
            function.setEndLine(maxLine(method.getTotalLineNumbers()));
            function.setComplexity(method.getComplexity());
            function.setLines(projectMethodLines(method));
            function.setBranches(projectMethodBranches(method));
            functions.add(function);
        }
        return functions;
    }

    private List<CoverageLine> projectMethodLines(ClassCoverageIndex.MethodCoverageDetail method) {
        Set<Integer> coveredLines = method.getCoveredLineNumbers() == null ? Set.of() : new LinkedHashSet<>(method.getCoveredLineNumbers());
        List<CoverageLine> lines = new ArrayList<>();
        if (method.getTotalLineNumbers() == null) {
            return lines;
        }
        for (Integer lineNumber : method.getTotalLineNumbers()) {
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

    private List<CoverageBranch> projectMethodBranches(ClassCoverageIndex.MethodCoverageDetail method) {
        List<CoverageBranch> branches = new ArrayList<>();
        if (method.getTotalBranchTargetProbeMap() == null) {
            return branches;
        }
        Map<String, List<Integer>> coveredMap = method.getCoveredBranchTargetProbeMap();
        for (Map.Entry<String, List<Integer>> entry : method.getTotalBranchTargetProbeMap().entrySet()) {
            List<Integer> targets = entry.getValue();
            if (targets == null) {
                continue;
            }
            List<Integer> coveredTargets = coveredMap == null ? List.of() : coveredMap.getOrDefault(entry.getKey(), List.of());
            for (Integer target : targets) {
                CoverageBranch branch = new CoverageBranch();
                branch.setLine(parseLine(entry.getKey()));
                branch.setGroupId(entry.getKey());
                branch.setBranchIndex(target == null ? 0 : target);
                branch.setHits(coveredTargets.contains(target) ? 1 : 0);
                branch.getFootprints().addAll(toFootprints(method.getBranchFootprints() == null ? List.of() : method.getBranchFootprints().get(branchKey(entry.getKey(), target))));
                branches.add(branch);
            }
        }
        return branches;
    }

    private List<CoverageLine> projectLines(List<ClassCoverageIndex.MethodCoverageDetail> methods) {
        Set<Integer> totalLines = new LinkedHashSet<>();
        Set<Integer> coveredLines = new LinkedHashSet<>();
        if (methods != null) {
            for (ClassCoverageIndex.MethodCoverageDetail method : methods) {
                if (method == null) {
                    continue;
                }
                if (method.getTotalLineNumbers() != null) {
                    totalLines.addAll(method.getTotalLineNumbers());
                }
                if (method.getCoveredLineNumbers() != null) {
                    coveredLines.addAll(method.getCoveredLineNumbers());
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

    private List<CoverageBranch> projectBranches(List<ClassCoverageIndex.MethodCoverageDetail> methods) {
        List<CoverageBranch> branches = new ArrayList<>();
        if (methods == null) {
            return branches;
        }
        for (ClassCoverageIndex.MethodCoverageDetail method : methods) {
            if (method == null || method.getTotalBranchTargetProbeMap() == null) {
                continue;
            }
            Map<String, List<Integer>> coveredMap = method.getCoveredBranchTargetProbeMap();
            for (Map.Entry<String, List<Integer>> entry : method.getTotalBranchTargetProbeMap().entrySet()) {
                List<Integer> targets = entry.getValue();
                if (targets == null) {
                    continue;
                }
                List<Integer> coveredTargets = coveredMap == null ? List.of() : coveredMap.getOrDefault(entry.getKey(), List.of());
                for (Integer target : targets) {
                    CoverageBranch branch = new CoverageBranch();
                    branch.setLine(parseLine(entry.getKey()));
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
