package com.oAT.web.control.api;

import com.oAT.web.control.entity.ResultNotified;
import com.oAT.web.esDao.entity.App;
import com.oAT.web.esDao.entity.LabelGroup;
import com.oAT.web.service.AppService;
import com.oAT.web.service.ClientSessionService;
import com.oAT.web.service.ProjectService;
import com.oAT.web.service.UserService;
import com.oAT.web.service.entity.AppVo;
import com.oAT.web.service.entity.LabelGroupVo;
import com.oAT.web.service.entity.LableType;
import com.oAT.web.service.entity.ProjectMemberVo;
import com.oAT.web.service.entity.ProjectVo;
import com.oAT.web.service.entity.UserVo;
import org.springframework.util.Assert;
import org.springframework.util.DigestUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.SessionAttribute;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/api/projects/{projectId}")
public class ProjectSettingsApiControl {

    private final ProjectService projectService;
    private final AppService appService;
    private final ClientSessionService clientSessionService;
    private final UserService userService;

    public ProjectSettingsApiControl(ProjectService projectService,
                                     AppService appService,
                                     ClientSessionService clientSessionService,
                                     UserService userService) {
        this.projectService = projectService;
        this.appService = appService;
        this.clientSessionService = clientSessionService;
        this.userService = userService;
    }

    @GetMapping("/apps")
    public ResultNotified<List<FrontendContextApiControl.AppSummary>> apps(@PathVariable String projectId,
                                                                           @SessionAttribute UserVo user) {
        ensureProjectAccess(projectId, user);
        List<AppVo> apps = appService.getAppList(projectId);
        List<FrontendContextApiControl.AppSummary> result = new ArrayList<>();
        for (AppVo app : apps) {
            app.setOnlineCount(clientSessionService.getOnlineSessionsByAppId(app.getId()).size());
            result.add(toAppSummary(app));
        }
        return new ResultNotified<>(true, "获取应用列表成功", result);
    }

    @PostMapping("/apps")
    public ResultNotified<FrontendContextApiControl.AppSummary> createApp(@PathVariable String projectId,
                                                                          @SessionAttribute UserVo user,
                                                                          @RequestBody SaveAppRequest request) {
        ensureProjectAccess(projectId, user);
        Assert.notNull(request, "请求体不能为空");
        Assert.hasText(request.getName(), "应用名称不能为空");

        App app = new App();
        app.setCreateProjectId(projectId);
        app.setCreateUserId(user.getId());
        app.setName(request.getName().trim());
        app.setSrcName(request.getSrcName());
        app.setRange(request.getRange());
        app.setDescribe(request.getDescribe());
        app.setProperties(request.getProperties());
        app.setCurrentVersion(request.getCurrentVersion());
        app.setCurrentBranch(request.getCurrentBranch());
        app.setCurrentCommitId(request.getCurrentCommitId());
        applyProbeAlertSettings(app, request);

        AppVo created = appService.createApp(app);
        created.setOnlineCount(clientSessionService.getOnlineSessionsByAppId(created.getId()).size());
        return new ResultNotified<>(true, "应用创建成功", toAppSummary(created));
    }

    @PostMapping("/apps/{appId}/delete")
    public ResultNotified<String> deleteApp(@PathVariable String projectId,
                                            @PathVariable String appId,
                                            @SessionAttribute UserVo user,
                                            @RequestBody DeleteAppRequest request) {
        ensureProjectAccess(projectId, user);
        Assert.notNull(request, "请求体不能为空");
        Assert.hasText(request.getPassword(), "删除密码不能为空");
        String md5Pwd = DigestUtils.md5DigestAsHex(request.getPassword().getBytes(StandardCharsets.UTF_8));
        Assert.isTrue(user.getPassword().equalsIgnoreCase(md5Pwd), "删除失败!密码错误");
        appService.deleteApp(projectId, appId);
        return new ResultNotified<>(true, "应用已经被删除", appId);
    }

