package com.alicia.cloudstorage.identity.repository;

import com.alicia.cloudstorage.identity.entity.EmailVerificationCode;
import com.alicia.cloudstorage.identity.entity.EmailVerificationPurpose;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface EmailVerificationCodeRepository extends JpaRepository<EmailVerificationCode, Long> {

    Optional<EmailVerificationCode> findFirstByEmailAndPurposeAndConsumedAtIsNullOrderByCreatedAtDesc(
            String email,
            EmailVerificationPurpose purpose
    );
}
