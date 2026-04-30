package com.alicia.cloudstorage.api.auth;

import com.alicia.cloudstorage.api.entity.SysUser;
import com.alicia.cloudstorage.api.entity.UserRole;
import com.alicia.cloudstorage.api.entity.UserStatus;
import com.alicia.cloudstorage.api.repository.SysUserRepository;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    private final TokenService tokenService;
    private final SysUserRepository sysUserRepository;

    public AuthService(TokenService tokenService, SysUserRepository sysUserRepository) {
        this.tokenService = tokenService;
        this.sysUserRepository = sysUserRepository;
    }

    /**
     * 从请求头中的 Bearer Token 解析当前登录用户编号。
     */
    public Long requireUserId(String authorization) {
        return requireUser(authorization).getId();
    }

    /**
     * 校验普通用户令牌，并返回对应的有效用户实体。
     */
    public SysUser requireUser(String authorization) {
        String token = resolveBearerToken(authorization);
        TokenService.TokenClaims tokenClaims = tokenService.parseToken(token);
        SysUser user = findActiveUserById(tokenClaims.userId());
        long currentTokenVersion = user.getTokenVersion() == null ? 0L : user.getTokenVersion();

        if (tokenClaims.tokenVersion() != currentTokenVersion) {
            throw new AuthException("登录状态已失效，请重新登录。");
        }

        return user;
    }

    /**
     * 校验管理员令牌，确保当前用户角色为管理员。
     */
    public SysUser requireAdminUser(String authorization) {
        SysUser user = requireUser(authorization);

        if (user.getRole() != UserRole.ADMIN) {
            throw new AuthException("当前接口仅允许管理员访问。");
        }

        return user;
    }

    /**
     * 根据用户编号查找有效用户，并拦截已被停用的账号。
     */
    public SysUser findActiveUserById(Long userId) {
        SysUser user = sysUserRepository.findById(userId)
                .orElseThrow(() -> new AuthException("用户不存在。"));

        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new AuthException("当前账号已停用。");
        }

        return user;
    }

    /**
     * 从 Authorization 请求头里提取 Bearer Token。
     */
    private String resolveBearerToken(String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            throw new AuthException("缺少 Bearer Token。");
        }

        String token = authorization.substring("Bearer ".length()).trim();
        if (token.isBlank()) {
            throw new AuthException("Token 不能为空。");
        }

        return token;
    }
}