    @GetMapping("/members")
    public ResultNotified<ProjectMembersPayload> members(@PathVariable String projectId,
                                                         @SessionAttribute UserVo user) {
        ensureProjectAccess(projectId, user);
        List<ProjectMemberVo> members = projectService.getProjectMembers(projectId);
        List<UserVo> users = new ArrayList<>(userService.getAllUser());
        Set<String> existingMemberIds = new HashSet<>();
        List<ProjectMemberSummary> memberSummaries = new ArrayList<>();

        for (ProjectMemberVo member : members) {
            existingMemberIds.add(member.getMemberId());
            UserVo profile = findUser(users, member.getMemberId());
            if (profile != null) {
                member.setMemberName(profile.getName());
                member.setMemberEmail(profile.getEmail());
            }
            memberSummaries.add(toProjectMemberSummary(member));
        }

        List<FrontendContextApiControl.UserSummary> availableUsers = new ArrayList<>();
        for (UserVo candidate : users) {
            if (!existingMemberIds.contains(candidate.getId())) {
                availableUsers.add(toUserSummary(candidate));
            }
        }

        ProjectMembersPayload payload = new ProjectMembersPayload();
        payload.setMembers(memberSummaries);
        payload.setAvailableUsers(availableUsers);
        payload.setCurrentUserRole(resolveUserRole(projectId, user));
        return new ResultNotified<>(true, "获取项目成员成功", payload);
    }

    @PostMapping("/members/add")
    public ResultNotified<ProjectMembersPayload> addMembers(@PathVariable String projectId,
                                                            @SessionAttribute UserVo user,
                                                            @RequestBody AddMembersRequest request) {
        ensureProjectAccess(projectId, user);
        Assert.notNull(request, "请求体不能为空");
        Assert.notEmpty(request.getUserIds(), "待添加用户不能为空");
        for (String userId : request.getUserIds()) {
            if (StringUtils.hasText(userId)) {
                projectService.addProjectMember(projectId, userId);
            }
        }
        return members(projectId, user);
    }

    @PostMapping("/members/{projectMemberId}/remove")
    public ResultNotified<ProjectMembersPayload> removeMember(@PathVariable String projectId,
                                                              @PathVariable String projectMemberId,
                                                              @SessionAttribute UserVo user) {
        ensureProjectAccess(projectId, user);
        projectService.deleteProjectMember(projectId, projectMemberId);
        return members(projectId, user);
    }

    @PostMapping("/members/{projectMemberId}/role")
    public ResultNotified<ProjectMembersPayload> updateMemberRole(@PathVariable String projectId,
                                                                  @PathVariable String projectMemberId,
                                                                  @SessionAttribute UserVo user,
                                                                  @RequestBody UpdateMemberRoleRequest request) {
        ensureProjectAccess(projectId, user);
        Assert.notNull(request, "请求体不能为空");
        Assert.hasText(request.getRole(), "role 不能为空");
        projectService.updateProjectMemberRole(projectId, projectMemberId, ProjectMemberVo.Role.valueOf(request.getRole()));
        return members(projectId, user);
    }

    @GetMapping("/labels")
    public ResultNotified<ProjectLabelsPayload> labels(@PathVariable String projectId,
                                                       @SessionAttribute UserVo user) {
        ensureProjectAccess(projectId, user);
        ProjectLabelsPayload payload = new ProjectLabelsPayload();
        payload.setUsecaseLabels(toLabelSummaries(projectService.getLables(projectId, LableType.usecase)));
        payload.setSnapshotLabels(toLabelSummaries(projectService.getLables(projectId, LableType.snapshot)));
        payload.setCurrentUserRole(resolveUserRole(projectId, user));
        return new ResultNotified<>(true, "获取标签成功", payload);
    }

