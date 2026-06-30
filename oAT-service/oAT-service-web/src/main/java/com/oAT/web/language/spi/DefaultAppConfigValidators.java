package com.oAT.web.language.spi;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.oAT.web.common.UtilJson;
import com.oAT.web.coveragecore.model.CoverageLanguage;
import com.oAT.web.esDao.entity.App;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

@Configuration
public class DefaultAppConfigValidators {
    @Bean
    AppConfigValidator javaAppConfigValidator() {
        return new BasicAppConfigValidator(CoverageLanguage.JAVA) {
            @Override
            public void validateAndApplyDefaults(App app) {
                super.validateAndApplyDefaults(app);
                if (!StringUtils.hasText(app.getRange())) {
                    app.setRange("all");
                }
                ObjectNode config = normalizeConfig(app);
                putIfMissing(config, "sourceRoot", "src/main/java");
                putIfMissing(config, "probeOfflineThresholdSeconds", app.getProbeOfflineThresholdSeconds() == null ? 90 : app.getProbeOfflineThresholdSeconds());
                app.setLanguageConfig(config.toPrettyString());
            }
        };
    }

    @Bean
    AppConfigValidator frontendAppConfigValidator() {
        return new BatchAppConfigValidator(CoverageLanguage.FRONTEND) {
            @Override
            protected void applyLanguageDefaults(ObjectNode config) {
                putIfMissing(config, "sourceRoot", "src");
                putIfMissing(config, "sourceMapRoot", "dist/assets");
            }
        };
    }

    @Bean
    AppConfigValidator goAppConfigValidator() {
        return new BatchAppConfigValidator(CoverageLanguage.GO) {
            @Override
            protected void applyLanguageDefaults(ObjectNode config) {
                putIfMissing(config, "profileFormat", "go-cover");
            }
        };
    }

    @Bean
    AppConfigValidator pythonAppConfigValidator() {
        return new BatchAppConfigValidator(CoverageLanguage.PYTHON) {
            @Override
            protected void applyLanguageDefaults(ObjectNode config) {
                putIfMissing(config, "packageRoot", "src");
                putIfMissing(config, "profileFormat", "coverage-json");
            }
        };
    }

    @Bean
    AppConfigValidator cppAppConfigValidator() {
        return new BatchAppConfigValidator(CoverageLanguage.CPP) {
            @Override
            protected void applyLanguageDefaults(ObjectNode config) {
                putIfMissing(config, "sourceRoot", "src");
                putIfMissing(config, "profileFormat", "lcov");
            }
        };
    }

    private static class BasicAppConfigValidator implements AppConfigValidator {
        private final CoverageLanguage language;

        private BasicAppConfigValidator(CoverageLanguage language) {
            this.language = language;
        }

        @Override
        public CoverageLanguage language() {
            return language;
        }
    }

    private abstract static class BatchAppConfigValidator extends BasicAppConfigValidator {
        private BatchAppConfigValidator(CoverageLanguage language) {
            super(language);
        }

        @Override
        public void validateAndApplyDefaults(App app) {
            super.validateAndApplyDefaults(app);
            app.setRange(StringUtils.hasText(app.getRange()) ? app.getRange() : "only");
            app.setSrcName("");
            app.setProperties("");
            app.setProbeAlertOnOnline(Boolean.FALSE);
            app.setProbeAlertOnOffline(Boolean.TRUE);
            app.setProbeAlertOnRecovered(Boolean.FALSE);
            ObjectNode config = normalizeConfig(app);
            applyLanguageDefaults(config);
            putIfMissing(config, "silentThresholdSeconds", app.getProbeOfflineThresholdSeconds() == null ? 300 : app.getProbeOfflineThresholdSeconds());
            putIfMissing(config, "reportIntervalSeconds", 60);
            Integer silentThreshold = positiveInt(config, "silentThresholdSeconds");
            app.setProbeOfflineThresholdSeconds(silentThreshold == null ? 300 : Math.max(30, silentThreshold));
            app.setLanguageConfig(config.toPrettyString());
        }

        protected abstract void applyLanguageDefaults(ObjectNode config);
    }

    private static ObjectNode normalizeConfig(App app) {
        if (StringUtils.hasText(app.getLanguageConfig())) {
            try {
                JsonNode node = UtilJson.getObjectMapper().readTree(app.getLanguageConfig());
                if (node != null && node.isObject()) {
                    return (ObjectNode) node;
                }
            } catch (Exception e) {
                throw new IllegalArgumentException("languageConfig必须是有效 JSON", e);
            }
        }
        return UtilJson.getObjectMapper().createObjectNode();
    }

    private static void putIfMissing(ObjectNode config, String field, String value) {
        if (!config.hasNonNull(field) && StringUtils.hasText(value)) {
            config.put(field, value);
        }
    }

    private static void putIfMissing(ObjectNode config, String field, int value) {
        if (!config.hasNonNull(field)) {
            config.put(field, value);
        }
    }

    private static Integer positiveInt(ObjectNode config, String field) {
        JsonNode value = config.get(field);
        if (value == null || !value.canConvertToInt()) {
            return null;
        }
        int intValue = value.asInt();
        return intValue > 0 ? intValue : null;
    }
}
