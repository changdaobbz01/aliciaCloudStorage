package com.alicia.cloudstorage.api.service;

import com.alicia.cloudstorage.api.dto.AppPackageInfoResponse;
import com.alicia.cloudstorage.api.dto.AppPackageVersionResponse;
import com.alicia.cloudstorage.api.entity.AppPackageRelease;
import com.alicia.cloudstorage.api.entity.AppPackageReleaseStatus;
import com.alicia.cloudstorage.api.repository.AppPackageReleaseRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HexFormat;
import java.util.List;
import java.util.Properties;

@Service
public class AppPackageService {

    private static final String PUBLIC_DOWNLOAD_PATH = "/api/app-package/download/current";
    private static final String CURRENT_PACKAGE_FILE_NAME = "current.apk";
    private static final String METADATA_FILE_NAME = "metadata.properties";
    private static final String APP_PACKAGE_MEDIA_TYPE = "application/vnd.android.package-archive";

    private final Path legacyStorageDirectory;
    private final long maxPackageSizeBytes;
    private final AppPackageStorage appPackageStorage;
    private final AppPackageReleaseRepository appPackageReleaseRepository;
    private final TransactionTemplate transactionTemplate;

    public AppPackageService(
            @Value("${alicia.app-package.storage-dir:/app/data/app-package}") String legacyStorageDirectory,
            @Value("${alicia.app-package.max-size-bytes:314572800}") long maxPackageSizeBytes,
            AppPackageStorage appPackageStorage,
            AppPackageReleaseRepository appPackageReleaseRepository,
            TransactionTemplate transactionTemplate
    ) {
        this.legacyStorageDirectory = Path.of(legacyStorageDirectory).toAbsolutePath().normalize();
        this.maxPackageSizeBytes = maxPackageSizeBytes;
        this.appPackageStorage = appPackageStorage;
        this.appPackageReleaseRepository = appPackageReleaseRepository;
        this.transactionTemplate = transactionTemplate;
    }

    public AppPackageInfoResponse getCurrentPackageInfo() {
        return appPackageReleaseRepository
                .findFirstByStatusOrderByUploadedAtDesc(AppPackageReleaseStatus.CURRENT)
                .map(this::toPackageInfoResponse)
                .orElseGet(() -> toPackageInfoResponse(loadLegacyCurrentPackageMetadata()));
    }

    public AppPackageVersionResponse getCurrentPackageVersionInfo() {
        return appPackageReleaseRepository
                .findFirstByStatusOrderByUploadedAtDesc(AppPackageReleaseStatus.CURRENT)
                .map(this::toPackageVersionResponse)
                .orElseGet(() -> toPackageVersionResponse(loadLegacyCurrentPackageMetadata()));
    }

    public AppPackageInfoResponse storePackage(MultipartFile file, String versionName, String releaseNotes, Long uploadedBy) {
        String fileName = normalizePackageFileName(file);
        validatePackageFile(file, fileName);

        String normalizedVersionName = normalizeRequiredText(
                versionName,
                "更新版本不能为空。",
                64,
                "更新版本长度不能超过 64 个字符。"
        );
        String normalizedReleaseNotes = normalizeRequiredText(
                releaseNotes,
                "更新说明不能为空。",
                4000,
                "更新说明长度不能超过 4000 个字符。"
        );
        FileInspection inspection = inspectPackageFile(file);
        AppPackageStorage.StoredAppPackage storedPackage = appPackageStorage.store(file, fileName);

        try {
            AppPackageRelease savedRelease = transactionTemplate.execute(status -> {
                appPackageReleaseRepository.findByStatus(AppPackageReleaseStatus.CURRENT)
                        .forEach(currentRelease -> currentRelease.setStatus(AppPackageReleaseStatus.HISTORY));

                AppPackageRelease release = new AppPackageRelease();
                release.setFileName(fileName);
                release.setFileSizeBytes(storedPackage.fileSizeBytes());
                release.setContentType(storedPackage.contentType());
                release.setObjectKey(storedPackage.objectKey());
                release.setSha256Hex(inspection.sha256Hex());
                release.setVersionName(normalizedVersionName);
                release.setReleaseNotes(normalizedReleaseNotes);
                release.setStatus(AppPackageReleaseStatus.CURRENT);
                release.setUploadedBy(uploadedBy);
                release.setUploadedAt(LocalDateTime.now());

                AppPackageRelease saved = appPackageReleaseRepository.save(release);
                appPackageReleaseRepository.flush();
                return saved;
            });

            if (savedRelease == null) {
                throw new IllegalStateException("APK 版本保存失败，请稍后重试。");
            }

            return toPackageInfoResponse(savedRelease);
        } catch (RuntimeException exception) {
            appPackageStorage.deleteObjectQuietly(storedPackage.objectKey());
            throw exception;
        }
    }

