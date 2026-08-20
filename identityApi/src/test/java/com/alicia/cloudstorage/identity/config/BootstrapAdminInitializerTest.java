package com.alicia.cloudstorage.identity.config;

import com.alicia.cloudstorage.identity.entity.IdentityUser;
import com.alicia.cloudstorage.identity.repository.IdentityUserRepository;
import com.alicia.cloudstorage.identity.service.IdentityUserCreationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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
    private IdentityUserCreationService identityUserCreationService;

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
        when(identityUserCreationService.createBootstrapAdmin(
                "13800000000",
                "Passw0rd",
                " Alicia Admin ",
                " cos:user-avatars/1/a.png "
        )).thenReturn(identityUser("13800000000"));

        initializer.run(null);

        verify(identityUserCreationService).createBootstrapAdmin(
                "13800000000",
                "Passw0rd",
                " Alicia Admin ",
                " cos:user-avatars/1/a.png "
        );
    }

    @Test
    void wrapsInvalidBootstrapConfigurationAsStartupFailure() {
        BootstrapAdminInitializer initializer = initializer("13800000000", "Passw0rd", null, null);

        when(identityUserRepository.count()).thenReturn(0L);
        when(identityUserCreationService.createBootstrapAdmin("13800000000", "Passw0rd", null, null))
                .thenThrow(new IllegalArgumentException("密码长度至少为 6 位。"));

        assertThatThrownBy(() -> initializer.run(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Invalid bootstrap admin configuration: 密码长度至少为 6 位。");
    }

    private BootstrapAdminInitializer initializer(
            String phone,
            String password,
            String nickname,
            String avatarUrl
    ) {
        return new BootstrapAdminInitializer(
                identityUserRepository,
                identityUserCreationService,
                phone,
                password,
                nickname,
                avatarUrl
        );
    }

    private IdentityUser identityUser(String phoneNumber) {
        IdentityUser user = new IdentityUser();
        user.setPhoneNumber(phoneNumber);
        return user;
    }
}
