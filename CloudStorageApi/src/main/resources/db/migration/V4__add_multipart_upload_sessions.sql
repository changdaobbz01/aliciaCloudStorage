CREATE TABLE IF NOT EXISTS multipart_upload_session (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    owner_id BIGINT NOT NULL,
    parent_id BIGINT NULL,
    parent_scope_id BIGINT NOT NULL DEFAULT 0,
    upload_token VARCHAR(64) NOT NULL,
    cos_upload_id VARCHAR(255) NOT NULL,
    object_key VARCHAR(500) NOT NULL,
    file_name VARCHAR(255) NOT NULL,
    file_size BIGINT NOT NULL,
    content_type VARCHAR(120) NOT NULL,
    chunk_size BIGINT NOT NULL,
    total_chunks INT NOT NULL,
    file_fingerprint VARCHAR(128) NOT NULL,
    status VARCHAR(20) NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_multipart_upload_session_owner FOREIGN KEY (owner_id) REFERENCES sys_user(id),
    CONSTRAINT fk_multipart_upload_session_parent FOREIGN KEY (parent_id) REFERENCES storage_node(id) ON DELETE SET NULL,
    CONSTRAINT uk_multipart_upload_session_token UNIQUE (upload_token)
);

CREATE INDEX idx_multipart_upload_session_resume
    ON multipart_upload_session (owner_id, parent_scope_id, file_name, file_size, file_fingerprint, status, updated_at);

CREATE TABLE IF NOT EXISTS multipart_upload_part (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    session_id BIGINT NOT NULL,
    part_number INT NOT NULL,
    part_size BIGINT NOT NULL,
    e_tag VARCHAR(255) NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_multipart_upload_part_session FOREIGN KEY (session_id) REFERENCES multipart_upload_session(id) ON DELETE CASCADE,
    CONSTRAINT uk_multipart_upload_part_session_number UNIQUE (session_id, part_number)
);
