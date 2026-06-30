package com.oAT.web.control.api;

import com.oAT.web.api.context.FrontendContextPayloads.*;
import com.oAT.web.api.common.ApiSummaries.*;
import com.oAT.web.common.PaletteColors;
import com.oAT.web.control.entity.ResultNotified;
import com.oAT.web.exceptions.UserOperationException;
import com.oAT.web.service.AppService;
import com.oAT.web.service.ClientSessionService;
import com.oAT.web.service.ProjectService;
import com.oAT.web.service.SnapshotService;
import com.oAT.web.service.SystemLogService;
import com.oAT.web.service.UserService;
import com.oAT.web.service.entity.AppVo;
import com.oAT.web.service.entity.ProjectMemberVo;
import com.oAT.web.service.entity.ProjectVo;
import com.oAT.web.service.entity.SnapshotVo;
import com.oAT.web.service.entity.SystemLogVo;
import com.oAT.web.service.entity.UserVo;
import com.oAT.web.service.entity.UserRegisterVo;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.SessionAttribute;

import jakarta.servlet.http.HttpSession;
import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/api")
public class FrontendContextApiControl {

    private final UserService userService;
    private final ProjectService projectService;
    private final AppService appService;
    private final ClientSessionService clientSessionService;
    private final SnapshotService snapshotService;
    private final SystemLogService systemLogService;

    @Value("${ai.llm.enabled:true}")
    private boolean aiLlmEnabled;

    @Value("${ai.llm.timeout:120}")
    private int aiTimeout;

    public FrontendContextApiControl(UserService userService,
                                     ProjectService projectService,
                                     AppService appService,
                                     ClientSessionService clientSessionService,
                                     SnapshotService snapshotService,
                                     SystemLogService systemLogService) {
        this.userService = userService;
        this.projectService = projectService;
        this.appService = appService;
        this.clientSessionService = clientSessionService;
        this.snapshotService = snapshotService;
        this.systemLogService = systemLogService;
    }

    @PostMapping("/auth/register")
    public ResultNotified<String> register(@RequestBody UserRegisterVo request) {
        if (request == null || !org.springframework.util.StringUtils.hasText(request.getName())
                || !org.springframework.util.StringUtils.hasText(request.getEmail())
                || !org.springframework.util.StringUtils.hasText(request.getPassword())) {
            ResultNotified<String> invalid = new ResultNotified<>(false, "用户名、邮箱和密码不能为空");
            invalid.setErrorMessage("REGISTER_INVALID");
            return invalid;
        }
        if (!request.getPassword().equals(request.getAgainPassword())) {
            ResultNotified<String> invalid = new ResultNotified<>(false, "两次输入的密码不一致");
            invalid.setErrorMessage("REGISTER_PASSWORD_MISMATCH");
            return invalid;
        }
        userService.doRegister(request);
        return new ResultNotified<>(true, "注册成功，请登录", "/login");
    }

    @PostMapping("/auth/login")
    public ResultNotified<UserSummary> login(@RequestBody LoginRequest request, HttpSession session) {
        if (request == null || !org.springframework.util.StringUtils.hasText(request.getNameOrEmail())
                || !org.springframework.util.StringUtils.hasText(request.getPassword())) {
            ResultNotified<UserSummary> invalid = new ResultNotified<>(false, "用户名和密码不能为空");
            invalid.setErrorMessage("LOGIN_INVALID");
            return invalid;
        }
        try {
            UserVo user = userService.doLogin(request.getNameOrEmail(), request.getNameOrEmail(), request.getPassword());
            session.setAttribute("user", user);
            return new ResultNotified<>(true, "登录成功", toUserSummary(user));
        } catch (UserOperationException e) {
            ResultNotified<UserSummary> denied = new ResultNotified<>(false, e.getMessage());
            denied.setErrorMessage("LOGIN_FAILED");
            return denied;
        }
    }

    @GetMapping("/auth/me")
    public ResponseEntity<ResultNotified<UserSummary>> currentUser(HttpSession session) {
        UserVo user = (UserVo) session.getAttribute("user");
        if (user == null) {
            ResultNotified<UserSummary> unauthorized = new ResultNotified<>(false, "未登录或登录已过期");
            unauthorized.setErrorMessage("AUTH_REQUIRED");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(unauthorized);
        }
        UserVo latestUser = userService.getUser(user.getId());
        UserVo effectiveUser = latestUser != null ? latestUser : user;
        return ResponseEntity.ok(new ResultNotified<>(true, "获取当前用户成功", toUserSummary(effectiveUser)));
    }

