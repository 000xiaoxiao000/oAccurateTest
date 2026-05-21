package com.oAT.web.control.api;

import com.oAT.web.control.entity.ResultNotified;
import com.oAT.web.service.AIInteractiveService;
import com.oAT.web.service.entity.AIAbilityCardVo;
import com.oAT.web.service.entity.AIActionVo;
import com.oAT.web.service.entity.AIInteractivePageVo;
import com.oAT.web.service.entity.AIInteractiveReplyVo;
import com.oAT.web.service.entity.AIQuickLinkVo;
import com.oAT.web.service.entity.UserVo;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.SessionAttribute;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/projects/{projectId}/ai")
public class AIInteractiveApiControl {

    private final AIInteractiveService aiInteractiveService;

    public AIInteractiveApiControl(AIInteractiveService aiInteractiveService) {
        this.aiInteractiveService = aiInteractiveService;
    }

    @GetMapping("/context")
    public ResultNotified<AIInteractivePagePayload> context(@PathVariable String projectId,
                                                            @SessionAttribute UserVo user) {
        AIInteractivePageVo page = aiInteractiveService.buildPage(projectId, user);
        AIInteractivePagePayload payload = new AIInteractivePagePayload();
        payload.setProjectId(page.getProjectId());
        payload.setProjectName(page.getProjectName());
        payload.setProjectSummary(page.getProjectSummary());
        payload.setWelcomeMessage(page.getWelcomeMessage());
        payload.setMascotHint(page.getMascotHint());
        payload.setOnlineAppCount(page.getOnlineAppCount());
        payload.setAppCount(page.getAppCount());
        payload.setAppNames(page.getAppNames());
        payload.setStarterQuestions(page.getStarterQuestions());
        payload.setAbilityCards(page.getAbilityCards());
        payload.setQuickLinks(page.getQuickLinks());
        payload.setMascot(page.getMascot());
        payload.setAiTimeout(page.getAiTimeout());
        payload.setSessionState(page.getSessionState());
        return new ResultNotified<>(true, "获取 AI 工作台上下文成功", payload);
    }

    @PostMapping("/ask")
    public ResultNotified<AIInteractiveReplyVo> ask(@PathVariable String projectId,
                                                    @SessionAttribute UserVo user,
                                                    @RequestBody AskRequest request) {
        String question = request.getQuestion() == null ? "" : request.getQuestion().trim();
        if (!StringUtils.hasText(question) && !StringUtils.hasText(request.getImageData())) {
            return new ResultNotified<>(false, "请输入您想了解的内容或上传图片");
        }
        AIInteractiveReplyVo reply = aiInteractiveService.ask(
                projectId,
                user,
                question,
                request.getPageContext(),
                request.getImageData(),
                request.getSessionState(),
                request.getActiveSessionId(),
                request.getSessionSortMode(),
                request.getTimelineExpanded()
        );
        return new ResultNotified<>(true, "分析完成", reply);
    }

    @PostMapping("/session-state")
    public ResultNotified<String> saveSessionState(@PathVariable String projectId,
                                                   @SessionAttribute UserVo user,
                                                   @RequestBody SessionStateRequest request) {
        return new ResultNotified<>(true, "保存成功",
                aiInteractiveService.saveSessionState(projectId, user, request.getSessionState()));
    }

    @PostMapping("/session-state/clear")
    public ResultNotified<String> clearSessionState(@PathVariable String projectId,
                                                    @SessionAttribute UserVo user) {
        return new ResultNotified<>(true, "记忆已清空",
                aiInteractiveService.clearSessionMemory(projectId, user));
    }

    public static class AIInteractivePagePayload {
        private String projectId;
        private String projectName;
        private String projectSummary;
        private String welcomeMessage;
        private String mascotHint;
        private int onlineAppCount;
        private int appCount;
        private List<String> appNames;
        private List<String> starterQuestions;
        private List<AIAbilityCardVo> abilityCards;
        private List<AIQuickLinkVo> quickLinks;
        private Map<String, String> mascot;
        private int aiTimeout;
        private String sessionState;

        public String getProjectId() { return projectId; }
        public void setProjectId(String projectId) { this.projectId = projectId; }
        public String getProjectName() { return projectName; }
        public void setProjectName(String projectName) { this.projectName = projectName; }
        public String getProjectSummary() { return projectSummary; }
        public void setProjectSummary(String projectSummary) { this.projectSummary = projectSummary; }
        public String getWelcomeMessage() { return welcomeMessage; }
        public void setWelcomeMessage(String welcomeMessage) { this.welcomeMessage = welcomeMessage; }
        public String getMascotHint() { return mascotHint; }
        public void setMascotHint(String mascotHint) { this.mascotHint = mascotHint; }
        public int getOnlineAppCount() { return onlineAppCount; }
        public void setOnlineAppCount(int onlineAppCount) { this.onlineAppCount = onlineAppCount; }
        public int getAppCount() { return appCount; }
        public void setAppCount(int appCount) { this.appCount = appCount; }
        public List<String> getAppNames() { return appNames; }
        public void setAppNames(List<String> appNames) { this.appNames = appNames; }
        public List<String> getStarterQuestions() { return starterQuestions; }
        public void setStarterQuestions(List<String> starterQuestions) { this.starterQuestions = starterQuestions; }
        public List<AIAbilityCardVo> getAbilityCards() { return abilityCards; }
        public void setAbilityCards(List<AIAbilityCardVo> abilityCards) { this.abilityCards = abilityCards; }
        public List<AIQuickLinkVo> getQuickLinks() { return quickLinks; }
        public void setQuickLinks(List<AIQuickLinkVo> quickLinks) { this.quickLinks = quickLinks; }
        public Map<String, String> getMascot() { return mascot; }
        public void setMascot(Map<String, String> mascot) { this.mascot = mascot; }
        public int getAiTimeout() { return aiTimeout; }
        public void setAiTimeout(int aiTimeout) { this.aiTimeout = aiTimeout; }
        public String getSessionState() { return sessionState; }
        public void setSessionState(String sessionState) { this.sessionState = sessionState; }
    }

    public static class AskRequest {
        private String question;
        private String pageContext;
        private String imageData;
        private String sessionState;
        private String activeSessionId;
        private String sessionSortMode;
        private Boolean timelineExpanded;

        public String getQuestion() { return question; }
        public void setQuestion(String question) { this.question = question; }
        public String getPageContext() { return pageContext; }
        public void setPageContext(String pageContext) { this.pageContext = pageContext; }
        public String getImageData() { return imageData; }
        public void setImageData(String imageData) { this.imageData = imageData; }
        public String getSessionState() { return sessionState; }
        public void setSessionState(String sessionState) { this.sessionState = sessionState; }
        public String getActiveSessionId() { return activeSessionId; }
        public void setActiveSessionId(String activeSessionId) { this.activeSessionId = activeSessionId; }
        public String getSessionSortMode() { return sessionSortMode; }
        public void setSessionSortMode(String sessionSortMode) { this.sessionSortMode = sessionSortMode; }
        public Boolean getTimelineExpanded() { return timelineExpanded; }
        public void setTimelineExpanded(Boolean timelineExpanded) { this.timelineExpanded = timelineExpanded; }
    }

    public static class SessionStateRequest {
        private String sessionState;

        public String getSessionState() { return sessionState; }
        public void setSessionState(String sessionState) { this.sessionState = sessionState; }
    }
}
