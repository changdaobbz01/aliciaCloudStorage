package com.alicia.cloudstorage.identity.service;

import com.alicia.cloudstorage.identity.dto.VerifyEmailRegistrationRequest;
import com.alicia.cloudstorage.identity.entity.EmailVerificationCode;
import com.alicia.cloudstorage.identity.entity.EmailVerificationPurpose;
import com.alicia.cloudstorage.identity.entity.IdentityUser;
import com.alicia.cloudstorage.identity.entity.IdentityUserRole;
import com.alicia.cloudstorage.identity.entity.IdentityUserStatus;
import com.alicia.cloudstorage.identity.mail.EmailSender;
import com.alicia.cloudstorage.identity.repository.EmailVerificationCodeRepository;
import com.alicia.cloudstorage.identity.repository.IdentityUserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IdentityEmailRegistrationServiceTest {

    private static final Clock FIXED_CLOCK = Clock.fixed(
            Instant.parse("2026-08-17T02:30:00Z"),
            ZoneId.of("Asia/Shanghai")
    );
    private static final LocalDateTime NOW = LocalDateTime.now(FIXED_CLOCK);

    @Mock
    private EmailVerificationCodeRepository verificationCodeRepository;

    @Mock
    private IdentityUserRepository identityUserRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private EmailSender emailSender;

    @Mock
    private IdentityTokenService identityTokenService;

    private IdentityEmailRegistrationService service;

    @BeforeEach
    void setUp() {
        service = new IdentityEmailRegistrationService(
                verificationCodeRepository,
                identityUserRepository,
                passwordEncoder,
                emailSender,
                identityTokenService,
                FIXED_CLOCK
        );
    }

    @Test
    void requestRegistrationCodeStoresHashedCodeAndSendsEmail() {
        when(identityUserRepository.existsByEmail("newuser@example.com")).thenReturn(false);
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
        when(identityUserRepository.existsByEmail("newuser@example.com")).thenReturn(true);

        service.requestRegistrationCode("NewUser@Example.COM", "127.0.0.1", "JUnit");

        verify(verificationCodeRepository, never()).save(any());
        verify(emailSender, never()).sendText(anyString(), anyString(), anyString());
    }

    @Test
    void requestRegistrationCodeRejectsBeforeResendCooldown() {
        EmailVerificationCode latestCode = new EmailVerificationCode();
        latestCode.setResendAfter(NOW.plusSeconds(30));

        when(identityUserRepository.existsByEmail("newuser@example.com")).thenReturn(false);
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
    void verifyRegistrationConsumesCodeCreatesIdentityUserAndReturnsToken() {
        EmailVerificationCode latestCode = activeCode();

        when(verificationCodeRepository.findFirstByEmailAndPurposeAndConsumedAtIsNullOrderByCreatedAtDesc(
                "newuser@example.com",
                EmailVerificationPurpose.REGISTER
        )).thenReturn(Optional.of(latestCode));
        when(passwordEncoder.matches("123456", "hashed-code")).thenReturn(true);
        when(identityUserRepository.existsByEmail("newuser@example.com")).thenReturn(false);
        when(passwordEncoder.encode("Passw0rd")).thenReturn("password-hash");
        when(identityUserRepository.save(any(IdentityUser.class))).thenAnswer(invocation -> {
            IdentityUser user = invocation.getArgument(0);
            ReflectionTestUtils.setField(user, "id", 88L);
            ReflectionTestUtils.setField(user, "createdAt", NOW);
            return user;
        });
        when(identityTokenService.createToken(any(IdentityUser.class))).thenReturn("token");

        var response = service.verifyRegistration(
                new VerifyEmailRegistrationRequest("NewUser@Example.COM", "123456", "New User", "Passw0rd")
        );

        assertThat(response.token()).isEqualTo("token");
        assertThat(response.user().id()).isEqualTo(88L);
        assertThat(response.user().email()).isEqualTo("newuser@example.com");
        assertThat(response.user().role()).isEqualTo("USER");
        assertThat(latestCode.getConsumedAt()).isEqualTo(NOW);
        verify(verificationCodeRepository).save(latestCode);

        ArgumentCaptor<IdentityUser> userCaptor = ArgumentCaptor.forClass(IdentityUser.class);
        verify(identityUserRepository).save(userCaptor.capture());
        IdentityUser savedUser = userCaptor.getValue();
        assertThat(savedUser.getPhoneNumber()).isNull();
        assertThat(savedUser.getEmail()).isEqualTo("newuser@example.com");
        assertThat(savedUser.getEmailVerifiedAt()).isEqualTo(NOW);
        assertThat(savedUser.getNickname()).isEqualTo("New User");
        assertThat(savedUser.getPasswordHash()).isEqualTo("password-hash");
        assertThat(savedUser.getTokenVersion()).isEqualTo(0L);
        assertThat(savedUser.getRole()).isEqualTo(IdentityUserRole.USER);
        assertThat(savedUser.getStatus()).isEqualTo(IdentityUserStatus.ACTIVE);
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
        verify(identityUserRepository, never()).save(any(IdentityUser.class));
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
