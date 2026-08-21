package com.alicia.cloudstorage.identity.service;

import com.alicia.cloudstorage.identity.dto.AdminCreateIdentityUserRequest;
import com.alicia.cloudstorage.identity.entity.IdentityUser;
import com.alicia.cloudstorage.identity.entity.IdentityUserRole;
import com.alicia.cloudstorage.identity.entity.IdentityUserStatus;
import com.alicia.cloudstorage.identity.repository.IdentityUserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IdentityAdminUserServiceTest {

    @Mock
    private IdentityPrincipalService identityPrincipalService;

    @Mock
    private IdentityUserRepository identityUserRepository;

    @Mock
    private IdentityUserCreationService identityUserCreationService;

    @Mock
    private IdentityAuditLogService identityAuditLogService;

    @InjectMocks
    private IdentityAdminUserService identityAdminUserService;

    @Test
    void listUsersRequiresAdminAndReturnsIdentityUsers() {
        IdentityUser admin = identityUser(1L, IdentityUserRole.ADMIN, 0L);
        IdentityUser firstUser = identityUser(1L, IdentityUserRole.ADMIN, 0L);
        IdentityUser secondUser = identityUser(2L, IdentityUserRole.USER, 0L);
        secondUser.setEmail("second@example.com");
        secondUser.setNickname("Second");

        when(identityPrincipalService.requireAdminUser("Bearer admin-token")).thenReturn(admin);
        when(identityUserRepository.findAllByOrderByIdAsc()).thenReturn(List.of(firstUser, secondUser));

        var response = identityAdminUserService.listUsers("Bearer admin-token");

        assertThat(response).hasSize(2);
        assertThat(response.get(0).id()).isEqualTo(1L);
        assertThat(response.get(1).email()).isEqualTo("second@example.com");
        verify(identityPrincipalService).requireAdminUser("Bearer admin-token");
    }

    @Test
    void createUserRequiresAdminAndDelegatesIdentityCreation() {
        IdentityUser admin = identityUser(1L, IdentityUserRole.ADMIN, 0L);
        IdentityUser createdUser = identityUser(72L, IdentityUserRole.USER, 0L);
        createdUser.setPhoneNumber("13800000001");
        createdUser.setEmail(null);
        createdUser.setNickname("New User");
        createdUser.setAvatarUrl("https://example.com/avatar.png");

        AdminCreateIdentityUserRequest request = new AdminCreateIdentityUserRequest(
                "13800000001",
                null,
                "New User",
                "https://example.com/avatar.png",
                "Passw0rd",
                "USER"
        );

        when(identityPrincipalService.requireAdminUser("Bearer admin-token")).thenReturn(admin);
        when(identityUserCreationService.createAdminManagedUser(request)).thenReturn(createdUser);

        var response = identityAdminUserService.createUser("Bearer admin-token", request);

        verify(identityPrincipalService).requireAdminUser("Bearer admin-token");
        verify(identityUserCreationService).createAdminManagedUser(request);
        assertThat(response.id()).isEqualTo(72L);
        assertThat(response.phoneNumber()).isEqualTo("13800000001");
    }

    private IdentityUser identityUser(Long id, IdentityUserRole role, Long tokenVersion) {
        IdentityUser user = newIdentityUser();
        ReflectionTestUtils.setField(user, "id", id);
        ReflectionTestUtils.setField(user, "phoneNumber", "13800000000");
        ReflectionTestUtils.setField(user, "email", "email-user@example.com");
        ReflectionTestUtils.setField(user, "emailVerifiedAt", LocalDateTime.of(2026, 8, 17, 15, 30));
        ReflectionTestUtils.setField(user, "nickname", "Alicia");
        ReflectionTestUtils.setField(user, "avatarUrl", "cos:user-avatars/18/avatar.webp");
        ReflectionTestUtils.setField(user, "passwordHash", "hash");
        ReflectionTestUtils.setField(user, "tokenVersion", tokenVersion);
        ReflectionTestUtils.setField(user, "role", role);
        ReflectionTestUtils.setField(user, "status", IdentityUserStatus.ACTIVE);
        ReflectionTestUtils.setField(user, "createdAt", LocalDateTime.of(2026, 4, 29, 15, 30));
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
