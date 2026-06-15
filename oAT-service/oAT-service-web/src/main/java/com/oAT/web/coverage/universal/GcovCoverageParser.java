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

@Component
public class GcovCoverageParser implements CoverageParser {
    @Override
    public SourceType sourceType() {
        return SourceType.CPP;
    }

    @Override
    public List<UniversalCoverageFile> parse(byte[] rawData) {
        try {
            JsonNode root = UtilJson.getObjectMapper().readTree(new String(rawData, StandardCharsets.UTF_8));
            JsonNode filesNode = root.has("files") ? root.get("files") : root;
            List<UniversalCoverageFile> files = new ArrayList<>();
            if (filesNode.isArray()) {
                for (JsonNode fileNode : filesNode) {
                    files.add(parseFile(fileNode));
                }
            } else if (filesNode.isObject()) {
                Iterator<JsonNode> values = filesNode.elements();
                while (values.hasNext()) {
                    files.add(parseFile(values.next()));
                }
            }
            return files;
        } catch (Exception e) {
            throw new IllegalArgumentException("解析 gcov JSON 覆盖率失败", e);
        }
    }

    private UniversalCoverageFile parseFile(JsonNode fileNode) {
        String filePath = text(fileNode, "file", "filename", "source_file", "path");
        UniversalCoverageFile file = new UniversalCoverageFile(SourceType.CPP, filePath);

        JsonNode functions = fileNode.get("functions");
        if (functions != null && functions.isArray()) {
            for (JsonNode function : functions) {
                int startLine = intValue(function, "start_line", "line_number", "line");
                int count = intValue(function, "execution_count", "count", "blocks_executed");
                file.getFunctions().add(new FunctionCoverage(text(function, "name", "demangled_name"), startLine, startLine, count));
            }
        }

        JsonNode lines = fileNode.get("lines");
        if (lines != null && lines.isArray()) {
            for (JsonNode line : lines) {
                int lineNumber = intValue(line, "line_number", "line");
                int count = intValue(line, "count", "execution_count");
                file.getLines().add(new LineCoverage(lineNumber, count));
                JsonNode branches = line.get("branches");
                if (branches != null && branches.isArray()) {
                    int branchIndex = 0;
                    for (JsonNode branch : branches) {
                        file.getBranches().add(new BranchCoverage(lineNumber, branchIndex++, intValue(branch, "count", "taken"), String.valueOf(lineNumber)));
                    }
                }
            }
        }
        return file;
    }

    private String text(JsonNode node, String... names) {
        if (node == null) {
            return null;
        }
        for (String name : names) {
            JsonNode value = node.get(name);
            if (value != null && !value.isNull()) {
                return value.asText();
            }
        }
        return null;
    }

    private int intValue(JsonNode node, String... names) {
        for (String name : names) {
            JsonNode value = node.get(name);
            if (value != null && !value.isNull()) {
                return value.asInt(0);
            }
        }
        return 0;
    }
}
