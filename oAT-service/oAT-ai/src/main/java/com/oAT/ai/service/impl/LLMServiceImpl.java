package com.oAT.ai.service.impl;

import com.oAT.ai.config.AIConfig;
import com.oAT.ai.service.LLMService;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.output.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * LLM 大模型服务实现类
 * 基于 LangChain4j 框架，支持多种 LLM 提供商
 */
@Service
public class LLMServiceImpl implements LLMService {

    private static final Logger logger = LoggerFactory.getLogger(LLMServiceImpl.class);

    @Autowired
    private AIConfig aiConfig;

    @Autowired(required = false)
    private ChatLanguageModel chatLanguageModel;

    @Override
    public String chat(String systemPrompt, String userMessage) {
        if (!isAvailable()) {
            logger.warn("LLM service is not available, please check configuration");
            return null;
        }

        try {
            // 构建消息列表
            List<ChatMessage> messages = new ArrayList<>();
            if (StringUtils.hasText(systemPrompt)) {
                messages.add(SystemMessage.from(systemPrompt));
            }
            messages.add(UserMessage.from(userMessage));

            logger.info("Sending request to LLM...");
            logger.debug("User message: {}", userMessage);

            // 调用模型
            Response<AiMessage> response = chatLanguageModel.generate(messages);
            String answer = response.content().text();

            logger.info("LLM response received successfully");
            logger.debug("Response: {}", answer);

            return answer;
        } catch (Exception e) {
            logger.error("Failed to call LLM", e);
            return null;
        }
    }

    @Override
    public boolean isAvailable() {
        return aiConfig.isEnabled() && chatLanguageModel != null;
    }
}
