package com.oAT.web.config;

import com.oAT.web.control.LoginInterceptor;
import com.oAT.web.control.ProjectInterceptor;
import com.oAT.web.service.ResourceService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.io.File;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Autowired
    ProjectInterceptor projectInterceptor;
    @Autowired
    LoginInterceptor loginInterceptor;
    @Autowired
    ResourceService resourceService;

    // 拦截器配置
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        //自定义拦截器，添加拦截路径和排除拦截路径
        // addPathPatterns 用于添加拦截规则
        // excludePathPatterns 用于排除拦截
        registry.addInterceptor(loginInterceptor)
                .addPathPatterns("/**")
                .excludePathPatterns("/", "/login", "/doLogin", "/register",
                        "/doRegister", "/client/**", "/r/**", "/error",
                        "/css/**", "/images/**", "/js/**", "/share/**");
        //项目节点拦截
        registry.addInterceptor(projectInterceptor).addPathPatterns("/p/**");
    }

    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        //设置首页
        registry.addViewController("/").setViewName("forward:/myProjects");
        registry.setOrder(Ordered.HIGHEST_PRECEDENCE);
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        //  自定义资源配置
        File resourceRoot = new File(resourceService.getCacheRoot());
        if (!resourceRoot.exists()) {
            resourceRoot.mkdirs();
        }
        registry.addResourceHandler("/r/**")
                .addResourceLocations(resourceRoot.toURI().toString());
    }

}
