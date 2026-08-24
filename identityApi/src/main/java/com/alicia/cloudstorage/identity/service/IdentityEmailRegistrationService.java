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
    private final IdentityAuditLogService identityAuditLogService;
    private final IdentityRefreshTokenService identityRefreshTokenService;
    private final IdentityUserResponseAssembler identityUserResponseAssembler;

    public IdentityEmailRegistrationService(
            IdentityUserRepository identityUserRepository,
            IdentityUserInputNormalizer identityUserInputNormalizer,
            EmailVerificationCodeService emailVerificationCodeService,
            IdentityUserCreationService identityUserCreationService,
            IdentityTokenService identityTokenService,
            IdentityAuditLogService identityAuditLogService,
            IdentityRefreshTokenService identityRefreshTokenService,
            IdentityUserResponseAssembler identityUserResponseAssembler
    ) {
        this.identityUserRepository = identityUserRepository;
        this.identityUserInputNormalizer = identityUserInputNormalizer;
        this.emailVerificationCodeService = emailVerificationCodeService;
        this.identityUserCreationService = identityUserCreationService;
        this.identityTokenService = identityTokenService;
        this.identityAuditLogService = identityAuditLogService;
        this.identityRefreshTokenService = identityRefreshTokenService;
        this.identityUserResponseAssembler = identityUserResponseAssembler;
    }

    public void requestRegistrationCode(String rawEmail, String requestIp, String userAgent) {
        String email = null;
        try {
            email = identityUserInputNormalizer.normalizeEmail(rawEmail);

            if (identityUserRepository.existsByEmail(email)) {
                identityAuditLogService.record(
                        IdentityAuditEventType.EMAIL_REGISTRATION_CODE_REQUEST,
                        IdentityAuditOutcome.SUCCESS,
                        null,
                        null,
                        email,
                        "already_registered"
                );
                return;
            }

            emailVerificationCodeService.requestRegistrationCode(email, requestIp, userAgent);
            identityAuditLogService.record(
                    IdentityAuditEventType.EMAIL_REGISTRATION_CODE_REQUEST,
                    IdentityAuditOutcome.SUCCESS,
                    null,
                    null,
                    email,
                    "sent"
            );
        } catch (RuntimeException ex) {
            identityAuditLogService.record(
                    IdentityAuditEventType.EMAIL_REGISTRATION_CODE_REQUEST,
                    IdentityAuditOutcome.FAILURE,
                    null,
                    null,
                    email == null ? rawEmail : email,
                    ex.getMessage()
            );
            throw ex;
        }
    }

    public IdentityLoginResponse verifyRegistration(
            VerifyEmailRegistrationRequest request,
            String clientAddress,
            String userAgent
    ) {
        String email = null;
        IdentityUser user = null;
        try {
            email = identityUserInputNormalizer.normalizeEmail(request.email());
            EmailVerificationCodeService.VerifiedEmailCode verifiedCode =
                    emailVerificationCodeService.verifyRegistrationCode(email, request.code());

            if (identityUserRepository.existsByEmail(email)) {
                throw new IllegalArgumentException("邮箱已注册，请直接登录。");
            }

            emailVerificationCodeService.consume(verifiedCode);

            user = identityUserCreationService.createVerifiedEmailUser(
                    email,
                    request.nickname(),
                    request.password(),
                    verifiedCode.verifiedAt()
            );
            IdentityRefreshTokenService.IssuedRefreshToken refreshToken =
                    identityRefreshTokenService.issue(user, clientAddress, userAgent);
            IdentityLoginResponse response = new IdentityLoginResponse(
                    identityTokenService.createToken(user, refreshToken.sessionId()),
                    refreshToken.token(),
                    identityUserResponseAssembler.toResponse(user)
            );
            identityAuditLogService.record(
                    IdentityAuditEventType.EMAIL_REGISTRATION_VERIFY,
                    IdentityAuditOutcome.SUCCESS,
                    user.getId(),
                    user.getId(),
                    email,
                    null
            );
            return response;
        } catch (RuntimeException ex) {
            identityAuditLogService.record(
                    IdentityAuditEventType.EMAIL_REGISTRATION_VERIFY,
                    IdentityAuditOutcome.FAILURE,
                    user == null ? null : user.getId(),
                    user == null ? null : user.getId(),
                    email == null && request != null ? request.email() : email,
                    ex.getMessage()
            );
            throw ex;
        }
    }
}
