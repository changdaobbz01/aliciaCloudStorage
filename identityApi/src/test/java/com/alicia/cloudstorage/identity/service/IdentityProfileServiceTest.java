package com.alicia.cloudstorage.identity.service;

import com.alicia.cloudstorage.identity.dto.UpdateIdentityProfileRequest;
import com.alicia.cloudstorage.identity.dto.IdentityUserResponse;
import com.alicia.cloudstorage.identity.entity.IdentityUser;
import com.alicia.cloudstorage.identity.entity.IdentityUserRole;
import com.alicia.cloudstorage.identity.entity.IdentityUserStatus;
import com.alicia.cloudstorage.identity.repository.IdentityUserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IdentityProfileServiceTest {

    @Mock
    private IdentityPrincipalService identityPrincipalService;

    @Mock
    private IdentityUserRepository identityUserRepository;

    @Spy
    private IdentityUserInputNormalizer identityUserInputNormalizer = new IdentityUserInputNormalizer();

    @Mock
    private IdentityAuditLogService identityAuditLogService;

    @Mock
    private IdentityUserResponseAssembler identityUserResponseAssembler;

    private IdentityProfileService identityProfileService;

    @BeforeEach
    void setUp() {
        identityProfileService = new IdentityProfileService(
                identityPrincipalService,
                identityUserRepository,
                identityUserInputNormalizer,
                identityAuditLogService,
                identityUserResponseAssembler
        );
    }

    @Test
    void updateProfileUpdatesIdentityFields() {
        IdentityUser user = identityUser(18L);

        when(identityPrincipalService.requireActiveUser("Bearer token")).thenReturn(user);
        when(identityUserRepository.existsByPhoneNumberAndIdNot("13900000000", 18L)).thenReturn(false);
        when(identityUserRepository.save(user)).thenReturn(user);
        when(identityUserResponseAssembler.toResponse(user))
                .thenAnswer(invocation -> IdentityUserResponse.from(invocation.getArgument(0)));

        var response = identityProfileService.updateProfile(
                "Bearer token",
                new UpdateIdentityProfileRequest(
                        "13900000000",
                        " Updated Alicia ",
                        " cos:user-avatars/18/new.webp "
                )
        );

        assertThat(user.getPhoneNumber()).isEqualTo("13900000000");
        assertThat(user.getNickname()).isEqualTo("Updated Alicia");
        assertThat(user.getAvatarUrl()).isEqualTo("cos:user-avatars/18/new.webp");
        assertThat(response.nickname()).isEqualTo("Updated Alicia");
        verify(identityUserRepository).save(user);
    }

    @Test
    void updateProfileAllowsEmptyPhoneForEmailUser() {
        IdentityUser user = identityUser(18L);

        when(identityPrincipalService.requireActiveUser("Bearer token")).thenReturn(user);
        when(identityUserRepository.save(user)).thenReturn(user);
        when(identityUserResponseAssembler.toResponse(user))
                .thenAnswer(invocation -> IdentityUserResponse.from(invocation.getArgument(0)));

        var response = identityProfileService.updateProfile(
                "Bearer token",
                new UpdateIdentityProfileRequest("", "Email User", null)
        );

        assertThat(user.getPhoneNumber()).isNull();
        assertThat(response.phoneNumber()).isNull();
        verify(identityUserRepository).save(user);
    }

    @Test
    void updateProfileRejectsDuplicatePhoneNumber() {
        IdentityUser user = identityUser(18L);

        when(identityPrincipalService.requireActiveUser("Bearer token")).thenReturn(user);
        when(identityUserRepository.existsByPhoneNumberAndIdNot("13900000000", 18L)).thenReturn(true);

        assertThatThrownBy(() -> identityProfileService.updateProfile(
                "Bearer token",
                new UpdateIdentityProfileRequest("13900000000", "Alicia", null)
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("手机号已被其他账户使用。");

        verify(identityUserRepository, never()).save(user);
    }

    @Test
    void updateProfileRejectsEmptyPhoneForPhoneOnlyUser() {
        IdentityUser user = identityUser(18L);
        ReflectionTestUtils.setField(user, "email", null);

        when(identityPrincipalService.requireActiveUser("Bearer token")).thenReturn(user);

        assertThatThrownBy(() -> identityProfileService.updateProfile(
                "Bearer token",
                new UpdateIdentityProfileRequest("", "Phone User", null)
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("手机号不能为空。");

        verify(identityUserRepository, never()).save(user);
    }

    private IdentityUser identityUser(Long id) {
        IdentityUser user = new IdentityUser();
        ReflectionTestUtils.setField(user, "id", id);
        ReflectionTestUtils.setField(user, "phoneNumber", "13800000000");
        ReflectionTestUtils.setField(user, "email", "email-user@example.com");
        ReflectionTestUtils.setField(user, "emailVerifiedAt", LocalDateTime.of(2026, 8, 17, 15, 30));
        ReflectionTestUtils.setField(user, "nickname", "Alicia");
        ReflectionTestUtils.setField(user, "avatarUrl", "cos:user-avatars/18/avatar.webp");
        ReflectionTestUtils.setField(user, "passwordHash", "hash");
        ReflectionTestUtils.setField(user, "tokenVersion", 2L);
        ReflectionTestUtils.setField(user, "role", IdentityUserRole.USER);
        ReflectionTestUtils.setField(user, "status", IdentityUserStatus.ACTIVE);
        ReflectionTestUtils.setField(user, "createdAt", LocalDateTime.of(2026, 4, 29, 15, 30));
        return user;
    }
}
