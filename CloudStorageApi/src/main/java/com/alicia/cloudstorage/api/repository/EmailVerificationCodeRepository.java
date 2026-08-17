package com.alicia.cloudstorage.api.repository;

import com.alicia.cloudstorage.api.entity.EmailVerificationCode;
import com.alicia.cloudstorage.api.entity.EmailVerificationPurpose;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface EmailVerificationCodeRepository extends JpaRepository<EmailVerificationCode, Long> {

    Optional<EmailVerificationCode> findFirstByEmailAndPurposeAndConsumedAtIsNullOrderByCreatedAtDesc(
            String email,
            EmailVerificationPurpose purpose
    );
}
