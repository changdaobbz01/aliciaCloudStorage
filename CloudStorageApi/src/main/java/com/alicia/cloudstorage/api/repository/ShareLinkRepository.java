package com.alicia.cloudstorage.api.repository;

import com.alicia.cloudstorage.api.entity.ShareLink;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ShareLinkRepository extends JpaRepository<ShareLink, Long> {

    Optional<ShareLink> findByShareCode(String shareCode);

    Optional<ShareLink> findByIdAndOwnerId(Long id, Long ownerId);

    boolean existsByShareCode(String shareCode);

    List<ShareLink> findByOwnerIdOrderByCreatedAtDesc(Long ownerId);
}
