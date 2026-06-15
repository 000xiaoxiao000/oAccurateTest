package com.oAT.web.coverage.universal;

import com.fasterxml.jackson.databind.JsonNode;
import com.oAT.web.common.UtilJson;
import com.oAT.web.coverage.universal.UniversalCoverageFile.BranchCoverage;
import com.oAT.web.coverage.universal.UniversalCoverageFile.FunctionCoverage;
import com.oAT.web.coverage.universal.UniversalCoverageFile.LineCoverage;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class IstanbulCoverageParser implements CoverageParser {
    @Override
    public SourceType sourceType() {
        return SourceType.FRONTEND;
    }

    @Override
    public List<UniversalCoverageFile> parse(byte[] rawData) {
        try {
            JsonNode root = UtilJson.getObjectMapper().readTree(new String(rawData, StandardCharsets.UTF_8));
            List<UniversalCoverageFile> files = new ArrayList<>();
            Iterator<Map.Entry<String, JsonNode>> fields = root.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> entry = fields.next();
                UniversalCoverageFile file = parseFile(firstText(text(entry.getValue().get("path")), entry.getKey()), entry.getValue());
                if (StringUtils.hasText(file.getFilePath())) {
                    files.add(file);
                }
            }
            return files;
        } catch (Exception e) {
            throw new IllegalArgumentException("解析 Istanbul 覆盖率失败", e);
        }
    }

    private UniversalCoverageFile parseFile(String filePath, JsonNode fileNode) {
        UniversalCoverageFile file = new UniversalCoverageFile(SourceType.FRONTEND, filePath);
        file.setLines(parseLines(fileNode));
        file.setFunctions(parseFunctions(fileNode));
        file.setBranches(parseBranches(fileNode));
        return file;
    }

    private List<LineCoverage> parseLines(JsonNode fileNode) {
        Map<Integer, LineCoverage> lineMap = new LinkedHashMap<>();
        JsonNode statementMap = fileNode.get("statementMap");
        JsonNode statementCounts = fileNode.get("s");
        if (statementMap == null) {
            return new ArrayList<>();
        }
        Iterator<Map.Entry<String, JsonNode>> statements = statementMap.fields();
        while (statements.hasNext()) {
            Map.Entry<String, JsonNode> statement = statements.next();
            int line = statement.getValue().path("start").path("line").asInt(0);
            if (line <= 0) {
                line = statement.getValue().path("loc").path("start").path("line").asInt(0);
            }
            if (line <= 0) {
                continue;
            }
            int count = statementCounts == null ? 0 : statementCounts.path(statement.getKey()).asInt(0);
            lineMap.compute(line, (key, existing) -> {
                if (existing == null) {
                    return new LineCoverage(key, count);
                }
                existing.setCoveredCount(existing.getCoveredCount() + count);
                return existing;
            });
        }
        return new ArrayList<>(lineMap.values());
    }

    private List<FunctionCoverage> parseFunctions(JsonNode fileNode) {
        List<FunctionCoverage> functions = new ArrayList<>();
        JsonNode functionMap = fileNode.get("fnMap");
        JsonNode functionCounts = fileNode.get("f");
        if (functionMap == null) {
            return functions;
        }
        Iterator<Map.Entry<String, JsonNode>> entries = functionMap.fields();
        while (entries.hasNext()) {
            Map.Entry<String, JsonNode> entry = entries.next();
            JsonNode fn = entry.getValue();
            int startLine = fn.path("loc").path("start").path("line").asInt(0);
            int endLine = fn.path("loc").path("end").path("line").asInt(startLine);
            int count = functionCounts == null ? 0 : functionCounts.path(entry.getKey()).asInt(0);
            functions.add(new FunctionCoverage(firstText(text(fn.get("name")), "(anonymous)"), startLine, endLine, count));
        }
        return functions;
    }

    private List<BranchCoverage> parseBranches(JsonNode fileNode) {
        List<BranchCoverage> branches = new ArrayList<>();
        JsonNode branchMap = fileNode.get("branchMap");
        JsonNode branchCounts = fileNode.get("b");
        if (branchMap == null && branchCounts == null) {
            return branches;
        }
        JsonNode branchesNode = branchMap == null ? branchCounts : branchMap;
        Iterator<Map.Entry<String, JsonNode>> entries = branchesNode.fields();
        while (entries.hasNext()) {
            Map.Entry<String, JsonNode> entry = entries.next();
            String branchId = entry.getKey();
            JsonNode counts = branchCounts == null ? null : branchCounts.get(branchId);
            JsonNode locations = branchMap == null ? null : entry.getValue().get("locations");
            int targetCount = locations != null && locations.isArray()
                    ? locations.size()
                    : counts != null && counts.isArray() ? counts.size() : 0;
            for (int targetIndex = 0; targetIndex < targetCount; targetIndex++) {
                int line = resolveBranchLine(entry.getValue(), locations, targetIndex);
                int count = counts == null ? 0 : counts.path(targetIndex).asInt(0);
                branches.add(new BranchCoverage(line, targetIndex, count, branchId));
            }
        }
        return branches;
    }

    private int resolveBranchLine(JsonNode branchNode, JsonNode locations, int targetIndex) {
        if (locations != null && locations.path(targetIndex).has("start")) {
            return locations.path(targetIndex).path("start").path("line").asInt(0);
        }
        return branchNode.path("loc").path("start").path("line").asInt(0);
    }

    private String text(JsonNode node) {
        return node == null || node.isNull() ? null : node.asText();
    }

    private String firstText(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return null;
    }
}
