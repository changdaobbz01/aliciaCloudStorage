package com.alicia.cloudstorage.api.repository;

import com.alicia.cloudstorage.api.entity.AppPackageRelease;
import com.alicia.cloudstorage.api.entity.AppPackageReleaseStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AppPackageReleaseRepository extends JpaRepository<AppPackageRelease, Long> {

    Optional<AppPackageRelease> findFirstByStatusOrderByUploadedAtDesc(AppPackageReleaseStatus status);

    List<AppPackageRelease> findByStatus(AppPackageReleaseStatus status);
}
