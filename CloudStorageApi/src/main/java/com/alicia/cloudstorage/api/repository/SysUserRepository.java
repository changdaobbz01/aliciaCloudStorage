package com.alicia.cloudstorage.api.repository;

import com.alicia.cloudstorage.api.entity.SysUser;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface SysUserRepository extends JpaRepository<SysUser, Long> {

    Optional<SysUser> findByPhoneNumber(String phoneNumber);

    boolean existsByPhoneNumber(String phoneNumber);

    boolean existsByPhoneNumberAndIdNot(String phoneNumber, Long id);

    List<SysUser> findAllByOrderByIdAsc();

    @Query("""
            select coalesce(sum(user.storageQuotaBytes), 0)
            from SysUser user
            """)
    Long sumStorageQuotaBytes();
}
