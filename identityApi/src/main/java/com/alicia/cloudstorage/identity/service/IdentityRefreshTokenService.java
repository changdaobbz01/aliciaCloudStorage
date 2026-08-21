package com.alicia.cloudstorage.identity.service;

import com.alicia.cloudstorage.identity.dto.IdentitySessionResponse;
import com.alicia.cloudstorage.identity.entity.IdentityUser;
import com.alicia.cloudstorage.identity.entity.IdentityUserStatus;
import com.alicia.cloudstorage.identity.repository.IdentityUserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;

@Service
public class IdentityRefreshTokenService {

    private static final int RANDOM_TOKEN_BYTES = 32;
    private static final int MAX_CLIENT_IP_LENGTH = 64;
    private static final int MAX_USER_AGENT_LENGTH = 500;
    private static final String INSERT_SQL = """
            INSERT INTO identity_refresh_token (
                user_id,
                token_hash,
                token_version,
                issued_at,
                expires_at,
                client_ip,
                user_agent
            ) VALUES (?, ?, ?, ?, ?, ?, ?)
            """;
    private static final String SELECT_ACTIVE_BY_HASH_SQL = """
            SELECT id, user_id, token_hash, token_version, issued_at, last_used_at, expires_at, revoked_at, revoke_reason, client_ip, user_agent
            FROM identity_refresh_token
            WHERE token_hash = ?
            """;
    private static final String SELECT_ACTIVE_BY_ID_SQL = """
            SELECT id, user_id, token_hash, token_version, issued_at, last_used_at, expires_at, revoked_at, revoke_reason, client_ip, user_agent
            FROM identity_refresh_token
            WHERE id = ?
            """;
    private static final String SELECT_USER_SESSIONS_SQL = """
            SELECT id, issued_at, last_used_at, expires_at, revoked_at, revoke_reason, client_ip, user_agent
            FROM identity_refresh_token
            WHERE user_id = ?
            """;
    private static final String SELECT_SESSION_OWNER_SQL = """
            SELECT user_id
            FROM identity_refresh_token
            WHERE id = ?
            """;
    private static final String ROTATE_SQL = """
            UPDATE identity_refresh_token
            SET token_hash = ?,
                last_used_at = ?,
                expires_at = ?,
                client_ip = ?,
                user_agent = ?
            WHERE id = ?
              AND token_hash = ?
              AND revoked_at IS NULL
            """;
    private static final String REVOKE_SESSION_SQL = """
            UPDATE identity_refresh_token
            SET revoked_at = ?,
                revoke_reason = ?
            WHERE id = ?
              AND revoked_at IS NULL
            """;
    private static final String REVOKE_ALL_FOR_USER_SQL = """
            UPDATE identity_refresh_token
            SET revoked_at = ?,
                revoke_reason = ?
            WHERE user_id = ?
              AND revoked_at IS NULL
            """;

    private final IdentityUserRepository identityUserRepository;
    private final JdbcTemplate jdbcTemplate;
    private final Clock clock;
    private final long refreshTokenExpireSeconds;
    private final SecureRandom secureRandom = new SecureRandom();

    public IdentityRefreshTokenService(
            IdentityUserRepository identityUserRepository,
            JdbcTemplate jdbcTemplate,
            Clock clock,
            @Value("${alicia.auth.refresh-token-expire-seconds:2592000}") long refreshTokenExpireSeconds
    ) {
        if (refreshTokenExpireSeconds <= 0L) {
            throw new IllegalStateException("Refresh token expiration must be greater than zero.");
        }

        this.identityUserRepository = identityUserRepository;
        this.jdbcTemplate = jdbcTemplate;
        this.clock = clock;
        this.refreshTokenExpireSeconds = refreshTokenExpireSeconds;
    }

