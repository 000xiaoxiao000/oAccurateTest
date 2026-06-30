package com.oAT.web.language.spi;

import com.oAT.web.common.UtilJson;
import com.oAT.web.coveragecore.model.CoverageLanguage;
import com.oAT.web.esDao.entity.App;
import org.springframework.util.StringUtils;

public interface AppConfigValidator {
    CoverageLanguage language();

    default void validateAndApplyDefaults(App app) {
        if (app == null) {
            return;
        }
        app.setLanguage(language().name());
        if (!StringUtils.hasText(app.getLanguageConfig())) {
            app.setLanguageConfig("{}");
            return;
        }
        try {
            UtilJson.getObjectMapper().readTree(app.getLanguageConfig());
        } catch (Exception e) {
            throw new IllegalArgumentException("languageConfig必须是有效 JSON", e);
        }
    }
}
