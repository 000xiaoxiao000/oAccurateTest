package com.oAT.web.language.spi;

import com.oAT.web.coveragecore.model.CoverageLanguage;
import com.oAT.web.esDao.entity.App;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Component
public class AppConfigValidatorRegistry {
    private final Map<CoverageLanguage, AppConfigValidator> validators = new EnumMap<>(CoverageLanguage.class);

    public AppConfigValidatorRegistry(List<AppConfigValidator> validatorList) {
        if (validatorList != null) {
            for (AppConfigValidator validator : validatorList) {
                validators.put(validator.language(), validator);
            }
        }
    }

    public void validateAndApplyDefaults(App app) {
        if (app == null) {
            return;
        }
        CoverageLanguage language = CoverageLanguage.from(app.getLanguage());
        AppConfigValidator validator = validators.get(language);
        if (validator == null) {
            app.setLanguage(language.name());
            return;
        }
        validator.validateAndApplyDefaults(app);
    }
}
