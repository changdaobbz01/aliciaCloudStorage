package com.alicia.cloudstorage.identity.service;

import com.alicia.cloudstorage.identity.entity.IdentityUserRole;
import org.springframework.stereotype.Service;

import java.util.Locale;

@Service
public class IdentityUserInputNormalizer {

    public String normalizeEmail(String value) {
        if (value == null) {
            throw new IllegalArgumentException("邮箱不能为空。");
        }

        String email = value.trim().toLowerCase(Locale.ROOT);
        if (email.isEmpty() || email.length() > 320 || !email.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) {
            throw new IllegalArgumentException("请输入有效邮箱地址。");
        }

        return email;
    }

    public String normalizeOptionalEmail(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }

        return normalizeEmail(value);
    }

    public String normalizePhoneNumber(String value) {
        if (value == null) {
            throw new IllegalArgumentException("请输入 11 位手机号。");
        }

        String phoneNumber = value.trim();
        if (!phoneNumber.matches("^1\\d{10}$")) {
            throw new IllegalArgumentException("请输入 11 位手机号。");
        }

        return phoneNumber;
    }

    public String normalizeOptionalPhoneNumber(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }

        return normalizePhoneNumber(value);
    }

    public String normalizeNickname(String value) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException("昵称不能为空。");
        }

        return value.trim();
    }

    public String normalizeAvatarUrl(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }

        return value.trim();
    }

    public IdentityUserRole normalizeRole(String role) {
        if (role == null || role.isBlank()) {
            return IdentityUserRole.USER;
        }

        try {
            return IdentityUserRole.valueOf(role.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("角色只能是 ADMIN 或 USER。");
        }
    }
}
