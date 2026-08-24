package com.alicia.cloudstorage.identity.repository;

import com.alicia.cloudstorage.identity.entity.IdentityUserAppRole;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface IdentityUserAppRoleRepository extends JpaRepository<IdentityUserAppRole, Long> {

    List<IdentityUserAppRole> findByUser_IdOrderByAppCodeAsc(Long userId);

    Optional<IdentityUserAppRole> findByUser_IdAndAppCode(Long userId, String appCode);
}
