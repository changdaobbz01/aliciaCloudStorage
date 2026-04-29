package com.alicia.cloudstorage.api.repository;

import com.alicia.cloudstorage.api.entity.MultipartUploadPart;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface MultipartUploadPartRepository extends JpaRepository<MultipartUploadPart, Long> {

    List<MultipartUploadPart> findBySessionIdOrderByPartNumberAsc(Long sessionId);

    Optional<MultipartUploadPart> findBySessionIdAndPartNumber(Long sessionId, Integer partNumber);

    long countBySessionId(Long sessionId);

    void deleteBySessionId(Long sessionId);
}
