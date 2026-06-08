package com.oAT.web.control;

import com.oAT.web.config.FrontendProperties;
import com.oAT.server.model.ClientSessionVo;
import com.oAT.web.common.DateUtil;
import com.oAT.web.control.entity.ResultNotified;
import com.oAT.web.esDao.entity.App;
import com.oAT.web.esDao.entity.SystemLog;
import com.oAT.web.service.AppService;
import com.oAT.web.service.ClientSessionService;
import com.oAT.web.service.OnlineInstanceSseService;
import com.oAT.web.service.ProbeAlertDashboardService;
import com.oAT.web.service.ProbeAlertSseService;
import com.oAT.web.service.ProjectService;
import com.oAT.web.service.SystemLogService;
import com.oAT.web.service.entity.AppVo;
import com.oAT.web.service.entity.ProbeAlertDashboardVo;
import com.oAT.web.service.entity.UserVo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.Assert;
import org.springframework.util.DigestUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import jakarta.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 应用管理控制器
 */
@Controller
@RequestMapping("/p/{projectId}/app/")
public class AppControl {

    @Autowired
    FrontendProperties frontendProperties;


    @Autowired
    ProjectService projectService;

    @Autowired
    AppService appService;

    @Autowired
    ClientSessionService sessionService;

    @Autowired
    private ProbeAlertDashboardService probeAlertDashboardService;

    @Autowired
    private ProbeAlertSseService probeAlertSseService;

    @Autowired
    private OnlineInstanceSseService onlineInstanceSseService;

    @Autowired
    private SystemLogService systemLogService;

    @RequestMapping("create")
    public String openAppAddView(@PathVariable String projectId,
                                 @SessionAttribute UserVo user,
                                 Model model) {
        return "redirect:" + frontendProperties.url("/p/" + projectId + "/apps?create=1");
    }

    // 打开APP列表页
    @RequestMapping("/list")
    public String openAppListView(@PathVariable String projectId, Model model, @SessionAttribute UserVo user) {
        return "redirect:" + frontendProperties.url("/p/" + projectId + "/apps");
    }

    /**
     * 创建新的应用
     */
    @RequestMapping("doCreate")
    @ResponseBody
    public ResultNotified<String> doCreateApp(@SessionAttribute UserVo user,
                                              App app,
                                              HttpServletRequest request) {
        app.setCreateUserId(user.getId());
        boolean probeAlertEnabled = isCheckboxChecked(request, "probeAlertEnabled");
        boolean probeAlertOnOffline = isCheckboxChecked(request, "probeAlertOnOffline");
        boolean probeAlertOnRecovered = isCheckboxChecked(request, "probeAlertOnRecovered");
        boolean probeAlertOnOnline = isCheckboxChecked(request, "probeAlertOnOnline");
        boolean notificationEventsAdjusted = probeAlertEnabled && !probeAlertOnOffline && !probeAlertOnRecovered && !probeAlertOnOnline;
        app.setProbeAlertEnabled(probeAlertEnabled);
        if (notificationEventsAdjusted) {
            probeAlertOnOffline = true;
            probeAlertOnRecovered = true;
        }
        app.setProbeAlertOnOffline(probeAlertOnOffline);
        app.setProbeAlertOnRecovered(probeAlertOnRecovered);
        app.setProbeAlertOnOnline(probeAlertOnOnline);
        AppVo appVo = appService.createApp(app);
        doLog(SystemLogService.Action.addApp, "添加了一个新应用", user, appVo);
        return new ResultNotified<>(true, "应用创建成功", "/p/" + app.getCreateProjectId() + "/apps");
    }

    private boolean isCheckboxChecked(HttpServletRequest request, String name) {
        String[] values = request.getParameterValues(name);
        return values != null && Arrays.stream(values).anyMatch("true"::equalsIgnoreCase);
    }

    private void doLog(SystemLogService.Action action, String actionMessage, UserVo user, AppVo appVo) {
        SystemLog log = new SystemLog();

        log.setTitle(String.format("%s %s <a href='/p/%s/apps/%s/settings'>%s</a>", user.getName(), actionMessage,
                appVo.getCreateProjectId(), appVo.getId(), appVo.getName()));
        log.setMessage(appVo.getDescribe());
        log.setUserId(user.getId());
        log.setUserName(user.getName());
        log.setProjectId(appVo.getCreateProjectId());
        log.setAction(SystemLogService.Action.addApp.toString());
        systemLogService.addLog(log);
    }

    @RequestMapping("edit")
    public String openEditView(@PathVariable String projectId,
                               String appId,
                               Model model,
                               @SessionAttribute UserVo user) {
        return "redirect:" + frontendProperties.url("/p/" + projectId + "/apps/" + appId + "/settings");
    }