    @PostMapping("/labels/upsert")
    public ResultNotified<ProjectLabelsPayload> upsertLabel(@PathVariable String projectId,
                                                            @SessionAttribute UserVo user,
                                                            @RequestBody UpsertLabelRequest request) {
        ensureProjectAccess(projectId, user);
        Assert.notNull(request, "请求体不能为空");
        Assert.hasText(request.getType(), "type 不能为空");
        Assert.hasText(request.getName(), "name 不能为空");

        String color = StringUtils.hasText(request.getColor()) ? request.getColor() : "grey";
        LabelGroup.Label newLabel = new LabelGroup.Label(request.getName(), color);
        List<LabelGroupVo> groups = projectService.getLableGroup(projectId, LableType.valueOf(request.getType()));

        if (groups.isEmpty()) {
            LabelGroupVo groupVo = new LabelGroupVo();
            groupVo.setProjectid(projectId);
            groupVo.setType(request.getType());
            groupVo.setGroupName(request.getType() + " label");
            groupVo.setLabels(new LabelGroup.Label[]{newLabel});
            projectService.doSaveLabelGroup(groupVo);
        } else {
            LabelGroupVo groupVo = groups.get(0);
            List<LabelGroup.Label> labels = new ArrayList<>(Arrays.asList(groupVo.getLabels()));
            boolean updated = false;
            for (LabelGroup.Label label : labels) {
                if (request.getName().equals(label.getName())) {
                    label.setColor(color);
                    updated = true;
                    break;
                }
            }
            if (!updated) {
                labels.add(newLabel);
            }
            groupVo.setLabels(labels.toArray(new LabelGroup.Label[0]));
            projectService.doSaveLabelGroup(groupVo);
        }

        return labels(projectId, user);
    }

    @PostMapping("/labels/delete")
    public ResultNotified<ProjectLabelsPayload> deleteLabel(@PathVariable String projectId,
                                                            @SessionAttribute UserVo user,
                                                            @RequestBody DeleteLabelRequest request) {
        ensureProjectAccess(projectId, user);
        Assert.notNull(request, "请求体不能为空");
        Assert.hasText(request.getType(), "type 不能为空");
        Assert.hasText(request.getName(), "name 不能为空");

        List<LabelGroupVo> groups = projectService.getLableGroup(projectId, LableType.valueOf(request.getType()));
        Assert.isTrue(!groups.isEmpty(), "找不到标签组");

        LabelGroupVo groupVo = groups.get(0);
        List<LabelGroup.Label> labels = new ArrayList<>(Arrays.asList(groupVo.getLabels()));
        labels.removeIf(label -> request.getName().equals(label.getName()));
        groupVo.setLabels(labels.toArray(new LabelGroup.Label[0]));
        projectService.doSaveLabelGroup(groupVo);

        return labels(projectId, user);
    }

    private ProjectVo ensureProjectAccess(String projectId, UserVo user) {
        ProjectVo project = projectService.getProjectByProjectIdAndMemberId(projectId, user.getId());
        Assert.notNull(project, "找不到指定项目,或者您没有该项目的访问权限");
        return project;
    }

    private void applyProbeAlertSettings(App app, SaveAppRequest request) {
        boolean probeAlertEnabled = Boolean.TRUE.equals(request.getProbeAlertEnabled());
        boolean probeAlertOnOffline = Boolean.TRUE.equals(request.getProbeAlertOnOffline());
        boolean probeAlertOnRecovered = Boolean.TRUE.equals(request.getProbeAlertOnRecovered());
        boolean probeAlertOnOnline = Boolean.TRUE.equals(request.getProbeAlertOnOnline());
        if (probeAlertEnabled && !probeAlertOnOffline && !probeAlertOnRecovered && !probeAlertOnOnline) {
            probeAlertOnOffline = true;
            probeAlertOnRecovered = true;
        }
        Integer threshold = request.getProbeOfflineThresholdSeconds();
        if (threshold != null && threshold > 0 && threshold < 30) {
            threshold = 30;
        }

        app.setProbeAlertEnabled(probeAlertEnabled);
        app.setProbeAlertOnOffline(probeAlertOnOffline);
        app.setProbeAlertOnRecovered(probeAlertOnRecovered);
        app.setProbeAlertOnOnline(probeAlertOnOnline);
        app.setProbeOfflineThresholdSeconds(threshold);
        app.setProbeWebhookUrl(request.getProbeWebhookUrl());
    }

    private FrontendContextApiControl.AppSummary toAppSummary(AppVo app) {
        FrontendContextApiControl.AppSummary summary = new FrontendContextApiControl.AppSummary();
        summary.setId(app.getId());
        summary.setName(app.getName());
        summary.setSrcName(app.getSrcName());
        summary.setDescribe(app.getDescribe());
        summary.setRange(app.getRange());
        summary.setOnlineCount(app.getOnlineCount());
        summary.setCurrentVersion(app.getCurrentVersion());
        summary.setCurrentBranch(app.getCurrentBranch());
        summary.setCurrentCommitId(app.getCurrentCommitId());
        summary.setRepoConfigured(app.getRepoAddress() != null && !app.getRepoAddress().trim().isEmpty());
        summary.setProbeAlertEnabled(Boolean.TRUE.equals(app.getProbeAlertEnabled()));
        return summary;
    }

