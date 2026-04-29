package com.alicia.cloudstorage.api.repository;

import com.alicia.cloudstorage.api.entity.MultipartUploadSession;
import com.alicia.cloudstorage.api.entity.MultipartUploadStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface MultipartUploadSessionRepository extends JpaRepository<MultipartUploadSession, Long> {

    Optional<MultipartUploadSession> findByUploadTokenAndOwnerId(String uploadToken, Long ownerId);

    List<MultipartUploadSession> findByStatusAndUpdatedAtBefore(MultipartUploadStatus status, LocalDateTime updatedAt);

    Optional<MultipartUploadSession> findFirstByOwnerIdAndParentScopeIdAndFileNameAndFileSizeAndFileFingerprintAndStatusOrderByUpdatedAtDesc(
            Long ownerId,
            Long parentScopeId,
            String fileName,
            Long fileSize,
            String fileFingerprint,
            MultipartUploadStatus status
    );
}
