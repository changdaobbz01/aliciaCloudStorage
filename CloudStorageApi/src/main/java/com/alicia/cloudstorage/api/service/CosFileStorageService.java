package com.alicia.cloudstorage.api.service;

import com.qcloud.cos.COSClient;
import com.qcloud.cos.ClientConfig;
import com.qcloud.cos.auth.BasicCOSCredentials;
import com.qcloud.cos.auth.COSCredentials;
import com.qcloud.cos.exception.CosClientException;
import com.qcloud.cos.http.HttpProtocol;
import com.qcloud.cos.model.AbortMultipartUploadRequest;
import com.qcloud.cos.model.CompleteMultipartUploadRequest;
import com.qcloud.cos.model.COSObject;
import com.qcloud.cos.model.COSObjectInputStream;
import com.qcloud.cos.model.InitiateMultipartUploadRequest;
import com.qcloud.cos.model.InitiateMultipartUploadResult;
import com.qcloud.cos.model.ObjectMetadata;
import com.qcloud.cos.model.PartETag;
import com.qcloud.cos.model.PutObjectRequest;
import com.qcloud.cos.model.UploadPartRequest;
import com.qcloud.cos.model.UploadPartResult;
import com.qcloud.cos.region.Region;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class CosFileStorageService {

    private final String secretId;
    private final String secretKey;
    private final String region;
    private final String bucket;
    private final long maxFileSizeBytes;

    public CosFileStorageService(
            @Value("${alicia.cos.secret-id:}") String secretId,
            @Value("${alicia.cos.secret-key:}") String secretKey,
            @Value("${alicia.cos.region:ap-shanghai}") String region,
            @Value("${alicia.cos.bucket:}") String bucket,
            @Value("${alicia.cos.max-file-size-bytes:104857600}") long maxFileSizeBytes
    ) {
        this.secretId = secretId;
        this.secretKey = secretKey;
        this.region = region;
        this.bucket = bucket;
        this.maxFileSizeBytes = maxFileSizeBytes;
    }

    /**
     * 将用户上传的文件写入腾讯 COS，并返回云端对象的元信息。
     */
    public StoredCosFile uploadUserFile(Long userId, MultipartFile file, String fileName) {
        validateCosConfig();
        validateUploadFile(file);

        String objectKey = buildObjectKey(userId, fileName);
        String contentType = resolveContentType(file.getContentType(), fileName);
        COSClient cosClient = createCosClient();

        try (InputStream inputStream = file.getInputStream()) {
            ObjectMetadata metadata = new ObjectMetadata();
            metadata.setContentLength(file.getSize());
            metadata.setContentType(contentType);

            PutObjectRequest request = new PutObjectRequest(bucket, objectKey, inputStream, metadata);
            cosClient.putObject(request);

            return new StoredCosFile(objectKey, contentType, file.getSize());
        } catch (IOException exception) {
            throw new IllegalArgumentException("读取上传文件失败。", exception);
        } catch (CosClientException exception) {
            throw buildCosStorageException("上传文件", exception);
        } finally {
            cosClient.shutdown();
        }
    }

    /**
     * 上传当前用户的头像图片到 COS。
     */
    public StoredCosFile uploadUserAvatar(Long userId, MultipartFile file) {
        validateCosConfig();
        validateAvatarFile(file);

        String fileName = file.getOriginalFilename() == null ? "avatar" : file.getOriginalFilename();
        String objectKey = buildAvatarObjectKey(userId, fileName);
        String contentType = resolveContentType(file.getContentType(), fileName);
        COSClient cosClient = createCosClient();

        try (InputStream inputStream = file.getInputStream()) {
            ObjectMetadata metadata = new ObjectMetadata();
            metadata.setContentLength(file.getSize());
            metadata.setContentType(contentType);

            cosClient.putObject(new PutObjectRequest(bucket, objectKey, inputStream, metadata));

            return new StoredCosFile(objectKey, contentType, file.getSize());
        } catch (IOException exception) {
            throw new IllegalArgumentException("读取头像文件失败。", exception);
        } catch (CosClientException exception) {
            throw buildCosStorageException("上传头像", exception);
        } finally {
            cosClient.shutdown();
        }
    }

    /**
     * 上传当前用户的主页背景图到 COS。
     */
    public StoredCosFile uploadUserHomeBackground(Long userId, MultipartFile file) {
        validateCosConfig();
        validateHomeBackgroundFile(file);

        String fileName = file.getOriginalFilename() == null ? "home-background" : file.getOriginalFilename();
        String objectKey = buildHomeBackgroundObjectKey(userId, fileName);
        String contentType = resolveContentType(file.getContentType(), fileName);
        COSClient cosClient = createCosClient();

        try (InputStream inputStream = file.getInputStream()) {
            ObjectMetadata metadata = new ObjectMetadata();
            metadata.setContentLength(file.getSize());
            metadata.setContentType(contentType);

            cosClient.putObject(new PutObjectRequest(bucket, objectKey, inputStream, metadata));

            return new StoredCosFile(objectKey, contentType, file.getSize());
        } catch (IOException exception) {
            throw new IllegalArgumentException("读取背景图文件失败。", exception);
        } catch (CosClientException exception) {
            throw buildCosStorageException("上传背景图", exception);
        } finally {
            cosClient.shutdown();
        }
    }

    /**
     * 在腾讯 COS 创建分片上传任务，并返回后续上传分片所需的 uploadId 和对象键。
     */
    public StoredCosMultipartUpload initiateMultipartUpload(
            Long userId,
            String fileName,
            String rawContentType,
            long fileSize,
            long chunkSize
    ) {
        validateCosConfig();
        validateUploadFileSize(fileSize);

        String objectKey = buildObjectKey(userId, fileName);
        String contentType = resolveContentType(rawContentType, fileName);
        COSClient cosClient = createCosClient();

        try {
            ObjectMetadata metadata = new ObjectMetadata();
            metadata.setContentLength(fileSize);
            metadata.setContentType(contentType);

            InitiateMultipartUploadRequest request = new InitiateMultipartUploadRequest(bucket, objectKey, metadata)
                    .withDataSizePartSize(fileSize, chunkSize);
            InitiateMultipartUploadResult result = cosClient.initiateMultipartUpload(request);

            return new StoredCosMultipartUpload(objectKey, result.getUploadId(), contentType, fileSize);
        } catch (CosClientException exception) {
            throw buildCosStorageException("准备分片上传", exception);
        } finally {
            cosClient.shutdown();
        }
    }

    /**
     * 将一个分片写入已初始化的腾讯 COS 分片上传任务。
     */
    public StoredCosPart uploadMultipartPart(
            String objectKey,
            String uploadId,
            int partNumber,
            long partSize,
            boolean lastPart,
            InputStream inputStream
    ) {
        validateCosConfig();

        if (!hasText(objectKey) || !hasText(uploadId)) {
            throw new IllegalArgumentException("分片上传会话不完整。");
        }

        if (partSize <= 0) {
            throw new IllegalArgumentException("分片内容不能为空。");
        }

        COSClient cosClient = createCosClient();

        try {
            UploadPartRequest request = new UploadPartRequest()
                    .withBucketName(bucket)
                    .withKey(objectKey)
                    .withUploadId(uploadId)
                    .withPartNumber(partNumber)
                    .withPartSize(partSize)
                    .withLastPart(lastPart)
                    .withInputStream(inputStream);
            UploadPartResult result = cosClient.uploadPart(request);

            return new StoredCosPart(result.getPartNumber(), result.getETag(), partSize);
        } catch (CosClientException exception) {
            throw buildCosStorageException("上传文件分片", exception);
        } finally {
            cosClient.shutdown();
        }
    }

    /**
     * 通知腾讯 COS 合并已经上传完成的所有分片。
     */
    public void completeMultipartUpload(String objectKey, String uploadId, List<StoredCosPart> parts) {
        validateCosConfig();

        if (!hasText(objectKey) || !hasText(uploadId) || parts == null || parts.isEmpty()) {
            throw new IllegalArgumentException("分片上传会话不完整。");
        }

        List<PartETag> partETags = new ArrayList<>(parts.stream()
                .map(part -> new PartETag(part.partNumber(), part.eTag()))
                .toList());
        COSClient cosClient = createCosClient();

        try {
            CompleteMultipartUploadRequest request = new CompleteMultipartUploadRequest(
                    bucket,
                    objectKey,
                    uploadId,
                    partETags
            );
            cosClient.completeMultipartUpload(request);
        } catch (CosClientException exception) {
            throw buildCosStorageException("合并文件分片", exception);
        } finally {
            cosClient.shutdown();
        }
    }

    /**
     * 取消腾讯 COS 上未完成的分片上传任务。
     */
    public void abortMultipartUploadQuietly(String objectKey, String uploadId) {
        if (!hasText(objectKey) || !hasText(uploadId) || !hasText(secretId) || !hasText(secretKey) || !hasText(region) || !hasText(bucket)) {
            return;
        }

        COSClient cosClient = createCosClient();

        try {
            cosClient.abortMultipartUpload(new AbortMultipartUploadRequest(bucket.trim(), objectKey.trim(), uploadId.trim()));
        } catch (Exception ignored) {
            // 清理未完成的分片上传失败时不影响主流程。
        } finally {
            cosClient.shutdown();
        }
    }

    /**
     * 从腾讯 COS 打开文件流，供下载接口直接回传给前端。
     */
    public DownloadedCosFile openFileStream(String objectKey) {
        validateCosConfig();

        COSClient cosClient = createCosClient();

        try {
            COSObject cosObject = cosClient.getObject(bucket, objectKey);
            ObjectMetadata metadata = cosObject.getObjectMetadata();
            COSObjectInputStream objectInputStream = cosObject.getObjectContent();
            InputStream safeInputStream = wrapCosStream(objectInputStream, cosObject, cosClient);

            return new DownloadedCosFile(
                    safeInputStream,
                    metadata.getContentType(),
                    metadata.getContentLength()
            );
        } catch (CosClientException exception) {
            cosClient.shutdown();
            throw buildCosStorageException("读取文件", exception);
        }
    }

    /**
     * 当元数据入库失败时，尝试删除已经上传到 COS 的孤立对象。
     */
    public void deleteObjectQuietly(String objectKey) {
        if (!hasText(objectKey) || !hasText(secretId) || !hasText(secretKey) || !hasText(region) || !hasText(bucket)) {
            return;
        }

        COSClient cosClient = createCosClient();

        try {
            cosClient.deleteObject(bucket.trim(), objectKey.trim());
        } catch (Exception ignored) {
            // 清理孤立对象失败时不影响主流程。
        } finally {
            cosClient.shutdown();
        }
    }

    /**
     * 校验当前环境里的 COS 凭证和桶配置是否完整。
     */
    private void validateCosConfig() {
        if (!hasText(secretId) || !hasText(secretKey) || !hasText(region) || !hasText(bucket)) {
            throw new IllegalArgumentException("腾讯 COS 配置不完整。");
        }
    }

    /**
     * 校验上传文件是否为空，以及大小是否超过系统限制。
     */
    private void validateUploadFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("请选择要上传的文件。");
        }

        validateUploadFileSize(file.getSize());
    }

    private void validateAvatarFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("请选择头像图片。");
        }

        long maxAvatarSize = 2L * 1024L * 1024L;
        if (file.getSize() > maxAvatarSize) {
            throw new IllegalArgumentException("头像图片不能超过 2 MB。");
        }

        String contentType = file.getContentType() == null ? "" : file.getContentType().trim().toLowerCase(Locale.ROOT);
        if (!List.of("image/jpeg", "image/png", "image/gif", "image/webp").contains(contentType)) {
            throw new IllegalArgumentException("头像只支持 JPG、PNG、GIF 或 WebP 图片。");
        }
    }

    private void validateHomeBackgroundFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("请选择主页背景图。");
        }

        long maxBackgroundSize = 10L * 1024L * 1024L;
        if (file.getSize() > maxBackgroundSize) {
            throw new IllegalArgumentException("主页背景图不能超过 10 MB。");
        }

        String contentType = file.getContentType() == null ? "" : file.getContentType().trim().toLowerCase(Locale.ROOT);
        if (!List.of("image/jpeg", "image/png", "image/gif", "image/webp").contains(contentType)) {
            throw new IllegalArgumentException("主页背景图只支持 JPG、PNG、GIF 或 WebP 图片。");
        }
    }

    private void validateUploadFileSize(long fileSize) {
        if (fileSize <= 0) {
            throw new IllegalArgumentException("请选择要上传的文件。");
        }

        if (fileSize > maxFileSizeBytes) {
            long maxMb = Math.max(1L, maxFileSizeBytes / 1024 / 1024);
            throw new IllegalArgumentException("文件大小不能超过 " + maxMb + " MB。");
        }
    }

    /**
     * 生成用户文件在 COS 中的对象键，按用户和日期分层存放。
     */
    private String buildObjectKey(Long userId, String fileName) {
        String extension = extractExtension(fileName);
        LocalDate today = LocalDate.now();
        String fileId = UUID.randomUUID().toString().replace("-", "");

        return "user-files/%d/%d/%02d/%02d/%s%s".formatted(
                userId,
                today.getYear(),
                today.getMonthValue(),
                today.getDayOfMonth(),
                fileId,
                extension
        );
    }

    private String buildAvatarObjectKey(Long userId, String fileName) {
        String extension = extractExtension(fileName);
        String avatarId = UUID.randomUUID().toString().replace("-", "");

        return "user-avatars/%d/%s%s".formatted(userId, avatarId, extension);
    }

    private String buildHomeBackgroundObjectKey(Long userId, String fileName) {
        String extension = extractExtension(fileName);
        String backgroundId = UUID.randomUUID().toString().replace("-", "");

        return "user-home-backgrounds/%d/%s%s".formatted(userId, backgroundId, extension);
    }

    /**
     * 根据请求内容或文件后缀推断文件的 MIME 类型。
     */
    private String resolveContentType(String rawContentType, String fileName) {
        if (hasText(rawContentType) && !"application/octet-stream".equalsIgnoreCase(rawContentType.trim())) {
            return rawContentType.trim();
        }

        String extension = extractExtension(fileName);
        return switch (extension.toLowerCase(Locale.ROOT)) {
            case ".jpg", ".jpeg" -> "image/jpeg";
            case ".png" -> "image/png";
            case ".gif" -> "image/gif";
            case ".webp" -> "image/webp";
            case ".pdf" -> "application/pdf";
            case ".txt" -> "text/plain";
            case ".md" -> "text/markdown";
            case ".doc" -> "application/msword";
            case ".docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
            case ".xls" -> "application/vnd.ms-excel";
            case ".xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
            case ".ppt" -> "application/vnd.ms-powerpoint";
            case ".pptx" -> "application/vnd.openxmlformats-officedocument.presentationml.presentation";
            case ".zip" -> "application/zip";
            case ".mp4" -> "video/mp4";
            case ".mp3" -> "audio/mpeg";
            default -> "application/octet-stream";
        };
    }

    /**
     * 为 COS 下载流包一层关闭逻辑，确保响应结束后客户端资源被正确释放。
     */
    private InputStream wrapCosStream(COSObjectInputStream objectInputStream, COSObject cosObject, COSClient cosClient) {
        return new FilterInputStream(objectInputStream) {
            @Override
            public void close() throws IOException {
                try {
                    super.close();
                } finally {
                    try {
                        cosObject.close();
                    } finally {
                        cosClient.shutdown();
                    }
                }
            }
        };
    }

    /**
     * 按照腾讯 COS SDK 要求创建客户端实例。
     */
    private COSClient createCosClient() {
        COSCredentials credentials = new BasicCOSCredentials(secretId.trim(), secretKey.trim());
        ClientConfig config = new ClientConfig(new Region(region.trim()));
        config.setHttpProtocol(HttpProtocol.https);
        return new COSClient(credentials, config);
    }

    /**
     * 从文件名中提取后缀，提取不到时返回空字符串。
     */
    private String extractExtension(String fileName) {
        if (!hasText(fileName)) {
            return "";
        }

        int dotIndex = fileName.lastIndexOf('.');
        if (dotIndex < 0 || dotIndex == fileName.length() - 1) {
            return "";
        }

        return fileName.substring(dotIndex).toLowerCase(Locale.ROOT);
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
            return new CosStorageException("连接云存储超时，请稍后继续上传。", exception);
        }

        return new CosStorageException("云存储" + action + "失败，请稍后重试。", exception);
    }

    /**
     * 判断字符串是否包含有效文本内容。
     */
    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    public record StoredCosFile(String objectKey, String contentType, long contentLength) {
    }

    public record StoredCosMultipartUpload(String objectKey, String uploadId, String contentType, long contentLength) {
    }

    public record StoredCosPart(int partNumber, String eTag, long partSize) {
    }

    public record DownloadedCosFile(InputStream inputStream, String contentType, long contentLength) {
    }
}
