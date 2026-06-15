package com.oAT.web.coverage.universal;

import com.oAT.web.coverage.universal.UniversalCoverageFile.LineCoverage;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class GoCoverageParser implements CoverageParser {
    private static final Pattern COVER_PROFILE_LINE = Pattern.compile("^(.+):(\\d+)\\.\\d+,(\\d+)\\.\\d+\\s+\\d+\\s+(\\d+)$");

    @Override
    public SourceType sourceType() {
        return SourceType.GO;
    }

    @Override
    public List<UniversalCoverageFile> parse(byte[] rawData) {
        String raw = new String(rawData, StandardCharsets.UTF_8);
        Map<String, UniversalCoverageFile> files = new LinkedHashMap<>();
        for (String line : raw.split("\\R")) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("mode:")) {
                continue;
            }
            Matcher matcher = COVER_PROFILE_LINE.matcher(trimmed);
            if (!matcher.matches()) {
                continue;
            }
            String filePath = matcher.group(1);
            int startLine = Integer.parseInt(matcher.group(2));
            int endLine = Integer.parseInt(matcher.group(3));
            int count = Integer.parseInt(matcher.group(4));
            UniversalCoverageFile file = files.computeIfAbsent(filePath, key -> new UniversalCoverageFile(SourceType.GO, key));
            for (int currentLine = startLine; currentLine <= Math.max(startLine, endLine); currentLine++) {
                file.getLines().add(new LineCoverage(currentLine, count));
            }
        }
        return new ArrayList<>(files.values());
    }
}
