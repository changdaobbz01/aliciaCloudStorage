CREATE TABLE IF NOT EXISTS share_link (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    share_code VARCHAR(40) NOT NULL UNIQUE,
    owner_id BIGINT NOT NULL,
    title VARCHAR(255) NOT NULL,
    password_hash VARCHAR(255) NULL,
    expires_at DATETIME NULL,
    allow_download TINYINT(1) NOT NULL DEFAULT 1,
    allow_save TINYINT(1) NOT NULL DEFAULT 1,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    view_count BIGINT NOT NULL DEFAULT 0,
    last_accessed_at DATETIME NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_share_link_owner FOREIGN KEY (owner_id) REFERENCES sys_user(id)
);

CREATE TABLE IF NOT EXISTS share_link_item (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    share_id BIGINT NOT NULL,
    node_id BIGINT NOT NULL,
    sort_order INT NOT NULL DEFAULT 0,
    CONSTRAINT fk_share_link_item_share FOREIGN KEY (share_id) REFERENCES share_link(id) ON DELETE CASCADE,
    CONSTRAINT fk_share_link_item_node FOREIGN KEY (node_id) REFERENCES storage_node(id) ON DELETE CASCADE
);

CREATE INDEX idx_share_link_owner_created
    ON share_link (owner_id, created_at);

CREATE INDEX idx_share_link_item_share_order
    ON share_link_item (share_id, sort_order);

CREATE INDEX idx_share_link_item_node
    ON share_link_item (node_id);
