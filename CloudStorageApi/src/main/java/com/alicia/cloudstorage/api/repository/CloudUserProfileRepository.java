package com.alicia.cloudstorage.api.repository;

import com.alicia.cloudstorage.api.entity.CloudUserProfileEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface CloudUserProfileRepository extends JpaRepository<CloudUserProfileEntity, Long> {

    @Query("""
            select coalesce(sum(profile.storageQuotaBytes), 0)
            from CloudUserProfileEntity profile
            """)
    Long sumStorageQuotaBytes();
}
