package com.alicia.cloudstorage.identity.service;

import com.alicia.cloudstorage.identity.entity.IdentityUser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IdentityCredentialServiceTest {

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private IdentityCredentialService identityCredentialService;

    @Test
    void encodeInitialPasswordValidatesAndHashesPassword() {
        when(passwordEncoder.encode("Passw0rd")).thenReturn("password-hash");

        String passwordHash = identityCredentialService.encodeInitialPassword("Passw0rd");

        assertThat(passwordHash).isEqualTo("password-hash");
    }

    @Test
    void encodeInitialPasswordRejectsBlankPassword() {
        assertThatThrownBy(() -> identityCredentialService.encodeInitialPassword(" "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("密码不能为空。");

        verify(passwordEncoder, never()).encode(" ");
    }

    @Test
    void encodeInitialPasswordRejectsShortPassword() {
        assertThatThrownBy(() -> identityCredentialService.encodeInitialPassword("short"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("密码长度至少为 6 位。");

        verify(passwordEncoder, never()).encode("short");
    }

    @Test
    void changePasswordUpdatesHashAndInvalidatesExistingTokens() {
        IdentityUser user = identityUser("current-hash", 2L);

        when(passwordEncoder.matches("OldPass1", "current-hash")).thenReturn(true);
        when(passwordEncoder.encode("NewPass1")).thenReturn("new-hash");

        identityCredentialService.changePassword(user, "OldPass1", "NewPass1");

        assertThat(user.getPasswordHash()).isEqualTo("new-hash");
        assertThat(user.getTokenVersion()).isEqualTo(3L);
    }

    @Test
    void changePasswordRejectsIncorrectOldPassword() {
        IdentityUser user = identityUser("current-hash", 2L);

        when(passwordEncoder.matches("wrong", "current-hash")).thenReturn(false);

        assertThatThrownBy(() -> identityCredentialService.changePassword(user, "wrong", "NewPass1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("旧密码不正确。");

        assertThat(user.getPasswordHash()).isEqualTo("current-hash");
        assertThat(user.getTokenVersion()).isEqualTo(2L);
    }

    @Test
    void changePasswordRejectsSamePassword() {
        IdentityUser user = identityUser("current-hash", 2L);

        when(passwordEncoder.matches("SamePass1", "current-hash")).thenReturn(true);

        assertThatThrownBy(() -> identityCredentialService.changePassword(user, "SamePass1", "SamePass1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("新密码不能与旧密码相同。");

        verify(passwordEncoder, never()).encode("SamePass1");
    }

    @Test
    void resetPasswordUpdatesHashAndInvalidatesExistingTokens() {
        IdentityUser user = identityUser("current-hash", 2L);

        when(passwordEncoder.matches("ResetPass1", "current-hash")).thenReturn(false);
        when(passwordEncoder.encode("ResetPass1")).thenReturn("reset-hash");

        identityCredentialService.resetPassword(user, "ResetPass1");

        assertThat(user.getPasswordHash()).isEqualTo("reset-hash");
        assertThat(user.getTokenVersion()).isEqualTo(3L);
    }

    @Test
    void resetPasswordRejectsSameCurrentPassword() {
        IdentityUser user = identityUser("current-hash", 2L);

        when(passwordEncoder.matches("ResetPass1", "current-hash")).thenReturn(true);

        assertThatThrownBy(() -> identityCredentialService.resetPassword(user, "ResetPass1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("新密码不能与当前密码相同。");

        verify(passwordEncoder, never()).encode("ResetPass1");
    }

    private IdentityUser identityUser(String passwordHash, Long tokenVersion) {
        IdentityUser user = newIdentityUser();
        ReflectionTestUtils.setField(user, "passwordHash", passwordHash);
        ReflectionTestUtils.setField(user, "tokenVersion", tokenVersion);
        return user;
    }

    private IdentityUser newIdentityUser() {
        try {
            var constructor = IdentityUser.class.getDeclaredConstructor();
            constructor.setAccessible(true);
            return constructor.newInstance();
        } catch (ReflectiveOperationException ex) {
            throw new AssertionError("Failed to create IdentityUser test fixture.", ex);
        }
    }
}
