package com.oAT.web.coverage.universal;

import org.springframework.util.StringUtils;

public enum SourceType {
    JAVA,
    FRONTEND,
    CPP,
    GO,
    PYTHON;

    public static SourceType from(String value) {
        if (!StringUtils.hasText(value)) {
            return JAVA;
        }
        for (SourceType sourceType : values()) {
            if (sourceType.name().equalsIgnoreCase(value.trim())) {
                return sourceType;
            }
        }
        return JAVA;
    }
}
