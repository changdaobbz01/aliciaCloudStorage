package com.alicia.cloudstorage.identity.service;

import com.alicia.cloudstorage.identity.dto.IdentityLoginResponse;
import com.alicia.cloudstorage.identity.dto.VerifyEmailRegistrationRequest;
import com.alicia.cloudstorage.identity.entity.EmailVerificationCode;
import com.alicia.cloudstorage.identity.entity.EmailVerificationPurpose;
import com.alicia.cloudstorage.identity.entity.IdentityUser;
import com.alicia.cloudstorage.identity.entity.IdentityUserRole;
import com.alicia.cloudstorage.identity.entity.IdentityUserStatus;
import com.alicia.cloudstorage.identity.mail.EmailSender;
import com.alicia.cloudstorage.identity.repository.EmailVerificationCodeRepository;
import com.alicia.cloudstorage.identity.repository.IdentityUserRepository;
import com.alicia.cloudstorage.identity.dto.IdentityUserResponse;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.Locale;

@Service
@Transactional
public class IdentityEmailRegistrationService {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final int CODE_BOUND = 1_000_000;
    private static final int MAX_ATTEMPTS = 5;
    private static final long CODE_TTL_MINUTES = 10L;
    private static final long RESEND_COOLDOWN_SECONDS = 60L;

    private final EmailVerificationCodeRepository verificationCodeRepository;
    private final IdentityUserRepository identityUserRepository;
    private final PasswordEncoder passwordEncoder;
    private final IdentityCredentialService identityCredentialService;
    private final EmailSender emailSender;
    private final IdentityTokenService identityTokenService;
    private final Clock clock;

    public IdentityEmailRegistrationService(
            EmailVerificationCodeRepository verificationCodeRepository,
            IdentityUserRepository identityUserRepository,
            PasswordEncoder passwordEncoder,
            IdentityCredentialService identityCredentialService,
            EmailSender emailSender,
            IdentityTokenService identityTokenService,
            Clock clock
    ) {
        this.verificationCodeRepository = verificationCodeRepository;
        this.identityUserRepository = identityUserRepository;
        this.passwordEncoder = passwordEncoder;
        this.identityCredentialService = identityCredentialService;
        this.emailSender = emailSender;
        this.identityTokenService = identityTokenService;
        this.clock = clock;
    }

    public void requestRegistrationCode(String rawEmail, String requestIp, String userAgent) {
        String email = normalizeEmail(rawEmail);

        if (identityUserRepository.existsByEmail(email)) {
            return;
        }

        LocalDateTime now = now();
        verificationCodeRepository.findFirstByEmailAndPurposeAndConsumedAtIsNullOrderByCreatedAtDesc(
                email,
                EmailVerificationPurpose.REGISTER
        ).ifPresent(latest -> {
            if (latest.getResendAfter().isAfter(now)) {
                throw new IllegalArgumentException("验证码已发送，请稍后再试。");
            }
        });

        String code = generateCode();
        EmailVerificationCode verificationCode = new EmailVerificationCode();
        verificationCode.setEmail(email);
        verificationCode.setPurpose(EmailVerificationPurpose.REGISTER);
        verificationCode.setCodeHash(passwordEncoder.encode(code));
        verificationCode.setAttempts(0);
        verificationCode.setMaxAttempts(MAX_ATTEMPTS);
        verificationCode.setExpiresAt(now.plusMinutes(CODE_TTL_MINUTES));
        verificationCode.setResendAfter(now.plusSeconds(RESEND_COOLDOWN_SECONDS));
        verificationCode.setRequestIpHash(sha256Hex(normalizeMetadata(requestIp)));
        verificationCode.setUserAgentHash(sha256Hex(normalizeMetadata(userAgent)));
        verificationCodeRepository.save(verificationCode);

        emailSender.sendText(email, "Alicia 云盘注册验证码", registrationEmailBody(code));
    }

    public IdentityLoginResponse verifyRegistration(VerifyEmailRegistrationRequest request) {
        String email = normalizeEmail(request.email());
        String code = request.code().trim();
        LocalDateTime now = now();

        EmailVerificationCode verificationCode = verificationCodeRepository
                .findFirstByEmailAndPurposeAndConsumedAtIsNullOrderByCreatedAtDesc(
                        email,
                        EmailVerificationPurpose.REGISTER
                )
                .orElseThrow(this::invalidCode);

        if (!isUsable(verificationCode, now)) {
            throw invalidCode();
        }

        if (!passwordEncoder.matches(code, verificationCode.getCodeHash())) {
            verificationCode.setAttempts(verificationCode.getAttempts() + 1);
            verificationCodeRepository.save(verificationCode);
            throw invalidCode();
        }

        if (identityUserRepository.existsByEmail(email)) {
            throw new IllegalArgumentException("邮箱已注册，请直接登录。");
        }

        verificationCode.setConsumedAt(now);
        verificationCodeRepository.save(verificationCode);

        IdentityUser user = createVerifiedEmailUser(email, request.nickname(), request.password(), now);
        return new IdentityLoginResponse(
                identityTokenService.createToken(user),
                IdentityUserResponse.from(user)
        );
    }

    private IdentityUser createVerifiedEmailUser(String email, String nickname, String password, LocalDateTime verifiedAt) {
        String normalizedNickname = normalizeNickname(nickname);
        String passwordHash = identityCredentialService.encodeInitialPassword(password);

        IdentityUser user = new IdentityUser();
        user.setPhoneNumber(null);
        user.setEmail(email);
        user.setEmailVerifiedAt(verifiedAt);
        user.setNickname(normalizedNickname);
        user.setAvatarUrl(null);
        user.setPasswordHash(passwordHash);
        user.setTokenVersion(0L);
        user.setRole(IdentityUserRole.USER);
        user.setStatus(IdentityUserStatus.ACTIVE);

        return identityUserRepository.save(user);
    }

    private boolean isUsable(EmailVerificationCode verificationCode, LocalDateTime now) {
        return verificationCode.getConsumedAt() == null
                && !verificationCode.getExpiresAt().isBefore(now)
                && verificationCode.getAttempts() < verificationCode.getMaxAttempts();
    }

    private IllegalArgumentException invalidCode() {
        return new IllegalArgumentException("验证码不正确或已过期。");
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock);
    }

    private String generateCode() {
        return "%06d".formatted(SECURE_RANDOM.nextInt(CODE_BOUND));
    }

    private String registrationEmailBody(String code) {
        return """
                你的 Alicia 云盘注册验证码是：%s

                验证码 10 分钟内有效，请勿转发给他人。
                如果这不是你本人操作，可以忽略这封邮件。
                """.formatted(code);
    }

    private String normalizeEmail(String value) {
        if (value == null) {
            throw new IllegalArgumentException("邮箱不能为空。");
        }

        String email = value.trim().toLowerCase(Locale.ROOT);
        if (email.isEmpty() || email.length() > 320 || !email.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) {
            throw new IllegalArgumentException("请输入有效邮箱地址。");
        }

        return email;
    }

    private String normalizeNickname(String value) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException("昵称不能为空。");
        }

        return value.trim();
    }

    private String normalizeMetadata(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }

        return value.trim();
    }

    private String sha256Hex(String value) {
        if (value.isEmpty()) {
            return null;
        }

        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 digest is unavailable.", ex);
        }
    }
}
