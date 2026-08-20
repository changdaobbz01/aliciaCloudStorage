package com.alicia.cloudstorage.api.auth;

import com.alicia.cloudstorage.api.identity.IdentityAuthGateway;
import com.alicia.cloudstorage.api.identity.IdentityAccount;
import org.springframework.stereotype.Service;

@Service
public class CurrentPrincipalService {

    private final IdentityAuthGateway identityAuthGateway;

    public CurrentPrincipalService(IdentityAuthGateway identityAuthGateway) {
        this.identityAuthGateway = identityAuthGateway;
    }

    /**
     * 从请求头中的 Bearer Token 解析当前登录用户编号。
     */
    public Long requireUserId(String authorization) {
        return requirePrincipal(authorization).userId();
    }

    /**
     * 校验普通用户令牌，并返回轻量当前登录主体。
     */
    public CurrentPrincipal requirePrincipal(String authorization) {
        IdentityAccount account = identityAuthGateway.me(authorization);
        return new CurrentPrincipal(account.id(), account.role());
    }

    /**
     * 校验管理员令牌，并返回轻量当前登录主体。
     */
    public CurrentPrincipal requireAdminPrincipal(String authorization) {
        CurrentPrincipal principal = requirePrincipal(authorization);

        if (!principal.isAdmin()) {
            throw new AuthException("当前接口仅允许管理员访问。");
        }

        return principal;
    }

}
