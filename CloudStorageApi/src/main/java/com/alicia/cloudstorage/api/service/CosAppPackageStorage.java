package com.alicia.cloudstorage.api.service;

import com.qcloud.cos.COSClient;
import com.qcloud.cos.ClientConfig;
import com.qcloud.cos.auth.BasicCOSCredentials;
import com.qcloud.cos.auth.COSCredentials;
import com.qcloud.cos.endpoint.UserSpecifiedEndpointBuilder;
import com.qcloud.cos.exception.CosClientException;
import com.qcloud.cos.http.HttpMethodName;
import com.qcloud.cos.http.HttpProtocol;
import com.qcloud.cos.model.GeneratePresignedUrlRequest;
import com.qcloud.cos.model.ObjectMetadata;
import com.qcloud.cos.model.PutObjectRequest;
import com.qcloud.cos.model.ResponseHeaderOverrides;
import com.qcloud.cos.region.Region;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ContentDisposition;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Date;
import java.util.Locale;
import java.util.UUID;

@Service
public class CosAppPackageStorage implements AppPackageStorage {

    static final String APP_PACKAGE_MEDIA_TYPE = "application/vnd.android.package-archive";

    private final String secretId;
    private final String secretKey;
    private final String region;
    private final String bucket;
    private final String customDomain;
    private final String objectPrefix;
    private final long downloadUrlExpireSeconds;

    public CosAppPackageStorage(
            @Value("${alicia.cos.secret-id:}") String secretId,
            @Value("${alicia.cos.secret-key:}") String secretKey,
            @Value("${alicia.cos.region:ap-shanghai}") String region,
            @Value("${alicia.cos.bucket:}") String bucket,
            @Value("${alicia.cos.custom-domain:}") String customDomain,
            @Value("${alicia.app-package.cos-prefix:app-packages}") String objectPrefix,
            @Value("${alicia.app-package.download-url-expire-seconds:1800}") long downloadUrlExpireSeconds
    ) {
        this.secretId = secretId;
        this.secretKey = secretKey;
        this.region = region;
        this.bucket = bucket;
        this.customDomain = customDomain;
        this.objectPrefix = normalizeObjectPrefix(objectPrefix);
        this.downloadUrlExpireSeconds = downloadUrlExpireSeconds;
    }

    @Override
    public StoredAppPackage store(MultipartFile file, String fileName) {
        validateCosConfig();

        String objectKey = buildObjectKey();
        COSClient cosClient = createCosClient();

        try (InputStream inputStream = file.getInputStream()) {
            ObjectMetadata metadata = new ObjectMetadata();
            metadata.setContentLength(file.getSize());
            metadata.setContentType(APP_PACKAGE_MEDIA_TYPE);
            metadata.setContentDisposition(
                    ContentDisposition.attachment()
                            .filename(fileName, StandardCharsets.UTF_8)
                            .build()
                            .toString()
            );

            cosClient.putObject(new PutObjectRequest(bucket.trim(), objectKey, inputStream, metadata));
            return new StoredAppPackage(objectKey, APP_PACKAGE_MEDIA_TYPE, file.getSize());
        } catch (IOException exception) {
            throw new IllegalArgumentException("读取 APK 安装包失败。", exception);
        } catch (CosClientException exception) {
            throw buildCosStorageException("上传 APK", exception);
        } finally {
            cosClient.shutdown();
        }
    }

    @Override
    public AppPackageDownloadLink createDownloadLink(String objectKey, String fileName, String contentType) {
        validateCosConfig();

        if (!hasText(objectKey)) {
            throw new IllegalArgumentException("APK 对象键不能为空。");
        }

        if (downloadUrlExpireSeconds <= 0) {
            throw new IllegalArgumentException("APK 下载链接有效期配置必须大于 0。");
        }

        Date expiration = new Date(System.currentTimeMillis() + downloadUrlExpireSeconds * 1000L);
        COSClient cosClient = createPresignedUrlCosClient();

        try {
            GeneratePresignedUrlRequest request = new GeneratePresignedUrlRequest(
                    bucket.trim(),
                    objectKey.trim(),
                    HttpMethodName.GET
            );
            request.setExpiration(expiration);

            ResponseHeaderOverrides responseHeaders = new ResponseHeaderOverrides();
            responseHeaders.setContentType(hasText(contentType) ? contentType.trim() : APP_PACKAGE_MEDIA_TYPE);
            responseHeaders.setContentDisposition(
                    ContentDisposition.attachment()
                            .filename(fileName, StandardCharsets.UTF_8)
                            .build()
                            .toString()
            );
            request.setResponseHeaders(responseHeaders);

            URL url = cosClient.generatePresignedUrl(request);
            return new AppPackageDownloadLink(URI.create(url.toString()), expiration.getTime());
        } catch (CosClientException exception) {
            throw buildCosStorageException("生成 APK 下载链接", exception);
        } finally {
            cosClient.shutdown();
        }
    }

