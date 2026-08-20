package com.alicia.cloudstorage.identity.service;

import com.alicia.cloudstorage.identity.dto.AdminCreateIdentityUserRequest;
import com.alicia.cloudstorage.identity.entity.IdentityUser;
import com.alicia.cloudstorage.identity.entity.IdentityUserRole;
import com.alicia.cloudstorage.identity.entity.IdentityUserStatus;
import com.alicia.cloudstorage.identity.repository.IdentityUserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IdentityUserCreationServiceTest {

    private static final LocalDateTime VERIFIED_AT = LocalDateTime.of(2026, 8, 20, 10, 30);

    @Mock
    private IdentityUserRepository identityUserRepository;

    @Mock
    private IdentityCredentialService identityCredentialService;

    @Spy
    private IdentityUserInputNormalizer identityUserInputNormalizer = new IdentityUserInputNormalizer();

    private IdentityUserCreationService service;

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        service = new IdentityUserCreationService(
                identityUserRepository,
                identityCredentialService,
                identityUserInputNormalizer
        );
    }

    @Test
    void createAdminManagedUserPersistsOnlyIdentityFields() {
        AdminCreateIdentityUserRequest request = new AdminCreateIdentityUserRequest(
                "13800000001",
                null,
                "New User",
                "https://example.com/avatar.png",
                "Passw0rd",
                "USER"
        );

        when(identityCredentialService.encodeInitialPassword("Passw0rd")).thenReturn("password-hash");
        when(identityUserRepository.existsByPhoneNumber("13800000001")).thenReturn(false);
        when(identityUserRepository.save(any(IdentityUser.class))).thenAnswer(invocation -> {
            IdentityUser user = invocation.getArgument(0);
            ReflectionTestUtils.setField(user, "id", 72L);
            ReflectionTestUtils.setField(user, "createdAt", LocalDateTime.of(2026, 8, 19, 9, 30));
            return user;
        });

        IdentityUser createdUser = service.createAdminManagedUser(request);

        ArgumentCaptor<IdentityUser> userCaptor = ArgumentCaptor.forClass(IdentityUser.class);
        verify(identityUserRepository).save(userCaptor.capture());
        IdentityUser savedUser = userCaptor.getValue();
        assertThat(savedUser.getPhoneNumber()).isEqualTo("13800000001");
        assertThat(savedUser.getEmail()).isNull();
        assertThat(savedUser.getEmailVerifiedAt()).isNull();
        assertThat(savedUser.getNickname()).isEqualTo("New User");
        assertThat(savedUser.getAvatarUrl()).isEqualTo("https://example.com/avatar.png");
        assertThat(savedUser.getPasswordHash()).isEqualTo("password-hash");
        assertThat(savedUser.getTokenVersion()).isZero();
        assertThat(savedUser.getRole()).isEqualTo(IdentityUserRole.USER);
        assertThat(savedUser.getStatus()).isEqualTo(IdentityUserStatus.ACTIVE);
        assertThat(createdUser.getId()).isEqualTo(72L);
    }

    @Test
    void createAdminManagedUserAcceptsEmailIdentifierAndNormalizesIt() {
        AdminCreateIdentityUserRequest request = new AdminCreateIdentityUserRequest(
                null,
                "NewUser@Example.COM",
                "New User",
                null,
                "Passw0rd",
                "ADMIN"
        );

        when(identityCredentialService.encodeInitialPassword("Passw0rd")).thenReturn("password-hash");
        when(identityUserRepository.existsByEmail("newuser@example.com")).thenReturn(false);
        when(identityUserRepository.save(any(IdentityUser.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.createAdminManagedUser(request);

        ArgumentCaptor<IdentityUser> userCaptor = ArgumentCaptor.forClass(IdentityUser.class);
        verify(identityUserRepository).save(userCaptor.capture());
        assertThat(userCaptor.getValue().getEmail()).isEqualTo("newuser@example.com");
        assertThat(userCaptor.getValue().getRole()).isEqualTo(IdentityUserRole.ADMIN);
    }

    @Test
    void createAdminManagedUserRejectsMissingLoginIdentifier() {
        assertThatThrownBy(() -> service.createAdminManagedUser(
                new AdminCreateIdentityUserRequest(null, null, "New User", null, "Passw0rd", "USER")
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("手机号或邮箱不能为空。");

        verify(identityUserRepository, never()).save(any());
    }

    @Test
    void createAdminManagedUserRejectsDuplicatePhoneNumber() {
        when(identityCredentialService.encodeInitialPassword("Passw0rd")).thenReturn("password-hash");
        when(identityUserRepository.existsByPhoneNumber("13800000001")).thenReturn(true);

        assertThatThrownBy(() -> service.createAdminManagedUser(
                new AdminCreateIdentityUserRequest("13800000001", null, "New User", null, "Passw0rd", "USER")
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("手机号已被其他账户使用。");

        verify(identityUserRepository, never()).save(any());
    }

    @Test
    void createAdminManagedUserRejectsInvalidRole() {
        assertThatThrownBy(() -> service.createAdminManagedUser(
                new AdminCreateIdentityUserRequest("13800000001", null, "New User", null, "Passw0rd", "OWNER")
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("角色只能是 ADMIN 或 USER。");
    }

    @Test
    void createVerifiedEmailUserPersistsVerifiedEmailIdentity() {
        when(identityUserRepository.existsByEmail("newuser@example.com")).thenReturn(false);
        when(identityCredentialService.encodeInitialPassword("Passw0rd")).thenReturn("password-hash");
        when(identityUserRepository.save(any(IdentityUser.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.createVerifiedEmailUser("NewUser@Example.COM", " New User ", "Passw0rd", VERIFIED_AT);

        ArgumentCaptor<IdentityUser> userCaptor = ArgumentCaptor.forClass(IdentityUser.class);
        verify(identityUserRepository).save(userCaptor.capture());
        IdentityUser savedUser = userCaptor.getValue();
        assertThat(savedUser.getPhoneNumber()).isNull();
        assertThat(savedUser.getEmail()).isEqualTo("newuser@example.com");
        assertThat(savedUser.getEmailVerifiedAt()).isEqualTo(VERIFIED_AT);
        assertThat(savedUser.getNickname()).isEqualTo("New User");
        assertThat(savedUser.getAvatarUrl()).isNull();
        assertThat(savedUser.getPasswordHash()).isEqualTo("password-hash");
        assertThat(savedUser.getTokenVersion()).isZero();
        assertThat(savedUser.getRole()).isEqualTo(IdentityUserRole.USER);
        assertThat(savedUser.getStatus()).isEqualTo(IdentityUserStatus.ACTIVE);
    }

    @Test
    void createVerifiedEmailUserRejectsDuplicateEmail() {
        when(identityUserRepository.existsByEmail("newuser@example.com")).thenReturn(true);

        assertThatThrownBy(() -> service.createVerifiedEmailUser(
                "NewUser@Example.COM",
                "New User",
                "Passw0rd",
                VERIFIED_AT
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("邮箱已注册，请直接登录。");

        verify(identityUserRepository, never()).save(any());
    }

    @Test
    void createBootstrapAdminPersistsActiveAdminWithDefaultNickname() {
        when(identityCredentialService.encodeInitialPassword("Passw0rd")).thenReturn("password-hash");
        when(identityUserRepository.save(any(IdentityUser.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.createBootstrapAdmin(
                " 13800000000 ",
                "Passw0rd",
                " ",
                " cos:user-avatars/1/a.png "
        );

        ArgumentCaptor<IdentityUser> userCaptor = ArgumentCaptor.forClass(IdentityUser.class);
        verify(identityUserRepository).save(userCaptor.capture());
        IdentityUser savedUser = userCaptor.getValue();
        assertThat(savedUser.getPhoneNumber()).isEqualTo("13800000000");
        assertThat(savedUser.getEmail()).isNull();
        assertThat(savedUser.getEmailVerifiedAt()).isNull();
        assertThat(savedUser.getNickname()).isEqualTo("\u7cfb\u7edf\u7ba1\u7406\u5458");
        assertThat(savedUser.getAvatarUrl()).isEqualTo("cos:user-avatars/1/a.png");
        assertThat(savedUser.getPasswordHash()).isEqualTo("password-hash");
        assertThat(savedUser.getTokenVersion()).isZero();
        assertThat(savedUser.getRole()).isEqualTo(IdentityUserRole.ADMIN);
        assertThat(savedUser.getStatus()).isEqualTo(IdentityUserStatus.ACTIVE);
    }
}
