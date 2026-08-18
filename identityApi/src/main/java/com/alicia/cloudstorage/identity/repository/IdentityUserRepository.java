package com.alicia.cloudstorage.identity.repository;

import com.alicia.cloudstorage.identity.entity.IdentityUser;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IdentityUserRepository extends JpaRepository<IdentityUser, Long> {
}
