CREATE TABLE IF NOT EXISTS sys_user (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    phone_number VARCHAR(20) NOT NULL UNIQUE,
    nickname VARCHAR(100) NOT NULL,
    avatar_url VARCHAR(500) NULL,
    password_hash VARCHAR(255) NOT NULL,
    role VARCHAR(20) NOT NULL DEFAULT 'USER',
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS storage_node (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    owner_id BIGINT NOT NULL,
    parent_id BIGINT NULL,
    node_name VARCHAR(255) NOT NULL,
    node_type VARCHAR(20) NOT NULL COMMENT 'FOLDER or FILE',
    file_size BIGINT NOT NULL DEFAULT 0,
    file_extension VARCHAR(32) NULL,
    mime_type VARCHAR(120) NULL,
    storage_path VARCHAR(500) NULL,
    is_deleted TINYINT(1) NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_storage_node_owner FOREIGN KEY (owner_id) REFERENCES sys_user(id),
    CONSTRAINT fk_storage_node_parent FOREIGN KEY (parent_id) REFERENCES storage_node(id) ON DELETE SET NULL
);

CREATE INDEX idx_storage_node_owner_parent_deleted
    ON storage_node (owner_id, parent_id, is_deleted);

CREATE INDEX idx_storage_node_owner_type_deleted
    ON storage_node (owner_id, node_type, is_deleted);
