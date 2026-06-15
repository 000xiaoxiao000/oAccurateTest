package com.oAT.web.coverage.universal;

import com.fasterxml.jackson.databind.JsonNode;
import com.oAT.web.common.UtilJson;
import com.oAT.web.coverage.universal.UniversalCoverageFile.BranchCoverage;
import com.oAT.web.coverage.universal.UniversalCoverageFile.FunctionCoverage;
import com.oAT.web.coverage.universal.UniversalCoverageFile.LineCoverage;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

@Component
public class PythonCoverageParser implements CoverageParser {
    @Override
    public SourceType sourceType() {
        return SourceType.PYTHON;
    }

    @Override
    public List<UniversalCoverageFile> parse(byte[] rawData) {
        try {
            JsonNode root = UtilJson.getObjectMapper().readTree(new String(rawData, StandardCharsets.UTF_8));
            JsonNode filesNode = root.get("files");
            List<UniversalCoverageFile> files = new ArrayList<>();
            if (filesNode == null || !filesNode.isObject()) {
                return files;
            }
            Iterator<Map.Entry<String, JsonNode>> entries = filesNode.fields();
            while (entries.hasNext()) {
                Map.Entry<String, JsonNode> entry = entries.next();
                files.add(parseFile(entry.getKey(), entry.getValue()));
            }
            return files;
        } catch (Exception e) {
            throw new IllegalArgumentException("解析 coverage.py JSON 覆盖率失败", e);
        }
    }

    private UniversalCoverageFile parseFile(String filePath, JsonNode fileNode) {
        UniversalCoverageFile file = new UniversalCoverageFile(SourceType.PYTHON, filePath);
        addLines(file, fileNode.get("executed_lines"), 1);
        addLines(file, fileNode.get("missing_lines"), 0);
        addFunctions(file, fileNode.get("functions"));
        addBranches(file, fileNode.get("executed_branches"), 1);
        addBranches(file, fileNode.get("missing_branches"), 0);
        return file;
    }

    private void addLines(UniversalCoverageFile file, JsonNode lineNumbers, int coveredCount) {
        if (lineNumbers == null || !lineNumbers.isArray()) {
            return;
        }
        for (JsonNode lineNumber : lineNumbers) {
            file.getLines().add(new LineCoverage(lineNumber.asInt(0), coveredCount));
        }
    }

    private void addFunctions(UniversalCoverageFile file, JsonNode functions) {
        if (functions == null || !functions.isObject()) {
            return;
        }
        Iterator<Map.Entry<String, JsonNode>> entries = functions.fields();
        while (entries.hasNext()) {
            Map.Entry<String, JsonNode> entry = entries.next();
            JsonNode function = entry.getValue();
            JsonNode executedLines = function.get("executed_lines");
            JsonNode missingLines = function.get("missing_lines");
            int startLine = firstLine(executedLines, missingLines);
            int endLine = lastLine(executedLines, missingLines, startLine);
            int coveredCount = executedLines != null && executedLines.size() > 0 ? 1 : 0;
            file.getFunctions().add(new FunctionCoverage(entry.getKey(), startLine, endLine, coveredCount));
        }
    }

    private void addBranches(UniversalCoverageFile file, JsonNode branches, int coveredCount) {
        if (branches == null || !branches.isArray()) {
            return;
        }
        int index = 0;
        for (JsonNode branch : branches) {
            int line = branch.isArray() && branch.size() > 0 ? branch.get(0).asInt(0) : 0;
            file.getBranches().add(new BranchCoverage(line, index++, coveredCount, String.valueOf(line)));
        }
    }

    private int firstLine(JsonNode left, JsonNode right) {
        int result = Integer.MAX_VALUE;
        result = minLine(result, left);
        result = minLine(result, right);
        return result == Integer.MAX_VALUE ? 0 : result;
    }

    private int lastLine(JsonNode left, JsonNode right, int fallback) {
        int result = fallback;
        result = maxLine(result, left);
        result = maxLine(result, right);
        return result;
    }

    private int minLine(int current, JsonNode lines) {
        if (lines == null || !lines.isArray()) {
            return current;
        }
        for (JsonNode line : lines) {
            current = Math.min(current, line.asInt(current));
        }
        return current;
    }

    private int maxLine(int current, JsonNode lines) {
        if (lines == null || !lines.isArray()) {
            return current;
        }
        for (JsonNode line : lines) {
            current = Math.max(current, line.asInt(current));
        }
        return current;
    }
}
