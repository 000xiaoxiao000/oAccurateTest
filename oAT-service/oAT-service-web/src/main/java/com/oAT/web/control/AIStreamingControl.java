package com.oAT.web.control;

import com.oAT.ai.agent.AgentContext;
import com.oAT.ai.agent.AIAgentService;
import com.oAT.web.service.ProjectService;
import com.oAT.web.service.entity.ProjectVo;
import com.oAT.web.service.entity.UserVo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * AI流式输出控制器
 * 支持SSE（Server-Sent Events）实现打字机效果
 */
@Controller
@RequestMapping("/p/{projectId}/AIInteractive")
public class AIStreamingControl {

    private static final Logger logger = LoggerFactory.getLogger(AIStreamingControl.class);
    
    private static final Long SSE_TIMEOUT = 1800_000L; // 30分钟
    
    private final ExecutorService sseExecutor = Executors.newFixedThreadPool(10);

    @Autowired
    private AIAgentService aiAgentService;
    
    @Autowired
    private ProjectService projectService;

    /**
     * 流式AI对话（SSE）
     */
    @PostMapping(value = "/ask/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @ResponseBody
    public SseEmitter askStreaming(@PathVariable String projectId,
                                   @SessionAttribute UserVo user,
                                   @RequestParam String question,
                                   @RequestParam(required = false) String pageContext,
                                   @RequestParam(required = false) String imageData) {
        
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT);
        
        // 异步处理
        CompletableFuture.runAsync(() -> {
            try {
                // 1. 发送开始事件
                sendEvent(emitter, "start", "{\"question\":\"" + escapeJson(question) + "\"}");
                
                // 2. 获取项目信息
                ProjectVo project = projectService.getProject(projectId);
                if (project == null) {
                    sendEvent(emitter, "error", "{\"message\":\"项目不存在\"}");
                    emitter.complete();
                    return;
                }
                
                // 3. 设置上下文
                AgentContext context = new AgentContext(projectId, user.getId(), user.getName());
                if (pageContext != null) {
                    context.setPageContext(pageContext);
                }
                
                // 4. 发送思考中事件
                sendEvent(emitter, "thinking", "{\"message\":\"AI正在分析您的问题...\"}");
                
                // 5. 调用AI服务
                String response;
                if (imageData != null && !imageData.isEmpty()) {
                    response = aiAgentService.chatWithImage(context, question, pageContext, imageData);
                } else if (pageContext != null && !pageContext.isEmpty()) {
                    response = aiAgentService.chatWithContext(context, question, pageContext);
                } else {
                    response = aiAgentService.chat(context, question);
                }
                
                // 6. 流式发送响应（模拟打字机效果）
                if (response != null) {
                    sendTypingEffect(emitter, response);
                } else {
                    sendEvent(emitter, "error", "{\"message\":\"AI服务暂时无法响应\"}");
                }
                
                // 7. 发送完成事件
                sendEvent(emitter, "complete", "{}");
                emitter.complete();
                
            } catch (Exception e) {
                logger.error("SSE streaming failed", e);
                try {
                    sendEvent(emitter, "error", "{\"message\":\"" + escapeJson(e.getMessage()) + "\"}");
                    emitter.completeWithError(e);
                } catch (IOException ioException) {
                    logger.error("Failed to send error event", ioException);
                }
            }
        }, sseExecutor);
        
        // 设置完成和错误回调
        emitter.onCompletion(() -> logger.debug("SSE connection completed"));
        emitter.onTimeout(() -> logger.warn("SSE connection timeout"));
        emitter.onError(throwable -> logger.error("SSE connection error", throwable));
        
        return emitter;
    }

    /**
     * 发送SSE事件
     */
    private void sendEvent(SseEmitter emitter, String event, String data) throws IOException {
        emitter.send(SseEmitter.event()
            .name(event)
            .data(data)
            .reconnectTime(3000));
    }

    /**
     * 模拟打字机效果发送响应
     * 按字符分批发送，创造打字机效果
     */
    private void sendTypingEffect(SseEmitter emitter, String response) throws IOException, InterruptedException {
        int chunkSize = 10; // 每次发送10个字符
        int length = response.length();
        
        for (int i = 0; i < length; i += chunkSize) {
            int end = Math.min(i + chunkSize, length);
            String chunk = response.substring(i, end);
            
            SseEmitter.SseEventBuilder event = SseEmitter.event()
                .name("content")
                .data(chunk)
                .reconnectTime(3000);
            
            emitter.send(event);
            
            // 延迟50ms，模拟打字速度
            Thread.sleep(50);
        }
    }

    /**
     * JSON转义
     */
    private String escapeJson(String text) {
        if (text == null) return "";
        return text.replace("\\", "\\\\")
                  .replace("\"", "\\\"")
                  .replace("\n", "\\n")
                  .replace("\r", "\\r")
                  .replace("\t", "\\t");
    }
}
