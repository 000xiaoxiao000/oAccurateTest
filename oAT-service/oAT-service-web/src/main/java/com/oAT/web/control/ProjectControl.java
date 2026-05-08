package com.oAT.web.control;

import com.oAT.web.control.entity.ResultNotified;
import com.oAT.web.esDao.entity.LabelGroup;
import com.oAT.web.esDao.entity.SystemLog;
import com.oAT.web.exceptions.UserOperationException;
import com.oAT.web.service.*;
import com.oAT.web.service.entity.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.SessionAttribute;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 项目管理控制器
 */
@Controller
public class ProjectControl {

    @Autowired
    ProjectService projectService;
    @Autowired
    UserService userService;
    static final Logger logger = LoggerFactory.getLogger(ProjectControl.class);
    @Autowired
    private SystemLogService systemLogService;

    @Autowired
    AppService appService;

    @Autowired
    ClientSessionService sessionService;

    @Autowired
    private SnapshotService snapshotService;

    /**
     * 打开我的项目列表
     *
     * @param model
     * @return
     */
    @RequestMapping("/myProjects")
    public String openMyProjectsView(@SessionAttribute UserVo user, Model model) {
        //该条件需由登录拦截器进行验证
        List<ProjectVo> projects = projectService.findProjectByMemberId(user.getId());
        model.addAttribute("projects", projects);
        return "/project/myProjectList";
    }

    @RequestMapping("/project/create")
    public String createProjectView() {
        return "/project/createProject";
    }

    @RequestMapping("/p/{projectId}/edit")
    public String openEditProjectView(@PathVariable String projectId, @SessionAttribute UserVo user, Model model) {
        ProjectVo projectVo = projectService.getProjectByProjectIdAndMemberId(projectId, user.getId());
        String loginName = user.getName();
        List<AppVo> apps = appService.getAppList(projectId);
        model.addAttribute("defaultApp", apps.isEmpty() ? null : apps.get(0));
        List<ProjectMemberVo> members = projectService.getProjectMembers(projectId);

        // 登录用户权限，原则是最小权限（访客）
        String loginNameRole = "visitor";
        for (ProjectMemberVo member : members) {
            if(loginName.equals(member.getMemberName())){
                loginNameRole = String.valueOf(member.getRole());
            }
        }

        model.addAttribute("loginNameRole", loginNameRole);
        model.addAttribute("projectInfo", projectVo);
        return "/settings/editProject";
    }

    @RequestMapping("/p/{projectId}/delete")
    public String openDeleteProjectView(@PathVariable String projectId, @SessionAttribute UserVo user, Model model) {
        String loginName = user.getName();
        List<AppVo> apps = appService.getAppList(projectId);
        model.addAttribute("defaultApp", apps.isEmpty() ? null : apps.get(0));
        List<ProjectMemberVo> members = projectService.getProjectMembers(projectId);

        // 登录用户权限，原则是最小权限（访客）
        String loginNameRole = "visitor";
        for (ProjectMemberVo member : members) {
            if(loginName.equals(member.getMemberName())){
                loginNameRole = String.valueOf(member.getRole());
            }
        }

        model.addAttribute("loginNameRole", loginNameRole);
        return "/settings/deleteProject";
    }

    /**
     * 打开项目主页
     *
     * @return
     */
    @RequestMapping("/p/{projectId}/home")
    public String openHomeView(@PathVariable String projectId, @SessionAttribute UserVo user, Model model) {
        List<SystemLogVo> logs = systemLogService.getSystemLog(projectId, 0, 20);
        model.addAttribute("logs", logs);

        // 打开应用列表
        List<AppVo> list = appService.getAppList(projectId);
        for (AppVo appVo : list) {
            appVo.setOnlineCount(sessionService.getOnlineSessionsByAppId(appVo.getId()).size());
        }
        model.addAttribute("apps", list);

        // 我的快照
        List<SnapshotVo> snapshots = snapshotService.findSnapshot(projectId, user.getId());
        model.addAttribute("snapshots", snapshots);
        return "/project/home";
    }

    @RequestMapping("/project/doCreate")
    @ResponseBody
    public ResultNotified doCreateProject(@SessionAttribute UserVo user, String name, String describe) {
        ProjectService.CreateProjectParam param = new ProjectService.CreateProjectParam(name, describe, user.getId());
        param.userName = user.getName();
        ProjectVo vo = projectService.createProject(param);
        doLog(SystemLogService.Action.addProject, "创建了新应用", user, vo);
        return new ResultNotified(true, "项目创建成功", "/p/" + vo.getId() + "/home");
    }

