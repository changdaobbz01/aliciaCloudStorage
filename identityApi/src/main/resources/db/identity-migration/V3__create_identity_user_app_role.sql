CREATE TABLE IF NOT EXISTS identity_user_app_role (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    app_code VARCHAR(64) NOT NULL,
    role_code VARCHAR(64) NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT uk_identity_user_app_role_user_app UNIQUE (user_id, app_code),
    CONSTRAINT fk_identity_user_app_role_user
        FOREIGN KEY (user_id) REFERENCES identity_user(id) ON DELETE CASCADE,
    INDEX idx_identity_user_app_role_app_role (app_code, role_code),
    INDEX idx_identity_user_app_role_user (user_id)
);

INSERT INTO identity_user_app_role (
    user_id,
    app_code,
    role_code,
    created_at,
    updated_at
)
SELECT
    id,
    'cloud',
    'CLOUD_ADMIN',
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
FROM identity_user
WHERE role = 'ADMIN'
ON DUPLICATE KEY UPDATE
    role_code = VALUES(role_code),
    updated_at = CURRENT_TIMESTAMP;
