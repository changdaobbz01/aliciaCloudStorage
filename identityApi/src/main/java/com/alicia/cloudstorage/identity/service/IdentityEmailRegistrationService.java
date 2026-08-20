package com.alicia.cloudstorage.identity.service;

import com.alicia.cloudstorage.identity.dto.IdentityLoginResponse;
import com.alicia.cloudstorage.identity.dto.IdentityUserResponse;
import com.alicia.cloudstorage.identity.dto.VerifyEmailRegistrationRequest;
import com.alicia.cloudstorage.identity.entity.IdentityUser;
import com.alicia.cloudstorage.identity.repository.IdentityUserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class IdentityEmailRegistrationService {

    private final IdentityUserRepository identityUserRepository;
    private final IdentityUserInputNormalizer identityUserInputNormalizer;
    private final EmailVerificationCodeService emailVerificationCodeService;
    private final IdentityUserCreationService identityUserCreationService;
    private final IdentityTokenService identityTokenService;

    public IdentityEmailRegistrationService(
            IdentityUserRepository identityUserRepository,
            IdentityUserInputNormalizer identityUserInputNormalizer,
            EmailVerificationCodeService emailVerificationCodeService,
            IdentityUserCreationService identityUserCreationService,
            IdentityTokenService identityTokenService
    ) {
        this.identityUserRepository = identityUserRepository;
        this.identityUserInputNormalizer = identityUserInputNormalizer;
        this.emailVerificationCodeService = emailVerificationCodeService;
        this.identityUserCreationService = identityUserCreationService;
        this.identityTokenService = identityTokenService;
    }

    public void requestRegistrationCode(String rawEmail, String requestIp, String userAgent) {
        String email = identityUserInputNormalizer.normalizeEmail(rawEmail);

        if (identityUserRepository.existsByEmail(email)) {
            return;
        }

        emailVerificationCodeService.requestRegistrationCode(email, requestIp, userAgent);
    }

    public IdentityLoginResponse verifyRegistration(VerifyEmailRegistrationRequest request) {
        String email = identityUserInputNormalizer.normalizeEmail(request.email());
        EmailVerificationCodeService.VerifiedEmailCode verifiedCode =
                emailVerificationCodeService.verifyRegistrationCode(email, request.code());

        if (identityUserRepository.existsByEmail(email)) {
            throw new IllegalArgumentException("邮箱已注册，请直接登录。");
        }

        emailVerificationCodeService.consume(verifiedCode);

        IdentityUser user = identityUserCreationService.createVerifiedEmailUser(
                email,
                request.nickname(),
                request.password(),
                verifiedCode.verifiedAt()
        );
        return new IdentityLoginResponse(
                identityTokenService.createToken(user),
                IdentityUserResponse.from(user)
        );
    }
}