    @Override
    public void deleteObjectQuietly(String objectKey) {
        if (!hasText(objectKey) || !hasText(secretId) || !hasText(secretKey) || !hasText(region) || !hasText(bucket)) {
            return;
        }

        try {
            COSClient cosClient = createCosClient();
            try {
                cosClient.deleteObject(bucket.trim(), objectKey.trim());
            } finally {
                cosClient.shutdown();
            }
        } catch (Exception ignored) {
            // 删除补偿失败不影响主流程，COS 私有对象不会再被当前版本记录引用。
        }
    }

    private String buildObjectKey() {
        LocalDate today = LocalDate.now();
        String packageId = UUID.randomUUID().toString().replace("-", "");

        return "%s/releases/%d/%02d/%02d/%s.apk".formatted(
                objectPrefix,
                today.getYear(),
                today.getMonthValue(),
                today.getDayOfMonth(),
                packageId
        );
    }

    private String normalizeObjectPrefix(String rawPrefix) {
        String normalized = hasText(rawPrefix) ? rawPrefix.trim().replace('\\', '/') : "app-packages";
        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        if (!hasText(normalized) || normalized.contains("..")) {
            throw new IllegalArgumentException("APK COS 对象前缀配置不合法。");
        }
        return normalized;
    }

    private void validateCosConfig() {
        if (!hasText(secretId) || !hasText(secretKey) || !hasText(region) || !hasText(bucket)) {
            throw new IllegalArgumentException("腾讯 COS 配置不完整。");
        }
    }

    private COSClient createCosClient() {
        COSCredentials credentials = new BasicCOSCredentials(secretId.trim(), secretKey.trim());
        return new COSClient(credentials, createBaseClientConfig());
    }

    private COSClient createPresignedUrlCosClient() {
        COSCredentials credentials = new BasicCOSCredentials(secretId.trim(), secretKey.trim());
        ClientConfig config = createBaseClientConfig();
        String endpoint = normalizeCustomDomainEndpoint(customDomain);

        if (hasText(endpoint)) {
            config.setEndpointBuilder(new UserSpecifiedEndpointBuilder(endpoint, endpoint));
        }

        return new COSClient(credentials, config);
    }

    private ClientConfig createBaseClientConfig() {
        ClientConfig config = new ClientConfig(new Region(region.trim()));
        config.setHttpProtocol(HttpProtocol.https);
        return config;
    }

    private String normalizeCustomDomainEndpoint(String rawCustomDomain) {
        if (!hasText(rawCustomDomain)) {
            return "";
        }

        String trimmed = rawCustomDomain.trim();

        if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            URI uri = URI.create(trimmed);
            if (!hasText(uri.getHost())) {
                throw new IllegalArgumentException("COS custom domain host is invalid.");
            }
            if (hasText(uri.getRawPath()) && !"/".equals(uri.getRawPath())) {
                throw new IllegalArgumentException("COS custom domain must not include a path.");
            }
            if (hasText(uri.getRawQuery()) || hasText(uri.getRawFragment())) {
                throw new IllegalArgumentException("COS custom domain must not include query or fragment.");
            }
            return uri.getPort() > 0 ? uri.getHost() + ":" + uri.getPort() : uri.getHost();
        }

        String endpoint = trimmed;
        while (endpoint.endsWith("/")) {
            endpoint = endpoint.substring(0, endpoint.length() - 1);
        }
        if (!hasText(endpoint)) {
            throw new IllegalArgumentException("COS custom domain host is invalid.");
        }
        if (endpoint.contains("/") || endpoint.contains("://")) {
            throw new IllegalArgumentException("COS custom domain must be a host name only.");
        }

        return endpoint;
    }

    private CosStorageException buildCosStorageException(String action, CosClientException exception) {
        String detail = exception.getMessage() == null ? "" : exception.getMessage();
        String normalizedDetail = detail.toLowerCase(Locale.ROOT);

        if (normalizedDetail.contains("accessdenied")
                || normalizedDetail.contains("signaturedoesnotmatch")
                || normalizedDetail.contains("invalidaccesskeyid")
                || normalizedDetail.contains("403")) {
            return new CosStorageException("云存储鉴权失败，请检查腾讯 COS 密钥和存储桶权限。", exception);
        }

        if (normalizedDetail.contains("nosuchbucket")
                || normalizedDetail.contains("bucket")
                || normalizedDetail.contains("404")) {
            return new CosStorageException("云存储桶不存在或配置不正确，请检查腾讯 COS Bucket 配置。", exception);
        }

        if (normalizedDetail.contains("timeout")
                || normalizedDetail.contains("timed out")
                || normalizedDetail.contains("connection")
                || normalizedDetail.contains("socket")) {
            return new CosStorageException("连接云存储超时，请稍后继续操作。", exception);
        }

        return new CosStorageException("云存储" + action + "失败，请稍后重试。", exception);
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
