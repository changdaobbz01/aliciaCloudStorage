package com.alicia.cloudstorage.identity.service;

import com.alicia.cloudstorage.identity.entity.IdentityUser;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class IdentityCredentialService {

    private static final int MIN_PASSWORD_LENGTH = 6;

    private final PasswordEncoder passwordEncoder;

    public IdentityCredentialService(PasswordEncoder passwordEncoder) {
        this.passwordEncoder = passwordEncoder;
    }

    public boolean matches(String rawPassword, String passwordHash) {
        return passwordEncoder.matches(rawPassword, passwordHash);
    }

    public String encodeInitialPassword(String password) {
        String normalizedPassword = normalizePassword(password, "密码不能为空。");
        requireMinLength(normalizedPassword, "密码长度至少为 6 位。");
        return passwordEncoder.encode(normalizedPassword);
    }

    public void changePassword(IdentityUser user, String oldPassword, String newPassword) {
        String normalizedOldPassword = normalizePassword(oldPassword, "旧密码不能为空。");
        String normalizedNewPassword = normalizePassword(newPassword, "新密码不能为空。");

        if (!passwordEncoder.matches(normalizedOldPassword, user.getPasswordHash())) {
            throw new IllegalArgumentException("旧密码不正确。");
        }

        requireMinLength(normalizedNewPassword, "新密码长度至少为 6 位。");

        if (normalizedOldPassword.equals(normalizedNewPassword)) {
            throw new IllegalArgumentException("新密码不能与旧密码相同。");
        }

        setPasswordAndInvalidateTokens(user, normalizedNewPassword);
    }

    public void resetPassword(IdentityUser user, String newPassword) {
        String normalizedNewPassword = normalizePassword(newPassword, "新密码不能为空。");
        requireMinLength(normalizedNewPassword, "新密码长度至少为 6 位。");

        if (passwordEncoder.matches(normalizedNewPassword, user.getPasswordHash())) {
            throw new IllegalArgumentException("新密码不能与当前密码相同。");
        }

        setPasswordAndInvalidateTokens(user, normalizedNewPassword);
    }

    private String normalizePassword(String value, String errorMessage) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(errorMessage);
        }

        return value;
    }

    private void requireMinLength(String value, String errorMessage) {
        if (value.length() < MIN_PASSWORD_LENGTH) {
            throw new IllegalArgumentException(errorMessage);
        }
    }

    private void setPasswordAndInvalidateTokens(IdentityUser user, String password) {
        user.setPasswordHash(passwordEncoder.encode(password));
        invalidateTokens(user);
    }

    private void invalidateTokens(IdentityUser user) {
        long currentVersion = user.getTokenVersion() == null ? 0L : user.getTokenVersion();
        user.setTokenVersion(currentVersion + 1);
    }
}
