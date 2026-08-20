package com.alicia.cloudstorage.identity.service;

import com.alicia.cloudstorage.identity.dto.VerifyEmailRegistrationRequest;
import com.alicia.cloudstorage.identity.entity.EmailVerificationCode;
import com.alicia.cloudstorage.identity.entity.IdentityUser;
import com.alicia.cloudstorage.identity.repository.IdentityUserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IdentityEmailRegistrationServiceTest {

    private static final LocalDateTime VERIFIED_AT = LocalDateTime.of(2026, 8, 17, 10, 30);

    @Mock
    private IdentityUserRepository identityUserRepository;

    @Mock
    private EmailVerificationCodeService emailVerificationCodeService;

    @Mock
    private IdentityUserCreationService identityUserCreationService;

    @Mock
    private IdentityTokenService identityTokenService;

    private IdentityUserInputNormalizer identityUserInputNormalizer;

    private IdentityEmailRegistrationService service;

    @BeforeEach
    void setUp() {
        identityUserInputNormalizer = new IdentityUserInputNormalizer();
        service = new IdentityEmailRegistrationService(
                identityUserRepository,
                identityUserInputNormalizer,
                emailVerificationCodeService,
                identityUserCreationService,
                identityTokenService
        );
    }

    @Test
    void requestRegistrationCodeDelegatesForAvailableEmail() {
        when(identityUserRepository.existsByEmail("newuser@example.com")).thenReturn(false);

        service.requestRegistrationCode("NewUser@Example.COM", "127.0.0.1", "JUnit");

        verify(emailVerificationCodeService)
                .requestRegistrationCode("newuser@example.com", "127.0.0.1", "JUnit");
    }

    @Test
    void requestRegistrationCodeDoesNotSendForAlreadyRegisteredEmail() {
        when(identityUserRepository.existsByEmail("newuser@example.com")).thenReturn(true);

        service.requestRegistrationCode("NewUser@Example.COM", "127.0.0.1", "JUnit");

        verify(emailVerificationCodeService, never())
                .requestRegistrationCode("newuser@example.com", "127.0.0.1", "JUnit");
    }

    @Test
    void verifyRegistrationConsumesCodeCreatesIdentityUserAndReturnsToken() {
        EmailVerificationCodeService.VerifiedEmailCode verifiedCode = verifiedCode();

        when(emailVerificationCodeService.verifyRegistrationCode("newuser@example.com", "123456"))
                .thenReturn(verifiedCode);
        when(identityUserRepository.existsByEmail("newuser@example.com")).thenReturn(false);
        when(identityUserCreationService.createVerifiedEmailUser(
                "newuser@example.com",
                "New User",
                "Passw0rd",
                VERIFIED_AT
        )).thenReturn(identityUser(88L));
        when(identityTokenService.createToken(org.mockito.ArgumentMatchers.any(IdentityUser.class))).thenReturn("token");

        var response = service.verifyRegistration(
                new VerifyEmailRegistrationRequest("NewUser@Example.COM", "123456", "New User", "Passw0rd")
        );

        assertThat(response.token()).isEqualTo("token");
        assertThat(response.user().id()).isEqualTo(88L);
        assertThat(response.user().email()).isEqualTo("newuser@example.com");
        assertThat(response.user().role()).isEqualTo("USER");
        verify(emailVerificationCodeService).consume(verifiedCode);
        verify(identityUserCreationService).createVerifiedEmailUser(
                "newuser@example.com",
                "New User",
                "Passw0rd",
                VERIFIED_AT
        );
    }

    @Test
    void verifyRegistrationChecksDuplicateEmailBeforeConsumingCode() {
        EmailVerificationCodeService.VerifiedEmailCode verifiedCode = verifiedCode();

        when(emailVerificationCodeService.verifyRegistrationCode("newuser@example.com", "123456"))
                .thenReturn(verifiedCode);
        when(identityUserRepository.existsByEmail("newuser@example.com")).thenReturn(true);

        assertThatThrownBy(() -> service.verifyRegistration(
                new VerifyEmailRegistrationRequest("NewUser@Example.COM", "123456", "New User", "Passw0rd")
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("邮箱已注册，请直接登录。");

        verify(emailVerificationCodeService, never()).consume(verifiedCode);
        verify(identityUserCreationService, never()).createVerifiedEmailUser(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any()
        );
    }

    @Test
    void verifyRegistrationDoesNotCreateUserWhenCodeIsInvalid() {
        when(emailVerificationCodeService.verifyRegistrationCode("newuser@example.com", "000000"))
                .thenThrow(new IllegalArgumentException("验证码不正确或已过期。"));

        assertThatThrownBy(() -> service.verifyRegistration(
                new VerifyEmailRegistrationRequest("NewUser@Example.COM", "000000", "New User", "Passw0rd")
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("验证码不正确或已过期。");

        verify(identityUserRepository, never()).existsByEmail("newuser@example.com");
        verify(identityUserCreationService, never()).createVerifiedEmailUser(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any()
        );
    }

    private EmailVerificationCodeService.VerifiedEmailCode verifiedCode() {
        return new EmailVerificationCodeService.VerifiedEmailCode(new EmailVerificationCode(), VERIFIED_AT);
    }

    private IdentityUser identityUser(Long id) {
        IdentityUser user = new IdentityUser();
        ReflectionTestUtils.setField(user, "id", id);
        ReflectionTestUtils.setField(user, "email", "newuser@example.com");
        ReflectionTestUtils.setField(user, "nickname", "New User");
        ReflectionTestUtils.setField(user, "role", com.alicia.cloudstorage.identity.entity.IdentityUserRole.USER);
        ReflectionTestUtils.setField(user, "status", com.alicia.cloudstorage.identity.entity.IdentityUserStatus.ACTIVE);
        ReflectionTestUtils.setField(user, "createdAt", VERIFIED_AT);
        return user;
    }
}
