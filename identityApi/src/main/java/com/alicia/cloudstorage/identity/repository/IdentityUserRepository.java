package com.alicia.cloudstorage.identity.repository;

import com.alicia.cloudstorage.identity.entity.IdentityUser;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface IdentityUserRepository extends JpaRepository<IdentityUser, Long> {

    Optional<IdentityUser> findByEmail(String email);

    Optional<IdentityUser> findByPhoneNumber(String phoneNumber);

    boolean existsByEmail(String email);

    boolean existsByPhoneNumber(String phoneNumber);

    List<IdentityUser> findAllByOrderByIdAsc();
}
