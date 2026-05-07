package com.oAT.web.control;

import com.oAT.web.control.entity.ResultNotified;
import com.oAT.web.service.AIInteractiveService;
import com.oAT.web.service.entity.AIInteractivePageVo;
import com.oAT.web.service.entity.AIInteractiveReplyVo;
import com.oAT.web.service.entity.UserVo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.SessionAttribute;

import java.util.Map;

@Controller
public class AIInteractiveControl {

    @Autowired
    private AIInteractiveService aiInteractiveService;

    @RequestMapping("/p/{projectId}/AIInteractive")
    public String openAIInteractiveView(@PathVariable String projectId,
                                        @SessionAttribute UserVo user,
                                        Model model) {
        AIInteractivePageVo page = aiInteractiveService.buildPage(projectId, user);
        model.addAttribute("projectId", page.getProjectId());
        model.addAttribute("projectName", page.getProjectName());
        model.addAttribute("projectSummary", page.getProjectSummary());
        model.addAttribute("welcomeMessage", page.getWelcomeMessage());
        model.addAttribute("starterQuestions", page.getStarterQuestions());
        model.addAttribute("abilityCards", page.getAbilityCards());
        model.addAttribute("onlineAppCount", page.getOnlineAppCount());
        model.addAttribute("appCount", page.getAppCount());
        model.addAttribute("appNames", page.getAppNames());
        model.addAttribute("mascotHint", page.getMascotHint());
        model.addAttribute("quickLinks", page.getQuickLinks());
        model.addAttribute("sessionState", page.getSessionState());
        model.addAttribute("aiTimeout", page.getAiTimeout());
        Map<String, String> mascot = page.getMascot();
        if (mascot != null) {
            model.addAllAttributes(mascot);
        }
        return "/ai/interactive";
    }

    @PostMapping("/p/{projectId}/AIInteractive/sessionState")
    @ResponseBody
    public ResultNotified<String> saveSessionState(@PathVariable String projectId,
                                                   @SessionAttribute UserVo user,
                                                   @RequestParam(required = false) String sessionState) {
        return new ResultNotified<>(true, "保存成功", aiInteractiveService.saveSessionState(projectId, user, sessionState));
    }

    @PostMapping("/p/{projectId}/AIInteractive/sessionState/clear")
    @ResponseBody
    public ResultNotified<String> clearSessionMemory(@PathVariable String projectId,
                                                     @SessionAttribute UserVo user) {
        return new ResultNotified<>(true, "记忆已清空", aiInteractiveService.clearSessionMemory(projectId, user));
    }

    @PostMapping("/p/{projectId}/AIInteractive/ask")
    @ResponseBody
    public ResultNotified<AIInteractiveReplyVo> ask(@PathVariable String projectId,
                                                    @SessionAttribute UserVo user,
                                                    String question,
                                                    String pageContext,
                                                    String imageData,
                                                    @RequestParam(required = false) String sessionState,
                                                    @RequestParam(required = false) String activeSessionId,
                                                    @RequestParam(required = false) String sessionSortMode,
                                                    @RequestParam(required = false) Boolean timelineExpanded) {
        if (!StringUtils.hasText(question) && !StringUtils.hasText(imageData)) {
            return new ResultNotified<>(false, "请输入您想了解的内容或上传图片");
        }
        return new ResultNotified<>(true, "分析完成",
                aiInteractiveService.ask(projectId, user, question.trim(), pageContext, imageData,
                        sessionState, activeSessionId, sessionSortMode, timelineExpanded));
    }
}
