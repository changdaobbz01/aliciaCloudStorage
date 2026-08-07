package com.alicia.cloudstorage.api.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CosFileStorageServiceTest {

    @Test
    void presignedDownloadUrlUsesConfiguredCustomDomain() {
        CosFileStorageService service = new CosFileStorageService(
                "secret-id",
                "secret-key",
                "ap-shanghai",
                "alicia-cloud-storage-file-1320975974",
                "files.windwindwind-alicia.cn",
                1024L,
                600L
        );

        CosFileStorageService.PresignedCosUrl url = service.createInlineDownloadUrl(
                "user-files/1/demo.txt",
                "text/plain",
                "demo.txt"
        );

        assertThat(url.url()).startsWith("https://files.windwindwind-alicia.cn/user-files/1/demo.txt?");
        assertThat(url.url()).contains("sign=q-sign-algorithm");
    }

    @Test
    void presignedDownloadUrlAcceptsCustomDomainWithScheme() {
        CosFileStorageService service = new CosFileStorageService(
                "secret-id",
                "secret-key",
                "ap-shanghai",
                "alicia-cloud-storage-file-1320975974",
                "https://files.windwindwind-alicia.cn/",
                1024L,
                600L
        );

        CosFileStorageService.PresignedCosUrl url = service.createInlineDownloadUrl(
                "user-files/1/demo.txt",
                null,
                null
        );

        assertThat(url.url()).startsWith("https://files.windwindwind-alicia.cn/user-files/1/demo.txt?");
    }

    @Test
    void presignedDownloadUrlRejectsCustomDomainWithPath() {
        CosFileStorageService service = new CosFileStorageService(
                "secret-id",
                "secret-key",
                "ap-shanghai",
                "alicia-cloud-storage-file-1320975974",
                "https://files.windwindwind-alicia.cn/assets",
                1024L,
                600L
        );

        assertThatThrownBy(() -> service.createInlineDownloadUrl("user-files/1/demo.txt", null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("COS custom domain must not include a path.");
    }
}
