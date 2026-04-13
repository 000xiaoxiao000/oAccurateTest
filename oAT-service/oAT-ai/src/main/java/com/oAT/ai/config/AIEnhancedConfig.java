package com.oAT.ai.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * AI 增强能力配置
 */
@Configuration
@ConfigurationProperties(prefix = "ai.enhanced")
public class AIEnhancedConfig {

    private final SemanticCache semanticCache = new SemanticCache();
    private final Conversation conversation = new Conversation();
    private final SelfLearning selfLearning = new SelfLearning();
    private final Feedback feedback = new Feedback();

    public SemanticCache getSemanticCache() {
        return semanticCache;
    }

    public Conversation getConversation() {
        return conversation;
    }

    public SelfLearning getSelfLearning() {
        return selfLearning;
    }

    public Feedback getFeedback() {
        return feedback;
    }

    public static class SemanticCache {
        private boolean enabled = true;
        private double threshold = 0.85;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public double getThreshold() {
            return threshold;
        }

        public void setThreshold(double threshold) {
            this.threshold = threshold;
        }
    }

    public static class Conversation {
        private int maxRounds = 20;

        public int getMaxRounds() {
            return maxRounds;
        }

        public void setMaxRounds(int maxRounds) {
            this.maxRounds = maxRounds;
        }
    }

    public static class SelfLearning {
        private int intervalHours = 6;

        public int getIntervalHours() {
            return intervalHours;
        }

        public void setIntervalHours(int intervalHours) {
            this.intervalHours = intervalHours;
        }
    }

    public static class Feedback {
        private int retentionDays = 30;

        public int getRetentionDays() {
            return retentionDays;
        }

        public void setRetentionDays(int retentionDays) {
            this.retentionDays = retentionDays;
        }
    }
}
