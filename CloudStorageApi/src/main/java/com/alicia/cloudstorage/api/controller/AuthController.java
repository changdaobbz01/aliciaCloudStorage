package com.alicia.cloudstorage.api.controller;

import com.alicia.cloudstorage.api.auth.AuthRequestAttributes;
import com.alicia.cloudstorage.api.dto.ApiMessageResponse;
import com.alicia.cloudstorage.api.dto.ChangePasswordRequest;
import com.alicia.cloudstorage.api.dto.LoginRequest;
import com.alicia.cloudstorage.api.dto.LoginResponse;
import com.alicia.cloudstorage.api.dto.UpdateProfileRequest;
import com.alicia.cloudstorage.api.dto.UserProfileResponse;
import com.alicia.cloudstorage.api.service.UserAccountService;
import jakarta.validation.Valid;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
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

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final UserAccountService userAccountService;

    /**
     * 注入账号业务服务，供登录和个人资料接口复用。
     */
    public AuthController(UserAccountService userAccountService) {
        this.userAccountService = userAccountService;
    }

    /**
     * 使用手机号和密码执行登录，并返回新的访问令牌。
     */
    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        return userAccountService.login(request);
    }

    /**
     * 查询当前登录用户的基础资料信息。
     */
    @GetMapping("/me")
    public UserProfileResponse me(@RequestAttribute(AuthRequestAttributes.CURRENT_USER_ID) Long userId) {
        return userAccountService.getCurrentUser(userId);
    }

    /**
     * 更新当前登录用户的手机号、昵称和头像地址。
     */
    @PutMapping("/profile")
    public UserProfileResponse updateProfile(
            @RequestAttribute(AuthRequestAttributes.CURRENT_USER_ID) Long userId,
            @Valid @RequestBody UpdateProfileRequest request
    ) {
        return userAccountService.updateCurrentUser(userId, request);
    }

    /**
     * 上传当前登录用户的本地头像图片。
     */
    @PostMapping(value = "/avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public UserProfileResponse uploadAvatar(
            @RequestAttribute(AuthRequestAttributes.CURRENT_USER_ID) Long userId,
            @RequestPart("file") MultipartFile file
    ) {
        return userAccountService.uploadCurrentUserAvatar(userId, file);
    }

    /**
     * 上传当前登录用户的主页背景图。
     */
    @PostMapping(value = "/background", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public UserProfileResponse uploadHomeBackground(
            @RequestAttribute(AuthRequestAttributes.CURRENT_USER_ID) Long userId,
            @RequestPart("file") MultipartFile file
    ) {
        return userAccountService.uploadCurrentUserHomeBackground(userId, file);
    }

    /**
     * 读取用户上传到 COS 的头像图片，供前端头像组件展示。
     */
    @GetMapping("/avatar/{userId}")
    public ResponseEntity<InputStreamResource> getAvatar(@PathVariable Long userId) {
        UserAccountService.AvatarDownloadPayload avatar = userAccountService.openUserAvatar(userId);
        MediaType mediaType = MediaType.APPLICATION_OCTET_STREAM;

        if (avatar.contentType() != null && !avatar.contentType().isBlank()) {
            mediaType = MediaType.parseMediaType(avatar.contentType());
        }

        return ResponseEntity.ok()
                .contentType(mediaType)
                .contentLength(avatar.contentLength())
                .body(new InputStreamResource(avatar.inputStream()));
    }

    /**
     * 读取用户上传到 COS 的主页背景图，供前端主页背景展示。
     */
    @GetMapping("/background/{userId}")
    public ResponseEntity<InputStreamResource> getHomeBackground(@PathVariable Long userId) {
        UserAccountService.HomeBackgroundDownloadPayload background = userAccountService.openUserHomeBackground(userId);
        MediaType mediaType = MediaType.APPLICATION_OCTET_STREAM;

        if (background.contentType() != null && !background.contentType().isBlank()) {
            mediaType = MediaType.parseMediaType(background.contentType());
        }

        return ResponseEntity.ok()
                .contentType(mediaType)
                .contentLength(background.contentLength())
                .body(new InputStreamResource(background.inputStream()));
    }

    /**
     * 清空当前登录用户已设置的主页背景图。
     */
    @DeleteMapping("/background")
    public UserProfileResponse clearHomeBackground(
            @RequestAttribute(AuthRequestAttributes.CURRENT_USER_ID) Long userId
    ) {
        return userAccountService.clearCurrentUserHomeBackground(userId);
    }

    /**
     * 校验旧密码后，为当前登录用户更新新的密码。
     */
    @PutMapping("/password")
    public ApiMessageResponse changePassword(
            @RequestAttribute(AuthRequestAttributes.CURRENT_USER_ID) Long userId,
            @Valid @RequestBody ChangePasswordRequest request
    ) {
        userAccountService.changePassword(userId, request);
        return new ApiMessageResponse("密码修改成功。");
    }
}
