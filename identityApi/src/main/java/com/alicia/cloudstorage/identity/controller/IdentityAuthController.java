package com.alicia.cloudstorage.identity.controller;

import com.alicia.cloudstorage.identity.dto.ChangePasswordRequest;
import com.alicia.cloudstorage.identity.dto.IdentityLoginRequest;
import com.alicia.cloudstorage.identity.dto.IdentityLoginResponse;
import com.alicia.cloudstorage.identity.dto.IdentityLogoutRequest;
import com.alicia.cloudstorage.identity.dto.IdentityMessageResponse;
import com.alicia.cloudstorage.identity.dto.IdentityRefreshTokenRequest;
import com.alicia.cloudstorage.identity.dto.IdentityUserResponse;
import com.alicia.cloudstorage.identity.dto.RequestEmailRegistrationCodeRequest;
import com.alicia.cloudstorage.identity.dto.UpdateIdentityProfileRequest;
import com.alicia.cloudstorage.identity.dto.VerifyEmailRegistrationRequest;
import com.alicia.cloudstorage.identity.service.IdentityAuthService;
import com.alicia.cloudstorage.identity.service.IdentityEmailRegistrationService;
import com.alicia.cloudstorage.identity.service.IdentityPasswordService;
import com.alicia.cloudstorage.identity.service.IdentityProfileService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/identity/auth")
public class IdentityAuthController {

    private static final String FORWARDED_FOR_HEADER = "X-Forwarded-For";

    private final IdentityAuthService identityAuthService;
    private final IdentityEmailRegistrationService identityEmailRegistrationService;
    private final IdentityPasswordService identityPasswordService;
    private final IdentityProfileService identityProfileService;

    public IdentityAuthController(
            IdentityAuthService identityAuthService,
            IdentityEmailRegistrationService identityEmailRegistrationService,
            IdentityPasswordService identityPasswordService,
            IdentityProfileService identityProfileService
    ) {
        this.identityAuthService = identityAuthService;
        this.identityEmailRegistrationService = identityEmailRegistrationService;
        this.identityPasswordService = identityPasswordService;
        this.identityProfileService = identityProfileService;
    }

    @PostMapping("/login")
    public IdentityLoginResponse login(
            @Valid @RequestBody IdentityLoginRequest request,
            HttpServletRequest servletRequest,
            @RequestHeader(value = FORWARDED_FOR_HEADER, required = false) String forwardedFor
    ) {
        return identityAuthService.login(
                request,
                resolveClientAddress(servletRequest, forwardedFor),
                servletRequest.getHeader(HttpHeaders.USER_AGENT)
        );
    }

    @GetMapping("/me")
    public IdentityUserResponse me(@RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        return identityAuthService.me(authorization);
    }

    @PostMapping("/token/refresh")
    public IdentityLoginResponse refreshToken(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @RequestBody(required = false) IdentityRefreshTokenRequest request,
            HttpServletRequest servletRequest,
            @RequestHeader(value = FORWARDED_FOR_HEADER, required = false) String forwardedFor
    ) {
        return identityAuthService.refreshToken(
                authorization,
                request,
                resolveClientAddress(servletRequest, forwardedFor),
                servletRequest.getHeader(HttpHeaders.USER_AGENT)
        );
    }

    @PostMapping("/logout")
    public IdentityMessageResponse logout(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @RequestBody(required = false) IdentityLogoutRequest request
    ) {
        identityAuthService.logout(authorization, request);
        return new IdentityMessageResponse("已退出登录。");
    }

    @PutMapping("/profile")
    public IdentityUserResponse updateProfile(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @Valid @RequestBody UpdateIdentityProfileRequest request
    ) {
        return identityProfileService.updateProfile(authorization, request);
    }

    @PutMapping("/password")
    public IdentityMessageResponse changePassword(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @Valid @RequestBody ChangePasswordRequest request
    ) {
        identityPasswordService.changePassword(authorization, request);
        return new IdentityMessageResponse("密码修改成功。");
    }

    @PostMapping("/register/email-code")
    public IdentityMessageResponse requestEmailRegistrationCode(
            @Valid @RequestBody RequestEmailRegistrationCodeRequest request,
            HttpServletRequest servletRequest,
            @RequestHeader(value = FORWARDED_FOR_HEADER, required = false) String forwardedFor
    ) {
        identityEmailRegistrationService.requestRegistrationCode(
                request.email(),
                resolveClientAddress(servletRequest, forwardedFor),
                servletRequest.getHeader(HttpHeaders.USER_AGENT)
        );
        return new IdentityMessageResponse("如果邮箱可用，验证码会发送到该邮箱。");
    }

    @PostMapping("/register/verify")
    public IdentityLoginResponse verifyEmailRegistration(
            @Valid @RequestBody VerifyEmailRegistrationRequest request,
            HttpServletRequest servletRequest,
            @RequestHeader(value = FORWARDED_FOR_HEADER, required = false) String forwardedFor
    ) {
        return identityEmailRegistrationService.verifyRegistration(
                request,
                resolveClientAddress(servletRequest, forwardedFor),
                servletRequest.getHeader(HttpHeaders.USER_AGENT)
        );
    }

    private String resolveClientAddress(HttpServletRequest servletRequest, String forwardedFor) {
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",", 2)[0].trim();
        }

        return servletRequest.getRemoteAddr();
    }
}
