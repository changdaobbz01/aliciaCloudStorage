package com.alicia.cloudstorage.identity.controller;

import com.alicia.cloudstorage.identity.dto.ChangePasswordRequest;
import com.alicia.cloudstorage.identity.dto.IdentityLoginRequest;
import com.alicia.cloudstorage.identity.dto.IdentityLoginResponse;
import com.alicia.cloudstorage.identity.dto.IdentityMessageResponse;
import com.alicia.cloudstorage.identity.dto.IdentityUserResponse;
import com.alicia.cloudstorage.identity.dto.RequestEmailRegistrationCodeRequest;
import com.alicia.cloudstorage.identity.dto.VerifyEmailRegistrationRequest;
import com.alicia.cloudstorage.identity.service.IdentityAuthService;
import com.alicia.cloudstorage.identity.service.IdentityEmailRegistrationService;
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

    private final IdentityAuthService identityAuthService;
    private final IdentityEmailRegistrationService identityEmailRegistrationService;

    public IdentityAuthController(
            IdentityAuthService identityAuthService,
            IdentityEmailRegistrationService identityEmailRegistrationService
    ) {
        this.identityAuthService = identityAuthService;
        this.identityEmailRegistrationService = identityEmailRegistrationService;
    }

    @PostMapping("/login")
    public IdentityLoginResponse login(@Valid @RequestBody IdentityLoginRequest request) {
        return identityAuthService.login(request);
    }

    @GetMapping("/me")
    public IdentityUserResponse me(@RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        return identityAuthService.me(authorization);
    }

    @PutMapping("/password")
    public IdentityMessageResponse changePassword(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @Valid @RequestBody ChangePasswordRequest request
    ) {
        identityAuthService.changePassword(authorization, request);
        return new IdentityMessageResponse("密码修改成功。");
    }

    @PostMapping("/register/email-code")
    public IdentityMessageResponse requestEmailRegistrationCode(
            @Valid @RequestBody RequestEmailRegistrationCodeRequest request,
            HttpServletRequest servletRequest
    ) {
        identityEmailRegistrationService.requestRegistrationCode(
                request.email(),
                servletRequest.getRemoteAddr(),
                servletRequest.getHeader(HttpHeaders.USER_AGENT)
        );
        return new IdentityMessageResponse("如果邮箱可用，验证码会发送到该邮箱。");
    }

    @PostMapping("/register/verify")
    public IdentityLoginResponse verifyEmailRegistration(@Valid @RequestBody VerifyEmailRegistrationRequest request) {
        return identityEmailRegistrationService.verifyRegistration(request);
    }
}
