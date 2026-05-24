package com.oAT.web.control;

import com.oAT.web.config.FrontendProperties;
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
    FrontendProperties frontendProperties;


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
        return "redirect:" + frontendProperties.url("/projects");
    }

    @RequestMapping("/project/create")
    public String createProjectView() {
        return "redirect:" + frontendProperties.url("/projects?create=1");
    }

    @RequestMapping("/p/{projectId}/edit")
    public String openEditProjectView(@PathVariable String projectId, @SessionAttribute UserVo user, Model model) {
        return "redirect:" + frontendProperties.url("/projects?edit=" + projectId);
    }

    @RequestMapping("/p/{projectId}/delete")
    public String openDeleteProjectView(@PathVariable String projectId, @SessionAttribute UserVo user, Model model) {
        return "redirect:" + frontendProperties.url("/projects?delete=" + projectId);
    }


    @RequestMapping("/project/doCreate")
    @ResponseBody
    public ResultNotified<String> doCreateProject(@SessionAttribute UserVo user, String name, String describe) {
        ProjectService.CreateProjectParam param = new ProjectService.CreateProjectParam(name, describe, user.getId());
        param.userName = user.getName();
        ProjectVo vo = projectService.createProject(param);
        doLog(SystemLogService.Action.addProject, "创建了新应用", user, vo);
        return new ResultNotified<>(true, "项目创建成功", "/p/" + vo.getId() + "/home");
    }

    @RequestMapping("/p/{projectId}/doEdit")
    @ResponseBody
    public ResultNotified<String> doUpdateProject(@PathVariable String projectId, @SessionAttribute UserVo user, ProjectVo projectVo) {
        try {
            projectVo.setId(projectId);
            projectVo = projectService.updateProject(projectVo);
            doLog(SystemLogService.Action.addProject, "修改了新应用", user, projectVo);
            return new ResultNotified<>(true, "项目信息修改成功", "/p/" + projectVo.getId() + "/edit");
        } catch (Exception e) {
            logger.warn("更新项目信息失败, projectId={}", projectId, e);
            return new ResultNotified<>(false, e.getMessage() == null ? "项目信息修改失败" : e.getMessage());
        }
    }

    private void doLog(SystemLogService.Action action, String actionMessage, UserVo user, ProjectVo vo) {
        SystemLog log = new SystemLog();
        log.setTitle(String.format("%s %s <a href='/projects?edit=%s'>%s</a>", user.getName(), actionMessage, vo.getId(), vo.getName()));
        log.setMessage(vo.getDescribe());
        log.setUserId(user.getId());
        log.setUserName(user.getName());
        log.setProjectId(vo.getId());
        log.setAction(action.toString());
        systemLogService.addLog(log);
    }

    @RequestMapping("/p/{projectId}/doDelete")
    @ResponseBody
    public ResultNotified<String> doDeleteProject(@SessionAttribute UserVo user,
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
            return new ResultNotified<>(true, "项目已成功移除", "/projects");
        } catch (UserOperationException e) {
            if (logger.isDebugEnabled()) {
                logger.error("项目删除失败", e);
            }
            return new ResultNotified<>(false, e.getMessage());
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
        return "redirect:" + frontendProperties.url("/projects");
    }

    @RequestMapping("/p/{projectId}/member/list")
    public String openProjectMemberView(@PathVariable String projectId, Model model, @SessionAttribute UserVo user) {
        return "redirect:" + frontendProperties.url("/p/" + projectId + "/members");
    }


    @RequestMapping("/p/{projectId}/member/add")
    public ResultNotified<String> addProjectMember(@PathVariable String projectId, String[] ids) {
        for (String id : ids) {
            projectService.addProjectMember(projectId, id);
        }
        return new ResultNotified<>(true, "用户添加成功");
    }


    @RequestMapping("/p/{projectId}/member/delete")
    public ResultNotified<String> removeProjectMember(@PathVariable String projectId, String projectMemberId) {
        projectService.deleteProjectMember(projectId, projectMemberId);
        return new ResultNotified<>(true, "用户移除成功");
    }

    /**
     * 修改成员角色
     */
    @RequestMapping("/p/{projectId}/member/updateRole")
    public ResultNotified<String> updateProjectMemberRole(@PathVariable String projectId, String projectMemberId, String role) {
        projectService.updateProjectMemberRole(projectId, projectMemberId, ProjectMemberVo.Role.valueOf(role));
        return new ResultNotified<>(true, "权限修改成功");
    }

    @RequestMapping("/p/{projectId}/label")
    public String openLableView(@PathVariable String projectId, Model model, @SessionAttribute UserVo user) {
        return "redirect:" + frontendProperties.url("/p/" + projectId + "/labels");
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
        return "redirect:" + frontendProperties.url("/p/" + projectId + "/labels");
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
        return "redirect:" + frontendProperties.url("/p/" + projectId + "/labels");
    }

}