    public void deleteCurrentPackage() {
        List<String> deletedObjectKeys = transactionTemplate.execute(status -> {
            var currentReleases = appPackageReleaseRepository.findByStatus(AppPackageReleaseStatus.CURRENT);
            if (currentReleases.isEmpty()) {
                return List.of();
            }

            List<String> objectKeys = currentReleases.stream()
                    .map(AppPackageRelease::getObjectKey)
                    .filter(this::hasText)
                    .toList();
            currentReleases.forEach(currentRelease -> currentRelease.setStatus(AppPackageReleaseStatus.DELETED));
            appPackageReleaseRepository.flush();
            return objectKeys;
        });

        if (deletedObjectKeys == null || deletedObjectKeys.isEmpty()) {
            deleteLegacyCurrentPackage();
            return;
        }

        deletedObjectKeys.forEach(appPackageStorage::deleteObjectQuietly);
    }

    public AppPackageDownloadPayload openCurrentPackage() {
        return appPackageReleaseRepository
                .findFirstByStatusOrderByUploadedAtDesc(AppPackageReleaseStatus.CURRENT)
                .map(this::openCosPackage)
                .orElseGet(this::openLegacyCurrentPackage);
    }

    private AppPackageDownloadPayload openCosPackage(AppPackageRelease release) {
        AppPackageStorage.AppPackageDownloadLink downloadLink = appPackageStorage.createDownloadLink(
                release.getObjectKey(),
                release.getFileName(),
                release.getContentType()
        );

        return AppPackageDownloadPayload.redirect(
                release.getFileName(),
                release.getFileSizeBytes(),
                release.getContentType(),
                downloadLink.uri()
        );
    }

    private AppPackageDownloadPayload openLegacyCurrentPackage() {
        Path packagePath = getLegacyCurrentPackagePath();

        if (!Files.exists(packagePath)) {
            throw new IllegalArgumentException("当前还没有可下载的 APK 安装包。");
        }

        StoredPackageMetadata metadata = readLegacyMetadata();
        String fileName = metadata == null ? CURRENT_PACKAGE_FILE_NAME : metadata.fileName();

        try {
            return AppPackageDownloadPayload.stream(
                    fileName,
                    Files.size(packagePath),
                    APP_PACKAGE_MEDIA_TYPE,
                    Files.newInputStream(packagePath)
            );
        } catch (IOException ex) {
            throw new IllegalStateException("读取当前安装包失败，请稍后重试。", ex);
        }
    }

