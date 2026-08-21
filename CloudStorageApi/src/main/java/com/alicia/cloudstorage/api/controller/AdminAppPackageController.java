package com.alicia.cloudstorage.api.controller;

import com.alicia.cloudstorage.api.principal.PrincipalRequestAttributes;
import com.alicia.cloudstorage.api.principal.CurrentPrincipal;
import com.alicia.cloudstorage.api.dto.ApiMessageResponse;
import com.alicia.cloudstorage.api.dto.AppPackageInfoResponse;
import com.alicia.cloudstorage.api.service.AppPackageService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/admin/app-package")
public class AdminAppPackageController {

    private final AppPackageService appPackageService;

    /**
     * 注入 APK 存储服务，供管理员上传和删除正式安装包。     */
    public AdminAppPackageController(AppPackageService appPackageService) {
        this.appPackageService = appPackageService;
    }

    /**
     * 返回管理员可见的当前 APK 分发信息。     */
    @GetMapping
    public AppPackageInfoResponse getCurrentPackageInfo() {
        return appPackageService.getCurrentPackageInfo();
    }

    /**
     * 上传并覆盖当前正式安卓安装包。     */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public AppPackageInfoResponse uploadCurrentPackage(
            @RequestPart("file") MultipartFile file,
            @RequestParam("versionName") String versionName,
            @RequestParam("releaseNotes") String releaseNotes,
            @RequestAttribute(PrincipalRequestAttributes.CURRENT_PRINCIPAL) CurrentPrincipal principal
    ) {
        return appPackageService.storePackage(file, versionName, releaseNotes, principal.userId());
    }

    /**
     * 移除当前对外提供下载的安装包。     */
    @DeleteMapping
    public ApiMessageResponse deleteCurrentPackage() {
        appPackageService.deleteCurrentPackage();
        return new ApiMessageResponse("当前安装包已移除。");
    }
}
