package com.oAT.web.coverage.universal;

import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Component
public class CoverageParserRegistry {
    private final Map<SourceType, CoverageParser> parsers = new EnumMap<>(SourceType.class);

    public CoverageParserRegistry(List<CoverageParser> parserList) {
        for (CoverageParser parser : parserList) {
            parsers.put(parser.sourceType(), parser);
        }
    }

    public CoverageParser get(SourceType sourceType) {
        CoverageParser parser = parsers.get(sourceType);
        if (parser == null) {
            throw new IllegalArgumentException("不支持的覆盖率类型: " + sourceType);
        }
        return parser;
    }
}
