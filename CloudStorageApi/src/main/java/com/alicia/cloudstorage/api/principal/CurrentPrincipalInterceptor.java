package com.alicia.cloudstorage.api.principal;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class CurrentPrincipalInterceptor implements HandlerInterceptor {

    private final CurrentPrincipalService currentPrincipalService;

    /**
     * 注入当前主体服务，用于通过 Identity 校验登录令牌。
     */
    public CurrentPrincipalInterceptor(CurrentPrincipalService currentPrincipalService) {
        this.currentPrincipalService = currentPrincipalService;
    }

    /**
     * 在请求进入业务控制器前解析当前主体，并写入请求上下文。
     */
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        CurrentPrincipal principal = currentPrincipalService.requirePrincipal(request.getHeader("Authorization"));
        request.setAttribute(PrincipalRequestAttributes.CURRENT_PRINCIPAL, principal);
        request.setAttribute(PrincipalRequestAttributes.CURRENT_USER_ID, principal.userId());
        return true;
    }
}
