package com.alicia.cloudstorage.api.service;

import com.alicia.cloudstorage.api.dto.AppPackageInfoResponse;
import com.alicia.cloudstorage.api.dto.AppPackageVersionResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.FileTime;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Properties;

@Service
public class AppPackageService {

    private static final String PUBLIC_DOWNLOAD_PATH = "/api/app-package/download/current";
    private static final String CURRENT_PACKAGE_FILE_NAME = "current.apk";
    private static final String METADATA_FILE_NAME = "metadata.properties";
    private final Path storageDirectory;

    public AppPackageService(
            @Value("${alicia.app-package.storage-dir:/app/data/app-package}") String storageDirectory
    ) {
        this.storageDirectory = Path.of(storageDirectory).toAbsolutePath().normalize();
    }

    public AppPackageInfoResponse getCurrentPackageInfo() {
        return toPackageInfoResponse(loadCurrentPackageMetadata());
    }

    public AppPackageVersionResponse getCurrentPackageVersionInfo() {
        return toPackageVersionResponse(loadCurrentPackageMetadata());
    }

    public AppPackageInfoResponse storePackage(MultipartFile file, String versionName, String releaseNotes) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("请先选择要上传的 APK 安装包。");
        }

        String originalFileName = file.getOriginalFilename() == null
                ? CURRENT_PACKAGE_FILE_NAME
                : file.getOriginalFilename().trim();

        if (!originalFileName.toLowerCase().endsWith(".apk")) {
            throw new IllegalArgumentException("只能上传 APK 安装包文件。");
        }

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
        LocalDateTime uploadedAt = LocalDateTime.now();
        StoredPackageMetadata metadata = new StoredPackageMetadata(
                originalFileName,
                file.getSize(),
                uploadedAt,
                normalizedVersionName,
                normalizedReleaseNotes
        );

        try {
            ensureStorageDirectory();

            try (InputStream inputStream = file.getInputStream()) {
                Files.copy(inputStream, getCurrentPackagePath(), StandardCopyOption.REPLACE_EXISTING);
            }

            writeMetadata(metadata);
        } catch (IOException ex) {
            throw new IllegalStateException("APK 保存失败，请稍后重试。", ex);
        }

        return toPackageInfoResponse(metadata);
    }

    public void deleteCurrentPackage() {
        try {
            Files.deleteIfExists(getCurrentPackagePath());
            Files.deleteIfExists(getMetadataPath());
        } catch (IOException ex) {
            throw new IllegalStateException("删除当前安装包失败，请稍后重试。", ex);
        }
    }

    public AppPackageDownloadPayload openCurrentPackage() {
        Path packagePath = getCurrentPackagePath();

        if (!Files.exists(packagePath)) {
            throw new IllegalArgumentException("当前还没有可下载的 APK 安装包。");
        }

        StoredPackageMetadata metadata = readMetadata();
        String fileName = metadata == null ? CURRENT_PACKAGE_FILE_NAME : metadata.fileName();

        try {
            return new AppPackageDownloadPayload(
                    fileName,
                    Files.size(packagePath),
                    Files.newInputStream(packagePath)
            );
        } catch (IOException ex) {
            throw new IllegalStateException("读取当前安装包失败，请稍后重试。", ex);
        }
    }

    private void ensureStorageDirectory() throws IOException {
        Files.createDirectories(storageDirectory);
    }

    private Path getCurrentPackagePath() {
        return storageDirectory.resolve(CURRENT_PACKAGE_FILE_NAME);
    }

    private Path getMetadataPath() {
        return storageDirectory.resolve(METADATA_FILE_NAME);
    }

    private StoredPackageMetadata loadCurrentPackageMetadata() {
        Path packagePath = getCurrentPackagePath();
        StoredPackageMetadata metadata = readMetadata();

        if (metadata == null || !Files.exists(packagePath)) {
            return null;
        }

        return metadata;
    }

    private StoredPackageMetadata readMetadata() {
        Path packagePath = getCurrentPackagePath();
        Path metadataPath = getMetadataPath();

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

    private void writeMetadata(StoredPackageMetadata metadata) throws IOException {
        Properties properties = new Properties();
        properties.setProperty("fileName", metadata.fileName());
        properties.setProperty("fileSizeBytes", String.valueOf(metadata.fileSizeBytes()));
        properties.setProperty("uploadedAt", metadata.uploadedAt().toString());
        if (metadata.versionName() != null) {
            properties.setProperty("versionName", metadata.versionName());
        }
        if (metadata.releaseNotes() != null) {
            properties.setProperty("releaseNotes", metadata.releaseNotes());
        }

        try (OutputStream outputStream = Files.newOutputStream(getMetadataPath())) {
            properties.store(outputStream, "Alicia Cloud Storage APK metadata");
        }
    }

    private AppPackageInfoResponse toPackageInfoResponse(StoredPackageMetadata metadata) {
        if (metadata == null) {
            return new AppPackageInfoResponse(false, null, null, null, PUBLIC_DOWNLOAD_PATH, null, null);
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

    private AppPackageVersionResponse toPackageVersionResponse(StoredPackageMetadata metadata) {
        if (metadata == null) {
            return new AppPackageVersionResponse(false, null, null, PUBLIC_DOWNLOAD_PATH, null);
        }

        return new AppPackageVersionResponse(
                true,
                metadata.versionName(),
                metadata.releaseNotes(),
                PUBLIC_DOWNLOAD_PATH,
                metadata.uploadedAt()
        );
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

    public record AppPackageDownloadPayload(
            String fileName,
            long fileSizeBytes,
            InputStream inputStream
    ) {
    }

    public record StoredPackageMetadata(
            String fileName,
            long fileSizeBytes,
            LocalDateTime uploadedAt,
            String versionName,
            String releaseNotes
    ) {
    }
}
