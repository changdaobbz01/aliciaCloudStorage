package com.alicia.cloudstorage.api.service;

import com.alicia.cloudstorage.api.dto.AppPackageInfoResponse;
import com.alicia.cloudstorage.api.dto.AppPackageVersionResponse;
import com.alicia.cloudstorage.api.entity.AppPackageRelease;
import com.alicia.cloudstorage.api.entity.AppPackageReleaseStatus;
import com.alicia.cloudstorage.api.repository.AppPackageReleaseRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class AppPackageServiceTest {

    private static final String APP_PACKAGE_MEDIA_TYPE = "application/vnd.android.package-archive";

    @org.junit.jupiter.api.io.TempDir
    Path tempDir;

    @Mock
    private AppPackageReleaseRepository appPackageReleaseRepository;

    private InMemoryAppPackageStorage appPackageStorage;
    private AppPackageRelease currentRelease;
    private AppPackageService appPackageService;

    @BeforeEach
    void setUp() {
        appPackageStorage = new InMemoryAppPackageStorage();
        appPackageService = new AppPackageService(
                tempDir.toString(),
                1024 * 1024,
                appPackageStorage,
                appPackageReleaseRepository,
                new TransactionTemplate(new NoOpTransactionManager())
        );

        lenient()
                .when(appPackageReleaseRepository.findFirstByStatusOrderByUploadedAtDesc(AppPackageReleaseStatus.CURRENT))
                .thenAnswer(invocation -> currentRelease != null && currentRelease.getStatus() == AppPackageReleaseStatus.CURRENT
                        ? Optional.of(currentRelease)
                        : Optional.empty());
        lenient()
                .when(appPackageReleaseRepository.findByStatus(AppPackageReleaseStatus.CURRENT))
                .thenAnswer(invocation -> currentRelease != null && currentRelease.getStatus() == AppPackageReleaseStatus.CURRENT
                        ? List.of(currentRelease)
                        : List.of());
        lenient()
                .when(appPackageReleaseRepository.save(any(AppPackageRelease.class)))
                .thenAnswer(invocation -> {
                    currentRelease = invocation.getArgument(0);
                    currentRelease.onCreate();
                    return currentRelease;
                });
    }

    @Test
    void storePackagePersistsReleaseMetadataAndRedirectsToCosDownloadUrl() {
        byte[] packageBytes = minimalApkBytes();
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "alicia-1.2.0.apk",
                APP_PACKAGE_MEDIA_TYPE,
                packageBytes
        );

        AppPackageInfoResponse packageInfo = appPackageService.storePackage(
                file,
                "1.2.0",
                "Fix startup and update prompt.",
                99L
        );
        AppPackageVersionResponse versionInfo = appPackageService.getCurrentPackageVersionInfo();
        AppPackageService.AppPackageDownloadPayload downloadPayload = appPackageService.openCurrentPackage();

        assertThat(packageInfo.available()).isTrue();
        assertThat(packageInfo.fileName()).isEqualTo("alicia-1.2.0.apk");
        assertThat(packageInfo.fileSizeBytes()).isEqualTo((long) packageBytes.length);
        assertThat(packageInfo.versionName()).isEqualTo("1.2.0");
        assertThat(packageInfo.releaseNotes()).isEqualTo("Fix startup and update prompt.");
        assertThat(packageInfo.downloadUrl()).isEqualTo("/api/app-package/download/current");
        assertThat(packageInfo.uploadedAt()).isNotNull();

        assertThat(versionInfo.available()).isTrue();
        assertThat(versionInfo.versionName()).isEqualTo("1.2.0");
        assertThat(versionInfo.downloadUrl()).isEqualTo("/api/app-package/download/current");
        assertThat(versionInfo.uploadedAt()).isEqualTo(packageInfo.uploadedAt());

        assertThat(currentRelease.getUploadedBy()).isEqualTo(99L);
        assertThat(currentRelease.getObjectKey()).startsWith("app-packages/releases/test-");
        assertThat(currentRelease.getSha256Hex()).hasSize(64);

        assertThat(downloadPayload.isRedirect()).isTrue();
        assertThat(downloadPayload.redirectUri().toString())
                .isEqualTo("https://files.example.com/" + currentRelease.getObjectKey() + "?sign=test");
        assertThat(downloadPayload.inputStream()).isNull();
    }

    @Test
    void deleteCurrentPackageClearsPackageAndVersionInfo() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "alicia-1.2.0.apk",
                APP_PACKAGE_MEDIA_TYPE,
                minimalApkBytes()
        );

        appPackageService.storePackage(file, "1.2.0", "Fix issues.", 99L);
        appPackageService.deleteCurrentPackage();

        assertThat(currentRelease.getStatus()).isEqualTo(AppPackageReleaseStatus.DELETED);
        assertThat(appPackageStorage.objects).isEmpty();
        assertThat(appPackageService.getCurrentPackageInfo().available()).isFalse();
        assertThat(appPackageService.getCurrentPackageVersionInfo().available()).isFalse();
        assertThatThrownBy(appPackageService::openCurrentPackage)
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void storePackageRejectsMissingVersionOrReleaseNotesBeforeCosUpload() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "alicia-1.2.0.apk",
                APP_PACKAGE_MEDIA_TYPE,
                minimalApkBytes()
        );

        assertThatThrownBy(() -> appPackageService.storePackage(file, " ", "Fix issues.", 99L))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> appPackageService.storePackage(file, "1.2.0", " ", 99L))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(appPackageStorage.objects).isEmpty();
    }

    @Test
    void legacyLocalPackageRemainsAvailableWhenNoCosReleaseExists() throws IOException {
        byte[] packageBytes = minimalApkBytes();
        Files.write(tempDir.resolve("current.apk"), packageBytes);

        Properties properties = new Properties();
        properties.setProperty("fileName", "legacy.apk");
        properties.setProperty("fileSizeBytes", String.valueOf(packageBytes.length));
        properties.setProperty("uploadedAt", LocalDateTime.now().minusDays(1).toString());
        properties.setProperty("versionName", "0.9.0");
        properties.setProperty("releaseNotes", "Legacy release.");
        try (var outputStream = Files.newOutputStream(tempDir.resolve("metadata.properties"))) {
            properties.store(outputStream, "legacy");
        }

        AppPackageInfoResponse packageInfo = appPackageService.getCurrentPackageInfo();
        AppPackageService.AppPackageDownloadPayload downloadPayload = appPackageService.openCurrentPackage();

        assertThat(packageInfo.available()).isTrue();
        assertThat(packageInfo.fileName()).isEqualTo("legacy.apk");
        assertThat(packageInfo.versionName()).isEqualTo("0.9.0");
        assertThat(downloadPayload.isRedirect()).isFalse();
        try (var inputStream = downloadPayload.inputStream()) {
            assertThat(inputStream.readAllBytes()).isEqualTo(packageBytes);
        }
    }

    private byte[] minimalApkBytes() {
        return new byte[]{'P', 'K', 3, 4, 1, 2, 3, 4};
    }

    private static class InMemoryAppPackageStorage implements AppPackageStorage {

        private final Map<String, byte[]> objects = new HashMap<>();
        private int nextId = 1;

        @Override
        public StoredAppPackage store(org.springframework.web.multipart.MultipartFile file, String fileName) {
            try {
                String objectKey = "app-packages/releases/test-" + nextId++ + ".apk";
                objects.put(objectKey, file.getBytes());
                return new StoredAppPackage(objectKey, APP_PACKAGE_MEDIA_TYPE, file.getSize());
            } catch (IOException exception) {
                throw new IllegalArgumentException("Failed to read test package.", exception);
            }
        }

        @Override
        public AppPackageDownloadLink createDownloadLink(String objectKey, String fileName, String contentType) {
            return new AppPackageDownloadLink(URI.create("https://files.example.com/" + objectKey + "?sign=test"), 0L);
        }

        @Override
        public void deleteObjectQuietly(String objectKey) {
            objects.remove(objectKey);
        }
    }

    private static class NoOpTransactionManager implements PlatformTransactionManager {

        @Override
        public TransactionStatus getTransaction(TransactionDefinition definition) {
            return new SimpleTransactionStatus();
        }

        @Override
        public void commit(TransactionStatus status) {
        }

        @Override
        public void rollback(TransactionStatus status) {
        }
    }
}
