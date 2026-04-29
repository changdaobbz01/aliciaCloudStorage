package com.alicia.cloudstorage.api.service;

import com.alicia.cloudstorage.api.dto.CreateMultipartUploadRequest;
import com.alicia.cloudstorage.api.entity.MultipartUploadPart;
import com.alicia.cloudstorage.api.entity.MultipartUploadSession;
import com.alicia.cloudstorage.api.entity.MultipartUploadStatus;
import com.alicia.cloudstorage.api.entity.StorageNode;
import com.alicia.cloudstorage.api.repository.MultipartUploadPartRepository;
import com.alicia.cloudstorage.api.repository.MultipartUploadSessionRepository;
import com.alicia.cloudstorage.api.repository.StorageNodeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StorageMultipartUploadServiceTest {

    @Mock
    private StorageNodeRepository storageNodeRepository;

    @Mock
    private MultipartUploadSessionRepository multipartUploadSessionRepository;

    @Mock
    private MultipartUploadPartRepository multipartUploadPartRepository;

    @Mock
    private CosFileStorageService cosFileStorageService;

    @Mock
    private StorageQuotaService storageQuotaService;

    private StorageMultipartUploadService storageMultipartUploadService;

    @BeforeEach
    void setUp() {
        storageMultipartUploadService = new StorageMultipartUploadService(
                storageNodeRepository,
                multipartUploadSessionRepository,
                multipartUploadPartRepository,
                cosFileStorageService,
                storageQuotaService,
                true,
                24L
        );
    }

    @Test
    void createMultipartUploadReusesExistingSessionAndReturnsUploadedParts() {
        Long userId = 21L;
        long chunkSize = 1024L * 1024L;
        long fileSize = chunkSize * 3;
        MultipartUploadSession session = uploadSession(101L, userId, null, "token-1", "movie.mp4", fileSize, chunkSize, 3);
        MultipartUploadPart uploadedPart = uploadPart(101L, 1, chunkSize, "etag-1");

        when(storageNodeRepository.existsActiveSiblingName(userId, null, "movie.mp4")).thenReturn(false);
        when(multipartUploadSessionRepository.findFirstByOwnerIdAndParentScopeIdAndFileNameAndFileSizeAndFileFingerprintAndStatusOrderByUpdatedAtDesc(
                userId,
                0L,
                "movie.mp4",
                fileSize,
                "fingerprint-1",
                MultipartUploadStatus.IN_PROGRESS
        )).thenReturn(Optional.of(session));
        when(multipartUploadPartRepository.findBySessionIdOrderByPartNumberAsc(101L)).thenReturn(List.of(uploadedPart));

        var response = storageMultipartUploadService.createMultipartUpload(
                userId,
                new CreateMultipartUploadRequest(null, "movie.mp4", fileSize, "video/mp4", chunkSize, 3, "fingerprint-1")
        );

        assertThat(response.uploadToken()).isEqualTo("token-1");
        assertThat(response.uploadedParts()).hasSize(1);
        assertThat(response.uploadedParts().get(0).partNumber()).isEqualTo(1);
        verify(cosFileStorageService, never()).initiateMultipartUpload(any(), any(), any(), anyLong(), anyLong());
    }

    @Test
    void createMultipartUploadRejectsWhenQuotaIsExceeded() {
        Long userId = 24L;
        long chunkSize = 1024L * 1024L;
        long fileSize = chunkSize * 2;

        when(storageNodeRepository.existsActiveSiblingName(userId, null, "backup.zip")).thenReturn(false);
        org.mockito.Mockito.doThrow(new IllegalArgumentException("剩余空间不足。"))
                .when(storageQuotaService)
                .validateUploadFits(userId, fileSize);

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> storageMultipartUploadService.createMultipartUpload(
                userId,
                new CreateMultipartUploadRequest(null, "backup.zip", fileSize, "application/zip", chunkSize, 2, "fingerprint-2")
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("剩余空间不足。");

        verify(cosFileStorageService, never()).initiateMultipartUpload(any(), any(), any(), anyLong(), anyLong());
    }

    @Test
    void uploadPartWritesChunkToCosAndSavesPartMetadata() {
        Long userId = 22L;
        MultipartUploadSession session = uploadSession(102L, userId, null, "token-2", "archive.zip", 8L, 5L, 2);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setContent(new byte[]{1, 2, 3});

        when(multipartUploadSessionRepository.findByUploadTokenAndOwnerId("token-2", userId)).thenReturn(Optional.of(session));
        when(cosFileStorageService.uploadMultipartPart(
                eq("cos/archive.zip"),
                eq("cos-upload-102"),
                eq(2),
                eq(3L),
                eq(true),
                any()
        ))
                .thenReturn(new CosFileStorageService.StoredCosPart(2, "etag-2", 3L));
        when(multipartUploadPartRepository.findBySessionIdAndPartNumber(102L, 2)).thenReturn(Optional.empty());
        when(multipartUploadPartRepository.save(any(MultipartUploadPart.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = storageMultipartUploadService.uploadPart(userId, "token-2", 2, request);

        assertThat(response.partNumber()).isEqualTo(2);
        assertThat(response.eTag()).isEqualTo("etag-2");
        assertThat(response.size()).isEqualTo(3L);
    }

    @Test
    void completeMultipartUploadMergesPartsAndCreatesStorageNode() {
        Long userId = 23L;
        MultipartUploadSession session = uploadSession(103L, userId, null, "token-3", "design.pdf", 8L, 5L, 2);
        MultipartUploadPart firstPart = uploadPart(103L, 1, 5L, "etag-1");
        MultipartUploadPart secondPart = uploadPart(103L, 2, 3L, "etag-2");

        when(multipartUploadSessionRepository.findByUploadTokenAndOwnerId("token-3", userId)).thenReturn(Optional.of(session));
        when(storageNodeRepository.existsActiveSiblingName(userId, null, "design.pdf")).thenReturn(false);
        when(multipartUploadPartRepository.findBySessionIdOrderByPartNumberAsc(103L)).thenReturn(List.of(firstPart, secondPart));
        when(storageNodeRepository.save(any(StorageNode.class))).thenAnswer(invocation -> {
            StorageNode node = invocation.getArgument(0);
            ReflectionTestUtils.setField(node, "id", 301L);
            return node;
        });
        when(multipartUploadSessionRepository.save(session)).thenReturn(session);

        var summary = storageMultipartUploadService.completeMultipartUpload(userId, "token-3");

        assertThat(summary.id()).isEqualTo(301L);
        assertThat(summary.name()).isEqualTo("design.pdf");
        assertThat(summary.extension()).isEqualTo("pdf");
        assertThat(session.getStatus()).isEqualTo(MultipartUploadStatus.COMPLETED);

        ArgumentCaptor<List<CosFileStorageService.StoredCosPart>> partsCaptor = ArgumentCaptor.forClass(List.class);
        verify(cosFileStorageService).completeMultipartUpload(eq("cos/design.pdf"), eq("cos-upload-103"), partsCaptor.capture());
        assertThat(partsCaptor.getValue()).extracting(CosFileStorageService.StoredCosPart::partNumber).containsExactly(1, 2);
    }

    private MultipartUploadSession uploadSession(
            Long id,
            Long ownerId,
            Long parentId,
            String uploadToken,
            String fileName,
            long fileSize,
            long chunkSize,
            int totalChunks
    ) {
        MultipartUploadSession session = new MultipartUploadSession();
        ReflectionTestUtils.setField(session, "id", id);
        session.setOwnerId(ownerId);
        session.setParentId(parentId);
        session.setUploadToken(uploadToken);
        session.setCosUploadId("cos-upload-" + id);
        session.setObjectKey("cos/" + fileName);
        session.setFileName(fileName);
        session.setFileSize(fileSize);
        session.setContentType("application/octet-stream");
        session.setChunkSize(chunkSize);
        session.setTotalChunks(totalChunks);
        session.setFileFingerprint("fingerprint-1");
        session.setStatus(MultipartUploadStatus.IN_PROGRESS);
        return session;
    }

    private MultipartUploadPart uploadPart(Long sessionId, int partNumber, long partSize, String eTag) {
        MultipartUploadPart part = new MultipartUploadPart();
        part.setSessionId(sessionId);
        part.setPartNumber(partNumber);
        part.setPartSize(partSize);
        part.setETag(eTag);
        return part;
    }

}
