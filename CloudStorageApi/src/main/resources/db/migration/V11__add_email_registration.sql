ALTER TABLE sys_user
    MODIFY phone_number VARCHAR(20) NULL,
    ADD COLUMN email VARCHAR(320) NULL AFTER phone_number,
    ADD COLUMN email_verified_at DATETIME NULL AFTER email;

CREATE UNIQUE INDEX uk_sys_user_email
    ON sys_user (email);

CREATE TABLE email_verification_code (
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

CREATE INDEX idx_email_verification_latest
    ON email_verification_code (email, purpose, consumed_at, created_at);
