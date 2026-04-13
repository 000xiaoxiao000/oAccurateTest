package com.oAT.web.control;

import com.oAT.web.service.AppService;
import com.oAT.web.service.ClientSessionService;
import com.oAT.web.service.ProjectService;
import com.oAT.web.service.GitService;
import com.oAT.web.service.entity.AppVo;
import com.oAT.web.service.entity.ProjectMemberVo;
import com.oAT.web.service.entity.UserVo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.SessionAttribute;

import java.util.HashMap;
import java.util.List;
import java.util.Map;


@Controller
@RequestMapping("/p/{projectId}")
public class manageAppCodeControl {
    @Autowired
    AppService appService;
    @Autowired
    ProjectService projectService;
    @Autowired
    ClientSessionService sessionService;
    @Autowired
    GitService gitService;

    @RequestMapping("/manageAppCode")
    public String manageAppCode(@PathVariable String projectId, Model model, @SessionAttribute UserVo user) {
        List<AppVo> list = appService.getAppList(projectId);
        model.addAttribute("apps", list);
        model.addAttribute("defaultApp", list.isEmpty() ? null : list.get(0));
        for (AppVo appVo : list) {
            appVo.setOnlineCount(sessionService.getOnlineSessionsByAppId(appVo.getId()).size());
        }

        model.addAttribute("loginNameRole", getLoginUserRole(projectId, user.getName()));
        return "/settings/manageAppCode";
    }

    @RequestMapping("/app/{appId}/repository")
    public String repositoryConfig(@PathVariable String projectId, @PathVariable String appId,
                                   Model model, @SessionAttribute UserVo user) {
        AppVo app = appService.getApp(appId);
        model.addAttribute("app", app);
        model.addAttribute("loginNameRole", getLoginUserRole(projectId, user.getName()));
        return "/settings/codeRepositoryConfig";
    }

    @RequestMapping("/app/{appId}/repository/save")
    @ResponseBody
    public com.oAT.web.control.entity.ResultNotified saveRepositoryConfig(@PathVariable String projectId, @PathVariable String appId, AppVo appVo) {
        AppVo existingApp = appService.getApp(appId);
        existingApp.setRepoAddress(appVo.getRepoAddress());
        existingApp.setRepoUserName(appVo.getRepoUserName());
        existingApp.setRepoPassword(appVo.getRepoPassword());
        // existingApp.setRepoBranch(appVo.getRepoBranch());
        appService.updateApp(projectId, existingApp);
        return new com.oAT.web.control.entity.ResultNotified(true, "配置保存成功");
    }

    @RequestMapping("/app/git/branches")
    @ResponseBody
    public Map<String, Object> getGitBranches(String repoUrl, String username, String password) {
        Map<String, Object> result = new HashMap<>();
        try {
            List<String> branches = gitService.getRemoteBranches(repoUrl, username, password);
            result.put("success", true);
            result.put("branches", branches);
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", e.getMessage());
        }
        return result;
    }

    @RequestMapping("/app/{appId}/git/branches")
    @ResponseBody
    public Map<String, Object> getGitBranchesByApp(@PathVariable String appId) {
        Map<String, Object> result = new HashMap<>();
        try {
            AppVo app = appService.getApp(appId);
            if (app == null) {
                result.put("success", false);
                result.put("message", "应用不存在");
                return result;
            }
            if (app.getRepoAddress() == null || app.getRepoAddress().isEmpty()) {
                result.put("success", false);
                result.put("message", "Git仓库地址未配置");
                return result;
            }
            List<String> branches = gitService.getRemoteBranches(app.getRepoAddress(), app.getRepoUserName(), app.getRepoPassword());
            result.put("success", true);
            result.put("branches", branches);
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", e.getMessage());
        }
        return result;
    }

    private String getLoginUserRole(String projectId, String loginName) {
        List<ProjectMemberVo> members = projectService.getProjectMembers(projectId);

        // 登录用户权限，原则是最小权限（访客）
        String loginNameRole = "visitor";
        for (ProjectMemberVo member : members) {
            if (loginName.equals(member.getMemberName())) {
                loginNameRole = String.valueOf(member.getRole());
            }
        }
        return loginNameRole;
    }
}