    private void validatePackageFile(MultipartFile file, String fileName) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("请先选择要上传的 APK 安装包。");
        }

        if (!fileName.toLowerCase().endsWith(".apk")) {
            throw new IllegalArgumentException("只能上传 APK 安装包文件。");
        }

        if (maxPackageSizeBytes <= 0) {
            throw new IllegalStateException("APK 最大上传大小配置必须大于 0。");
        }

        if (file.getSize() > maxPackageSizeBytes) {
            long maxMb = Math.max(1L, maxPackageSizeBytes / 1024 / 1024);
            throw new IllegalArgumentException("APK 安装包不能超过 " + maxMb + " MB。");
        }
    }

    private FileInspection inspectPackageFile(MultipartFile file) {
        try (InputStream inputStream = file.getInputStream()) {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[8192];
            byte[] firstBytes = new byte[4];
            int firstBytesLength = 0;
            int read;

            while ((read = inputStream.read(buffer)) != -1) {
                if (firstBytesLength < firstBytes.length) {
                    int copyLength = Math.min(read, firstBytes.length - firstBytesLength);
                    System.arraycopy(buffer, 0, firstBytes, firstBytesLength, copyLength);
                    firstBytesLength += copyLength;
                }
                digest.update(buffer, 0, read);
            }

            if (firstBytesLength < 2 || firstBytes[0] != 'P' || firstBytes[1] != 'K') {
                throw new IllegalArgumentException("APK 安装包格式不正确。");
            }

            return new FileInspection(HexFormat.of().formatHex(digest.digest()));
        } catch (IOException ex) {
            throw new IllegalArgumentException("读取 APK 安装包失败。", ex);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("当前运行环境不支持 SHA-256 校验。", ex);
        }
    }

    private String normalizePackageFileName(MultipartFile file) {
        String rawFileName = file == null ? null : file.getOriginalFilename();
        if (rawFileName == null || rawFileName.isBlank()) {
            return CURRENT_PACKAGE_FILE_NAME;
        }

        String normalized = rawFileName.trim().replace('\\', '/');
        int slashIndex = normalized.lastIndexOf('/');
        if (slashIndex >= 0) {
            normalized = normalized.substring(slashIndex + 1);
        }

        normalized = normalized.trim();
        return normalized.isEmpty() ? CURRENT_PACKAGE_FILE_NAME : normalized;
    }

    private StoredPackageMetadata loadLegacyCurrentPackageMetadata() {
        Path packagePath = getLegacyCurrentPackagePath();
        StoredPackageMetadata metadata = readLegacyMetadata();

        if (metadata == null || !Files.exists(packagePath)) {
            return null;
        }

        return metadata;
    }

    private StoredPackageMetadata readLegacyMetadata() {
        Path packagePath = getLegacyCurrentPackagePath();
        Path metadataPath = getLegacyMetadataPath();

        try {
            if (Files.exists(metadataPath)) {
                Properties properties = new Properties();
                try (InputStream inputStream = Files.newInputStream(metadataPath)) {
                    properties.load(inputStream);
                }

                String fileName = properties.getProperty("fileName", CURRENT_PACKAGE_FILE_NAME);
                long fileSizeBytes = Long.parseLong(properties.getProperty("fileSizeBytes", "0"));
                LocalDateTime uploadedAt = LocalDateTime.parse(properties.getProperty("uploadedAt"));
                String versionName = normalizeOptionalText(properties.getProperty("versionName"));
                String releaseNotes = normalizeOptionalText(properties.getProperty("releaseNotes"));
                return new StoredPackageMetadata(fileName, fileSizeBytes, uploadedAt, versionName, releaseNotes);
            }

            if (!Files.exists(packagePath)) {
                return null;
            }

            FileTime lastModifiedTime = Files.getLastModifiedTime(packagePath);
            return new StoredPackageMetadata(
                    CURRENT_PACKAGE_FILE_NAME,
                    Files.size(packagePath),
                    LocalDateTime.ofInstant(lastModifiedTime.toInstant(), ZoneId.systemDefault()),
                    null,
                    null
            );
        } catch (IOException ex) {
            throw new IllegalStateException("读取安装包元信息失败，请稍后重试。", ex);
        } catch (RuntimeException ex) {
            throw new IllegalStateException("当前安装包元信息已损坏，请重新上传 APK。", ex);
        }
    }

    private void deleteLegacyCurrentPackage() {
        try {
            Files.deleteIfExists(getLegacyCurrentPackagePath());
            Files.deleteIfExists(getLegacyMetadataPath());
        } catch (IOException ex) {
            throw new IllegalStateException("删除当前安装包失败，请稍后重试。", ex);
        }
    }

    private Path getLegacyCurrentPackagePath() {
        return legacyStorageDirectory.resolve(CURRENT_PACKAGE_FILE_NAME);
    }

    private Path getLegacyMetadataPath() {
        return legacyStorageDirectory.resolve(METADATA_FILE_NAME);
    }

    private AppPackageInfoResponse toPackageInfoResponse(AppPackageRelease release) {
        if (release == null) {
            return unavailablePackageInfo();
        }

        return new AppPackageInfoResponse(
                true,
                release.getFileName(),
                release.getFileSizeBytes(),
                release.getUploadedAt(),
                PUBLIC_DOWNLOAD_PATH,
                release.getVersionName(),
                release.getReleaseNotes()
        );
    }

    private AppPackageInfoResponse toPackageInfoResponse(StoredPackageMetadata metadata) {
        if (metadata == null) {
            return unavailablePackageInfo();
        }

        return new AppPackageInfoResponse(
                true,
                metadata.fileName(),
                metadata.fileSizeBytes(),
                metadata.uploadedAt(),
                PUBLIC_DOWNLOAD_PATH,
                metadata.versionName(),
                metadata.releaseNotes()
        );
    }

    private AppPackageInfoResponse unavailablePackageInfo() {
        return new AppPackageInfoResponse(false, null, null, null, PUBLIC_DOWNLOAD_PATH, null, null);
    }

    private AppPackageVersionResponse toPackageVersionResponse(AppPackageRelease release) {
        if (release == null) {
            return unavailablePackageVersion();
        }

        return new AppPackageVersionResponse(
                true,
                release.getVersionName(),
                release.getReleaseNotes(),
                PUBLIC_DOWNLOAD_PATH,
                release.getUploadedAt()
        );
    }

    private AppPackageVersionResponse toPackageVersionResponse(StoredPackageMetadata metadata) {
        if (metadata == null) {
            return unavailablePackageVersion();
        }

        return new AppPackageVersionResponse(
                true,
                metadata.versionName(),
                metadata.releaseNotes(),
                PUBLIC_DOWNLOAD_PATH,
                metadata.uploadedAt()
        );
    }

    private AppPackageVersionResponse unavailablePackageVersion() {
        return new AppPackageVersionResponse(false, null, null, PUBLIC_DOWNLOAD_PATH, null);
    }

    private String normalizeRequiredText(String value, String emptyMessage, int maxLength, String tooLongMessage) {
        String normalized = normalizeOptionalText(value);
        if (normalized == null) {
            throw new IllegalArgumentException(emptyMessage);
        }
        if (normalized.length() > maxLength) {
            throw new IllegalArgumentException(tooLongMessage);
        }
        return normalized;
    }

    private String normalizeOptionalText(String value) {
        if (value == null) {
            return null;
        }

        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private record FileInspection(String sha256Hex) {
    }

    public record AppPackageDownloadPayload(
            String fileName,
            long fileSizeBytes,
            String contentType,
            InputStream inputStream,
            URI redirectUri
    ) {
        public static AppPackageDownloadPayload stream(String fileName, long fileSizeBytes, String contentType, InputStream inputStream) {
            return new AppPackageDownloadPayload(fileName, fileSizeBytes, contentType, inputStream, null);
        }

        public static AppPackageDownloadPayload redirect(String fileName, long fileSizeBytes, String contentType, URI redirectUri) {
            return new AppPackageDownloadPayload(fileName, fileSizeBytes, contentType, null, redirectUri);
        }

        public boolean isRedirect() {
            return redirectUri != null;
        }
    }

    private record StoredPackageMetadata(
            String fileName,
            long fileSizeBytes,
            LocalDateTime uploadedAt,
            String versionName,
            String releaseNotes
    ) {
    }
}
