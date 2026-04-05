package com.oAT.web.control;

import com.oAT.web.service.entity.UserVo;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URLEncoder;

@Component
public class LoginInterceptor implements HandlerInterceptor {
    static Logger logger = LoggerFactory.getLogger(LoginInterceptor.class);

    @Override
    public boolean preHandle(HttpServletRequest request, @NotNull HttpServletResponse response, @NotNull Object handler) {
        // 如果为共享请求，则跳转权登陆权限验证
        Boolean share = (Boolean) request.getAttribute("_share");
        if (share != null && share) {
            return true;
        }
        UserVo user = (UserVo) request.getSession().getAttribute("user");
        if (user == null) {
            try {
                String redirect = request.getRequestURL().toString();
                if (request.getQueryString() != null) {
                    redirect += "?" + request.getQueryString();
                }
                redirect = URLEncoder.encode(redirect, "UTF-8");
                response.sendRedirect("/login?redirect=" + redirect);
                return false;
            } catch (IOException e) {
                throw new RuntimeException("登录重定向失败!", e);
            }
        }
        return true;
    }

}
