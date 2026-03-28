package com.oAT.web.control;

import com.oAT.web.service.AppService;
import com.oAT.web.service.ProjectService;
import com.oAT.web.service.entity.AppVo;
import com.oAT.web.service.entity.ProjectVo;
import com.oAT.web.service.entity.UserVo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.net.URLEncoder;
import java.util.List;


@Component
public class ProjectInterceptor implements HandlerInterceptor {

    @Autowired
    ProjectService projectService;
    @Autowired
    AppService appService;

    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        String projectId = null;
        UserVo user = null;
        ProjectVo project = null;
        Assert.isTrue(request.getRequestURI().startsWith("/p/"), "url must matching  start with  '/p/{projectId}'");
        projectId = request.getRequestURI().split("/")[2];
        Assert.isTrue(!projectId.trim().isEmpty(), "url must matching  start with  '/p/{projectId}'");


        // 如果为共享请求，则跳过项目权限验证
        Boolean share = (Boolean) request.getAttribute("_share");
        if (share != null && share) {
            request.setAttribute("project", projectService.getProject(projectId));
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
        return true;
    }

}
