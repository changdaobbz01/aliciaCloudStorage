package com.alicia.cloudstorage.identity.service;

import com.alicia.cloudstorage.identity.entity.IdentityUser;
import com.alicia.cloudstorage.identity.entity.IdentityUserRole;
import com.alicia.cloudstorage.identity.entity.IdentityUserStatus;
import com.alicia.cloudstorage.identity.repository.IdentityUserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.test.util.ReflectionTestUtils;

import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IdentityRefreshTokenServiceTest {

    private static final Clock FIXED_CLOCK = Clock.fixed(
            Instant.parse("2026-08-21T08:00:00Z"),
            ZoneId.of("Asia/Shanghai")
    );

    @Mock
    private IdentityUserRepository identityUserRepository;

    @Mock
    private JdbcTemplate jdbcTemplate;

    private IdentityRefreshTokenService service;

    @BeforeEach
    void setUp() {
        service = new IdentityRefreshTokenService(identityUserRepository, jdbcTemplate, FIXED_CLOCK, 3600L);
    }

    @Test
    void issueStoresRefreshSessionAndReturnsRawToken() {
        IdentityUser user = identityUser(18L, 2L, IdentityUserStatus.ACTIVE);
        when(jdbcTemplate.update(any(), any(KeyHolder.class))).thenAnswer(invocation -> {
            KeyHolder keyHolder = invocation.getArgument(1);
            keyHolder.getKeyList().add(Map.of("GENERATED_KEY", 51L));
            return 1;
        });

        var issuedToken = service.issue(user, "203.0.113.8", "JUnit");

        assertThat(issuedToken.sessionId()).isEqualTo(51L);
        assertThat(issuedToken.token()).hasSizeGreaterThan(40);
        assertThat(issuedToken.expiresAt()).isEqualTo(LocalDateTime.of(2026, 8, 21, 17, 0));
        verify(jdbcTemplate).update(any(), any(KeyHolder.class));
    }

    @Test
    void rotateAcceptsActiveRefreshTokenAndReplacesStoredHash() throws Exception {
        IdentityUser user = identityUser(18L, 2L, IdentityUserStatus.ACTIVE);
        stubSessionQuery(storedSession(51L, 18L, 2L, null, LocalDateTime.of(2026, 8, 21, 18, 0)));
        when(identityUserRepository.findById(18L)).thenReturn(Optional.of(user));
        when(jdbcTemplate.update(anyString(), any(), any(), any(), any(), any(), eq(51L), any()))
                .thenReturn(1);

        var refreshedSession = service.rotate("refresh-token", "203.0.113.8", "JUnit");

        assertThat(refreshedSession.user()).isSameAs(user);
        assertThat(refreshedSession.sessionId()).isEqualTo(51L);
        assertThat(refreshedSession.refreshToken()).hasSizeGreaterThan(40);
        assertThat(refreshedSession.refreshToken()).isNotEqualTo("refresh-token");
    }

    @Test
    void rotateRejectsRevokedRefreshSession() throws Exception {
        stubSessionQuery(storedSession(
                51L,
                18L,
                2L,
                LocalDateTime.of(2026, 8, 21, 16, 30),
                LocalDateTime.of(2026, 8, 21, 18, 0)
        ));

        assertThatThrownBy(() -> service.rotate("refresh-token", "203.0.113.8", "JUnit"))
                .isInstanceOf(IdentityAuthException.class)
                .hasMessage("登录状态已失效。");
    }

    @Test
    void requireActiveSessionRejectsUserMismatch() throws Exception {
        IdentityUser user = identityUser(18L, 2L, IdentityUserStatus.ACTIVE);
        stubSessionQuery(storedSession(51L, 99L, 2L, null, LocalDateTime.of(2026, 8, 21, 18, 0)));

        assertThatThrownBy(() -> service.requireActiveSession(51L, user))
                .isInstanceOf(IdentityAuthException.class)
                .hasMessage("登录状态已失效。");
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private void stubSessionQuery(ResultSet resultSet) throws Exception {
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), any()))
                .thenAnswer(invocation -> {
                    RowMapper rowMapper = invocation.getArgument(1);
                    return List.of(rowMapper.mapRow(resultSet, 0));
                });
    }

    private ResultSet storedSession(
            Long id,
            Long userId,
            Long tokenVersion,
            LocalDateTime revokedAt,
            LocalDateTime expiresAt
    ) throws Exception {
        ResultSet resultSet = mock(ResultSet.class);
        when(resultSet.getLong("id")).thenReturn(id);
        when(resultSet.getLong("user_id")).thenReturn(userId);
        when(resultSet.getString("token_hash")).thenReturn("stored-token-hash");
        when(resultSet.getLong("token_version")).thenReturn(tokenVersion);
        when(resultSet.getTimestamp("issued_at"))
                .thenReturn(Timestamp.valueOf(LocalDateTime.of(2026, 8, 21, 16, 0)));
        when(resultSet.getTimestamp("last_used_at")).thenReturn(null);
        when(resultSet.getTimestamp("expires_at")).thenReturn(Timestamp.valueOf(expiresAt));
        when(resultSet.getTimestamp("revoked_at"))
                .thenReturn(revokedAt == null ? null : Timestamp.valueOf(revokedAt));
        when(resultSet.getString("revoke_reason")).thenReturn(null);
        return resultSet;
    }

    private IdentityUser identityUser(Long id, Long tokenVersion, IdentityUserStatus status) {
        IdentityUser user = new IdentityUser();
        ReflectionTestUtils.setField(user, "id", id);
        ReflectionTestUtils.setField(user, "phoneNumber", "13800000000");
        ReflectionTestUtils.setField(user, "email", "email-user@example.com");
        ReflectionTestUtils.setField(user, "nickname", "Alicia");
        ReflectionTestUtils.setField(user, "passwordHash", "hash");
        ReflectionTestUtils.setField(user, "tokenVersion", tokenVersion);
        ReflectionTestUtils.setField(user, "role", IdentityUserRole.USER);
        ReflectionTestUtils.setField(user, "status", status);
        ReflectionTestUtils.setField(user, "createdAt", LocalDateTime.of(2026, 4, 29, 15, 30));
        return user;
    }
}
