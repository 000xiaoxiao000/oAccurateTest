package com.oAT.web.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;

@Configuration
@ConfigurationProperties(prefix = "ai.interactive.route")
public class AIInteractiveRouteConfig {

    private final PageMatcher coverage = new PageMatcher();
    private final PageMatcher trace = new PageMatcher();
    private final PageMatcher snapshot = new PageMatcher();
    private final PageMatcher app = new PageMatcher();
    private final PageMatcher codeRelation = new PageMatcher();

    public PageMatcher getCoverage() {
        return coverage;
    }

    public PageMatcher getTrace() {
        return trace;
    }

    public PageMatcher getSnapshot() {
        return snapshot;
    }

    public PageMatcher getApp() {
        return app;
    }

    public PageMatcher getCodeRelation() {
        return codeRelation;
    }

    public static class PageMatcher {
        private List<String> keywords = new ArrayList<>();

        public List<String> getKeywords() {
            return keywords;
        }

        public void setKeywords(List<String> keywords) {
            this.keywords = keywords;
        }
    }
}
