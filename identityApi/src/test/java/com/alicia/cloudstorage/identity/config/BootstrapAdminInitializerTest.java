package com.alicia.cloudstorage.identity.config;

import com.alicia.cloudstorage.identity.entity.IdentityUser;
import com.alicia.cloudstorage.identity.entity.IdentityUserRole;
import com.alicia.cloudstorage.identity.entity.IdentityUserStatus;
import com.alicia.cloudstorage.identity.repository.IdentityUserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BootstrapAdminInitializerTest {

    @Mock
    private IdentityUserRepository identityUserRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Test
    void skipsWhenIdentityUsersAlreadyExist() {
        BootstrapAdminInitializer initializer = initializer("13800000000", "Passw0rd", "Admin", null);

        when(identityUserRepository.count()).thenReturn(1L);

        initializer.run(null);

        verify(identityUserRepository, never()).save(any());
    }

    @Test
    void skipsEmptyDatabaseWhenNoBootstrapCredentialsProvided() {
        BootstrapAdminInitializer initializer = initializer(" ", "", null, null);

        when(identityUserRepository.count()).thenReturn(0L);

        initializer.run(null);

        verify(identityUserRepository, never()).save(any());
    }

    @Test
    void rejectsPartialBootstrapCredentials() {
        BootstrapAdminInitializer initializer = initializer("13800000000", "", null, null);

        when(identityUserRepository.count()).thenReturn(0L);

        assertThatThrownBy(() -> initializer.run(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Bootstrap admin requires both phone and password when one of them is configured.");

        verify(identityUserRepository, never()).save(any());
    }

    @Test
    void createsFirstAdminInIdentityServiceWhenDatabaseIsEmpty() {
        BootstrapAdminInitializer initializer =
                initializer(" 13800000000 ", " Passw0rd ", " Alicia Admin ", " cos:user-avatars/1/a.png ");

        when(identityUserRepository.count()).thenReturn(0L);
        when(passwordEncoder.encode("Passw0rd")).thenReturn("password-hash");
        when(identityUserRepository.save(any(IdentityUser.class))).thenAnswer(invocation -> invocation.getArgument(0));

        initializer.run(null);

        ArgumentCaptor<IdentityUser> userCaptor = ArgumentCaptor.forClass(IdentityUser.class);
        verify(identityUserRepository).save(userCaptor.capture());
        IdentityUser savedUser = userCaptor.getValue();
        assertThat(savedUser.getPhoneNumber()).isEqualTo("13800000000");
        assertThat(savedUser.getEmail()).isNull();
        assertThat(savedUser.getEmailVerifiedAt()).isNull();
        assertThat(savedUser.getNickname()).isEqualTo("Alicia Admin");
        assertThat(savedUser.getAvatarUrl()).isEqualTo("cos:user-avatars/1/a.png");
        assertThat(savedUser.getPasswordHash()).isEqualTo("password-hash");
        assertThat(savedUser.getTokenVersion()).isZero();
        assertThat(savedUser.getRole()).isEqualTo(IdentityUserRole.ADMIN);
        assertThat(savedUser.getStatus()).isEqualTo(IdentityUserStatus.ACTIVE);
    }

    @Test
    void usesDefaultNicknameWhenNicknameIsMissing() {
        BootstrapAdminInitializer initializer = initializer("13800000000", "Passw0rd", null, null);

        when(identityUserRepository.count()).thenReturn(0L);
        when(passwordEncoder.encode("Passw0rd")).thenReturn("password-hash");
        when(identityUserRepository.save(any(IdentityUser.class))).thenAnswer(invocation -> invocation.getArgument(0));

        initializer.run(null);

        ArgumentCaptor<IdentityUser> userCaptor = ArgumentCaptor.forClass(IdentityUser.class);
        verify(identityUserRepository).save(userCaptor.capture());
        assertThat(userCaptor.getValue().getNickname()).isEqualTo("\u7cfb\u7edf\u7ba1\u7406\u5458");
    }

    private BootstrapAdminInitializer initializer(
            String phone,
            String password,
            String nickname,
            String avatarUrl
    ) {
        return new BootstrapAdminInitializer(
                identityUserRepository,
                passwordEncoder,
                phone,
                password,
                nickname,
                avatarUrl
        );
    }
}
