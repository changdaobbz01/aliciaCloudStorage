package com.alicia.cloudstorage.identity.service;

import com.alicia.cloudstorage.identity.dto.UpdateIdentityApplicationRoleRequest;
import com.alicia.cloudstorage.identity.entity.IdentityUser;
import com.alicia.cloudstorage.identity.entity.IdentityUserAppRole;
import com.alicia.cloudstorage.identity.entity.IdentityUserRole;
import com.alicia.cloudstorage.identity.repository.IdentityUserAppRoleRepository;
import com.alicia.cloudstorage.identity.repository.IdentityUserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IdentityApplicationRoleServiceTest {

    @Mock
    private IdentityPrincipalService identityPrincipalService;

    @Mock
    private IdentityUserRepository identityUserRepository;

    @Mock
    private IdentityUserAppRoleRepository identityUserAppRoleRepository;

    @Mock
    private IdentityAuditLogService identityAuditLogService;

    private IdentityApplicationRoleService service;

    @BeforeEach
    void setUp() {
        service = new IdentityApplicationRoleService(
                identityPrincipalService,
                identityUserRepository,
                identityUserAppRoleRepository,
                identityAuditLogService
        );
    }

    @Test
    void effectiveRolesDefaultKnownAppAdminsForGlobalAdmin() {
        IdentityUser admin = identityUser(1L, IdentityUserRole.ADMIN);
        when(identityUserAppRoleRepository.findByUser_IdOrderByAppCodeAsc(1L)).thenReturn(List.of());

        var roles = service.effectiveRolesForUser(admin);

        assertThat(roles)
                .containsEntry("cloud", "CLOUD_ADMIN")
                .containsEntry("rag", "RAG_ADMIN");
    }

    @Test
    void effectiveRolesDefaultKnownAppUsersForRegularUser() {
        IdentityUser user = identityUser(2L, IdentityUserRole.USER);
        when(identityUserAppRoleRepository.findByUser_IdOrderByAppCodeAsc(2L)).thenReturn(List.of());

        var roles = service.effectiveRolesForUser(user);

        assertThat(roles)
                .containsEntry("cloud", "CLOUD_USER")
                .containsEntry("rag", "RAG_USER");
    }

    @Test
    void effectiveRolesKeepGlobalAdminAsCloudAdminWhenExplicitRoleIsLower() {
        IdentityUser admin = identityUser(1L, IdentityUserRole.ADMIN);
        IdentityUserAppRole appRole = appRole(admin, "cloud", "CLOUD_USER");
        when(identityUserAppRoleRepository.findByUser_IdOrderByAppCodeAsc(1L)).thenReturn(List.of(appRole));

        var roles = service.effectiveRolesForUser(admin);

        assertThat(roles).containsEntry("cloud", "CLOUD_ADMIN");
    }

    @Test
    void effectiveRolesUseExplicitKnownAppRoleForRegularUser() {
        IdentityUser user = identityUser(2L, IdentityUserRole.USER);
        IdentityUserAppRole appRole = appRole(user, "rag", "RAG_ADMIN");
        when(identityUserAppRoleRepository.findByUser_IdOrderByAppCodeAsc(2L)).thenReturn(List.of(appRole));

        var roles = service.effectiveRolesForUser(user);

        assertThat(roles).containsEntry("rag", "RAG_ADMIN");
    }

    @Test
    void updateUserRoleCreatesApplicationRoleAndAudits() {
        IdentityUser admin = identityUser(1L, IdentityUserRole.ADMIN);
        IdentityUser target = identityUser(2L, IdentityUserRole.USER);

        when(identityPrincipalService.requireAdminUser("Bearer admin")).thenReturn(admin);
        when(identityUserRepository.findById(2L)).thenReturn(Optional.of(target));
        when(identityUserAppRoleRepository.findByUser_IdAndAppCode(2L, "rag")).thenReturn(Optional.empty());
        when(identityUserAppRoleRepository.save(any(IdentityUserAppRole.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.updateUserRole(
                "Bearer admin",
                2L,
                "rag",
                new UpdateIdentityApplicationRoleRequest("rag_admin")
        );

        ArgumentCaptor<IdentityUserAppRole> appRoleCaptor = ArgumentCaptor.forClass(IdentityUserAppRole.class);
        verify(identityUserAppRoleRepository).save(appRoleCaptor.capture());

        assertThat(response.appCode()).isEqualTo("rag");
        assertThat(response.roleCode()).isEqualTo("RAG_ADMIN");
        assertThat(appRoleCaptor.getValue().getUser()).isSameAs(target);
        assertThat(appRoleCaptor.getValue().getAppCode()).isEqualTo("rag");
        assertThat(appRoleCaptor.getValue().getRoleCode()).isEqualTo("RAG_ADMIN");
        verify(identityAuditLogService).record(
                IdentityAuditEventType.ADMIN_APP_ROLE_UPDATE,
                IdentityAuditOutcome.SUCCESS,
                1L,
                2L,
                "rag",
                "RAG_ADMIN"
        );
    }

    @Test
    void updateUserRoleDoesNotDowngradeGlobalAdminToApplicationUser() {
        IdentityUser admin = identityUser(1L, IdentityUserRole.ADMIN);
        IdentityUser target = identityUser(2L, IdentityUserRole.ADMIN);

        when(identityPrincipalService.requireAdminUser("Bearer admin")).thenReturn(admin);
        when(identityUserRepository.findById(2L)).thenReturn(Optional.of(target));

        assertThatThrownBy(() -> service.updateUserRole(
                "Bearer admin",
                2L,
                "rag",
                new UpdateIdentityApplicationRoleRequest("RAG_USER")
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("全局管理员始终拥有应用管理员权限。");
    }

    private IdentityUser identityUser(Long id, IdentityUserRole role) {
        IdentityUser user = new IdentityUser();
        ReflectionTestUtils.setField(user, "id", id);
        ReflectionTestUtils.setField(user, "role", role);
        return user;
    }

    private IdentityUserAppRole appRole(IdentityUser user, String appCode, String roleCode) {
        IdentityUserAppRole appRole = new IdentityUserAppRole();
        appRole.setUser(user);
        appRole.setAppCode(appCode);
        appRole.setRoleCode(roleCode);
        return appRole;
    }
}
