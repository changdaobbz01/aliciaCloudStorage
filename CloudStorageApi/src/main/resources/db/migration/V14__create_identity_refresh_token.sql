CREATE TABLE IF NOT EXISTS identity_refresh_token (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    token_hash VARCHAR(64) NOT NULL,
    token_version BIGINT NOT NULL,
    issued_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_used_at DATETIME NULL,
    expires_at DATETIME NOT NULL,
    revoked_at DATETIME NULL,
    revoke_reason VARCHAR(64) NULL,
    client_ip VARCHAR(64) NULL,
    user_agent VARCHAR(500) NULL,
    CONSTRAINT uk_identity_refresh_token_hash UNIQUE (token_hash),
    INDEX idx_identity_refresh_token_user_id (user_id),
    INDEX idx_identity_refresh_token_expires_at (expires_at),
    INDEX idx_identity_refresh_token_revoked_at (revoked_at)
);
