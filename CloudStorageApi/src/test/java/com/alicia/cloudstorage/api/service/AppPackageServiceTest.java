package com.alicia.cloudstorage.api.service;

import com.alicia.cloudstorage.api.dto.AppPackageInfoResponse;
import com.alicia.cloudstorage.api.dto.AppPackageVersionResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AppPackageServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void storePackagePersistsVersionMetadataAndExposesVersionEndpoint() throws IOException {
        AppPackageService appPackageService = new AppPackageService(tempDir.toString());
        byte[] packageBytes = "apk-binary".getBytes(StandardCharsets.UTF_8);
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "alicia-1.2.0.apk",
                "application/vnd.android.package-archive",
                packageBytes
        );

        AppPackageInfoResponse packageInfo = appPackageService.storePackage(
                file,
                "1.2.0",
                "1. 修复启动闪退\n2. 优化更新提示"
        );
        AppPackageVersionResponse versionInfo = appPackageService.getCurrentPackageVersionInfo();

        assertThat(packageInfo.available()).isTrue();
        assertThat(packageInfo.fileName()).isEqualTo("alicia-1.2.0.apk");
        assertThat(packageInfo.fileSizeBytes()).isEqualTo((long) packageBytes.length);
        assertThat(packageInfo.versionName()).isEqualTo("1.2.0");
        assertThat(packageInfo.releaseNotes()).contains("修复启动闪退");
        assertThat(packageInfo.downloadUrl()).isEqualTo("/api/app-package/download/current");
        assertThat(packageInfo.uploadedAt()).isNotNull();

        assertThat(versionInfo.available()).isTrue();
        assertThat(versionInfo.versionName()).isEqualTo("1.2.0");
        assertThat(versionInfo.releaseNotes()).contains("优化更新提示");
        assertThat(versionInfo.downloadUrl()).isEqualTo("/api/app-package/download/current");
        assertThat(versionInfo.uploadedAt()).isEqualTo(packageInfo.uploadedAt());

        try (var inputStream = appPackageService.openCurrentPackage().inputStream()) {
            assertThat(inputStream.readAllBytes()).isEqualTo(packageBytes);
        }
    }

    @Test
    void deleteCurrentPackageClearsPackageAndVersionInfo() {
        AppPackageService appPackageService = new AppPackageService(tempDir.toString());
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "alicia-1.2.0.apk",
                "application/vnd.android.package-archive",
                "apk-binary".getBytes(StandardCharsets.UTF_8)
        );

        appPackageService.storePackage(file, "1.2.0", "修复若干问题");
        appPackageService.deleteCurrentPackage();

        assertThat(appPackageService.getCurrentPackageInfo().available()).isFalse();
        assertThat(appPackageService.getCurrentPackageVersionInfo().available()).isFalse();
        assertThatThrownBy(appPackageService::openCurrentPackage)
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void storePackageRejectsMissingVersionOrReleaseNotes() {
        AppPackageService appPackageService = new AppPackageService(tempDir.toString());
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "alicia-1.2.0.apk",
                "application/vnd.android.package-archive",
                "apk-binary".getBytes(StandardCharsets.UTF_8)
        );

        assertThatThrownBy(() -> appPackageService.storePackage(file, " ", "修复若干问题"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> appPackageService.storePackage(file, "1.2.0", " "))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
