package com.oAT.web.control;

import com.oAT.web.control.entity.ResultNotified;
import com.oAT.web.service.entity.AIFeedbackVo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.io.Serializable;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * AI反馈控制器
 * 收集和处理用户对AI回答的反馈
 */
@Controller
@RequestMapping("/api/ai/feedback")
public class AIFeedbackControl {

    private static final Logger logger = LoggerFactory.getLogger(AIFeedbackControl.class);
    
    // 临时存储反馈数据（实际应存入数据库）
    private final Map<String, AIFeedbackVo> feedbackStore = new ConcurrentHashMap<>();

    /**
     * 提交反馈
     */
    @PostMapping("/submit")
    @ResponseBody
    public ResultNotified<String> submitFeedback(@RequestBody AIFeedbackVo feedback) {
        try {
            // 生成反馈ID
            feedback.setFeedbackId(UUID.randomUUID().toString());
            feedback.setCreateTime(new Date());
            
            // 存储反馈
            feedbackStore.put(feedback.getFeedbackId(), feedback);
            
            logger.info("Feedback received: id={}, type={}, rating={}", 
                feedback.getFeedbackId(), feedback.getFeedbackType(), feedback.getRating());
            
            return new ResultNotified<>(true, "感谢您的反馈！");
        } catch (Exception e) {
            logger.error("Failed to submit feedback", e);
            return new ResultNotified<>(false, "提交反馈失败：" + e.getMessage());
        }
    }

    /**
     * 快速评分（点赞/点踩）
     */
    @PostMapping("/rate")
    @ResponseBody
    public ResultNotified<String> quickRate(@RequestParam String feedbackId,
                                           @RequestParam boolean helpful) {
        try {
            AIFeedbackVo feedback = feedbackStore.get(feedbackId);
            if (feedback == null) {
                return new ResultNotified<>(false, "反馈记录不存在");
            }
            
            feedback.setRating(helpful ? 5 : 1);
            feedback.setFeedbackType(helpful ? "helpful" : "not_helpful");
            
            logger.info("Quick rate: id={}, helpful={}", feedbackId, helpful);
            
            return new ResultNotified<>(true, "感谢评分！");
        } catch (Exception e) {
            logger.error("Failed to rate feedback", e);
            return new ResultNotified<>(false, "评分失败：" + e.getMessage());
        }
    }

    /**
     * 获取反馈统计
     */
    @GetMapping("/stats")
    @ResponseBody
    public ResultNotified<?> getFeedbackStats(@RequestParam(required = false) String projectId) {
        try {
            int totalFeedback = 0;
            int positiveFeedback = 0;
            int negativeFeedback = 0;
            Map<String, Integer> typeDistribution = new HashMap<>();
            
            for (AIFeedbackVo feedback : feedbackStore.values()) {
                if (projectId != null && !projectId.equals(feedback.getProjectId())) {
                    continue;
                }
                
                totalFeedback++;
                
                String type = feedback.getFeedbackType();
                typeDistribution.put(type, typeDistribution.getOrDefault(type, 0) + 1);
                
                if ("helpful".equals(type)) {
                    positiveFeedback++;
                } else {
                    negativeFeedback++;
                }
            }
            
            Map<String, Object> stats = new HashMap<>();
            stats.put("totalFeedback", totalFeedback);
            stats.put("positiveFeedback", positiveFeedback);
            stats.put("negativeFeedback", negativeFeedback);
            stats.put("satisfactionRate", totalFeedback > 0 
                ? String.format("%.2f%%", (double) positiveFeedback / totalFeedback * 100) 
                : "0%");
            stats.put("typeDistribution", typeDistribution);
            
            return new ResultNotified<>(true, "获取反馈统计成功", (Serializable) stats);
        } catch (Exception e) {
            logger.error("Failed to get feedback stats", e);
            return new ResultNotified<>(false, "获取统计失败：" + e.getMessage());
        }
    }

    /**
     * 获取用户的反馈历史
     */
    @GetMapping("/my")
    @ResponseBody
    public ResultNotified<?> getMyFeedback(@RequestParam String userId) {
        try {
            List<Map<String, Object>> myFeedbacks = new ArrayList<>();
            
            for (AIFeedbackVo feedback : feedbackStore.values()) {
                if (userId.equals(feedback.getUserId())) {
                    Map<String, Object> item = new HashMap<>();
                    item.put("feedbackId", feedback.getFeedbackId());
                    item.put("question", feedback.getQuestion());
                    item.put("rating", feedback.getRating());
                    item.put("feedbackType", feedback.getFeedbackType());
                    item.put("createTime", feedback.getCreateTime());
                    myFeedbacks.add(item);
                }
            }
            
            // 按时间倒序
            myFeedbacks.sort((a, b) -> {
                Date dateA = (Date) a.get("createTime");
                Date dateB = (Date) b.get("createTime");
                return dateB.compareTo(dateA);
            });
            
            return new ResultNotified<>(true, "获取反馈历史成功", (Serializable) (Serializable) myFeedbacks);
        } catch (Exception e) {
            logger.error("Failed to get user feedback", e);
            return new ResultNotified<>(false, "获取反馈历史失败：" + e.getMessage());
        }
    }
}
