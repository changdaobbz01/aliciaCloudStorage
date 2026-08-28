package com.alicia.cloudstorage.api.principal;

import com.alicia.cloudstorage.api.identity.UserRole;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class CurrentPrincipalTest {

    @Test
    void globalAdministratorsCanAccessCloudAdminApis() {
        CurrentPrincipal principal = new CurrentPrincipal(1L, UserRole.ADMIN, Map.of("cloud", "CLOUD_USER"));

        assertThat(principal.isAdmin()).isTrue();
        assertThat(principal.isCloudAdmin()).isTrue();
    }

    @Test
    void cloudApplicationAdministratorsCanAccessCloudAdminApis() {
        CurrentPrincipal principal = new CurrentPrincipal(2L, UserRole.USER, Map.of("cloud", "CLOUD_ADMIN"));

        assertThat(principal.isAdmin()).isTrue();
        assertThat(principal.isCloudAdmin()).isTrue();
    }

    @Test
    void regularCloudUsersCannotAccessCloudAdminApis() {
        CurrentPrincipal principal = new CurrentPrincipal(3L, UserRole.USER, Map.of("cloud", "CLOUD_USER"));

        assertThat(principal.isAdmin()).isFalse();
        assertThat(principal.isCloudAdmin()).isFalse();
    }

    @Test
    void adminsOfOtherApplicationsCannotAccessCloudAdminApis() {
        CurrentPrincipal principal = new CurrentPrincipal(4L, UserRole.USER, Map.of("rag", "RAG_ADMIN"));

        assertThat(principal.isAdmin()).isFalse();
        assertThat(principal.isCloudAdmin()).isFalse();
    }
}