    @RequestMapping("/p/{projectId}/doEdit")
    @ResponseBody
    public ResultNotified doUpdateProject(@PathVariable String projectId, @SessionAttribute UserVo user, ProjectVo projectVo) {
        try {
            projectVo.setId(projectId);
            projectVo = projectService.updateProject(projectVo);
            doLog(SystemLogService.Action.addProject, "修改了新应用", user, projectVo);
            return new ResultNotified(true, "项目信息修改成功", "/p/" + projectVo.getId() + "/edit");
        } catch (Exception e) {
            logger.warn("更新项目信息失败, projectId={}", projectId, e);
            return new ResultNotified(false, e.getMessage() == null ? "项目信息修改失败" : e.getMessage());
        }
    }

    private void doLog(SystemLogService.Action action, String actionMessage, UserVo user, ProjectVo vo) {
        SystemLog log = new SystemLog();
        log.setTitle(String.format("%s %s <a href='edit'>%s</a>", user.getName(), actionMessage, vo.getName()));
        log.setMessage(vo.getDescribe());
        log.setUserId(user.getId());
        log.setUserName(user.getName());
        log.setProjectId(vo.getId());
        log.setAction(action.toString());
        systemLogService.addLog(log);
    }

    @RequestMapping("/p/{projectId}/doDelete")
    @ResponseBody
    public ResultNotified doDeleteProject(@SessionAttribute UserVo user,
                                  @PathVariable String projectId,
                                  String password) {
        try {
            projectService.deleteProject(projectId, user.getId(), password);
            SystemLog log = new SystemLog();
            log.setTitle(String.format("%s 删除了项目 项目id:%s ", user.getName(), projectId));
            log.setUserId(user.getId());
            log.setUserName(user.getName());
            log.setProjectId(projectId);
            log.setAction(SystemLogService.Action.deleteProject.toString());
            systemLogService.addLog(log);
            return new ResultNotified(true, "项目已成功移除", "/myProjects");
        } catch (UserOperationException e) {
            if (logger.isDebugEnabled()) {
                logger.error("项目删除失败", e);
            }
            return new ResultNotified(false, e.getMessage());
        }
    }


    /**
     * 获取项目菜单
     *
     * @param user
     * @param model
     * @return
     */
    @RequestMapping("/project/projectMenu")
    public String getProjectMenu(@SessionAttribute UserVo user, Model model) {
        List<ProjectVo> projects = projectService.findProjectByMemberId(user.getId());
        model.addAttribute("projects", projects);
        return "/project/projectMenu";
    }

    @RequestMapping("/p/{projectId}/member/list")
    public String openProjectMemberView(@PathVariable String projectId, Model model, @SessionAttribute UserVo user) {
        String loginName = user.getName();

        List<ProjectMemberVo> members = projectService.getProjectMembers(projectId);
        List<UserVo> users = userService.getAllUser();

        // 添加成员中请选择用户
        for (ProjectMemberVo member : members) {
            for (UserVo user1 : users.toArray(new UserVo[0])) {
                if (member.getMemberId().equals(user1.getId())) {
                    member.setMemberName(user1.getName());
                    member.setMemberEmail(user1.getEmail());
                    // 移除已添加的成员
                    users.remove(user1);
                }
            }
        }

        // 登录用户权限，原则是最小权限（访客）
        String loginNameRole = "visitor";
        for (ProjectMemberVo member : members) {
            if(loginName.equals(member.getMemberName())){
                loginNameRole = String.valueOf(member.getRole());
            }
        }

        model.addAttribute("users", users);
        model.addAttribute("members", members);
        model.addAttribute("loginNameRole", loginNameRole);
        return "/settings/projectMember";
    }


    @RequestMapping("/p/{projectId}/member/add")
    public ResultNotified addProjectMember(@PathVariable String projectId, String[] ids) {
        for (String id : ids) {
            projectService.addProjectMember(projectId, id);
        }
        return new ResultNotified(true, "用户添加成功");
    }


    @RequestMapping("/p/{projectId}/member/delete")
    public ResultNotified removeProjectMember(@PathVariable String projectId, String projectMemberId) {
        projectService.deleteProjectMember(projectId, projectMemberId);
        return new ResultNotified(true, "用户移除成功");
    }

