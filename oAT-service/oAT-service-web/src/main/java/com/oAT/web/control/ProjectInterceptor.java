package com.oAT.web.control;

import com.oAT.web.service.AppService;
import com.oAT.web.service.ProjectService;
import com.oAT.web.service.entity.AppVo;
import com.oAT.web.service.entity.ProjectVo;
import com.oAT.web.service.entity.UserVo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.net.URLEncoder;
import java.util.List;


@Component
public class ProjectInterceptor implements HandlerInterceptor {

    private static final String[] PRIMARY_COLORS = {"#5865f2", "#00b5ad", "#ff8a65", "#7e57c2", "#26a69a", "#42a5f5"};

    @Autowired
    ProjectService projectService;
    @Autowired
    AppService appService;

    @Value("${ai.llm.timeout:120}")
    private int aiTimeout;

    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        String projectId = null;
        UserVo user = null;
        ProjectVo project = null;
        Assert.isTrue(request.getRequestURI().startsWith("/p/"), "url must matching  start with  '/p/{projectId}'");
        projectId = request.getRequestURI().split("/")[2];
        Assert.isTrue(!projectId.trim().isEmpty(), "url must matching  start with '/p/{projectId}'");


        // 如果为共享请求，则跳过项目权限验证
        Boolean share = (Boolean) request.getAttribute("_share");
        if (share != null && share) {
            project = projectService.getProject(projectId);
            request.setAttribute("project", project);
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
        return pick(PRIMARY_COLORS, seed / 5 + 13);
    }

    private int positiveHash(String value) {
        int hash = value == null ? 0 : value.hashCode();
        if (hash == Integer.MIN_VALUE) {
            return 0;
        }
        return Math.abs(hash);
    }

    private String pick(String[] values, int seed) {
        if (values.length == 0) {
            return "";
        }
        return values[seed % values.length];
    }
}
