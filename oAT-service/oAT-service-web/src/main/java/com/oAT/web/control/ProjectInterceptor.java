package com.oAT.web.control;

import com.oAT.web.common.PaletteColors;
import com.oAT.web.service.AppService;
import com.oAT.web.service.ProjectService;
import com.oAT.web.service.entity.AppVo;
import com.oAT.web.service.entity.ProjectVo;
import com.oAT.web.service.entity.UserVo;
import org.jspecify.annotations.NonNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.List;


@Component
public class ProjectInterceptor implements HandlerInterceptor {

    @Autowired
    ProjectService projectService;
    @Autowired
    AppService appService;

    @Value("${ai.llm.enabled:true}")
    private boolean aiLlmEnabled;

    @Value("${ai.llm.timeout:120}")
    private int aiTimeout;

    @Override
    public boolean preHandle(HttpServletRequest request, @NonNull HttpServletResponse response, @NonNull Object handler)
            throws Exception {
        String projectId;
        UserVo user;
        ProjectVo project;
        Assert.isTrue(request.getRequestURI().startsWith("/p/"), "url must matching  start with  '/p/{projectId}'");
        projectId = request.getRequestURI().split("/")[2];
        Assert.isTrue(!projectId.trim().isEmpty(), "url must matching  start with '/p/{projectId}'");


        // 如果为共享请求，则跳过项目权限验证
        Boolean share = (Boolean) request.getAttribute("_share");
        if (share != null && share) {
            project = projectService.getProject(projectId);
            request.setAttribute("project", project);
            request.setAttribute("apps", appService.getAppList(projectId));
            request.setAttribute("aiLlmEnabled", aiLlmEnabled);
            setMascotPrimary(request, project);
            return true;
        }
        // 验证用户是否拥有项目权限
        user = (UserVo) request.getSession().getAttribute("user");
        Assert.notNull(user, "user must be logging state");
        project = projectService.getProjectByProjectIdAndMemberId(projectId, user.getId());
        if (project == null) {
            request.setAttribute("errorMessage", "找不到指定项目,或者您没有该项目的访问权限");
            request.getRequestDispatcher("/error/404").forward(request, response);
            return false;
        }
        List<AppVo> apps = appService.getAppList(projectId);
        request.setAttribute("apps", apps);
        request.setAttribute("project", project);
        request.setAttribute("aiLlmEnabled", aiLlmEnabled);
        setMascotPrimary(request, project);
        request.setAttribute("aiTimeout", aiTimeout);
        return true;
    }

    /**
     * 根据 projectId 和 projectName 计算与 AIInteractive 页面一致的 mascotPrimary 颜色，
     * 使所有页面的悬浮小人与 AIInteractive 页面的小人颜色统一。
     */
    private void setMascotPrimary(HttpServletRequest request, ProjectVo project) {
        String mascotPrimary = computeMascotPrimary(project.getId(), project.getName());
        request.setAttribute("mascotPrimary", mascotPrimary);
    }

    private String computeMascotPrimary(String projectId, String projectName) {
        int seed = positiveHash(projectId + ":" + projectName);
        return PaletteColors.pickPrimary(seed / 5 + 13);
    }

    private int positiveHash(String value) {
        int hash = value == null ? 0 : value.hashCode();
        if (hash == Integer.MIN_VALUE) {
            return 0;
        }
        return Math.abs(hash);
    }

}
