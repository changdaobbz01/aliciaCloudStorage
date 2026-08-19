package com.alicia.cloudstorage.identity.service;

import com.alicia.cloudstorage.identity.entity.IdentityUser;
import com.alicia.cloudstorage.identity.entity.IdentityUserRole;
import com.alicia.cloudstorage.identity.entity.IdentityUserStatus;
import com.alicia.cloudstorage.identity.repository.IdentityUserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class IdentityPrincipalService {

    private final IdentityUserRepository identityUserRepository;
    private final IdentityTokenService identityTokenService;

    public IdentityPrincipalService(
            IdentityUserRepository identityUserRepository,
            IdentityTokenService identityTokenService
    ) {
        this.identityUserRepository = identityUserRepository;
        this.identityTokenService = identityTokenService;
    }

    public IdentityUser requireActiveUser(String authorizationHeader) {
        IdentityTokenService.TokenClaims tokenClaims = identityTokenService.parseToken(extractBearerToken(authorizationHeader));
        IdentityUser user = identityUserRepository.findById(tokenClaims.userId())
                .orElseThrow(() -> new IdentityAuthException("登录用户不存在。"));

        long currentTokenVersion = user.getTokenVersion() == null ? 0L : user.getTokenVersion();
        if (currentTokenVersion != tokenClaims.tokenVersion()) {
            throw new IdentityAuthException("登录状态已失效。");
        }

        if (user.getStatus() != IdentityUserStatus.ACTIVE) {
            throw new IdentityAuthException("当前账号已停用。");
        }

        return user;
    }

    public IdentityUser requireAdminUser(String authorizationHeader) {
        IdentityUser user = requireActiveUser(authorizationHeader);

        if (user.getRole() != IdentityUserRole.ADMIN) {
            throw new IdentityAuthException("当前接口仅允许管理员访问。");
        }

        return user;
    }

    private String extractBearerToken(String authorizationHeader) {
        if (authorizationHeader == null || authorizationHeader.isBlank()) {
            throw new IdentityAuthException("请先登录。");
        }

        String prefix = "Bearer ";
        if (!authorizationHeader.regionMatches(true, 0, prefix, 0, prefix.length())) {
            throw new IdentityAuthException("登录凭证格式不正确。");
        }

        String token = authorizationHeader.substring(prefix.length()).trim();
        if (token.isEmpty()) {
            throw new IdentityAuthException("请先登录。");
        }

        return token;
    }
}
