ALTER TABLE storage_node
    ADD COLUMN entity_version BIGINT NOT NULL DEFAULT 0;

CREATE TABLE rag_execution_action_receipt (
    step_id CHAR(36) PRIMARY KEY,
    execution_id CHAR(36) NOT NULL,
    actor_user_id BIGINT NOT NULL,
    action_type VARCHAR(40) NOT NULL,
    payload_schema_version VARCHAR(32) NOT NULL,
    request_hash CHAR(64) NOT NULL,
    status VARCHAR(32) NOT NULL,
    result_code VARCHAR(64) NULL,
    result_json TEXT NULL,
    completed_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    CONSTRAINT uk_rag_action_receipt_execution_step
        UNIQUE (execution_id, step_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_rag_action_receipt_execution
    ON rag_execution_action_receipt (execution_id, created_at);

CREATE TABLE rag_execution_action_nonce (
    nonce_key VARCHAR(140) PRIMARY KEY,
    caller VARCHAR(64) NOT NULL,
    nonce VARCHAR(64) NOT NULL,
    expires_at DATETIME(6) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    CONSTRAINT uk_rag_action_nonce_caller_nonce
        UNIQUE (caller, nonce)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_rag_action_nonce_expiry
    ON rag_execution_action_nonce (expires_at);