    private FrontendContextApiControl.UserSummary toUserSummary(UserVo user) {
        FrontendContextApiControl.UserSummary summary = new FrontendContextApiControl.UserSummary();
        summary.setId(user.getId());
        summary.setName(user.getName());
        summary.setNickname(user.getNickname());
        summary.setEmail(user.getEmail());
        summary.setHeader(user.getHeader());
        summary.setPhone(user.getPhone());
        summary.setReadme(user.getReadme());
        return summary;
    }

    private ProjectMemberSummary toProjectMemberSummary(ProjectMemberVo member) {
        ProjectMemberSummary summary = new ProjectMemberSummary();
        summary.setId(member.getId());
        summary.setProjectId(member.getProjectId());
        summary.setMemberId(member.getMemberId());
        summary.setMemberName(member.getMemberName());
        summary.setMemberEmail(member.getMemberEmail());
        summary.setRole(member.getRole() == null ? null : member.getRole().name());
        summary.setStar(Boolean.TRUE.equals(member.getStar()));
        summary.setDefaultProject(Boolean.TRUE.equals(member.getDefaultProject()));
        summary.setCreateTime(member.getCreateTime());
        return summary;
    }

    private List<LabelSummary> toLabelSummaries(List<LabelGroup.Label> labels) {
        List<LabelSummary> result = new ArrayList<>();
        for (LabelGroup.Label label : labels) {
            LabelSummary summary = new LabelSummary();
            summary.setName(label.getName());
            summary.setColor(label.getColor());
            result.add(summary);
        }
        return result;
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

    private UserVo findUser(List<UserVo> users, String userId) {
        for (UserVo user : users) {
            if (userId.equals(user.getId())) {
                return user;
            }
        }
        return null;
    }

    public static class AddMembersRequest {
        private List<String> userIds;

        public List<String> getUserIds() {
            return userIds;
        }

        public void setUserIds(List<String> userIds) {
            this.userIds = userIds;
        }
    }

    public static class UpdateMemberRoleRequest {
        private String role;

        public String getRole() {
            return role;
        }

        public void setRole(String role) {
            this.role = role;
        }
    }

    public static class UpsertLabelRequest {
        private String type;
        private String name;
        private String color;

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getColor() {
            return color;
        }

        public void setColor(String color) {
            this.color = color;
        }
    }

    public static class DeleteLabelRequest {
        private String type;
        private String name;

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }
    }

    public static class SaveAppRequest {
        private String name;
        private String srcName;
        private String range;
        private String describe;
        private String properties;
        private String currentVersion;
        private String currentBranch;
        private String currentCommitId;
        private Boolean probeAlertEnabled;
        private Integer probeOfflineThresholdSeconds;
        private String probeWebhookUrl;
        private Boolean probeAlertOnOnline;
        private Boolean probeAlertOnOffline;
        private Boolean probeAlertOnRecovered;

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getSrcName() { return srcName; }
        public void setSrcName(String srcName) { this.srcName = srcName; }
        public String getRange() { return range; }
        public void setRange(String range) { this.range = range; }
        public String getDescribe() { return describe; }
        public void setDescribe(String describe) { this.describe = describe; }
        public String getProperties() { return properties; }
        public void setProperties(String properties) { this.properties = properties; }
        public String getCurrentVersion() { return currentVersion; }
        public void setCurrentVersion(String currentVersion) { this.currentVersion = currentVersion; }
        public String getCurrentBranch() { return currentBranch; }
        public void setCurrentBranch(String currentBranch) { this.currentBranch = currentBranch; }
        public String getCurrentCommitId() { return currentCommitId; }
        public void setCurrentCommitId(String currentCommitId) { this.currentCommitId = currentCommitId; }
        public Boolean getProbeAlertEnabled() { return probeAlertEnabled; }
        public void setProbeAlertEnabled(Boolean probeAlertEnabled) { this.probeAlertEnabled = probeAlertEnabled; }
        public Integer getProbeOfflineThresholdSeconds() { return probeOfflineThresholdSeconds; }
        public void setProbeOfflineThresholdSeconds(Integer probeOfflineThresholdSeconds) { this.probeOfflineThresholdSeconds = probeOfflineThresholdSeconds; }
        public String getProbeWebhookUrl() { return probeWebhookUrl; }
        public void setProbeWebhookUrl(String probeWebhookUrl) { this.probeWebhookUrl = probeWebhookUrl; }
        public Boolean getProbeAlertOnOnline() { return probeAlertOnOnline; }
        public void setProbeAlertOnOnline(Boolean probeAlertOnOnline) { this.probeAlertOnOnline = probeAlertOnOnline; }
        public Boolean getProbeAlertOnOffline() { return probeAlertOnOffline; }
        public void setProbeAlertOnOffline(Boolean probeAlertOnOffline) { this.probeAlertOnOffline = probeAlertOnOffline; }
        public Boolean getProbeAlertOnRecovered() { return probeAlertOnRecovered; }
        public void setProbeAlertOnRecovered(Boolean probeAlertOnRecovered) { this.probeAlertOnRecovered = probeAlertOnRecovered; }
    }

