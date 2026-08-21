CREATE TABLE IF NOT EXISTS identity_audit_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    event_type VARCHAR(64) NOT NULL,
    outcome VARCHAR(16) NOT NULL,
    actor_user_id BIGINT NULL,
    target_user_id BIGINT NULL,
    identifier VARCHAR(255) NULL,
    detail VARCHAR(1000) NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_identity_audit_log_created_at (created_at),
    INDEX idx_identity_audit_log_actor_user_id (actor_user_id),
    INDEX idx_identity_audit_log_target_user_id (target_user_id),
    INDEX idx_identity_audit_log_event_type (event_type)
);
