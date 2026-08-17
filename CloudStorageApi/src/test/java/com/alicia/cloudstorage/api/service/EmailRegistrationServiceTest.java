package com.alicia.cloudstorage.api.service;

import com.alicia.cloudstorage.api.auth.TokenService;
import com.alicia.cloudstorage.api.dto.LoginResponse;
import com.alicia.cloudstorage.api.dto.UserProfileResponse;
import com.alicia.cloudstorage.api.dto.VerifyEmailRegistrationRequest;
import com.alicia.cloudstorage.api.entity.EmailVerificationCode;
import com.alicia.cloudstorage.api.entity.EmailVerificationPurpose;
import com.alicia.cloudstorage.api.mail.EmailSender;
import com.alicia.cloudstorage.api.repository.EmailVerificationCodeRepository;
import com.alicia.cloudstorage.api.repository.SysUserRepository;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EmailRegistrationServiceTest {

    private static final Clock FIXED_CLOCK = Clock.fixed(
            Instant.parse("2026-08-17T02:30:00Z"),
            ZoneId.of("Asia/Shanghai")
    );
    private static final LocalDateTime NOW = LocalDateTime.now(FIXED_CLOCK);

    @Mock
    private EmailVerificationCodeRepository verificationCodeRepository;

    @Mock
    private SysUserRepository sysUserRepository;

    @Mock
    private UserAccountService userAccountService;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private EmailSender emailSender;

    private EmailRegistrationService service;

    @BeforeEach
    void setUp() {
        service = new EmailRegistrationService(
                verificationCodeRepository,
                sysUserRepository,
                userAccountService,
                passwordEncoder,
                emailSender,
                FIXED_CLOCK
        );
        when(userAccountService.normalizeEmail("NewUser@Example.COM")).thenReturn("newuser@example.com");
    }

    @Test
    void requestRegistrationCodeStoresHashedCodeAndSendsEmail() {
        when(sysUserRepository.existsByEmail("newuser@example.com")).thenReturn(false);
        when(verificationCodeRepository.findFirstByEmailAndPurposeAndConsumedAtIsNullOrderByCreatedAtDesc(
                "newuser@example.com",
                EmailVerificationPurpose.REGISTER
        )).thenReturn(Optional.empty());
        when(passwordEncoder.encode(anyString())).thenReturn("hashed-code");

        service.requestRegistrationCode("NewUser@Example.COM", "127.0.0.1", "JUnit");

        ArgumentCaptor<EmailVerificationCode> codeCaptor = ArgumentCaptor.forClass(EmailVerificationCode.class);
        verify(verificationCodeRepository).save(codeCaptor.capture());
        EmailVerificationCode savedCode = codeCaptor.getValue();
        assertThat(savedCode.getEmail()).isEqualTo("newuser@example.com");
        assertThat(savedCode.getPurpose()).isEqualTo(EmailVerificationPurpose.REGISTER);
        assertThat(savedCode.getCodeHash()).isEqualTo("hashed-code");
        assertThat(savedCode.getExpiresAt()).isEqualTo(NOW.plusMinutes(10));
        assertThat(savedCode.getResendAfter()).isEqualTo(NOW.plusSeconds(60));
        assertThat(savedCode.getRequestIpHash()).hasSize(64);
        assertThat(savedCode.getUserAgentHash()).hasSize(64);
        verify(emailSender).sendText(
                org.mockito.ArgumentMatchers.eq("newuser@example.com"),
                org.mockito.ArgumentMatchers.eq("Alicia 云盘注册验证码"),
                org.mockito.ArgumentMatchers.contains("10 分钟内有效")
        );
    }

    @Test
    void requestRegistrationCodeDoesNotSendForAlreadyRegisteredEmail() {
        when(sysUserRepository.existsByEmail("newuser@example.com")).thenReturn(true);

        service.requestRegistrationCode("NewUser@Example.COM", "127.0.0.1", "JUnit");

        verify(verificationCodeRepository, never()).save(org.mockito.ArgumentMatchers.any());
        verify(emailSender, never()).sendText(anyString(), anyString(), anyString());
    }

    @Test
    void requestRegistrationCodeRejectsBeforeResendCooldown() {
        EmailVerificationCode latestCode = new EmailVerificationCode();
        latestCode.setResendAfter(NOW.plusSeconds(30));

        when(sysUserRepository.existsByEmail("newuser@example.com")).thenReturn(false);
        when(verificationCodeRepository.findFirstByEmailAndPurposeAndConsumedAtIsNullOrderByCreatedAtDesc(
                "newuser@example.com",
                EmailVerificationPurpose.REGISTER
        )).thenReturn(Optional.of(latestCode));

        assertThatThrownBy(() -> service.requestRegistrationCode("NewUser@Example.COM", "127.0.0.1", "JUnit"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("验证码已发送");
        verify(emailSender, never()).sendText(anyString(), anyString(), anyString());
    }

    @Test
    void verifyRegistrationConsumesCodeAndCreatesUser() {
        EmailVerificationCode latestCode = activeCode();
        UserProfileResponse userProfile = new UserProfileResponse(
                88L,
                "",
                "newuser@example.com",
                "New User",
                null,
                null,
                "USER",
                "ACTIVE",
                NOW,
                1024L,
                0L,
                1024L
        );
        LoginResponse loginResponse = new LoginResponse("token", userProfile);

        when(verificationCodeRepository.findFirstByEmailAndPurposeAndConsumedAtIsNullOrderByCreatedAtDesc(
                "newuser@example.com",
                EmailVerificationPurpose.REGISTER
        )).thenReturn(Optional.of(latestCode));
        when(passwordEncoder.matches("123456", "hashed-code")).thenReturn(true);
        when(sysUserRepository.existsByEmail("newuser@example.com")).thenReturn(false);
        when(userAccountService.createVerifiedEmailUser("newuser@example.com", "New User", "Passw0rd"))
                .thenReturn(loginResponse);

        LoginResponse response = service.verifyRegistration(
                new VerifyEmailRegistrationRequest("NewUser@Example.COM", "123456", "New User", "Passw0rd")
        );

        assertThat(response).isSameAs(loginResponse);
        assertThat(latestCode.getConsumedAt()).isEqualTo(NOW);
        verify(verificationCodeRepository).save(latestCode);
    }

    @Test
    void verifyRegistrationIncrementsAttemptsWhenCodeDoesNotMatch() {
        EmailVerificationCode latestCode = activeCode();

        when(verificationCodeRepository.findFirstByEmailAndPurposeAndConsumedAtIsNullOrderByCreatedAtDesc(
                "newuser@example.com",
                EmailVerificationPurpose.REGISTER
        )).thenReturn(Optional.of(latestCode));
        when(passwordEncoder.matches("000000", "hashed-code")).thenReturn(false);

        assertThatThrownBy(() -> service.verifyRegistration(
                new VerifyEmailRegistrationRequest("NewUser@Example.COM", "000000", "New User", "Passw0rd")
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("验证码不正确");

        assertThat(latestCode.getAttempts()).isEqualTo(1);
        verify(verificationCodeRepository).save(latestCode);
        verify(userAccountService, never()).createVerifiedEmailUser(anyString(), anyString(), anyString());
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
