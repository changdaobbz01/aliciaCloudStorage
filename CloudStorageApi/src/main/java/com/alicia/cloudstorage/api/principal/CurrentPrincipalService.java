package com.alicia.cloudstorage.api.principal;

import com.alicia.cloudstorage.api.identity.IdentityAuthGateway;
import com.alicia.cloudstorage.api.identity.IdentityUserSnapshot;
import org.springframework.stereotype.Service;

@Service
public class CurrentPrincipalService {

    private final IdentityAuthGateway identityAuthGateway;

    public CurrentPrincipalService(IdentityAuthGateway identityAuthGateway) {
        this.identityAuthGateway = identityAuthGateway;
    }

    /**
     * 通过 Identity 校验普通用户令牌，并返回轻量当前登录主体。
     */
    public CurrentPrincipal requirePrincipal(String authorization) {
        IdentityUserSnapshot account = identityAuthGateway.me(authorization);
        return new CurrentPrincipal(account.id(), account.role());
    }

    /**
     * 通过 Identity 校验管理员令牌，并返回轻量当前登录主体。
     */
    public CurrentPrincipal requireAdminPrincipal(String authorization) {
        CurrentPrincipal principal = requirePrincipal(authorization);

        if (!principal.isAdmin()) {
            throw new PrincipalAccessException("当前接口仅允许管理员访问。");
        }

        return principal;
    }

}
