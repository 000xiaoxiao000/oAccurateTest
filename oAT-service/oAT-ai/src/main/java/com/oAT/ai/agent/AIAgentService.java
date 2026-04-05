package com.oAT.ai.agent;

import com.oAT.ai.agent.tools.*;
import com.oAT.ai.config.AIConfigProperties;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.service.AiServices;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * AI Agent 服务
 * 整合所有工具，提供智能对话能力
 */
@Service
public class AIAgentService {

    private static final Logger logger = LoggerFactory.getLogger(AIAgentService.class);

    private final AIAgent aiAgent;
    private final AIConfigProperties configProperties;
    private final AgentDataProvider dataProvider;

    @Autowired
    public AIAgentService(ChatLanguageModel chatLanguageModel,
                          AIConfigProperties configProperties,
                          AgentDataProvider dataProvider) {
        this.configProperties = configProperties;
        this.dataProvider = dataProvider;

        if (chatLanguageModel == null) {
            logger.warn("ChatLanguageModel is null, AI Agent will not be available");
            this.aiAgent = null;
        } else {
            ChatMemory chatMemory = MessageWindowChatMemory.withMaxMessages(10);
            List<Object> tools = createTools();
            this.aiAgent = AiServices.builder(AIAgent.class)
                    .chatLanguageModel(chatLanguageModel)
                    .chatMemory(chatMemory)
                    .tools(tools.toArray())
                    .build();
            logger.info("AI Agent initialized with {} tools", tools.size());
        }
    }

    private List<Object> createTools() {
        List<Object> tools = new ArrayList<>();
        tools.add(new ProjectInfoTool(dataProvider));
        tools.add(new AppStatusTool(dataProvider));
        tools.add(new CoverageTool(dataProvider));
        tools.add(new TraceQueryTool(dataProvider));
        tools.add(new SnapshotTool(dataProvider));
        tools.add(new CodeRelationTool(dataProvider));
        return tools;
    }

    public boolean isAvailable() {
        return aiAgent != null && configProperties.isEnabled();
    }

    public String chat(AgentContext context, String question) {
        if (!isAvailable()) {
            return null;
        }
        try {
            AgentContext.setContext(context);
            String response = aiAgent.chat(question, context.getProjectId(), context.getUserName());
            logger.info("AI Agent response: {}", response != null ?
                    (response.length() > 100 ? response.substring(0, 100) + "..." : response) : "null");
            return response;
        } catch (Exception e) {
            logger.error("AI Agent chat failed", e);
            return null;
        } finally {
            AgentContext.clearContext();
        }
    }

    public String chatWithContext(AgentContext context, String question, String pageContext) {
        if (!isAvailable()) {
            return null;
        }
        try {
            context.setPageContext(pageContext);
            AgentContext.setContext(context);
            return aiAgent.chatWithContext(question, context.getProjectId(), context.getUserName(), pageContext);
        } catch (Exception e) {
            logger.error("AI Agent chat with context failed", e);
            return null;
        } finally {
            AgentContext.clearContext();
        }
    }

    public String chatWithImage(AgentContext context, String question, String pageContext, String imageData) {
        if (!isAvailable()) {
            return null;
        }
        try {
            context.setPageContext(pageContext);
            AgentContext.setContext(context);
            // 截断过长的 base64 数据，防止超过模型上下文限制（保留前 500KB）
            String truncatedImage = imageData != null && imageData.length() > 680000
                    ? imageData.substring(0, 680000) + "... [图片数据已截断]"
                    : imageData;
            return aiAgent.chatWithImage(question, context.getProjectId(), context.getUserName(),
                    pageContext, truncatedImage);
        } catch (Exception e) {
            logger.error("AI Agent chat with image failed", e);
            return null;
        } finally {
            AgentContext.clearContext();
        }
    }

    public String getProjectOverview(AgentContext context) {
        if (!isAvailable()) {
            return null;
        }
        try {
            AgentContext.setContext(context);
            return aiAgent.getProjectOverview(context.getProjectId(), context.getUserName());
        } catch (Exception e) {
            logger.error("AI Agent get project overview failed", e);
            return null;
        } finally {
            AgentContext.clearContext();
        }
    }
}
