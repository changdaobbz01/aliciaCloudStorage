package com.alicia.cloudstorage.api.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class AdminAuthInterceptor implements HandlerInterceptor {

    private final CurrentPrincipalService currentPrincipalService;

    /**
     * 注入鉴权服务，用于管理员接口的权限校验。
     */
    public AdminAuthInterceptor(CurrentPrincipalService currentPrincipalService) {
        this.currentPrincipalService = currentPrincipalService;
    }

    /**
     * 在管理员接口执行前校验令牌，并确认当前用户具备管理员角色。
     */
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        CurrentPrincipal principal = currentPrincipalService.requireAdminPrincipal(request.getHeader("Authorization"));
        request.setAttribute(AuthRequestAttributes.CURRENT_PRINCIPAL, principal);
        request.setAttribute(AuthRequestAttributes.CURRENT_USER_ID, principal.userId());
        return true;
    }
}