    @RequestMapping(value = "{appId}/edit", method = RequestMethod.POST)
    public String openEditView(@PathVariable String projectId,
                               @PathVariable String appId,
                               @SessionAttribute UserVo user,
                               AppVo app,
                               HttpServletRequest request,
                               RedirectAttributes redirectAttributes) {
        Assert.isTrue(app.getId().equalsIgnoreCase(appId), "参数非法");
        boolean probeAlertEnabled = isCheckboxChecked(request, "probeAlertEnabled");
        boolean probeAlertOnOffline = isCheckboxChecked(request, "probeAlertOnOffline");
        boolean probeAlertOnRecovered = isCheckboxChecked(request, "probeAlertOnRecovered");
        boolean probeAlertOnOnline = isCheckboxChecked(request, "probeAlertOnOnline");
        boolean notificationEventsAdjusted = probeAlertEnabled && !probeAlertOnOffline && !probeAlertOnRecovered && !probeAlertOnOnline;
        app.setProbeAlertEnabled(probeAlertEnabled);
        if (notificationEventsAdjusted) {
            probeAlertOnOffline = true;
            probeAlertOnRecovered = true;
        }
        app.setProbeAlertOnOffline(probeAlertOnOffline);
        app.setProbeAlertOnRecovered(probeAlertOnRecovered);
        app.setProbeAlertOnOnline(probeAlertOnOnline);
        Integer submittedOfflineThresholdSeconds = app.getProbeOfflineThresholdSeconds();
        boolean offlineThresholdAdjusted = submittedOfflineThresholdSeconds != null
                && submittedOfflineThresholdSeconds > 0
                && submittedOfflineThresholdSeconds < 30;
        app = appService.updateApp(projectId, app);
        doLog(SystemLogService.Action.editApp, "修改了应用信息", user, app);
        String toastMessage = "应用修改成功";
        if (offlineThresholdAdjusted) {
            toastMessage += "。下线阈值最小支持 30 秒，已自动按 30 秒保存。";
        }
        if (notificationEventsAdjusted) {
            toastMessage += "。启用告警时至少需要选择一个通知事件，已自动启用下线和恢复上线通知。";
        }
        redirectAttributes.addFlashAttribute("toastMessage", toastMessage);
        redirectAttributes.addFlashAttribute("toastMessageType", "success");
        return "redirect:" + frontendProperties.url("/p/" + projectId + "/apps/" + appId + "/settings");
    }

    @RequestMapping(value = "{appId}/delete", method = RequestMethod.POST)
    @ResponseBody
    public ResultNotified<String> deleteApp(@PathVariable String projectId,
                                            @PathVariable String appId,
                                            String password,
                                            @SessionAttribute UserVo user) {
        String md5Pwd = DigestUtils.md5DigestAsHex(password.getBytes(StandardCharsets.UTF_8));
        Assert.isTrue(user.getPassword().equalsIgnoreCase(md5Pwd), "删除失败!密码错误");
        AppVo appVo = appService.deleteApp(projectId, appId);
        //记录删除日志
        SystemLog log = new SystemLog();
        log.setTitle(String.format("%s 删除了应用 %s", user.getName(), appVo.getName()));
        log.setUserId(user.getId());
        log.setUserName(user.getName());
        log.setProjectId(projectId);
        log.setAction(SystemLogService.Action.addApp.toString());
        systemLogService.addLog(log);
        return new ResultNotified<>(true, "应用已经被删除", "/p/" + projectId + "/home");
    }

    @RequestMapping("doEdit")
    @ResponseBody
    public ResultNotified<String> doEditApp(@PathVariable String projectId,
                                            @SessionAttribute UserVo user,
                                            AppVo app,
                                            HttpServletRequest request) {
        app.setProbeAlertEnabled(isCheckboxChecked(request, "probeAlertEnabled"));
        boolean probeAlertOnOffline = isCheckboxChecked(request, "probeAlertOnOffline");
        boolean probeAlertOnRecovered = isCheckboxChecked(request, "probeAlertOnRecovered");
        boolean probeAlertOnOnline = isCheckboxChecked(request, "probeAlertOnOnline");
        if (Boolean.TRUE.equals(app.getProbeAlertEnabled()) && !probeAlertOnOffline && !probeAlertOnRecovered && !probeAlertOnOnline) {
            probeAlertOnOffline = true;
            probeAlertOnRecovered = true;
        }
        app.setProbeAlertOnOffline(probeAlertOnOffline);
        app.setProbeAlertOnRecovered(probeAlertOnRecovered);
        app.setProbeAlertOnOnline(probeAlertOnOnline);
        app = appService.updateApp(projectId, app);
        doLog(SystemLogService.Action.editApp, "修改了应用信息", user, app);
        return new ResultNotified<>(true, "应用修改成功");
    }

    @RequestMapping("{appid}/oAT.key")
    @ResponseBody
    public String doDonwloadRegisterKey(@PathVariable String projectId, @PathVariable("appid") String appId) {
        StringBuilder builder = new StringBuilder();
        builder.append("#将此注册文件拷贝至应用工作目录，即：user.dir");
        builder.append("\r\n");
        builder.append("#app id 用于识别当前应用");
        builder.append("\r\n");
        builder.append("appKey=" + appId);
        builder.append("\r\n");
        builder.append("#保留配置");
        builder.append("\r\n");
        builder.append("#projectKey=" + projectId);
        return builder.toString();

    }

