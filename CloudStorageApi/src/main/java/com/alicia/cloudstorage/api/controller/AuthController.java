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
import com.alicia.cloudstorage.api.service.EmailRegistrationService;
import com.alicia.cloudstorage.api.service.UserAccountService;
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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.net.URI;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private static final String SIGNED_MEDIA_REDIRECT_CACHE_CONTROL = "private, max-age=240";

    private final UserAccountService userAccountService;
    private final EmailRegistrationService emailRegistrationService;

    /**
     * 注入账号业务服务，供登录和个人资料接口复用。
     */
    public AuthController(
            UserAccountService userAccountService,
            EmailRegistrationService emailRegistrationService
    ) {
        this.userAccountService = userAccountService;
        this.emailRegistrationService = emailRegistrationService;
    }

    /**
     * 使用手机号和密码执行登录，并返回新的访问令牌。
     */
    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        return userAccountService.login(request);
    }

    @PostMapping("/register/email-code")
    public ApiMessageResponse requestEmailRegistrationCode(
            @Valid @RequestBody RequestEmailRegistrationCodeRequest request,
            HttpServletRequest servletRequest
    ) {
        emailRegistrationService.requestRegistrationCode(
                request.email(),
                servletRequest.getRemoteAddr(),
                servletRequest.getHeader(HttpHeaders.USER_AGENT)
        );
        return new ApiMessageResponse("如果邮箱可用，验证码会发送到该邮箱。");
    }

    @PostMapping("/register/verify")
    public LoginResponse verifyEmailRegistration(@Valid @RequestBody VerifyEmailRegistrationRequest request) {
        return emailRegistrationService.verifyRegistration(request);
    }

    /**
     * 查询当前登录用户的基础资料信息。
     */
    @GetMapping("/me")
    public UserProfileResponse me(
            @RequestAttribute(AuthRequestAttributes.CURRENT_PRINCIPAL) CurrentPrincipal principal
    ) {
        return userAccountService.getCurrentUser(principal.userId());
    }

    /**
     * 更新当前登录用户的手机号、昵称和头像地址。
     */
    @PutMapping("/profile")
    public UserProfileResponse updateProfile(
            @RequestAttribute(AuthRequestAttributes.CURRENT_PRINCIPAL) CurrentPrincipal principal,
            @Valid @RequestBody UpdateProfileRequest request
    ) {
        return userAccountService.updateCurrentUser(principal.userId(), request);
    }

    /**
     * 上传当前登录用户的本地头像图片。
     */
    @PostMapping(value = "/avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public UserProfileResponse uploadAvatar(
            @RequestAttribute(AuthRequestAttributes.CURRENT_PRINCIPAL) CurrentPrincipal principal,
            @RequestPart("file") MultipartFile file
    ) {
        return userAccountService.uploadCurrentUserAvatar(principal.userId(), file);
    }

    /**
     * 读取用户上传到 COS 的头像图片，供前端头像组件展示。
     */
    @GetMapping("/avatar/{userId}")
    public ResponseEntity<Void> getAvatar(@PathVariable Long userId) {
        String accessUrl = userAccountService.resolveUserAvatarAccessUrl(userId).url();
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
            @Valid @RequestBody ChangePasswordRequest request
    ) {
        userAccountService.changePassword(principal.userId(), request);
        return new ApiMessageResponse("密码修改成功。");
    }
}
