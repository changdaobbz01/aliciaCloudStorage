package com.alicia.cloudstorage.api.repository;

import com.alicia.cloudstorage.api.entity.ShareLink;
import com.alicia.cloudstorage.api.entity.ShareLinkStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface ShareLinkRepository extends JpaRepository<ShareLink, Long> {

    Optional<ShareLink> findByShareCode(String shareCode);

    Optional<ShareLink> findByIdAndOwnerId(Long id, Long ownerId);

    boolean existsByShareCode(String shareCode);

    List<ShareLink> findByOwnerIdOrderByCreatedAtDesc(Long ownerId);

    long countByStatus(ShareLinkStatus status);

    long countByPasswordHashIsNotNull();

    long countByAllowDownloadTrue();

    long countByAllowSaveTrue();

    @Query("""
            select count(link)
            from ShareLink link
            where link.status = com.alicia.cloudstorage.api.entity.ShareLinkStatus.ACTIVE
              and (link.expiresAt is null or link.expiresAt > :now)
            """)
    long countAvailableActiveLinks(@Param("now") LocalDateTime now);

    @Query("""
            select count(link)
            from ShareLink link
            where link.status = com.alicia.cloudstorage.api.entity.ShareLinkStatus.ACTIVE
              and link.expiresAt is not null
              and link.expiresAt <= :now
            """)
    long countExpiredActiveLinks(@Param("now") LocalDateTime now);

    @Query("""
            select coalesce(sum(link.viewCount), 0)
            from ShareLink link
            """)
    Long sumViewCount();

    @Query("""
            select max(link.createdAt)
            from ShareLink link
            """)
    LocalDateTime findLatestCreatedAt();

    @Query("""
            select max(link.lastAccessedAt)
            from ShareLink link
            """)
    LocalDateTime findLatestAccessedAt();
}