    @Transactional
    public IssuedRefreshToken issue(IdentityUser user, String clientIp, String userAgent) {
        String rawToken = generateRawToken();
        String tokenHash = hashToken(rawToken);
        LocalDateTime now = LocalDateTime.now(clock);
        LocalDateTime expiresAt = now.plusSeconds(refreshTokenExpireSeconds);
        KeyHolder keyHolder = new GeneratedKeyHolder();

        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(INSERT_SQL, Statement.RETURN_GENERATED_KEYS);
            statement.setLong(1, user.getId());
            statement.setString(2, tokenHash);
            statement.setLong(3, user.getTokenVersion() == null ? 0L : user.getTokenVersion());
            statement.setTimestamp(4, Timestamp.valueOf(now));
            statement.setTimestamp(5, Timestamp.valueOf(expiresAt));
            statement.setString(6, normalize(clientIp, MAX_CLIENT_IP_LENGTH));
            statement.setString(7, normalize(userAgent, MAX_USER_AGENT_LENGTH));
            return statement;
        }, keyHolder);

        Number key = keyHolder.getKey();
        if (key == null) {
            throw new IllegalStateException("创建刷新令牌会话失败。");
        }

        return new IssuedRefreshToken(key.longValue(), rawToken, expiresAt);
    }

    @Transactional
    public RefreshedIdentitySession rotate(String refreshToken, String clientIp, String userAgent) {
        StoredRefreshSession session = requireStoredSessionByHash(refreshToken);
        requireActive(session);
        IdentityUser user = loadActiveUser(session);
        requireTokenVersion(session, user);

        String nextRawToken = generateRawToken();
        String nextTokenHash = hashToken(nextRawToken);
        LocalDateTime now = LocalDateTime.now(clock);
        LocalDateTime nextExpiresAt = now.plusSeconds(refreshTokenExpireSeconds);
        int updated = jdbcTemplate.update(
                ROTATE_SQL,
                nextTokenHash,
                Timestamp.valueOf(now),
                Timestamp.valueOf(nextExpiresAt),
                normalize(clientIp, MAX_CLIENT_IP_LENGTH),
                normalize(userAgent, MAX_USER_AGENT_LENGTH),
                session.id(),
                session.tokenHash()
        );

        if (updated != 1) {
            throw new IdentityAuthException("刷新登录状态已失效。");
        }

        return new RefreshedIdentitySession(user, session.id(), nextRawToken, nextExpiresAt);
    }

    @Transactional(readOnly = true)
    public void requireActiveSession(Long sessionId, IdentityUser user) {
        if (sessionId == null) {
            return;
        }

        StoredRefreshSession session = requireStoredSessionById(sessionId);
        if (!session.userId().equals(user.getId())) {
            throw new IdentityAuthException("登录状态已失效。");
        }

        requireActive(session);
        requireTokenVersion(session, user);
    }

    @Transactional
    public void revokeSession(Long sessionId, String reason) {
        if (sessionId == null) {
            return;
        }

        jdbcTemplate.update(
                REVOKE_SESSION_SQL,
                Timestamp.valueOf(LocalDateTime.now(clock)),
                normalize(reason, 64),
                sessionId
        );
    }

    @Transactional
    public void revokeAllForUser(Long userId, String reason) {
        jdbcTemplate.update(
                REVOKE_ALL_FOR_USER_SQL,
                Timestamp.valueOf(LocalDateTime.now(clock)),
                normalize(reason, 64),
                userId
        );
    }

    @Transactional(readOnly = true)
    public List<IdentitySessionResponse> listUserSessions(
            Long userId,
            Long currentSessionId,
            boolean includeRevoked
    ) {
        String sql = SELECT_USER_SESSIONS_SQL
                + (includeRevoked ? "" : " AND revoked_at IS NULL")
                + " ORDER BY CASE WHEN revoked_at IS NULL THEN 0 ELSE 1 END, COALESCE(last_used_at, issued_at) DESC, id DESC";

        return jdbcTemplate.query(
                sql,
                (resultSet, rowNumber) -> mapSessionResponse(resultSet, currentSessionId),
                userId
        );
    }

    @Transactional
    public void revokeUserSession(Long userId, Long sessionId, String reason) {
        List<Long> owners = jdbcTemplate.query(
                SELECT_SESSION_OWNER_SQL,
                (resultSet, rowNumber) -> resultSet.getLong("user_id"),
                sessionId
        );

        if (owners.isEmpty() || !owners.get(0).equals(userId)) {
            throw new IllegalArgumentException("登录会话不存在。");
        }

        revokeSession(sessionId, reason);
    }

    private StoredRefreshSession requireStoredSessionByHash(String refreshToken) {
        String rawToken = normalize(refreshToken, 512);
        if (rawToken == null) {
            throw new IdentityAuthException("刷新令牌不能为空。");
        }

        List<StoredRefreshSession> sessions = jdbcTemplate.query(
                SELECT_ACTIVE_BY_HASH_SQL,
                (rs, rowNum) -> mapStoredSession(rs),
                hashToken(rawToken)
        );

        if (sessions.isEmpty()) {
            throw new IdentityAuthException("刷新登录状态已失效。");
        }

        return sessions.get(0);
    }

    private StoredRefreshSession requireStoredSessionById(Long sessionId) {
        List<StoredRefreshSession> sessions = jdbcTemplate.query(
                SELECT_ACTIVE_BY_ID_SQL,
                (rs, rowNum) -> mapStoredSession(rs),
                sessionId
        );

        if (sessions.isEmpty()) {
            throw new IdentityAuthException("登录状态已失效。");
        }

        return sessions.get(0);
    }

    private StoredRefreshSession mapStoredSession(java.sql.ResultSet resultSet) throws java.sql.SQLException {
        Timestamp lastUsedAt = resultSet.getTimestamp("last_used_at");
        Timestamp revokedAt = resultSet.getTimestamp("revoked_at");
        return new StoredRefreshSession(
                resultSet.getLong("id"),
                resultSet.getLong("user_id"),
                resultSet.getString("token_hash"),
                resultSet.getLong("token_version"),
                resultSet.getTimestamp("issued_at").toLocalDateTime(),
                lastUsedAt == null ? null : lastUsedAt.toLocalDateTime(),
                resultSet.getTimestamp("expires_at").toLocalDateTime(),
                revokedAt == null ? null : revokedAt.toLocalDateTime(),
                resultSet.getString("revoke_reason"),
                resultSet.getString("client_ip"),
                resultSet.getString("user_agent")
        );
    }

    private IdentitySessionResponse mapSessionResponse(
            java.sql.ResultSet resultSet,
            Long currentSessionId
    ) throws java.sql.SQLException {
        Long sessionId = resultSet.getLong("id");
        Timestamp lastUsedAt = resultSet.getTimestamp("last_used_at");
        Timestamp revokedAt = resultSet.getTimestamp("revoked_at");
        return new IdentitySessionResponse(
                sessionId,
                resultSet.getTimestamp("issued_at").toLocalDateTime(),
                lastUsedAt == null ? null : lastUsedAt.toLocalDateTime(),
                resultSet.getTimestamp("expires_at").toLocalDateTime(),
                revokedAt == null ? null : revokedAt.toLocalDateTime(),
                resultSet.getString("revoke_reason"),
                resultSet.getString("client_ip"),
                resultSet.getString("user_agent"),
                currentSessionId != null && currentSessionId.equals(sessionId)
        );
    }

    private void requireActive(StoredRefreshSession session) {
        if (session.revokedAt() != null) {
            throw new IdentityAuthException("登录状态已失效。");
        }

        if (!LocalDateTime.now(clock).isBefore(session.expiresAt())) {
            throw new IdentityAuthException("登录状态已过期。");
        }
    }

    private IdentityUser loadActiveUser(StoredRefreshSession session) {
        IdentityUser user = identityUserRepository.findById(session.userId())
                .orElseThrow(() -> new IdentityAuthException("登录用户不存在。"));

        if (user.getStatus() != IdentityUserStatus.ACTIVE) {
            throw new IdentityAuthException("当前账号已停用。");
        }

        return user;
    }

    private void requireTokenVersion(StoredRefreshSession session, IdentityUser user) {
        long currentTokenVersion = user.getTokenVersion() == null ? 0L : user.getTokenVersion();
        if (currentTokenVersion != session.tokenVersion()) {
            throw new IdentityAuthException("登录状态已失效。");
        }
    }

    private String generateRawToken() {
        byte[] bytes = new byte[RANDOM_TOKEN_BYTES];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String hashToken(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder(bytes.length * 2);
            for (byte value : bytes) {
                builder.append(String.format("%02x", value));
            }
            return builder.toString();
        } catch (Exception ex) {
            throw new IllegalStateException("生成刷新令牌摘要失败。", ex);
        }
    }

    private String normalize(String value, int maxLength) {
        if (value == null) {
            return null;
        }

        String normalized = value.trim();
        if (normalized.isEmpty()) {
            return null;
        }

        if (normalized.length() <= maxLength) {
            return normalized;
        }

        return normalized.substring(0, maxLength);
    }

    public record IssuedRefreshToken(
            Long sessionId,
            String token,
            LocalDateTime expiresAt
    ) {
    }

    public record RefreshedIdentitySession(
            IdentityUser user,
            Long sessionId,
            String refreshToken,
            LocalDateTime expiresAt
    ) {
    }

    private record StoredRefreshSession(
            Long id,
            Long userId,
            String tokenHash,
            long tokenVersion,
            LocalDateTime issuedAt,
            LocalDateTime lastUsedAt,
            LocalDateTime expiresAt,
            LocalDateTime revokedAt,
            String revokeReason,
            String clientIp,
            String userAgent
    ) {
    }
}
