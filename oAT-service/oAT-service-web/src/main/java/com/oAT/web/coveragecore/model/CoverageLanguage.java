package com.oAT.web.coveragecore.model;

import com.oAT.web.coverage.universal.SourceType;
import org.springframework.util.StringUtils;

public enum CoverageLanguage {
    JAVA,
    FRONTEND,
    GO,
    PYTHON,
    CPP;

    public static CoverageLanguage from(String value) {
        if (!StringUtils.hasText(value)) {
            return JAVA;
        }
        for (CoverageLanguage language : values()) {
            if (language.name().equalsIgnoreCase(value.trim())) {
                return language;
            }
        }
        return JAVA;
    }

    public SourceType toSourceType() {
        return SourceType.from(name());
    }
}
