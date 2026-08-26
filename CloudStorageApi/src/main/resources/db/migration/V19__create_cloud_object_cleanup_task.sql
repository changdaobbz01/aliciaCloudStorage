CREATE TABLE IF NOT EXISTS cloud_object_cleanup_task (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    object_key VARCHAR(1024) NOT NULL,
    source VARCHAR(64) NOT NULL,
    status VARCHAR(32) NOT NULL,
    attempts INT NOT NULL DEFAULT 0,
    last_error VARCHAR(1000) NULL,
    next_retry_at DATETIME NOT NULL,
    completed_at DATETIME NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE INDEX idx_cloud_object_cleanup_status_retry
    ON cloud_object_cleanup_task (status, next_retry_at);

CREATE INDEX idx_cloud_object_cleanup_source_created
    ON cloud_object_cleanup_task (source, created_at);

CREATE INDEX idx_cloud_object_cleanup_object_key
    ON cloud_object_cleanup_task (object_key(255));
