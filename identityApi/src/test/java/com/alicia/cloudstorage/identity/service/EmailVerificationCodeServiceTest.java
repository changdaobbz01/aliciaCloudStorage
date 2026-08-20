package com.alicia.cloudstorage.identity.service;

import com.alicia.cloudstorage.identity.entity.EmailVerificationCode;
import com.alicia.cloudstorage.identity.entity.EmailVerificationPurpose;
import com.alicia.cloudstorage.identity.mail.EmailSender;
import com.alicia.cloudstorage.identity.repository.EmailVerificationCodeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EmailVerificationCodeServiceTest {

    private static final Clock FIXED_CLOCK = Clock.fixed(
            Instant.parse("2026-08-17T02:30:00Z"),
            ZoneId.of("Asia/Shanghai")
    );
    private static final LocalDateTime NOW = LocalDateTime.now(FIXED_CLOCK);

    @Mock
    private EmailVerificationCodeRepository verificationCodeRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private EmailSender emailSender;

    private EmailVerificationCodeService service;

    @BeforeEach
    void setUp() {
        service = new EmailVerificationCodeService(
                verificationCodeRepository,
                passwordEncoder,
                emailSender,
                FIXED_CLOCK
        );
    }

    @Test
    void requestRegistrationCodeStoresHashedCodeAndSendsEmail() {
        when(verificationCodeRepository.findFirstByEmailAndPurposeAndConsumedAtIsNullOrderByCreatedAtDesc(
                "newuser@example.com",
                EmailVerificationPurpose.REGISTER
        )).thenReturn(Optional.empty());
        when(passwordEncoder.encode(anyString())).thenReturn("hashed-code");

        service.requestRegistrationCode("newuser@example.com", "127.0.0.1", "JUnit");

        ArgumentCaptor<EmailVerificationCode> codeCaptor = ArgumentCaptor.forClass(EmailVerificationCode.class);
        verify(verificationCodeRepository).save(codeCaptor.capture());
        EmailVerificationCode savedCode = codeCaptor.getValue();
        assertThat(savedCode.getEmail()).isEqualTo("newuser@example.com");
        assertThat(savedCode.getPurpose()).isEqualTo(EmailVerificationPurpose.REGISTER);
        assertThat(savedCode.getCodeHash()).isEqualTo("hashed-code");
        assertThat(savedCode.getAttempts()).isZero();
        assertThat(savedCode.getMaxAttempts()).isEqualTo(5);
        assertThat(savedCode.getExpiresAt()).isEqualTo(NOW.plusMinutes(10));
        assertThat(savedCode.getResendAfter()).isEqualTo(NOW.plusSeconds(60));
        assertThat(savedCode.getRequestIpHash()).hasSize(64);
        assertThat(savedCode.getUserAgentHash()).hasSize(64);
        verify(emailSender).sendText(
                eq("newuser@example.com"),
                eq("Alicia 云盘注册验证码"),
                contains("10 分钟内有效")
        );
    }

    @Test
    void requestRegistrationCodeRejectsBeforeResendCooldown() {
        EmailVerificationCode latestCode = new EmailVerificationCode();
        latestCode.setResendAfter(NOW.plusSeconds(30));

        when(verificationCodeRepository.findFirstByEmailAndPurposeAndConsumedAtIsNullOrderByCreatedAtDesc(
                "newuser@example.com",
                EmailVerificationPurpose.REGISTER
        )).thenReturn(Optional.of(latestCode));

        assertThatThrownBy(() -> service.requestRegistrationCode("newuser@example.com", "127.0.0.1", "JUnit"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("验证码已发送，请稍后再试。");

        verify(emailSender, never()).sendText(anyString(), anyString(), anyString());
    }

    @Test
    void verifyRegistrationCodeReturnsVerifiedCodeWhenCodeMatches() {
        EmailVerificationCode latestCode = activeCode();

        when(verificationCodeRepository.findFirstByEmailAndPurposeAndConsumedAtIsNullOrderByCreatedAtDesc(
                "newuser@example.com",
                EmailVerificationPurpose.REGISTER
        )).thenReturn(Optional.of(latestCode));
        when(passwordEncoder.matches("123456", "hashed-code")).thenReturn(true);

        var verifiedCode = service.verifyRegistrationCode("newuser@example.com", " 123456 ");

        assertThat(verifiedCode.verificationCode()).isSameAs(latestCode);
        assertThat(verifiedCode.verifiedAt()).isEqualTo(NOW);
    }

    @Test
    void verifyRegistrationCodeIncrementsAttemptsWhenCodeDoesNotMatch() {
        EmailVerificationCode latestCode = activeCode();

        when(verificationCodeRepository.findFirstByEmailAndPurposeAndConsumedAtIsNullOrderByCreatedAtDesc(
                "newuser@example.com",
                EmailVerificationPurpose.REGISTER
        )).thenReturn(Optional.of(latestCode));
        when(passwordEncoder.matches("000000", "hashed-code")).thenReturn(false);

        assertThatThrownBy(() -> service.verifyRegistrationCode("newuser@example.com", "000000"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("验证码不正确或已过期。");

        assertThat(latestCode.getAttempts()).isEqualTo(1);
        verify(verificationCodeRepository).save(latestCode);
    }

    @Test
    void verifyRegistrationCodeRejectsExpiredCode() {
        EmailVerificationCode latestCode = activeCode();
        latestCode.setExpiresAt(NOW.minusSeconds(1));

        when(verificationCodeRepository.findFirstByEmailAndPurposeAndConsumedAtIsNullOrderByCreatedAtDesc(
                "newuser@example.com",
                EmailVerificationPurpose.REGISTER
        )).thenReturn(Optional.of(latestCode));

        assertThatThrownBy(() -> service.verifyRegistrationCode("newuser@example.com", "123456"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("验证码不正确或已过期。");
    }

    @Test
    void consumeMarksCodeAsConsumedAtVerifiedTime() {
        EmailVerificationCode latestCode = activeCode();
        EmailVerificationCodeService.VerifiedEmailCode verifiedCode =
                new EmailVerificationCodeService.VerifiedEmailCode(latestCode, NOW);

        service.consume(verifiedCode);

        assertThat(latestCode.getConsumedAt()).isEqualTo(NOW);
        verify(verificationCodeRepository).save(latestCode);
    }

    private EmailVerificationCode activeCode() {
        EmailVerificationCode latestCode = new EmailVerificationCode();
        latestCode.setEmail("newuser@example.com");
        latestCode.setPurpose(EmailVerificationPurpose.REGISTER);
        latestCode.setCodeHash("hashed-code");
        latestCode.setAttempts(0);
        latestCode.setMaxAttempts(5);
        latestCode.setExpiresAt(NOW.plusMinutes(5));
        latestCode.setResendAfter(NOW.minusSeconds(1));
        return latestCode;
    }
}
