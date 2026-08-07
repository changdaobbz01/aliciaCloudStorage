package com.alicia.cloudstorage.api.controller;

import com.alicia.cloudstorage.api.dto.AppPackageInfoResponse;
import com.alicia.cloudstorage.api.dto.AppPackageVersionResponse;
import com.alicia.cloudstorage.api.service.AppPackageService;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/api/app-package")
public class AppPackageController {

    private final AppPackageService appPackageService;

    /**
     * 注入 APK 存储服务，供公开下载入口和首页查询复用。     */
    public AppPackageController(AppPackageService appPackageService) {
        this.appPackageService = appPackageService;
    }

    /**
     * 返回当前正式安装包的对外下载信息。     */
    @GetMapping
    public AppPackageInfoResponse getCurrentPackageInfo() {
        return appPackageService.getCurrentPackageInfo();
    }

    @GetMapping("/version")
    public AppPackageVersionResponse getCurrentPackageVersionInfo() {
        return appPackageService.getCurrentPackageVersionInfo();
    }

    /**
     * 向浏览器或手机客户端流式返回当前正式 APK。     */
    @GetMapping("/download/current")
    public ResponseEntity<?> downloadCurrentPackage() {
        AppPackageService.AppPackageDownloadPayload downloadPayload = appPackageService.openCurrentPackage();

        if (downloadPayload.isRedirect()) {
            return ResponseEntity.status(HttpStatus.FOUND)
                    .location(downloadPayload.redirectUri())
                    .header(HttpHeaders.CACHE_CONTROL, "no-store, no-cache, must-revalidate")
                    .header(HttpHeaders.PRAGMA, "no-cache")
                    .header(HttpHeaders.EXPIRES, "0")
                    .build();
        }

        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, "no-store, no-cache, must-revalidate")
                .header(HttpHeaders.PRAGMA, "no-cache")
                .header(HttpHeaders.EXPIRES, "0")
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment()
                                .filename(downloadPayload.fileName(), StandardCharsets.UTF_8)
                                .build()
                                .toString()
                )
                .contentType(MediaType.parseMediaType(downloadPayload.contentType()))
                .contentLength(downloadPayload.fileSizeBytes())
                .body(new InputStreamResource(downloadPayload.inputStream()));
    }
}