    @RequestMapping("doDelete")
    @ResponseBody
    public ResultNotified<String> doDeleteApp(@PathVariable String projectId, String appId, @SessionAttribute UserVo user) {
        AppVo appVo = appService.deleteApp(projectId, appId);
        //记录删除日志
        SystemLog log = new SystemLog();
        log.setTitle(String.format("%s 删除了应用 %s", user.getName(), appVo.getName()));
        log.setUserId(user.getId());
        log.setUserName(user.getName());
        log.setProjectId(projectId);
        log.setAction(SystemLogService.Action.addApp.toString());
        systemLogService.addLog(log);
        return new ResultNotified<>(true, "应用删除成功");
    }

    @RequestMapping("online")
    public String openOnlineList(@PathVariable String projectId, Model model, @SessionAttribute UserVo user) {
        return "redirect:" + frontendProperties.url("/p/" + projectId + "/apps/online");
    }

    @RequestMapping("online-counts")
    @ResponseBody
    public Map<String, Object> getOnlineCounts(@PathVariable String projectId) {
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("counts", onlineInstanceSseService.buildCounts(projectId));
        result.put("serverTime", System.currentTimeMillis());
        return result;
    }

    @RequestMapping(value = "online-counts/stream", produces = "text/event-stream")
    @ResponseBody
    public SseEmitter streamOnlineCounts(@PathVariable String projectId) {
        return onlineInstanceSseService.subscribe(projectId);
    }

    @RequestMapping("online-sessions")
    @ResponseBody
    public Map<String, Object> getOnlineSessions(@PathVariable String projectId) {
        List<Map<String, Object>> sessions = new ArrayList<>();
        for (ClientSessionVo onlineSession : getProjectOnlineSessions(projectId)) {
            Map<String, Object> item = new HashMap<>();
            item.put("addressIp", onlineSession.getClientInfo().getAddressIp());
            item.put("agentVersion", onlineSession.getClientInfo().getAgentVersion());
            item.put("systemDir", onlineSession.getClientInfo().getSystemDir());
            item.put("pid", onlineSession.getClientInfo().getPid());
            item.put("jvmVersion", onlineSession.getClientInfo().getJvmVersion());
            item.put("jvmOption", onlineSession.getClientInfo().getJvmOption());
            item.put("onlineTime", onlineSession.getOnlineTime());
            item.put("appName", onlineSession.getApplication() == null ? "未定义" : onlineSession.getApplication().getAppName());
            item.put("projectSrcName", onlineSession.getApplication() == null ? "" : onlineSession.getApplication().getProjectSrcName());
            sessions.add(item);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("sessions", sessions);
        result.put("total", sessions.size());
        result.put("serverTime", System.currentTimeMillis());
        return result;
    }

    private List<ClientSessionVo> getProjectOnlineSessions(String projectId) {
        List<ClientSessionVo> list = new ArrayList<>();
        List<AppVo> apps = appService.getAppList(projectId);
        List<String> appIds = new ArrayList<>();
        for (AppVo appVo : apps) {
            appIds.add(appVo.getId());
        }
        for (ClientSessionVo onlineSession : sessionService.getOnlineSessions()) {
            if (!StringUtils.hasText(onlineSession.getClientInfo().getAppKey()) ||
                    appIds.contains(onlineSession.getClientInfo().getAppKey())) {
                onlineSession.setOnlineTime(DateUtil.timeDifference(onlineSession.getLoginTime(), new Date()));
                list.add(onlineSession);
            }
        }
        return list;
    }

    @RequestMapping("{appId}/settings")
    public String openSetting(@PathVariable String projectId, @PathVariable String appId,
                              @SessionAttribute UserVo user, Model model) {
        return "redirect:" + frontendProperties.url("/p/" + projectId + "/apps/" + appId + "/settings");
    }

    @RequestMapping("{appId}/probe-alerts")
    public String openProbeAlerts(@PathVariable String projectId, @PathVariable String appId,
                                  @SessionAttribute UserVo user, Model model) {
        return "redirect:" + frontendProperties.url("/p/" + projectId + "/apps/" + appId + "/probe-alerts");
    }

    @RequestMapping("probe-alerts/recent")
    @ResponseBody
    public Map<String, Object> recentProbeAlerts(@PathVariable String projectId,
                                                 @RequestParam(value = "limit", defaultValue = "10") int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 20));
        List<ProbeAlertDashboardVo.ProbeAlertEventItemVo> events = probeAlertDashboardService.getRecentProjectEvents(projectId, safeLimit);
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("events", events);
        return result;
    }

    @RequestMapping(value = "probe-alerts/stream", produces = "text/event-stream")
    @ResponseBody
    public SseEmitter streamProbeAlerts(@PathVariable String projectId) {
        return probeAlertSseService.subscribe(projectId);
    }

}
