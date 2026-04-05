package com.oAT.ai.config;

import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.ollama.OllamaChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

import java.time.Duration;

/**
 * AI 大模型配置类
 * 基于 LangChain4j 框架，支持多种 LLM 提供商
 */
@Configuration
@ConfigurationProperties(prefix = "ai.llm")
public class AIConfig implements AIConfigProperties {

    private static final Logger logger = LoggerFactory.getLogger(AIConfig.class);

    /**
     * LLM 提供商类型
     */
    public enum Provider {
        OLLAMA,
        OPENAI,
        DEEPSEEK,
        CUSTOM
    }

    /**
     * 是否启用 AI 大模型功能
     */
    private boolean enabled = false;

    /**
     * LLM 提供商 (ollama, openai, deepseek, custom)
     */
    private String provider = "ollama";

    /**
     * API 基础 URL
     */
    private String baseUrl = "http://localhost:11434";

    /**
     * API Key
     */
    private String apiKey;

    /**
     * 模型名称
     */
    private String model = "gemma3:1b";

    /**
     * 最大 token 数
     */
    private int maxTokens = 2000;

    /**
     * 温度参数 (0-2, 值越低输出越确定)
     */
    private double temperature = 0.7;

    /**
     * 请求超时时间(秒)
     */
    private int timeout = 60;

    /**
     * 系统提示词前缀
     */
    private String systemPromptPrefix = "你是一个专业的代码覆盖率分析助手，专注于帮助用户进行链路分析、问题排查和数据洞察。";

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public int getMaxTokens() {
        return maxTokens;
    }

    public void setMaxTokens(int maxTokens) {
        this.maxTokens = maxTokens;
    }

    public double getTemperature() {
        return temperature;
    }

    public void setTemperature(double temperature) {
        this.temperature = temperature;
    }

    public int getTimeout() {
        return timeout;
    }

    public void setTimeout(int timeout) {
        this.timeout = timeout;
    }

    public String getSystemPromptPrefix() {
        return systemPromptPrefix;
    }

    public void setSystemPromptPrefix(String systemPromptPrefix) {
        this.systemPromptPrefix = systemPromptPrefix;
    }

    /**
     * 创建 ChatLanguageModel Bean
     * 根据 provider 配置创建对应的模型实例
     */
    @Bean
    public ChatLanguageModel chatLanguageModel() {
        if (!enabled) {
            logger.info("AI LLM is disabled, chatLanguageModel bean will not be created");
            return null;
        }

        Provider providerType = parseProvider(this.provider);
        logger.info("Initializing AI LLM with provider: {}, model: {}", providerType, model);

        switch (providerType) {
            case OLLAMA:
                return createOllamaModel();
            case OPENAI:
            case DEEPSEEK:
            case CUSTOM:
            default:
                return createOpenAiCompatibleModel(providerType);
        }
    }

    private Provider parseProvider(String providerStr) {
        if (!StringUtils.hasText(providerStr)) {
            return Provider.OLLAMA;
        }
        try {
            return Provider.valueOf(providerStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            logger.warn("Unknown provider: {}, using CUSTOM mode", providerStr);
            return Provider.CUSTOM;
        }
    }

    /**
     * 创建 Ollama 模型
     */
    private ChatLanguageModel createOllamaModel() {
        return OllamaChatModel.builder()
                .baseUrl(normalizeBaseUrl(baseUrl))
                .modelName(model)
                .temperature(temperature)
                .timeout(Duration.ofSeconds(timeout))
                .build();
    }

    /**
     * 创建 OpenAI 兼容模型 (OpenAI, DeepSeek, 自定义)
     */
    private ChatLanguageModel createOpenAiCompatibleModel(Provider providerType) {
        String apiUrl = normalizeBaseUrl(baseUrl);
        if (providerType == Provider.DEEPSEEK) {
            apiUrl = "https://api.deepseek.com/v1";
        } else if (providerType == Provider.OPENAI && !StringUtils.hasText(baseUrl)) {
            apiUrl = "https://api.openai.com/v1";
        }

        return OpenAiChatModel.builder()
                .baseUrl(apiUrl)
                .apiKey(apiKey != null ? apiKey : "none")
                .modelName(model)
                .maxTokens(maxTokens)
                .temperature(temperature)
                .timeout(Duration.ofSeconds(timeout))
                .build();
    }

    /**
     * 标准化基础 URL（移除末尾的 /v1 或 /）
     */
    private String normalizeBaseUrl(String url) {
        if (!StringUtils.hasText(url)) {
            return url;
        }
        // 对于 Ollama，保留基础地址
        if (url.endsWith("/v1")) {
            return url.substring(0, url.length() - 3);
        }
        if (url.endsWith("/")) {
            return url.substring(0, url.length() - 1);
        }
        return url;
    }
}
