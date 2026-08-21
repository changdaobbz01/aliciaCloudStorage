CREATE TABLE IF NOT EXISTS sys_user (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    phone_number VARCHAR(20) NULL UNIQUE,
    email VARCHAR(320) NULL,
    email_verified_at DATETIME NULL,
    nickname VARCHAR(100) NOT NULL,
    avatar_url VARCHAR(500) NULL,
    password_hash VARCHAR(255) NOT NULL,
    token_version BIGINT NOT NULL DEFAULT 0,
    role VARCHAR(20) NOT NULL DEFAULT 'USER',
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_sys_user_email (email)
);

DROP PROCEDURE IF EXISTS identity_add_column_if_missing;

DELIMITER //

CREATE PROCEDURE identity_add_column_if_missing(
    IN p_table_name VARCHAR(64),
    IN p_column_name VARCHAR(64),
    IN p_column_definition TEXT
)
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = DATABASE()
          AND table_name = p_table_name
          AND column_name = p_column_name
    ) THEN
        SET @identity_ddl = CONCAT('ALTER TABLE `', p_table_name, '` ADD COLUMN ', p_column_definition);
        PREPARE identity_statement FROM @identity_ddl;
        EXECUTE identity_statement;
        DEALLOCATE PREPARE identity_statement;
    END IF;
END//

DELIMITER ;

CALL identity_add_column_if_missing(
    'sys_user',
    'token_version',
    '`token_version` BIGINT NOT NULL DEFAULT 0 AFTER `password_hash`'
);
CALL identity_add_column_if_missing(
    'sys_user',
    'email',
    '`email` VARCHAR(320) NULL AFTER `phone_number`'
);
CALL identity_add_column_if_missing(
    'sys_user',
    'email_verified_at',
    '`email_verified_at` DATETIME NULL AFTER `email`'
);

DROP PROCEDURE IF EXISTS identity_add_column_if_missing;

ALTER TABLE sys_user
    MODIFY COLUMN phone_number VARCHAR(20) NULL;

DROP PROCEDURE IF EXISTS identity_execute_if_index_missing;

DELIMITER //

CREATE PROCEDURE identity_execute_if_index_missing(
    IN p_table_name VARCHAR(64),
    IN p_index_name VARCHAR(64),
    IN p_statement TEXT
)
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.statistics
        WHERE table_schema = DATABASE()
          AND table_name = p_table_name
          AND index_name = p_index_name
    ) THEN
        SET @identity_ddl = p_statement;
        PREPARE identity_statement FROM @identity_ddl;
        EXECUTE identity_statement;
        DEALLOCATE PREPARE identity_statement;
    END IF;
END//

DELIMITER ;

CALL identity_execute_if_index_missing(
    'sys_user',
    'uk_sys_user_email',
    'CREATE UNIQUE INDEX uk_sys_user_email ON sys_user (email)'
);

CREATE TABLE IF NOT EXISTS email_verification_code (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    email VARCHAR(320) NOT NULL,
    purpose VARCHAR(40) NOT NULL,
    code_hash VARCHAR(255) NOT NULL,
    attempts INT NOT NULL DEFAULT 0,
    max_attempts INT NOT NULL DEFAULT 5,
    expires_at DATETIME NOT NULL,
    resend_after DATETIME NOT NULL,
    consumed_at DATETIME NULL,
    request_ip_hash VARCHAR(128) NULL,
    user_agent_hash VARCHAR(128) NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

CALL identity_execute_if_index_missing(
    'email_verification_code',
    'idx_email_verification_latest',
    'CREATE INDEX idx_email_verification_latest ON email_verification_code (email, purpose, consumed_at, created_at)'
);

CREATE TABLE IF NOT EXISTS identity_audit_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    event_type VARCHAR(64) NOT NULL,
    outcome VARCHAR(16) NOT NULL,
    actor_user_id BIGINT NULL,
    target_user_id BIGINT NULL,
    identifier VARCHAR(255) NULL,
    detail VARCHAR(1000) NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CALL identity_execute_if_index_missing(
    'identity_audit_log',
    'idx_identity_audit_log_created_at',
    'CREATE INDEX idx_identity_audit_log_created_at ON identity_audit_log (created_at)'
);
CALL identity_execute_if_index_missing(
    'identity_audit_log',
    'idx_identity_audit_log_actor_user_id',
    'CREATE INDEX idx_identity_audit_log_actor_user_id ON identity_audit_log (actor_user_id)'
);
CALL identity_execute_if_index_missing(
    'identity_audit_log',
    'idx_identity_audit_log_target_user_id',
    'CREATE INDEX idx_identity_audit_log_target_user_id ON identity_audit_log (target_user_id)'
);
CALL identity_execute_if_index_missing(
    'identity_audit_log',
    'idx_identity_audit_log_event_type',
    'CREATE INDEX idx_identity_audit_log_event_type ON identity_audit_log (event_type)'
);

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
    user_agent VARCHAR(500) NULL
);

CALL identity_execute_if_index_missing(
    'identity_refresh_token',
    'uk_identity_refresh_token_hash',
    'CREATE UNIQUE INDEX uk_identity_refresh_token_hash ON identity_refresh_token (token_hash)'
);
CALL identity_execute_if_index_missing(
    'identity_refresh_token',
    'idx_identity_refresh_token_user_id',
    'CREATE INDEX idx_identity_refresh_token_user_id ON identity_refresh_token (user_id)'
);
CALL identity_execute_if_index_missing(
    'identity_refresh_token',
    'idx_identity_refresh_token_expires_at',
    'CREATE INDEX idx_identity_refresh_token_expires_at ON identity_refresh_token (expires_at)'
);
CALL identity_execute_if_index_missing(
    'identity_refresh_token',
    'idx_identity_refresh_token_revoked_at',
    'CREATE INDEX idx_identity_refresh_token_revoked_at ON identity_refresh_token (revoked_at)'
);

DROP PROCEDURE IF EXISTS identity_execute_if_index_missing;
