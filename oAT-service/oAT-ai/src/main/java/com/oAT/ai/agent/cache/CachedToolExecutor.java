package com.oAT.ai.agent.cache;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.invocation.InvocationContext;
import dev.langchain4j.service.tool.ToolExecutionResult;
import dev.langchain4j.service.tool.ToolExecutor;

/**
 * Adds the shared tool call cache in front of LangChain4j's native tool executor.
 */
public final class CachedToolExecutor implements ToolExecutor {

    private final String toolName;
    private final ToolExecutor delegate;

    public CachedToolExecutor(String toolName, ToolExecutor delegate) {
        this.toolName = toolName;
        this.delegate = delegate;
    }

    @Override
    public String execute(ToolExecutionRequest request, Object memoryId) {
        String arguments = request != null ? request.arguments() : null;
        String cached = ToolCallCacheSupport.getCachedResult(toolName, arguments);
        if (cached != null) {
            return cached;
        }

        String result = delegate.execute(request, memoryId);
        ToolCallCacheSupport.cacheResult(toolName, arguments, result);
        return result;
    }

    @Override
    public ToolExecutionResult executeWithContext(ToolExecutionRequest request, InvocationContext context) {
        String arguments = request != null ? request.arguments() : null;
        String cached = ToolCallCacheSupport.getCachedResult(toolName, arguments);
        if (cached != null) {
            return ToolExecutionResult.builder()
                    .result(cached)
                    .resultText(cached)
                    .build();
        }

        ToolExecutionResult result = delegate.executeWithContext(request, context);
        if (result != null && !result.isError()) {
            ToolCallCacheSupport.cacheResult(toolName, arguments, result.resultText());
        }
        return result;
    }
}
