package com.alicia.cloudstorage.api.service;

import com.alicia.cloudstorage.api.dto.ApiMessageResponse;
import com.alicia.cloudstorage.api.dto.CreateMultipartUploadRequest;
import com.alicia.cloudstorage.api.dto.MultipartUploadPartResponse;
import com.alicia.cloudstorage.api.dto.MultipartUploadStatusResponse;
import com.alicia.cloudstorage.api.dto.StorageNodeSummaryResponse;
import com.alicia.cloudstorage.api.entity.MultipartUploadPart;
import com.alicia.cloudstorage.api.entity.MultipartUploadSession;
import com.alicia.cloudstorage.api.entity.MultipartUploadStatus;
import com.alicia.cloudstorage.api.entity.NodeType;
import com.alicia.cloudstorage.api.entity.StorageNode;
import com.alicia.cloudstorage.api.repository.MultipartUploadPartRepository;
import com.alicia.cloudstorage.api.repository.MultipartUploadSessionRepository;
import com.alicia.cloudstorage.api.repository.StorageNodeRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class StorageMultipartUploadService {

    private static final Logger log = LoggerFactory.getLogger(StorageMultipartUploadService.class);
    private static final long MIN_CHUNK_SIZE_BYTES = 1024L * 1024L;
    private static final int MAX_CHUNK_COUNT = 10_000;

    private final StorageNodeRepository storageNodeRepository;
    private final MultipartUploadSessionRepository multipartUploadSessionRepository;
    private final MultipartUploadPartRepository multipartUploadPartRepository;
    private final CosFileStorageService cosFileStorageService;
    private final StorageQuotaService storageQuotaService;
    private final boolean cleanupEnabled;
    private final long staleHours;

    public StorageMultipartUploadService(
            StorageNodeRepository storageNodeRepository,
            MultipartUploadSessionRepository multipartUploadSessionRepository,
            MultipartUploadPartRepository multipartUploadPartRepository,
            CosFileStorageService cosFileStorageService,
            StorageQuotaService storageQuotaService,
            @Value("${alicia.multipart-upload.cleanup-enabled:true}") boolean cleanupEnabled,
            @Value("${alicia.multipart-upload.stale-hours:24}") long staleHours
    ) {
        this.storageNodeRepository = storageNodeRepository;
        this.multipartUploadSessionRepository = multipartUploadSessionRepository;
        this.multipartUploadPartRepository = multipartUploadPartRepository;
        this.cosFileStorageService = cosFileStorageService;
        this.storageQuotaService = storageQuotaService;
        this.cleanupEnabled = cleanupEnabled;
        this.staleHours = staleHours;
    }

    /**
     * 初始化或复用当前用户的 COS 分片上传会话。
     */
    @Transactional
    public MultipartUploadStatusResponse createMultipartUpload(Long userId, CreateMultipartUploadRequest request) {
        Long parentId = validateParentFolder(userId, request.parentId());
        String fileName = extractFileName(request.fileName());
        String fingerprint = normalizeFingerprint(request.fingerprint());
        long fileSize = request.fileSize();
        long chunkSize = request.chunkSize();
        int totalChunks = request.totalChunks();

        validateChunkPlan(fileSize, chunkSize, totalChunks);
        validateSiblingNameUnique(userId, parentId, fileName);
        storageQuotaService.validateUploadFits(userId, fileSize);

        Long parentScopeId = parentId == null ? 0L : parentId;
        return multipartUploadSessionRepository
                .findFirstByOwnerIdAndParentScopeIdAndFileNameAndFileSizeAndFileFingerprintAndStatusOrderByUpdatedAtDesc(
                        userId,
                        parentScopeId,
                        fileName,
                        fileSize,
                        fingerprint,
                        MultipartUploadStatus.IN_PROGRESS
                )
                .map(this::toStatusResponse)
                .orElseGet(() -> createNewSession(userId, parentId, fileName, fingerprint, fileSize, chunkSize, totalChunks, request.contentType()));
    }

    /**
     * 查询分片上传会话和已成功上传的分片列表，供前端断点续传。
     */
    @Transactional(readOnly = true)
    public MultipartUploadStatusResponse getMultipartUploadStatus(Long userId, String uploadToken) {
        return toStatusResponse(loadSession(userId, uploadToken));
    }

    /**
     * 上传单个分片。重复上传同一 partNumber 时会覆盖 COS 与数据库中的分片记录。
     */
    @Transactional
    public MultipartUploadPartResponse uploadPart(
            Long userId,
            String uploadToken,
            int partNumber,
            HttpServletRequest servletRequest
    ) {
        MultipartUploadSession session = loadSession(userId, uploadToken);
        validateSessionInProgress(session);
        validatePartNumber(session, partNumber);

        long contentLength = servletRequest.getContentLengthLong();
        validatePartSize(session, partNumber, contentLength);

        try (InputStream inputStream = servletRequest.getInputStream()) {
            boolean lastPart = partNumber == session.getTotalChunks();
            CosFileStorageService.StoredCosPart storedPart = cosFileStorageService.uploadMultipartPart(
                    session.getObjectKey(),
                    session.getCosUploadId(),
                    partNumber,
                    contentLength,
                    lastPart,
                    inputStream
            );
            MultipartUploadPart part = multipartUploadPartRepository
                    .findBySessionIdAndPartNumber(session.getId(), partNumber)
                    .orElseGet(MultipartUploadPart::new);
            part.setSessionId(session.getId());
            part.setPartNumber(partNumber);
            part.setPartSize(storedPart.partSize());
            part.setETag(storedPart.eTag());

            return toPartResponse(multipartUploadPartRepository.save(part));
        } catch (IOException exception) {
            throw new IllegalArgumentException("读取文件分片失败。", exception);
        }
    }

    /**
     * 合并所有已上传分片，并在元数据表中创建正式文件节点。
     */
    @Transactional
    public StorageNodeSummaryResponse completeMultipartUpload(Long userId, String uploadToken) {
        MultipartUploadSession session = loadSession(userId, uploadToken);
        validateSessionInProgress(session);
        validateParentFolder(userId, session.getParentId());
        validateSiblingNameUnique(userId, session.getParentId(), session.getFileName());
        storageQuotaService.validateUploadFits(userId, session.getFileSize());

        List<MultipartUploadPart> parts = multipartUploadPartRepository.findBySessionIdOrderByPartNumberAsc(session.getId());
        validateCompleteParts(session, parts);
        List<CosFileStorageService.StoredCosPart> storedParts = parts.stream()
                .map(part -> new CosFileStorageService.StoredCosPart(part.getPartNumber(), part.getETag(), part.getPartSize()))
                .toList();

        cosFileStorageService.completeMultipartUpload(session.getObjectKey(), session.getCosUploadId(), storedParts);

        try {
            StorageNode fileNode = new StorageNode();
            fileNode.setOwnerId(userId);
            fileNode.setParentId(session.getParentId());
            fileNode.setNodeName(session.getFileName());
            fileNode.setNodeType(NodeType.FILE);
            fileNode.setFileSize(session.getFileSize());
            fileNode.setFileExtension(extractExtension(session.getFileName()));
            fileNode.setMimeType(session.getContentType());
            fileNode.setStoragePath(session.getObjectKey());
            clearTrashMetadata(fileNode);

            StorageNode savedNode = storageNodeRepository.save(fileNode);
            session.setStatus(MultipartUploadStatus.COMPLETED);
            multipartUploadSessionRepository.save(session);

            return toSummary(savedNode);
        } catch (RuntimeException exception) {
            cosFileStorageService.deleteObjectQuietly(session.getObjectKey());
            throw exception;
        }
    }

    /**
     * 取消未完成的分片上传会话，并尝试清理 COS 上的未合并分片。
     */
    @Transactional
    public ApiMessageResponse abortMultipartUpload(Long userId, String uploadToken) {
        MultipartUploadSession session = loadSession(userId, uploadToken);

        if (session.getStatus() == MultipartUploadStatus.COMPLETED) {
            throw new IllegalArgumentException("文件已经上传完成，不能取消。");
        }

        if (session.getStatus() == MultipartUploadStatus.IN_PROGRESS) {
            cosFileStorageService.abortMultipartUploadQuietly(session.getObjectKey(), session.getCosUploadId());
            session.setStatus(MultipartUploadStatus.ABORTED);
            multipartUploadSessionRepository.save(session);
            multipartUploadPartRepository.deleteBySessionId(session.getId());
        }

        return new ApiMessageResponse("分片上传已取消。");
    }

    /**
     * 定期清理长时间未完成的分片上传，避免 COS 残留未合并分片占用空间。
     */
    @Scheduled(
            fixedDelayString = "${alicia.multipart-upload.cleanup-fixed-delay-ms:3600000}",
            initialDelayString = "${alicia.multipart-upload.cleanup-initial-delay-ms:300000}"
    )
    @Transactional
    public void cleanupStaleMultipartUploads() {
        if (!cleanupEnabled) {
            return;
        }

        LocalDateTime cutoff = LocalDateTime.now().minusHours(Math.max(1L, staleHours));
        List<MultipartUploadSession> staleSessions = multipartUploadSessionRepository.findByStatusAndUpdatedAtBefore(
                MultipartUploadStatus.IN_PROGRESS,
                cutoff
        );

        for (MultipartUploadSession session : staleSessions) {
            cosFileStorageService.abortMultipartUploadQuietly(session.getObjectKey(), session.getCosUploadId());
            session.setStatus(MultipartUploadStatus.ABORTED);
            multipartUploadSessionRepository.save(session);
            multipartUploadPartRepository.deleteBySessionId(session.getId());
            log.info("Cleaned stale multipart upload session id={}, fileName={}", session.getId(), session.getFileName());
        }
    }

    private MultipartUploadStatusResponse createNewSession(
            Long userId,
            Long parentId,
            String fileName,
            String fingerprint,
            long fileSize,
            long chunkSize,
            int totalChunks,
            String contentType
    ) {
        CosFileStorageService.StoredCosMultipartUpload storedUpload = cosFileStorageService.initiateMultipartUpload(
                userId,
                fileName,
                contentType,
                fileSize,
                chunkSize
        );

        try {
            MultipartUploadSession session = new MultipartUploadSession();
            session.setOwnerId(userId);
            session.setParentId(parentId);
            session.setUploadToken(UUID.randomUUID().toString());
            session.setCosUploadId(storedUpload.uploadId());
            session.setObjectKey(storedUpload.objectKey());
            session.setFileName(fileName);
            session.setFileSize(fileSize);
            session.setContentType(storedUpload.contentType());
            session.setChunkSize(chunkSize);
            session.setTotalChunks(totalChunks);
            session.setFileFingerprint(fingerprint);
            session.setStatus(MultipartUploadStatus.IN_PROGRESS);

            return toStatusResponse(multipartUploadSessionRepository.save(session));
        } catch (RuntimeException exception) {
            cosFileStorageService.abortMultipartUploadQuietly(storedUpload.objectKey(), storedUpload.uploadId());
            throw exception;
        }
    }

    private MultipartUploadSession loadSession(Long userId, String uploadToken) {
        if (uploadToken == null || uploadToken.isBlank()) {
            throw new IllegalArgumentException("上传会话不能为空。");
        }

        return multipartUploadSessionRepository.findByUploadTokenAndOwnerId(uploadToken.trim(), userId)
                .orElseThrow(() -> new IllegalArgumentException("上传会话不存在或已失效。"));
    }

    private void validateSessionInProgress(MultipartUploadSession session) {
        if (session.getStatus() != MultipartUploadStatus.IN_PROGRESS) {
            throw new IllegalArgumentException("上传会话已结束，不能继续操作。");
        }
    }

    private void validatePartNumber(MultipartUploadSession session, int partNumber) {
        if (partNumber < 1 || partNumber > session.getTotalChunks()) {
            throw new IllegalArgumentException("分片编号不合法。");
        }
    }

    private void validatePartSize(MultipartUploadSession session, int partNumber, long contentLength) {
        if (contentLength <= 0) {
            throw new IllegalArgumentException("分片内容不能为空。");
        }

        long expectedSize = expectedPartSize(session, partNumber);
        if (contentLength != expectedSize) {
            throw new IllegalArgumentException("分片大小与上传计划不一致。");
        }
    }

    private long expectedPartSize(MultipartUploadSession session, int partNumber) {
        long offset = (long) (partNumber - 1) * session.getChunkSize();
        long remaining = session.getFileSize() - offset;
        return Math.min(session.getChunkSize(), remaining);
    }

    private void validateChunkPlan(long fileSize, long chunkSize, int totalChunks) {
        if (fileSize <= 0) {
            throw new IllegalArgumentException("请选择要上传的文件。");
        }

        if (chunkSize <= 0) {
            throw new IllegalArgumentException("分片大小必须大于 0。");
        }

        if (totalChunks <= 0 || totalChunks > MAX_CHUNK_COUNT) {
            throw new IllegalArgumentException("分片数量必须介于 1 到 10000 之间。");
        }

        long expectedTotalChunks = (fileSize + chunkSize - 1) / chunkSize;
        if (expectedTotalChunks != totalChunks) {
            throw new IllegalArgumentException("分片数量与文件大小不匹配。");
        }

        if (totalChunks > 1 && chunkSize < MIN_CHUNK_SIZE_BYTES) {
            throw new IllegalArgumentException("分片大小不能小于 1 MB。");
        }
    }

    private void validateCompleteParts(MultipartUploadSession session, List<MultipartUploadPart> parts) {
        if (parts.size() != session.getTotalChunks()) {
            throw new IllegalArgumentException("文件分片尚未全部上传完成。");
        }

        for (int index = 0; index < parts.size(); index += 1) {
            int expectedPartNumber = index + 1;
            MultipartUploadPart part = parts.get(index);

            if (part.getPartNumber() != expectedPartNumber || part.getPartSize() != expectedPartSize(session, expectedPartNumber)) {
                throw new IllegalArgumentException("文件分片尚未全部上传完成。");
            }
        }
    }

    private Long validateParentFolder(Long userId, Long parentId) {
        if (parentId == null) {
            return null;
        }

        StorageNode parentNode = storageNodeRepository.findByIdAndOwnerIdAndDeletedFalse(parentId, userId)
                .orElseThrow(() -> new IllegalArgumentException("父级文件夹不存在。"));

        if (parentNode.getNodeType() != NodeType.FOLDER) {
            throw new IllegalArgumentException("只能在文件夹内创建内容。");
        }

        return parentId;
    }

    private void validateSiblingNameUnique(Long userId, Long parentId, String nodeName) {
        if (storageNodeRepository.existsActiveSiblingName(userId, parentId, nodeName)) {
            throw new IllegalArgumentException("当前目录下已存在同名文件或文件夹。");
        }
    }

    private String extractFileName(String originalFilename) {
        if (originalFilename == null || originalFilename.isBlank()) {
            throw new IllegalArgumentException("上传文件缺少文件名。");
        }

        String normalized = originalFilename.replace("\\", "/");
        int slashIndex = normalized.lastIndexOf('/');
        String fileName = slashIndex >= 0 ? normalized.substring(slashIndex + 1) : normalized;
        fileName = fileName.trim();

        if (fileName.isBlank()) {
            throw new IllegalArgumentException("上传文件缺少文件名。");
        }

        return normalizeNodeName(fileName, "文件名称");
    }

    private String normalizeNodeName(String rawNodeName, String fieldName) {
        if (rawNodeName == null || rawNodeName.isBlank()) {
            throw new IllegalArgumentException(fieldName + "不能为空。");
        }

        String nodeName = rawNodeName.trim();
        if (nodeName.length() > 255) {
            throw new IllegalArgumentException(fieldName + "长度不能超过 255 个字符。");
        }

        if (nodeName.contains("/") || nodeName.contains("\\")) {
            throw new IllegalArgumentException(fieldName + "不能包含斜杠。");
        }

        return nodeName;
    }

    private String normalizeFingerprint(String rawFingerprint) {
        String fingerprint = rawFingerprint == null ? "" : rawFingerprint.trim();

        if (fingerprint.isBlank()) {
            throw new IllegalArgumentException("文件指纹不能为空。");
        }

        if (fingerprint.length() > 128) {
            throw new IllegalArgumentException("文件指纹长度不能超过 128 个字符。");
        }

        return fingerprint;
    }

    private String extractExtension(String fileName) {
        int dotIndex = fileName.lastIndexOf('.');
        if (dotIndex < 0 || dotIndex == fileName.length() - 1) {
            return null;
        }

        return fileName.substring(dotIndex + 1).toLowerCase(Locale.ROOT);
    }

    private void clearTrashMetadata(StorageNode node) {
        node.setDeleted(false);
        node.setDeletedAt(null);
        node.setDeletedBy(null);
        node.setOriginalParentId(null);
    }

    private MultipartUploadStatusResponse toStatusResponse(MultipartUploadSession session) {
        List<MultipartUploadPartResponse> uploadedParts = multipartUploadPartRepository
                .findBySessionIdOrderByPartNumberAsc(session.getId())
                .stream()
                .map(this::toPartResponse)
                .toList();

        return new MultipartUploadStatusResponse(
                session.getUploadToken(),
                session.getFileName(),
                session.getFileSize(),
                session.getContentType(),
                session.getChunkSize(),
                session.getTotalChunks(),
                uploadedParts,
                session.getStatus().name()
        );
    }

    private MultipartUploadPartResponse toPartResponse(MultipartUploadPart part) {
        return new MultipartUploadPartResponse(part.getPartNumber(), part.getETag(), part.getPartSize());
    }

    private StorageNodeSummaryResponse toSummary(StorageNode node) {
        return new StorageNodeSummaryResponse(
                node.getId(),
                node.getParentId(),
                node.getNodeName(),
                node.getNodeType().name(),
                node.getFileSize(),
                node.getFileExtension(),
                node.getMimeType(),
                node.getUpdatedAt(),
                node.getDeletedAt()
        );
    }
}
