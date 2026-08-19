package com.alicia.cloudstorage.identity.service;

import com.alicia.cloudstorage.identity.dto.AdminResetUserPasswordRequest;
import com.alicia.cloudstorage.identity.entity.IdentityUser;
import com.alicia.cloudstorage.identity.repository.IdentityUserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class IdentityAdminUserService {

    private final IdentityPrincipalService identityPrincipalService;
    private final IdentityUserRepository identityUserRepository;
    private final PasswordEncoder passwordEncoder;

    public IdentityAdminUserService(
            IdentityPrincipalService identityPrincipalService,
            IdentityUserRepository identityUserRepository,
            PasswordEncoder passwordEncoder
    ) {
        this.identityPrincipalService = identityPrincipalService;
        this.identityUserRepository = identityUserRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public void resetUserPassword(
            String authorizationHeader,
            Long targetUserId,
            AdminResetUserPasswordRequest request
    ) {
        IdentityUser adminUser = identityPrincipalService.requireAdminUser(authorizationHeader);
        if (adminUser.getId().equals(targetUserId)) {
            throw new IllegalArgumentException("当前接口仅用于重置其他用户密码，请使用修改密码功能。");
        }

        IdentityUser targetUser = identityUserRepository.findById(targetUserId)
                .orElseThrow(() -> new IllegalArgumentException("用户不存在。"));
        String newPassword = normalizePassword(request.newPassword(), "新密码不能为空。");

        if (newPassword.length() < 6) {
            throw new IllegalArgumentException("新密码长度至少为 6 位。");
        }

        if (passwordEncoder.matches(newPassword, targetUser.getPasswordHash())) {
            throw new IllegalArgumentException("新密码不能与当前密码相同。");
        }

        targetUser.setPasswordHash(passwordEncoder.encode(newPassword));
        invalidateTokens(targetUser);
        identityUserRepository.save(targetUser);
    }

    private String normalizePassword(String value, String errorMessage) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(errorMessage);
        }

        return value;
    }

    private void invalidateTokens(IdentityUser user) {
        long currentVersion = user.getTokenVersion() == null ? 0L : user.getTokenVersion();
        user.setTokenVersion(currentVersion + 1);
    }
}
