package com.oAT.web.language.universal;

import com.oAT.web.coverage.universal.CoverageParserRegistry;
import com.oAT.web.language.spi.CoverageLanguageAdapter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class UniversalCoverageAdapterConfig {
    @Bean
    CoverageLanguageAdapter frontendCoverageLanguageAdapter(CoverageParserRegistry parserRegistry) {
        return new UniversalCoverageLanguageAdapter.Frontend(parserRegistry);
    }

    @Bean
    CoverageLanguageAdapter goCoverageLanguageAdapter(CoverageParserRegistry parserRegistry) {
        return new UniversalCoverageLanguageAdapter.Go(parserRegistry);
    }

    @Bean
    CoverageLanguageAdapter pythonCoverageLanguageAdapter(CoverageParserRegistry parserRegistry) {
        return new UniversalCoverageLanguageAdapter.Python(parserRegistry);
    }

    @Bean
    CoverageLanguageAdapter cppCoverageLanguageAdapter(CoverageParserRegistry parserRegistry) {
        return new UniversalCoverageLanguageAdapter.Cpp(parserRegistry);
    }
}
