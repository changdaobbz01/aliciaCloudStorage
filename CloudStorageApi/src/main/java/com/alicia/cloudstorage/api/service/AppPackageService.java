package com.alicia.cloudstorage.api.service;

import com.alicia.cloudstorage.api.dto.AppPackageInfoResponse;
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

    /**
     * 确定运行期安装包持久化目录。     */
    public AppPackageService(
            @Value("${alicia.app-package.storage-dir:/app/data/app-package}") String storageDirectory
    ) {
        this.storageDirectory = Path.of(storageDirectory).toAbsolutePath().normalize();
    }

    /**
     * 返回当前对外公开的 APK 下载信息，供管理台和首页下载入口复用。     */
    public AppPackageInfoResponse getCurrentPackageInfo() {
        Path packagePath = getCurrentPackagePath();
        StoredPackageMetadata metadata = readMetadata();

        if (metadata == null || !Files.exists(packagePath)) {
            return new AppPackageInfoResponse(false, null, null, null, PUBLIC_DOWNLOAD_PATH);
        }

        return new AppPackageInfoResponse(
                true,
                metadata.fileName(),
                metadata.fileSizeBytes(),
                metadata.uploadedAt(),
                PUBLIC_DOWNLOAD_PATH
        );
    }

    /**
     * 保存管理员新上传的 APK，并覆盖现有正式安装包。     */
    public AppPackageInfoResponse storePackage(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("请先选择要上传的 APK 安装包。");
        }

        String originalFileName = file.getOriginalFilename() == null
                ? CURRENT_PACKAGE_FILE_NAME
                : file.getOriginalFilename().trim();

        if (!originalFileName.toLowerCase().endsWith(".apk")) {
            throw new IllegalArgumentException("只能上传 APK 安装包文件。");
        }

        LocalDateTime uploadedAt = LocalDateTime.now();
        StoredPackageMetadata metadata = new StoredPackageMetadata(originalFileName, file.getSize(), uploadedAt);

        try {
            ensureStorageDirectory();

            try (InputStream inputStream = file.getInputStream()) {
                Files.copy(inputStream, getCurrentPackagePath(), StandardCopyOption.REPLACE_EXISTING);
            }

            writeMetadata(metadata);
        } catch (IOException ex) {
            throw new IllegalStateException("APK 保存失败，请稍后重试。", ex);
        }

        return new AppPackageInfoResponse(
                true,
                metadata.fileName(),
                metadata.fileSizeBytes(),
                metadata.uploadedAt(),
                PUBLIC_DOWNLOAD_PATH
        );
    }

    /**
     * 删除当前对外提供下载的正式安装包。     */
    public void deleteCurrentPackage() {
        try {
            Files.deleteIfExists(getCurrentPackagePath());
            Files.deleteIfExists(getMetadataPath());
        } catch (IOException ex) {
            throw new IllegalStateException("删除当前安装包失败，请稍后重试。", ex);
        }
    }

    /**
     * 以流的方式打开当前正式 APK，供公网下载接口直接回传。     */
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
                return new StoredPackageMetadata(fileName, fileSizeBytes, uploadedAt);
            }

            if (!Files.exists(packagePath)) {
                return null;
            }

            FileTime lastModifiedTime = Files.getLastModifiedTime(packagePath);
            return new StoredPackageMetadata(
                    CURRENT_PACKAGE_FILE_NAME,
                    Files.size(packagePath),
                    LocalDateTime.ofInstant(lastModifiedTime.toInstant(), ZoneId.systemDefault())
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

        try (OutputStream outputStream = Files.newOutputStream(getMetadataPath())) {
            properties.store(outputStream, "Alicia Cloud Storage APK metadata");
        }
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
            LocalDateTime uploadedAt
    ) {
    }
}
