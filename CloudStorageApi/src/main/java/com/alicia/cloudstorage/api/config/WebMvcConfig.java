package com.alicia.cloudstorage.api.config;

import com.alicia.cloudstorage.api.principal.AdminPrincipalInterceptor;
import com.alicia.cloudstorage.api.principal.CurrentPrincipalInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private final CurrentPrincipalInterceptor currentPrincipalInterceptor;
    private final AdminPrincipalInterceptor adminPrincipalInterceptor;

    /**
     * 注入当前主体与管理员主体拦截器。
     */
    public WebMvcConfig(
            CurrentPrincipalInterceptor currentPrincipalInterceptor,
            AdminPrincipalInterceptor adminPrincipalInterceptor
    ) {
        this.currentPrincipalInterceptor = currentPrincipalInterceptor;
        this.adminPrincipalInterceptor = adminPrincipalInterceptor;
    }

    /**
     * 注册当前主体和管理员主体拦截器，保护需要登录态的接口。
     */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(adminPrincipalInterceptor)
                .addPathPatterns("/api/admin/**");

        registry.addInterceptor(currentPrincipalInterceptor)
                .addPathPatterns(
                        "/api/cloud-profile/me",
                        "/api/cloud-profile/avatar",
                        "/api/cloud-profile/background",
                        "/api/share-links/**",
                        "/api/storage/**"
                );
    }
}