    public static class DeleteAppRequest {
        private String password;

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }
    }

    public static class ProjectMembersPayload {
        private List<ProjectMemberSummary> members;
        private List<FrontendContextApiControl.UserSummary> availableUsers;
        private String currentUserRole;

        public List<ProjectMemberSummary> getMembers() {
            return members;
        }

        public void setMembers(List<ProjectMemberSummary> members) {
            this.members = members;
        }

        public List<FrontendContextApiControl.UserSummary> getAvailableUsers() {
            return availableUsers;
        }

        public void setAvailableUsers(List<FrontendContextApiControl.UserSummary> availableUsers) {
            this.availableUsers = availableUsers;
        }

        public String getCurrentUserRole() {
            return currentUserRole;
        }

        public void setCurrentUserRole(String currentUserRole) {
            this.currentUserRole = currentUserRole;
        }
    }

    public static class ProjectMemberSummary {
        private String id;
        private String projectId;
        private String memberId;
        private String memberName;
        private String memberEmail;
        private String role;
        private boolean star;
        private boolean defaultProject;
        private java.util.Date createTime;

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public String getProjectId() {
            return projectId;
        }

        public void setProjectId(String projectId) {
            this.projectId = projectId;
        }

        public String getMemberId() {
            return memberId;
        }

        public void setMemberId(String memberId) {
            this.memberId = memberId;
        }

        public String getMemberName() {
            return memberName;
        }

        public void setMemberName(String memberName) {
            this.memberName = memberName;
        }

        public String getMemberEmail() {
            return memberEmail;
        }

        public void setMemberEmail(String memberEmail) {
            this.memberEmail = memberEmail;
        }

        public String getRole() {
            return role;
        }

        public void setRole(String role) {
            this.role = role;
        }

        public boolean isStar() {
            return star;
        }

        public void setStar(boolean star) {
            this.star = star;
        }

        public boolean isDefaultProject() {
            return defaultProject;
        }

        public void setDefaultProject(boolean defaultProject) {
            this.defaultProject = defaultProject;
        }

        public java.util.Date getCreateTime() {
            return createTime;
        }

        public void setCreateTime(java.util.Date createTime) {
            this.createTime = createTime;
        }
    }

    public static class ProjectLabelsPayload {
        private List<LabelSummary> usecaseLabels;
        private List<LabelSummary> snapshotLabels;
        private String currentUserRole;

        public List<LabelSummary> getUsecaseLabels() {
            return usecaseLabels;
        }

        public void setUsecaseLabels(List<LabelSummary> usecaseLabels) {
            this.usecaseLabels = usecaseLabels;
        }

        public List<LabelSummary> getSnapshotLabels() {
            return snapshotLabels;
        }

        public void setSnapshotLabels(List<LabelSummary> snapshotLabels) {
            this.snapshotLabels = snapshotLabels;
        }

        public String getCurrentUserRole() {
            return currentUserRole;
        }

        public void setCurrentUserRole(String currentUserRole) {
            this.currentUserRole = currentUserRole;
        }
    }

    public static class LabelSummary {
        private String name;
        private String color;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getColor() {
            return color;
        }

        public void setColor(String color) {
            this.color = color;
        }
    }
}
