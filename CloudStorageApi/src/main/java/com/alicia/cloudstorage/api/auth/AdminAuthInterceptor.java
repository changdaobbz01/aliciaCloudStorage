package com.alicia.cloudstorage.api.auth;

import com.alicia.cloudstorage.api.entity.SysUser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class AdminAuthInterceptor implements HandlerInterceptor {

    private final AuthService authService;

    /**
     * 注入鉴权服务，用于管理员接口的权限校验。
     */
    public AdminAuthInterceptor(AuthService authService) {
        this.authService = authService;
    }

    /**
     * 在管理员接口执行前校验令牌，并确认当前用户具备管理员角色。
     */
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        SysUser adminUser = authService.requireAdminUser(request.getHeader("Authorization"));
        request.setAttribute(AuthRequestAttributes.CURRENT_USER_ID, adminUser.getId());
        return true;
    }
}
