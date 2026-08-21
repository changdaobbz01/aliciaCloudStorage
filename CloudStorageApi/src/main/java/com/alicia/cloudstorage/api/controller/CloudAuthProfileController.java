package com.alicia.cloudstorage.api.controller;

import com.alicia.cloudstorage.api.auth.AuthRequestAttributes;
import com.alicia.cloudstorage.api.auth.CurrentPrincipal;
import com.alicia.cloudstorage.api.dto.UserProfileResponse;
import com.alicia.cloudstorage.api.service.CloudCurrentUserService;
import com.alicia.cloudstorage.api.service.CloudUserAvatarService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.net.URI;

@RestController
@RequestMapping("/api/auth")
public class CloudAuthProfileController {

    private static final String SIGNED_MEDIA_REDIRECT_CACHE_CONTROL = "private, max-age=240";

    private final CloudCurrentUserService cloudCurrentUserService;
    private final CloudUserAvatarService cloudUserAvatarService;

    /**
     * 保留云盘聚合资料和头像入口；身份写操作直接走 identityApi。
     */
    public CloudAuthProfileController(
            CloudCurrentUserService cloudCurrentUserService,
            CloudUserAvatarService cloudUserAvatarService
    ) {
        this.cloudCurrentUserService = cloudCurrentUserService;
        this.cloudUserAvatarService = cloudUserAvatarService;
    }

    /**
     * 查询当前登录用户，并合并 identity 账号资料与云盘 profile。
     */
    @GetMapping("/me")
    public UserProfileResponse me(
            @RequestAttribute(AuthRequestAttributes.CURRENT_PRINCIPAL) CurrentPrincipal principal,
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization
    ) {
        return cloudCurrentUserService.getCurrentUser(authorization);
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
        return cloudUserAvatarService.uploadCurrentUserAvatar(authorization, file);
    }

    /**
     * 读取用户上传到 COS 的头像图片，供前端头像组件展示。
     */
    @GetMapping("/avatar/{userId}")
    public ResponseEntity<Void> getAvatar(@PathVariable Long userId) {
        String accessUrl = cloudUserAvatarService.resolveUserAvatarAccessUrl(userId).url();
        return ResponseEntity.status(HttpStatus.FOUND)
                .header(HttpHeaders.CACHE_CONTROL, SIGNED_MEDIA_REDIRECT_CACHE_CONTROL)
                .location(URI.create(accessUrl))
                .build();
    }
}
