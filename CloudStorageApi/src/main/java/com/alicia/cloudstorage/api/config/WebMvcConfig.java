package com.alicia.cloudstorage.api.config;

import com.alicia.cloudstorage.api.auth.AdminAuthInterceptor;
import com.alicia.cloudstorage.api.auth.AuthInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private final AuthInterceptor authInterceptor;
    private final AdminAuthInterceptor adminAuthInterceptor;

    /**
     * 注入登录态与管理员权限拦截器。
     */
    public WebMvcConfig(AuthInterceptor authInterceptor, AdminAuthInterceptor adminAuthInterceptor) {
        this.authInterceptor = authInterceptor;
        this.adminAuthInterceptor = adminAuthInterceptor;
    }

    /**
     * 注册登录态和管理员权限拦截器，保护需要鉴权的接口。
     */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(adminAuthInterceptor)
                .addPathPatterns("/api/admin/**");

        registry.addInterceptor(authInterceptor)
                .addPathPatterns(
                        "/api/auth/me",
                        "/api/auth/profile",
                        "/api/auth/avatar",
                        "/api/auth/background",
                        "/api/auth/password",
                        "/api/storage/**"
                );
    }
}