    @PostMapping("/auth/logout")
    public ResultNotified<String> logout(HttpSession session) {
        session.removeAttribute("user");
        return new ResultNotified<>(true, "退出成功", "/login");
    }

    @GetMapping("/projects")
    public ResultNotified<List<ProjectSummary>> projects(@SessionAttribute UserVo user) {
        List<ProjectVo> projects = projectService.findProjectByMemberId(user.getId());
        List<ProjectSummary> result = new ArrayList<>();
        for (ProjectVo project : projects) {
            result.add(toProjectSummary(project));
        }
        return new ResultNotified<>(true, "获取项目列表成功", result);
    }

    @PostMapping("/projects")
    public ResultNotified<ProjectSummary> createProject(@SessionAttribute UserVo user,
                                                        @RequestBody SaveProjectRequest request) {
        if (request == null || !org.springframework.util.StringUtils.hasText(request.getName())) {
            ResultNotified<ProjectSummary> invalid = new ResultNotified<>(false, "项目名称不能为空");
            invalid.setErrorMessage("PROJECT_NAME_REQUIRED");
            return invalid;
        }
        ProjectService.CreateProjectParam param = new ProjectService.CreateProjectParam(
                request.getName().trim(),
                request.getDescribe(),
                user.getId()
        );
        param.userName = user.getName();
        ProjectVo project = projectService.createProject(param);
        return new ResultNotified<>(true, "项目创建成功", toProjectSummary(project));
    }

    @PostMapping("/projects/{projectId}")
    public ResultNotified<ProjectSummary> updateProject(@PathVariable String projectId,
                                                        @SessionAttribute UserVo user,
                                                        @RequestBody SaveProjectRequest request) {
        if (request == null || !org.springframework.util.StringUtils.hasText(request.getName())) {
            ResultNotified<ProjectSummary> invalid = new ResultNotified<>(false, "项目名称不能为空");
            invalid.setErrorMessage("PROJECT_NAME_REQUIRED");
            return invalid;
        }
        ProjectVo existing = projectService.getProjectByProjectIdAndMemberId(projectId, user.getId());
        if (existing == null) {
            ResultNotified<ProjectSummary> denied = new ResultNotified<>(false, "找不到指定项目,或者您没有该项目的访问权限");
            denied.setErrorMessage("PROJECT_ACCESS_DENIED");
            return denied;
        }
        existing.setName(request.getName().trim());
        existing.setDescribe(request.getDescribe());
        ProjectVo updated = projectService.updateProject(existing);
        return new ResultNotified<>(true, "项目信息修改成功", toProjectSummary(updated));
    }

    @PostMapping("/projects/{projectId}/delete")
    public ResultNotified<String> deleteProject(@PathVariable String projectId,
                                                @SessionAttribute UserVo user,
                                                @RequestBody DeleteProjectRequest request) {
        if (request == null || !org.springframework.util.StringUtils.hasText(request.getPassword())) {
            ResultNotified<String> invalid = new ResultNotified<>(false, "删除密码不能为空");
            invalid.setErrorMessage("PROJECT_DELETE_PASSWORD_REQUIRED");
            return invalid;
        }
        try {
            projectService.deleteProject(projectId, user.getId(), request.getPassword());
            return new ResultNotified<>(true, "项目已成功移除", projectId);
        } catch (UserOperationException e) {
            ResultNotified<String> denied = new ResultNotified<>(false, e.getMessage());
            denied.setErrorMessage("PROJECT_DELETE_FAILED");
            return denied;
        }
    }

