package com.oAT.web.config;

import com.oAT.web.control.AIInteractiveAccessInterceptor;
import com.oAT.web.control.LoginInterceptor;
import com.oAT.web.control.ProjectInterceptor;
import com.oAT.web.service.ResourceService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.io.File;
import java.util.List;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Autowired
    ProjectInterceptor projectInterceptor;
    @Autowired
    AIInteractiveAccessInterceptor aiInteractiveAccessInterceptor;
    @Autowired
    LoginInterceptor loginInterceptor;
    @Autowired
    ResourceService resourceService;
    @Autowired
    FrontendProperties frontendProperties;

    @Bean
    public FilterRegistrationBean<CorsFilter> frontendCoverageCorsFilter() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOriginPatterns(List.of("*"));
        configuration.setAllowedMethods(List.of("POST", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/projects/*/apps/*/coverage/frontend/report", configuration);
        source.registerCorsConfiguration("/api/projects/*/apps/*/coverage/universal/*/report", configuration);

        FilterRegistrationBean<CorsFilter> registration = new FilterRegistrationBean<>(new CorsFilter(source));
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return registration;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(loginInterceptor)
                .addPathPatterns("/**")
                .excludePathPatterns("/", "/login", "/doLogin", "/register", "/doRegister",
                        "/client/**", "/r/**", "/error", "/share/**", "/share/api/**",
                        "/webhook/**",
                        "/api/projects/*/apps/*/coverage/frontend/report",
                        "/api/projects/*/apps/*/coverage/universal/*/report",
                        "/api/auth/login", "/api/auth/register", "/api/auth/me");
        registry.addInterceptor(aiInteractiveAccessInterceptor).addPathPatterns("/api/projects/*/ai/**");
        registry.addInterceptor(projectInterceptor).addPathPatterns("/p/**");
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/projects/*/apps/*/coverage/frontend/report")
                .allowedOriginPatterns("*")
                .allowedMethods("POST", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true)
                .maxAge(3600);
        registry.addMapping("/api/projects/*/apps/*/coverage/universal/*/report")
                .allowedOriginPatterns("*")
                .allowedMethods("POST", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true)
                .maxAge(3600);
        registry.addMapping("/api/**")
                .allowedOrigins(frontendProperties.getAllowedOrigins())
                .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true)
                .maxAge(3600);
        registry.addMapping("/share/api/**")
                .allowedOrigins(frontendProperties.getAllowedOrigins())
                .allowedMethods("GET", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true)
                .maxAge(3600);
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        File resourceRoot = new File(resourceService.getCacheRoot());
        if (!resourceRoot.exists()) {
            resourceRoot.mkdirs();
        }
        registry.addResourceHandler("/r/**")
                .addResourceLocations(resourceRoot.toURI().toString());
    }
}
