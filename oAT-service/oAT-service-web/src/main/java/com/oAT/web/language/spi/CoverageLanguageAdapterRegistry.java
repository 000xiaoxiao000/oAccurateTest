package com.oAT.web.language.spi;

import com.oAT.web.coveragecore.model.CoverageLanguage;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Component
public class CoverageLanguageAdapterRegistry {
    private final Map<CoverageLanguage, CoverageLanguageAdapter> adapters = new EnumMap<>(CoverageLanguage.class);

    public CoverageLanguageAdapterRegistry(List<CoverageLanguageAdapter> adapterList) {
        if (adapterList != null) {
            for (CoverageLanguageAdapter adapter : adapterList) {
                adapters.put(adapter.language(), adapter);
            }
        }
    }

    public CoverageLanguageAdapter get(CoverageLanguage language) {
        return adapters.get(language);
    }
}