    @GetMapping("/projects/{projectId}/context")
    public ResultNotified<ProjectContext> projectContext(@PathVariable String projectId,
                                                         @SessionAttribute UserVo user) {
        UserVo currentUser = userService.getUser(user.getId());
        UserVo effectiveUser = currentUser != null ? currentUser : user;
        ProjectVo project = projectService.getProjectByProjectIdAndMemberId(projectId, user.getId());
        if (project == null) {
            ResultNotified<ProjectContext> denied = new ResultNotified<>(false, "找不到指定项目,或者您没有该项目的访问权限");
            denied.setErrorMessage("PROJECT_ACCESS_DENIED");
            return denied;
        }

        List<AppVo> apps = appService.getAppList(projectId);
        List<AppSummary> appSummaries = new ArrayList<>();
        int onlineAppCount = 0;
        for (AppVo app : apps) {
            int onlineCount = clientSessionService.getOnlineSessionsByAppId(app.getId()).size();
            app.setOnlineCount(onlineCount);
            if (onlineCount > 0) {
                onlineAppCount++;
            }
            appSummaries.add(toAppSummary(app));
        }

        ProjectContext context = new ProjectContext();
        context.setProject(toProjectSummary(project));
        context.setApps(appSummaries);
        context.setCurrentUserRole(resolveUserRole(projectId, effectiveUser));
        context.setAi(toAiSummary(project));
        context.setOnlineAppCount(onlineAppCount);
        context.setAppCount(appSummaries.size());
        context.setCurrentUser(toUserSummary(effectiveUser));
        context.setRecentLogs(systemLogService.getSystemLog(projectId, 0, 10));
        List<SnapshotVo> snapshots = snapshotService.findSnapshot(projectId, effectiveUser.getId());
        context.setRecentSnapshots(new ArrayList<>(snapshots.subList(0, Math.min(5, snapshots.size()))));

        return new ResultNotified<>(true, "获取项目上下文成功", context);
    }

    private UserSummary toUserSummary(UserVo user) {
        UserSummary summary = new UserSummary();
        summary.setId(user.getId());
        summary.setName(user.getName());
        summary.setNickname(user.getNickname());
        summary.setEmail(user.getEmail());
        summary.setHeader(user.getHeader());
        summary.setPhone(user.getPhone());
        summary.setReadme(user.getReadme());
        return summary;
    }

    private ProjectSummary toProjectSummary(ProjectVo project) {
        ProjectSummary summary = new ProjectSummary();
        if (project == null) {
            summary.setName("未知项目");
            return summary;
        }
        summary.setId(project.getId());
        summary.setName(org.springframework.util.StringUtils.hasText(project.getName()) ? project.getName() : "未命名项目");
        summary.setDescribe(project.getDescribe());
        summary.setCreate(project.getCreate());
        summary.setCreateDisplayName(project.getCreateDisplayName());
        summary.setMemberCount(Math.max(project.getMemberCount(), 0));
        summary.setCreateTime(project.getCreateTime());
        summary.setUpdateTime(project.getUpdateTime());
        return summary;
    }

    private AppSummary toAppSummary(AppVo app) {
        AppSummary summary = new AppSummary();
        summary.setId(app.getId());
        summary.setName(app.getName());
        summary.setSrcName(app.getSrcName());
        summary.setLanguage(app.getLanguage());
        summary.setLanguageConfig(app.getLanguageConfig());
        summary.setDescribe(app.getDescribe());
        summary.setRange(app.getRange());
        summary.setOnlineCount(app.getOnlineCount());
        summary.setCurrentVersion(app.getCurrentVersion());
        summary.setCurrentBranch(app.getCurrentBranch());
        summary.setCurrentCommitId(app.getCurrentCommitId());
        summary.setRepoConfigured(app.getRepoAddress() != null && !app.getRepoAddress().trim().isEmpty());
        summary.setProbeAlertEnabled(Boolean.TRUE.equals(app.getProbeAlertEnabled()));
        summary.setSourceType(app.getLanguage());
        return summary;
    }

    private AiSummary toAiSummary(ProjectVo project) {
        AiSummary ai = new AiSummary();
        ai.setEnabled(aiLlmEnabled);
        ai.setTimeout(aiTimeout);
        ai.setInteractivePath("/p/" + project.getId() + "/ai");
        ai.setAskApiPath("/api/projects/" + project.getId() + "/ai/ask");
        ai.setFeedbackApiBasePath("/api/ai/feedback");
        ai.setMascotPrimary(computeMascotPrimary(project.getId(), project.getName()));
        return ai;
    }

    private String resolveUserRole(String projectId, UserVo user) {
        List<ProjectMemberVo> members = projectService.getProjectMembers(projectId);
        for (ProjectMemberVo member : members) {
            if (user.getName() != null && user.getName().equals(member.getMemberName()) && member.getRole() != null) {
                return member.getRole().name();
            }
        }
        return ProjectMemberVo.Role.visitor.name();
    }

    private String computeMascotPrimary(String projectId, String projectName) {
        int hash = (projectId + ":" + projectName).hashCode();
        if (hash == Integer.MIN_VALUE) {
            hash = 0;
        }
        return PaletteColors.pickPrimary(Math.abs(hash) / 5 + 13);
    }



}
