package com.oAT.web.control;

import com.oAT.web.service.entity.UserVo;
import org.springframework.http.MediaType;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import static com.oAT.web.common.UtilJson.JSON_MAPPER;

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
                if (isApiRequest(request)) {
                    writeUnauthorizedApiResponse(response);
                    return false;
                }
                String redirect = buildRedirectPath(request);
                redirect = URLEncoder.encode(redirect, StandardCharsets.UTF_8);
                response.sendRedirect("/login?redirect=" + redirect);
                return false;
            } catch (IOException e) {
                throw new RuntimeException("登录重定向失败!", e);
            }
        }
        return true;
    }

    private boolean isApiRequest(HttpServletRequest request) {
        String uri = request.getRequestURI();
        if (uri != null && uri.startsWith("/api/")) {
            return true;
        }
        String accept = request.getHeader("Accept");
        return accept != null && accept.contains(MediaType.APPLICATION_JSON_VALUE);
    }

    private String buildRedirectPath(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String queryString = request.getQueryString();
        if ("/index.html".equals(uri)) {
            return normalizeRedirect(request.getParameter("redirect"));
        }
        String redirect = StringUtils.hasText(uri) ? uri : "/projects";
        if (StringUtils.hasText(queryString)) {
            redirect += "?" + queryString;
        }
        return normalizeRedirect(redirect);
    }

    static String normalizeRedirect(String redirect) {
        if (!StringUtils.hasText(redirect)) {
            return "/projects";
        }

        String normalized = redirect.trim();
        for (int i = 0; i < 20; i++) {
            String decoded = URLDecoder.decode(normalized, StandardCharsets.UTF_8);
            if (decoded.equals(normalized)) {
                break;
            }
            normalized = decoded;
        }

        if (normalized.startsWith("http://") || normalized.startsWith("https://") || normalized.startsWith("//")) {
            return "/projects";
        }
        if (!normalized.startsWith("/")) {
            return "/projects";
        }
        if (normalized.startsWith("/login") || normalized.startsWith("/index.html")) {
            String nested = extractRedirectParameter(normalized);
            return StringUtils.hasText(nested) ? normalizeRedirect(nested) : "/projects";
        }
        return normalized;
    }

    private static String extractRedirectParameter(String value) {
        int redirectIndex = value.indexOf("redirect=");
        if (redirectIndex < 0) {
            return null;
        }
        String nested = value.substring(redirectIndex + "redirect=".length());
        int ampIndex = nested.indexOf('&');
        if (ampIndex >= 0) {
            nested = nested.substring(0, ampIndex);
        }
        return nested;
    }

    private void writeUnauthorizedApiResponse(HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(JSON_MAPPER.writeValueAsString(new ApiUnauthorizedBody()));
    }

    private static final class ApiUnauthorizedBody {
        public final boolean result = false;
        public final boolean success = false;
        public final String message = "未登录或登录已过期";
        public final String errorMessage = "AUTH_REQUIRED";
    }

}
