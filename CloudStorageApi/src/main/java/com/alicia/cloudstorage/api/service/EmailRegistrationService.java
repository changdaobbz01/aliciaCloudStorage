package com.alicia.cloudstorage.api.service;

import com.alicia.cloudstorage.api.dto.LoginResponse;
import com.alicia.cloudstorage.api.dto.VerifyEmailRegistrationRequest;
import com.alicia.cloudstorage.api.entity.EmailVerificationCode;
import com.alicia.cloudstorage.api.entity.EmailVerificationPurpose;
import com.alicia.cloudstorage.api.mail.EmailSender;
import com.alicia.cloudstorage.api.repository.EmailVerificationCodeRepository;
import com.alicia.cloudstorage.api.repository.SysUserRepository;
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

@Service
@Transactional
public class EmailRegistrationService {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final int CODE_BOUND = 1_000_000;
    private static final int MAX_ATTEMPTS = 5;
    private static final long CODE_TTL_MINUTES = 10L;
    private static final long RESEND_COOLDOWN_SECONDS = 60L;

    private final EmailVerificationCodeRepository verificationCodeRepository;
    private final SysUserRepository sysUserRepository;
    private final UserAccountService userAccountService;
    private final PasswordEncoder passwordEncoder;
    private final EmailSender emailSender;
    private final Clock clock;

    public EmailRegistrationService(
            EmailVerificationCodeRepository verificationCodeRepository,
            SysUserRepository sysUserRepository,
            UserAccountService userAccountService,
            PasswordEncoder passwordEncoder,
            EmailSender emailSender,
            Clock clock
    ) {
        this.verificationCodeRepository = verificationCodeRepository;
        this.sysUserRepository = sysUserRepository;
        this.userAccountService = userAccountService;
        this.passwordEncoder = passwordEncoder;
        this.emailSender = emailSender;
        this.clock = clock;
    }

    public void requestRegistrationCode(String rawEmail, String requestIp, String userAgent) {
        String email = userAccountService.normalizeEmail(rawEmail);

        if (sysUserRepository.existsByEmail(email)) {
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

    public LoginResponse verifyRegistration(VerifyEmailRegistrationRequest request) {
        String email = userAccountService.normalizeEmail(request.email());
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

        if (sysUserRepository.existsByEmail(email)) {
            throw new IllegalArgumentException("邮箱已注册，请直接登录。");
        }

        verificationCode.setConsumedAt(now);
        verificationCodeRepository.save(verificationCode);

        return userAccountService.createVerifiedEmailUser(email, request.nickname(), request.password());
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
