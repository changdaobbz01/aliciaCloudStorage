CREATE TABLE IF NOT EXISTS cloud_user_profile (
    identity_user_id BIGINT PRIMARY KEY,
    home_background_url VARCHAR(500) NULL,
    storage_quota_bytes BIGINT NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_cloud_user_profile_identity_user
        FOREIGN KEY (identity_user_id) REFERENCES sys_user(id) ON DELETE CASCADE
);

INSERT INTO cloud_user_profile (
    identity_user_id,
    home_background_url,
    storage_quota_bytes,
    created_at,
    updated_at
)
SELECT
    su.id,
    su.home_background_url,
    su.storage_quota_bytes,
    su.created_at,
    su.updated_at
FROM sys_user su
LEFT JOIN cloud_user_profile cup
    ON cup.identity_user_id = su.id
WHERE cup.identity_user_id IS NULL;
