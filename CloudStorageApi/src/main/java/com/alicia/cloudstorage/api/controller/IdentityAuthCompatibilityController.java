package com.alicia.cloudstorage.api.controller;

import com.alicia.cloudstorage.api.auth.AuthRequestAttributes;
import com.alicia.cloudstorage.api.auth.CurrentPrincipal;
import com.alicia.cloudstorage.api.dto.ApiMessageResponse;
import com.alicia.cloudstorage.api.dto.ChangePasswordRequest;
import com.alicia.cloudstorage.api.dto.LoginRequest;
import com.alicia.cloudstorage.api.dto.LoginResponse;
import com.alicia.cloudstorage.api.dto.RequestEmailRegistrationCodeRequest;
import com.alicia.cloudstorage.api.dto.UpdateProfileRequest;
import com.alicia.cloudstorage.api.dto.UserProfileResponse;
import com.alicia.cloudstorage.api.dto.VerifyEmailRegistrationRequest;
import com.alicia.cloudstorage.api.service.IdentityAvatarCompatibilityService;
import com.alicia.cloudstorage.api.service.IdentityEmailRegistrationCompatibilityService;
import com.alicia.cloudstorage.api.service.IdentityPasswordCompatibilityService;
import com.alicia.cloudstorage.api.service.IdentityProfileCompatibilityService;
import com.alicia.cloudstorage.api.service.IdentitySessionCompatibilityService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.net.URI;

@RestController
@RequestMapping("/api/auth")
public class IdentityAuthCompatibilityController {

    private static final String SIGNED_MEDIA_REDIRECT_CACHE_CONTROL = "private, max-age=240";

    private final IdentityAvatarCompatibilityService identityAvatarCompatibilityService;
    private final IdentityEmailRegistrationCompatibilityService identityEmailRegistrationCompatibilityService;
    private final IdentityPasswordCompatibilityService identityPasswordCompatibilityService;
    private final IdentityProfileCompatibilityService identityProfileCompatibilityService;
    private final IdentitySessionCompatibilityService identitySessionCompatibilityService;

    /**
     * 保留旧版 /api/auth/** 合约，同时将身份读写委托给 identityApi。
     */
    public IdentityAuthCompatibilityController(
            IdentityAvatarCompatibilityService identityAvatarCompatibilityService,
            IdentityEmailRegistrationCompatibilityService identityEmailRegistrationCompatibilityService,
            IdentityPasswordCompatibilityService identityPasswordCompatibilityService,
            IdentityProfileCompatibilityService identityProfileCompatibilityService,
            IdentitySessionCompatibilityService identitySessionCompatibilityService
    ) {
        this.identityAvatarCompatibilityService = identityAvatarCompatibilityService;
        this.identityEmailRegistrationCompatibilityService = identityEmailRegistrationCompatibilityService;
        this.identityPasswordCompatibilityService = identityPasswordCompatibilityService;
        this.identityProfileCompatibilityService = identityProfileCompatibilityService;
        this.identitySessionCompatibilityService = identitySessionCompatibilityService;
    }

    /**
     * 使用手机号、邮箱或账号标识登录，并返回旧版客户端兼容的用户资料。
     */
    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        return identitySessionCompatibilityService.login(request);
    }

    @PostMapping("/register/email-code")
    public ApiMessageResponse requestEmailRegistrationCode(
            @Valid @RequestBody RequestEmailRegistrationCodeRequest request,
            HttpServletRequest servletRequest
    ) {
        identityEmailRegistrationCompatibilityService.requestRegistrationCode(
                request.email(),
                servletRequest.getRemoteAddr(),
                servletRequest.getHeader(HttpHeaders.USER_AGENT)
        );
        return new ApiMessageResponse("如果邮箱可用，验证码会发送到该邮箱。");
    }

    @PostMapping("/register/verify")
    public LoginResponse verifyEmailRegistration(@Valid @RequestBody VerifyEmailRegistrationRequest request) {
        return identityEmailRegistrationCompatibilityService.verifyRegistration(request);
    }

    /**
     * 查询当前登录用户，并合并 identity 账号资料与云盘 profile。
     */
    @GetMapping("/me")
    public UserProfileResponse me(
            @RequestAttribute(AuthRequestAttributes.CURRENT_PRINCIPAL) CurrentPrincipal principal,
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization
    ) {
        return identityProfileCompatibilityService.getCurrentUser(authorization);
    }

    /**
     * 更新当前登录用户的身份资料，并返回云盘兼容响应。
     */
    @PutMapping("/profile")
    public UserProfileResponse updateProfile(
            @RequestAttribute(AuthRequestAttributes.CURRENT_PRINCIPAL) CurrentPrincipal principal,
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @Valid @RequestBody UpdateProfileRequest request
    ) {
        return identityProfileCompatibilityService.updateCurrentUser(authorization, request);
    }

    /**
     * 上传当前登录用户的本地头像图片。
     */
    @PostMapping(value = "/avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public UserProfileResponse uploadAvatar(
            @RequestAttribute(AuthRequestAttributes.CURRENT_PRINCIPAL) CurrentPrincipal principal,
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @RequestPart("file") MultipartFile file
    ) {
        return identityAvatarCompatibilityService.uploadCurrentUserAvatar(authorization, file);
    }

    /**
     * 读取用户上传到 COS 的头像图片，供前端头像组件展示。
     */
    @GetMapping("/avatar/{userId}")
    public ResponseEntity<Void> getAvatar(@PathVariable Long userId) {
        String accessUrl = identityAvatarCompatibilityService.resolveUserAvatarAccessUrl(userId).url();
        return ResponseEntity.status(HttpStatus.FOUND)
                .header(HttpHeaders.CACHE_CONTROL, SIGNED_MEDIA_REDIRECT_CACHE_CONTROL)
                .location(URI.create(accessUrl))
                .build();
    }

    /**
     * 校验旧密码后，为当前登录用户更新新的密码。
     */
    @PutMapping("/password")
    public ApiMessageResponse changePassword(
            @RequestAttribute(AuthRequestAttributes.CURRENT_PRINCIPAL) CurrentPrincipal principal,
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @Valid @RequestBody ChangePasswordRequest request
    ) {
        identityPasswordCompatibilityService.changePassword(authorization, request);
        return new ApiMessageResponse("密码修改成功。");
    }
}
