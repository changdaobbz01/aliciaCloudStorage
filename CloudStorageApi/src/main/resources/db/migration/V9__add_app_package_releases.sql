CREATE TABLE IF NOT EXISTS app_package_release (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    file_name VARCHAR(255) NOT NULL,
    file_size_bytes BIGINT NOT NULL,
    content_type VARCHAR(120) NOT NULL,
    object_key VARCHAR(512) NOT NULL,
    sha256_hex VARCHAR(64) NULL,
    version_name VARCHAR(64) NOT NULL,
    release_notes TEXT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'CURRENT',
    uploaded_by BIGINT NULL,
    uploaded_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_app_package_release_uploaded_by
        FOREIGN KEY (uploaded_by) REFERENCES sys_user(id) ON DELETE SET NULL
);

CREATE UNIQUE INDEX uk_app_package_release_object_key
    ON app_package_release (object_key);

CREATE INDEX idx_app_package_release_status_uploaded
    ON app_package_release (status, uploaded_at);

CREATE INDEX idx_app_package_release_uploaded_by
    ON app_package_release (uploaded_by);