    /**
     * 修改成员角色
     */
    @RequestMapping("/p/{projectId}/member/updateRole")
    public ResultNotified updateProjectMemberRole(@PathVariable String projectId, String projectMemberId, String role) {
        projectService.updateProjectMemberRole(projectId, projectMemberId, ProjectMemberVo.Role.valueOf(role));
        return new ResultNotified(true, "权限修改成功");
    }

    @RequestMapping("/p/{projectId}/label")
    public String openLableView(@PathVariable String projectId, Model model, @SessionAttribute UserVo user) {
        List<LabelGroup.Label> snapshotLables = projectService.getLables(projectId, LableType.snapshot);
        List<LabelGroup.Label> usecaseLables = projectService.getLables(projectId, LableType.usecase);

        String loginName = user.getName();
        List<AppVo> apps = appService.getAppList(projectId);
        model.addAttribute("defaultApp", apps.isEmpty() ? null : apps.get(0));
        List<ProjectMemberVo> members = projectService.getProjectMembers(projectId);

        // 登录用户权限，原则是最小权限（访客）
        String loginNameRole = "visitor";
        for (ProjectMemberVo member : members) {
            if(loginName.equals(member.getMemberName())){
                loginNameRole = String.valueOf(member.getRole());
            }
        }

        model.addAttribute("loginNameRole", loginNameRole);

        model.addAttribute("snapshotLables", snapshotLables);
        model.addAttribute("usecaseLables", usecaseLables);
        return "/settings/labelList";
    }

    @RequestMapping("/p/{projectId}/label/add")
    public String addLabel(@PathVariable String projectId, String type, String name, String color) {
        Assert.notNull(type, "参数'type'不能为空");
        Assert.notNull(name, "参数'name'不能为空");
        if (!StringUtils.hasText(color)) {
            color = "grey"; // 默认灰色
        }
        Assert.notNull(color, "参数'color'不能为空");

        List<LabelGroupVo> group = projectService.getLableGroup(projectId, LableType.valueOf(type));
        LabelGroup.Label newLabel = new LabelGroup.Label(name, color);
        if (group.isEmpty()) {// 第一次添加
            LabelGroupVo groupvo = new LabelGroupVo();
            groupvo.setProjectid(projectId);
            groupvo.setType(type);
            groupvo.setGroupName(type + " label");
            groupvo.setLabels(new LabelGroup.Label[]{newLabel});
            projectService.doSaveLabelGroup(groupvo);
        } else {
            // 新增 或修改
            LabelGroupVo groupvo = group.get(0);
            List<LabelGroup.Label> list = new ArrayList<>();
            list.addAll(Arrays.asList(groupvo.getLabels()));
            // 变更颜色
            for (LabelGroup.Label label : list) {
                if (label.getName().equals(newLabel.getName())) {
                    label.setColor(newLabel.getColor());
                    break;
                }
            }
            // 添加新的标签
            if (!list.contains(newLabel)) {
                list.add(newLabel);
            }
            groupvo.setLabels(list.toArray(new LabelGroup.Label[0]));
            projectService.doSaveLabelGroup(groupvo);
        }
        return "redirect:/p/" + projectId + "/label";
    }
    // 删除标签
    @RequestMapping("/p/{projectId}/label/delete")
    public String addLabel(@PathVariable String projectId, String type, String name) {
        Assert.notNull(type, "参数'type'不能为空");
        Assert.notNull(name, "参数'name'不能为空");
        List<LabelGroupVo> group = projectService.getLableGroup(projectId, LableType.valueOf(type));
        Assert.isTrue(!group.isEmpty(), String.format("删除标签失败，找不到标签组 projectId=%s,type=%s", projectId, type));

        LabelGroupVo groupvo = group.get(0);
        List<LabelGroup.Label> list = new ArrayList<>();
        list.addAll(Arrays.asList(groupvo.getLabels()));

        for (LabelGroup.Label label : groupvo.getLabels()) {
            if (label.getName().equals(name)) {
                list.remove(label);
                break;
            }
        }
        groupvo.setLabels(list.toArray(new LabelGroup.Label[0]));
        projectService.doSaveLabelGroup(groupvo);
        return "redirect:/p/" + projectId + "/label";
    }

}
