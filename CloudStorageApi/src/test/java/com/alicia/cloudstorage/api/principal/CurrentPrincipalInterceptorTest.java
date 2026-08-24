package com.alicia.cloudstorage.api.principal;

import com.alicia.cloudstorage.api.identity.IdentityUserSnapshot;
import com.alicia.cloudstorage.api.identity.UserRole;
import com.alicia.cloudstorage.api.identity.UserStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CurrentPrincipalInterceptorTest {

    @Mock
    private CurrentPrincipalService currentPrincipalService;

    @Test
    void preHandleStoresPrincipalAndIdentityUserSnapshot() {
        CurrentPrincipalInterceptor interceptor = new CurrentPrincipalInterceptor(currentPrincipalService);
        IdentityUserSnapshot identityUser = identityUser(22L, UserRole.USER);
        AuthenticatedPrincipal authenticated =
                new AuthenticatedPrincipal(new CurrentPrincipal(22L, UserRole.USER), identityUser);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer token");

        when(currentPrincipalService.requireAuthenticatedPrincipal("Bearer token")).thenReturn(authenticated);

        boolean proceed = interceptor.preHandle(request, new MockHttpServletResponse(), new Object());

        assertThat(proceed).isTrue();
        assertThat(request.getAttribute(PrincipalRequestAttributes.CURRENT_PRINCIPAL))
                .isEqualTo(authenticated.principal());
        assertThat(request.getAttribute(PrincipalRequestAttributes.CURRENT_USER_ID)).isEqualTo(22L);
        assertThat(request.getAttribute(PrincipalRequestAttributes.CURRENT_IDENTITY_USER)).isSameAs(identityUser);
    }

    private IdentityUserSnapshot identityUser(Long id, UserRole role) {
        return new IdentityUserSnapshot(
                id,
                "13900000000",
                "user@example.com",
                "Alicia",
                null,
                role,
                UserStatus.ACTIVE,
                LocalDateTime.of(2026, 4, 29, 15, 30)
        );
    }
}
