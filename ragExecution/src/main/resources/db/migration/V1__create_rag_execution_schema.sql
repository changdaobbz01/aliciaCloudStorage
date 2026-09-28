CREATE TABLE rag_execution (
    id CHAR(36) PRIMARY KEY,
    owner_user_id BIGINT NOT NULL,
    conversation_id VARCHAR(128) NOT NULL,
    source_response_id VARCHAR(128) NOT NULL,
    plan_id VARCHAR(128) NOT NULL,
    plan_schema_version VARCHAR(32) NOT NULL,
    plan_hash CHAR(64) NOT NULL,
    action_summary VARCHAR(1000) NOT NULL,
    risk VARCHAR(16) NOT NULL,
    status VARCHAR(32) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    idempotency_key VARCHAR(128) NOT NULL,
    expires_at TIMESTAMP(6) NOT NULL,
    confirmed_at TIMESTAMP(6) NULL,
    queued_at TIMESTAMP(6) NULL,
    started_at TIMESTAMP(6) NULL,
    finished_at TIMESTAMP(6) NULL,
    lease_owner VARCHAR(128) NULL,
    lease_until TIMESTAMP(6) NULL,
    heartbeat_at TIMESTAMP(6) NULL,
    result_code VARCHAR(64) NULL,
    result_summary_json JSON NULL,
    error_code VARCHAR(64) NULL,
    error_message_safe VARCHAR(1000) NULL,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    CONSTRAINT uk_rag_execution_owner_idempotency
        UNIQUE (owner_user_id, idempotency_key),
    CONSTRAINT uk_rag_execution_owner_plan
        UNIQUE (owner_user_id, plan_id, plan_hash)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_rag_execution_status_lease
    ON rag_execution (status, lease_until, expires_at, queued_at);

CREATE INDEX idx_rag_execution_owner_created
    ON rag_execution (owner_user_id, created_at);

CREATE TABLE rag_execution_step (
    id CHAR(36) PRIMARY KEY,
    execution_id CHAR(36) NOT NULL,
    step_index INT NOT NULL,
    action_type VARCHAR(40) NOT NULL,
    payload_schema_version VARCHAR(32) NOT NULL,
    payload_json JSON NOT NULL,
    payload_hash CHAR(64) NOT NULL,
    status VARCHAR(32) NOT NULL,
    attempts INT NOT NULL DEFAULT 0,
    available_at TIMESTAMP(6) NOT NULL,
    started_at TIMESTAMP(6) NULL,
    finished_at TIMESTAMP(6) NULL,
    result_json JSON NULL,
    error_code VARCHAR(64) NULL,
    error_message_safe VARCHAR(1000) NULL,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    CONSTRAINT fk_rag_execution_step_execution
        FOREIGN KEY (execution_id) REFERENCES rag_execution (id),
    CONSTRAINT uk_rag_execution_step_index
        UNIQUE (execution_id, step_index)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_rag_execution_step_due
    ON rag_execution_step (status, available_at);

CREATE TABLE rag_execution_event (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    execution_id CHAR(36) NOT NULL,
    sequence_no BIGINT NOT NULL,
    event_type VARCHAR(64) NOT NULL,
    visibility VARCHAR(16) NOT NULL,
    public_payload_json JSON NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    CONSTRAINT fk_rag_execution_event_execution
        FOREIGN KEY (execution_id) REFERENCES rag_execution (id),
    CONSTRAINT uk_rag_execution_event_sequence
        UNIQUE (execution_id, sequence_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_rag_execution_event_created
    ON rag_execution_event (execution_id, created_at);
