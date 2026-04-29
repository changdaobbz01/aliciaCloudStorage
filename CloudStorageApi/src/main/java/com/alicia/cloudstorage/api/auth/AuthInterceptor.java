package com.alicia.cloudstorage.api.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class AuthInterceptor implements HandlerInterceptor {

    private final AuthService authService;

    /**
     * 注入鉴权服务，用于解析请求头中的登录令牌。
     */
    public AuthInterceptor(AuthService authService) {
        this.authService = authService;
    }

    /**
     * 在请求进入业务控制器前校验登录令牌，并写入当前用户编号。
     */
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        Long userId = authService.requireUserId(request.getHeader("Authorization"));
        request.setAttribute(AuthRequestAttributes.CURRENT_USER_ID, userId);
        return true;
    }
}
