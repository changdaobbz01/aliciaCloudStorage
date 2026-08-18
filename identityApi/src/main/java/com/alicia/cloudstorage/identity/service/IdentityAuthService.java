package com.alicia.cloudstorage.identity.service;

import com.alicia.cloudstorage.identity.dto.IdentityLoginRequest;
import com.alicia.cloudstorage.identity.dto.IdentityLoginResponse;
import com.alicia.cloudstorage.identity.dto.IdentityUserResponse;
import com.alicia.cloudstorage.identity.entity.IdentityUser;
import com.alicia.cloudstorage.identity.entity.IdentityUserStatus;
import com.alicia.cloudstorage.identity.repository.IdentityUserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

@Service
@Transactional(readOnly = true)
public class IdentityAuthService {

    private final IdentityUserRepository identityUserRepository;
    private final PasswordEncoder passwordEncoder;
    private final IdentityTokenService identityTokenService;

    public IdentityAuthService(
            IdentityUserRepository identityUserRepository,
            PasswordEncoder passwordEncoder,
            IdentityTokenService identityTokenService
    ) {
        this.identityUserRepository = identityUserRepository;
        this.passwordEncoder = passwordEncoder;
        this.identityTokenService = identityTokenService;
    }

    public IdentityLoginResponse login(IdentityLoginRequest request) {
        LoginIdentifier loginIdentifier = normalizeLoginIdentifier(request);
        IdentityUser user = switch (loginIdentifier.type()) {
            case EMAIL -> identityUserRepository.findByEmail(loginIdentifier.value())
                    .orElseThrow(() -> new IllegalArgumentException("账号或密码不正确。"));
            case PHONE -> identityUserRepository.findByPhoneNumber(loginIdentifier.value())
                    .orElseThrow(() -> new IllegalArgumentException("账号或密码不正确。"));
        };

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new IllegalArgumentException("账号或密码不正确。");
        }

        if (user.getStatus() != IdentityUserStatus.ACTIVE) {
            throw new IdentityAuthException("当前账号已停用。");
        }

        return new IdentityLoginResponse(
                identityTokenService.createToken(user),
                IdentityUserResponse.from(user)
        );
    }

    public IdentityUserResponse me(String authorizationHeader) {
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

        return IdentityUserResponse.from(user);
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

    private LoginIdentifier normalizeLoginIdentifier(IdentityLoginRequest request) {
        String rawIdentifier = firstPresent(request.identifier(), request.email(), request.phoneNumber());
        if (rawIdentifier == null) {
            throw new IllegalArgumentException("请输入手机号或邮箱。");
        }

        String identifier = rawIdentifier.trim();
        if (identifier.contains("@")) {
            return new LoginIdentifier(LoginIdentifierType.EMAIL, normalizeEmail(identifier));
        }

        return new LoginIdentifier(LoginIdentifierType.PHONE, normalizePhoneNumber(identifier));
    }

    private String firstPresent(String... values) {
        for (String value : values) {
            if (value != null && !value.trim().isEmpty()) {
                return value;
            }
        }

        return null;
    }

    private String normalizeEmail(String value) {
        String email = value.trim().toLowerCase(Locale.ROOT);
        if (email.isEmpty() || email.length() > 320 || !email.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) {
            throw new IllegalArgumentException("请输入有效邮箱地址。");
        }

        return email;
    }

    private String normalizePhoneNumber(String value) {
        String phoneNumber = value.trim();
        if (!phoneNumber.matches("^1\\d{10}$")) {
            throw new IllegalArgumentException("请输入 11 位手机号。");
        }

        return phoneNumber;
    }

    private enum LoginIdentifierType {
        PHONE,
        EMAIL
    }

    private record LoginIdentifier(LoginIdentifierType type, String value) {
    }
}
