package com.oAT.web.coverage.universal;

import com.oAT.web.esDao.entity.ClassCoverageIndex;
import org.springframework.util.StringUtils;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class UniversalCoverageFile implements Serializable {
    private SourceType sourceType;
    private String filePath;
    private List<FunctionCoverage> functions = new ArrayList<>();
    private List<LineCoverage> lines = new ArrayList<>();
    private List<BranchCoverage> branches = new ArrayList<>();
    private Map<Integer, List<ClassCoverageIndex.CoverageFootprintRecord>> lineFootprints = new LinkedHashMap<>();
    private Map<String, List<ClassCoverageIndex.CoverageFootprintRecord>> branchFootprints = new LinkedHashMap<>();

    public UniversalCoverageFile() {
    }

    public UniversalCoverageFile(SourceType sourceType, String filePath) {
        this.sourceType = sourceType;
        this.filePath = filePath;
    }

    public UniversalCoverageFile merge(UniversalCoverageFile next) {
        if (next == null) {
            return this;
        }
        Map<Integer, LineCoverage> lineMap = new LinkedHashMap<>();
        for (LineCoverage line : lines) {
            lineMap.put(line.getLine(), line);
        }
        for (LineCoverage line : next.lines) {
            lineMap.compute(line.getLine(), (key, existing) -> existing == null ? line : existing.merge(line));
        }
        lines = new ArrayList<>(lineMap.values());
        mergeFootprints(lineFootprints, next.lineFootprints);

        Map<String, FunctionCoverage> functionMap = new LinkedHashMap<>();
        for (FunctionCoverage function : functions) {
            functionMap.put(function.key(), function);
        }
        for (FunctionCoverage function : next.functions) {
            functionMap.compute(function.key(), (key, existing) -> existing == null ? function : existing.merge(function));
        }
        functions = new ArrayList<>(functionMap.values());

        Map<String, BranchCoverage> branchMap = new LinkedHashMap<>();
        for (BranchCoverage branch : branches) {
            branchMap.put(branch.key(), branch);
        }
        for (BranchCoverage branch : next.branches) {
            branchMap.compute(branch.key(), (key, existing) -> existing == null ? branch : existing.merge(branch));
        }
        branches = new ArrayList<>(branchMap.values());
        mergeFootprints(branchFootprints, next.branchFootprints);
        return this;
    }

    public UniversalCoverageFile withFootprint(ClassCoverageIndex.CoverageFootprintRecord footprint) {
        if (footprint == null) {
            return this;
        }
        for (LineCoverage line : lines) {
            if (line.getLine() > 0 && line.getCoveredCount() > 0) {
                lineFootprints.computeIfAbsent(line.getLine(), ignored -> new ArrayList<>()).add(footprint);
            }
        }
        for (BranchCoverage branch : branches) {
            if (branch.getCoveredCount() > 0) {
                branchFootprints.computeIfAbsent(branch.key(), ignored -> new ArrayList<>()).add(footprint);
            }
        }
        return this;
    }

    public ClassCoverageIndex toClassCoverageIndex(String appId) {
        ClassCoverageIndex index = new ClassCoverageIndex();
        index.setAppId(appId);
        index.setClassName(filePath);
        index.setSourceType(sourceType == null ? SourceType.JAVA.name() : sourceType.name());
        index.setLanguage(sourceType == null ? SourceType.JAVA.name() : sourceType.name());
        index.setDisplayName(filePath);
        index.setSourcePath(filePath);

        Set<Integer> totalLines = new LinkedHashSet<>();
        Set<Integer> coveredLines = new LinkedHashSet<>();
        for (LineCoverage line : lines) {
            if (line.getLine() <= 0) {
                continue;
            }
            totalLines.add(line.getLine());
            if (line.getCoveredCount() > 0) {
                coveredLines.add(line.getLine());
            }
        }

        List<ClassCoverageIndex.MethodCoverageDetail> methods = new ArrayList<>();
        for (FunctionCoverage function : functions) {
            ClassCoverageIndex.MethodCoverageDetail method = function.toMethodCoverageDetail();
            method.setLineFootprints(lineFootprintsFor(function));
            methods.add(method);
        }

        Set<String> branchGroups = new LinkedHashSet<>();
        Set<String> coveredBranchGroups = new LinkedHashSet<>();
        int coveredBranchTargets = 0;
        for (BranchCoverage branch : branches) {
            String groupKey = branch.branchGroupKey();
            branchGroups.add(groupKey);
            if (branch.getCoveredCount() > 0) {
                coveredBranchGroups.add(groupKey);
                coveredBranchTargets++;
            }
        }
        if (!methods.isEmpty()) {
            methods.get(0).setBranchFootprints(new LinkedHashMap<>(branchFootprints));
        }

        index.setMethods(methods);
        index.setTotalLines(totalLines.size());
        index.setCoveredLines(coveredLines.size());
        index.setTotalMethods(methods.size());
        index.setCoveredMethods((int) methods.stream().filter(ClassCoverageIndex.MethodCoverageDetail::isCovered).count());
        index.setTotalBranches(branchGroups.size());
        index.setCoveredBranches(coveredBranchGroups.size());
        index.setTotalBranchTargets(branches.size());
        index.setCoveredBranchTargets(coveredBranchTargets);
        index.setTotalComplexity(index.getTotalMethods() + index.getTotalBranches());
        index.setLineRate(rate(index.getCoveredLines(), index.getTotalLines()));
        index.setMethodRate(rate(index.getCoveredMethods(), index.getTotalMethods()));
        index.setBranchRate(rate(index.getCoveredBranchTargets(), index.getTotalBranchTargets()));
        return index;
    }

    public String stableId(String reportId) {
        int hash = filePath == null ? 0 : filePath.hashCode();
        return reportId + "_" + Integer.toHexString(hash);
    }

    private static Double rate(int covered, int total) {
        return total > 0 ? (double) covered / total * 100D : 0D;
    }

    public SourceType getSourceType() { return sourceType; }
    public void setSourceType(SourceType sourceType) { this.sourceType = sourceType; }
    public String getFilePath() { return filePath; }
    public void setFilePath(String filePath) { this.filePath = filePath; }
    public List<FunctionCoverage> getFunctions() { return functions; }
    public void setFunctions(List<FunctionCoverage> functions) { this.functions = functions == null ? new ArrayList<>() : functions; }
    public List<LineCoverage> getLines() { return lines; }
    public void setLines(List<LineCoverage> lines) { this.lines = lines == null ? new ArrayList<>() : lines; }
    public List<BranchCoverage> getBranches() { return branches; }
    public void setBranches(List<BranchCoverage> branches) { this.branches = branches == null ? new ArrayList<>() : branches; }
    public Map<Integer, List<ClassCoverageIndex.CoverageFootprintRecord>> getLineFootprints() { return lineFootprints; }
    public void setLineFootprints(Map<Integer, List<ClassCoverageIndex.CoverageFootprintRecord>> lineFootprints) { this.lineFootprints = lineFootprints == null ? new LinkedHashMap<>() : lineFootprints; }
    public Map<String, List<ClassCoverageIndex.CoverageFootprintRecord>> getBranchFootprints() { return branchFootprints; }
    public void setBranchFootprints(Map<String, List<ClassCoverageIndex.CoverageFootprintRecord>> branchFootprints) { this.branchFootprints = branchFootprints == null ? new LinkedHashMap<>() : branchFootprints; }

    private Map<Integer, List<ClassCoverageIndex.CoverageFootprintRecord>> lineFootprintsFor(FunctionCoverage function) {
        Map<Integer, List<ClassCoverageIndex.CoverageFootprintRecord>> result = new LinkedHashMap<>();
        int normalizedStart = Math.max(1, function.getStartLine());
        int normalizedEnd = Math.max(normalizedStart, function.getEndLine());
        for (int line = normalizedStart; line <= normalizedEnd; line++) {
            List<ClassCoverageIndex.CoverageFootprintRecord> records = lineFootprints.get(line);
            if (records != null && !records.isEmpty()) {
                result.put(line, new ArrayList<>(records));
            }
        }
        return result;
    }

    private <K> void mergeFootprints(Map<K, List<ClassCoverageIndex.CoverageFootprintRecord>> target,
                                     Map<K, List<ClassCoverageIndex.CoverageFootprintRecord>> source) {
        if (source == null || source.isEmpty()) {
            return;
        }
        source.forEach((key, records) -> {
            if (records != null && !records.isEmpty()) {
                target.computeIfAbsent(key, ignored -> new ArrayList<>()).addAll(records);
            }
        });
    }

    public static class FunctionCoverage implements Serializable {
        private String name;
        private int startLine;
        private int endLine;
        private int coveredCount;

        public FunctionCoverage() {
        }

        public FunctionCoverage(String name, int startLine, int endLine, int coveredCount) {
            this.name = name;
            this.startLine = startLine;
            this.endLine = endLine;
            this.coveredCount = coveredCount;
        }

        FunctionCoverage merge(FunctionCoverage next) {
            coveredCount += next.coveredCount;
            startLine = Math.min(startLine, next.startLine);
            endLine = Math.max(endLine, next.endLine);
            return this;
        }

        ClassCoverageIndex.MethodCoverageDetail toMethodCoverageDetail() {
            int normalizedStart = Math.max(1, startLine);
            int normalizedEnd = Math.max(normalizedStart, endLine);
            List<Integer> totalLineNumbers = new ArrayList<>();
            for (int line = normalizedStart; line <= normalizedEnd; line++) {
                totalLineNumbers.add(line);
            }

            ClassCoverageIndex.MethodCoverageDetail method = new ClassCoverageIndex.MethodCoverageDetail();
            method.setMethodName(StringUtils.hasText(name) ? name : "(anonymous)");
            method.setMethodDesc(startLine > 0 ? "lines " + normalizedStart + "-" + normalizedEnd : "");
            method.setTotalLineNumbers(totalLineNumbers);
            method.setCoveredLineNumbers(coveredCount > 0 ? new ArrayList<>(totalLineNumbers) : new ArrayList<>());
            method.setTotalLines(totalLineNumbers.size());
            method.setCoveredLines(coveredCount > 0 ? totalLineNumbers.size() : 0);
            method.setCovered(coveredCount > 0);
            method.setComplexity(1);
            method.setTotalBranches(0);
            method.setCoveredBranches(0);
            method.setTotalBranchTargets(0);
            method.setCoveredBranchTargets(0);
            method.setBranchRate(0D);
            return method;
        }

        String key() {
            return (name == null ? "" : name) + "\n" + startLine + "\n" + endLine;
        }

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public int getStartLine() { return startLine; }
        public void setStartLine(int startLine) { this.startLine = startLine; }
        public int getEndLine() { return endLine; }
        public void setEndLine(int endLine) { this.endLine = endLine; }
        public int getCoveredCount() { return coveredCount; }
        public void setCoveredCount(int coveredCount) { this.coveredCount = coveredCount; }
    }

    public static class LineCoverage implements Serializable {
        private int line;
        private int coveredCount;

        public LineCoverage() {
        }

        public LineCoverage(int line, int coveredCount) {
            this.line = line;
            this.coveredCount = coveredCount;
        }

        LineCoverage merge(LineCoverage next) {
            coveredCount += next.coveredCount;
            return this;
        }

        public int getLine() { return line; }
        public void setLine(int line) { this.line = line; }
        public int getCoveredCount() { return coveredCount; }
        public void setCoveredCount(int coveredCount) { this.coveredCount = coveredCount; }
    }

    public static class BranchCoverage implements Serializable {
        private int line;
        private int branchIndex;
        private int coveredCount;
        private String groupId;

        public BranchCoverage() {
        }

        public BranchCoverage(int line, int branchIndex, int coveredCount, String groupId) {
            this.line = line;
            this.branchIndex = branchIndex;
            this.coveredCount = coveredCount;
            this.groupId = groupId;
        }

        BranchCoverage merge(BranchCoverage next) {
            coveredCount += next.coveredCount;
            return this;
        }

        String key() {
            return branchGroupKey() + ":" + branchIndex;
        }

        String branchGroupKey() {
            return StringUtils.hasText(groupId) ? groupId : String.valueOf(line);
        }

        public int getLine() { return line; }
        public void setLine(int line) { this.line = line; }
        public int getBranchIndex() { return branchIndex; }
        public void setBranchIndex(int branchIndex) { this.branchIndex = branchIndex; }
        public int getCoveredCount() { return coveredCount; }
        public void setCoveredCount(int coveredCount) { this.coveredCount = coveredCount; }
        public String getGroupId() { return groupId; }
        public void setGroupId(String groupId) { this.groupId = groupId; }
    }
}
